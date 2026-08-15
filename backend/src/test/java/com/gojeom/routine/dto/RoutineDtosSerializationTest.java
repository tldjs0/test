package com.gojeom.routine.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gojeom.common.enums.RoutineSourceType;
import com.gojeom.drawer.dto.SavedResultDtos.DrawerItem;
import com.gojeom.routine.dto.RoutineDtos.NotificationView;
import com.gojeom.routine.dto.RoutineDtos.RoutineSummary;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 응답 직렬화가 API.md 계약과 맞는지.
 *
 * <p>둘 다 <b>실제로 어긋났던</b> 지점이다.
 * <ul>
 *   <li>전역 {@code default-property-inclusion: non_null}이 null 필드를 <b>키째로</b> 지운다.
 *       API.md가 null을 명시한 자리는 키가 남아 있어야 한다.</li>
 *   <li>{@code LocalTime}이 기본적으로 {@code "21:00:00"}으로 나간다.
 *       API.md는 {@code "21:00"}으로 계약했다.</li>
 * </ul>
 * 기능 테스트로는 잡히지 않고 프론트에서야 드러나는 종류라 여기서 고정한다.
 */
class RoutineDtosSerializationTest {

    /** 운영과 같은 설정. {@code application.yml}의 {@code non_null}을 그대로 재현한다. */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    @DisplayName("알림 시각은 초 없이 HH:mm으로 나간다 (API.md §6.7)")
    void 알림_시각_형식() throws Exception {
        String json = mapper.writeValueAsString(new NotificationView(false, LocalTime.of(21, 0)));

        assertThat(json).contains("\"21:00\"").doesNotContain("21:00:00");
    }

    @Test
    @DisplayName("경로 A 목표의 null 필드가 응답에서 사라지지 않는다")
    void 목표_요약의_null_키가_남는다() throws Exception {
        RoutineSummary summary = new RoutineSummary(UUID.randomUUID(),
                RoutineSourceType.FROM_ANALYSIS, null, "제목", null, LocalDate.now(), null, 5);

        String json = mapper.writeValueAsString(summary);

        // 프론트가 "값이 null"과 "필드가 없음"을 따로 다루지 않아도 되게 한다.
        assertThat(json).contains("\"category\":null")
                .contains("\"durationWeeks\":null")
                .contains("\"endDate\":null");
    }

    @Test
    @DisplayName("서랍 카드의 thumbnailUrl · progressRate가 null이어도 키가 남는다")
    void 서랍_카드의_null_키가_남는다() throws Exception {
        DrawerItem item = new DrawerItem(UUID.randomUUID(), UUID.randomUUID(),
                null, "제목", OffsetDateTime.now(), null);

        String json = mapper.writeValueAsString(item);

        assertThat(json).contains("\"thumbnailUrl\":null").contains("\"progressRate\":null");
    }
}
