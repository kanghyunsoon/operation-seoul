package com.operation.seoul.community.service;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.community.domain.RegionAnswer;
import com.operation.seoul.community.domain.RegionQuestion;
import com.operation.seoul.community.dto.RegionAnswerRequest;
import com.operation.seoul.community.dto.RegionAnswerResponse;
import com.operation.seoul.community.dto.RegionQuestionRequest;
import com.operation.seoul.community.dto.RegionQuestionResponse;
import com.operation.seoul.community.repository.RegionQuestionRepository;
import com.operation.seoul.location.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionQuestionService {

    private final RegionRepository regionRepository;
    private final RegionQuestionRepository questionRepository;
    private final CurrentUserResolver currentUserResolver;

    public List<RegionQuestionResponse> getQuestions(Long regionId, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        List<RegionQuestionResponse> questions = questionRepository.findQuestionResponsesByRegionId(regionId, userId);
        questions.forEach(question -> {
            question.setMine(question.getUserId().equals(userId));
            List<RegionAnswerResponse> answers = questionRepository.findAnswerResponsesByQuestionId(question.getId());
            answers.forEach(answer -> answer.setMine(answer.getUserId().equals(userId)));
            question.setAnswers(answers);
        });
        return questions;
    }

    public RegionQuestionResponse createQuestion(Long regionId, RegionQuestionRequest request, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionQuestion question = new RegionQuestion();
        question.setRegionId(regionId);
        question.setUserId(userId);
        question.setTitle(cleanText(request.getTitle()));
        question.setContent(cleanText(request.getContent()));
        questionRepository.insertQuestion(question);
        return findQuestionResponse(regionId, question.getId(), userId);
    }

    public RegionQuestionResponse updateQuestion(Long regionId, Long questionId, RegionQuestionRequest request, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionQuestion question = requireQuestion(regionId, questionId);
        requireOwnerOrAdmin(question.getUserId(), userId);
        question.setTitle(cleanText(request.getTitle()));
        question.setContent(cleanText(request.getContent()));
        questionRepository.updateQuestion(question);
        return findQuestionResponse(regionId, questionId, userId);
    }

    public void deleteQuestion(Long regionId, Long questionId, Long fallbackUserId) {
        requireRegion(regionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionQuestion question = requireQuestion(regionId, questionId);
        requireOwnerOrAdmin(question.getUserId(), userId);
        int deleted = questionRepository.deleteQuestionById(questionId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "문의글을 삭제하지 못했습니다.");
        }
    }

    public RegionQuestionResponse toggleQuestionLike(Long regionId, Long questionId, Long fallbackUserId) {
        requireRegion(regionId);
        requireQuestion(regionId, questionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        if (questionRepository.countLikeByQuestionIdAndUserId(questionId, userId) > 0) {
            questionRepository.deleteQuestionLike(questionId, userId);
        } else {
            questionRepository.insertQuestionLike(questionId, userId);
        }
        return findQuestionResponse(regionId, questionId, userId);
    }

    public RegionAnswerResponse createAnswer(Long regionId, Long questionId, RegionAnswerRequest request, Long fallbackUserId) {
        requireRegion(regionId);
        requireQuestion(regionId, questionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionAnswer answer = new RegionAnswer();
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setContent(cleanText(request.getContent()));
        questionRepository.insertAnswer(answer);
        return findAnswerResponse(questionId, answer.getId(), userId);
    }

    public RegionAnswerResponse updateAnswer(Long regionId, Long questionId, Long answerId, RegionAnswerRequest request, Long fallbackUserId) {
        requireRegion(regionId);
        requireQuestion(regionId, questionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionAnswer answer = requireAnswer(questionId, answerId);
        requireOwnerOrAdmin(answer.getUserId(), userId);
        answer.setContent(cleanText(request.getContent()));
        questionRepository.updateAnswer(answer);
        return findAnswerResponse(questionId, answerId, userId);
    }

    public void deleteAnswer(Long regionId, Long questionId, Long answerId, Long fallbackUserId) {
        requireRegion(regionId);
        requireQuestion(regionId, questionId);
        Long userId = currentUserResolver.resolveUserId(fallbackUserId);
        RegionAnswer answer = requireAnswer(questionId, answerId);
        requireOwnerOrAdmin(answer.getUserId(), userId);
        int deleted = questionRepository.deleteAnswerById(answerId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "답변을 삭제하지 못했습니다.");
        }
    }

    private RegionQuestionResponse findQuestionResponse(Long regionId, Long questionId, Long userId) {
        return questionRepository.findQuestionResponsesByRegionId(regionId, userId).stream()
                .filter(item -> item.getId().equals(questionId))
                .peek(item -> {
                    item.setMine(item.getUserId().equals(userId));
                    List<RegionAnswerResponse> answers = questionRepository.findAnswerResponsesByQuestionId(item.getId());
                    answers.forEach(answer -> answer.setMine(answer.getUserId().equals(userId)));
                    item.setAnswers(answers);
                })
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문의글을 찾을 수 없습니다."));
    }

    private RegionAnswerResponse findAnswerResponse(Long questionId, Long answerId, Long userId) {
        return questionRepository.findAnswerResponsesByQuestionId(questionId).stream()
                .filter(item -> item.getId().equals(answerId))
                .peek(item -> item.setMine(item.getUserId().equals(userId)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "답변을 찾을 수 없습니다."));
    }

    private void requireRegion(Long regionId) {
        if (!regionRepository.existsById(regionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "작전을 찾을 수 없습니다.");
        }
    }

    private RegionQuestion requireQuestion(Long regionId, Long questionId) {
        RegionQuestion question = questionRepository.findQuestionByIdAndRegionId(questionId, regionId);
        if (question == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "문의글을 찾을 수 없습니다.");
        }
        return question;
    }

    private RegionAnswer requireAnswer(Long questionId, Long answerId) {
        RegionAnswer answer = questionRepository.findAnswerByIdAndQuestionId(answerId, questionId);
        if (answer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "답변을 찾을 수 없습니다.");
        }
        return answer;
    }

    private void requireOwnerOrAdmin(Long ownerId, Long userId) {
        if (!ownerId.equals(userId) && !currentUserResolver.resolveIsAdmin(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 수정할 수 있습니다.");
        }
    }

    private String cleanText(String value) {
        return value == null ? "" : value.trim();
    }
}
