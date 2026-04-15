package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestApplicationServicesTemplate extends EmailTemplatesRendererHelper {

    @Override
    protected String getBundle() {
        return "subscription-services";
    }

    @Override
    protected String getApp() {
        return "application-services";
    }

    @Override
    protected String getBundleDisplayName() {
        return "Subscription Services";
    }

    @Override
    protected String getAppDisplayName() {
        return "Application Services";
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testDailyDigestEmailBody(boolean useBetaTemplate) throws JsonProcessingException {
        final String result = generateAggregatedEmailBody(JSON_APP_SERVICES_DEFAULT_AGGREGATION_CONTEXT, useBetaTemplate);

        // Verify section links with descriptions and counts
        assertTrue(result.contains("Red Hat build of Keycloak (2)"));
        assertTrue(result.contains("Red Hat JBoss Enterprise Application Platform (3)"));

        // Verify section anchors
        assertTrue(result.contains("application-services-section1-1"));
        assertTrue(result.contains("application-services-section1-2"));

        // Verify release counts in section bodies
        assertTrue(result.contains("2 "), "Keycloak section should show 2 releases");
        assertTrue(result.contains("3 "), "EAP section should show 3 releases");
        assertTrue(result.contains("releases affecting your subscriptions."));

        // Verify base_url is used in payload links
        assertEquals(5, StringUtils.countMatches(result, "https://access.redhat.com/jbossnetwork/restricted/softwareDetail.html?softwareId="));

        // Verify payload data is rendered in the tables
        assertTrue(result.contains("Red Hat build of Keycloak 26.2.13 Maven Repository"));
        assertTrue(result.contains("Red Hat build of Keycloak 26.2.13 Server"));
        assertTrue(result.contains("Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Installation Manager"));
        assertTrue(result.contains("Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Source Code"));
        assertTrue(result.contains("Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Maven Repository"));

        // Verify product versions are rendered
        assertTrue(result.contains("26.2.13"));
        assertTrue(result.contains("8.1"));

        // Verify HCC logo
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyDigestSortsProductsByDescription() throws JsonProcessingException {
        // Products are in reverse alphabetical order by description to verify sorting
        String json = "{" +
            "\"application-services\":{" +
            "  \"global_releases_number\":2," +
            "  \"products\":{" +
            "    \"zzz-product\":{\"description\":\"Zebra Product\",\"payloads\":[{\"id\":\"1\",\"description\":\"Zebra Release\",\"version\":\"1.0\"}]}," +
            "    \"aaa-product\":{\"description\":\"Alpha Product\",\"payloads\":[{\"id\":\"2\",\"description\":\"Alpha Release\",\"version\":\"2.0\"}]}" +
            "  }" +
            "}," +
            "\"start_time\":null,\"end_time\":null" +
            "}";

        final String result = generateAggregatedEmailBody(json, false);

        int alphaPos = result.indexOf("Alpha Product");
        int zebraPos = result.indexOf("Zebra Product");
        assertTrue(alphaPos > -1, "Alpha Product should appear in output");
        assertTrue(zebraPos > -1, "Zebra Product should appear in output");
        assertTrue(alphaPos < zebraPos, "Products should be sorted alphabetically by description");
    }

    @Test
    public void testDailyDigestHidesProductsWithNoPayloads() throws JsonProcessingException {
        String json = "{" +
            "\"application-services\":{" +
            "  \"global_releases_number\":1," +
            "  \"products\":{" +
            "    \"keycloak-releases\":{\"description\":\"Red Hat build of Keycloak\",\"payloads\":[]}," +
            "    \"eap-releases\":{\"description\":\"Red Hat JBoss Enterprise Application Platform\",\"payloads\":[{\"id\":\"1\",\"description\":\"EAP Release\",\"version\":\"8.0\"}]}" +
            "  }" +
            "}," +
            "\"start_time\":null,\"end_time\":null" +
            "}";

        final String result = generateAggregatedEmailBody(json, false);

        assertFalse(result.contains("Red Hat build of Keycloak (0)"), "Products with empty payloads should not appear in links");
        assertTrue(result.contains("Red Hat JBoss Enterprise Application Platform (1)"), "Products with payloads should appear");
    }

    public static final String JSON_APP_SERVICES_DEFAULT_AGGREGATION_CONTEXT = "{" +
        "   \"application-services\":{" +
        "      \"global_releases_number\":5," +
        "      \"products\":{" +
        "         \"keycloak-releases\":{" +
        "            \"description\":\"Red Hat build of Keycloak\"," +
        "            \"payloads\":[" +
        "               {" +
        "                  \"id\":\"108766\"," +
        "                  \"description\":\"Red Hat build of Keycloak 26.2.13 Maven Repository\"," +
        "                  \"version\":\"26.2.13\"" +
        "               }," +
        "               {" +
        "                  \"id\":\"108767\"," +
        "                  \"description\":\"Red Hat build of Keycloak 26.2.13 Server\"," +
        "                  \"version\":\"26.2.13\"" +
        "               }" +
        "            ]" +
        "         }," +
        "         \"eap-releases\":{" +
        "            \"description\":\"Red Hat JBoss Enterprise Application Platform\"," +
        "            \"payloads\":[" +
        "               {" +
        "                  \"id\":\"108920\"," +
        "                  \"description\":\"Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Installation Manager\"," +
        "                  \"version\":\"8.1\"" +
        "               }," +
        "               {" +
        "                  \"id\":\"108917\"," +
        "                  \"description\":\"Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Source Code\"," +
        "                  \"version\":\"8.1\"" +
        "               }," +
        "               {" +
        "                  \"id\":\"108918\"," +
        "                  \"description\":\"Red Hat JBoss Enterprise Application Platform 8.1 Update 05 Maven Repository\"," +
        "                  \"version\":\"8.1\"" +
        "               }" +
        "            ]" +
        "         }" +
        "      }" +
        "   }," +
        "   \"start_time\":null," +
        "   \"end_time\":null" +
        "}";
}
