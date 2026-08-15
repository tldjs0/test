package com.gojeom.analysis.service;

import com.gojeom.analysis.dto.AnalysisDtos.CategoryChangeView;
import com.gojeom.analysis.dto.AnalysisDtos.ComparisonImage;
import com.gojeom.analysis.dto.AnalysisDtos.DailyCareView;
import com.gojeom.analysis.dto.AnalysisDtos.Overview;
import com.gojeom.analysis.dto.AnalysisDtos.OverviewKeyword;
import com.gojeom.analysis.dto.AnalysisDtos.ResultResponse;
import com.gojeom.analysis.entity.AnalysisResult;
import com.gojeom.analysis.repository.AnalysisKeywordRepository;
import com.gojeom.analysis.repository.AnalysisRepository;
import com.gojeom.common.enums.ImageStatus;
import com.gojeom.common.enums.ResultViewState;
import com.gojeom.drawer.repository.SavedResultRepository;
import com.gojeom.profile.entity.Profile;
import com.gojeom.profile.repository.ProfileRepository;
import com.gojeom.storage.StorageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결과지 응답 조립. <b>{@code FRESH}와 {@code SAVED}가 같은 스키마를 공유한다.</b>
 * (API.md §6.4 · §6.5)
 *
 * <p>{@code GET /analyses/{id}/result}와 {@code GET /saved-results/{id}}가 이 클래스를
 * 함께 쓴다. 프론트가 결과 화면 컴포넌트를 재사용하려면 두 응답이 <b>글자 하나까지</b>
 * 같은 모양이어야 한다. 조립을 두 곳에 복사해두면 언젠가 갈라진다.
 *
 * <p>다른 것은 {@code viewState} 하나뿐이다.
 */
@Component
@RequiredArgsConstructor
public class ResultAssembler {

    /**
     * 결과 화면 면책 문구. <b>항상 노출한다. 숨기거나 접을 수 없다.</b>
     *
     * <p>이 문구가 없는 결과 화면은 배포할 수 없다. (PRD F-07 · AGENTS.md 규칙 5)
     * DB에 저장하지 않고 응답 상수로 내려보낸다. (ERD.md §3.7)
     */
    public static final String DISCLAIMER =
            "AI가 생성한 참고용 이미지와 관리 방향입니다. "
                    + "피부·건강 상태에 대한 의료적 진단이나 시술 결과를 의미하지 않습니다.";

    private final AnalysisKeywordRepository keywordRepository;
    private final SavedResultRepository savedResultRepository;
    private final AnalysisRepository analysisRepository;
    private final ProfileRepository profileRepository;
    private final StorageService storageService;

    public ResultResponse assemble(AnalysisResult result, ResultViewState viewState) {
        List<OverviewKeyword> keywords = keywordRepository
                .findByAnalysisIdAndSelectedTrueOrderByDisplayOrderAsc(result.getAnalysisId()).stream()
                .map(k -> new OverviewKeyword(k.getId(), k.getLabel(), true))
                .toList();

        return new ResultResponse(
                result.getId(),
                result.getAnalysisId(),
                viewState,
                result.getTitle(),
                result.getCreatedAt(),
                comparisonImage(result),
                new Overview(result.getSummary(), keywords, result.getKeepPoints(),
                        result.getEmphasizePoints(), result.getChangeIntensity()),
                result.getCategoryChanges().stream()
                        .map(c -> new CategoryChangeView(c.category(), c.description()))
                        .toList(),
                result.getDailyCares().stream()
                        .map(c -> new DailyCareView(c.title(), c.description()))
                        .toList(),
                savedResultRepository.existsByAnalysisResultId(result.getId()),
                DISCLAIMER);
    }

    /**
     * 비교 이미지 블록. 좌 {@code currentUrl}(현재) / 우 {@code peakUrl}(고점).
     *
     * <p><b>"현재"는 사용자의 프로필 사진을 그대로 쓴다.</b> 생성하는 것은 고점 쪽
     * 한 장뿐이다. 지금 모습은 이미 사용자가 올려둔 사진이 정답이라, 굳이 AI로
     * 다시 만들면 사실과 다른 "현재"를 보여주게 된다.
     *
     * <p>사진을 지운 프로필이면 {@code currentUrl}이 null이다. 프론트는 두 URL이
     * 모두 있어야 슬라이더를 그린다.
     */
    private ComparisonImage comparisonImage(AnalysisResult result) {
        if (result.getImageStatus() != ImageStatus.DONE) {
            return new ComparisonImage(result.getImageStatus(), null, null);
        }
        String currentKey = analysisRepository.findById(result.getAnalysisId())
                .flatMap(analysis -> profileRepository.findById(analysis.getProfileId()))
                .map(Profile::getPhotoKey)
                .orElse(null);

        return new ComparisonImage(
                ImageStatus.DONE,
                storageService.presignDownload(currentKey),
                storageService.presignDownload(result.getComparisonImageKey()));
    }
}
