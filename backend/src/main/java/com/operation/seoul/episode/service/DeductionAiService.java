package com.operation.seoul.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.FinalDeductionQuestion;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeductionAiService {
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(requestFactory());

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.1-flash-lite}")
    private String geminiModel;

    public Result answer(Episode episode, List<String> collectedClues, List<FinalDeductionQuestion> history, String question) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY_MISSING", "Gemini API 키가 설정되어 있지 않습니다.");
        }
        String raw = callGemini(buildPrompt(episode, collectedClues, history, question));
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            String type = root.path("answerType").asText("UNKNOWN").trim().toUpperCase();
            String text = root.path("answerText").asText("").trim();
            return new Result(type, text);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DEDUCTION_AI_RESPONSE_PARSE_FAILED", "추리 AI 응답을 해석할 수 없습니다.");
        }
    }

    private String buildPrompt(Episode episode, List<String> collectedClues, List<FinalDeductionQuestion> history, String question) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the host of a Korean lateral-thinking mystery game.\n");
        builder.append("Answer the player's question by reading the case context. Return JSON only.\n");
        builder.append("Allowed answerType: YES, NO, PARTIAL, UNKNOWN, REFUSED_DIRECT_REVEAL.\n");
        builder.append("Rules:\n");
        builder.append("- The answerText must be Korean, one concise sentence.\n");
        builder.append("- Do not reveal exact final answer keywords, culprit name, weapon, motive, method, or final place.\n");
        builder.append("- If the question directly asks to confirm a final answer keyword, use REFUSED_DIRECT_REVEAL.\n");
        builder.append("- If the case context is insufficient for a definite yes/no, use PARTIAL or UNKNOWN.\n");
        builder.append("- Do not invent facts outside the provided case context.\n\n");
        builder.append("Case title: ").append(nullToBlank(episode.getTitle())).append('\n');
        builder.append("Final question: ").append(nullToBlank(episode.getFinalQuestion())).append('\n');
        builder.append("Case truth summary:\n").append(nullToBlank(episode.getFinalTruthSummary())).append("\n\n");
        builder.append("Secret facts:\n").append(nullToBlank(episode.getDeductionSecretFacts())).append("\n\n");
        builder.append("Forbidden direct reveals:\n").append(nullToBlank(episode.getDeductionForbiddenReveals())).append("\n\n");
        builder.append("Collected clues:\n");
        for (String clue : collectedClues) {
            builder.append("- ").append(nullToBlank(clue)).append('\n');
        }
        builder.append("\nRecent Q&A:\n");
        for (FinalDeductionQuestion item : history.stream().limit(10).toList()) {
            builder.append("Q: ").append(nullToBlank(item.getUserQuestion())).append('\n');
            builder.append("A: ").append(nullToBlank(item.getAiAnswerType())).append(" / ").append(nullToBlank(item.getAiAnswerText())).append('\n');
        }
        builder.append("\nPlayer question: ").append(nullToBlank(question)).append('\n');
        builder.append("""
                Return exactly:
                {"answerType":"YES|NO|PARTIAL|UNKNOWN|REFUSED_DIRECT_REVEAL","answerText":"Korean sentence"}
                """);
        return builder.toString();
    }

    private String callGemini(String prompt) {
        String url = API_BASE_URL + "/models/" + geminiModel.trim() + ":generateContent?key="
                + URLEncoder.encode(geminiApiKey.trim(), StandardCharsets.UTF_8);
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            JsonNode root = objectMapper.readTree(restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class));
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        } catch (RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DEDUCTION_AI_REQUEST_FAILED", "추리 AI 요청에 실패했습니다. 상태=" + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DEDUCTION_AI_REQUEST_FAILED", "추리 AI 요청에 실패했습니다. 원인=" + e.getClass().getSimpleName());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DEDUCTION_AI_RESPONSE_PARSE_FAILED", "추리 AI 응답을 해석할 수 없습니다.");
        }
    }

    private String extractJson(String value) {
        String text = value == null ? "" : value.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(45_000);
        return factory;
    }

    public record Result(String answerType, String answerText) {
    }
}
