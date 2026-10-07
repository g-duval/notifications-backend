package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.auth.annotation.Authorization;
import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.repositories.DigestSubscriptionOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.DigestSubscriptionOrgConfig;
import com.redhat.cloud.notifications.models.DigestSubscriptionOrgConfigId;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SubscriptionTypeDTO;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_3_0;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_EDIT;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.NOTIFICATIONS_VIEW;
import static com.redhat.cloud.notifications.routers.SecurityContextUtil.getOrgId;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class OrgConfigResource {

    static final List<Integer> ALLOWED_MINUTES = Arrays.asList(0, 15, 30, 45);

    private static final Set<SubscriptionType> ALLOWED_DIGEST_TYPES = Set.of(SubscriptionType.DAILY, SubscriptionType.WEEKLY);

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @Inject
    DigestSubscriptionOrgConfigRepository digestSubscriptionOrgConfigRepository;

    @ConfigProperty(name = "notifications.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDigestTime;

    @ConfigProperty(name = "notifications.default.weekly.digest.day", defaultValue = "MONDAY")
    DayOfWeek defaultWeeklyDigestDay;

    @Path(API_NOTIFICATIONS_V_1_0 + "/org-config")
    public static class V1 extends OrgConfigResource {
    }

    @Path(API_NOTIFICATIONS_V_3_0 + "/org-config")
    public static class V3 extends OrgConfigResource {

        @APIResponse(responseCode = "204")
        @APIResponse(responseCode = "400", description = "Invalid time, day, or subscription type")
        @PUT
        @Path("/digest-subscription/{subscriptionType}")
        @Consumes(APPLICATION_JSON)
        @Operation(summary = "Set digest subscription preference", description = "Creates or updates the digest subscription schedule for the given type. Accepted minute values are 00, 15, 30, and 45. For WEEKLY, scheduled_execution_day (MONDAY to SUNDAY) is required.")
        @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT, resourceType = "digest_subscription")
        public void saveDigestSubscriptionPreference(@Context SecurityContext sec,
                                                     @PathParam("subscriptionType") SubscriptionType subscriptionType,
                                                     @NotNull @Valid DigestSubscriptionRequest request) {
            String orgId = getOrgId(sec);
            validateSubscriptionType(subscriptionType);
            validateDigestRequest(subscriptionType, request);
            Log.infof("Update digest subscription preference for orgId %s, type %s, time %s, day %s", orgId, subscriptionType, request.getScheduledExecutionTime(), request.getScheduledExecutionDay());
            digestSubscriptionOrgConfigRepository.createOrUpdateDigestPreference(orgId, subscriptionType, request.getScheduledExecutionTime(), request.getScheduledExecutionDay());
        }

        @APIResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = DigestSubscriptionResponse.class)))
        @GET
        @Path("/digest-subscription/{subscriptionType}")
        @Produces(APPLICATION_JSON)
        @Operation(summary = "Retrieve digest subscription preference", description = "Retrieves the digest subscription schedule for the given type.")
        @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "digest_subscription")
        public DigestSubscriptionResponse getDigestSubscriptionPreference(@Context SecurityContext sec,
                                                                         @PathParam("subscriptionType") SubscriptionType subscriptionType) {
            String orgId = getOrgId(sec);
            validateSubscriptionType(subscriptionType);
            Log.infof("Get digest subscription preference for orgId %s, type %s", orgId, subscriptionType);
            DigestSubscriptionOrgConfig config = digestSubscriptionOrgConfigRepository.findDigestSubscriptionOrgConfig(orgId, subscriptionType);
            if (config == null) {
                final String cron = DigestSubscriptionOrgConfigRepository.buildCronExpression(subscriptionType, defaultDigestTime, defaultWeeklyDigestDay);
                config = new DigestSubscriptionOrgConfig(new DigestSubscriptionOrgConfigId(orgId, subscriptionType), cron);
            }
            return toResponse(config);
        }
    }

    @APIResponse(responseCode = "204")
    @APIResponse(responseCode = "400", description = "Invalid minute value specified")
    @PUT
    @Path("/daily-digest/time-preference")
    @Consumes(APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Set the daily digest time", description = "Sets the daily digest UTC time. The accepted minute values are 00, 15, 30, and 45. Use this endpoint to set the time when daily emails are sent.")
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_WRITE_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_EDIT, resourceType = "daily_digest")
    public void saveDailyDigestTimePreference(@Context SecurityContext sec, @NotNull LocalTime expectedTime) {
        String orgId = getOrgId(sec);
        if (!ALLOWED_MINUTES.contains(expectedTime.getMinute())) {
            String errorMessage = "Accepted minute values are: " + ALLOWED_MINUTES.stream().map(min -> String.format("%02d", min)).collect(Collectors.joining(", ")) + ".";

            throw new BadRequestException(errorMessage);
        }
        Log.infof("Update daily digest time preference for orgId %s at %s", orgId, expectedTime);
        aggregationOrgConfigRepository.createOrUpdateDailyDigestPreference(orgId, expectedTime);
    }

    @APIResponse(responseCode = "200", content = @Content(schema = @Schema(type = SchemaType.STRING)))
    @GET
    @Path("/daily-digest/time-preference")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve the daily digest time", description = "Retrieves the daily digest time setting. Use this endpoint to check the time that daily emails are sent.")
    @Authorization(legacyRBACRole = ConsoleIdentityProvider.RBAC_READ_NOTIFICATIONS, workspacePermissions = NOTIFICATIONS_VIEW, resourceType = "daily_digest")
    public Response getDailyDigestTimePreference(@Context SecurityContext sec) {
        String orgId = getOrgId(sec);
        Log.infof("Get daily digest time preference for orgId %s", orgId);
        AggregationOrgConfig storedParameters = aggregationOrgConfigRepository.findJobAggregationOrgConfig(orgId);
        if (null != storedParameters) {
            return Response.ok(storedParameters.getScheduledExecutionTime()).build();
        } else {
            return Response.ok(defaultDigestTime).build();
        }
    }

    void validateSubscriptionType(SubscriptionType subscriptionType) {
        if (!ALLOWED_DIGEST_TYPES.contains(subscriptionType)) {
            throw new BadRequestException("Subscription type must be one of: " + ALLOWED_DIGEST_TYPES);
        }
    }

    void validateDigestRequest(SubscriptionType subscriptionType, DigestSubscriptionRequest request) {
        if (!ALLOWED_MINUTES.contains(request.getScheduledExecutionTime().getMinute())) {
            String errorMessage = "Accepted minute values are: " + ALLOWED_MINUTES.stream().map(min -> String.format("%02d", min)).collect(Collectors.joining(", ")) + ".";
            throw new BadRequestException(errorMessage);
        }
        if (subscriptionType == SubscriptionType.WEEKLY) {
            if (request.getScheduledExecutionDay() == null) {
                throw new BadRequestException("scheduled_execution_day is required for WEEKLY (MONDAY to SUNDAY).");
            }
        } else if (request.getScheduledExecutionDay() != null) {
            throw new BadRequestException("scheduled_execution_day must not be set for DAILY subscriptions.");
        }
    }

    static DigestSubscriptionResponse toResponse(DigestSubscriptionOrgConfig config) {
        DigestSubscriptionResponse response = new DigestSubscriptionResponse();
        response.setSubscriptionType(SubscriptionTypeDTO.valueOf(config.getId().subscriptionType.name()));
        response.setScheduledExecutionTime(DigestSubscriptionOrgConfigRepository.parseTimeFromCron(config.getCronExpression()));
        response.setScheduledExecutionDay(DigestSubscriptionOrgConfigRepository.parseDayFromCron(config.getCronExpression()));
        response.setNextRun(config.getNextRun());
        return response;
    }
}
