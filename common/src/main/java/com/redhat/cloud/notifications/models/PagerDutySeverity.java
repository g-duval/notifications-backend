package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Locale;

/** @apiNote For converting from a JSON string, use {@link #fromJson(String)} instead of {@link #valueOf(String)}. */
@Schema(enumeration = { "critical", "error", "warning", "info" })
public enum PagerDutySeverity {
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("error")
    ERROR,
    @JsonProperty("warning")
    WARNING,
    @JsonProperty("info")
    INFO;

    /** Parses the lowercase or uppercase severity into this enum
     *
     * @return a {@link PagerDutySeverity} constant
     */
    public static PagerDutySeverity fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ENGLISH));
    }
}

