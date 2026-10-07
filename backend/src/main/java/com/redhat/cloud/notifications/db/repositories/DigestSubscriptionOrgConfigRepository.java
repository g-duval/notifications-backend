package com.redhat.cloud.notifications.db.repositories;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.redhat.cloud.notifications.models.DigestSubscriptionOrgConfig;
import com.redhat.cloud.notifications.models.DigestSubscriptionOrgConfigId;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@ApplicationScoped
public class DigestSubscriptionOrgConfigRepository {

    private static final CronParser CRON_PARSER = new CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );

    @Inject
    EntityManager entityManager;

    @Transactional
    public void createOrUpdateDigestPreference(String orgId, SubscriptionType subscriptionType, LocalTime scheduledExecutionTime, DayOfWeek scheduledExecutionDay) {
        String cronExpression = buildCronExpression(subscriptionType, scheduledExecutionTime, scheduledExecutionDay);
        DigestSubscriptionOrgConfigId id = new DigestSubscriptionOrgConfigId(orgId, subscriptionType);
        DigestSubscriptionOrgConfig config = entityManager.find(DigestSubscriptionOrgConfig.class, id);

        LocalDateTime nextRun = computeNextRun(cronExpression);

        if (config != null) {
            config.setCronExpression(cronExpression);
            config.setNextRun(nextRun);
            entityManager.merge(config);
        } else {
            config = new DigestSubscriptionOrgConfig(id, cronExpression);
            config.setNextRun(nextRun);
            entityManager.persist(config);
        }
    }

    public DigestSubscriptionOrgConfig findDigestSubscriptionOrgConfig(String orgId, SubscriptionType subscriptionType) {
        DigestSubscriptionOrgConfigId id = new DigestSubscriptionOrgConfigId(orgId, subscriptionType);
        return entityManager.find(DigestSubscriptionOrgConfig.class, id);
    }

    public static String buildCronExpression(SubscriptionType subscriptionType, LocalTime time, DayOfWeek day) {
        int minute = time.getMinute();
        int hour = time.getHour();
        if (subscriptionType == SubscriptionType.WEEKLY) {
            if (day == null) {
                throw new IllegalArgumentException("day is required for WEEKLY subscriptions");
            }
            return minute + " " + hour + " * * " + toCronDay(day);
        } else if (subscriptionType != SubscriptionType.DAILY) {
            throw new IllegalArgumentException("Unsupported subscription type for digest scheduling: " + subscriptionType);
        }
        return minute + " " + hour + " * * *";
    }

    public static LocalTime parseTimeFromCron(String cronExpression) {
        String[] parts = cronExpression.split(" ");
        return LocalTime.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
    }

    public static DayOfWeek parseDayFromCron(String cronExpression) {
        String[] parts = cronExpression.split(" ");
        String dayField = parts[4];
        if ("*".equals(dayField)) {
            return null;
        }
        return fromCronDay(Integer.parseInt(dayField));
    }

    // cron: 0=Sunday, 1=Monday ... 6=Saturday
    private static int toCronDay(DayOfWeek day) {
        return day.getValue() % 7;
    }

    // cron: 0=Sunday, 1=Monday ... 6=Saturday
    private static DayOfWeek fromCronDay(int cronDay) {
        if (cronDay == 0) {
            return DayOfWeek.SUNDAY;
        }
        return DayOfWeek.of(cronDay);
    }

    private static LocalDateTime computeNextRun(String cronExpression) {
        ExecutionTime executionTime = ExecutionTime.forCron(
            CRON_PARSER.parse(cronExpression).validate()
        );
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun = executionTime.nextExecution(now)
            .map(ZonedDateTime::toLocalDateTime)
            .orElse(null);
        if (nextRun == null) {
            Log.warnf("Could not compute next execution for cron expression '%s'", cronExpression);
        }
        return nextRun;
    }
}
