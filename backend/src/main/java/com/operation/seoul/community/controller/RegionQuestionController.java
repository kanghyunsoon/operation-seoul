package com.operation.seoul.community.controller;

import com.operation.seoul.community.dto.RegionAnswerRequest;
import com.operation.seoul.community.dto.RegionAnswerResponse;
import com.operation.seoul.community.dto.RegionQuestionRequest;
import com.operation.seoul.community.dto.RegionQuestionResponse;
import com.operation.seoul.community.service.RegionQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions/{regionId}/questions")
@RequiredArgsConstructor
public class RegionQuestionController {

    private final RegionQuestionService questionService;

    @GetMapping
    public ResponseEntity<List<RegionQuestionResponse>> getQuestions(@PathVariable Long regionId) {
        return ResponseEntity.ok(questionService.getQuestions(regionId));
    }

    @PostMapping
    public ResponseEntity<RegionQuestionResponse> createQuestion(
            @PathVariable Long regionId,
            @Valid @RequestBody RegionQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.createQuestion(regionId, request));
    }

    @PutMapping("/{questionId}")
    public ResponseEntity<RegionQuestionResponse> updateQuestion(
            @PathVariable Long regionId,
            @PathVariable Long questionId,
            @Valid @RequestBody RegionQuestionRequest request) {
        return ResponseEntity.ok(questionService.updateQuestion(regionId, questionId, request));
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long regionId, @PathVariable Long questionId) {
        questionService.deleteQuestion(regionId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{questionId}/answers")
    public ResponseEntity<RegionAnswerResponse> createAnswer(
            @PathVariable Long regionId,
            @PathVariable Long questionId,
            @Valid @RequestBody RegionAnswerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.createAnswer(regionId, questionId, request));
    }

    @PutMapping("/{questionId}/answers/{answerId}")
    public ResponseEntity<RegionAnswerResponse> updateAnswer(
            @PathVariable Long regionId,
            @PathVariable Long questionId,
            @PathVariable Long answerId,
            @Valid @RequestBody RegionAnswerRequest request) {
        return ResponseEntity.ok(questionService.updateAnswer(regionId, questionId, answerId, request));
    }

    @DeleteMapping("/{questionId}/answers/{answerId}")
    public ResponseEntity<Void> deleteAnswer(
            @PathVariable Long regionId,
            @PathVariable Long questionId,
            @PathVariable Long answerId) {
        questionService.deleteAnswer(regionId, questionId, answerId);
        return ResponseEntity.noContent().build();
    }
}
