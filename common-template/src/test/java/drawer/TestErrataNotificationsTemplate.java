package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static helpers.ErrataTestHelpers.BUGFIX_ERRATA;
import static helpers.ErrataTestHelpers.ENHANCEMENT_ERRATA;
import static helpers.ErrataTestHelpers.SECURITY_ERRATA;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestErrataNotificationsTemplate {

    private static final Action ACTION = ErrataTestHelpers.createErrataAction();

    @Inject
    TemplateService templateService;

    @ValueSource(strings = { BUGFIX_ERRATA, SECURITY_ERRATA, ENHANCEMENT_ERRATA })
    @ParameterizedTest
    void testRenderedErrataTemplates(final String eventType) {
        String result = renderTemplate(eventType, ACTION);
        checkResult(eventType, result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "subscription-services", "errata-notifications", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }

    private void checkResult(String eventType, String result) {
        switch (eventType) {
            case BUGFIX_ERRATA -> assertEquals("Red Hat published new bugfix errata that affect your products. Explore these and others in the errata search.", result);
            case SECURITY_ERRATA -> assertEquals("Red Hat published new security errata that affect your products. Explore these and others in the errata search.", result);
            case ENHANCEMENT_ERRATA -> assertEquals("Red Hat published new enhancement errata that affect your products. Explore these and others in the errata search.", result);
            default -> throw new IllegalArgumentException(eventType + "is not a valid event type");
        }
    }
}
