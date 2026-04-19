package com.operation.seoul.game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.location.domain.Mission;
import com.operation.seoul.location.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * [Service: 채팅 퀴즈 및 스토리텔링 엔진 - SSE 스트리밍 적용]
 */
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final MissionRepository missionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱을 위한 객체

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // 1. 최종 정답 검증기
    public boolean verifyFinalAnswer(Long missionId, String userAnswer) {
        Mission mission = missionRepository.findById(missionId).orElseThrow();
        String answerKeyword = mission.getAnswerKeyword();
        if(answerKeyword == null || answerKeyword.isEmpty()) return true;
        String cleanUserAnswer = userAnswer.replace(" ", "").toLowerCase();
        String cleanAnswerKeyword = answerKeyword.replace(" ", "").toLowerCase();
        return cleanUserAnswer.contains(cleanAnswerKeyword);
    }

    // 2. 실시간 스트리밍 대사 생성기
    public ResponseBodyEmitter streamNarration(boolean isCorrect) {
        // 프론트엔드로 데이터를 쏴줄 파이프라인 생성 (타임아웃 60초)
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(60000L);

        // 스트리밍 전용 구글 API 주소 (streamGenerateContent?alt=sse 사용)
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=" + geminiApiKey;

        // AI에게 내릴 명령(Prompt) 세팅
        String prompt = isCorrect ?
                "너는 비밀 작전을 수행하는 요원들의 지휘관이야. 유저가 방탈출 미션 정답을 맞췄어. '훌륭하다 요원.' 으로 시작하는 몰입감 있는 칭찬 대사를 2문장으로 해줘." :
                "너는 비밀 작전을 수행하는 요원들의 지휘관이야. 유저가 미션 정답을 틀렸어. 단서를 다시 확인해보라고 냉철하지만 조력자 느낌으로 2문장으로 말해줘.";

        // JSON 요청 바디 만들기 (문자열 형태)
        String requestBody = "{ \"contents\": [{ \"parts\": [{\"text\": \"" + prompt + "\"}] }] }";

        // 자바 내장 HttpClient를 이용한 비동기 요청 생성
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 비동기로 구글 서버와 연결하고, 글자가 오는 족족 프론트로 던지기
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        try {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if (!data.equals("[DONE]")) {
                                    // 구글이 보낸 글자 조각(Chunk) 파싱
                                    JsonNode node = objectMapper.readTree(data);
                                    String textChunk = node.path("candidates").get(0)
                                            .path("content").path("parts").get(0)
                                            .path("text").asText();

                                    // 프론트엔드(Vue)로 한글자 전송
                                    emitter.send(textChunk);
                                }
                            }
                        } catch (Exception e) {
                            // 글자 조각 파싱 중 에러 발생 시 무시하고 다음 조각으로 진행
                        }
                    });
                    emitter.complete(); // 대답이 다 끝나면 파이프라인 닫기
                }).exceptionally(ex -> {
                    emitter.completeWithError(ex);
                    return null;
                });

        return emitter; // 만들어진 파이프라인을 컨트롤러로 반환
    }
}