package com.redhat.cloud.notifications.routers.internal.kessel;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.kessel.KesselAuthorization;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryAuthorization;
import com.redhat.cloud.notifications.auth.kessel.KesselInventoryResourceType;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import io.grpc.stub.StreamObserver;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.project_kessel.api.inventory.v1beta1.resources.CreateNotificationsIntegrationRequest;
import org.project_kessel.api.inventory.v1beta1.resources.CreateNotificationsIntegrationResponse;
import org.project_kessel.api.inventory.v1beta1.resources.Metadata;
import org.project_kessel.api.inventory.v1beta1.resources.NotificationsIntegration;
import org.project_kessel.api.inventory.v1beta1.resources.ReporterData;
import org.project_kessel.api.relations.v1beta1.CreateTuplesRequest;
import org.project_kessel.api.relations.v1beta1.CreateTuplesResponse;
import org.project_kessel.api.relations.v1beta1.ObjectReference;
import org.project_kessel.api.relations.v1beta1.ObjectType;
import org.project_kessel.api.relations.v1beta1.Relationship;
import org.project_kessel.api.relations.v1beta1.SubjectReference;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;
import org.project_kessel.relations.client.RelationTuplesClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@ApplicationScoped
@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL)
public class KesselAssetsMigrationService {
    /**
     * Defines the relation between the subject and the resource when passing
     * the tuple to Kessel. <a href="https://github.com/RedHatInsights/rbac-config/blob/a806fb03c95959391eceb0b42c7eefd8ae2350ae/configs/prod/schemas/schema.zed#L3">
     * Reference</a>
     */
    public static final String RELATION = "workspace";

    @Inject
    BackendConfig backendConfig;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    RelationTuplesClient relationTuplesClient;

    @Inject
    WorkspaceUtils workspaceUtils;

    @Inject
    KesselAuthorization kesselAuthorizationService;

    @Inject
    KesselInventoryAuthorization kesselInventoryAuthorizationService;

