package io.kontur.insightsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import io.sentry.Sentry;

@SpringBootApplication
@EnableAsync
@EnableRetry
public class InsightsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightsApiApplication.class, args);
    }

}
