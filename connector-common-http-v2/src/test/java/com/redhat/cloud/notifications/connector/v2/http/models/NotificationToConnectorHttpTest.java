package com.redhat.cloud.notifications.connector.v2.http.models;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class NotificationToConnectorHttpTest {

    @Inject
    Validator validator;

    @Test
    void testValidNotificationToConnectorHttp() {
        // Given - valid object with all required fields
        NotificationToConnectorHttp notification = new NotificationToConnectorHttp();

        NotificationToConnectorHttp.EndpointProperties endpointProperties = new NotificationToConnectorHttp.EndpointProperties();
        endpointProperties.setTargetUrl("https://example.com/webhook");
        notification.setEndpointProperties(endpointProperties);

        JsonObject payload = new JsonObject().put("message", "test");
        notification.setPayload(payload);

        // When - validate
        Set<ConstraintViolation<NotificationToConnectorHttp>> violations = validator.validate(notification);

        // Then - no violations
        assertTrue(violations.isEmpty(), "Valid object should not have validation violations");
    }

    @Test
    void testNullEndpointProperties() {
        // Given - notification with null endpointProperties
        NotificationToConnectorHttp notification = new NotificationToConnectorHttp();
        notification.setEndpointProperties(null);

        JsonObject payload = new JsonObject().put("message", "test");
        notification.setPayload(payload);

        // When - validate
        Set<ConstraintViolation<NotificationToConnectorHttp>> violations = validator.validate(notification);

        // Then - should have one violation
        assertEquals(1, violations.size(), "Should have exactly one violation for null endpointProperties");
        ConstraintViolation<NotificationToConnectorHttp> violation = violations.iterator().next();
        assertEquals("endpointProperties", violation.getPropertyPath().toString());
        assertEquals("must not be null", violation.getMessage());
    }

    @Test
    void testNullPayload() {
        // Given - notification with null payload
        NotificationToConnectorHttp notification = new NotificationToConnectorHttp();

        NotificationToConnectorHttp.EndpointProperties endpointProperties = new NotificationToConnectorHttp.EndpointProperties();
        endpointProperties.setTargetUrl("https://example.com/webhook");
        notification.setEndpointProperties(endpointProperties);

        notification.setPayload(null);

        // When - validate
        Set<ConstraintViolation<NotificationToConnectorHttp>> violations = validator.validate(notification);

        // Then - should have one violation
        assertEquals(1, violations.size(), "Should have exactly one violation for null payload");
        ConstraintViolation<NotificationToConnectorHttp> violation = violations.iterator().next();
        assertEquals("payload", violation.getPropertyPath().toString());
        assertEquals("must not be null", violation.getMessage());
    }

    @Test
    void testNullTargetUrl() {
        // Given - notification with null targetUrl in endpointProperties
        NotificationToConnectorHttp notification = new NotificationToConnectorHttp();

        NotificationToConnectorHttp.EndpointProperties endpointProperties = new NotificationToConnectorHttp.EndpointProperties();
        endpointProperties.setTargetUrl(null);
        notification.setEndpointProperties(endpointProperties);

        JsonObject payload = new JsonObject().put("message", "test");
        notification.setPayload(payload);

        // When - validate
        Set<ConstraintViolation<NotificationToConnectorHttp>> violations = validator.validate(notification);

        // Then - no violations (nested validation requires @Valid annotation on endpointProperties field)
        // Note: To enable validation of targetUrl, add @Valid annotation to the endpointProperties field
        assertTrue(violations.isEmpty(),
            "Nested field validation does not occur without @Valid annotation on parent field");
    }

    @Test
    void testMultipleNullFields() {
        // Given - notification with multiple null fields
        NotificationToConnectorHttp notification = new NotificationToConnectorHttp();
        notification.setEndpointProperties(null);
        notification.setPayload(null);

        // When - validate
        Set<ConstraintViolation<NotificationToConnectorHttp>> violations = validator.validate(notification);

        // Then - should have two violations
        assertEquals(2, violations.size(), "Should have two violations for null endpointProperties and payload");

        boolean hasEndpointPropertiesViolation = violations.stream()
            .anyMatch(v -> "endpointProperties".equals(v.getPropertyPath().toString()));
        boolean hasPayloadViolation = violations.stream()
            .anyMatch(v -> "payload".equals(v.getPropertyPath().toString()));

        assertTrue(hasEndpointPropertiesViolation, "Should have violation for endpointProperties");
        assertTrue(hasPayloadViolation, "Should have violation for payload");
    }

    @Test
    void testAuthenticationIsOptional() {
        // Given - valid object without authentication (optional field)
        NotificationToConnectorHttp notification = new NotificationToConnectorHttp();

        NotificationToConnectorHttp.EndpointProperties endpointProperties = new NotificationToConnectorHttp.EndpointProperties();
        endpointProperties.setTargetUrl("https://example.com/webhook");
        notification.setEndpointProperties(endpointProperties);

        JsonObject payload = new JsonObject().put("message", "test");
        notification.setPayload(payload);

        notification.setAuthentication(null); // Authentication is optional

        // When - validate
        Set<ConstraintViolation<NotificationToConnectorHttp>> violations = validator.validate(notification);

        // Then - no violations (authentication is optional)
        assertTrue(violations.isEmpty(), "Should not have violations when authentication is null");
    }
}
