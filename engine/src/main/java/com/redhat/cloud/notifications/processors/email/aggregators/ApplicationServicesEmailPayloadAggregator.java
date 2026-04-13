package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ApplicationServicesEmailPayloadAggregator extends AbstractEmailPayloadAggregator {

    private static final String EVENT_TYPE = "event_type";

    private static final String APPLICATION_SERVICES_KEY = "application-services";
    private static final String PRODUCTS_KEY = "products";
    private static final String EVENTS_KEY = "events";
    private static final String CONTEXT_KEY = "context";
    private static final String PAYLOAD_KEY = "payload";
    private static final String BASE_URL_KEY = "base_url";
    private static final String FAMILY_KEY = "family";
    private static final String DESCRIPTION_KEY = "description";
    private static final String PAYLOADS_KEY = "payloads";
    private static final String GLOBAL_RELEASES_NUMBER_KEY = "global_releases_number";

    public ApplicationServicesEmailPayloadAggregator() {
        JsonObject applicationServices = new JsonObject();
        applicationServices.put(PRODUCTS_KEY, new JsonObject());
        applicationServices.put(GLOBAL_RELEASES_NUMBER_KEY, 0);
        context.put(APPLICATION_SERVICES_KEY, applicationServices);
    }

    @Override
    void processEmailAggregation(EmailAggregation notification) {
        try {
            JsonObject applicationServices = context.getJsonObject(APPLICATION_SERVICES_KEY);
            JsonObject products = applicationServices.getJsonObject(PRODUCTS_KEY);
            JsonObject notificationJson = notification.getPayload();
            String eventType = notificationJson.getString(EVENT_TYPE);
            JsonObject payloadContext = notificationJson.getJsonObject(CONTEXT_KEY);

            if (eventType == null || payloadContext == null) {
                Log.debugf("Skipping Application Services aggregation: eventType=%s, hasContext=%b, orgId=%s",
                    eventType, payloadContext != null, getOrgId());
                return;
            }

            if (!applicationServices.containsKey(BASE_URL_KEY) && payloadContext.containsKey(BASE_URL_KEY)) {
                applicationServices.put(BASE_URL_KEY, payloadContext.getString(BASE_URL_KEY));
            }

            if (!products.containsKey(eventType)) {
                String family = payloadContext.getString(FAMILY_KEY);
                if (family == null) {
                    Log.debugf("Skipping Application Services product initialization: missing family for eventType=%s, orgId=%s",
                        eventType, getOrgId());
                    return;
                }
                JsonObject eventTypeObject = new JsonObject();
                eventTypeObject.put(DESCRIPTION_KEY, family);
                eventTypeObject.put(PAYLOADS_KEY, new JsonArray());
                products.put(eventType, eventTypeObject);
            }

            JsonArray events = notificationJson.getJsonArray(EVENTS_KEY);
            if (events == null) {
                Log.debugf("Skipping Application Services aggregation: no events array for eventType=%s, orgId=%s",
                    eventType, getOrgId());
                return;
            }

            int releasesCount = applicationServices.getInteger(GLOBAL_RELEASES_NUMBER_KEY, 0);
            JsonArray targetPayloads = products.getJsonObject(eventType).getJsonArray(PAYLOADS_KEY);
            for (int i = 0; i < events.size(); i++) {
                JsonObject event = events.getJsonObject(i);
                if (event == null) {
                    continue;
                }
                JsonObject payload = event.getJsonObject(PAYLOAD_KEY);
                if (payload != null) {
                    targetPayloads.add(payload);
                    releasesCount++;
                }
            }
            applicationServices.put(GLOBAL_RELEASES_NUMBER_KEY, releasesCount);
        } catch (Exception e) {
            Log.warnf(e, "Failed to process Application Services aggregation for orgId=%s", getOrgId());
        }
    }
}
