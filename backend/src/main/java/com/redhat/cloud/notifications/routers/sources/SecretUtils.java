package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class SecretUtils {

    /**
     * Used to gather data regarding the number of times that Sources gets
     * called.
     */
    @Inject
    MeterRegistry meterRegistry;

    @Inject
    BackendConfig backendConfig;

    @ConfigProperty(name = "sources.psk")
    String sourcesPsk;

    /**
     * Used to manage the secrets on Sources with PSK authentication.
     */
    @Inject
    @RestClient
    SourcesPskService sourcesPskService;

    /**
     * Used to manage the secrets on Sources with OIDC authentication.
     */
    @Inject
    @RestClient
    SourcesOidcService sourcesOidcService;

    private static final String SOURCES_TIMER = "sources.get.secret.request";

    /**
     * Loads the endpoint's secrets from Sources.
     * @param endpoint the endpoint to get the secrets from.
     */
    public void loadSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final Long secretTokenSourcesId = props.getSecretTokenSourcesId();
            if (secretTokenSourcesId != null) {
                Secret secret = loadSecretFromSources(endpoint, secretTokenSourcesId);
                props.setSecretToken(secret.password);
            }

            final Long bearerSourcesId = props.getBearerAuthenticationSourcesId();
            if (bearerSourcesId != null) {
                Secret secret = loadSecretFromSources(endpoint, bearerSourcesId);
                props.setBearerAuthentication(secret.password);
            }
        }
    }

    private Secret loadSecretFromSources(Endpoint endpoint, Long secretId) {

        final Timer.Sample getSecretTimer = Timer.start(this.meterRegistry);

        final Secret secret;
        if (backendConfig.isSourcesOidcAuthEnabled(endpoint.getOrgId())) {
            Log.debug("Using OIDC Sources client");
            secret = this.sourcesOidcService.getById(
                endpoint.getOrgId(),
                secretId
            );
        } else {
            Log.debug("Using PSK Sources client");
            secret = this.sourcesPskService.getById(
                endpoint.getOrgId(),
                this.sourcesPsk,
                secretId
            );
        }

        getSecretTimer.stop(this.meterRegistry.timer(SOURCES_TIMER));
        return secret;
    }

    /**
     * Creates the endpoint's secrets in Sources.
     * @param endpoint the endpoint to create the secrets from.
     */
    public void createSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final String secretToken = props.getSecretToken();
            if (secretToken != null && !secretToken.isBlank()) {
                final long id = this.createSecretTokenSecret(secretToken, Secret.TYPE_SECRET_TOKEN, endpoint.getOrgId());

                Log.infof("[secret_id: %s] Secret token secret created in Sources", id);

                props.setSecretTokenSourcesId(id);
            }

            final String bearerToken = props.getBearerAuthentication();
            if (bearerToken != null && !bearerToken.isBlank()) {
                final long id = this.createSecretTokenSecret(secretToken, Secret.TYPE_BEARER_AUTHENTICATION, endpoint.getOrgId());
                Log.infof("[secret_id: %s] Secret bearer token created in Sources", id);
                props.setBearerAuthenticationSourcesId(id);
            }
        }
    }

    /**
     * <p>Updates the endpoint's secrets in Sources. However a few cases are covered for the secrets:</p>
     * <ul>
     *  <li>If the endpoint has an ID for the secret, and the incoming secret is {@code null}, it is assumed that the
     *  user wants the secret to be deleted.</li>
     *  <li>If the endpoint has an ID for the secret, and the incoming secret isn't {@code null}, then the secret is
     *  updated.</li>
     *  <li>If the endpoint doesn't have an ID for the secret, and the incoming secret is {@code null}, it's basically
     *  a NOP — although the attempt is logged for debugging purposes.</li>
     *  <li>If the endpoint doesn't have an ID for the secret, and the incoming secret isn't {@code null}, it is
     *  assumed that the user wants the secret to be created.</li>
     * </ul>
     * @param endpoint the endpoint to update the secrets from.
     */
    public void updateSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final String secretToken = props.getSecretToken();
            final Long secretTokenId = props.getSecretTokenSourcesId();
            props.setSecretTokenSourcesId(updateSecretToken(endpoint, secretToken, secretTokenId, Secret.TYPE_SECRET_TOKEN, "Secret token secret"));

            final String bearerToken = props.getBearerAuthentication();
            final Long bearerTokenId = props.getBearerAuthenticationSourcesId();
            props.setBearerAuthenticationSourcesId(updateSecretToken(endpoint, bearerToken, bearerTokenId, Secret.TYPE_BEARER_AUTHENTICATION, "Bearer token"));
        }
    }

    private Long updateSecretToken(Endpoint endpoint, String password, Long secretId, String secretType, String logDisplaySecretType) {
        if (secretId != null) {
            if (password == null || password.isBlank()) {
                if (backendConfig.isSourcesOidcAuthEnabled(endpoint.getOrgId())) {
                    Log.debug("Using OIDC Sources client");
                    this.sourcesOidcService.delete(
                        endpoint.getOrgId(),
                        secretId
                    );
                } else {
                    Log.debug("Using PSK Sources client");
                    this.sourcesPskService.delete(
                        endpoint.getOrgId(),
                        this.sourcesPsk,
                        secretId
                    );
                }
                Log.infof("[endpoint_id: %s][secret_id: %s] %s deleted in Sources during an endpoint update operation", endpoint.getId(), secretId, logDisplaySecretType);
                return null;
            } else {
                Secret secret = new Secret();
                secret.password = password;

                if (backendConfig.isSourcesOidcAuthEnabled(endpoint.getOrgId())) {
                    Log.debug("Using OIDC Sources client");
                    this.sourcesOidcService.update(
                        endpoint.getOrgId(),
                        secretId,
                        secret
                    );
                } else {
                    Log.debug("Using PSK Sources client");
                    this.sourcesPskService.update(
                        endpoint.getOrgId(),
                        this.sourcesPsk,
                        secretId,
                        secret
                    );
                }
                Log.infof("[endpoint_id: %s][secret_id: %s] %s updated in Sources", endpoint.getId(), secretId, logDisplaySecretType);
                return secretId;
            }
        } else {
            if (password == null || password.isBlank()) {
                Log.debugf("[endpoint_id: %s] %s not created in Sources: the secret token object is null or blank", endpoint.getId(), logDisplaySecretType);
            } else {
                final long id = this.createSecretTokenSecret(password, secretType, endpoint.getOrgId());
                Log.infof("[endpoint_id: %s][secret_id: %s] %s created in Sources during an endpoint update operation", endpoint.getId(), id, logDisplaySecretType);
                return id;
            }
        }
        return secretId;
    }

    /**
     * Deletes the endpoint's secrets. It requires for the properties to have a "secret token" ID on the database.
     * @param endpoint the endpoint to delete the secrets from.
     */
    public void deleteSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final Long secretTokenId = props.getSecretTokenSourcesId();
            if (secretTokenId != null) {
                deleteSecret(endpoint, secretTokenId, "[endpoint_id: %s][secret_id: %s] Secret token secret deleted in Sources");
            }

            final Long bearerSourcesId = props.getBearerAuthenticationSourcesId();
            if (bearerSourcesId != null) {
                deleteSecret(endpoint, bearerSourcesId, "[endpoint_id: %s][secret_id: %s] Bearer token deleted in Sources");
            }
        }
    }

    private void deleteSecret(Endpoint endpoint, Long secretId, String logMessageFormat) {
        if (backendConfig.isSourcesOidcAuthEnabled(endpoint.getOrgId())) {
            Log.debug("Using OIDC Sources client");
            this.sourcesOidcService.delete(
                endpoint.getOrgId(),
                secretId
            );
        } else {
            Log.debug("Using PSK Sources client");
            this.sourcesPskService.delete(
                endpoint.getOrgId(),
                this.sourcesPsk,
                secretId
            );
        }
        Log.infof(logMessageFormat, endpoint.getId(), secretId);
    }

    /**
     * Creates a "secret token" secret in Sources.
     * @param secretToken the "secret token"'s contents.
     * @param orgId the organization id related to this operation for the tenant identification.
     * @return the id of the created secret.
     */
    private long createSecretTokenSecret(final String secretToken, final String tokenType, final String orgId) {
        Secret secret = new Secret();

        secret.authenticationType = tokenType;
        secret.password = secretToken;

        return createSecret(orgId, secret);
    }

    private Long createSecret(String orgId, Secret secret) {
        final Secret createdSecret;
        if (backendConfig.isSourcesOidcAuthEnabled(orgId)) {
            Log.debug("Using OIDC Sources client");
            createdSecret = this.sourcesOidcService.create(
                orgId,
                secret
            );
        } else {
            Log.debug("Using PSK Sources client");
            createdSecret = this.sourcesPskService.create(
                orgId,
                this.sourcesPsk,
                secret
            );
        }

        return createdSecret.id;
    }
}
