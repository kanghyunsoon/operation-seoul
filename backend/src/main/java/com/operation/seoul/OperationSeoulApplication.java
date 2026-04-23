package com.operation.seoul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//http://localhost:8080/api/v1/regions/1/missions db 로컬호스트 주소
@SpringBootApplication
public class OperationSeoulApplication {
    public static void main(String[] args) {
        SpringApplication.run(OperationSeoulApplication.class, args);
   }
}