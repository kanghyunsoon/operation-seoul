package com.operation.seoul;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@SpringBootApplication
public class OperationSeoulApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OperationSeoulApplication.class);
        application.setDefaultProperties(Map.of("spring.profiles.default", "local"));
        application.run(args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
