package com.operation.seoul.admin.episode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationRequest;
import com.operation.seoul.admin.episode.dto.AiEpisodeDraftValidationResponse;
import com.operation.seoul.admin.episode.dto.AiEpisodePlanResponse;
import com.operation.seoul.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminEpisodeGeminiService {
    private static final String GENRE_ID = "CRIME_MYSTERY";
    private static final String GENRE_NAME = "범죄 미스터리";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final List<String> SLOT_IDS = List.of("CULPRIT", "WEAPON", "MOTIVE", "METHOD");
    private static final Map<String, String> SLOT_LABELS = Map.of(
            "CULPRIT", "범인",
            "WEAPON", "흉기",
            "MOTIVE", "동기",
            "METHOD", "방법"
    );
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate(requestFactory());

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash-lite}")
    private String geminiModel;

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(145_000);
        return factory;
    }

    public AiEpisodePlanResponse createAnswerPlan(AiEpisodeDraftRequest request) {
        validatePlaces(request);
        List<String> storyAnchors = extractTourApiStoryAnchors(request);
        List<AiEpisodePlanResponse.AnswerKeyword> keywords = answerPlanKeywords(request);
        attachPlanSourceBasis(keywords, storyAnchors);
        return AiEpisodePlanResponse.builder()
                .selectedGenreId(GENRE_ID)
                .selectedGenreName(GENRE_NAME)
                .answerSlots(answerSlotPlans())
                .finalAnswerKeywords(keywords)
                .finalAnswerKeywordItems(keywords)
                .finalAnswers(planFinalAnswers(keywords))
                .finalQuestionGuide("조사 미션 8개를 완료한 뒤 범인, 흉기, 동기, 방법을 각각 입력합니다.")
                .rationale(storyAnchors.isEmpty()
                        ? "장르는 범죄 미스터리로 고정하고, 최종 정답 키워드는 선택 장소의 검수 문맥을 바탕으로 구체화합니다."
                        : "장르는 범죄 미스터리로 고정하고, 최종 정답 키워드는 TourAPI 역사/사건 앵커를 바탕으로 구체화합니다.")
                .tourApiStoryAnchors(storyAnchors)
                .planReviewRequired(false)
                .reviewReason("")
                .fieldVerificationRecommended(true)
                .rejectedGenreReasons(List.of("장소 힌트나 최종 장소 추리 구조는 사용하지 않습니다."))
                .validationWarnings(List.of())
                .nextSteps(List.of("4개 정답 슬롯을 검수하고 AI 초안을 생성하세요."))
                .build();
    }

    public AiEpisodeDraftResponse createGeminiDraft(AiEpisodeDraftRequest request) {
        validatePlaces(request);
        normalizeFinalAnswerKeywordItems(request);
        repairWeakFinalAnswerKeywords(request);
        validateFinalAnswerContract(request);
        ensureApiKey();
        AiEpisodeDraftResponse.EpisodeDraft draft;
        try {
            JsonNode root = parseJson(callGemini(buildDraftPrompt(request)), "GEMINI_DRAFT_PARSE_FAILED");
            draft = objectMapper.treeToValue(draftJsonNode(root), AiEpisodeDraftResponse.EpisodeDraft.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_DRAFT_PARSE_FAILED", "Gemini 응답 JSON을 해석할 수 없습니다.");
        }
        return buildDraftResponse(draft, request, new ArrayList<>());
    }

    private AiEpisodeDraftResponse buildDraftResponse(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        applyApprovedFinalAnswerContract(draft, request, safeWarnings);
        normalizeDraft(draft, request);
        applyDeterministicCrimeMysteryGuardrail(draft, request, safeWarnings);
        AiEpisodeDraftValidationRequest validationRequest = new AiEpisodeDraftValidationRequest();
        validationRequest.setDraft(draft);
        validationRequest.setSourceInput(request);
        AiEpisodeDraftValidationResponse validation = validateDraft(validationRequest);
        return AiEpisodeDraftResponse.builder()
                .generatorType("GEMINI_CRIME_MYSTERY")
                .message("장소 배경을 바탕으로 구성한 범죄 미스터리 초안입니다.")
                .publishable(validation.isValid())
                .draft(draft)
                .validationWarnings(mergeWarnings(safeWarnings, validation))
                .nextSteps(List.of("8개 조사 단서의 중복과 정답 노출 여부를 검수하세요."))
                .build();
    }

    public AiEpisodeDraftValidationResponse validateDraft(AiEpisodeDraftValidationRequest request) {
        List<AiEpisodeDraftValidationResponse.Finding> findings = new ArrayList<>();
        AiEpisodeDraftResponse.EpisodeDraft draft = request == null ? null : request.getDraft();
        if (draft == null) {
            addFinding(findings, "ERROR", "DRAFT_REQUIRED", "검증할 초안이 없습니다.", null, "draft");
            return validationResponse(findings);
        }
        if (!GENRE_NAME.equals(draft.getGenre()) || !GENRE_NAME.equals(draft.getSelectedGenre())) {
            addFinding(findings, "ERROR", "GENRE_MUST_BE_CRIME_MYSTERY", "장르는 범죄 미스터리로 고정되어야 합니다.", null, "genre");
        }
        validateFinalAnswers(draft, findings);
        validateAnswerCoherence(draft, findings);
        validateNarrativeFields(draft, findings);
        validateMissions(draft, findings);
        validateSuspects(draft, findings);
        validateEvidences(draft, findings);
        validatePlaceSafety(draft, findings);
        findings.addAll(AiDraftTextQualityValidator.findings(draft));
        return validationResponse(findings);
    }

    private void applyApprovedFinalAnswerContract(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        if (draft == null) return;
        normalizeFinalAnswerKeywordItems(request);
        repairWeakFinalAnswerKeywords(request);
        Map<String, String> approved = approvedAnswers(request);
        NameRole culprit = splitNameRole(approved.get("CULPRIT"));
        approved.put("CULPRIT", culprit.name());
        List<String> values = SLOT_IDS.stream().map(approved::get).toList();
        draft.setGenre(GENRE_NAME);
        draft.setSelectedGenre(GENRE_NAME);
        draft.setFinalAnswerKeywords(values);
        draft.setFinalAnswerKeywordItems(SLOT_IDS.stream()
                .map(slot -> AiEpisodeDraftResponse.AnswerKeywordItem.builder()
                        .slotId(slot).type(slot).displayType(SLOT_LABELS.get(slot)).label(SLOT_LABELS.get(slot))
                        .keyword(approved.get(slot)).value(approved.get(slot))
                        .personName("CULPRIT".equals(slot) ? culprit.name() : "")
                        .personRole("CULPRIT".equals(slot) ? culprit.role() : "")
                        .role("CULPRIT".equals(slot) ? culprit.role() : "")
                        .aliases(List.of()).build())
                .toList());
        draft.setFinalAnswers(AiEpisodeDraftResponse.FinalAnswers.builder()
                .culprit(approved.get("CULPRIT")).weapon(approved.get("WEAPON"))
                .motive(approved.get("MOTIVE")).method(approved.get("METHOD"))
                .relatedPerson(approved.get("CULPRIT")).coreClue(approved.get("WEAPON"))
                .finalLocation(approved.get("METHOD")).build());
        draft.setFinalAnswer(String.format("%s: %s / %s: %s / %s: %s / %s: %s",
                SLOT_LABELS.get("CULPRIT"), approved.get("CULPRIT"),
                SLOT_LABELS.get("WEAPON"), approved.get("WEAPON"),
                SLOT_LABELS.get("MOTIVE"), approved.get("MOTIVE"),
                SLOT_LABELS.get("METHOD"), approved.get("METHOD")));
        draft.setFinalAnswerType("CASE_TRUTH");
        draft.setFinalQuestion(defaultIfBlank(draft.getFinalQuestion(), "범인, 흉기, 동기, 방법을 각각 입력하세요."));
    }

    private void normalizeDraft(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        draft.setGenre(GENRE_NAME);
        draft.setSelectedGenre(GENRE_NAME);
        draft.setFinalAnswerType("CASE_TRUTH");
        draft.setMaxDeductionQuestions(draft.getMaxDeductionQuestions() == null ? 20 : draft.getMaxDeductionQuestions());
        draft.setActualHistorySummary(defaultIfBlank(draft.getActualHistorySummary(), "이 지역의 문화적 배경과 장소의 분위기를 바탕으로 사건의 모티브를 구성했습니다."));
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null ? List.of() : request.getPlaces();
        if (places.size() != 10) return;
        List<AiEpisodeDraftResponse.MissionDraft> source = safeList(draft.getMissions());
        List<AiEpisodeDraftResponse.MissionDraft> missions = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            AiEpisodeDraftRequest.PlaceInput place = places.get(i);
            AiEpisodeDraftResponse.MissionDraft mission = i < source.size() ? source.get(i) : new AiEpisodeDraftResponse.MissionDraft();
            boolean start = i == 0;
            boolean finalPlace = i == places.size() - 1;
            mission.setOrder(i + 1);
            mission.setPlaceName(defaultIfBlank(place.getName(), "spot " + (i + 1)));
            mission.setAddress(place.getAddress());
            mission.setLatitude(place.getLatitude());
            mission.setLongitude(place.getLongitude());
            mission.setMarkerType(start ? "START" : finalPlace ? "FINAL" : "ANSWER_HINT");
            mission.setPublicMarkerType(start ? "START" : "ANSWER_HINT");
            mission.setClueRole(start ? "START" : finalPlace ? "FINAL_PLACE" : "ANSWER_HINT");
            mission.setFinalPlace(finalPlace);
            mission.setPuzzleType(defaultIfBlank(mission.getPuzzleType(), "STORY_COMBINATION"));
            mission.setQuestionText(defaultIfBlank(mission.getQuestionText(), "현장 기록과 사건 자료를 비교해 답하세요."));
            mission.setAnswer(defaultIfBlank(mission.getAnswer(), "단서" + (i + 1)));
            mission.setAnswerFormat(defaultIfBlank(mission.getAnswerFormat(), "TEXT"));
            mission.setHints(ensureThreeHints(mission.getHints()));
            if (!start && !finalPlace) {
                String target = defaultTargetKeywordType(i - 1);
                mission.setTargetKeywordType(target);
                mission.setTargetKeywordDisplayType(SLOT_LABELS.get(target));
                mission.setRewardClueSlotId("ANSWER_CLUE");
                mission.setRewardClueLabel(SLOT_LABELS.get(target) + " 단서");
                mission.setSupportsKeywordSlots(List.of(target));
                mission.setRewardClue(blank(mission.getRewardClue()) ? null : mission.getRewardClue().trim());
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), investigationStoryText(i + 1, target)));
            } else if (start) {
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), "사건 파일의 첫 장을 열고 피해자, 용의자, 사건 시간대를 확인합니다."));
            }
            if (finalPlace) {
                mission.setUnlockCondition(defaultIfBlank(mission.getUnlockCondition(), "ALL_INVESTIGATION_MISSIONS_CLEARED"));
                mission.setRewardClue(defaultIfBlank(mission.getRewardClue(), "조사 미션 8개 완료 시 자동 공개"));
                mission.setStoryText(defaultIfBlank(mission.getStoryText(), "모든 조사 단서를 대조한 뒤 범인, 흉기, 동기, 방법을 최종 입력합니다."));
            }
            missions.add(mission);
        }
        draft.setMissions(missions);
    }

    private String investigationStoryText(int order, String target) {
        return switch (order) {
            case 2 -> "사건 발생 시간대의 출입 기록과 내부 동선을 대조해 실제 접근 가능했던 인물을 좁힙니다.";
            case 3 -> "알리바이 기록, CCTV 공백, 휴대폰 위치 기록을 비교해 용의자들의 진술이 맞는지 확인합니다.";
            case 4 -> "피해자 주변에서 나온 물질 흔적을 분석해 사망 원인과 연결되는 물증을 확인합니다.";
            case 5 -> "현장에 남은 물건의 상태와 보관 위치를 대조해 어떤 도구가 범행에 쓰였는지 추적합니다.";
            case 6 -> "피해자와 용의자들 사이의 계약, 장부, 문서 기록을 확인해 사건의 이해관계를 찾습니다.";
            case 7 -> "사건 직전의 메시지, 통화, 목격 진술을 비교해 누가 가장 강한 압박을 받았는지 판단합니다.";
            case 8 -> "피해자가 반복하던 행동과 사건 당일 준비물의 변화를 대조해 범행 순서를 복원합니다.";
            case 9 -> "마지막 조작 흔적과 접근 순서를 맞춰 범행 방법이 어떻게 실행됐는지 확정합니다.";
            default -> "수집한 사건 기록을 대조해 " + SLOT_LABELS.getOrDefault(target, "정답") + " 판단에 필요한 단서를 확인합니다.";
        };
    }

    private void applyDeterministicCrimeMysteryGuardrail(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request, List<String> warnings) {
        if (draft == null) return;
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : warnings;
        Map<String, String> approved = approvedAnswers(request);
        String culprit = approved.get("CULPRIT");
        String weapon = approved.get("WEAPON");
        String motive = approved.get("MOTIVE");
        String method = approved.get("METHOD");
        String routineLabel = methodRoutineLabel(method);
        String containerLabel = evidenceContainerLabel(weapon, method);
        String motiveDocument = motiveDocumentLabel(motive);
        draft.setFictionSynopsis(defaultIfBlank(draft.getFictionSynopsis(),
                "중요한 행사 전날 밤 피해자가 제한된 공간에서 숨진 채 발견되었다. 외부 침입 흔적은 없고, 사건 시간대에 의미 있는 접근 권한을 가진 인물은 세 명뿐이었다. 조사 단서는 피해자의 " + routineLabel + ", 접근 기록, 독성 분석, 알리바이의 빈틈을 따라 하나의 진실로 수렴한다."));
        draft.setMissionDescription("8개 조사 단서로 범인, 흉기, 동기, 방법을 종합해 최종 진실을 판단합니다.");
        if (!finalTruthExplainsAnswers(draft, culprit, weapon, motive, method)) {
            draft.setFinalTruthSummary(String.format(
                    "범인: %s. 흉기: %s. 동기: %s. 방법: %s. 피해자의 %s, %s 접근 흔적, 독성 성분 분석, %s와 알리바이 검증 결과가 서로 맞물리며 이 네 가지 정답으로 수렴합니다.",
                    culprit, weapon, motive, method, routineLabel, containerLabel, motiveDocument));
            safeWarnings.add("GUARDRAIL_REPAIRED_FINAL_TRUTH_SUMMARY");
        }
        if (!hasUsableSuspects(draft, culprit)) {
            draft.setSuspects(canonicalSuspects(draft.getSuspects(), culprit));
            safeWarnings.add("GUARDRAIL_REPAIRED_SUSPECTS");
        }
        if (shouldRepairSynopsis(draft, request) || !synopsisMentionsAllSuspects(draft)) {
            draft.setFictionSynopsis(canonicalSynopsis(draft, weapon, motive, method));
            safeWarnings.add("GUARDRAIL_REPAIRED_SYNOPSIS_SUSPECTS");
        }
        if (redactRealPlaceNamesFromStoryFields(draft, request)) {
            safeWarnings.add("GUARDRAIL_REDACTED_REAL_PLACE_NAMES");
        }
        if (normalizeSuspectVictimReferences(draft)) {
            safeWarnings.add("GUARDRAIL_NORMALIZED_SUSPECT_VICTIM_REFERENCES");
        }
        if (redactSuspectNamesFromInvestigationClues(draft)) {
            safeWarnings.add("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_SUSPECT_NAMES");
        }
        if (rewriteGenericSuspectReferences(draft)) {
            safeWarnings.add("GUARDRAIL_REWROTE_GENERIC_SUSPECT_REFERENCES");
        }
        if (redactFinalAnswerValuesFromInvestigationClues(draft)) {
            safeWarnings.add("GUARDRAIL_REDACTED_INVESTIGATION_CLUE_ANSWER_VALUES");
        }
        List<String> investigationClueIssues = investigationClueIssues(draft);
        if (!investigationClueIssues.isEmpty()) {
            logInvestigationClueRepairSnapshot(draft, investigationClueIssues);
            applyCanonicalInvestigationClues(draft, request);
            safeWarnings.add("GUARDRAIL_REPAIRED_INVESTIGATION_CLUES");
            investigationClueIssues.forEach(issue -> safeWarnings.add("GUARDRAIL_INVESTIGATION_CLUES_" + issue));
        }
        if (!hasUsableEvidences(draft) || evidencesLeakFinalAnswerValues(draft)) {
            draft.setEvidences(canonicalEvidences(draft.getMissions()));
            safeWarnings.add("GUARDRAIL_REPAIRED_EVIDENCES");
        }
    }

    private boolean finalTruthExplainsAnswers(AiEpisodeDraftResponse.EpisodeDraft draft, String culprit, String weapon, String motive, String method) {
        String truth = compact(draft.getFinalTruthSummary());
        return Stream.of(culprit, weapon, motive, method)
                .allMatch(value -> !blank(value) && truth.contains(compact(value)));
    }

    private boolean hasUsableSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, String culprit) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        boolean hasCulprit = false;
        Set<String> names = new LinkedHashSet<>();
        for (AiEpisodeDraftResponse.SuspectDraft suspect : suspects) {
            if (suspect == null || blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                return false;
            }
            if (!names.add(compact(suspect.getDisplayName()))) {
                return false;
            }
            String suspectText = compact(String.join(" ",
                    trim(suspect.getDisplayName()),
                    trim(suspect.getAlias()),
                    trim(suspect.getRelationToVictim())));
            hasCulprit = hasCulprit || suspectText.contains(compact(culprit));
        }
        return hasCulprit;
    }

    private boolean shouldRepairSynopsis(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        String synopsis = trim(draft == null ? "" : draft.getFictionSynopsis());
        String compacted = compact(synopsis);
        if (synopsis.length() < 140) return true;
        if (!containsAny(compacted, "피해자", "숨진", "사망", "발견", "외부침입", "잠겨", "용의자", "세명", "3명")) {
            return true;
        }
        for (AiEpisodeDraftRequest.PlaceInput place : request == null || request.getPlaces() == null ? List.<AiEpisodeDraftRequest.PlaceInput>of() : request.getPlaces()) {
            String placeName = trim(place.getName());
            if (placeName.length() >= 3 && synopsis.contains(placeName)) {
                return true;
            }
        }
        return false;
    }

    private boolean synopsisMentionsAllSuspects(AiEpisodeDraftResponse.EpisodeDraft draft) {
        String synopsis = compact(draft.getFictionSynopsis());
        if (blank(synopsis)) return false;
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) return false;
        return suspects.stream()
                .filter(Objects::nonNull)
                .map(AiEpisodeDraftResponse.SuspectDraft::getDisplayName)
                .filter(name -> !blank(name))
                .allMatch(name -> synopsis.contains(compact(name)));
    }

    private boolean redactRealPlaceNamesFromStoryFields(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        if (draft == null || request == null || request.getPlaces() == null) return false;
        List<String> placeNames = request.getPlaces().stream()
                .filter(Objects::nonNull)
                .map(AiEpisodeDraftRequest.PlaceInput::getName)
                .map(this::trim)
                .filter(name -> name.length() >= 3)
                .distinct()
                .toList();
        if (placeNames.isEmpty()) return false;
        String beforeDraftText = String.join(" ",
                trim(draft.getEpisodeTitle()),
                trim(draft.getSubtitle()),
                trim(draft.getFictionSynopsis()),
                trim(draft.getMissionDescription()),
                trim(draft.getFinalTruthSummary()),
                trim(draft.getActualHistorySummary()));
        boolean changed = containsAnyPlaceName(beforeDraftText, placeNames);
        draft.setEpisodeTitle(redactRealPlaceNames(draft.getEpisodeTitle(), placeNames, "case scene"));
        draft.setSubtitle(redactRealPlaceNames(draft.getSubtitle(), placeNames, "case scene"));
        draft.setFictionSynopsis(redactRealPlaceNames(draft.getFictionSynopsis(), placeNames, "case scene"));
        draft.setMissionDescription(redactRealPlaceNames(draft.getMissionDescription(), placeNames, "investigation point"));
        draft.setFinalTruthSummary(redactRealPlaceNames(draft.getFinalTruthSummary(), placeNames, "case scene"));
        draft.setActualHistorySummary(redactRealPlaceNames(draft.getActualHistorySummary(), placeNames, "final point"));
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null) continue;
            String before = String.join(" ",
                    trim(mission.getStoryText()),
                    trim(mission.getQuestionText()),
                    trim(mission.getRewardClue()));
            mission.setStoryText(redactRealPlaceNames(mission.getStoryText(), placeNames, "investigation point"));
            mission.setQuestionText(redactRealPlaceNames(mission.getQuestionText(), placeNames, "investigation point"));
            mission.setRewardClue(redactRealPlaceNames(mission.getRewardClue(), placeNames, "investigation point"));
            changed = changed || containsAnyPlaceName(before, placeNames);
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null) continue;
            String before = String.join(" ", trim(evidence.getTitle()), trim(evidence.getTextSummary()));
            evidence.setTitle(redactRealPlaceNames(evidence.getTitle(), placeNames, "case file"));
            evidence.setTextSummary(redactRealPlaceNames(evidence.getTextSummary(), placeNames, "case file"));
            changed = changed || containsAnyPlaceName(before, placeNames);
        }
        return changed;
    }

    private boolean normalizeSuspectVictimReferences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null || draft.getSuspects() == null) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.SuspectDraft suspect : draft.getSuspects()) {
            if (suspect == null) continue;
            String shortDescription = normalizeVictimReference(suspect.getShortDescription());
            String relationToVictim = normalizeVictimReference(suspect.getRelationToVictim());
            String suspiciousPoint = normalizeVictimReference(suspect.getSuspiciousPoint());
            String alibiSummary = normalizeVictimReference(suspect.getAlibiSummary());
            changed = changed
                    || !Objects.equals(shortDescription, suspect.getShortDescription())
                    || !Objects.equals(relationToVictim, suspect.getRelationToVictim())
                    || !Objects.equals(suspiciousPoint, suspect.getSuspiciousPoint())
                    || !Objects.equals(alibiSummary, suspect.getAlibiSummary());
            suspect.setShortDescription(shortDescription);
            suspect.setRelationToVictim(relationToVictim);
            suspect.setSuspiciousPoint(suspiciousPoint);
            suspect.setAlibiSummary(alibiSummary);
        }
        return changed;
    }

    private String normalizeVictimReference(String value) {
        if (value == null) return null;
        return value.replace("김준혁", "한태준");
    }

    private String redactRealPlaceNames(String value, List<String> placeNames, String replacement) {
        if (blank(value)) return value;
        String result = value;
        for (String placeName : placeNames) {
            result = result.replace(placeName, replacement);
        }
        return result;
    }

    private boolean containsAnyPlaceName(String value, List<String> placeNames) {
        if (blank(value)) return false;
        return placeNames.stream().anyMatch(value::contains);
    }

    private String canonicalSynopsis(AiEpisodeDraftResponse.EpisodeDraft draft, String weapon, String motive, String method) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        String first = suspectName(suspects, 0, "용의자 A");
        String second = suspectName(suspects, 1, "용의자 B");
        String third = suspectName(suspects, 2, "용의자 C");
        String weaponPhrase = blank(weapon) ? "독성 물질" : weapon;
        String routineLabel = methodRoutineLabel(method);
        String containerLabel = evidenceContainerLabel(weapon, method);
        CaseSynopsisTemplate template = caseSynopsisTemplate(weapon, motive, method);
        return template.victimIntro() + "\n\n"
                + "사인은 " + weaponPhrase + "에서 검출된 독성 성분과 연결된 급성 반응으로 추정되었다.\n\n"
                + template.lockedRoomBeat() + " 현장에는 몸싸움의 흔적이 없었고, 독성 물질이 어떤 경로로 피해자에게 닿았는지는 즉시 밝혀지지 않았다.\n\n"
                + "수사 결과, 사건 추정 시간대에 내부에 남아 있었던 인물은 " + first + ", " + second + ", " + third + " 세 명뿐이었다.\n\n"
                + template.conflictBeat(defaultIfBlank(motive, template.defaultMotive())) + " 플레이어는 세 용의자의 알리바이, "
                + containerLabel + " 접근 기록, " + routineLabel + " 변조 흔적, 그리고 현장 기록의 공백을 대조해 범인과 범행 방식을 밝혀야 한다.";
    }

    private CaseSynopsisTemplate caseSynopsisTemplate(String weapon, String motive, String method) {
        String text = compact(String.join(" ", trim(weapon), trim(motive), trim(method)));
        if (containsAny(text, "항만", "화물", "밀수", "장부", "서류", "봉투")) {
            return new CaseSynopsisTemplate(
                    "항만 물류 감사관 한태준이 비공개 감사 보고회를 하루 앞둔 밤, 운영사 회의실 안쪽 자료 검토실에서 숨진 채 발견되었다.",
                    "자료 검토실 출입문은 전자 잠금장치로 닫혀 있었고 외부 침입 기록은 남아 있지 않았다.",
                    "한태준은 최근 항만 물품 거래와 내부 장부를 대조하며 비정상적인 화물 흐름을 추적하고 있었지만, %s를 둘러싼 이해관계가 여러 사람에게 치명적인 압박이 되고 있었다.",
                    "밀수 장부 은폐"
            );
        }
        if (containsAny(text, "연구", "실험", "시약", "논문", "특허")) {
            return new CaseSynopsisTemplate(
                    "바이오 연구소 책임자 한태준이 신약 투자 발표회를 하루 앞둔 밤, 연구동 회의실에서 숨진 채 발견되었다.",
                    "회의실은 내부 보안 카드로 잠긴 상태였고 CCTV는 사건 직전 짧은 공백을 보였다.",
                    "한태준은 최근 연구 성과와 특허 권리를 재정리하고 있었지만, %s를 둘러싼 갈등이 연구팀 내부에 깊게 남아 있었다.",
                    "연구 조작 기록 은폐"
            );
        }
        if (containsAny(text, "미술", "전시", "갤러리", "작품", "위작", "붓펜", "잉크", "서명")) {
            return new CaseSynopsisTemplate(
                    "유명 미술품 수집가 한태준이 개인 갤러리 개관 행사 전날 밤, 자신의 집무실에서 숨진 채 발견되었다.",
                    "집무실 문은 안에서 잠겨 있었고 외부 침입 흔적은 발견되지 않았다.",
                    "한태준은 최근 고가 작품의 감정 결과와 전시 공개를 앞두고 있었지만, %s를 둘러싼 이해관계가 관계자들을 압박하고 있었다.",
                    "위작 전시 의혹 은폐"
            );
        }
        if (containsAny(text, "카페", "와인", "잔", "음료", "식당", "보온병")) {
            return new CaseSynopsisTemplate(
                    "외식 브랜드 투자자 한태준이 신규 매장 계약 발표 전날 밤, 비공개 시음 회의실에서 숨진 채 발견되었다.",
                    "회의실 출입 기록은 내부 관계자 카드만 남아 있었고 외부 침입 흔적은 없었다.",
                    "한태준은 최근 투자금 흐름과 매장 운영권을 재검토하고 있었지만, %s를 둘러싼 갈등이 관계자들에게 큰 위협이 되고 있었다.",
                    "투자금 횡령 발각 은폐"
            );
        }
        return new CaseSynopsisTemplate(
                "중요 계약을 앞둔 사업가 한태준이 발표 전날 밤, 제한 구역 안쪽 회의실에서 숨진 채 발견되었다.",
                "회의실 출입문은 내부에서 잠긴 상태였고 외부 침입 흔적은 발견되지 않았다.",
                "한태준은 최근 내부 계약과 권리 관계를 재정리하고 있었지만, %s를 둘러싼 이해관계가 세 용의자 모두에게 부담으로 작용하고 있었다.",
                "비공개 계약 은폐"
        );
    }

    private record CaseSynopsisTemplate(String victimIntro, String lockedRoomBeat, String conflictBeatFormat, String defaultMotive) {
        String conflictBeat(String motive) {
            return String.format(conflictBeatFormat, motive);
        }
    }

    private String suspectName(List<AiEpisodeDraftResponse.SuspectDraft> suspects, int index, String fallback) {
        if (suspects == null || suspects.size() <= index || suspects.get(index) == null) {
            return fallback;
        }
        return defaultIfBlank(suspects.get(index).getDisplayName(), fallback);
    }

    private boolean hasUsableInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        return investigationClueIssues(draft).isEmpty();
    }

    private List<String> investigationClueIssues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        Set<String> issues = new LinkedHashSet<>();
        List<AiEpisodeDraftResponse.MissionDraft> investigation = safeList(draft.getMissions()).stream()
                .filter(mission -> mission != null)
                .filter(mission -> !"START".equals(normalize(mission.getMarkerType())))
                .filter(mission -> !Boolean.TRUE.equals(mission.getFinalPlace()))
                .filter(mission -> !"FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        if (investigation.size() != 8) issues.add("COUNT");
        Set<String> clues = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> counts.put(slot, 0));
        List<String> answers = answerValues(draft);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigation) {
            String clue = trim(mission.getRewardClue());
            if (blank(clue) || clue.length() < 10) issues.add("BLANK_OR_SHORT");
            if (!blank(clue) && isGenericFallbackClue(clue)) issues.add("GENERIC");
            if (!blank(clue) && !clues.add(compact(clue))) issues.add("DUPLICATE");
            if (!blank(clue) && answers.stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) {
                issues.add("DIRECT_ANSWER_LEAK");
            }
            String target = normalize(mission.getTargetKeywordType());
            if (!SLOT_IDS.contains(target)) {
                issues.add("TARGET_SLOT");
            } else {
                if ("CULPRIT".equals(target) && contradictsCulpritWithinSuspects(clue)) issues.add("CULPRIT_OUTSIDE_SUSPECTS");
                if (!isSlotRelevantClue(target, clue)) issues.add("SLOT_RELEVANCE");
                counts.computeIfPresent(target, (key, count) -> count + 1);
            }
            List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream()
                    .map(this::normalize)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (supports.size() != 1 || !supports.contains(target)) issues.add("SUPPORT_SLOT");
            if (containsForbiddenPlaceHint(mission)) issues.add("PLACE_HINT");
        }
        if (!SLOT_IDS.stream().allMatch(slot -> counts.getOrDefault(slot, 0) == 2)) issues.add("SLOT_BALANCE");
        return new ArrayList<>(issues);
    }

    private boolean redactFinalAnswerValuesFromInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> answers = answerValues(draft);
        if (answers.stream().allMatch(this::blank)) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null || "START".equals(normalize(mission.getMarkerType())) || Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))) {
                continue;
            }
            String clue = mission.getRewardClue();
            if (blank(clue)) continue;
            String redacted = clue;
            for (int i = 0; i < SLOT_IDS.size() && i < answers.size(); i++) {
                String answer = answers.get(i);
                if (blank(answer)) continue;
                redacted = redactAnswerValue(redacted, answer, indirectAnswerReference(SLOT_IDS.get(i)));
            }
            if (!redacted.equals(clue)) {
                mission.setRewardClue(redacted);
                changed = true;
            }
        }
        return changed;
    }

    private boolean redactSuspectNamesFromInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.isEmpty()) return false;
        boolean changed = false;
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null || "START".equals(normalize(mission.getMarkerType())) || Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))) {
                continue;
            }
            String clue = mission.getRewardClue();
            if (blank(clue)) continue;
            String redacted = clue;
            for (int i = 0; i < suspects.size(); i++) {
                AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
                if (suspect == null || blank(suspect.getDisplayName())) continue;
                redacted = redactAnswerValue(redacted, suspect.getDisplayName(), suspectReference(mission.getTargetKeywordType()));
            }
            if (!redacted.equals(clue)) {
                mission.setRewardClue(redacted);
                changed = true;
            }
        }
        return changed;
    }

    private boolean rewriteGenericSuspectReferences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        boolean changed = false;
        Map<Integer, String> targetByOrder = new LinkedHashMap<>();
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            if (mission == null) continue;
            Integer order = mission.getOrder();
            String target = mission.getTargetKeywordType();
            if (order != null && !blank(target)) targetByOrder.put(order, target);
            if (mission.getRewardClue() == null || isNonInvestigationMission(mission)) continue;
            String rewritten = rewriteGenericSuspectReference(mission.getRewardClue(), target);
            if (!rewritten.equals(mission.getRewardClue())) {
                mission.setRewardClue(rewritten);
                changed = true;
            }
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null || evidence.getTextSummary() == null) continue;
            String rewritten = rewriteGenericSuspectReference(evidence.getTextSummary(), targetByOrder.get(evidence.getSourceMissionOrder()));
            if (!rewritten.equals(evidence.getTextSummary())) {
                evidence.setTextSummary(rewritten);
                changed = true;
            }
        }
        return changed;
    }

    private boolean isNonInvestigationMission(AiEpisodeDraftResponse.MissionDraft mission) {
        return "START".equals(normalize(mission.getMarkerType()))
                || Boolean.TRUE.equals(mission.getFinalPlace())
                || "FINAL".equals(normalize(mission.getMarkerType()));
    }

    private String rewriteGenericSuspectReference(String text, String targetKeywordType) {
        if (blank(text)) return text;
        String replacement = genericSuspectReference(targetKeywordType);
        String result = text
                .replace("특정 용의자", replacement)
                .replace("용의자 중 한 명", replacement)
                .replace("용의자 중 하나", replacement)
                .replace("해당 용의자", replacement)
                .replace("해고 통보를 받은 용의자", replacement)
                .replace("용의자의 재직 기록", replacement + "의 재직 기록")
                .replace("용의자의 개인 소지품", replacement + "의 개인 소지품")
                .replace("용의자가 피해자에게", replacement + "가 피해자에게")
                .replace("용의자가 피해자의", replacement + "가 피해자의")
                .replace("용의자의 동선", replacement + "의 동선")
                .replace("문서에 언급된 인물 사이에", replacement + " 사이에")
                .replace("문서에 언급된 인물는", replacement + "은")
                .replace("문서에 언급된 인물이", replacement + "이")
                .replace("문서에 언급된 인물의", replacement + "의");
        return naturalizeRedactedSuspectReferences(normalizePersonReferenceParticles(result));
    }

    private String genericSuspectReference(String targetKeywordType) {
        return switch (normalize(targetKeywordType)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "물증과 연결된 인물";
            case "MOTIVE" -> "이해관계가 드러난 인물";
            case "METHOD" -> "동선이 겹친 인물";
            default -> "사건 기록 속 인물";
        };
    }

    private String redactAnswerValue(String text, String answer, String replacement) {
        String result = text.replace("용의자 " + answer, replacement);
        result = result.replace(answer + "은", replacement + "는");
        result = result.replace(answer + "는", replacement + "는");
        result = result.replace(answer + "이", replacement + "가");
        result = result.replace(answer + "가", replacement + "가");
        result = result.replace(answer + "을", replacement + "을");
        result = result.replace(answer + "를", replacement + "를");
        result = result.replace(answer + "에게", replacement + "에게");
        result = result.replace(answer + "의", replacement + "의");
        result = result.replace(answer + "와", replacement + "와");
        result = result.replace(answer + "과", replacement + "과");
        return naturalizeRedactedSuspectReferences(normalizePersonReferenceParticles(result.replace(answer, replacement)));
    }

    private String normalizePersonReferenceParticles(String text) {
        if (blank(text)) {
            return text;
        }
        return text
                .replace("관련 인물가", "관련 인물이")
                .replace("기록 속 인물가", "기록 속 인물이")
                .replace("기록 속 인물는", "기록 속 인물은")
                .replace("물증과 연결된 인물가", "물증과 연결된 인물이")
                .replace("물증과 연결된 인물는", "물증과 연결된 인물은")
                .replace("이해관계가 드러난 인물가", "이해관계가 드러난 인물이")
                .replace("이해관계가 드러난 인물는", "이해관계가 드러난 인물은")
                .replace("동선이 겹친 인물가", "동선이 겹친 인물이")
                .replace("동선이 겹친 인물는", "동선이 겹친 인물은")
                .replace("문서에 언급된 인물가", "문서에 언급된 인물이")
                .replace("문서에 언급된 인물는", "문서에 언급된 인물은")
                .replace("관련 인물가", "관련 인물이")
                .replace("관련 인물는", "관련 인물은")
                .replace("사건 기록 속 인물가", "사건 기록 속 인물이")
                .replace("사건 기록 속 인물는", "사건 기록 속 인물은");
    }

    private String naturalizeRedactedSuspectReferences(String text) {
        if (blank(text)) {
            return text;
        }
        return text
                .replace("CCTV에는 기록 속 인물이", "CCTV에는 동일 인물이")
                .replace("집 외부 CCTV에는 기록 속 인물이", "집 외부 CCTV에는 동일 인물이")
                .replace("'기록 속 인물,", "'메모의 대상자,")
                .replace("기록 속 인물이 박 회장에 대해", "메모의 대상자가 박 회장에 대해")
                .replace("기록 속 인물이 피해자에 대해", "메모의 대상자가 피해자에 대해")
                .replace("기록 속 인물은", "메모의 대상자는")
                .replace("기록 속 인물이", "메모의 대상자가")
                .replace("이해관계가 드러난 인물이 박 회장에게 보낸 문자", "문자 발신자가 박 회장에게 보낸 문자")
                .replace("이해관계가 드러난 인물이 피해자에게 보낸 문자", "문자 발신자가 피해자에게 보낸 문자")
                .replace("유언장에 따르면, 이해관계가 드러난 인물은", "유언장에 따르면, 해당 당사자는")
                .replace("이해관계가 드러난 인물은", "해당 당사자는")
                .replace("이해관계가 드러난 인물이", "문서상 이해관계자가")
                .replace("동선이 겹친 인물이 사용한 것으로 추정되는 특정 화학 물질에 대한 온라인 구매 기록", "동선 기록과 연결된 계정에서 특정 화학 물질을 온라인으로 구매한 기록")
                .replace("동선이 겹친 인물이", "동선 기록과 연결된 인물이");
    }

    private String suspectReference(String targetKeywordType) {
        return switch (normalize(targetKeywordType)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "물증과 연결된 인물";
            case "MOTIVE" -> "이해관계가 드러난 인물";
            case "METHOD" -> "동선이 겹친 인물";
            default -> "사건 기록 속 인물";
        };
    }

    private String indirectAnswerReference(String slot) {
        return switch (normalize(slot)) {
            case "CULPRIT" -> "기록 속 인물";
            case "WEAPON" -> "해당 물증";
            case "MOTIVE" -> "해당 동기";
            case "METHOD" -> "해당 실행 방식";
            default -> "해당 단서";
        };
    }


    private void logInvestigationClueRepairSnapshot(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        if (!log.isWarnEnabled()) return;
        String debugPath = writeInvestigationClueRepairSnapshot(draft, issues);
        log.warn("ai_draft_investigation_clues_repaired issues={} title={} debugPath={}",
                issues,
                abbreviateForLog(draft.getEpisodeTitle(), 80),
                debugPath);
    }

    private String writeInvestigationClueRepairSnapshot(AiEpisodeDraftResponse.EpisodeDraft draft, List<String> issues) {
        List<AiEpisodeDraftResponse.MissionDraft> investigation = safeList(draft.getMissions()).stream()
                .filter(mission -> mission != null)
                .filter(mission -> !"START".equals(normalize(mission.getMarkerType())))
                .filter(mission -> !Boolean.TRUE.equals(mission.getFinalPlace()))
                .filter(mission -> !"FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        List<String> answers = answerValues(draft);
        List<Map<String, Object>> missions = investigation.stream()
                .map(mission -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("order", mission.getOrder());
                    item.put("targetKeywordType", normalize(mission.getTargetKeywordType()));
                    item.put("supportsKeywordSlots", safeList(mission.getSupportsKeywordSlots()).stream().map(this::normalize).toList());
                    item.put("issues", investigationClueMissionIssues(mission, answers));
                    item.put("rewardClue", trim(mission.getRewardClue()));
                    return item;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", trim(draft.getEpisodeTitle()));
        payload.put("issues", issues);
        payload.put("missions", missions);
        Path path = Path.of("build", "ai-draft-debug", "latest-pre-guardrail-investigation-clues.json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload), StandardCharsets.UTF_8);
            return path.toString();
        } catch (Exception e) {
            log.warn("ai_draft_investigation_clue_debug_write_failed path={} error={}", path, e.toString());
            return "WRITE_FAILED";
        }
    }

    private List<String> investigationClueMissionIssues(AiEpisodeDraftResponse.MissionDraft mission, List<String> answers) {
        Set<String> issues = new LinkedHashSet<>();
        if (mission == null) return List.of("NULL_MISSION");
        String clue = trim(mission.getRewardClue());
        if (blank(clue) || clue.length() < 10) issues.add("BLANK_OR_SHORT");
        if (!blank(clue) && isGenericFallbackClue(clue)) issues.add("GENERIC");
        if (!blank(clue) && safeList(answers).stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) {
            issues.add("DIRECT_ANSWER_LEAK");
        }
        String target = normalize(mission.getTargetKeywordType());
        if (!SLOT_IDS.contains(target)) {
            issues.add("TARGET_SLOT");
        } else {
            if ("CULPRIT".equals(target) && contradictsCulpritWithinSuspects(clue)) issues.add("CULPRIT_OUTSIDE_SUSPECTS");
            if (!isSlotRelevantClue(target, clue)) issues.add("SLOT_RELEVANCE");
        }
        List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .toList();
        if (supports.size() != 1 || !supports.contains(target)) issues.add("SUPPORT_SLOT");
        if (containsForbiddenPlaceHint(mission)) issues.add("PLACE_HINT");
        return new ArrayList<>(issues);
    }

    private boolean hasUsableEvidences(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = safeList(draft.getEvidences());
        if (evidences.size() != 8) return false;
        Set<Integer> orders = evidences.stream()
                .map(AiEpisodeDraftResponse.EvidenceDraft::getSourceMissionOrder)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (int order = 2; order <= 9; order++) {
            if (!orders.contains(order)) return false;
        }
        return evidences.stream().allMatch(evidence -> evidence != null && !blank(evidence.getTitle()) && !blank(evidence.getTextSummary()));
    }

    private boolean evidencesLeakFinalAnswerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        List<String> answers = answerValues(draft).stream()
                .filter(value -> !blank(value))
                .map(this::compact)
                .toList();
        if (answers.isEmpty()) return false;
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            if (evidence == null) continue;
            String text = compact(String.join(" ", trim(evidence.getTitle()), trim(evidence.getTextSummary())));
            if (answers.stream().anyMatch(answer -> !blank(answer) && text.contains(answer))) {
                return true;
            }
        }
        return false;
    }

    private List<AiEpisodeDraftResponse.SuspectDraft> canonicalSuspects(List<AiEpisodeDraftResponse.SuspectDraft> source, String culprit) {
        List<AiEpisodeDraftResponse.SuspectDraft> result = new ArrayList<>();
        AiEpisodeDraftResponse.SuspectDraft culpritDraft = safeList(source).stream()
                .filter(suspect -> suspect != null && containsAny(compact(String.join(" ", trim(suspect.getDisplayName()), trim(suspect.getAlias()))), compact(culprit)))
                .findFirst()
                .orElseGet(() -> AiEpisodeDraftResponse.SuspectDraft.builder().displayName(culprit).build());
        result.add(AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(defaultIfBlank(culpritDraft.getDisplayName(), culprit))
                .alias(culpritDraft.getAlias())
                .relationToVictim(defaultIfBlank(culpritDraft.getRelationToVictim(), "피해자의 비서"))
                .alibiSummary(defaultIfBlank(culpritDraft.getAlibiSummary(), "사건 추정 시각 동안 행사 자료를 정리하고 있었다고 주장하며, 일부 노트북 사용 기록이 남아 있다."))
                .suspiciousPoint(defaultIfBlank(culpritDraft.getSuspiciousPoint(), "최근 해고 통보를 받았고 피해자의 일정과 약 복용 습관을 가장 잘 알고 있었다."))
                .shortDescription(culpritDraft.getShortDescription())
                .portraitImageUrl(culpritDraft.getPortraitImageUrl())
                .imagePrompt(culpritDraft.getImagePrompt())
                .build());
        addNonCulpritSuspect(result, source, "박도현", "사업 파트너",
                "사건 시간 동안 투자자와 화상회의를 했다고 주장하며, 회의 접속 기록이 대부분 남아 있다.",
                "피해자와 투자 분쟁이 있었고 피해자 사망 시 경제적 이익을 얻을 수 있었다.");
        addNonCulpritSuspect(result, source, "이재훈", "피해자의 조카",
                "사건 시간 동안 전시 준비를 하고 있었다고 주장하며, 일부 CCTV에 모습이 남아 있다.",
                "유산 상속 예정자였고 최근 피해자와 크게 다퉜으나 CCTV 공백 시간이 사망 추정 시각과 어긋난다.");
        return result.stream().limit(3).toList();
    }

    private void addNonCulpritSuspect(List<AiEpisodeDraftResponse.SuspectDraft> result, List<AiEpisodeDraftResponse.SuspectDraft> source, String fallbackName, String relation, String alibi, String suspicion) {
        AiEpisodeDraftResponse.SuspectDraft existing = safeList(source).stream()
                .filter(suspect -> suspect != null && result.stream().noneMatch(saved -> compact(saved.getDisplayName()).equals(compact(suspect.getDisplayName()))))
                .findFirst()
                .orElse(null);
        String displayName = defaultIfBlank(existing == null ? "" : existing.getDisplayName(), fallbackName);
        String compactDisplayName = compact(displayName);
        if (result.stream().anyMatch(saved -> compact(saved.getDisplayName()).equals(compactDisplayName))) {
            displayName = fallbackName;
        }
        result.add(AiEpisodeDraftResponse.SuspectDraft.builder()
                .displayName(displayName)
                .alias(existing == null ? null : existing.getAlias())
                .relationToVictim(defaultIfBlank(existing == null ? "" : existing.getRelationToVictim(), relation))
                .alibiSummary(defaultIfBlank(existing == null ? "" : existing.getAlibiSummary(), alibi))
                .suspiciousPoint(defaultIfBlank(existing == null ? "" : existing.getSuspiciousPoint(), suspicion))
                .shortDescription(existing == null ? null : existing.getShortDescription())
                .portraitImageUrl(existing == null ? null : existing.getPortraitImageUrl())
                .imagePrompt(existing == null ? null : existing.getImagePrompt())
                .build());
    }

    private void applyCanonicalInvestigationClues(AiEpisodeDraftResponse.EpisodeDraft draft, AiEpisodeDraftRequest request) {
        Map<String, String> answers = approvedAnswers(request);
        List<String> clues = canonicalInvestigationClues(answers);
        List<String> targets = List.of("CULPRIT", "CULPRIT", "WEAPON", "WEAPON", "MOTIVE", "MOTIVE", "METHOD", "METHOD");
        List<AiEpisodeDraftResponse.MissionDraft> missions = safeList(draft.getMissions());
        int clueIndex = 0;
        for (AiEpisodeDraftResponse.MissionDraft mission : missions) {
            if (mission == null || "START".equals(normalize(mission.getMarkerType())) || Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType()))) {
                continue;
            }
            if (clueIndex >= clues.size()) break;
            String target = targets.get(clueIndex);
            mission.setTargetKeywordType(target);
            mission.setTargetKeywordDisplayType(SLOT_LABELS.get(target));
            mission.setRewardClueSlotId("ANSWER_CLUE");
            mission.setRewardClueLabel(SLOT_LABELS.get(target) + " 단서");
            mission.setSupportsKeywordSlots(List.of(target));
            mission.setRewardClue(clues.get(clueIndex));
            clueIndex++;
        }
    }

    private List<String> canonicalInvestigationClues(Map<String, String> answers) {
        if (answers != null) {
            String weapon = compact(answers.get("WEAPON"));
            String method = compact(answers.get("METHOD"));
            String objectLabel = cleanEvidenceObjectLabel(weapon, method);
            String containerLabel = cleanEvidenceContainerLabel(weapon, method);
            String motiveDocument = cleanMotiveDocumentLabel(compact(answers.get("MOTIVE")));
            String routineLabel = cleanMethodRoutineLabel(method);
            return List.of(
                    "출입 기록과 알리바이를 대조하면 사건 직전 피해자의 업무 공간에 혼자 접근한 사람은 한 명뿐이며, 같은 시간대에 " + containerLabel + " 보관 위치도 열려 있었다.",
                    containerLabel + "에서 피해자의 흔적 외 추가 지문 하나가 검출됐고, 그 지문 주인의 알리바이에는 CCTV 공백과 맞물리는 짧은 이동 시간이 남아 있다.",
                    "감식 결과 피해자는 음식 전체가 아니라 사건 직전 반복적으로 만진 " + objectLabel + "의 표면 성분과 접촉한 뒤 급성 반응을 보인 것으로 좁혀졌다.",
                    objectLabel + "의 오염 흔적과 물질 성분은 오래된 것이 아니라 사건 당일 새로 묻은 상태였고, 평소 보관 위치가 아닌 제한 구역에서 옮겨진 정황이 확인됐다.",
                    "사건 일주일 전 작성된 " + motiveDocument + "에는 피해자가 공개하려던 결정 때문에 내부 관계자 한 명이 직위나 계약상 손실을 볼 내용과 갈등 기록이 적혀 있었다.",
                    "삭제된 메시지와 목격 진술을 대조하면 그 관계자는 공개를 막아야 한다는 압박을 받았고, 피해자와 언쟁한 직후 감정적 문장을 남겼다.",
                    "피해자는 사건 직전에도 평소 절차대로 " + routineLabel + "을 확인했으며, 증상 발생 시각은 그 반복 행동 직후로 맞아떨어진다.",
                    "시간표, 지문, 오염 시점, 문서 기록을 겹치면 알리바이가 남는 두 명은 접근 권한과 동기, 조작 순서 조건을 동시에 만족하지 못한다."
            );
        }
        String weapon = compact(answers.get("WEAPON"));
        String motive = compact(answers.get("MOTIVE"));
        String method = compact(answers.get("METHOD"));
        String objectLabel = evidenceObjectLabel(weapon, method);
        String containerLabel = evidenceContainerLabel(weapon, method);
        String motiveDocument = motiveDocumentLabel(motive);
        String routineLabel = methodRoutineLabel(method);
        return List.of(
                containerLabel + "에서는 피해자의 흔적 외에 업무 공간을 자유롭게 출입할 수 있는 한 사람의 추가 지문만 검출되었다.",
                "사건 시간대 출입 기록과 알리바이 대조 결과, 두 명의 용의자는 주요 시각의 동선이 외부 기록으로 확인되었다.",
                objectLabel + " 분석 결과 일반 성분과 다른 독성 물질이 검출되었고, 같은 성분은 다른 음식이나 주변 물건에서는 확인되지 않았다.",
                containerLabel + " 안쪽 잔류물과 폐기 흔적이 서로 맞아, 독성 물질이 사건 직전 준비물에만 섞였다는 점이 드러났다.",
                "사건 전 작성된 " + motiveDocument + "에는 피해자와 가까운 인물에게 불리한 결정과 은폐해야 할 문제가 함께 기록되어 있었다.",
                "피해자와 가까운 직원이 사건 직전 강한 불만과 압박감을 드러냈다는 메시지 기록이 남아 있었다.",
                "피해자는 사건 전 일정한 순서로 " + routineLabel + "를 확인하거나 사용했고, 그 준비물은 제한된 업무 공간에 보관되어 있었다.",
                routineLabel + " 교체 추정 시간과 보관 지점 접근 기록이 같은 업무 동선 위에서 겹친다."
        );
    }

    private String cleanEvidenceObjectLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "서류", "봉투", "문서", "장부")) return "문서 봉투";
        if (containsAny(text, "붓펜", "잉크", "서명", "펜")) return "서명 도구";
        if (containsAny(text, "향수", "분사")) return "휴대용 분사 물품";
        if (containsAny(text, "약", "캡슐", "복용")) return "복용 물품";
        if (containsAny(text, "음료", "커피", "차", "와인", "잔", "보온병")) return "음료 용기";
        return "현장 물증";
    }

    private String cleanEvidenceContainerLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "서류", "봉투", "문서", "장부")) return "문서 보관함";
        if (containsAny(text, "붓펜", "잉크", "서명", "펜")) return "필기구 보관함";
        if (containsAny(text, "향수", "분사")) return "개인 소지품 보관함";
        if (containsAny(text, "약", "캡슐", "복용")) return "약품 보관함";
        if (containsAny(text, "음료", "커피", "차", "와인", "잔", "보온병")) return "음료 준비대";
        return "증거 보관 지점";
    }

    private String cleanMotiveDocumentLabel(String motive) {
        if (containsAny(motive, "위작", "전시", "작품", "감정")) return "감정 보고서";
        if (containsAny(motive, "밀수", "장부", "계약", "은폐")) return "비공개 계약 문서";
        if (containsAny(motive, "연구", "특허", "논문", "조작")) return "연구 감사 문서";
        if (containsAny(motive, "횡령", "투자", "손실", "채무")) return "회계 검토 문서";
        if (containsAny(motive, "유산", "상속")) return "상속 관련 문서";
        return "내부 결정 문서";
    }

    private String cleanMethodRoutineLabel(String method) {
        if (containsAny(method, "서류", "봉투", "문서")) return "매일 확인하던 문서";
        if (containsAny(method, "서명", "붓펜", "펜", "잉크")) return "서명 확인 절차";
        if (containsAny(method, "향수", "분사")) return "현장 준비물 사용";
        if (containsAny(method, "약", "캡슐", "복용")) return "반복 복용하던 약";
        if (containsAny(method, "음료", "마시", "커피", "차", "와인")) return "반복되던 음료 준비";
        return "반복되던 확인 절차";
    }

    private String evidenceObjectLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "음료", "커피", "차", "잔", "컵", "보온병")) return "음료 용기";
        if (containsAny(text, "시약", "실험", "연구")) return "시료 용기";
        if (containsAny(text, "약", "캡슐", "수면제", "복용")) return "약물 용기";
        if (containsAny(text, "서류", "봉투", "문서")) return "문서 봉투";
        return "현장 물증";
    }

    private String evidenceContainerLabel(String weapon, String method) {
        String text = compact(weapon + " " + method);
        if (containsAny(text, "음료", "커피", "차", "잔", "컵", "보온병")) return "음료 보관대";
        if (containsAny(text, "시약", "실험", "연구")) return "실험 준비물 보관함";
        if (containsAny(text, "약", "캡슐", "수면제", "복용")) return "약통";
        if (containsAny(text, "서류", "봉투", "문서")) return "문서 보관함";
        return "증거 보관 지점";
    }

    private String motiveDocumentLabel(String motive) {
        if (containsAny(motive, "연구", "조작", "논문", "실험", "시약")) return "연구 감사 문서";
        if (containsAny(motive, "해고", "계약", "인수인계")) return "인사 문서";
        if (containsAny(motive, "채무", "손실", "횡령", "재정", "금전")) return "회계 문서";
        if (containsAny(motive, "유산", "상속")) return "상속 관련 문서";
        return "내부 문서";
    }

    private String methodRoutineLabel(String method) {
        if (containsAny(method, "음료", "커피", "차", "마시는")) return "매일 마시던 음료";
        if (containsAny(method, "약", "캡슐", "수면제", "복용")) return "매일 복용하던 약";
        if (containsAny(method, "서류", "봉투", "문서")) return "매일 확인하던 문서";
        return "반복되던 준비물";
    }

    private List<AiEpisodeDraftResponse.EvidenceDraft> canonicalEvidences(List<AiEpisodeDraftResponse.MissionDraft> missions) {
        return safeList(missions).stream()
                .filter(mission -> mission != null && mission.getOrder() != null && mission.getOrder() >= 2 && mission.getOrder() <= 9)
                .map(mission -> AiEpisodeDraftResponse.EvidenceDraft.builder()
                        .title(mission.getOrder() + "번 조사 증거")
                        .type("STORY_CLUE")
                        .textSummary(mission.getRewardClue())
                        .sourceMissionOrder(mission.getOrder())
                        .build())
                .toList();
    }

    private void validateFinalAnswers(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers == null || blank(answers.getCulprit()) || blank(answers.getWeapon()) || blank(answers.getMotive()) || blank(answers.getMethod())) {
            addFinding(findings, "ERROR", "FOUR_FINAL_ANSWERS_REQUIRED", "최종 정답은 범인, 흉기, 동기, 방법 4개입니다.", null, "finalAnswers");
        }
        if (draft.getFinalAnswerKeywords() == null || draft.getFinalAnswerKeywords().size() != 4 || draft.getFinalAnswerKeywords().stream().anyMatch(this::blank)) {
            addFinding(findings, "ERROR", "FOUR_FINAL_KEYWORDS_REQUIRED", "최종 정답 키워드는 정확히 4개여야 합니다.", null, "finalAnswerKeywords");
        }
        List<AiEpisodeDraftResponse.AnswerKeywordItem> items = safeList(draft.getFinalAnswerKeywordItems());
        Set<String> itemSlots = items.stream()
                .map(item -> normalize(defaultIfBlank(item.getSlotId(), item.getType())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (items.size() != 4 || !itemSlots.equals(new LinkedHashSet<>(SLOT_IDS)) || items.stream().anyMatch(item -> blank(answerKeywordItemValue(item)))) {
            addFinding(findings, "ERROR", "FOUR_FINAL_KEYWORD_ITEMS_REQUIRED", "finalAnswerKeywordItems must contain exactly CULPRIT, WEAPON, MOTIVE, METHOD with non-empty values.", null, "finalAnswerKeywordItems");
        }
        for (AiEpisodeDraftResponse.AnswerKeywordItem item : items) {
            String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
            String value = answerKeywordItemValue(item);
            if (SLOT_IDS.contains(slot) && weakFinalAnswerKeyword(slot, value)) {
                addFinding(findings, "ERROR", "CONCRETE_FINAL_KEYWORD_REQUIRED", "최종 정답 키워드는 구체적인 인물, 물건, 동기, 범행 과정이어야 합니다.", null, "finalAnswerKeywordItems");
            }
        }
    }

    private void validateMissions(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.MissionDraft> missions = safeList(draft.getMissions());
        if (missions.size() != 10) {
            addFinding(findings, "ERROR", "TEN_PLACES_REQUIRED", "START 1, investigation 8, FINAL 1 missions are required.", null, "missions");
            return;
        }
        List<AiEpisodeDraftResponse.MissionDraft> investigation = missions.stream()
                .filter(mission -> !"START".equals(normalize(mission.getMarkerType())))
                .filter(mission -> !Boolean.TRUE.equals(mission.getFinalPlace()))
                .filter(mission -> !"FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        if (missions.stream().filter(mission -> "START".equals(normalize(mission.getMarkerType()))).count() != 1) addFinding(findings, "ERROR", "ONE_START_REQUIRED", "START mission must be exactly one.", null, "missions");
        List<AiEpisodeDraftResponse.MissionDraft> finals = missions.stream()
                .filter(mission -> Boolean.TRUE.equals(mission.getFinalPlace()) || "FINAL".equals(normalize(mission.getMarkerType())))
                .toList();
        if (finals.size() != 1) addFinding(findings, "ERROR", "ONE_FINAL_REQUIRED", "FINAL mission must be exactly one.", null, "missions");
        for (AiEpisodeDraftResponse.MissionDraft finalMission : finals) {
            if (!"ALL_INVESTIGATION_MISSIONS_CLEARED".equals(normalize(finalMission.getUnlockCondition()))) {
                addFinding(findings, "ERROR", "FINAL_UNLOCK_CONDITION_REQUIRED", "Final place must unlock automatically after all 8 investigation missions are cleared.", finalMission.getOrder(), "unlockCondition");
            }
            if (!blank(finalMission.getTargetKeywordType()) || !safeList(finalMission.getSupportsKeywordSlots()).isEmpty()) {
                addFinding(findings, "ERROR", "FINAL_PLACE_MUST_NOT_BE_ANSWER_CLUE", "Final place must not be used as a final-answer clue.", finalMission.getOrder(), "targetKeywordType");
            }
        }
        if (investigation.size() != 8) addFinding(findings, "ERROR", "EIGHT_INVESTIGATION_CLUES_REQUIRED", "Investigation missions must be exactly eight.", null, "missions");
        Set<String> clues = new LinkedHashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> counts.put(slot, 0));
        List<String> answers = answerValues(draft);
        for (AiEpisodeDraftResponse.MissionDraft mission : investigation) {
            String clue = trim(mission.getRewardClue());
            if (blank(mission.getStoryText())) {
                addFinding(findings, "ERROR", "MISSION_STORY_TEXT_REQUIRED", "Investigation mission storyText is required.", mission.getOrder(), "storyText");
            }
            if (blank(clue) || clue.length() < 10) addFinding(findings, "ERROR", "DEDUCTIVE_CLUE_REQUIRED", "Investigation rewardClue is required.", mission.getOrder(), "rewardClue");
            if (isGenericFallbackClue(clue)) addFinding(findings, "ERROR", "GENERIC_DEDUCTIVE_CLUE", "Investigation rewardClue must be a concrete case fact, not a generic fallback sentence.", mission.getOrder(), "rewardClue");
            if (!blank(clue) && !clues.add(compact(clue))) addFinding(findings, "ERROR", "DUPLICATE_CLUE", "Investigation clues must be unique.", mission.getOrder(), "rewardClue");
            if (answers.stream().anyMatch(value -> !blank(value) && compact(clue).contains(compact(value)))) addFinding(findings, "ERROR", "DIRECT_ANSWER_LEAK", "rewardClue must not include final answer values.", mission.getOrder(), "rewardClue");
            String target = normalize(mission.getTargetKeywordType());
            if (!SLOT_IDS.contains(target)) addFinding(findings, "ERROR", "TARGET_KEYWORD_TYPE_REQUIRED", "targetKeywordType is required.", mission.getOrder(), "targetKeywordType");
            else counts.computeIfPresent(target, (key, count) -> count + 1);
            List<String> supports = safeList(mission.getSupportsKeywordSlots()).stream().map(this::normalize).filter(value -> !value.isBlank()).toList();
            if (supports.size() != 1 || !supports.contains(target)) {
                addFinding(findings, "ERROR", "EXACTLY_ONE_SUPPORTED_SLOT_REQUIRED", "Each investigation clue must support exactly one matching final answer slot.", mission.getOrder(), "supportsKeywordSlots");
            }
            if (containsAny(compact(clue), "atmosphere", "mood", "scenery", "backgroundonly")) {
                addFinding(findings, "ERROR", "DEDUCTIVE_CLUE_NOT_ATMOSPHERE", "Investigation rewardClue must be deductive evidence, not atmosphere or background description.", mission.getOrder(), "rewardClue");
            }
            if (!isSlotRelevantClue(target, clue)) {
                addFinding(findings, "ERROR", "CLUE_SLOT_MISMATCH", "Investigation rewardClue must match its targetKeywordType.", mission.getOrder(), "rewardClue");
            }
            if ("CULPRIT".equals(target) && contradictsCulpritWithinSuspects(clue)) {
                addFinding(findings, "ERROR", "CULPRIT_CLUE_CONTRADICTS_SUSPECT_SET", "CULPRIT rewardClue must not imply the culprit is outside the three suspects.", mission.getOrder(), "rewardClue");
            }
            if (containsForbiddenPlaceHint(mission)) addFinding(findings, "ERROR", "DESTINATION_HINT_FORBIDDEN", "Place hint structure is forbidden.", mission.getOrder(), "markerType");
        }
        for (String slot : SLOT_IDS) {
            int count = counts.getOrDefault(slot, 0);
            if (count != 2) addFinding(findings, "ERROR", "ANSWER_SLOT_EXACT_SUPPORT_REQUIRED", slot + " must be supported by exactly 2 investigation missions. current=" + count, null, "missions.targetKeywordType");
        }
    }

    private void validateSuspects(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.SuspectDraft> suspects = safeList(draft.getSuspects());
        if (suspects.size() != 3) addFinding(findings, "ERROR", "EXACTLY_THREE_SUSPECTS_REQUIRED", "Exactly three suspects are required.", null, "suspects");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < suspects.size(); i++) {
            AiEpisodeDraftResponse.SuspectDraft suspect = suspects.get(i);
            if (blank(suspect.getDisplayName()) || blank(suspect.getAlibiSummary()) || blank(suspect.getSuspiciousPoint())) {
                addFinding(findings, "ERROR", "SUSPECT_DETAILS_REQUIRED", "Suspect details are required.", null, "suspects[" + i + "]");
            }
            if (!blank(suspect.getDisplayName()) && !names.add(compact(suspect.getDisplayName()))) {
                addFinding(findings, "ERROR", "SUSPECT_NAMES_MUST_BE_UNIQUE", "Suspect names must be unique.", null, "suspects[" + i + "].displayName");
            }
        }
    }

    private void validateAnswerCoherence(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers == null) return;
        String culprit = trim(answers.getCulprit());
        if (!blank(culprit)) {
            boolean culpritExists = safeList(draft.getSuspects()).stream().anyMatch(suspect ->
                    suspect != null && containsAny(compact(String.join(" ",
                            trim(suspect.getDisplayName()),
                            trim(suspect.getAlias()),
                            trim(suspect.getRelationToVictim()))), compact(culprit)));
            if (!culpritExists) {
                addFinding(findings, "ERROR", "CULPRIT_MUST_BE_SUSPECT", "The culprit answer must be one of the three suspect cards.", null, "suspects");
            }
        }
        String truth = compact(draft.getFinalTruthSummary());
        for (String value : List.of(answers.getCulprit(), answers.getWeapon(), answers.getMotive(), answers.getMethod())) {
            if (!blank(value) && !truth.contains(compact(value))) {
                addFinding(findings, "ERROR", "FINAL_TRUTH_MUST_EXPLAIN_ANSWERS", "finalTruthSummary must explicitly explain all four final answer values.", null, "finalTruthSummary");
                break;
            }
        }
    }

    private void validateNarrativeFields(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        if (blank(draft.getEpisodeTitle())) addFinding(findings, "ERROR", "EPISODE_TITLE_REQUIRED", "episodeTitle is required.", null, "episodeTitle");
        if (blank(draft.getFictionSynopsis())) addFinding(findings, "ERROR", "FICTION_SYNOPSIS_REQUIRED", "fictionSynopsis must describe the fictional case overview.", null, "fictionSynopsis");
        if (blank(draft.getFinalTruthSummary())) addFinding(findings, "ERROR", "FINAL_TRUTH_SUMMARY_REQUIRED", "finalTruthSummary must explain the culprit, weapon, motive, and method.", null, "finalTruthSummary");
        validateSynopsisDomain(draft, findings);
    }

    private void validateSynopsisDomain(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        String synopsis = compact(draft.getFictionSynopsis());
        String keywords = compact(safeList(draft.getFinalAnswerKeywordItems()).stream()
                .flatMap(item -> Stream.of(item.getValue(), item.getKeyword(), item.getPersonName()))
                .filter(value -> !blank(value))
                .collect(Collectors.joining(" ")));
        if (containsAny(keywords, "항만", "화물", "밀수", "장부", "서류", "봉투")) {
            if (!containsAny(synopsis, "항만", "화물", "자료", "서류", "장부", "물류")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_MISMATCH", "fictionSynopsis must match the final keyword domain.", null, "fictionSynopsis");
            }
            if (containsAny(synopsis, "미술품", "갤러리", "전시", "작품")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_FORBIDDEN_TERM", "fictionSynopsis contains an incompatible domain term.", null, "fictionSynopsis");
            }
        }
        if (containsAny(keywords, "미술", "전시", "갤러리", "작품", "위작", "붓펜", "잉크", "서명")) {
            if (!containsAny(synopsis, "미술", "갤러리", "전시", "작품", "감정", "서명")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_MISMATCH", "fictionSynopsis must match the final keyword domain.", null, "fictionSynopsis");
            }
            if (containsAny(synopsis, "항만", "화물", "밀수")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_FORBIDDEN_TERM", "fictionSynopsis contains an incompatible domain term.", null, "fictionSynopsis");
            }
        }
        if (containsAny(keywords, "연구", "실험", "시약", "논문", "특허")) {
            if (!containsAny(synopsis, "연구", "실험", "회의실", "연구동", "특허")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_MISMATCH", "fictionSynopsis must match the final keyword domain.", null, "fictionSynopsis");
            }
            if (containsAny(synopsis, "갤러리", "미술품", "항만")) {
                addFinding(findings, "ERROR", "SYNOPSIS_DOMAIN_FORBIDDEN_TERM", "fictionSynopsis contains an incompatible domain term.", null, "fictionSynopsis");
            }
        }
    }

    private void validateEvidences(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        List<AiEpisodeDraftResponse.EvidenceDraft> evidences = safeList(draft.getEvidences());
        List<String> answerValues = answerValues(draft).stream()
                .filter(value -> !blank(value))
                .map(this::compact)
                .toList();
        if (evidences.size() != 8) {
            addFinding(findings, "ERROR", "EIGHT_EVIDENCE_CARDS_REQUIRED", "Exactly 8 evidence cards are required, one for each investigation mission.", null, "evidences");
        }
        Set<Integer> orders = new LinkedHashSet<>();
        for (int i = 0; i < evidences.size(); i++) {
            AiEpisodeDraftResponse.EvidenceDraft evidence = evidences.get(i);
            if (evidence == null) {
                addFinding(findings, "ERROR", "EVIDENCE_DETAILS_REQUIRED", "Evidence details are required.", null, "evidences[" + i + "]");
                continue;
            }
            if (blank(evidence.getTitle()) || blank(evidence.getTextSummary())) {
                addFinding(findings, "ERROR", "EVIDENCE_DETAILS_REQUIRED", "Evidence title and textSummary are required.", evidence.getSourceMissionOrder(), "evidences[" + i + "]");
            }
            Integer order = evidence.getSourceMissionOrder();
            if (order == null || order < 2 || order > 9 || !orders.add(order)) {
                addFinding(findings, "ERROR", "EVIDENCE_SOURCE_MISSION_REQUIRED", "Evidence must map uniquely to investigation mission orders 2-9.", order, "evidences[" + i + "].sourceMissionOrder");
            }
            String text = compact(String.join(" ", trim(evidence.getTitle()), trim(evidence.getTextSummary())));
            if (answerValues.stream().anyMatch(value -> !blank(value) && text.contains(value))) {
                addFinding(findings, "ERROR", "EVIDENCE_ANSWER_LEAK", "Evidence cards must not directly reveal final answer values.", order, "evidences[" + i + "]");
            }
        }
    }

    private void validatePlaceSafety(AiEpisodeDraftResponse.EpisodeDraft draft, List<AiEpisodeDraftValidationResponse.Finding> findings) {
        String text = String.join(" ", trim(draft.getFictionSynopsis()), trim(draft.getMissionDescription()), safeList(draft.getMissions()).stream().map(AiEpisodeDraftResponse.MissionDraft::getStoryText).map(this::trim).collect(Collectors.joining(" ")));
        if (containsAny(text, "실제로 발생한 살인", "실제 범죄 현장", "이 장소에서 살해", "이곳에서 실제로")) {
            addFinding(findings, "ERROR", "REAL_PLACE_CRIME_IMPLICATION", "실제 장소에서 실제 범죄가 발생한 것처럼 쓰면 안 됩니다.", null, "fictionSynopsis");
        }
        if (containsImmersionBreakingText(playerFacingText(draft))) {
            addFinding(findings, "ERROR", "IMMERSION_BREAKING_TEXT", "Player-facing text must not mention implementation, review, or fiction disclaimers.", null, "draft");
        }
    }

    private AiEpisodeDraftValidationResponse validationResponse(List<AiEpisodeDraftValidationResponse.Finding> findings) {
        long errors = findings.stream().filter(finding -> "ERROR".equals(finding.getSeverity())).count();
        long warns = findings.stream().filter(finding -> "WARN".equals(finding.getSeverity())).count();
        return AiEpisodeDraftValidationResponse.builder()
                .valid(errors == 0)
                .riskScore((int) Math.min(100, errors * 20 + warns * 5))
                .summary(errors == 0 ? "필수 범죄 미스터리 검증을 통과했습니다." : "수정이 필요한 항목이 " + errors + "개 남아 있습니다.")
                .findings(findings)
                .requiredFixes(findings.stream().filter(finding -> "ERROR".equals(finding.getSeverity())).map(AiEpisodeDraftValidationResponse.Finding::getMessage).distinct().toList())
                .publishChecklist(List.of(
                        "장소 정보는 배경 모티브로만 사용합니다.",
                        "8개 조사 단서는 하나의 사건 진실로 수렴해야 하며 범인, 흉기, 동기, 방법을 모두 추론할 수 있어야 합니다.",
                        "장소 힌트, 정답 누출, 깨진 한글이 없어야 합니다."))
                .build();
    }

    private String buildPlanPrompt(AiEpisodeDraftRequest request) {
        return """
                Return JSON only.
                Genre is fixed to CRIME_MYSTERY.
                Final answers are exactly four slots: CULPRIT, WEAPON, MOTIVE, METHOD.
                Every final answer keyword must be concrete and playable.
                Derive the four final answer values from the TourAPI story anchors below: historical incidents, cultural conflicts, records, materials, rituals, industries, disputes, or preservation facts.
                Do not choose a generic domain template just because a place is a museum, gallery, cafe, market, mountain, or station.
                The answer values should feel like a fictionalized case built from the anchors' concrete nouns and conflicts.
                Before returning JSON, internally verify that every slot would pass these server checks:
                - CULPRIT is a specific fictional Korean person name, not a role, occupation, historic name, literary name, mythic name, or public figure.
                - WEAPON includes both the ordinary carrier object and the harmful detail or substance.
                - MOTIVE is a concrete pressure, secret, dispute, contract, record, debt, ownership issue, or cover-up reason anchored in the TourAPI motifs.
                - METHOD is at least one complete Korean phrase that includes: harmful object/substance, where it was placed or delivered, how the victim contacts/uses it, and the action/resulting exposure.
                - METHOD must be more specific than WEAPON. It must not merely restate the weapon and must not be only a final result.
                - METHOD should follow this pattern: "<WEAPON or harmful substance> + <carrier/location> + <victim routine/contact> + <specific tampering/delivery verb>".
                - METHOD must be physically plausible. Match the victim interaction to the object: food, drink, and medicine can be eaten or drunk; pens, brushes, documents, gloves, cards, and tools should use contact, signing, opening, spraying, inhaling, or handling instead of eating/drinking.
                - Do not write unclear phrases such as "이식하여 섭취 유도", "내용물 섭취 유도", "몰래 사용하게 함", or "접촉하게 함" unless the exact carrier, contact point, and victim routine are named.
                If any slot fails the checklist, replace it before returning JSON.
                CULPRIT must be a new fictional modern Korean person. Do not use historical, literary, mythic, or public figure names such as 이몽룡, 성춘향, 홍길동, 임꺽정, 장보고, 유관순, 세종대왕, 이순신, 안중근, or 김구.
                METHOD must explain the concrete delivery route and action. Do not use vague result-only wording such as "혼란을 야기함", "몰래 투여함", "정신을 잃게 함", or "상태를 악화시킴".
                CULPRIT must be a Korean person name, optionally followed by role in parentheses, such as "오지훈(기록 담당자)" or "서민재". Never return only an occupation such as "큐레이터", "여행사 직원", or "관리자".
                WEAPON must identify a harmful object or substance with the dangerous detail, such as "마취 성분이 섞인 향수병", "독성 분말이 묻은 문서 봉투", or "독성 시약이 든 보온병"; never return only an ordinary object or container such as "향수병", "봉투", "약병", or "컵".
                MOTIVE must be a concrete reason that explains why the culprit acted, such as "위작 거래 은폐" or "불법 원정 사고 은폐"; never return generic words such as "은폐", "범죄", "복수", or "돈".
                METHOD must be a concrete crime process with object and action, such as "향수병에 마취 성분을 넣어 피해자에게 분사" or "문서 봉투 접착면에 독성 분말을 묻혀 피해자가 매일 장부를 열 때 손에 닿게 함"; never return a single verb or empty predicate such as "함", "넣기", "투여", or "조작".
                Bad example: CULPRIT="관리자", WEAPON="봉투", MOTIVE="은폐", METHOD="함".
                Good example: CULPRIT="서민재(기록 담당자)", WEAPON="독성 분말이 묻은 문서 봉투", MOTIVE="비공개 계약 문서 은폐", METHOD="문서 봉투 접착면에 독성 분말을 묻혀 피해자가 매일 장부를 열 때 손에 닿게 함".
                Do not create place hints, destination clues, or final-place guessing.
                Use the selected places and research context only as background motifs.
                Never imply that a real crime happened at a real place.
                Do not use immersion-breaking wording such as "real place", "fictional suspect", "needs admin review", or "RAG context".
                Never reuse stale sample answers or names: 강수진, 서민재, 윤서진, 독성 캡슐, 비밀 계약 은폐, 약병 바꿔치기, 마취 성분이 섞인 향수병, 비공개 계약 파기 은폐, 향수병에 마취 성분을 넣어 피해자에게 분사, 독성 분말이 묻은 문서 봉투, 비공개 계약 문서 은폐, 문서 봉투 접착면에 독성 분말을 묻혀 피해자가 매일 장부를 열 때 손에 닿게 함.
                Choose fresh culprit, weapon, motive, and method values that fit the selected route and case premise.

                Required JSON shape:
                {
                  "finalAnswerKeywords": [
                    {"slotId":"CULPRIT","type":"CULPRIT","label":"범인","keyword":"..."},
                    {"slotId":"WEAPON","type":"WEAPON","label":"흉기","keyword":"..."},
                    {"slotId":"MOTIVE","type":"MOTIVE","label":"동기","keyword":"..."},
                    {"slotId":"METHOD","type":"METHOD","label":"방법","keyword":"..."}
                  ]
                }

                Context:
                """ + buildAnswerPlanGenerationContext(request);
    }

    private String buildAnswerPlanGenerationContext(AiEpisodeDraftRequest request) {
        String historicalContext = buildTourApiHistoricalContext(request);
        List<String> storyAnchors = extractTourApiStoryAnchors(request);
        return String.join("\n",
                "Admin input:",
                "- area: " + safePromptText(request == null ? "" : request.getArea()),
                "- theme: " + safePromptText(request == null ? "" : request.getTheme()),
                "- playTime: " + safePromptText(request == null ? "" : request.getPlayTime()),
                "- genre: " + safePromptText(request == null ? "" : request.getSelectedGenreName()),
                "TourAPI story anchors to fictionalize:",
                storyAnchors.isEmpty() ? "(none)" : storyAnchors.stream().map(anchor -> "- " + safePromptText(anchor)).collect(Collectors.joining("\n")),
                "TourAPI historical/cultural motifs without place names or addresses:",
                blank(historicalContext) ? "(none)" : safePromptText(historicalContext));
    }

    private String buildDraftPrompt(AiEpisodeDraftRequest request) {
        return """
                Return JSON only, matching AiEpisodeDraftResponse.EpisodeDraft.
                Write in Korean.
                Genre is fixed to 범죄 미스터리.
                Final answers are exactly CULPRIT, WEAPON, MOTIVE, METHOD.
                Use the approved final answer values exactly. Do not invent a different culprit, weapon, motive, or method.
                The culprit answer value must be one of the 3 suspect displayName values.
                Suspect displayName must be a Korean personal name only, such as "서민재". Do not put role or job in displayName, such as "서민재(운영팀장)".
                Put roles and jobs in relationToVictim or shortDescription instead.
                All suspects, finalTruthSummary, rewardClue values, and evidences must converge to the approved final answers.
                finalTruthSummary must include the approved CULPRIT, WEAPON, MOTIVE, and METHOD values verbatim.
                Never reuse stale sample answers or names: 강수진, 독성 캡슐, 비밀 계약 은폐, 약병 바꿔치기, 독이 섞인 수면제 캡슐, 피해자의 매일 복용 약을 독성 캡슐로 바꿔치기.
                The final place is not a deduction answer. It unlocks automatically after all 8 investigation missions are cleared.
                Use TourAPI, external research notes, reference URLs, admin memo, and place descriptions only as background motifs.
                Do not state or imply that a real crime happened at any real place.
                Do not put selected real place names in fictionSynopsis as the murder scene. Create a fictional indoor location such as a private office, gallery office, research meeting room, archive room, or event preparation room.
                Do not write phrases that break immersion, including "실제 장소", "가상의 용의자", "관리자 검수", "RAG", "TourAPI", "외부 검색".
                Do not create place hints, destination clues, DESTINATION_HINT, DESTINATION_CLUE, FINAL_DESTINATION, or PLACE_HINT.
                Create 10 missions: order 1 START, orders 2-9 ANSWER_HINT, order 10 FINAL.
                The 8 investigation rewardClue values must be distinct deductive clues, not atmosphere.
                Never use generic clue text such as "조사 단서는 ... 판단에 필요한 근거를 제공합니다."
                Each investigation rewardClue must have exactly one internal targetKeywordType for answer-board grouping.
                Player-facing rewardClue text must still work as a mixed evidence chain: each clue should narrow more than one part of the truth through timeline, access, object trace, motive pressure, and routine sequence.
                Do not write visibly separated pairs such as "two culprit clues, two weapon clues". The eight clues should feel like one investigation that gradually converges on culprit, weapon, motive, and method together.
                rewardClue must not directly include final answer values, including the culprit name.
                Investigation rewardClue should avoid suspect display names in general. Use indirect labels such as "a suspect", "the person with access", "the owner of the fingerprint", or "the person shown in CCTV".
                Keep suspect display names in the suspects array and finalTruthSummary only, not in the 8 investigation rewardClue values.
                Before returning JSON, self-check every investigation rewardClue against the approved final answer values.
                If a rewardClue contains the exact approved CULPRIT, WEAPON, MOTIVE, or METHOD value, rewrite it as indirect evidence.
                Example: do not write the culprit name; write "the person with unrestricted office access" or "the owner of the extra fingerprint" instead.
                Example: do not write the weapon answer value; write toxicology, container, residue, or material facts that let the player infer it.
                Example: do not write the motive answer value; write documents, debt records, conflict messages, or benefit facts that imply it.
                Example: do not write the method answer value; write timing, access path, object state, or tampering sequence facts that imply it.
                Suspects must include exactly 3 people, each with alibiSummary and suspiciousPoint.
                Evidences must include exactly 8 cards mapped to sourceMissionOrder 2 through 9.
                actualHistorySummary must explain the historical/cultural motif behind the final place without saying the case is real.

                Do not omit or null these fields: episodeTitle, fictionSynopsis, finalTruthSummary, missions, suspects, evidences.

                Case blueprint:
                - Create a fictional victim, incident setup, cause or mechanism, limited suspect pool, and timeline.
                - The case overview must resemble a locked-room crime mystery: victim found dead or incapacitated, clear cause/mechanism, no obvious forced entry, and exactly 3 suspects present in the plausible incident window.
                - The culprit must be exactly one of the 3 suspects.
                - Each suspect needs a concrete alibiSummary and a concrete suspiciousPoint.
                - Suspect alibiSummary must include the claimed activity during the incident window and what record/witness partially supports it.
                - Suspect suspiciousPoint must include motive pressure, recent conflict, access, missing time, or benefit from the victim's death.
                - Build the truth so CULPRIT, WEAPON, MOTIVE, and METHOD are uniquely deducible only after combining all 8 clues.
                - The 8 clues must be evidence facts such as records, fingerprints, access logs, object traces, CCTV gaps, medical/toxicology facts, contracts, schedules, or witness observations.
                - Do not use simple mood, scenery, tourism facts, route directions, address numbers, sign text, or place-name extraction as deduction clues.
                - Every clue must add different information. Avoid repeating the same fact with different wording.
                - finalAnswerKeywordItems and finalAnswers must contain only the approved 4 answer slots.

                Target story pattern:
                - fictionSynopsis should be a full case overview, not route/place description. Format it like: "A prominent victim is found dead before an important event. Cause is identified. Door/timeline limits outside intrusion. No forced entry. Only 3 suspects remained inside. Victim had ownership, contract, inheritance, research, or business conflicts."
                - fictionSynopsis must include at least five concrete beats: victim identity, event scheduled soon, cause/mechanism, locked room or CCTV/time gap, exactly 3 suspects, and conflict background.
                - Suspect cards should read like: name and relation, alibi during the estimated incident time, supporting record, suspicious point, and why the person remains plausible.
                - Final truth should state culprit, weapon, method, and motive with the approved answer values, then explain why the other two suspects are weakened by the clues.
                - The 8 rewardClue values should read as a progressive evidence chain: restricted access trace tied to evidence storage, alibi narrowing tied to fingerprint or CCTV, material analysis tied to victim routine, object state tied to preparation timing, conflict document tied to benefit, pressure message tied to concealment, routine timing tied to execution, and final cross-check excluding the other two suspects.

                Slot-specific clue rules:
                - CULPRIT-targeted clues should primarily identify access, fingerprints, CCTV, alibi gaps, or exclusive opportunity, but may also mention object storage or timing if it helps narrow the whole truth. Do not write the culprit name.
                - WEAPON-targeted clues should primarily identify the object, material, residue, damage pattern, analysis result, or physical trace, but may also connect that trace to victim routine or access path.
                - MOTIVE-targeted clues should primarily identify revenge, conflict, loss, debt, inheritance, threat, benefit, betrayal, or concealment pressure, but may also connect the pressure to who had access.
                - METHOD-targeted clues should primarily identify replacement, tampering, injection, concealment, timing, access path, misdirection, or execution sequence, but may also mention why other suspects are weakened.
                - CULPRIT rewardClue should naturally include at least one of these Korean evidence anchors: 지문, 출입, 접근, 알리바이, 동선, 기록, CCTV, 목격, 권한, 일치, 용의자.
                - WEAPON rewardClue should naturally include at least one of these Korean evidence anchors: 흉기, 도구, 독극물, 물질, 성분, 검출, 흔적, 파손, 잔류물, 분석.
                - MOTIVE rewardClue should naturally include at least one of these Korean evidence anchors: 동기, 복수, 계약, 분쟁, 유산, 손실, 채무, 협박, 이익, 은폐, 배신, 갈등.
                - METHOD rewardClue should naturally include at least one of these Korean evidence anchors: 방법, 교체, 조작, 삽입, 주입, 은폐, 위장, 접근, 시간, 경로, 순서.

                Required mission contract:
                - START: markerType START, clueRole START, publicMarkerType START, finalPlace false.
                - Investigation: markerType ANSWER_HINT, clueRole ANSWER_HINT, publicMarkerType ANSWER_HINT, finalPlace false.
                - Orders 2-9 must each include a concrete rewardClue sentence. Never leave rewardClue null, empty, or templated.
                - Invalid rewardClue examples: "2번 조사 단서는 범인 판단에 필요한 근거를 제공합니다.", "이 단서는 정답 추리에 필요합니다.", "현장에서 단서가 발견되었다."
                - Valid rewardClue examples must name a concrete record, trace, timing, object state, document, witness observation, or analysis result.
                - Required internal tagging for answer board grouping:
                  order 2 targetKeywordType CULPRIT, but write the clue as access/opportunity evidence.
                  order 3 targetKeywordType CULPRIT, but write the clue as alibi narrowing or trace matching evidence.
                  order 4 targetKeywordType WEAPON, but write the clue as toxicology/material evidence.
                  order 5 targetKeywordType WEAPON, but write the clue as object/container evidence.
                  order 6 targetKeywordType MOTIVE, but write the clue as conflict/document/benefit evidence.
                  order 7 targetKeywordType MOTIVE, but write the clue as pressure/message/revenge evidence.
                  order 8 targetKeywordType METHOD, but write the clue as routine/timing evidence.
                  order 9 targetKeywordType METHOD, but write the clue as tampering/access-sequence evidence.
                - Do not make the clue text read like "culprit clue 1", "weapon clue 2", or any visible category checklist.
                - FINAL: markerType FINAL, clueRole FINAL_PLACE, publicMarkerType ANSWER_HINT, finalPlace true, unlockCondition ALL_INVESTIGATION_MISSIONS_CLEARED.

                Required JSON shape:
                {
                  "episodeTitle": "...",
                  "subtitle": "...",
                  "genre": "범죄 미스터리",
                  "fictionSynopsis": "사건 개요. 피해자, 사망/피해 정황, 제한된 용의자 범위, 시간대를 포함.",
                  "missionDescription": "8개 조사 단서로 범인, 흉기, 동기, 방법을 추론한다.",
                  "finalTruthSummary": "범인: <approved CULPRIT>. 흉기: <approved WEAPON>. 동기: <approved MOTIVE>. 방법: <approved METHOD>. 네 정답이 왜 유일한지 설명.",
                  "actualHistorySummary": "최종 장소의 역사/문화 모티브 설명.",
                  "missions": [
                    {"order":1,"markerType":"START","publicMarkerType":"START","clueRole":"START","finalPlace":false},
                    {"order":2,"markerType":"ANSWER_HINT","publicMarkerType":"ANSWER_HINT","clueRole":"ANSWER_HINT","finalPlace":false,"targetKeywordType":"CULPRIT","supportsKeywordSlots":["CULPRIT"],"rewardClue":"범인 이름 없이 접근 권한이나 알리바이 공백을 보여주는 증거"},
                    {"order":10,"markerType":"FINAL","publicMarkerType":"ANSWER_HINT","clueRole":"FINAL_PLACE","finalPlace":true,"unlockCondition":"ALL_INVESTIGATION_MISSIONS_CLEARED"}
                  ],
                  "suspects": [
                    {"displayName":"...","relationToVictim":"...","alibiSummary":"...","suspiciousPoint":"..."}
                  ],
                  "evidences": [
                    {"title":"...","type":"STORY_CLUE","textSummary":"rewardClue와 연결되는 구체적 증거","sourceMissionOrder":2}
                  ]
                }

                Context:
                """ + buildStoryGenerationContext(request);
    }

    private String buildStoryGenerationContext(AiEpisodeDraftRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("area: ").append(safePromptText(request == null ? "" : request.getArea())).append('\n');
        builder.append("theme: ").append(safePromptText(request == null ? "" : request.getTheme())).append('\n');
        builder.append("playTime: ").append(safePromptText(request == null ? "" : request.getPlayTime())).append('\n');
        appendApprovedAnswers(builder, request);
        builder.append("routePolicy:\n");
        builder.append("- Do not use real route place names, addresses, shop names, restaurant names, POI names, reference URLs, or external research notes in story generation.\n");
        builder.append("- Real route places are only mission map anchors. The server attaches mission.placeName after draft generation.\n");
        builder.append("- Write fictional crime locations only: private gallery office, locked study, research meeting room, archive room, preparation room, or similar indoor scenes.\n");
        builder.append("- Missions should be ordered investigation beats, not destination hints or place-name puzzles.\n");
        builder.append("missionOrders:\n");
        builder.append("- 1 START\n");
        builder.append("- 2 ANSWER_HINT CULPRIT access/opportunity clue\n");
        builder.append("- 3 ANSWER_HINT CULPRIT alibi/trace clue\n");
        builder.append("- 4 ANSWER_HINT WEAPON material/analysis clue\n");
        builder.append("- 5 ANSWER_HINT WEAPON object/container clue\n");
        builder.append("- 6 ANSWER_HINT MOTIVE conflict/document clue\n");
        builder.append("- 7 ANSWER_HINT MOTIVE pressure/message clue\n");
        builder.append("- 8 ANSWER_HINT METHOD routine/timing clue\n");
        builder.append("- 9 ANSWER_HINT METHOD tampering/sequence clue\n");
        builder.append("- 10 FINAL unlocks after all investigation missions\n");
        return builder.toString();
    }

    private String buildPlaceContext(AiEpisodeDraftRequest request) {
        List<AiEpisodeDraftRequest.PlaceInput> places = request == null || request.getPlaces() == null ? List.of() : request.getPlaces();
        StringBuilder builder = new StringBuilder();
        builder.append("area: ").append(safePromptText(request == null ? "" : request.getArea())).append('\n');
        builder.append("theme: ").append(safePromptText(request == null ? "" : request.getTheme())).append('\n');
        builder.append("playTime: ").append(safePromptText(request == null ? "" : request.getPlayTime())).append('\n');
        appendApprovedAnswers(builder, request);
        AiEpisodeDraftRequest.PlaceInput finalPlace = finalPlaceInput(request);
        if (finalPlace != null) {
            builder.append("finalPlaceMotif:\n");
            appendPlace(builder, finalPlace, places.indexOf(finalPlace) + 1);
        }
        builder.append("routePlaces:\n");
        for (int i = 0; i < places.size(); i++) {
            appendPlace(builder, places.get(i), i + 1);
        }
        return builder.toString();
    }

    private void appendApprovedAnswers(StringBuilder builder, AiEpisodeDraftRequest request) {
        Map<String, String> approved = approvedAnswers(request);
        builder.append("approvedFinalAnswers:\n");
        for (String slot : SLOT_IDS) {
            builder.append("- ").append(slot).append(": ").append(safePromptText(approved.get(slot))).append('\n');
        }
    }

    private AiEpisodeDraftRequest.PlaceInput finalPlaceInput(AiEpisodeDraftRequest request) {
        if (request == null) return null;
        if (request.getFinalSpot() != null) return request.getFinalSpot();
        List<AiEpisodeDraftRequest.PlaceInput> places = request.getPlaces() == null ? List.of() : request.getPlaces();
        return places.stream()
                .filter(place -> "FINAL".equals(normalize(place.getRole())))
                .findFirst()
                .orElse(places.isEmpty() ? null : places.get(places.size() - 1));
    }

    private void appendPlace(StringBuilder builder, AiEpisodeDraftRequest.PlaceInput place, int order) {
        if (place == null) return;
        builder.append("- order: ").append(order).append('\n');
        builder.append("  role: ").append(safePromptText(place.getRole())).append('\n');
        builder.append("  name: ").append(safePromptText(place.getName())).append('\n');
        builder.append("  address: ").append(safePromptText(place.getAddress())).append('\n');
        builder.append("  description: ").append(safePromptText(place.getDescription())).append('\n');
        builder.append("  adminMemo: ").append(safePromptText(place.getAdminMemo())).append('\n');
        appendPromptList(builder, "visibleElements", place.getVisibleElements());
        appendPromptList(builder, "keywords", place.getKeywords());
        appendPromptList(builder, "usablePuzzleSources", place.getUsablePuzzleSources());
        appendPromptList(builder, "verificationNotes", place.getVerificationNotes());
        appendPromptList(builder, "externalResearchNotes", place.getExternalResearchNotes());
        appendPromptList(builder, "referenceUrls", place.getReferenceUrls());
        builder.append("  researchSourceSummary: ").append(safePromptText(place.getResearchSourceSummary())).append('\n');
    }

    private void appendPromptList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) return;
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safePromptText)
                .limit(8)
                .collect(Collectors.joining(" | "));
        if (!joined.isBlank()) {
            builder.append("  ").append(label).append(": ").append(joined).append('\n');
        }
    }

    private String safePromptText(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 700) {
            return normalized.substring(0, 700);
        }
        return normalized;
    }

    private String callGemini(String prompt) {
        String url = API_BASE_URL + "/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;
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

    private JsonNode parseJson(String value, String code) {
        try {
            return objectMapper.readTree(stripJsonFence(value));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, code, "Gemini 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private JsonNode draftJsonNode(JsonNode root) {
        if (root != null && root.has("draft") && root.path("draft").isObject()) {
            return root.path("draft");
        }
        if (root != null && root.has("data") && root.path("data").has("draft") && root.path("data").path("draft").isObject()) {
            return root.path("data").path("draft");
        }
        return root;
    }

    private String stripJsonFence(String value) {
        String text = trim(value);
        if (text.startsWith("```")) text = text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        return text;
    }

    private void ensureApiKey() {
        if (blank(geminiApiKey)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_API_KEY_MISSING", "Gemini API 키가 설정되어 있지 않습니다.");
    }

    private void validatePlaces(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null || request.getPlaces().size() != 10) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLACE_COUNT", "AI 초안 생성에는 정확히 10개 장소가 필요합니다.");
    }

    private void normalizeFinalAnswerKeywordItems(AiEpisodeDraftRequest request) {
        if (request == null || (request.getFinalAnswerKeywordItems() != null && !request.getFinalAnswerKeywordItems().isEmpty())) return;
        if (request.getFinalAnswers() == null) return;
        Map<String, String> fromFinalAnswers = new LinkedHashMap<>();
        putIfNotBlank(fromFinalAnswers, "CULPRIT", request.getFinalAnswers().getCulprit());
        putIfNotBlank(fromFinalAnswers, "WEAPON", request.getFinalAnswers().getWeapon());
        putIfNotBlank(fromFinalAnswers, "MOTIVE", request.getFinalAnswers().getMotive());
        putIfNotBlank(fromFinalAnswers, "METHOD", request.getFinalAnswers().getMethod());
        if (SLOT_IDS.stream().anyMatch(slot -> blank(fromFinalAnswers.get(slot)))) return;
        List<AiEpisodeDraftRequest.AnswerKeywordInput> items = new ArrayList<>();
        for (String slot : SLOT_IDS) {
            AiEpisodeDraftRequest.AnswerKeywordInput item = new AiEpisodeDraftRequest.AnswerKeywordInput();
            item.setSlotId(slot);
            item.setType(slot);
            item.setLabel(SLOT_LABELS.get(slot));
            item.setDisplayType(SLOT_LABELS.get(slot));
            item.setKeyword(fromFinalAnswers.get(slot));
            items.add(item);
        }
        request.setFinalAnswerKeywordItems(items);
    }

    private void validateFinalAnswerContract(AiEpisodeDraftRequest request) {
        Map<String, String> values = approvedAnswers(request);
        if (SLOT_IDS.stream().anyMatch(slot -> blank(values.get(slot)))) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FINAL_ANSWER_KEYWORDS", "최종 정답 키워드는 범인, 흉기, 동기, 방법 4개를 모두 포함해야 합니다.");
        List<String> weakSlots = SLOT_IDS.stream().filter(slot -> weakFinalAnswerKeyword(slot, values.get(slot))).toList();
        if (!weakSlots.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_FINAL_ANSWER_KEYWORDS", "최종 정답 키워드는 구체적인 인물, 물건, 동기, 범행 과정이어야 합니다: " + String.join(", ", weakSlots));
        }
    }

    private void repairWeakFinalAnswerKeywords(AiEpisodeDraftRequest request) {
        if (request == null) return;
        Map<String, String> values = approvedAnswers(request);
        String method = values.get("METHOD");
        if (weakMethodKeyword(compact(method))) {
            updateFinalAnswerKeyword(request, "METHOD", concreteMethodFor(values.get("WEAPON"), values.get("MOTIVE"), method));
        }
    }

    private String concreteMethodFor(String weapon, String motive, String currentMethod) {
        String text = compact(String.join(" ", trim(weapon), trim(motive), trim(currentMethod)));
        if (containsAny(text, "붓펜", "잉크", "서명", "위작", "전시", "감정")) {
            return "독성 잉크가 든 붓펜으로 감정 확인 서명란을 오염시킴";
        }
        if (containsAny(text, "향수", "분사")) {
            return "향수병에 마취 성분을 넣어 피해자에게 분사";
        }
        if (containsAny(text, "와인", "잔", "음료", "보온병", "마시")) {
            return "잔 가장자리에 수면제를 묻혀 피해자가 마시게 함";
        }
        if (containsAny(text, "약", "캡슐", "고산병", "복용")) {
            return "약 캡슐에 진정제를 섞어 피해자에게 복용시킴";
        }
        if (containsAny(text, "봉투", "문서", "분말")) {
            return "문서 봉투 접착면에 독성 분말을 묻혀 피해자가 만지게 함";
        }
        return "현장 준비물에 유해 성분을 섞어 피해자에게 접촉시킴";
    }

    private void updateFinalAnswerKeyword(AiEpisodeDraftRequest request, String slot, String value) {
        if (request.getFinalAnswers() == null) {
            request.setFinalAnswers(new AiEpisodeDraftRequest.FinalAnswersInput());
        }
        if ("CULPRIT".equals(slot)) request.getFinalAnswers().setCulprit(value);
        if ("WEAPON".equals(slot)) request.getFinalAnswers().setWeapon(value);
        if ("MOTIVE".equals(slot)) request.getFinalAnswers().setMotive(value);
        if ("METHOD".equals(slot)) request.getFinalAnswers().setMethod(value);
        if (request.getFinalAnswerKeywordItems() == null) return;
        for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
            String itemSlot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
            if (slot.equals(itemSlot)) {
                item.setKeyword(value);
                if ("CULPRIT".equals(slot)) item.setPersonName(value);
            }
        }
    }

    private Map<String, String> approvedAnswers(AiEpisodeDraftRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        SLOT_IDS.forEach(slot -> result.put(slot, ""));
        if (request != null && request.getFinalAnswerKeywordItems() != null) {
            for (AiEpisodeDraftRequest.AnswerKeywordInput item : request.getFinalAnswerKeywordItems()) {
                String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
                if (SLOT_IDS.contains(slot)) putIfNotBlank(result, slot, answerKeywordValue(item));
            }
        }
        if (request != null && request.getFinalAnswers() != null) {
            putIfNotBlank(result, "CULPRIT", request.getFinalAnswers().getCulprit());
            putIfNotBlank(result, "WEAPON", request.getFinalAnswers().getWeapon());
            putIfNotBlank(result, "MOTIVE", request.getFinalAnswers().getMotive());
            putIfNotBlank(result, "METHOD", request.getFinalAnswers().getMethod());
        }
        return result;
    }

    private String answerKeywordValue(AiEpisodeDraftRequest.AnswerKeywordInput item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getSourceText());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    private String answerKeywordItemValue(AiEpisodeDraftResponse.AnswerKeywordItem item) {
        if (item == null) return "";
        String slot = normalize(defaultIfBlank(item.getSlotId(), item.getType()));
        String value = "CULPRIT".equals(slot) && !blank(item.getPersonName()) ? item.getPersonName() : defaultIfBlank(item.getKeyword(), item.getValue());
        return "CULPRIT".equals(slot) ? splitNameRole(value).name() : value;
    }

    private void putIfNotBlank(Map<String, String> values, String key, String value) {
        if (!blank(value)) values.put(key, value.trim());
    }

    private List<AiEpisodePlanResponse.AnswerSlotPlan> answerSlotPlans() {
        return SLOT_IDS.stream().map(slot -> AiEpisodePlanResponse.AnswerSlotPlan.builder().slotId(slot).label(SLOT_LABELS.get(slot)).description(SLOT_LABELS.get(slot) + " 정답 슬롯").minClueCount(2).build()).toList();
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> answerPlanKeywords(AiEpisodeDraftRequest request) {
        ensureApiKey();
        try {
            JsonNode root = parseJson(callGemini(buildPlanPrompt(request)), "GEMINI_PLAN_PARSE_FAILED");
            JsonNode keywords = root.has("finalAnswerKeywords") ? root.path("finalAnswerKeywords") : root;
            return sanitizePlanKeywords(keywords, "GEMINI");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini answer plan generation failed. reason={}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_FAILED", "Gemini 최종 정답 키워드 생성에 실패했습니다. 서버 템플릿으로 대체하지 않습니다.");
        }
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> deterministicAnswerKeywords(AiEpisodeDraftRequest request) {
        String context = compact(buildAnswerPlanContext(request));
        int seed = Math.floorMod(context.hashCode(), 10_000);
        String name = List.of("서민재", "박선우", "한지원", "오도윤", "정하린", "강태오", "윤서진", "최이현").get(seed % 8);
        String role = culpritRoleForContext(context, seed);
        CrimeAnswerTemplate template = crimeAnswerTemplateForContext(context, seed);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("CULPRIT", name);
        values.put("WEAPON", template.weapon());
        values.put("MOTIVE", template.motive());
        values.put("METHOD", template.method());
        List<String> weakSlots = SLOT_IDS.stream().filter(slot -> weakFinalAnswerKeyword(slot, values.get(slot))).toList();
        if (!weakSlots.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ANSWER_PLAN_TEMPLATE_INVALID", "서버 정답 템플릿이 구체성 검증을 통과하지 못했습니다: " + String.join(", ", weakSlots));
        }
        return SLOT_IDS.stream()
                .map(slot -> AiEpisodePlanResponse.AnswerKeyword.builder()
                        .slotId(slot)
                        .type(slot)
                        .label(SLOT_LABELS.get(slot))
                        .displayType(SLOT_LABELS.get(slot))
                        .keyword(values.get(slot))
                        .personName("CULPRIT".equals(slot) ? name : "")
                        .personRole("CULPRIT".equals(slot) ? role : "")
                        .role("CULPRIT".equals(slot) ? role : "")
                        .aliases("CULPRIT".equals(slot) ? List.of(name, role) : List.of())
                        .sourceBasis("서버 범죄 미스터리 정답 템플릿")
                        .sourceType("SERVER_TEMPLATE")
                        .build())
                .toList();
    }

    private String buildAnswerPlanContext(AiEpisodeDraftRequest request) {
        if (request == null) return "";
        return String.join(" ",
                trim(request.getArea()),
                trim(request.getTheme()),
                trim(request.getPlayTime()),
                trim(request.getSelectedGenreName()),
                buildTourApiAnswerSeedContext(request));
    }

    private String buildTourApiAnswerSeedContext(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null) return "";
        return request.getPlaces().stream()
                .filter(Objects::nonNull)
                .map(this::tourApiAnswerSeedContextForPlace)
                .filter(value -> !blank(value))
                .collect(Collectors.joining(" "));
    }

    private String tourApiAnswerSeedContextForPlace(AiEpisodeDraftRequest.PlaceInput place) {
        List<String> fragments = new ArrayList<>();
        Stream.of(place.getResearchSourceSummary())
                .map(this::safePromptText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        Stream.of(
                        place.getKeywords(),
                        place.getVerificationNotes(),
                        place.getExternalResearchNotes()
                )
                .flatMap(values -> safeList(values).stream())
                .map(this::safePromptText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        return safePromptText(String.join(" ", fragments));
    }

    private List<String> extractTourApiStoryAnchors(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null) return List.of();
        LinkedHashSet<String> anchors = new LinkedHashSet<>();
        for (AiEpisodeDraftRequest.PlaceInput place : request.getPlaces()) {
            if (place == null) continue;
            Stream.of(
                            place.getResearchSourceSummary(),
                            place.getDescription(),
                            place.getAdminMemo()
                    )
                    .map(this::cleanStoryAnchor)
                    .filter(value -> !blank(value))
                    .forEach(anchors::add);
            Stream.of(
                            place.getExternalResearchNotes(),
                            place.getVerificationNotes(),
                            place.getKeywords()
                    )
                    .flatMap(values -> safeList(values).stream())
                    .map(this::cleanStoryAnchor)
                    .filter(value -> !blank(value))
                    .forEach(anchors::add);
            if (anchors.size() >= 3) break;
        }
        return anchors.stream().limit(3).toList();
    }

    private String cleanStoryAnchor(String value) {
        String text = safePromptText(value);
        if (blank(text)) return "";
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 120 ? text.substring(0, 120).trim() : text;
    }

    private void attachPlanSourceBasis(List<AiEpisodePlanResponse.AnswerKeyword> keywords, List<String> storyAnchors) {
        if (keywords == null || keywords.isEmpty() || storyAnchors == null || storyAnchors.isEmpty()) return;
        String basis = String.join(" / ", storyAnchors);
        for (AiEpisodePlanResponse.AnswerKeyword keyword : keywords) {
            if (keyword != null) {
                keyword.setSourceBasis(basis);
            }
        }
    }

    private String buildTourApiHistoricalContext(AiEpisodeDraftRequest request) {
        if (request == null || request.getPlaces() == null) return "";
        return request.getPlaces().stream()
                .filter(Objects::nonNull)
                .map(this::tourApiHistoricalContextForPlace)
                .filter(value -> !blank(value))
                .collect(Collectors.joining(" "));
    }

    private String tourApiHistoricalContextForPlace(AiEpisodeDraftRequest.PlaceInput place) {
        List<String> fragments = new ArrayList<>();
        Stream.of(
                        place.getDescription(),
                        place.getResearchSourceSummary()
                )
                .map(this::safePromptText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        Stream.of(
                        place.getKeywords(),
                        place.getVerificationNotes(),
                        place.getExternalResearchNotes()
                )
                .flatMap(values -> safeList(values).stream())
                .map(this::safePromptText)
                .filter(value -> !blank(value))
                .forEach(fragments::add);
        return safePromptText(String.join(" ", fragments));
    }

    private String culpritRoleForContext(String context, int seed) {
        if (containsAny(context, "출입", "계약", "명부", "내부 고발")) return List.of("보안 운영팀장", "계약 관리 담당자", "내부 감사 담당자").get(seed % 3);
        if (containsAny(context, "안전 점검", "안전점검", "소독제", "시설 점검", "시설점검", "관리 책임", "관리책임")) return List.of("시설 안전 담당자", "운영 관리 책임자", "점검 기록 담당자").get(seed % 3);
        if (containsAny(context, "항만", "화물", "밀수", "장부", "서류", "봉투")) return List.of("물류 운영팀장", "기록 보관 담당자", "감사 대응 담당자").get(seed % 3);
        if (containsAny(context, "미술", "전시", "갤러리", "박물관", "작품", "화랑")) return List.of("큐레이터", "관장", "전시기획자").get(seed % 3);
        if (containsAny(context, "여행", "산", "전망", "케이블", "숙소", "관광")) return List.of("여행사 직원", "현장 가이드", "숙소 매니저").get(seed % 3);
        if (containsAny(context, "카페", "식당", "시장", "와인", "음료", "주점")) return List.of("매장 매니저", "소믈리에", "행사 케이터링 담당자").get(seed % 3);
        if (containsAny(context, "학교", "도서관", "연구", "실험", "기록관")) return List.of("연구원", "기록관리자", "조교").get(seed % 3);
        return List.of("운영팀장", "행사 담당자", "시설 관리자").get(seed % 3);
    }

    private CrimeAnswerTemplate crimeAnswerTemplateForContext(String context, int seed) {
        if (containsAny(context, "출입", "계약", "명부", "내부 고발")) {
            return new CrimeAnswerTemplate("독성 접착제가 묻은 출입카드", "내부 고발 계약 문서 은폐", "출입카드 접촉면에 독성 접착제를 발라 피해자가 사용하게 함");
        }
        if (containsAny(context, "안전 점검", "안전점검", "소독제", "시설 점검", "시설점검", "관리 책임", "관리책임")) {
            return new CrimeAnswerTemplate("환각 성분이 주입된 손 소독제", "안전 점검 부실 은폐", "손 소독제 용기에 환각 성분을 주입해 피해자가 사용하게 함");
        }
        if (containsAny(context, "항만", "화물", "밀수", "장부", "서류", "봉투")) {
            return new CrimeAnswerTemplate("독성 방부제가 묻은 항만 서류 봉투", "밀수 장부 은폐", "피해자가 매일 확인하던 화물 인수 서류를 독성 봉투로 바꿔치기");
        }
        if (containsAny(context, "미술", "전시", "갤러리", "박물관", "작품", "화랑")) {
            List<CrimeAnswerTemplate> artTemplates = List.of(
                    new CrimeAnswerTemplate("독성 안료가 묻은 감정용 장갑", "작품 소유권 분쟁 은폐", "감정용 장갑 안쪽에 독성 안료를 묻혀 피해자가 작품을 확인하며 접촉하게 함"),
                    new CrimeAnswerTemplate("마취 성분이 섞인 보존 처리 스프레이", "복원 기록 조작 은폐", "보존 처리 스프레이에 마취 성분을 섞어 피해자가 작품 상태를 점검할 때 흡입하게 함"),
                    new CrimeAnswerTemplate("독성 세척제가 든 붓 세척통", "감정 결과 조작 은폐", "붓 세척통에 독성 세척제를 넣어 피해자가 감정 도구를 정리하며 접촉하게 함")
            );
            return artTemplates.get(seed % artTemplates.size());
        }
        if (containsAny(context, "여행", "산", "전망", "케이블", "숙소", "관광")) {
            return new CrimeAnswerTemplate("진정제가 섞인 고산병 약 캡슐", "불법 원정 사고 은폐", "고산병 약 캡슐에 진정제를 섞어 피해자에게 복용시킴");
        }
        if (containsAny(context, "카페", "식당", "시장", "와인", "음료", "주점")) {
            return new CrimeAnswerTemplate("수면제가 묻은 와인잔", "투자금 횡령 발각 은폐", "와인잔 가장자리에 수면제를 묻혀 피해자가 마시게 함");
        }
        if (containsAny(context, "학교", "도서관", "연구", "실험", "기록관")) {
            return new CrimeAnswerTemplate("독성 시약이 섞인 연구실 음료", "연구 조작 기록 은폐", "피해자의 연구실 음료에 독성 시약을 섞어 마시게 함");
        }
        List<CrimeAnswerTemplate> defaults = List.of(
                new CrimeAnswerTemplate("마취 성분이 섞인 향수병", "비공개 계약 파기 은폐", "향수병에 마취 성분을 넣어 피해자에게 분사"),
                new CrimeAnswerTemplate("독성 분말이 묻은 초대장 봉투", "내부 고발 문서 은폐", "초대장 봉투 접착면에 독성 분말을 묻혀 피해자가 만지게 함"),
                new CrimeAnswerTemplate("진정제가 섞인 보온병 음료", "행사 예산 횡령 은폐", "보온병 음료에 진정제를 섞어 피해자에게 마시게 함")
        );
        return defaults.get(seed % defaults.size());
    }

    private record CrimeAnswerTemplate(String weapon, String motive, String method) {}

    private List<AiEpisodePlanResponse.AnswerKeyword> sanitizePlanKeywords(JsonNode node) {
        return sanitizePlanKeywords(node, "");
    }

    private List<AiEpisodePlanResponse.AnswerKeyword> sanitizePlanKeywords(JsonNode node, String sourceType) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node != null && node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                String slot = normalize(defaultIfBlank(item.path("slotId").asText(""), item.path("type").asText("")));
                if (!SLOT_IDS.contains(slot) && i < SLOT_IDS.size()) slot = SLOT_IDS.get(i);
                String keyword = trim(item.path("keyword").asText(""));
                if ("CULPRIT".equals(slot)) keyword = splitNameRole(keyword).name();
                if (SLOT_IDS.contains(slot) && !blank(keyword)) values.put(slot, keyword);
            }
        }
        if (!SLOT_IDS.stream().allMatch(slot -> !blank(values.get(slot)))) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_INVALID", "Gemini가 범인, 흉기, 동기, 방법 4개 정답 키워드를 모두 생성하지 못했습니다.");
        }
        List<String> weakSlots = SLOT_IDS.stream().filter(slot -> weakFinalAnswerKeyword(slot, values.get(slot))).toList();
        if (!weakSlots.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GEMINI_PLAN_INVALID", "Gemini가 구체적인 최종 정답 키워드를 생성하지 못했습니다: " + String.join(", ", weakSlots));
        }
        List<AiEpisodePlanResponse.AnswerKeyword> result = new ArrayList<>();
        for (int i = 0; i < SLOT_IDS.size(); i++) {
            String slot = SLOT_IDS.get(i);
            String value = values.get(slot);
            result.add(AiEpisodePlanResponse.AnswerKeyword.builder()
                    .slotId(slot)
                    .type(slot)
                    .label(SLOT_LABELS.get(slot))
                    .displayType(SLOT_LABELS.get(slot))
                    .keyword(value)
                    .personName("CULPRIT".equals(slot) ? value : "")
                    .aliases("CULPRIT".equals(slot) ? List.of(value) : List.of())
                    .sourceType(sourceType)
                    .build());
        }
        return result;
    }

    private AiEpisodePlanResponse.FinalAnswers planFinalAnswers(List<AiEpisodePlanResponse.AnswerKeyword> keywords) {
        Map<String, String> values = new LinkedHashMap<>();
        for (AiEpisodePlanResponse.AnswerKeyword keyword : keywords) values.put(normalize(keyword.getSlotId()), keyword.getKeyword());
        return AiEpisodePlanResponse.FinalAnswers.builder().culprit(values.get("CULPRIT")).weapon(values.get("WEAPON")).motive(values.get("MOTIVE")).method(values.get("METHOD")).build();
    }

    private List<String> answerValues(AiEpisodeDraftResponse.EpisodeDraft draft) {
        AiEpisodeDraftResponse.FinalAnswers answers = draft.getFinalAnswers();
        if (answers != null) return List.of(trim(answers.getCulprit()), trim(answers.getWeapon()), trim(answers.getMotive()), trim(answers.getMethod()));
        return draft.getFinalAnswerKeywords() == null ? List.of() : draft.getFinalAnswerKeywords();
    }

    private boolean containsForbiddenPlaceHint(AiEpisodeDraftResponse.MissionDraft mission) {
        String text = String.join(" ", trim(mission.getMarkerType()), trim(mission.getPublicMarkerType()), trim(mission.getClueRole()), trim(mission.getRewardClueSlotId()), trim(mission.getRewardClue()), trim(mission.getQuestionText()), trim(mission.getStoryText()), trim(mission.getPuzzleAnswerSource()));
        return containsAny(text, "DESTINATION_HINT", "DESTINATION_CLUE", "FINAL_DESTINATION", "PLACE_HINT", "장소 힌트", "장소 정답", "최종 장소를 찾", "최종 목적지를 찾");
    }

    private boolean weakFinalAnswerKeyword(String slot, String value) {
        String compacted = compact(value);
        if (blank(compacted)) return true;
        return switch (slot) {
            case "CULPRIT" -> weakCulpritKeyword(compacted);
            case "WEAPON" -> weakWeaponKeyword(compacted);
            case "MOTIVE" -> weakKeyword(compacted, 6, "범죄", "복수", "돈", "질투", "은폐", "원한", "분노", "실수", "협박", "비밀");
            case "METHOD" -> weakMethodKeyword(compacted);
            default -> false;
        };
    }

    private boolean weakCulpritKeyword(String compacted) {
        return !isSpecificKoreanPersonName(compacted);
    }

    private boolean weakWeaponKeyword(String compacted) {
        if (containsAny(compacted, "독성", "마취", "진정", "수면", "청산", "시안", "오염", "섞인", "묻힌", "주입", "변조", "유독", "환각")
                && containsAny(compacted, "약", "독", "캡슐", "병", "컵", "잔", "보온병", "향수", "시약", "분말", "액체", "주사", "칼", "도구", "붓펜", "펜", "잉크", "마커", "붓", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통")) {
            return false;
        }
        if (weakKeyword(compacted, 5, "약", "독", "흉기", "도구", "칼", "약물", "고산병약", "수면제", "캡슐", "향수병", "약병", "컵", "잔", "보온병", "붓펜", "펜", "연필", "마커", "붓", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통")) {
            return true;
        }
        return containsAny(compacted, "병", "컵", "잔", "보온병", "봉투", "상자", "붓펜", "펜", "연필", "마커", "붓", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통")
                && !containsAny(compacted, "독성", "마취", "진정", "수면", "청산", "시안", "오염", "섞인", "묻힌", "주입", "변조", "유독", "환각");
    }

    private boolean weakMethodKeyword(String compacted) {
        if (List.of("함", "넣기", "투여", "주입", "교체", "은폐", "조작", "살해", "독살", "바꿔치기", "유인", "방치", "사용", "사용함", "실행", "실행함", "시도", "시도함").contains(compacted)) {
            return true;
        }
        if (containsAny(compacted, "혼란을야기", "몰래투여", "정신을잃게", "상태를악화", "의식을잃게", "쓰러지게함")) {
            return true;
        }
        if (containsAny(compacted, "내용물섭취유도", "이식하여", "몰래이식", "사용하게함", "접촉하게함")
                && !containsAny(compacted, "피해자", "서명란", "손", "호흡", "흡입", "개봉", "장부", "문서", "봉투", "컵", "잔", "음료", "약")) {
            return true;
        }
        if (compacted.length() < 6) {
            return true;
        }
        boolean hasAction = containsAny(compacted, "넣", "섞", "바꿔", "교체", "투여", "분사", "주입", "묻혀", "먹여", "마시게", "흡입", "접촉", "조작", "유인", "오염", "서명", "바름", "발라", "칠함");
        boolean hasObjectOrVictim = containsAny(compacted, "피해자", "약", "캡슐", "병", "컵", "잔", "보온병", "향수", "음료", "시약", "문서", "서명", "서명란", "붓펜", "펜", "잉크", "마커", "봉투", "열쇠", "서랍", "준비물", "카드", "접착제", "소독제", "장갑", "안료", "스프레이", "세척제", "세척통");
        return !hasAction || !hasObjectOrVictim;
    }

    private boolean weakKeyword(String compacted, int minLength, String... genericValues) {
        if (compacted.length() < minLength) return true;
        for (String generic : genericValues) {
            if (compacted.equals(compact(generic))) return true;
        }
        return false;
    }

    private boolean isSpecificKoreanPersonName(String compacted) {
        Set<String> forbiddenNames = Set.of("이몽룡", "성춘향", "춘향", "몽룡", "홍길동", "임꺽정", "장보고", "유관순", "세종대왕", "이순신", "안중근", "김구");
        if (forbiddenNames.contains(compacted)) return false;
        Set<String> genericRoles = Set.of("여행사직원", "사업파트너", "피해자", "용의자", "관계자", "관리자", "직원", "가이드", "비서", "조카", "동료", "연구원", "큐레이터", "투자자", "운영자");
        if (genericRoles.contains(compacted)) return false;
        String namePattern = "(김|이|박|최|정|강|조|윤|장|임|한|오|서|신|권|황|안|송|전|홍|유|고|문|양|손|배|백|허|남|심|노|하|곽|성|차|주|우|구|민|류|나|진|지|엄|채|원|천|방|공|현|함|변|염|여|추|도|소|석|선|설|마|길|위|표|명|기|반|왕|금|옥|육|인|맹|제|모|탁|국|어|은|편|용|예|경|봉|사|부|가|복|태|목|형|계|피|두)[가-힣]{1,2}";
        return compacted.matches("^" + namePattern + "$")
                || compacted.matches("^" + namePattern + "\\([가-힣A-Za-z0-9·/\\-]+\\)$")
                || compacted.matches("^" + namePattern + "(팀장|대표|실장|매니저|가이드|직원|비서|교수|연구원|관리자|기자|작가|큐레이터|조교|의사|간호사|변호사|파트너)$");
    }

    private NameRole splitNameRole(String value) {
        String text = trim(value);
        if (blank(text)) return new NameRole("", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*([가-힣]{2,4})\\s*\\(([^)]+)\\)\\s*$")
                .matcher(text);
        if (matcher.matches()) {
            return new NameRole(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new NameRole(text, "");
    }

    private record NameRole(String name, String role) {}

    private String playerFacingText(AiEpisodeDraftResponse.EpisodeDraft draft) {
        if (draft == null) return "";
        List<String> values = new ArrayList<>();
        values.add(trim(draft.getEpisodeTitle()));
        values.add(trim(draft.getSubtitle()));
        values.add(trim(draft.getFictionSynopsis()));
        values.add(trim(draft.getMissionDescription()));
        values.add(trim(draft.getFinalQuestion()));
        values.add(trim(draft.getFinalTruthSummary()));
        values.add(trim(draft.getActualHistorySummary()));
        for (AiEpisodeDraftResponse.MissionDraft mission : safeList(draft.getMissions())) {
            values.add(trim(mission.getStoryText()));
            values.add(trim(mission.getQuestionText()));
            values.add(trim(mission.getRewardClue()));
            values.addAll(safeList(mission.getHints()));
        }
        for (AiEpisodeDraftResponse.SuspectDraft suspect : safeList(draft.getSuspects())) {
            values.add(trim(suspect.getDisplayName()));
            values.add(trim(suspect.getShortDescription()));
            values.add(trim(suspect.getRelationToVictim()));
            values.add(trim(suspect.getAlibiSummary()));
            values.add(trim(suspect.getSuspiciousPoint()));
        }
        for (AiEpisodeDraftResponse.EvidenceDraft evidence : safeList(draft.getEvidences())) {
            values.add(trim(evidence.getTitle()));
            values.add(trim(evidence.getTextSummary()));
        }
        return values.stream().filter(value -> !blank(value)).collect(Collectors.joining(" "));
    }

    private boolean containsImmersionBreakingText(String text) {
        if (blank(text)) return false;
        String lowered = text.toLowerCase(Locale.ROOT);
        return containsAny(lowered,
                "rag",
                "tourapi",
                "external search",
                "admin review",
                "needs admin review",
                "real place",
                "fictional suspect",
                "관리자 검수",
                "관리자 확인",
                "검수가 필요",
                "외부 검색",
                "실제 장소",
                "가상의 용의자",
                "허구의 용의자");
    }

    private boolean isGenericFallbackClue(String clue) {
        String compacted = compact(clue);
        return containsAny(compacted,
                "조사단서는범인판단에필요한근거를제공합니다",
                "조사단서는흉기판단에필요한근거를제공합니다",
                "조사단서는동기판단에필요한근거를제공합니다",
                "조사단서는방법판단에필요한근거를제공합니다",
                "판단에필요한근거를제공합니다");
    }

    private boolean isSlotRelevantClue(String target, String clue) {
        if (blank(target) || blank(clue)) return true;
        String compacted = compact(clue);
        if ("CULPRIT".equals(target) && containsAny(compacted, "인물", "인력", "한명", "행동", "걸음걸이", "모습", "증언", "보조", "직원", "연구원", "서재", "누락", "확인")) {
            return true;
        }
        if ("MOTIVE".equals(target) && containsAny(compacted, "해고", "계약", "분쟁", "유산", "손실", "채무", "협박", "이익", "이득", "재정", "금전", "수익", "상속", "불만", "갈등", "불화", "통보", "문자", "메모", "메시지", "연락", "기록", "격앙", "분노", "감정", "복수")) {
            return true;
        }
        if ("METHOD".equals(target) && containsAny(compacted, "교환", "약병", "약함", "약통", "약물", "복용", "캡슐", "외형", "목격", "증언", "장면", "이용", "바꾼", "바꾸", "교체", "조작", "반복", "순서", "준비물", "사용", "서명란", "오염", "접촉")) {
            return true;
        }
        return switch (target) {
            case "CULPRIT" -> containsAny(compacted, "지문", "출입", "접근", "알리바이", "동선", "기록", "cctv", "목격", "권한", "일치", "용의자");
            case "WEAPON" -> containsAny(compacted, "흉기", "독", "독극물", "캡슐", "약", "수면제", "잔", "물질", "성분", "검출", "도구");
            case "MOTIVE" -> containsAny(compacted, "동기", "복수", "해고", "계약", "분쟁", "유산", "손실", "채무", "원한", "협박", "이익", "불만", "갈등", "언쟁", "징계", "배제", "문자", "메모", "메시지", "연락", "기록", "격앙", "분노", "감정");
            case "METHOD" -> containsAny(compacted, "방법", "바꿔치기", "교체", "조작", "혼입", "투입", "주입", "희석", "위조", "제조", "복용", "캡슐", "접근", "시간", "경로", "열쇠", "봉인", "반복", "순서", "준비물", "사용", "서명란", "오염", "접촉");
            default -> true;
        };
    }

    private boolean contradictsCulpritWithinSuspects(String clue) {
        String compacted = compact(clue);
        if (blank(compacted)) return false;
        boolean allSuspects = containsAny(compacted, "용의자세명", "용의자3명", "세용의자", "모든용의자", "용의자전원");
        boolean excludesAll = containsAny(compacted, "모두다르", "전부다르", "일치하지않", "불일치", "해당하지않");
        return allSuspects && excludesAll;
    }

    private String defaultTargetKeywordType(int investigationIndex) {
        return SLOT_IDS.get(Math.min(3, Math.max(0, investigationIndex / 2)));
    }

    private List<String> ensureThreeHints(List<String> hints) {
        List<String> result = new ArrayList<>(safeList(hints).stream().filter(value -> !blank(value)).limit(3).toList());
        while (result.size() < 3) result.add("시간, 접근 권한, 물질 흔적을 비교하세요.");
        return result;
    }

    private List<String> mergeWarnings(List<String> warnings, AiEpisodeDraftValidationResponse validation) {
        List<String> result = new ArrayList<>();
        if (warnings != null) result.addAll(warnings);
        if (validation != null && validation.getFindings() != null) validation.getFindings().stream().filter(finding -> "ERROR".equals(finding.getSeverity())).map(AiEpisodeDraftValidationResponse.Finding::getMessage).forEach(result::add);
        return result;
    }

    private void addFinding(List<AiEpisodeDraftValidationResponse.Finding> findings, String severity, String code, String message, Integer missionOrder, String fieldPath) {
        findings.add(AiEpisodeDraftValidationResponse.Finding.builder()
                .severity(severity)
                .code(code)
                .message(koreanFindingMessage(code, message))
                .missionOrder(missionOrder)
                .fieldPath(fieldPath)
                .autoFixable(false)
                .fixType("REGENERATE")
                .build());
    }

    private String koreanFindingMessage(String code, String fallback) {
        return switch (code) {
            case "FOUR_FINAL_KEYWORD_ITEMS_REQUIRED" -> "finalAnswerKeywordItems에는 범인, 흉기, 동기, 방법 4개 슬롯과 값이 모두 필요합니다.";
            case "TEN_PLACES_REQUIRED" -> "미션 장소는 시작 1개, 조사 8개, 최종 1개로 총 10개여야 합니다.";
            case "ONE_START_REQUIRED" -> "시작 미션은 정확히 1개여야 합니다.";
            case "ONE_FINAL_REQUIRED" -> "최종 장소 미션은 정확히 1개여야 합니다.";
            case "FINAL_UNLOCK_CONDITION_REQUIRED" -> "최종 장소는 조사 미션 8개를 모두 완료한 뒤 자동 공개되어야 합니다.";
            case "FINAL_PLACE_MUST_NOT_BE_ANSWER_CLUE" -> "최종 장소는 최종 정답을 추리하는 단서로 사용하면 안 됩니다.";
            case "EIGHT_INVESTIGATION_CLUES_REQUIRED" -> "조사 미션은 정확히 8개여야 합니다.";
            case "DEDUCTIVE_CLUE_REQUIRED" -> "조사 미션에는 추리에 직접 기여하는 보상 단서가 필요합니다.";
            case "GENERIC_DEDUCTIVE_CLUE" -> "조사 단서가 너무 일반적입니다. 기록, 지문, 알리바이, 약물 분석처럼 구체적인 사건 정보로 작성해야 합니다.";
            case "DUPLICATE_CLUE" -> "조사 단서가 중복됩니다. 8개 단서는 서로 다른 정보를 제공해야 합니다.";
            case "DIRECT_ANSWER_LEAK" -> "조사 단서가 최종 정답 값을 직접 노출하고 있습니다.";
            case "TARGET_KEYWORD_TYPE_REQUIRED" -> "조사 단서에는 범인, 흉기, 동기, 방법 중 하나의 대상 슬롯이 필요합니다.";
            case "EXACTLY_ONE_SUPPORTED_SLOT_REQUIRED" -> "각 조사 단서는 하나의 정답 슬롯만 지원해야 합니다.";
            case "DEDUCTIVE_CLUE_NOT_ATMOSPHERE" -> "조사 단서는 분위기나 배경 묘사가 아니라 추리 근거여야 합니다.";
            case "CLUE_SLOT_MISMATCH" -> "조사 단서의 내용이 지정된 정답 슬롯과 맞지 않습니다.";
            case "CULPRIT_CLUE_CONTRADICTS_SUSPECT_SET" -> "범인 단서가 세 용의자 밖의 인물을 범인처럼 암시하고 있습니다.";
            case "DESTINATION_HINT_FORBIDDEN" -> "장소 힌트 또는 최종 장소 추리 구조는 사용할 수 없습니다.";
            case "ANSWER_SLOT_EXACT_SUPPORT_REQUIRED" -> "8개 조사 단서는 내부적으로 범인, 흉기, 동기, 방법을 모두 충분히 지원해야 합니다.";
            case "EXACTLY_THREE_SUSPECTS_REQUIRED" -> "용의자 카드는 정확히 3명이어야 합니다.";
            case "SUSPECT_DETAILS_REQUIRED" -> "각 용의자에는 이름, 알리바이, 의심 포인트가 필요합니다.";
            case "SUSPECT_NAMES_MUST_BE_UNIQUE" -> "용의자 3명은 서로 다른 이름이어야 합니다.";
            case "CULPRIT_MUST_BE_SUSPECT" -> "범인 정답은 용의자 카드 3명 중 한 명이어야 합니다.";
            case "FINAL_TRUTH_MUST_EXPLAIN_ANSWERS" -> "진실 요약에는 범인, 흉기, 동기, 방법 4개 정답이 모두 설명되어야 합니다.";
            case "EPISODE_TITLE_REQUIRED" -> "에피소드 제목이 필요합니다.";
            case "FICTION_SYNOPSIS_REQUIRED" -> "허구 사건 개요가 필요합니다.";
            case "FINAL_TRUTH_SUMMARY_REQUIRED" -> "최종 진실 요약에는 범인, 흉기, 동기, 방법이 설명되어야 합니다.";
            case "EIGHT_EVIDENCE_CARDS_REQUIRED" -> "조사 미션 8개에 대응하는 증거 카드 8개가 필요합니다.";
            case "EVIDENCE_DETAILS_REQUIRED" -> "증거 카드에는 제목과 요약이 필요합니다.";
            case "EVIDENCE_SOURCE_MISSION_REQUIRED" -> "증거 카드는 조사 미션 2번부터 9번까지 각각 하나씩 연결되어야 합니다.";
            case "IMMERSION_BREAKING_TEXT" -> "사용자에게 노출되는 문구에 구현 방식, 검수 표현, 허구 고지처럼 몰입을 깨는 표현이 포함되어 있습니다.";
            case "INVALID_FINAL_ANSWER_KEYWORDS" -> "최종 정답 키워드는 범인, 흉기, 동기, 방법 4개를 모두 포함해야 합니다.";
            default -> fallback;
        };
    }

    private <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private String defaultIfBlank(String value, String fallback) { return blank(value) ? fallback : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private String abbreviateForLog(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean containsAny(String text, String... targets) {
        if (blank(text) || targets == null) return false;
        for (String target : targets) if (!blank(target) && text.contains(target)) return true;
        return false;
    }
}
