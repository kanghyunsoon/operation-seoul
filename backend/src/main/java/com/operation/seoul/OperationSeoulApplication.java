package com.operation.seoul;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@SpringBootApplication
public class OperationSeoulApplication {

    /**
     * 백엔드 애플리케이션 진입점입니다.
     * 별도 프로필을 넘기지 않으면 로컬 개발 설정을 읽도록 `local` 프로필을 기본값으로 둡니다.
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

    /**
     * 외부 AI API 응답과 내부 DTO 변환에서 공통으로 사용하는 JSON 매퍼입니다.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
