package com.gojeom.drawer;

import com.gojeom.analysis.dto.AnalysisDtos.ResultResponse;
import com.gojeom.common.response.ApiResponse;
import com.gojeom.common.security.UserPrincipal;
import com.gojeom.drawer.dto.SavedResultDtos.DrawerResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 서랍 엔드포인트. (API.md §5 21~23번) */
@RestController
@RequestMapping("/api/v1/saved-results")
@RequiredArgsConstructor
public class SavedResultController {

    private final SavedResultService savedResultService;

    @GetMapping
    public ApiResponse<DrawerResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.ok(savedResultService.list(me.id()));
    }

    /** 결과지와 동일한 스키마. {@code viewState}만 {@code SAVED}다. */
    @GetMapping("/{savedResultId}")
    public ApiResponse<ResultResponse> detail(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID savedResultId) {
        return ApiResponse.ok(savedResultService.detail(me.id(), savedResultId));
    }

    @DeleteMapping("/{savedResultId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID savedResultId) {
        savedResultService.delete(me.id(), savedResultId);
    }
}
