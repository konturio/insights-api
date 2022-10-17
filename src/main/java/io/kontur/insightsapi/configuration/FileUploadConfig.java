package io.kontur.insightsapi.configuration;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileUploadConfig {

    @Bean
    public ServletFileUpload servletFileUpload() {
        return new ServletFileUpload();
    }
}
