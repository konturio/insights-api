package io.kontur.insightsapi.configuration;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class FileUploadConfig {

    @Bean
    public ServletFileUpload servletFileUpload() {
        return new ServletFileUpload();
    }
}
