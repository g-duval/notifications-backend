package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.v2.MessageConsumer.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class MessageConsumerTest {

    @Inject
    MessageConsumer messageConsumer;

    @Inject
    ConnectorConfig connectorConfig;

    @Test
    void testNullCloudEventData() {
        IncomingCloudEventMetadata<JsonObject> cloudEvent = mock(IncomingCloudEventMetadata.class);
        when(cloudEvent.getData()).thenReturn(null);

        Headers headers = new RecordHeaders()
            .add(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorConfig.getConnectorName().getBytes(UTF_8));

        OutgoingKafkaRecordMetadata<String> kafkaHeaders = OutgoingKafkaRecordMetadata.<String>builder()
            .withHeaders(headers)
            .build();

        Message<JsonObject> message = Message.of(new JsonObject())
            .addMetadata(kafkaHeaders)
            .addMetadata(cloudEvent);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            messageConsumer.processMessage(message)
        );
        assertEquals("Incoming CloudEvent data must not be null", exception.getMessage());
    }
}
