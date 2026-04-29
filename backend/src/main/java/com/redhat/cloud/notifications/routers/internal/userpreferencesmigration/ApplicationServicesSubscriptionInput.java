package com.redhat.cloud.notifications.routers.internal.userpreferencesmigration;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplicationServicesSubscriptionInput(
    String username,
    @JsonProperty("org-id") String orgId,
    @JsonProperty("notification_category") String eventType
) {
}
