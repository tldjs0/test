package com.gojeom.profile.dto;

import com.gojeom.profile.entity.Inbody;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** 인바디 서류 스캔 계약. (API.md §6.3) */
public final class InbodyScanDtos {

    private InbodyScanDtos() {
    }

    public record InbodyScanRequest(
            @NotBlank(message = "서류 사진을 먼저 올려주세요.")
            String documentKey) {
    }

    /**
     * <b>저장되지 않는 응답이다.</b> 프론트가 입력 폼을 채우는 데만 쓰고,
     * 사용자가 확인·수정한 뒤 저장 API를 호출해야 반영된다. (PRD G-8 · API.md C-12)
     *
     * @param unrecognized 읽지 못한 항목. 프론트는 빈 칸으로 두고 직접 입력을 유도한다
     */
    public record InbodyScanResponse(
            Inbody extracted,
            ScanConfidence confidence,
            List<String> unrecognized) {
    }

    /**
     * 인식 신뢰도.
     *
     * <p>API.md는 예시에 {@code "HIGH"}만 보여줄 뿐 값을 열거하지 않았다.
     * <b>6개 중 몇 개를 읽었는지</b>로 서버가 판정한다 — AI에게 자기 확신도를
     * 물으면 근거 없는 값이 나오지만, 읽은 개수는 사실이다.
     */
    public enum ScanConfidence {

        /** 6개 전부 */
        HIGH,

        /** 3~5개 */
        MEDIUM,

        /** 1~2개 */
        LOW;

        public static ScanConfidence of(int recognizedCount) {
            if (recognizedCount >= 6) {
                return HIGH;
            }
            return recognizedCount >= 3 ? MEDIUM : LOW;
        }
    }
}
