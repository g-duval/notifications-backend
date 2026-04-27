package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ApplicationServicesSortExtension {

    @TemplateExtension
    public static List<Map.Entry<String, Map<String, Object>>> sortByProductDescription(Map<String, Map<String, Object>> products) {
        if (products == null) {
            return List.of();
        }
        List<Map.Entry<String, Map<String, Object>>> entries = new ArrayList<>(products.entrySet());
        entries.sort(Comparator.comparing(entry -> extractField(entry.getValue(), "description")));
        return entries;
    }

    @TemplateExtension
    public static List<Map<String, Object>> sortByEventTypeDisplayName(List<Map<String, Object>> eventTypes) {
        if (eventTypes == null) {
            return List.of();
        }
        List<Map<String, Object>> sorted = new ArrayList<>(eventTypes);
        sorted.sort(Comparator.comparing(entry -> extractField(entry, "display_name")));
        return sorted;
    }

    private static String extractField(Map<String, Object> value, String field) {
        if (value == null) {
            return "";
        }
        Object val = value.get(field);
        return val != null ? val.toString() : "";
    }
}
