package com.operation.seoul.plan.controller;

import com.operation.seoul.auth.security.CurrentUserResolver;
import com.operation.seoul.global.dto.ApiResponse;
import com.operation.seoul.plan.dto.UserPlanRequest;
import com.operation.seoul.plan.dto.UserPlanResponse;
import com.operation.seoul.plan.service.UserPlanService;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/users/me/plans")
@RequiredArgsConstructor
public class UserPlanController {
    private final UserPlanService planService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserPlanResponse>>> getMyPlans() {
        return ResponseEntity.ok(ApiResponse.ok("My plans.", planService.getMyPlans(currentUserResolver.requireCurrentUser())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserPlanResponse>> createPlan(@RequestBody UserPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Plan saved.", planService.createPlan(currentUserResolver.requireCurrentUser(), request)));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<ApiResponse<UserPlanResponse>> updatePlan(@PathVariable Long planId, @RequestBody UserPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Plan updated.", planService.updatePlan(currentUserResolver.requireCurrentUser(), planId, request)));
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable Long planId) {
        planService.deletePlan(currentUserResolver.requireCurrentUser(), planId);
        return ResponseEntity.ok(ApiResponse.ok("Plan deleted.", null));
    }
}
