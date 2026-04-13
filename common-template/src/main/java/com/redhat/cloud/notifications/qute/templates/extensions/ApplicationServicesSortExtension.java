package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ApplicationServicesSortExtension {

    /* Sort products map entries alphabetically by their "description" field */
    @TemplateExtension
    public static List<Map.Entry<String, Map<String, Object>>> sortByDescription(Map<String, Map<String, Object>> products) {
        if (products == null) {
            return List.of();
        }
        List<Map.Entry<String, Map<String, Object>>> entries = new ArrayList<>(products.entrySet());
        entries.sort(Comparator.comparing(entry -> extractDescription(entry.getValue())));
        return entries;
    }

    private static String extractDescription(Map<String, Object> value) {
        Object desc = value.get("description");
        return desc != null ? desc.toString() : "";
    }
}
