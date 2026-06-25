package com.operation.seoul.episode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.operation.seoul.auth.domain.User;
import com.operation.seoul.casefile.domain.CaseSuspect;
import com.operation.seoul.casefile.repository.CaseFileRepository;
import com.operation.seoul.episode.domain.Episode;
import com.operation.seoul.episode.domain.FinalDeductionQuestion;
import com.operation.seoul.episode.domain.FinalDeductionSession;
import com.operation.seoul.episode.domain.UserEpisodeProgress;
import com.operation.seoul.episode.dto.ClueBoardResponse;
import com.operation.seoul.episode.dto.DeductionAskRequest;
import com.operation.seoul.episode.dto.DeductionAskResponse;
import com.operation.seoul.episode.dto.DeductionHypothesisRequest;
import com.operation.seoul.episode.dto.DeductionHypothesisResponse;
import com.operation.seoul.episode.repository.EpisodeRepository;
import com.operation.seoul.favorite.repository.EpisodeFavoriteRepository;
import com.operation.seoul.global.exception.ApiException;
import com.operation.seoul.location.service.OperationAreaResolver;
import com.operation.seoul.playeranalysis.service.PlayerAnalysisService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EpisodePlayServiceDeductionTest {
    private final EpisodeRepository episodeRepository = mock(EpisodeRepository.class);
    private final CaseFileRepository caseFileRepository = mock(CaseFileRepository.class);
    private final DeductionAiService deductionAiService = mock(DeductionAiService.class);
    private final EpisodePlayService service = new EpisodePlayService(
            episodeRepository,
            caseFileRepository,
            mock(EpisodeFavoriteRepository.class),
            new ObjectMapper(),
            mock(OperationAreaResolver.class),
            mock(MinigameProofValidator.class),
            mock(MinigameRetryVariantFactory.class),
            mock(PuzzleAttemptGuard.class),
            deductionAiService,
            mock(PlayerAnalysisService.class)
    );

    @Test
    void askDeductionRefusesDirectFinalKeywordAndAddsQuestionPenaltyWithoutAiCall() {
        mockDeductionState(session(0, 0), progress(0, 0));
        DeductionAskRequest request = new DeductionAskRequest();
        request.setQuestion("범인은 이몽룡이야?");

        DeductionAskResponse response = service.askDeduction(10L, request, user());

        assertEquals("REFUSED_DIRECT_REVEAL", response.getAnswerType());
        assertEquals(1, response.getQuestionCount());
        assertEquals(60, response.getClearTimePenaltySeconds());
        verifyNoInteractions(deductionAiService);
    }

    @Test
    void askDeductionUsesAiWithCaseContextForNormalQuestion() {
        mockDeductionState(session(0, 0), progress(0, 0));
        when(episodeRepository.findDeductionQuestions(10L)).thenReturn(List.of(previousQuestion()));
        when(deductionAiService.answer(any(), any(), any(), eq("피해자의 알리바이와 관련 있나요?")))
                .thenReturn(new DeductionAiService.Result("YES", "예. 알리바이의 빈틈과 관련이 있습니다."));
        DeductionAskRequest request = new DeductionAskRequest();
        request.setQuestion("피해자의 알리바이와 관련 있나요?");

        DeductionAskResponse response = service.askDeduction(10L, request, user());

        assertEquals("YES", response.getAnswerType());
        assertEquals("예. 알리바이의 빈틈과 관련이 있습니다.", response.getAnswerText());
        ArgumentCaptor<FinalDeductionQuestion> questionCaptor = ArgumentCaptor.forClass(FinalDeductionQuestion.class);
        verify(episodeRepository).insertDeductionQuestion(questionCaptor.capture());
        assertEquals("YES", questionCaptor.getValue().getAiAnswerType());
    }

    @Test
    void askDeductionAllowsAdminAfterLimit() {
        mockDeductionState(session(20, 0), progress(0, 1200));
        when(deductionAiService.answer(any(), any(), any(), eq("피해자의 알리바이와 관련 있나요?")))
                .thenReturn(new DeductionAiService.Result("YES", "예. 관련 있습니다."));
        DeductionAskRequest request = new DeductionAskRequest();
        request.setQuestion("피해자의 알리바이와 관련 있나요?");

        DeductionAskResponse response = service.askDeduction(10L, request, adminUser());

        assertEquals("YES", response.getAnswerType());
        assertEquals(21, response.getQuestionCount());
        assertEquals(999, response.getRemainingQuestionCount());
    }

    @Test
    void askDeductionRefusesSuspectAliasThatCanIdentifyCulprit() {
        mockDeductionState(session(0, 0), progress(0, 0));
        CaseSuspect suspect = new CaseSuspect();
        suspect.setDisplayName("한서윤");
        suspect.setAlias("기록 보관 담당자");
        when(caseFileRepository.findSuspects(1L)).thenReturn(List.of(suspect));
        DeductionAskRequest request = new DeductionAskRequest();
        request.setQuestion("기록 보관 담당자가 범인이야?");

        DeductionAskResponse response = service.askDeduction(10L, request, user());

        assertEquals("REFUSED_DIRECT_REVEAL", response.getAnswerType());
        verifyNoInteractions(deductionAiService);
    }

    @Test
    void clueBoardUsesExplicitSlotPrefixesWithoutRedistributingGenericAnswerClues() {
        UserEpisodeProgress progress = progress(0, 0);
        progress.setCollectedAnswerClues("[\"WEAPON::부검 결과 압흔의 폭이 좁고 모서리 자국이 남았다.\",\"MOTIVE::피해자가 계약 파기를 통보한 기록이 있다.\",\"슬롯 없는 구형 단서\"]");
        when(episodeRepository.findEpisodeById(1L)).thenReturn(episode());
        when(episodeRepository.findProgress(7L, 1L)).thenReturn(progress);
        when(caseFileRepository.findEvidences(1L)).thenReturn(List.of());

        ClueBoardResponse board = service.getClueBoard(1L, user());

        assertEquals(List.of("부검 결과 압흔의 폭이 좁고 모서리 자국이 남았다."), board.getWeaponClues());
        assertEquals(List.of("피해자가 계약 파기를 통보한 기록이 있다."), board.getMotiveClues());
        assertEquals(List.of(), board.getCulpritClues());
        assertEquals(List.of(), board.getMethodClues());
    }

    @Test
    void verifyHypothesisReturnsOnlyMatchedSlotCountAndAddsPenalty() {
        mockDeductionState(session(0, 0), progress(0, 0));
        DeductionHypothesisRequest request = new DeductionHypothesisRequest();
        request.setHypothesis("이몽룡이 망치로 비밀 거래 은폐 때문에 범행했다.");

        DeductionHypothesisResponse response = service.verifyDeductionHypothesis(10L, request, user());

        assertEquals(3, response.getMatchedSlotCount());
        assertEquals(4, response.getTotalSlotCount());
        assertEquals(1, response.getHypothesisCount());
        assertEquals(1, response.getRemainingHypothesisCount());
        assertEquals(300, response.getClearTimePenaltySeconds());
        ArgumentCaptor<FinalDeductionSession> sessionCaptor = ArgumentCaptor.forClass(FinalDeductionSession.class);
        verify(episodeRepository).updateDeductionSession(sessionCaptor.capture());
        assertEquals(1, sessionCaptor.getValue().getHypothesisCount());
    }

    @Test
    void verifyHypothesisBlocksAfterLimit() {
        mockDeductionState(session(0, 2), progress(0, 600));
        DeductionHypothesisRequest request = new DeductionHypothesisRequest();
        request.setHypothesis("이몽룡이 망치로 은폐했다.");

        ApiException exception = assertThrows(ApiException.class, () -> service.verifyDeductionHypothesis(10L, request, user()));

        assertEquals("HYPOTHESIS_LIMIT_EXCEEDED", exception.getCode());
    }

    @Test
    void verifyHypothesisAllowsAdminAfterLimit() {
        mockDeductionState(session(0, 2), progress(2, 600));
        DeductionHypothesisRequest request = new DeductionHypothesisRequest();
        request.setHypothesis("이몽룡이 망치로 비밀 거래 은폐 때문에 교살했다.");

        DeductionHypothesisResponse response = service.verifyDeductionHypothesis(10L, request, adminUser());

        assertEquals(4, response.getMatchedSlotCount());
        assertEquals(3, response.getHypothesisCount());
        assertEquals(999, response.getRemainingHypothesisCount());
    }

    @Test
    void verifyHypothesisAcceptsSemanticMethodSynonym() {
        mockDeductionState(session(0, 0), progress(0, 0));
        Episode crushingEpisode = episode();
        crushingEpisode.setFinalAnswer("범인: 이몽룡 / 흉기: 철제 선반 / 동기: 비밀 거래 은폐 / 방법: 압사");
        crushingEpisode.setFinalAnswerAliases("KW:이몽룡|철제 선반|비밀 거래 은폐|압사");
        when(episodeRepository.findEpisodeById(1L)).thenReturn(crushingEpisode);
        DeductionHypothesisRequest request = new DeductionHypothesisRequest();
        request.setHypothesis("이몽룡이 철제 선반으로 비밀 거래 은폐를 위해 피해자를 깔려죽게 했다.");

        DeductionHypothesisResponse response = service.verifyDeductionHypothesis(10L, request, user());

        assertEquals(4, response.getMatchedSlotCount());
    }

    @Test
    void verifyHypothesisCountsWeaponMentionedInsideMethodPhrase() {
        mockDeductionState(session(0, 0), progress(0, 0));
        Episode labelledEpisode = episode();
        labelledEpisode.setFinalAnswer("범인: 이몽룡 / 흉기: 망치 / 동기: 비밀 거래 은폐 / 방법: 둔기 가격");
        labelledEpisode.setFinalAnswerAliases("KW:CULPRIT=이몽룡|WEAPON=망치|MOTIVE=비밀 거래 은폐|METHOD=둔기 가격");
        when(episodeRepository.findEpisodeById(1L)).thenReturn(labelledEpisode);
        DeductionHypothesisRequest request = new DeductionHypothesisRequest();
        request.setHypothesis("이몽룡이 비밀 거래 은폐를 위해 망치로 피해자를 가격했다.");

        DeductionHypothesisResponse response = service.verifyDeductionHypothesis(10L, request, user());

        assertEquals(4, response.getMatchedSlotCount());
    }

    @Test
    void verifyHypothesisCountsCompoundWeaponFragmentAndMethodSynonym() {
        mockDeductionState(session(0, 0), progress(0, 0));
        Episode cabinetEpisode = episode();
        cabinetEpisode.setFinalAnswer("범인: 이몽룡 / 흉기: 기록보관함 / 동기: 비밀 거래 은폐 / 방법: 압사");
        cabinetEpisode.setFinalAnswerAliases("KW:CULPRIT=이몽룡|WEAPON=기록보관함|MOTIVE=비밀 거래 은폐|METHOD=압사");
        when(episodeRepository.findEpisodeById(1L)).thenReturn(cabinetEpisode);
        DeductionHypothesisRequest request = new DeductionHypothesisRequest();
        request.setHypothesis("피해자의 행동 루틴을 알고 보관함 고정레일을 풀고 밀어 넘어트려 피해자를 깔려죽게했나요?");

        DeductionHypothesisResponse response = service.verifyDeductionHypothesis(10L, request, user());

        assertEquals(2, response.getMatchedSlotCount());
    }

    private void mockDeductionState(FinalDeductionSession session, UserEpisodeProgress progress) {
        when(episodeRepository.findDeductionSession(10L)).thenReturn(session);
        when(episodeRepository.findEpisodeById(1L)).thenReturn(episode());
        when(episodeRepository.findProgress(7L, 1L)).thenReturn(progress);
    }

    private Episode episode() {
        Episode episode = new Episode();
        episode.setId(1L);
        episode.setTitle("테스트 사건");
        episode.setStatus("PUBLISHED");
        episode.setMaxDeductionQuestions(20);
        episode.setFinalQuestion("범인, 흉기, 동기, 방법을 밝혀라.");
        episode.setFinalTruthSummary("이몽룡이 망치와 교살 수법을 이용해 비밀 거래 은폐를 시도했다.");
        episode.setDeductionSecretFacts("범인은 알리바이의 빈틈을 만들었다.");
        episode.setDeductionForbiddenReveals("이몽룡, 망치, 비밀 거래 은폐, 교살");
        episode.setFinalAnswer("범인: 이몽룡 / 흉기: 망치 / 동기: 비밀 거래 은폐 / 방법: 교살");
        episode.setFinalAnswerAliases("KW:이몽룡|망치|비밀 거래 은폐|교살");
        return episode;
    }

    private FinalDeductionSession session(int questionCount, int hypothesisCount) {
        FinalDeductionSession session = new FinalDeductionSession();
        session.setId(10L);
        session.setUserId(7L);
        session.setEpisodeId(1L);
        session.setQuestionCount(questionCount);
        session.setHypothesisCount(hypothesisCount);
        session.setFinalGuessCount(0);
        session.setStatus("OPEN");
        return session;
    }

    private UserEpisodeProgress progress(int hypothesisCount, int penaltySeconds) {
        UserEpisodeProgress progress = new UserEpisodeProgress();
        progress.setId(11L);
        progress.setUserId(7L);
        progress.setEpisodeId(1L);
        progress.setCollectedAnswerClues("[\"단서1\",\"단서2\"]");
        progress.setCollectedDestinationClues("[]");
        progress.setCollectedStoryClues("[]");
        progress.setDeductionQuestionCount(0);
        progress.setHypothesisCount(hypothesisCount);
        progress.setClearTimePenaltySeconds(penaltySeconds);
        progress.setStatus("FINAL_READY");
        return progress;
    }

    private FinalDeductionQuestion previousQuestion() {
        FinalDeductionQuestion question = new FinalDeductionQuestion();
        question.setSessionId(10L);
        question.setUserQuestion("용의자의 동선이 중요해?");
        question.setAiAnswerType("YES");
        question.setAiAnswerText("예. 동선의 빈틈이 중요합니다.");
        return question;
    }

    private User user() {
        return User.builder()
                .id(7L)
                .role("ROLE_USER")
                .build();
    }

    private User adminUser() {
        return User.builder()
                .id(7L)
                .role("ROLE_ADMIN")
                .build();
    }
}