    @Inject
    NotificationsIntegrationClient notificationsIntegrationClient;

    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/kessel/migrate-assets")
    @POST
    @RunOnVirtualThread
    public void migrateAssets(@Nullable final KesselAssetsMigrationRequest kamRequest) {
        Log.info("Kessel assets' migration begins");

        // Grab the organization ID specified in the request.
        final Optional<String> orgId = (kamRequest == null) ? Optional.empty() : Optional.of(kamRequest.orgId());

        int fetchedEndpointsSize = 0;
        int offset = 0;
        int traceLoops = 0;
        do {
            Log.debugf("[loops: %s] Loops", traceLoops);

            final List<Endpoint> fetchedEndpoints = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(orgId, this.backendConfig.getKesselMigrationBatchSize(), offset);
            Log.debugf("[offset: %s][first_integration: %s][last_integration: %s] Fetched batch of %s integrations", offset, (fetchedEndpoints.isEmpty()) ? "none" : fetchedEndpoints.getFirst().getId(), (fetchedEndpoints.isEmpty()) ? "none" : fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());

            // If for some reason we have fetched full pages from the database
            // all the time, the last one might be empty, so there is no need
            // to attempt calling Kessel.
            if (fetchedEndpoints.isEmpty()) {
                Log.debug("Breaking the do-while loop because the size of the fetched integrations is zero");
                break;
            }

            final CreateTuplesRequest request = this.createTuplesRequest(fetchedEndpoints);
            Log.tracef("Generated a \"CreateTuplesRequest\": %s", request);

            final int finalOffset = offset;
            this.relationTuplesClient.createTuples(request, new StreamObserver<>() {
                @Override
                public void onNext(final CreateTuplesResponse createTuplesResponse) {
                    Log.debug("Calling onNext");
                    Log.infof("[offset: %s][first_integration: %s][last_integration: %s] Sent batch of %s integrations to Kessel", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());
                }

                @Override
                public void onError(final Throwable throwable) {
                    Log.debug("Calling onError");
                    Log.errorf(throwable, "[offset: %s][first_integration: %s][last_integration: %s] Unable to send batch of tuples to Kessel", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId());
                }

                @Override
                public void onCompleted() {
                    Log.debug("Calling onCompleted");
                    Log.infof("[offset: %s][first_integration: %s][last_integration: %s] Sent batch of %s integrations to Kessel", finalOffset, fetchedEndpoints.getFirst().getId(), fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());
                }
            });

            fetchedEndpointsSize = fetchedEndpoints.size();
            offset += fetchedEndpoints.size();
            traceLoops += 1;

            Log.debugf("[fetchedEndpointsSize: %s][kesselMigrationBatchSize: %s][offset: %s] do-while loop condition", fetchedEndpointsSize, offset, this.backendConfig.getKesselMigrationBatchSize());
        } while (fetchedEndpointsSize == this.backendConfig.getKesselMigrationBatchSize());

        Log.info("Finished migrating integrations to the Kessel inventory");
    }

    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/kessel/migrate-assets-inventory")
    @POST
    @RunOnVirtualThread
    public void migrateAssetsInventory(@Nullable final KesselAssetsMigrationRequest kamRequest) {
        Log.info("Kessel assets' migration begins");

        // Grab the organization ID specified in the request.
        final Optional<String> orgId = (kamRequest == null) ? Optional.empty() : Optional.of(kamRequest.orgId());

        int fetchedEndpointsSize = 0;
        int offset = 0;
        int traceLoops = 0;
        do {
            Log.debugf("[loops: %s] Loops", traceLoops);

            final List<Endpoint> fetchedEndpoints = this.endpointRepository.getNonSystemEndpointsByOrgIdWithLimitAndOffset(orgId, this.backendConfig.getKesselMigrationBatchSize(), offset);
            Log.debugf("[offset: %s][first_integration: %s][last_integration: %s] Fetched batch of %s integrations", offset, (fetchedEndpoints.isEmpty()) ? "none" : fetchedEndpoints.getFirst().getId(), (fetchedEndpoints.isEmpty()) ? "none" : fetchedEndpoints.getLast().getId(), fetchedEndpoints.size());

            // If for some reason we have fetched full pages from the database
            // all the time, the last one might be empty, so there is no need
            // to attempt calling Kessel.
            if (fetchedEndpoints.isEmpty()) {
                Log.debug("Breaking the do-while loop because the size of the fetched integrations is zero");
                break;
            }

            for (Endpoint endpoint : fetchedEndpoints) {
                final CreateNotificationsIntegrationRequest request = this.buildCreateIntegrationRequest(endpoint);
                Log.tracef("Generated a \"CreateNotificationsIntegrationRequest\": %s", request);

                // Send the request to the inventory.
                final CreateNotificationsIntegrationResponse response;

                try {
                    response = this.notificationsIntegrationClient.CreateNotificationsIntegration(request);
                    Log.tracef(" Received payload for the integration creation: %s", response);
                } catch (final Exception e) {
                    Log.errorf(
                        e,
                        "Unable to create integration in Kessel's inventory from request: %s",
                        request
                    );
                }
            }

            fetchedEndpointsSize = fetchedEndpoints.size();
            offset += fetchedEndpoints.size();
            traceLoops += 1;

            Log.debugf("[fetchedEndpointsSize: %s][kesselMigrationBatchSize: %s][offset: %s] do-while loop condition", fetchedEndpointsSize, offset, this.backendConfig.getKesselMigrationBatchSize());
        } while (fetchedEndpointsSize == this.backendConfig.getKesselMigrationBatchSize());

        Log.info("Finished migrating integrations to the Kessel inventory");
    }

