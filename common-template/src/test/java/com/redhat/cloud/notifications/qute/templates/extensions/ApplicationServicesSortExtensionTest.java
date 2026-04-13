package com.redhat.cloud.notifications.qute.templates.extensions;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationServicesSortExtensionTest {

    @Test
    void testSortByDescriptionNullInput() {
        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByDescription(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSortByDescription() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();
        products.put("keycloak-releases", Map.of("description", "Red Hat build of Keycloak", "payloads", List.of()));
        products.put("eap-releases", Map.of("description", "Red Hat JBoss Enterprise Application Platform", "payloads", List.of()));
        products.put("amq-releases", Map.of("description", "AMQ Broker", "payloads", List.of()));

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByDescription(products);

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

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByDescription(products);

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

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByDescription(products);

        assertEquals(1, result.size());
        assertEquals("only-product", result.get(0).getKey());
    }

    @Test
    void testSortByDescriptionEmptyMap() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();

        List<Map.Entry<String, Map<String, Object>>> result = ApplicationServicesSortExtension.sortByDescription(products);

        assertEquals(0, result.size());
    }

    @Test
    void testSortByDescriptionDoesNotMutateOriginalMap() {
        Map<String, Map<String, Object>> products = new LinkedHashMap<>();
        products.put("keycloak-releases", Map.of("description", "Red Hat build of Keycloak"));
        products.put("eap-releases", Map.of("description", "Red Hat JBoss Enterprise Application Platform"));
        products.put("amq-releases", Map.of("description", "AMQ Broker"));

        List<String> originalOrder = List.copyOf(products.keySet());

        ApplicationServicesSortExtension.sortByDescription(products);

        // Original map key order should be unchanged
        assertEquals(originalOrder, List.copyOf(products.keySet()));
    }
}
