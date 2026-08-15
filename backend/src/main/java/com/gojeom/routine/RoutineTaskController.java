package com.gojeom.routine;

import com.gojeom.common.response.ApiResponse;
import com.gojeom.common.security.UserPrincipal;
import com.gojeom.routine.dto.RoutineDtos.TaskUpdateRequest;
import com.gojeom.routine.dto.RoutineDtos.TaskUpdateResponse;
import com.gojeom.routine.service.RoutineService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 태스크 완료 체크. (API.md §5 28번)
 *
 * <p>경로가 {@code /routine-tasks}로 목표와 분리되어 있어 컨트롤러도 따로 둔다.
 * 로직은 {@link RoutineService}가 함께 갖는다 — 완료 체크는 목표의 진행률을
 * 바꾸는 동작이라 같은 트랜잭션 안에 있어야 한다.
 */
@RestController
@RequestMapping("/api/v1/routine-tasks")
@RequiredArgsConstructor
public class RoutineTaskController {

    private final RoutineService routineService;

    @PatchMapping("/{taskId}")
    public ApiResponse<TaskUpdateResponse> update(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskUpdateRequest request) {
        return ApiResponse.ok(routineService.updateTask(me.id(), taskId, request));
    }
}