    @Path("/kessel/check-migrated-assets")
    @GET
    @RunOnVirtualThread
    public void checkMigratedAssets(@QueryParam("use_inventory") Boolean useInventory) {
        Log.info("Kessel assets' migration check");

        List<String> orgs = endpointRepository.getOrgIdWithEndpoints();
        int syncEndpoints = 0;
        int errorOrgs = 0;
        int notCheckedEndpoints = 0;
        int endpointsWithDelta = 0;
        int totalEndpoints = 0;

        for (String org : orgs) {
            Set<UUID> endpointsUuidsFromDb = new HashSet<>(endpointRepository.getEndpointsUUIDsByOrgId(org));
            totalEndpoints += endpointsUuidsFromDb.size();
            try {
                UUID workspaceId = workspaceUtils.getDefaultWorkspaceId(org);
                Set<UUID> endpointsUuidsFromKessel;
                if (Boolean.TRUE.equals(useInventory)) {
                    endpointsUuidsFromKessel = kesselInventoryAuthorizationService.listWorkspaceIntegrations(workspaceId);
                } else {
                    endpointsUuidsFromKessel = kesselAuthorizationService.listWorkspaceIntegrations(workspaceId);
                }
                if (endpointsUuidsFromDb.containsAll(endpointsUuidsFromKessel) && endpointsUuidsFromKessel.containsAll(endpointsUuidsFromDb)) {
                    Log.tracef("Kessel assets' are sync for org %s", org);
                    syncEndpoints += endpointsUuidsFromDb.size();
                } else {
                    Log.errorf("Kessel assets' are not sync for org %s, kessel assets are: %s ; Notifications endpoints are: %s",
                        org,
                        endpointsUuidsFromKessel.stream().map(UUID::toString).collect(Collectors.joining(", ")),
                        endpointsUuidsFromDb.stream().map(UUID::toString).collect(Collectors.joining(", "))
                    );
                    endpointsWithDelta += endpointsUuidsFromDb.size();
                }
            } catch (Exception e) {
                Log.errorf(e, "Error checking endpoints for org %s containing %d endpoints", org, endpointsUuidsFromDb.size());
                errorOrgs++;
                notCheckedEndpoints += endpointsUuidsFromDb.size();
            }
        }
        Log.infof("%d orgs Scanned, %d OK / %d KO (covering %d endpoints); %d endpoints are sync, %d are not, over %d endpoints",
            orgs.size(),
                    orgs.size() - errorOrgs,
                    errorOrgs,
                    notCheckedEndpoints,
                    syncEndpoints,
                    endpointsWithDelta,
                    totalEndpoints);
        Log.info("Finished migrating integrations check");
    }

    protected CreateNotificationsIntegrationRequest buildCreateIntegrationRequest(final Endpoint endpoint) {
        return CreateNotificationsIntegrationRequest.newBuilder()
            .setIntegration(
                NotificationsIntegration.newBuilder()
                    .setMetadata(Metadata.newBuilder()
                        .setResourceType(KesselInventoryResourceType.INTEGRATION.getKesselRepresentation())
                        .setWorkspaceId(this.workspaceUtils.getDefaultWorkspaceId(endpoint.getOrgId()).toString())
                        .build()
                    ).setReporterData(ReporterData.newBuilder()
                        .setLocalResourceId(endpoint.getId().toString())
                        .setReporterInstanceId(this.backendConfig.getKesselInventoryReporterInstanceId())
                        .setReporterType(ReporterData.ReporterType.NOTIFICATIONS)
                        .build()
                    ).build()
            ).build();
    }

    /**
     * Creates a bulk import request ready to be sent to kessel.
     * @param endpoints the list of endpoints to create the bulk import request
     *                  from.
     * @return the bulk import request ready to be sent.
     */
    protected CreateTuplesRequest createTuplesRequest(final List<Endpoint> endpoints) {
        final List<Relationship> relations = new ArrayList<>(endpoints.size());

        for (final Endpoint endpoint : endpoints) {
            try {
                relations.add(this.mapEndpointToRelationship(endpoint));
            } catch (final Exception e) {
                Log.errorf("[org_id: %s][endpoint_id: %s] Unable to get the default workspace for integration", endpoint.getOrgId(), endpoint.getId());
            }
        }

        return CreateTuplesRequest
            .newBuilder()
            .addAllTuples(relations)
            .setUpsert(true)
            .build();
    }

    /**
     * Maps an endpoint to a Kessel relationship.
     * @param endpoint the endpoint to map.
     * @return the generated relationship.
     */
    protected Relationship mapEndpointToRelationship(final Endpoint endpoint) {
        return Relationship.newBuilder()
            .setResource(
                ObjectReference.newBuilder()
                    .setType(ResourceType.INTEGRATION.getKesselObjectType())
                    .setId(endpoint.getId().toString())
                    .build()
            ).setRelation(RELATION)
            .setSubject(
                SubjectReference.newBuilder()
                    .setSubject(
                        ObjectReference.newBuilder()
                            .setType(ObjectType.newBuilder().setNamespace("rbac").setName("workspace").build())
                            .setId(this.workspaceUtils.getDefaultWorkspaceId(endpoint.getOrgId()).toString())
                            .build()
                    ).build()
            ).build();
    }
}
