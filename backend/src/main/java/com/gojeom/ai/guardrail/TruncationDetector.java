package com.gojeom.ai.guardrail;

import com.gojeom.ai.AiStage;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 스키마 {@code maxLength}에 걸려 <b>말이 중간에서 잘린 출력</b>을 찾아낸다.
 *
 * <p>Structured Outputs는 {@code maxLength}를 디코딩 단계에서 강제한다. 모델이
 * 문장을 쓰기 시작하면 상한에서 그냥 끊긴다 — 오류가 아니라 정상 응답으로 온다.
 * 실제로 D2 검증에서 {@code emphasizePoints}가
 * <pre>"피부는 맑고 균일한 결로 정리하면 단정한 인상이 더 또"</pre>
 * 처럼 저장됐다. 프롬프트에서 "짧은 라벨"을 요구해 고쳤지만, 프롬프트는 보증이
 * 아니라 확률이다. 되돌아오면 로그로 드러나게 둔다.
 *
 * <p><b>실패시키지 않는다.</b> 잘린 라벨은 보기 나쁠 뿐 가드레일 위반이 아니다.
 * 이것 때문에 분석 전체를 실패로 돌리면 사용자가 더 손해다.
 * 안전 규칙 위반은 {@link OutputValidator}가 따로 막는다.
 */
@Slf4j
@Component
public class TruncationDetector {

    public void inspect(AiStage stage, String field, int cap, String value) {
        inspect(stage, field, cap, value == null ? List.of() : List.of(value));
    }

    public void inspect(AiStage stage, String field, int cap, List<String> values) {
        if (values == null) {
            return;
        }
        long capped = values.stream().filter(v -> v != null && v.length() >= cap).count();
        if (capped > 0) {
            // 값 자체는 남기지 않는다. 사용자 입력에서 파생된 텍스트다. (AGENTS.md 규칙 9)
            log.warn("AI 출력이 maxLength에 걸려 잘렸을 수 있다 stage={} field={} cap={} count={}",
                    stage, field, cap, capped);
        }
    }
}
