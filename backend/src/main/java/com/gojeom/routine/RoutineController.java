package com.gojeom.routine;

import com.gojeom.common.response.ApiResponse;
import com.gojeom.common.security.UserPrincipal;
import com.gojeom.routine.dto.RoutineDtos.RoutineCreateRequest;
import com.gojeom.routine.dto.RoutineDtos.RoutineCreateResponse;
import com.gojeom.routine.dto.RoutineDtos.RoutineDetailResponse;
import com.gojeom.routine.dto.RoutineDtos.RoutineListResponse;
import com.gojeom.routine.service.RoutineService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목표 엔드포인트. (API.md §5 24~27번)
 *
 * <p>생성은 <b>{@code 201}과 함께 결과를 바로 돌려준다.</b> 분석과 달리 폴링이 없다.
 * API.md §6.6의 계약이며, AI 호출이 4~6초라 폴링을 붙일 만큼 길지 않다.
 */
@RestController
@RequestMapping("/api/v1/routines")
@RequiredArgsConstructor
public class RoutineController {

    private final RoutineService routineService;

    /** 경로 2종을 {@code sourceType}으로 분기한다. 응답은 <b>항상 배열</b>이다. (C-15) */
    @PostMapping
    public ResponseEntity<ApiResponse<RoutineCreateResponse>> create(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody RoutineCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(routineService.create(me.id(), request)));
    }

    @GetMapping
    public ApiResponse<RoutineListResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(routineService.list(me.id()));
    }

    @GetMapping("/{routineId}")
    public ApiResponse<RoutineDetailResponse> detail(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID routineId) {
        return ApiResponse.ok(routineService.detail(me.id(), routineId));
    }

    @DeleteMapping("/{routineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID routineId) {
        routineService.delete(me.id(), routineId);
    }
}
