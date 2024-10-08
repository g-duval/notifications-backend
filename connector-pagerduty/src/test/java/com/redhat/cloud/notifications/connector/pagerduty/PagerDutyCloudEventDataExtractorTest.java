package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.createCloudEventData;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PagerDutyCloudEventDataExtractorTest extends CamelQuarkusTestSupport {

    @Inject
    PagerDutyCloudEventDataExtractor pagerDutyCloudEventDataExtractor;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void httpsProtocolShouldBeValid() {
        assertValidTargetUrl("https://example.com/foo?bar=baz");
    }

    @Test
    void httpsProtocolWithPortShouldBeValid() {
        assertValidTargetUrl("https://example.com:8000/foo?bar=baz");
    }

    @Test
    void httpsProtocolWithLocalhostShouldBeValid() {
        assertValidTargetUrl("https://localhost/");
    }

    @Test
    void httpsProtocolWithLocalhostAndPortShouldBeValid() {
        assertValidTargetUrl("https://localhost:1234/");
    }

    @Test
    void httpsProtocolWithIpAddressShouldBeValid() {
        assertValidTargetUrl("https://123.123.123.123/");
    }

    @Test
    void httpsProtocolWithIpAddressAndPortShouldBeValid() {
        assertValidTargetUrl("https://123.123.123.123:1234/");
    }

    @Test
    void ftpProtocolShouldBeInvalid() {
        assertInvalidTargetUrl("ftp://example.com/foo?bar=baz");
    }

    @Test
    void nonSenseUrlShouldBeInvalid() {
        assertInvalidTargetUrl("foo-bar_baz");
    }

    @Test
    void testExtractWithValidTargetUrlPath() {
        Exchange exchange = createExchangeWithBody(context, "I am not used!");
        JsonObject cloudEventData = createCloudEventData("https://foo.bar");
        /*
         * The 'extract' method will modify 'cloudEventData'.
         * We need to run assertions on the original JsonObject, so we're making a copy of it.
         */
        JsonObject cloudEventDataCopy = cloudEventData.copy();
        pagerDutyCloudEventDataExtractor.extract(exchange, cloudEventData);

        JsonObject expectedAuthentication = cloudEventDataCopy.getJsonObject("authentication");
        assertEquals(expectedAuthentication.getString("type"), exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class).name());
        assertEquals(expectedAuthentication.getLong("secretId"), exchange.getProperty(SECRET_ID, Long.class));
    }

    private void assertValidTargetUrl(String url) {
        Exchange exchange = createExchangeWithBody(context, "I am not used!");
        JsonObject cloudEventData = createCloudEventData(url);
        assertDoesNotThrow(() -> pagerDutyCloudEventDataExtractor.extract(exchange, cloudEventData));
    }

    private void assertInvalidTargetUrl(String url) {
        Exchange exchange = createExchangeWithBody(context, "I am not used!");
        JsonObject cloudEventData = createCloudEventData(url);
        assertThrows(IllegalArgumentException.class, () -> pagerDutyCloudEventDataExtractor.extract(exchange, cloudEventData));
    }
}
