package com.operation.seoul;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@SpringBootApplication
public class OperationSeoulApplication {

    /**
     * Backend application entry point.
     * The local profile is used by default when no explicit profile is provided.
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OperationSeoulApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.profiles.default", "local",
                "spring.sql.init.mode", "always",
                "spring.sql.init.encoding", "UTF-8",
                "mybatis.configuration.map-underscore-to-camel-case", "true"
        ));
        application.run(args);
    }

    /** Shared JSON mapper for external AI responses and internal DTO conversion. */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}