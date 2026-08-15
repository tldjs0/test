# GO. — Backend 기술 아키텍처

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v1.0 |
| 최종 수정일 | 2026-08-13 |
| 기준 문서 | [PRD.md](../PRD.md) · [API.md](../API.md) · [ERD.md](../ERD.md) |
| 런타임 | **Java 21** · Spring Boot 3.3.5 · Gradle 8.10 |

---

## 1. 아키텍처 개요

```text
┌──────────────┐   HTTPS/JSON    ┌─────────────────────────────────────┐
│  React SPA   │ ───────────────▶│         Spring Boot API             │
│              │◀─────────────── │                                     │
└──────┬───────┘   presigned URL │  Controller → Service → Repository  │
       │                         │                  │                  │
       │  PUT (직접 업로드)        │                  ├──▶ AI Module     │──▶ OpenAI API
       │                         │                  ├──▶ Storage       │──▶ S3 호환
       ▼                         │                  └──▶ JPA           │──▶ PostgreSQL
┌──────────────┐                 └─────────────────────────────────────┘
│   Object     │
│   Storage    │
└──────────────┘
```

### 설계 원칙

| # | 원칙 |
| --- | --- |
| A-1 | **이미지 바이트가 API 서버를 통과하지 않는다.** 업로드·다운로드 모두 presigned URL로 클라이언트가 스토리지와 직접 통신한다. |
| A-2 | **AI 호출은 전부 비동기다.** HTTP 요청은 작업을 등록하고 즉시 `202`를 반환한다. |
| A-3 | **상태는 DB가 유일한 진실이다.** 메모리 큐·세션에 진행 상태를 두지 않는다. |
| A-4 | **가드레일은 프롬프트에 의존하지 않는다.** 서버가 출력을 후검증한다. |
| A-5 | 도메인별 패키지로 나누되 계층(Controller/Service/Repository)은 유지한다. |
| A-6 | Entity를 컨트롤러 밖으로 내보내지 않는다. 경계는 항상 DTO다. |

---

## 2. 패키지 구조

