package com.gojeom.analysis;

import com.gojeom.analysis.dto.AnalysisDtos.AnalysisAcceptedResponse;
import com.gojeom.analysis.dto.AnalysisDtos.AnalysisCreateRequest;
import com.gojeom.analysis.dto.AnalysisDtos.AnalysisStatusResponse;
import com.gojeom.analysis.dto.AnalysisDtos.KeywordListResponse;
import com.gojeom.analysis.dto.AnalysisDtos.KeywordSelectionRequest;
import com.gojeom.analysis.dto.AnalysisDtos.ResultResponse;
import com.gojeom.analysis.service.AnalysisPurgeService;
import com.gojeom.analysis.service.AnalysisService;
import com.gojeom.common.response.ApiResponse;
import com.gojeom.common.security.UserPrincipal;
import com.gojeom.drawer.SavedResultService;
import com.gojeom.drawer.dto.SavedResultDtos.SaveResponse;
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
 * 고점 분석 엔드포인트. (API.md §5 15~19번)
 *
 * <p>생성과 키워드 확정은 <b>{@code 202 Accepted}</b>다. 작업을 등록했다는 뜻이지
 * 완료가 아니다. 프론트는 {@code GET /analyses/{id}}로 진행 상태를 폴링한다.
 * (ARCHITECTURE.md A-2)
 */
@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AnalysisPurgeService analysisPurgeService;
    private final SavedResultService savedResultService;

    @PostMapping
    public ResponseEntity<ApiResponse<AnalysisAcceptedResponse>> create(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody AnalysisCreateRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(analysisService.create(me.id(), request)));
    }

    @GetMapping("/{analysisId}")
    public ApiResponse<AnalysisStatusResponse> status(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID analysisId) {
        return ApiResponse.ok(analysisService.getStatus(me.id(), analysisId));
    }

    @GetMapping("/{analysisId}/keywords")
    public ApiResponse<KeywordListResponse> keywords(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID analysisId) {
        return ApiResponse.ok(analysisService.getKeywords(me.id(), analysisId));
    }

    @PostMapping("/{analysisId}/keywords/selection")
    public ResponseEntity<ApiResponse<AnalysisAcceptedResponse>> selectKeywords(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID analysisId,
            @Valid @RequestBody KeywordSelectionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(analysisService.selectKeywords(me.id(), analysisId, request)));
    }

    @GetMapping("/{analysisId}/result")
    public ApiResponse<ResultResponse> result(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID analysisId) {
        return ApiResponse.ok(analysisService.getResult(me.id(), analysisId));
    }

    /**
     * 시안 17의 "서랍에 결과 저장하기". (API.md §6.4)
     *
     * <p>경로가 {@code /analyses} 아래라 서랍 컨트롤러가 아니라 여기에 둔다.
     * 로직은 {@link SavedResultService}가 갖는다.
     */
    @PostMapping("/{analysisId}/result/save")
    public ApiResponse<SaveResponse> save(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID analysisId) {
        return ApiResponse.ok(savedResultService.save(me.id(), analysisId));
    }

    /**
     * 시안 11의 "내 분석 전체 삭제". 확인 모달을 거친 뒤 호출한다. (API.md §6.4 · F-13)
     *
     * <p>사진 객체를 <b>즉시</b> 지운다. 서랍 항목도 함께 정리된다.
     * <b>목표는 남는다</b> — 모달이 그렇게 약속한다. (V4 마이그레이션)
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(@AuthenticationPrincipal UserPrincipal me) {
        analysisPurgeService.purgeAll(me.id());
    }
}
