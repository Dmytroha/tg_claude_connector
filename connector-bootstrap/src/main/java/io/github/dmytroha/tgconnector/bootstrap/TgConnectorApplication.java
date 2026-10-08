package io.github.dmytroha.tgconnector.bootstrap;

import io.github.dmytroha.tgconnector.infrastructure.config.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.github.dmytroha.tgconnector")
@EnableScheduling
@EnableConfigurationProperties(TelegramProperties.class)
public class TgConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TgConnectorApplication.class, args);
    }
}
