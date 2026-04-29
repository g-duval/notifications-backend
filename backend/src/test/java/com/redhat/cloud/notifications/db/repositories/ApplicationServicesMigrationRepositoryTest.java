package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.db.repositories.ApplicationServicesMigrationRepository.APPLICATION_SERVICES_APPLICATION_NAME;
import static com.redhat.cloud.notifications.db.repositories.ApplicationServicesMigrationRepository.APPLICATION_SERVICES_BUNDLE_NAME;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApplicationServicesMigrationRepositoryTest extends DbIsolatedTest {
    @Inject
    ApplicationServicesMigrationRepository applicationServicesMigrationRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Test
    void testFindApplicationServicesEventTypes() {
        final Bundle bundle = resourceHelpers.createBundle(APPLICATION_SERVICES_BUNDLE_NAME);
        final Application application = resourceHelpers.createApplication(bundle.getId(), APPLICATION_SERVICES_APPLICATION_NAME);

        final List<EventType> appServicesEventTypes = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            appServicesEventTypes.add(
                resourceHelpers.createEventType(application.getId(), String.format("app-services-%s", UUID.randomUUID()))
            );
        }

        final Application anotherApplication = resourceHelpers.createApplication(bundle.getId());
        for (int i = 0; i < 10; i++) {
            resourceHelpers.createEventType(anotherApplication.getId(), String.format("other-event-type-%s", UUID.randomUUID()));
        }

        final List<EventType> resultEventTypes = applicationServicesMigrationRepository.findApplicationServicesEventTypes();

        Assertions.assertEquals(5, resultEventTypes.size(), "we only inserted 5 Application Services event types for the test, but a different number of them were found");

        final List<UUID> expectedIds = appServicesEventTypes.stream().map(EventType::getId).toList();
        for (final EventType eventType : resultEventTypes) {
            Assertions.assertTrue(expectedIds.contains(eventType.getId()), "the function under test fetched an event type which is not of the Application Services type");
        }
    }

    @Test
    @Transactional
    void testSaveApplicationServicesSubscriptions() {
        final Bundle bundle = resourceHelpers.createBundle(APPLICATION_SERVICES_BUNDLE_NAME);
        final Application application = resourceHelpers.createApplication(bundle.getId(), APPLICATION_SERVICES_APPLICATION_NAME);
        final EventType eventTypeRhbk = resourceHelpers.createEventType(application.getId(), "rhbk");
        final EventType eventTypeAppplatform = resourceHelpers.createEventType(application.getId(), "appplatform");
        final EventType eventTypeDataGrid = resourceHelpers.createEventType(application.getId(), "data-grid");

        applicationServicesMigrationRepository.saveApplicationServicesSubscription("username1", "orgId1", eventTypeRhbk);
        applicationServicesMigrationRepository.saveApplicationServicesSubscription("username1", "orgId1", eventTypeAppplatform);
        applicationServicesMigrationRepository.saveApplicationServicesSubscription("username2", "orgId2", eventTypeDataGrid);

        final List<EventTypeEmailSubscription> user1Subscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("orgId1", "username1");
        Assertions.assertEquals(2, user1Subscriptions.size(), "unexpected number of subscriptions created for username1");

        for (final EventTypeEmailSubscription emailSubscription : user1Subscriptions) {
            Assertions.assertEquals("username1", emailSubscription.getUserId());
            Assertions.assertEquals("orgId1", emailSubscription.getOrgId());
            Assertions.assertTrue(
                emailSubscription.getEventType().equals(eventTypeRhbk) || emailSubscription.getEventType().equals(eventTypeAppplatform),
                "unexpected event type for username1"
            );
        }

        final List<EventTypeEmailSubscription> user2Subscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("orgId2", "username2");
        Assertions.assertEquals(1, user2Subscriptions.size(), "unexpected number of subscriptions created for username2");
        Assertions.assertEquals("username2", user2Subscriptions.get(0).getUserId());
        Assertions.assertEquals("orgId2", user2Subscriptions.get(0).getOrgId());
        Assertions.assertEquals(eventTypeDataGrid, user2Subscriptions.get(0).getEventType());
    }

    @Test
    @Transactional
    void testDuplicatedInsertionsDoNothing() {
        final Bundle bundle = resourceHelpers.createBundle(APPLICATION_SERVICES_BUNDLE_NAME);
        final Application application = resourceHelpers.createApplication(bundle.getId(), APPLICATION_SERVICES_APPLICATION_NAME);
        final EventType eventType = resourceHelpers.createEventType(application.getId(), "rhbk");

        final String username = "username";
        final String orgId = "orgId";

        for (int i = 0; i < 5; i++) {
            applicationServicesMigrationRepository.saveApplicationServicesSubscription(username, orgId, eventType);
        }

        final List<EventTypeEmailSubscription> createdSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        Assertions.assertEquals(1, createdSubscriptions.size());
    }
}
