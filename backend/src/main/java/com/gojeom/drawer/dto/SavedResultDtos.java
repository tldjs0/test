package com.gojeom.drawer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 서랍 API 계약. (API.md §6.5) */
public final class SavedResultDtos {

    private SavedResultDtos() {
    }

    /** {@code POST /analyses/{id}/result/save} → 200 */
    public record SaveResponse(UUID savedResultId, OffsetDateTime savedAt) {
    }

    /**
     * {@code GET /saved-results} — <b>3개 섹션을 한 번에</b> 내려준다. (시안 19)
     *
     * <p>섹션별로 엔드포인트를 나누지 않는다. 서랍 화면이 세 섹션을 동시에 그리므로
     * 한 번의 요청으로 끝나는 편이 낫다.
     */
    public record DrawerResponse(
            List<DrawerItem> inProgress,
            List<DrawerItem> recent,
            List<DrawerItem> all) {
    }

    /**
     * 리스트 카드 1건.
     *
     * <p>{@code thumbnailUrl}·{@code progressRate}는 null일 수 있어
     * {@code @JsonInclude(ALWAYS)}로 키를 남긴다. 전역 {@code non_null} 설정이
     * 그냥 두면 키째로 지운다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DrawerItem(
            UUID savedResultId,
            UUID resultId,
            String thumbnailUrl,
            String title,
            OffsetDateTime analyzedAt,
            Double progressRate) {
    }
}
