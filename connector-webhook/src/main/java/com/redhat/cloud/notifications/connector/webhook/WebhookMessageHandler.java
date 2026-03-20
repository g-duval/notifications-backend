package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.v2.MessageHandler;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.http.models.NotificationToConnectorHttp;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.util.Optional;

import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.SECRET_TOKEN;

@ApplicationScoped
public class WebhookMessageHandler extends MessageHandler {

    public static final String JSON_UTF8 = "application/json; charset=utf-8";
    public static final String X_INSIGHT_TOKEN_HEADER = "X-Insight-Token";

    @Inject
    AuthenticationLoader authenticationLoader;

    @Inject
    @RestClient
    WebhookRestClient webhookRestClient;

    @Override
    public HandledMessageDetails handle(final IncomingCloudEventMetadata<JsonObject> incomingCloudEvent) {

        NotificationToConnectorHttp notification = incomingCloudEvent.getData().mapTo(NotificationToConnectorHttp.class);
        final Optional<AuthenticationResult> authenticationResultOptional;
        try {
            authenticationResultOptional = authenticationLoader.fetchAuthenticationData(notification.getOrgId(), notification.getAuthentication());
        } catch (Exception e) {
            throw new RuntimeException("Error fetching secrets '" + e.getMessage() + "'", e);
        }

        HandledHttpMessageDetails handledMessageDetails = new HandledHttpMessageDetails();
        handledMessageDetails.targetUrl = notification.getEndpointProperties().getTargetUrl();

        if (authenticationResultOptional.isPresent()) {
            if (BEARER == authenticationResultOptional.get().authenticationType) {
                final String bearerToken = "Bearer " + authenticationResultOptional.get().password;
                try (Response response = webhookRestClient.postWithBearer(bearerToken, notification.getEndpointProperties().getTargetUrl(), notification.getPayload().encode())) {
                    handledMessageDetails.httpStatus = response.getStatus();
                }
            } else if (SECRET_TOKEN == authenticationResultOptional.get().authenticationType) {
                final String insightToken = authenticationResultOptional.get().password;
                try (Response response = webhookRestClient.postWithInsightToken(insightToken, notification.getEndpointProperties().getTargetUrl(), notification.getPayload().encode())) {
                    handledMessageDetails.httpStatus = response.getStatus();
                }
            } else {
                throw new RuntimeException("Unsupported authentication type: " + authenticationResultOptional.get().authenticationType);
            }
        } else {
            try (Response response = webhookRestClient.post(notification.getEndpointProperties().getTargetUrl(), notification.getPayload().encode())) {
                handledMessageDetails.httpStatus = response.getStatus();
            }
        }

        return handledMessageDetails;
    }
}
