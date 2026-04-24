package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class ApplicationServicesMigrationRepository {

    public static final String APPLICATION_SERVICES_BUNDLE_NAME = "subscription-services";
    public static final String APPLICATION_SERVICES_APPLICATION_NAME = "application-services";

    @Inject
    EntityManager entityManager;

    public List<EventType> findApplicationServicesEventTypes() {
        final String query =
            "FROM " +
                "EventType AS et " +
            "INNER JOIN " +
                "et.application AS app " +
            "INNER JOIN " +
                "app.bundle AS bundle " +
            "WHERE " +
                "app.name = :applicationName " +
            "AND " +
                "bundle.name = :bundleName";

        return entityManager
            .createQuery(query, EventType.class)
            .setParameter("applicationName", APPLICATION_SERVICES_APPLICATION_NAME)
            .setParameter("bundleName", APPLICATION_SERVICES_BUNDLE_NAME)
            .getResultList();
    }

    public void saveApplicationServicesSubscription(final String username, final String orgId, final EventType eventType) {
        final String insertSql =
            "INSERT INTO " +
                "email_subscriptions(user_id, org_id, event_type_id, subscription_type, subscribed) " +
            "VALUES " +
                "(:userId, :orgId, :eventTypeId, 'DAILY', true) " +
            "ON CONFLICT DO NOTHING";

        entityManager
            .createNativeQuery(insertSql)
            .setParameter("userId", username)
            .setParameter("orgId", orgId)
            .setParameter("eventTypeId", eventType.getId())
            .executeUpdate();

        Log.infof("[org_id: %s][username: %s][event_type_id: %s][event_type_name: %s] Persisted application services subscription", orgId, username, eventType.getId(), eventType.getName());
    }
}