```text
backend/src/main/java/com/gojeom/
├─ GojeomApplication.java
│
├─ common/
│  ├─ response/
│  │  ├─ ApiResponse.java            { success, data } / { success, error }
│  │  └─ PageResponse.java
│  ├─ exception/
│  │  ├─ ErrorCode.java              API.md §4 에러 코드 enum (문구 포함)
│  │  ├─ BusinessException.java
│  │  └─ GlobalExceptionHandler.java @RestControllerAdvice
│  ├─ config/
│  │  ├─ SecurityConfig.java
│  │  ├─ AsyncConfig.java            분석용 스레드 풀
│  │  ├─ JpaConfig.java              Auditing
│  │  ├─ WebConfig.java              CORS
│  │  └─ OpenAiProperties.java       @ConfigurationProperties
│  └─ enums/
│     └─ Category.java               SKIN · BODY · HEALTH
│
├─ auth/
│  ├─ AuthController.java
│  ├─ AuthService.java
│  ├─ jwt/  JwtProvider · JwtAuthenticationFilter
│  └─ dto/
│
├─ user/
│  ├─ UserController.java            /users/me · 계정 삭제
│  ├─ UserService.java
│  ├─ entity/  User · Consent
│  ├─ repository/
│  └─ dto/
│
├─ profile/
│  ├─ ProfileController.java         등록 · 조회 · 수정 · 우선순위 · 사진 삭제
│  ├─ InbodyScanController.java      서류 OCR
│  ├─ service/  ProfileService · InbodyScanService
│  ├─ entity/   Profile
│  ├─ repository/
│  └─ dto/
│
├─ analysis/                         ★ 핵심 도메인
│  ├─ AnalysisController.java
│  ├─ service/
│  │  ├─ AnalysisService.java        생성 · 조회 · 키워드 확정
│  │  ├─ AnalysisPipeline.java       ★ 비동기 파이프라인 오케스트레이션
│  │  ├─ KeywordService.java
│  │  ├─ ResultService.java
│  │  └─ AnalysisSweeper.java        좀비 분석 정리 (@Scheduled)
│  ├─ entity/
│  │  ├─ Analysis.java
│  │  ├─ AnalysisReferenceImage.java
│  │  ├─ AnalysisKeyword.java
│  │  └─ AnalysisResult.java
│  ├─ repository/
│  └─ dto/
│
├─ drawer/
│  ├─ SavedResultController.java     서랍 3섹션
│  ├─ SavedResultService.java
│  ├─ entity/  SavedResult
│  └─ repository/
│
├─ routine/
│  ├─ RoutineController.java
│  ├─ service/
│  │  ├─ RoutineService.java
│  │  └─ RoutineGenerator.java       경로 A/B 분기 생성
│  ├─ entity/  Routine · RoutineTask
│  ├─ repository/
│  └─ dto/
│
├─ notification/
│  ├─ NotificationController.java
│  ├─ NotificationService.java
│  ├─ NotificationScheduler.java     @Scheduled 발송
│  ├─ entity/  NotificationSetting · DeviceToken
│  └─ repository/
│
├─ subscription/
│  ├─ SubscriptionController.java
│  ├─ service/  SubscriptionService · CreditService
│  ├─ entity/   Subscription · Payment
│  └─ repository/
│
├─ ai/                               ★ OpenAI 통합
│  ├─ OpenAiClient.java              HTTP 호출 · 재시도 · 타임아웃
│  ├─ prompt/
│  │  ├─ SystemPrompts.java          가드레일 G-1~G-8 공통 블록
│  │  ├─ ProfileAnalysisPrompt.java
│  │  ├─ InbodyOcrPrompt.java
│  │  ├─ KeywordExtractionPrompt.java
│  │  ├─ ResultGenerationPrompt.java
│  │  └─ RoutineGenerationPrompt.java
│  ├─ schema/
│  │  └─ JsonSchemas.java            API.md §7.2~7.4 스키마 상수
│  ├─ guardrail/
│  │  └─ OutputValidator.java        ★ 점수 패턴 · 금지어 후검증
│  ├─ image/
│  │  └─ ImageEditService.java       비교 이미지 생성
│  └─ job/
│     ├─ AiJob.java · AiJobRepository
│     └─ AiJobRecorder.java          토큰·지연·에러 기록
│
└─ storage/
   ├─ StorageService.java            presigned 발급 · 삭제
   ├─ ObjectKeyFactory.java          경로 규칙
   └─ StorageProperties.java
```

**패키지 배치 기준** — 도메인 우선, 그 안에서 계층. `analysis` 패키지가 가장 크므로 `service`를 하위 패키지로 분리했다.

---

## 3. 계층 규칙

```text
Controller  요청/응답 매핑, 검증(@Valid), 인증 주체 주입.  비즈니스 로직 없음
    ↓ DTO
Service     트랜잭션 경계, 도메인 규칙, 외부 연동 호출
    ↓ Entity
Repository  Spring Data JPA
```

| 규칙 | 내용 |
| --- | --- |
| L-1 | Controller는 Entity를 반환하지 않는다. 항상 Response DTO. |
| L-2 | `@Transactional`은 **Service에만** 붙인다. Controller·Repository에 붙이지 않는다. |
| L-3 | 조회 전용 메서드는 `@Transactional(readOnly = true)`. |
| L-4 | **외부 API 호출(OpenAI·스토리지)을 트랜잭션 안에서 하지 않는다.** 커넥션을 수십 초 잡는다. §5 참조. |
| L-5 | 리소스 소유권 검사는 Service 진입부에서 한다. 남의 것이면 `FORBIDDEN_RESOURCE`. |

---

## 4. 공통 응답과 예외

### ApiResponse

```java
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, data, null); }
    public static ApiResponse<Void> fail(ErrorCode code, Object details) { ... }
}
```

### ErrorCode

[API.md](../API.md) §4의 19개 코드를 enum으로 그대로 옮긴다. **사용자 노출 문구를 enum이 들고 있는다.**

