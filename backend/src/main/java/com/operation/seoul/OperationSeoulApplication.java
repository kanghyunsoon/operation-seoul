package com.operation.seoul;

import com.fasterxml.jackson.databind.ObjectMapper; // 🚨 추가된 임포트
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; // 🚨 추가된 임포트

//http://localhost:8080/api/v1/regions/1/missions db 로컬호스트 주소
@SpringBootApplication
public class OperationSeoulApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperationSeoulApplication.class, args);
    }

    // 🚨 ObjectMapper를 스프링 빈으로 등록하여 주입 문제를 해결합니다.
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}