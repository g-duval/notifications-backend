package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SubscriptionTypeDTO;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DigestSubscriptionResponse {

    @JsonProperty("subscription_type")
    private SubscriptionTypeDTO subscriptionType;

    @JsonProperty("scheduled_execution_time")
    private LocalTime scheduledExecutionTime;

    @JsonProperty("scheduled_execution_day")
    private DayOfWeek scheduledExecutionDay;

    @JsonProperty("next_run")
    private LocalDateTime nextRun;

    public SubscriptionTypeDTO getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(SubscriptionTypeDTO subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public LocalTime getScheduledExecutionTime() {
        return scheduledExecutionTime;
    }

    public void setScheduledExecutionTime(LocalTime scheduledExecutionTime) {
        this.scheduledExecutionTime = scheduledExecutionTime;
    }

    public DayOfWeek getScheduledExecutionDay() {
        return scheduledExecutionDay;
    }

    public void setScheduledExecutionDay(DayOfWeek scheduledExecutionDay) {
        this.scheduledExecutionDay = scheduledExecutionDay;
    }

    public LocalDateTime getNextRun() {
        return nextRun;
    }

    public void setNextRun(LocalDateTime nextRun) {
        this.nextRun = nextRun;
    }
}