```java
public enum ErrorCode {
    VALIDATION_ERROR(400, "입력값을 다시 확인해주세요."),
    NO_ANALYSIS_CREDIT(402, "분석권을 모두 사용했어요."),
    IMAGE_NO_FACE(422, "얼굴이 인식되지 않았어요. 정면을 향한 밝은 사진을 올려주세요."),
    INBODY_SCAN_FAILED(422, "서류를 읽지 못했어요. 직접 입력해주세요."),
    ANALYSIS_TIMEOUT(504, "분석이 지연되고 있어요. 다시 시도해주세요."),
    // ...
}
```

문구를 서버가 소유하는 이유 — 프론트가 코드별 문구 테이블을 따로 관리하지 않게 하기 위해서다. 문구 수정이 배포 한 번으로 끝난다.

`GlobalExceptionHandler`가 `BusinessException`, `MethodArgumentNotValidException`, 그 외 `Exception`을 각각 매핑한다. 예상 못 한 예외는 `AI_PROVIDER_ERROR`가 아니라 **500 + 스택 로깅**으로 처리하고, 사용자에게는 일반 문구를 준다.

---

## 5. 비동기 분석 파이프라인 ★

이 시스템의 핵심이다. [API.md](../API.md) §6.4와 [ERD.md](../ERD.md) §3.4의 상태 기계를 구현한다.

### 5.1 흐름

```text
POST /analyses
  ├─ 검증 (프로필 존재 · 분석권 > 0)
  ├─ Analysis(CREATED) 저장 ─── 트랜잭션 커밋
  ├─ 202 응답 반환
  └─ [커밋 후] @Async 파이프라인 시작
        │
        ├─ status = EXTRACTING
        ├─ OpenAI KEYWORD_EXTRACTION          (트랜잭션 밖)
        ├─ 키워드 저장 · status = KEYWORDS_READY
        │
        │   ⟵ 사용자가 그동안 키워드 선택 (시안 14)
        │
POST /analyses/{id}/keywords/selection
  ├─ 선택 저장 · status = GENERATING ─── 커밋
  ├─ 202 응답
  └─ [커밋 후] @Async
        ├─ OpenAI RESULT_GENERATION           (트랜잭션 밖)
        ├─ OutputValidator 후검증
        ├─ 트랜잭션: 결과 저장 + 분석권 차감 + status = DONE
        └─ 참고 사진이 있으면 별도 @Async 이미지 생성
              └─ image_status = DONE | FAILED
```

### 5.2 트랜잭션 밖에서 호출하기

OpenAI 호출은 수십 초가 걸린다. 트랜잭션 안에서 하면 DB 커넥션 풀이 금방 마른다.

```java
@Service
@RequiredArgsConstructor
public class AnalysisPipeline {

    @Async("analysisExecutor")
    public void runKeywordExtraction(UUID analysisId) {
        analysisTx.markExtracting(analysisId);              // 짧은 트랜잭션

        KeywordExtractionResult ai;
        try {
            ai = openAiClient.extractKeywords(...);         // ← 트랜잭션 밖
        } catch (Exception e) {
            analysisTx.markFailed(analysisId, resolveCode(e));
            return;
        }

        analysisTx.saveKeywords(analysisId, ai.keywords()); // 짧은 트랜잭션
    }
}
```

- `analysisTx`는 `@Transactional` 메서드만 가진 별도 빈이다. **같은 클래스 내부 호출은 프록시를 타지 않아** 트랜잭션이 걸리지 않으므로 반드시 빈을 분리한다.
- `@Async` 시작은 `TransactionalEventListener(phase = AFTER_COMMIT)`로 건다. 커밋 전에 시작하면 비동기 스레드가 아직 없는 행을 조회한다.

### 5.3 스레드 풀

```java
@Bean("analysisExecutor")
public Executor analysisExecutor() {
    var ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(8);
    ex.setMaxPoolSize(16);
    ex.setQueueCapacity(100);
    ex.setThreadNamePrefix("analysis-");
    ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return ex;
}
```

