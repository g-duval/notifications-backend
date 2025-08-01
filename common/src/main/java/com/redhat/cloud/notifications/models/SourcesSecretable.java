package com.redhat.cloud.notifications.models;

/**
 * The goal of this interface is to declare the required getters and setters the target class needs to implement. This
 * will enable the class to be able to store the secrets in Sources, and to pull the secrets from it.
 */
public interface SourcesSecretable {
    /**
     * Get the contents of the secret token.
     * @return the contents of the secret token.
     */
    String getSecretToken();

    /**
     * Set the contents of the secret token.
     * @param secretToken the contents of the secret token.
     */
    void setSecretToken(String secretToken);

    /**
     * Get the ID of the "secret token" secret stored in Sources.
     * @return the ID of the secret.
     */
    Long getSecretTokenSourcesId();

    /**
     * Set the ID of the "secret token" secret stored in Sources.
     * @param secretTokenSourcesId the ID of the secret.
     */
    void setSecretTokenSourcesId(Long secretTokenSourcesId);

    Long getBearerAuthenticationSourcesId();

    void setBearerAuthenticationSourcesId(Long bearerAuthenticationSourcesId);

    String getBearerAuthentication();

    void setBearerAuthentication(String bearerAuthentication);
}
