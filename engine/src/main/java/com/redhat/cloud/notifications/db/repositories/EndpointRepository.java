package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EndpointStatus.READY;
import static com.redhat.cloud.notifications.models.EndpointType.ANSIBLE;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.PAGERDUTY;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

@ApplicationScoped
public class EndpointRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    EngineConfig engineConfig;

    /**
     * The purpose of this method is to find or create an EMAIL_SUBSCRIPTION or DRAWER endpoint with empty properties. This
     * endpoint is used to aggregate and store in the DB the email or drawer actions outcome, which will be used later by the
     * event log. The recipients of the current email or drawer action have already been resolved before this step, possibly from
     * multiple endpoints and recipients settings. The properties created below have no impact on the resolution of the
     * action recipients.
     */
    @Transactional
    public Endpoint getOrCreateDefaultSystemSubscription(String accountId, String orgId, EndpointType endpointType) {
        String query = "FROM Endpoint WHERE orgId = :orgId AND compositeType.type = :endpointType";
        List<Endpoint> systemEndpoints = entityManager.createQuery(query, Endpoint.class)
            .setParameter("orgId", orgId)
            .setParameter("endpointType", endpointType)
            .getResultList();
        loadProperties(systemEndpoints);

        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
        Optional<Endpoint> endpointOptional = systemEndpoints
            .stream()
            .filter(endpoint -> properties.hasSameProperties(endpoint.getProperties(SystemSubscriptionProperties.class)))
            .findFirst();
        if (endpointOptional.isPresent()) {
            return endpointOptional.get();
        }

        String label = "Email";
        if (DRAWER == endpointType) {
            label = "Drawer";
        }

        // In order to avoid having duplicated names which could end up in the
        // "unique endpoint name" constraint being triggered, we generate and
        // assign the endpoint's UUID ourselves.
        final UUID endpointId = UUID.randomUUID();

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(properties);
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);
        endpoint.setEnabled(true);
        endpoint.setDescription(String.format("System %s endpoint", label.toLowerCase()));
        endpoint.setName(String.format("%s endpoint %s", label, endpointId));
        endpoint.setType(endpointType);
        endpoint.setStatus(READY);
        properties.setEndpoint(endpoint);

        entityManager.persist(endpoint);
        entityManager.persist(endpoint.getProperties());
        return endpoint;
    }

    public List<Endpoint> getTargetEndpoints(String orgId, EventType eventType) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled IS TRUE AND e.status = :status AND b.eventType = :eventType " +
                "AND (bga.behaviorGroup.orgId = :orgId OR bga.behaviorGroup.orgId IS NULL)";

        List<Endpoint> endpoints = entityManager.createQuery(query, Endpoint.class)
                .setParameter("status", READY)
                .setParameter("eventType", eventType)
                .setParameter("orgId", orgId)
                .getResultList();
        loadProperties(endpoints);
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getOrgId() == null) {
                if (endpoint.getType() != null && endpoint.getType().isSystemEndpointType) {
                    endpoint.setOrgId(orgId);
                } else {
                    Log.warnf("Invalid endpoint configured in default behavior group: %s", endpoint.getId());
                }
            }
        }
        return endpoints;
    }

    public List<Endpoint> getTargetEndpointsWithoutUsingBgs(String orgId, EventType eventType) {
        final String query = "SELECT DISTINCT e FROM Endpoint e, EndpointEventType eet " +
            "WHERE e = eet.endpoint AND eet.eventType = :eventType AND (e.orgId = :orgId OR e.orgId IS NULL) AND e.enabled IS TRUE AND e.status = :status";

        List<Endpoint> endpoints = entityManager.createQuery(query, Endpoint.class)
            .setParameter("status", READY)
            .setParameter("eventType", eventType)
            .setParameter("orgId", orgId)
            .getResultList();
        loadProperties(endpoints);
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getOrgId() == null) {
                if (endpoint.getType() != null && endpoint.getType().isSystemEndpointType) {
                    endpoint.setOrgId(orgId);
                } else {
                    Log.warnf("Invalid endpoint configured in default behavior group: %s", endpoint.getId());
                }
            }
        }
        return endpoints;
    }

    @CacheResult(cacheName = "aggregation-target-email-subscription-endpoints")
    public List<Endpoint> getTargetEmailSubscriptionEndpoints(String orgId, UUID eventTypeId) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled AND b.eventType.id = :eventTypeId AND (bga.behaviorGroup.orgId = :orgId OR bga.behaviorGroup.orgId IS NULL) " +
                "AND e.compositeType.type = :endpointType";

        List<Endpoint> endpoints = entityManager.createQuery(query, Endpoint.class)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("orgId", orgId)
                .setParameter("endpointType", EMAIL_SUBSCRIPTION)
                .getResultList();
        loadProperties(endpoints);
        return endpoints;
    }

    /**
     * Increments the server errors counter of the endpoint identified by the
     * given ID, unless the endpoint is a system endpoint.
     * @param endpointId the endpoint ID
     * @param currentServerErrors the current server errors number
     * @return {@code true} if the endpoint was disabled by this method,
     * {@code false} otherwise.
     */
    @Transactional
    public boolean incrementEndpointServerErrors(UUID endpointId, int currentServerErrors) {
        /*
         * This method must be an atomic operation from a DB perspective. Otherwise, we could send multiple email
         * notifications about the same disabled endpoint in case of failures happening on concurrent threads or pods.
         */
        Optional<Endpoint> endpointOptional = this.lockEndpoint(endpointId);
        if (endpointOptional.isEmpty()) {
            return false;
        }

        // Disabled endpoints should not see their "server errors" increased.
        final Endpoint endpoint = endpointOptional.get();
        if (!endpoint.isEnabled()) {
            return false;
        }

        // System endpoints should not be disabled since they are considered
        // internal.
        if (endpoint.getType().isSystemEndpointType) {
            return false;
        }

        /*
         * The endpoint should always be present unless it's been deleted recently from another thread or pod.
         * It may or may not have been disabled already from the frontend or because of a 4xx error.
         */
        final LocalDateTime currentTime = LocalDateTime.now(ZoneId.of("UTC"));

        if (endpoint.getServerErrors() + currentServerErrors > this.engineConfig.getMaxServerErrors()) {
            if (endpoint.getServerErrorsSince() != null) {
                final Duration spentDuration = Duration.between(endpoint.getServerErrorsSince(), currentTime);
                if (spentDuration.compareTo(this.engineConfig.getMinDelaySinceFirstServerErrorBeforeDisabling()) > 0) {
                    /*
                     * The endpoint exceeded the max server errors allowed from configuration
                     * and a reasonable duration was respected to give a chance to customers to fix the issue,
                     * it is therefore disabled.
                     */
                    final String hql = "UPDATE Endpoint SET enabled = FALSE WHERE id = :id AND enabled IS TRUE";
                    final int updated = entityManager.createQuery(hql)
                        .setParameter("id", endpointId)
                        .executeUpdate();
                    return updated > 0;
                }
            }
        }

        /*
         * The endpoint did NOT exceed the max server errors allowed from configuration.
         * The errors counter is therefore incremented.
         */
        if (endpoint.getServerErrors() == 0) {
            final String hql = "UPDATE Endpoint SET serverErrors = serverErrors + :currentServerErrors, serverErrorsSince = :currentDate WHERE id = :id";
            entityManager.createQuery(hql)
                .setParameter("currentServerErrors", currentServerErrors)
                .setParameter("currentDate", currentTime)
                .setParameter("id", endpointId)
                .executeUpdate();
        } else if (endpoint.getServerErrorsSince() == null) {
            // this case it to cover migration phase, when an endpoint already had some errors before introducing initial error date mechanism
            final String hql = "UPDATE Endpoint SET serverErrors = serverErrors + :currentServerErrors, serverErrorsSince = :currentDate WHERE id = :id";
            entityManager.createQuery(hql)
                .setParameter("currentServerErrors", currentServerErrors)
                .setParameter("currentDate", currentTime)
                .setParameter("id", endpointId)
                .executeUpdate();
        } else {
            final String hql = "UPDATE Endpoint SET serverErrors = serverErrors + :currentServerErrors WHERE id = :id";
            entityManager.createQuery(hql)
                .setParameter("currentServerErrors", currentServerErrors)
                .setParameter("id", endpointId)
                .executeUpdate();
        }

        return false;
    }

    /**
     * Gets the endpoint by its UUID and OrgId along with its associated properties.
     * @param endpointUuid the UUID of the endpoint.
     * @param orgId the OrgId of the tenant.
     * @return the endpoint found.
     */
    public Endpoint findByUuidAndOrgId(final UUID endpointUuid, final String orgId) {
        final String query =
                "SELECT " +
                    "e " +
                "FROM " +
                    "Endpoint AS e " +
                "WHERE " +
                    "e.id = :uuid " +
                "AND " +
                    "e.orgId = :orgId";

        Endpoint endpoint;
        try {
            endpoint = entityManager
                    .createQuery(query, Endpoint.class)
                    .setParameter("uuid", endpointUuid)
                    .setParameter("orgId", orgId)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NoResultException(
                    String.format(
                            "Endpoint with id=%s and orgId=%s not found",
                            endpointUuid,
                            orgId
                    )
            );
        }

        this.loadProperties(List.of(endpoint));

        return endpoint;
    }

    private Optional<Endpoint> lockEndpoint(UUID endpointId) {
        String hql = "FROM Endpoint WHERE id = :id";
        try {
            Endpoint endpoint = entityManager.createQuery(hql, Endpoint.class)
                    .setParameter("id", endpointId)
                    /*
                     * The endpoint will be locked by a "SELECT FOR UPDATE", preventing other threads or pods
                     * from updating it until the current transaction is complete.
                     */
                    .setLockMode(PESSIMISTIC_WRITE)
                    .getSingleResult();
            return Optional.of(endpoint);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Resets the server errors DB counter of the endpoint identified by the given ID.
     * @param endpointId the endpoint ID
     * @return {@code true} if the counter was reset by this method, {@code false} otherwise
     */
    @Transactional
    public boolean resetEndpointServerErrors(UUID endpointId) {
        String hql = "UPDATE Endpoint SET serverErrors = 0, serverErrorsSince = null WHERE id = :id AND serverErrors > 0";
        int updated = entityManager.createQuery(hql)
                .setParameter("id", endpointId)
                .executeUpdate();
        return updated > 0;
    }

    /**
     * Disables the endpoint identified by the given ID, unless the endpoint is
     * a system endpoint.
     * @param endpoint the endpoint to disable.
     * @return {@code true} if the endpoint was disabled by this method,
     * {@code false}.
     */
    @Transactional
    public boolean disableEndpoint(Endpoint endpoint) {
        if (endpoint.getType().isSystemEndpointType) {
            return false;
        }

        final String hql = "UPDATE Endpoint SET enabled = FALSE WHERE id = :id AND enabled IS TRUE";
        final int updated = entityManager.createQuery(hql)
                .setParameter("id", endpoint.getId())
                .executeUpdate();
        return updated > 0;
    }

    /**
     * Finds all the integration names of the given type and groups them by
     * the organization identifier.
     * @param integrationType the type of the integration we want to find.
     * @return a map with the organization ID as the key, and a list of
     * integration names as the value.
     */
    public Map<String, List<String>> findIntegrationNamesByTypeGroupedByOrganizationId(final CompositeEndpointType integrationType) {
        final String findByTypeQuery =
            "SELECT " +
                "e " +
            "FROM " +
                "Endpoint AS e " +
            "WHERE " +
                "e.compositeType = :type";

        // Fetch the endpoints from the database.
        final List<Endpoint> endpoints = this.entityManager.createQuery(findByTypeQuery, Endpoint.class)
            .setParameter("type", integrationType)
            .getResultList();

        // Group the endpoints by org ID.
        final Map<String, List<String>> result = new HashMap<>();
        for (final Endpoint endpoint : endpoints) {
            result.computeIfAbsent(endpoint.getOrgId(), unused -> new ArrayList<>()).add(endpoint.getName());
        }

        return result;
    }

    private void loadProperties(List<Endpoint> endpoints) {
        if (!endpoints.isEmpty()) {
            // Group endpoints in types and load in batches for each type.
            Set<Endpoint> endpointSet = new HashSet<>(endpoints);

            loadTypedProperties(WebhookProperties.class, endpointSet, ANSIBLE);
            loadTypedProperties(WebhookProperties.class, endpointSet, WEBHOOK);
            loadTypedProperties(CamelProperties.class, endpointSet, CAMEL);
            loadTypedProperties(SystemSubscriptionProperties.class, endpointSet, EMAIL_SUBSCRIPTION);
            loadTypedProperties(SystemSubscriptionProperties.class, endpointSet, DRAWER);
            loadTypedProperties(PagerDutyProperties.class, endpointSet, PAGERDUTY);
        }
    }

    private <T extends EndpointProperties> void loadTypedProperties(Class<T> typedEndpointClass, Set<Endpoint> endpoints, EndpointType type) {
        Map<UUID, Endpoint> endpointsMap = endpoints
                .stream()
                .filter(e -> e.getType().equals(type))
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        if (endpointsMap.size() > 0) {
            String hql = "FROM " + typedEndpointClass.getSimpleName() + " WHERE id IN (:endpointIds)";
            List<T> propList = entityManager.createQuery(hql, typedEndpointClass)
                    .setParameter("endpointIds", endpointsMap.keySet())
                    .getResultList();
            for (T props : propList) {
                if (props != null) {
                    Endpoint endpoint = endpointsMap.get(props.getId());
                    endpoint.setProperties(props);
                }
            }
        }
    }
}
