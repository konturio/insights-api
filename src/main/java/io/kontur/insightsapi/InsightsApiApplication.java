package io.kontur.insightsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableRetry
public class InsightsApiApplication extends SpringBootServletInitializer {

    /**
     * Allow running the application inside an external servlet container.
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(InsightsApiApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(InsightsApiApplication.class, args);
    }

}
