package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.global.exception.ApiException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

public final class GeminiContentClient {
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(requestFactory());

    public GeminiContentClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String generateContent(String prompt, String model, String apiKey) {
        String url = API_BASE_URL + "/models/" + model + ":generateContent?key=" + apiKey;
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            JsonNode root = objectMapper.readTree(restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class));
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        } catch (RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini 요청에 실패했습니다. 상태=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_REQUEST_FAILED", "Gemini 요청에 실패했습니다. 원인=" + e.getClass().getSimpleName());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_RESPONSE_PARSE_FAILED", "Gemini 응답을 해석할 수 없습니다.");
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(145_000);
        return factory;
    }
}