IO 대기가 대부분이라 CPU 코어 수보다 크게 잡는다. 이미지 생성은 더 느리고 실패율이 높아 **별도 풀(`imageExecutor`, core 4)** 로 분리해 텍스트 파이프라인을 막지 않게 한다.

### 5.4 좀비 분석 정리

**애플리케이션이 재시작되면 진행 중이던 `@Async` 작업은 사라진다.** DB에는 `EXTRACTING`인 행만 남아 클라이언트가 60초를 기다리다 타임아웃을 본다.

```java
@Scheduled(fixedDelay = 60_000)
public void sweepStale() {
    analysisRepository.failStale(Duration.ofMinutes(3));   // updated_at 기준
}
```

- 3분 이상 진행 상태에 머문 분석을 `FAILED` + `ANALYSIS_TIMEOUT`으로 전환한다.
- **분석권은 차감하지 않는다.**

> **MVP 전제** — 단일 인스턴스로 운영한다. 다중 인스턴스로 확장하면 `@Async`를 메시지 큐로 바꾸고 sweeper에 락을 걸어야 한다.

---

## 6. AI 모듈

### 6.1 OpenAiClient

| 항목 | 정책 |
| --- | --- |
| HTTP | `RestClient` (Spring 6.1+) |
| 타임아웃 | connect 5초 / read **60초** |
| 재시도 | 429·5xx만 **최대 2회**, 지수 백오프. 4xx는 재시도하지 않음 |
| 모델 ID | `application.yml`의 `openai.model.text` / `openai.model.image`. **하드코딩 금지** |
| 기록 | 호출마다 `AiJobRecorder`가 `ai_jobs`에 stage·model·토큰·지연·에러코드 기록 |

**`ai_jobs`에 프롬프트 원문과 사용자 사진을 저장하지 않는다.** (PRD §9)

### 6.2 Structured Outputs

모든 텍스트 단계는 `response_format: { type: "json_schema", json_schema: { strict: true, ... } }`를 쓴다. 스키마는 [API.md](../API.md) §7.2~7.4를 `JsonSchemas.java` 상수로 옮긴다.

**자유 서술을 정규식으로 파싱하지 않는다.** 스키마 위반은 파싱 실패가 아니라 재시도 대상이다.

### 6.3 가드레일 후검증 ★

프롬프트에 G-1~G-8을 넣는 것만으로는 부족하다. 출력을 검사한다.

```java
@Component
public class OutputValidator {
    private static final Pattern SCORE = Pattern.compile("\\d+\\s*점|상위\\s*\\d+\\s*%|\\d+\\s*등급");
    private static final List<String> BANNED = List.of("치료", "시술받", "진단", "처방");

    public void validate(String text) {
        if (SCORE.matcher(text).find())                 throw new GuardrailViolation(SCORE_PATTERN);
        if (BANNED.stream().anyMatch(text::contains))   throw new GuardrailViolation(BANNED_WORD);
    }
}
```

- 위반 시 **1회 재생성**하고, 재차 위반하면 `AI_PROVIDER_ERROR`로 실패 처리한다.
- 재생성 시 시스템 프롬프트에 위반 사유를 덧붙인다.

### 6.4 결과 정합성 검증

스키마로 강제되지 않는 규칙은 서버가 확인한다.

| 검증 | 처리 |
| --- | --- |
| `categoryChanges` 3건이 `SKIN`·`BODY`·`HEALTH` 각 1건인가 | 아니면 재생성 |
| 배열 순서를 `profiles.priorities` 순서로 정렬 | 저장 전 재정렬 |
| 인바디 OCR — 읽지 못한 항목이 `null`인가 | 추측값이 들어오면 해당 필드를 버리고 `unrecognized`에 담음 |

### 6.5 이미지 생성

