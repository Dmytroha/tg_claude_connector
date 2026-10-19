package io.github.dmytroha.tgconnector.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Deleting old messages.
 *
 * @param maxAge messages posted longer ago than this are deleted
 * @param cron   Spring cron expression for the daily clean-up
 * @param zone   time zone of {@code cron}
 */
@ConfigurationProperties(prefix = "connector.retention")
public record RetentionProperties(@DefaultValue("true") boolean enabled,
                                  @DefaultValue("90d") Duration maxAge,
                                  @DefaultValue("0 30 4 * * *") String cron,
                                  @DefaultValue("Europe/Prague") String zone) {
}
