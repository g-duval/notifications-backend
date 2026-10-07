package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class DigestSubscriptionRequest {

    @NotNull
    @JsonProperty("scheduled_execution_time")
    private LocalTime scheduledExecutionTime;

    @JsonProperty("scheduled_execution_day")
    private DayOfWeek scheduledExecutionDay;

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
}