- 입력: 사용자 사진(주) + 참고 사진 N장(보조) + 선택 키워드 프롬프트
- 프롬프트에 합성 경계 명시 — 참고 사진에서 **헤어스타일·피부 상태**를 가져오고, **얼굴(이목구비·골격)과 피부색**은 사용자 것을 유지 (G-2 · PRD §8.2)
- 응답은 base64 → 디코딩 → 스토리지 업로드 → `comparison_image_key` 기록. **바이트를 DB에 넣지 않는다.**
- **정책 거부는 정상 시나리오다.** 예외로 던지지 말고 `image_status = FAILED`로 저장하고 텍스트 결과만 응답한다.
- 이미지 실패는 분석권 미차감 사유가 **아니다.**

---

## 7. 분석권 차감

동시 요청으로 중복 차감되면 안 된다. 조회 후 저장하지 말고 **단일 UPDATE**로 처리한다.

```java
@Modifying
@Query("""
    UPDATE Subscription s SET s.analysisCredits = s.analysisCredits - 1
    WHERE s.id = :id AND s.analysisCredits > 0
""")
int consumeCredit(@Param("id") UUID id);
```

반환값이 `0`이면 잔여가 없다는 뜻이므로 `NO_ANALYSIS_CREDIT`을 던진다.

**차감 시점은 결과 생성 성공 시**다(PRD O-7). 결과 저장과 **같은 트랜잭션**에서 실행해 "결과는 저장됐는데 차감 안 됨" 또는 그 반대가 생기지 않게 한다.

미차감 사유: `NO_ANALYSIS_CREDIT` · `CONTENT_POLICY_BLOCKED` · `ANALYSIS_TIMEOUT` · `INBODY_SCAN_FAILED`

---

## 8. 스토리지

### 키 규칙

```text
profiles/{userId}/{uuid}.{ext}
references/{userId}/{uuid}.{ext}
results/{userId}/{resultId}/{current|peak}.png
inbody/{userId}/{uuid}.{ext}
```

### presigned URL

> 참고 사진 키에 `analysisId`를 넣지 않는다. presigned URL 발급 시점에는 분석이 아직 생성되지 않아 ID를 알 수 없다. 어떤 분석에 속하는지는 `analysis_reference_images` 행이 기록한다.

| 용도 | 만료 |
| --- | --- |
| 업로드 (PUT) | 5분 |
| 조회 (GET) | 10분 |

- 업로드 URL 발급 시 `Content-Type`과 `Content-Length` 상한(10MB)을 서명에 포함한다.
- 발급된 `objectKey`가 **요청자 소유 경로인지** 저장 시점에 다시 확인한다. 클라이언트가 임의 key를 보낼 수 있다.
- 삭제는 DB 삭제와 함께 처리한다. 사진 삭제·계정 삭제 시 **객체를 즉시 지운다.** (PRD §10)
- `INBODY_DOCUMENT`는 OCR 1회용이다. 소유권을 먼저 검증한 뒤 OCR 성공·실패와 관계없이
  처리 종료 시 영속 삭제 큐에 넣고, S3 삭제 실패는 스케줄러가 재시도한다.

---

## 9. 보안

| 항목 | 정책 |
| --- | --- |
| 인증 | JWT Bearer. Access 30분 / Refresh 14일 |
| 필터 | `JwtAuthenticationFilter`가 `SecurityContext`에 `UserPrincipal` 주입 |
| 인가 | 리소스 소유권을 Service에서 확인. 타인 것은 `403 FORBIDDEN_RESOURCE` (404 아님) |
| 비밀번호 | BCrypt |
| CORS | 프론트 오리진만 허용 |
| 시크릿 | 전부 환경 변수. `application.yml`에 값을 적지 않는다 |
| 로깅 | 사진·프롬프트·토큰 원문 미기록 |

**PII 주의** — 얼굴 사진은 생체정보에 준한다. 로그·에러 리포팅에 이미지 URL이나 key가 남지 않게 한다. 스토리지 리전은 국내로 둔다.

---

## 10. 설정

