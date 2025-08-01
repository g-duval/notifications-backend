package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.DelayedThrower;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.event.TestEventHelper;
import com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatProcessor;
import com.redhat.cloud.notifications.processors.camel.slack.SlackProcessor;
import com.redhat.cloud.notifications.processors.camel.teams.TeamsProcessor;
import com.redhat.cloud.notifications.processors.drawer.DrawerProcessor;
import com.redhat.cloud.notifications.processors.email.EmailAggregationProcessor;
import com.redhat.cloud.notifications.processors.email.EmailProcessor;
import com.redhat.cloud.notifications.processors.eventing.EventingProcessor;
import com.redhat.cloud.notifications.processors.pagerduty.PagerDutyProcessor;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class EndpointProcessor {

    public static final String PROCESSED_MESSAGES_COUNTER_NAME = "processor.input.processed";
    public static final String PROCESSED_ENDPOINTS_COUNTER_NAME = "processor.input.endpoint.processed";
    public static final String DELAYED_EXCEPTION_MSG = "Exceptions were thrown during an event processing";
    public static final String SLACK_ENDPOINT_SUBTYPE = "slack";
    public static final String TEAMS_ENDPOINT_SUBTYPE = "teams";
    public static final String GOOGLE_CHAT_ENDPOINT_SUBTYPE = "google_chat";

    public static final String NOTIFICATIONS_APP_BUNDLE_NAME = "console";
    public static final String NOTIFICATIONS_APP_NAME = "notifications";
    public static final String AGGREGATION_EVENT_TYPE_NAME = "aggregation";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    WebhookTypeProcessor webhookProcessor;

    @Inject
    EventingProcessor camelProcessor;

    @Inject
    EmailProcessor emailConnectorProcessor;

    @Inject
    EmailAggregationProcessor emailAggregationProcessor;

    @Inject
    SlackProcessor slackProcessor;

    @Inject
    TeamsProcessor teamsProcessor;

    @Inject
    GoogleChatProcessor googleChatProcessor;

    @Inject
    DrawerProcessor drawerProcessor;

    @Inject
    PagerDutyProcessor pagerDutyProcessor;

    @Inject
    MeterRegistry registry;

    @Inject
    EngineConfig engineConfig;

    private Counter processedItems;
    private Counter endpointTargeted;

    @PostConstruct
    void init() {
        processedItems = registry.counter(PROCESSED_MESSAGES_COUNTER_NAME);
        endpointTargeted = registry.counter(PROCESSED_ENDPOINTS_COUNTER_NAME);
    }

    public void process(Event event) {
        process(event, false);
    }

    public void process(Event event, boolean replayEmailsOnly) {
        processedItems.increment();
        final List<Endpoint> endpoints = new ArrayList<>();
        if (TestEventHelper.isIntegrationTestEvent(event)) {
            final UUID endpointUuid = TestEventHelper.extractEndpointUuidFromTestEvent(event);

            final Endpoint endpoint = this.endpointRepository.findByUuidAndOrgId(endpointUuid, event.getOrgId());

            endpoints.add(endpoint);
        } else if (isAggregatorEvent(event)) {
            Log.debugf("[org_id: %s] Processing aggregation event: %s", event.getOrgId(), event);

            endpoints.add(endpointRepository.getOrCreateDefaultSystemSubscription(event.getAccountId(), event.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION));

            Log.debugf("[org_id: %s] Found %s endpoints for the aggregation event: %s", event.getOrgId(), endpoints.size(), event);
        } else {
            if (engineConfig.isUseDirectEndpointToEventTypeEnabled()) {
                endpoints.addAll(endpointRepository.getTargetEndpointsWithoutUsingBgs(event.getOrgId(), event.getEventType()));
            } else {
                endpoints.addAll(endpointRepository.getTargetEndpoints(event.getOrgId(), event.getEventType()));
                if (engineConfig.isDirectEndpointToEventTypeDryRunEnabled()) {
                    final List<Endpoint> fetchEndpointWithoutBg = endpointRepository.getTargetEndpointsWithoutUsingBgs(event.getOrgId(), event.getEventType());
                    Set<Endpoint> endpointsWithBG = endpoints.stream().collect(Collectors.toSet());
                    Set<Endpoint> endpointsWithoutBG = fetchEndpointWithoutBg.stream().collect(Collectors.toSet());
                    if (!endpointsWithBG.equals(endpointsWithoutBG)) {
                        Log.errorf("Fetching endpoints with and without BG don't have the same result for orgId '%s' and Event type '%s (%s)'", event.getOrgId(), event.getEventType().getName(), event.getEventType().getId());
                        Log.info("Endpoint list from bg is: " + endpointsWithBG);
                        Log.info("Endpoint list without bg is: " + endpointsWithoutBG);
                    }
                }
            }
        }

        endpoints.removeIf(endpoint -> {
            // Default endpoints (orgId is null) used as system integrations for the all orgs can't be blacklisted
            if (null != endpoint.getOrgId() && engineConfig.isBlacklistedEndpoint(endpoint.getId())) {
                Log.infof("Org: %s, skipping endpoint: %s because it was blacklisted", endpoint.getOrgId(), endpoint.getId());
                return true;
            }
            return false;
        });

        // Target endpoints are grouped by endpoint type.
        endpointTargeted.increment(endpoints.size());
        Map<EndpointType, List<Endpoint>> endpointsByType = endpoints.stream().collect(Collectors.groupingBy(Endpoint::getType));

        DelayedThrower.throwEventually(DELAYED_EXCEPTION_MSG, accumulator -> {
            for (Map.Entry<EndpointType, List<Endpoint>> endpointsByTypeEntry : endpointsByType.entrySet()) {
                try {
                    if (replayEmailsOnly && endpointsByTypeEntry.getKey() != EndpointType.EMAIL_SUBSCRIPTION) {
                        continue;
                    }
                    // For each endpoint type, the list of target endpoints is sent alongside with the event to the relevant processor.
                    switch (endpointsByTypeEntry.getKey()) {
                        // TODO Introduce EndpointType.SLACK?
                        case CAMEL:
                            if (!event.getEventType().isRestrictToRecipientsIntegrations()) {
                                Map<String, List<Endpoint>> endpointsBySubType = endpointsByTypeEntry.getValue().stream().collect(Collectors.groupingBy(Endpoint::getSubType));
                                for (Map.Entry<String, List<Endpoint>> endpointsBySubTypeEntry : endpointsBySubType.entrySet()) {
                                    try {
                                        if (SLACK_ENDPOINT_SUBTYPE.equals(endpointsBySubTypeEntry.getKey())) {
                                            slackProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                        } else if (TEAMS_ENDPOINT_SUBTYPE.equals(endpointsBySubTypeEntry.getKey())) {
                                            teamsProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                        } else if (GOOGLE_CHAT_ENDPOINT_SUBTYPE.equals(endpointsBySubTypeEntry.getKey())) {
                                            googleChatProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                        } else {
                                            camelProcessor.process(event, endpointsBySubTypeEntry.getValue());
                                        }
                                    } catch (Exception e) {
                                        accumulator.add(e);
                                    }
                                }
                            }
                            break;
                        case EMAIL_SUBSCRIPTION:
                            if (isAggregatorEvent(event) && !replayEmailsOnly) {
                                Log.debugf("[org_id: %s] Sending event through the aggregator processor: %s", event.getOrgId(), event);
                                emailAggregationProcessor.processAggregation(event);
                            } else {
                                Log.debugf("[org_id: %s] Sending event through the email connector: %s", event.getOrgId(), event);
                                emailConnectorProcessor.process(event, endpointsByTypeEntry.getValue(), replayEmailsOnly);
                            }
                            break;
                        case WEBHOOK:
                        case ANSIBLE:
                            if (!event.getEventType().isRestrictToRecipientsIntegrations()) {
                                webhookProcessor.process(event, endpointsByTypeEntry.getValue());
                            }
                            break;
                        case DRAWER:
                            drawerProcessor.process(event, endpointsByTypeEntry.getValue());
                            break;
                        case PAGERDUTY:
                            if (!event.getEventType().isRestrictToRecipientsIntegrations()) {
                                pagerDutyProcessor.process(event, endpointsByTypeEntry.getValue());
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected endpoint type: " + endpointsByTypeEntry.getKey());
                    }
                } catch (Exception e) {
                    accumulator.add(e);
                }
            }
        });
    }

    public static boolean isAggregatorEvent(final com.redhat.cloud.notifications.models.Event event) {
        if (event.getEventWrapper() instanceof EventWrapperAction) {
            Action action = ((EventWrapperAction) event.getEventWrapper()).getEvent();

            return NOTIFICATIONS_APP_BUNDLE_NAME.equals(action.getBundle()) &&
                NOTIFICATIONS_APP_NAME.equals(action.getApplication()) &&
                AGGREGATION_EVENT_TYPE_NAME.equals(action.getEventType());
        }
        return false;
    }
}
