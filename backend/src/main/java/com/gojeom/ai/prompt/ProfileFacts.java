package com.gojeom.ai.prompt;

import com.gojeom.common.enums.Category;
import com.gojeom.profile.entity.Inbody;
import com.gojeom.profile.entity.ProfileAnalysisSummary;
import java.math.BigDecimal;
import java.util.List;

/**
 * 프로필 정보를 프롬프트용 텍스트로 옮긴다.
 *
 * <p><b>입력하지 않은 항목은 아예 쓰지 않는다.</b> "미입력"이라고 적으면 모델이
 * 그 사실을 근거 삼아 추측을 시작한다. 없는 줄은 없는 채로 둔다. (PRD G-5)
 *
 * <p>우선순위는 <b>배열 순서가 곧 순위</b>이므로 인덱스를 그대로 번호로 쓴다.
 * (AGENTS.md 규칙 3)
 */
public final class ProfileFacts {

    private ProfileFacts() {
    }

    public static String render(List<Category> priorities, short heightCm, BigDecimal weightKg,
                                BigDecimal sleepHours, Inbody inbody, ProfileAnalysisSummary summary) {
        StringBuilder sb = new StringBuilder();

        sb.append("[사용자 우선순위]\n");
        for (int i = 0; i < priorities.size(); i++) {
            sb.append(i + 1).append("순위: ").append(priorities.get(i).label()).append('\n');
        }

        sb.append("\n[사용자가 입력한 신체 정보]\n");
        sb.append("키: ").append(heightCm).append("cm\n");
        sb.append("몸무게: ").append(weightKg).append("kg\n");
        if (sleepHours != null) {
            sb.append("평균 수면 시간: ").append(sleepHours).append("시간\n");
        }
        appendInbody(sb, inbody);
        appendSummary(sb, summary);
        return sb.toString();
    }

    private static void appendInbody(StringBuilder sb, Inbody inbody) {
        if (inbody == null || inbody.isEmpty()) {
            return;
        }
        sb.append("\n[인바디 (사용자 입력)]\n");
        append(sb, "체수분", inbody.bodyWaterL(), "L");
        append(sb, "단백질", inbody.proteinKg(), "kg");
        append(sb, "무기질", inbody.mineralKg(), "kg");
        append(sb, "체지방", inbody.bodyFatKg(), "kg");
        append(sb, "골격근량", inbody.skeletalMuscleKg(), "kg");
        // BMI는 무단위다. 시안의 kg 표기는 오류다. (ERD.md §5.2)
        append(sb, "BMI", inbody.bmi(), "");
    }

    private static void appendSummary(StringBuilder sb, ProfileAnalysisSummary summary) {
        if (summary == null) {
            return;
        }
        sb.append("\n[사진 기반 현재 상태 요약 (이전 단계에서 생성됨)]\n");
        if (summary.faceImpression() != null && !summary.faceImpression().isEmpty()) {
            sb.append("얼굴 인상: ").append(String.join(", ", summary.faceImpression())).append('\n');
        }
        if (summary.bodyRange() != null) {
            sb.append("체형: ").append(summary.bodyRange()).append('\n');
        }
        if (summary.healthNotes() != null && !summary.healthNotes().isEmpty()) {
            sb.append("건강 메모: ").append(String.join(", ", summary.healthNotes())).append('\n');
        }
    }

    private static void append(StringBuilder sb, String label, BigDecimal value, String unit) {
        if (value != null) {
            sb.append(label).append(": ").append(value).append(unit).append('\n');
        }
    }
}