```yaml
# application.yml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate      # 스키마는 Flyway가 소유
    open-in-view: false               # ★ 뷰 렌더링 중 커넥션 점유 방지
  flyway:
    enabled: true

openai:
  api-key: ${OPENAI_API_KEY}
  model:
    text:  ${OPENAI_MODEL_TEXT}
    image: ${OPENAI_MODEL_IMAGE}
  timeout:
    read-seconds: 60
  max-retries: 2

storage:
  endpoint:   ${STORAGE_ENDPOINT}
  bucket:     ${STORAGE_BUCKET}
  access-key: ${STORAGE_ACCESS_KEY}
  secret-key: ${STORAGE_SECRET_KEY}
  presign:
    upload-seconds: 300
    download-seconds: 600

jwt:
  secret: ${JWT_SECRET}
  access-minutes: 30
  refresh-days: 14

analysis:
  timeout-seconds: 60
  sweep-after-minutes: 3
```

**`ddl-auto: validate`** — 스키마 소유권은 Flyway에 있다. `update`로 두면 팀원 간 스키마가 갈라진다.

**`open-in-view: false`** — 기본값 `true`는 뷰 렌더링까지 커넥션을 잡는다. AI 대기가 긴 이 서비스에서는 커넥션 고갈 원인이 된다.

---

## 11. 스케줄러

| 작업 | 주기 | 내용 |
| --- | --- | --- |
| `AnalysisSweeper` | 1분 | 3분 이상 진행 상태인 분석을 `FAILED` 전환 (§5.4) |
| `NotificationScheduler` | 1분 | 발송 시각이 된 태스크 알림 발송 |
| `AccountPurger` | 매일 | `deleted_at`이 30일 지난 계정 하드 삭제 |

다중 인스턴스로 가면 `ShedLock` 등으로 중복 실행을 막아야 한다. MVP는 단일 인스턴스 전제다.

---

## 12. 테스트 전략

| 계층 | 방식 |
| --- | --- |
| Service | 단위 테스트. OpenAI·스토리지는 인터페이스로 두고 stub 주입 |
| Repository | `@DataJpaTest` + **Testcontainers PostgreSQL** (H2는 JSONB를 지원하지 않음) |
| Controller | `@WebMvcTest` + MockMvc. 응답 봉투·에러 코드 검증 |
| 가드레일 | `OutputValidator` 단위 테스트를 **금지 패턴별로** 작성. 회귀 방지 가치가 가장 큼 |
| 통합 | 분석 파이프라인 E2E 1건 (OpenAI는 고정 응답 stub) |

**Testcontainers를 쓰는 이유** — `profiles.priorities`, `analysis_results.category_changes` 등 JSONB 컬럼이 많아 H2로는 검증이 안 된다.

---

## 13. 구현 순서 제안

| 단계 | 범위 |
| --- | --- |
| 1 | `common` (ApiResponse · ErrorCode · GlobalExceptionHandler) + Flyway `V1__init.sql` |
| 2 | `auth` · `user` + JWT 필터 |
| 3 | `storage` presigned + `profile` 등록/조회 |
| 4 | `ai` 모듈 골격 (OpenAiClient · JsonSchemas · OutputValidator · AiJobRecorder) |
| 5 | `analysis` 파이프라인 — 키워드 추출 → 선택 → 결과 생성 |
| 6 | 이미지 생성 (별도 풀) + `drawer` |
| 7 | `routine` 2경로 + `notification` |
| 8 | `subscription` + 스케줄러 |

---

## 14. 미결 사항

| # | 내용 | 영향 |
| --- | --- | --- |
| B-1 | 단일 인스턴스 전제. 확장 시 `@Async` → 메시지 큐, 스케줄러 락 필요 | §5.4 · §11 |
| B-2 | `FROM_ANALYSIS` 목표의 태스크를 며칠치 생성할지 미정 (기간 개념 없음) | [ERD.md](../ERD.md) E-3 |
| B-3 | PG사 미정 → `subscription` 결제·웹훅 구현 보류 | PRD O-3 |
| B-4 | 홈 화면 수치 대시보드 API 미정의 — PRD **O-1 정책 결정 전까지 설계하지 않음** | PRD O-1 |
| B-5 | 알림 전송 수단(FCM vs Web Push) 미정 | [API.md](../API.md) A-6 |
