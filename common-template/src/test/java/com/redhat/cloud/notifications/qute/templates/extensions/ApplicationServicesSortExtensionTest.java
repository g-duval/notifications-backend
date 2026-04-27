package com.redhat.cloud.notifications.qute.templates.extensions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationServicesSortExtensionTest {

    @Test
    void testSortByDescriptionNullInput() {
        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByProductDescription(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSortByDescription() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();
        products.put("keycloak-releases", Map.of("description", "Red Hat build of Keycloak", "payloads", List.of()));
        products.put("eap-releases", Map.of("description", "Red Hat JBoss Enterprise Application Platform", "payloads", List.of()));
        products.put("amq-releases", Map.of("description", "AMQ Broker", "payloads", List.of()));

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByProductDescription(products);

        assertEquals(3, result.size());
        assertEquals("amq-releases", result.get(0).getKey());
        assertEquals("eap-releases", result.get(1).getKey());
        assertEquals("keycloak-releases", result.get(2).getKey());
    }

    @Test
    void testSortByDescriptionWithMissingDescription() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();
        products.put("product-b", Map.of("description", "Bravo"));
        products.put("product-a", Map.of("payloads", List.of()));
        products.put("product-c", Map.of("description", "Alpha"));

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByProductDescription(products);

        assertEquals(3, result.size());
        // Missing description sorts as empty string, so it comes first
        assertEquals("product-a", result.get(0).getKey());
        assertEquals("product-c", result.get(1).getKey());
        assertEquals("product-b", result.get(2).getKey());
    }

    @Test
    void testSortByDescriptionSingleProduct() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();
        products.put("only-product", Map.of("description", "Only Product"));

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByProductDescription(products);

        assertEquals(1, result.size());
        assertEquals("only-product", result.get(0).getKey());
    }

    @Test
    void testSortByDescriptionEmptyMap() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByProductDescription(products);

        assertEquals(0, result.size());
    }

    @Test
    void testSortByDescriptionDoesNotMutateOriginalMap() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();
        products.put("keycloak-releases", Map.of("description", "Red Hat build of Keycloak"));
        products.put("eap-releases", Map.of("description", "Red Hat JBoss Enterprise Application Platform"));
        products.put("amq-releases", Map.of("description", "AMQ Broker"));

        List<String> originalOrder = List.copyOf(products.keySet());

        ApplicationServicesSortExtension.sortByProductDescription(products);

        // Original map key order should be unchanged
        assertEquals(originalOrder, List.copyOf(products.keySet()));
    }

    @Test
    void testSortByDisplayNameNullInput() {
        List<Map<String, Object>> result = ApplicationServicesSortExtension.sortByEventTypeDisplayName(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSortByDisplayName() {
        List<Map<String, Object>> eventTypes = new ArrayList<>();
        eventTypes.add(Map.of("display_name", "Policies", "name", "policies"));
        eventTypes.add(Map.of("display_name", "Advisor", "name", "advisor"));
        eventTypes.add(Map.of("display_name", "Vulnerability", "name", "vulnerability"));

        List<Map<String, Object>> result = ApplicationServicesSortExtension.sortByEventTypeDisplayName(eventTypes);

        assertEquals(3, result.size());
        assertEquals("Advisor", result.get(0).get("display_name"));
        assertEquals("Policies", result.get(1).get("display_name"));
        assertEquals("Vulnerability", result.get(2).get("display_name"));
    }

    @Test
    void testSortByDisplayNameWithMissingDisplayName() {
        List<Map<String, Object>> eventTypes = new ArrayList<>();
        eventTypes.add(Map.of("display_name", "Drift", "name", "drift"));
        eventTypes.add(Map.of("name", "no-display-name"));
        eventTypes.add(Map.of("display_name", "Advisor", "name", "advisor"));

        List<Map<String, Object>> result = ApplicationServicesSortExtension.sortByEventTypeDisplayName(eventTypes);

        assertEquals(3, result.size());
        assertEquals("no-display-name", result.get(0).get("name"));
        assertEquals("Advisor", result.get(1).get("display_name"));
        assertEquals("Drift", result.get(2).get("display_name"));
    }

    @Test
    void testSortByDisplayNameSingleElement() {
        List<Map<String, Object>> eventTypes = new ArrayList<>();
        eventTypes.add(Map.of("display_name", "Only Event Type"));

        List<Map<String, Object>> result = ApplicationServicesSortExtension.sortByEventTypeDisplayName(eventTypes);

        assertEquals(1, result.size());
        assertEquals("Only Event Type", result.get(0).get("display_name"));
    }

    @Test
    void testSortByDisplayNameEmptyList() {
        List<Map<String, Object>> result = ApplicationServicesSortExtension.sortByEventTypeDisplayName(new ArrayList<>());
        assertEquals(0, result.size());
    }

    @Test
    void testSortByDisplayNameDoesNotMutateOriginalList() {
        List<Map<String, Object>> eventTypes = new ArrayList<>();
        eventTypes.add(Map.of("display_name", "Policies"));
        eventTypes.add(Map.of("display_name", "Advisor"));
        eventTypes.add(Map.of("display_name", "Vulnerability"));

        List<String> originalOrder = eventTypes.stream()
            .map(e -> (String) e.get("display_name"))
            .toList();

        ApplicationServicesSortExtension.sortByEventTypeDisplayName(eventTypes);

        List<String> orderAfterSort = eventTypes.stream()
            .map(e -> (String) e.get("display_name"))
            .toList();
        assertEquals(originalOrder, orderAfterSort);
    }
}
