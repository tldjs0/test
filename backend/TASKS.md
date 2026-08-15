# 백엔드 작업 목록 — 8/14(금) ~ 8/16(일)

[PLAN.md](PLAN.md)의 실행 단위. 체크하며 진행한다.

**원칙** — 목데이터·고정 응답·fixture 금지. 못 만들면 범위를 줄이되, 만든 것은 진짜로 동작한다. ([AGENTS.md](../AGENTS.md) 규칙 15)

---

## 0. 시작 전 (목요일 밤) — 🔴 블로커

**아래 3개가 정해지지 않으면 금요일 오후부터 막힌다.** 코딩보다 먼저 처리한다.

### 0-1. ✅ OpenAI 키와 모델 확정 — **완료 (8/13)**

- [x] API 키 발급 · 크레딧 충전 ($100)
- [x] `.env`에 실제 키 입력 → `/v1/models` 인증 성공 (126개 모델)
- [x] 모델 핀 고정 — `OPENAI_MODEL_TEXT=gpt-5.4-mini` · `OPENAI_MODEL_IMAGE=gpt-image-1`

**스파이크 결과** — 실제 스키마로 3개 모델 비교. 셋 다 strict JSON Schema를 지켰고 한국어 출력도 정상.

| 모델 | 지연 | 출력 토큰 | 비고 |
| --- | --- | --- | --- |
| `gpt-4.1-mini` | 5.4초 | 358 | 설명이 일반적 |
| `gpt-5.4` | 10.9초 | 649 | 가장 구체적, 느림 |
| **`gpt-5.4-mini`** | **4.5초** | 490 | 채택 — 구체적이면서 가장 빠름 |

- 결과 생성 스키마(중첩 배열 2개 포함)까지 통과. `categoryChanges` 3건도 SKIN·BODY·HEALTH로 정확히 나옴
- **P95 30초 목표에 여유가 크다.** 키워드 추출 + 결과 생성을 합쳐도 10초 안쪽
- 발견된 문제와 조치는 §0-5 참조

> `gpt-image-1`은 미검증이다. 이미지 생성은 이번 스프린트 범위 밖이라 나중에 확인한다.

### 0-2. ✅ 오브젝트 스토리지 — **완료 (8/14)**

**AWS S3 · 서울 리전(`ap-northeast-2`)** 채택. PRD §10의 국내 저장 요건을 만족한다.

- [x] 버킷 `gojeom-media-kr-1234` 생성 · 퍼블릭 액세스 전면 차단
- [x] IAM 사용자 `gojeom-backend` + 정책 `gojeom1-s3-access` (해당 버킷 객체 3개 액션만)
- [x] CORS 설정 (`PUT`·`GET`, origin `http://localhost:5173`)
- [x] `.env`의 `STORAGE_*` 5개 값 입력

**검증 결과** — SigV4로 실제 요청

| 항목 | 결과 |
| --- | --- |
| PUT / GET / DELETE | 200 / 200 / 204 |
| CORS 프리플라이트 | 200, `Allow-Origin` · `Allow-Methods` 정상 |

**겪은 문제 2가지 (재발 방지용 기록)**

1. 첫 버킷이 `us-east-1`에 생성됐다 — 콘솔 리전 선택기가 기본값이었다. S3는 생성 후 리전 변경이 불가하므로 **버킷 생성 전에 리전 선택기를 서울로 바꿔야 한다.**
2. IAM 정책을 **만들기만 하고 사용자에 연결하지 않아** `AccessDenied`가 났다. 오류 메시지 `no identity-based policy allows` 가 이 상황을 뜻한다.

> 프론트를 배포하면 그 주소를 CORS `AllowedOrigins`에 추가해야 한다.

### 0-3. 배포 환경

**서버는 가비아를 제공받았다.** 애플리케이션 서버는 가비아, 파일 저장만 AWS S3를 쓴다.

- [ ] 가비아 서버 사양·OS 확인 (Java 21 설치 가능 여부)
- [ ] PostgreSQL을 가비아 서버에 둘지, 별도로 둘지 결정
- [ ] 배포 주소 확정 → `CORS_ALLOWED_ORIGINS`와 **S3 버킷 CORS `AllowedOrigins`** 양쪽에 추가
- [ ] 서버 환경 변수 주입 방식 결정 (`.env`는 커밋되지 않으므로 별도 전달)

> **AWS에는 EC2·RDS를 만들지 않는다.** 서버가 이미 있으므로 AWS 사용은 S3 하나로 제한한다. 비용이 새는 지점을 원천 차단하는 효과가 있다.

### 0-4. ✅ 문서 선반영 — **완료 (8/14)**

- [x] `AUTH_EMAIL_DUPLICATED` (409, "이미 가입된 이메일이에요.") — API.md §4 · `ErrorCode` enum 양쪽 반영

### 0-6. 🔴 Google OAuth 클라이언트 발급 — 코딩 전에 미리

구현(2.5h)보다 **콘솔 설정에서 시간을 더 먹는다.** 금요일 코딩 전에 끝내둔다.

[Google Cloud Console](https://console.cloud.google.com) 에서:

- [ ] 프로젝트 생성 (예: `gojeom`)
- [ ] **OAuth 동의 화면** 구성 — User Type `외부`, 앱 이름·지원 이메일 입력
- [ ] 게시 상태는 **테스트**로 두고, **테스트 사용자에 팀원 이메일 추가**
      (테스트 모드에서는 등록된 계정만 로그인된다. 빠뜨리면 팀원이 못 쓴다)
- [ ] 범위(scope)는 `email` · `profile` · `openid` 3개면 충분
- [ ] **사용자 인증 정보 → OAuth 클라이언트 ID → 웹 애플리케이션**
- [ ] 승인된 JavaScript 원본: `http://localhost:5173` (배포 후 그 주소도 추가)
- [ ] 발급된 **클라이언트 ID**를 `.env`의 `GOOGLE_CLIENT_ID`에 입력

**클라이언트 ID는 프론트와 백엔드가 함께 쓴다.** 프론트는 로그인 버튼에, 백엔드는 ID 토큰 검증의 `audience`로 쓴다. 클라이언트 시크릿은 이 방식에서 **필요 없다.**

**결정할 것 하나** — 이메일로 가입한 계정과 같은 이메일로 Google 로그인하면?

> 현재 방침: **같은 계정으로 로그인시킨다.** `users.email`이 유일 키이므로 이메일 기준 1계정이다.
> 단 Google로 먼저 가입하면 `password_hash`가 NULL이라 이메일 로그인은 불가하다.
> 비밀번호 설정 기능은 이번 범위 밖이다.

### 0-5. ✅ 키워드 카테고리 FACE 추가 — **완료 (8/13)**

스파이크에서 발견한 실제 문제. `"차분한 인상"`·`"또렷한 인상"` 같은 얼굴 관련 키워드가 3종(SKIN/BODY/HEALTH) 강제 탓에 **`HEALTH`로 오분류**됐다. Figma 시안의 실제 키워드(`다이아몬드형`·`귀족턱`·`큰 눈`)도 대부분 얼굴 관련이라 같은 문제가 난다.

- [x] `V2__keyword_category_face.sql` — `analysis_keywords.category` CHECK를 4종으로 확장 (적용·검증 완료)
- [x] `KeywordCategory` enum 신설 (4종) — `Category`(3종)와 분리
- [x] API.md · ERD.md 반영

**키워드만 4종이다.** `profiles.priorities` · `routines.category` · `routine_tasks.category` · `categoryChanges`는 3종 그대로다. 우선순위와 루틴에는 얼굴형이 없기 때문이다.

> 구현 시 `Category`에 `FACE`를 추가하고 싶어질 수 있는데, 그러면 우선순위에 얼굴형이 들어가 시안(06 우선 순위 등록)과 어긋난다. 두 enum을 합치지 않는다.

---

## 금 (8/14) — 인증 · 프로필 · 스토리지

목표: **프론트가 회원가입 → 로그인 → 프로필 등록까지 실제 API로 수행할 수 있다.**

> **D1-1 ~ D1-5 완료 (8/14).** 회원가입·로그인·토큰갱신·`/users/me` 전 흐름을 실제 DB로 검증했다. 검증 결과는 이 절 끝의 «D1 검증» 참조.

### D1-1. ✅ 공통 엔티티 기반 — 완료

- [x] `common/entity/BaseCreatedEntity` — `created_at`만 있는 테이블용
- [x] `common/entity/BaseTimeEntity` — `created_at` + `updated_at`

**겪은 문제** — Spring Data Auditing의 기본 `DateTimeProvider`는 `LocalDateTime`을 공급해 `OffsetDateTime` 필드에 넣지 못한다. `JpaConfig`에 UTC `OffsetDateTime`을 공급하는 provider를 등록해 해결했다. 이걸로 "UTC 저장 / KST 표시" 규칙이 코드에 못박혔다.

> ⚠️ **`ddl-auto: validate`이므로 엔티티가 스키마와 정확히 일치해야 앱이 뜬다.** 컬럼명·타입이 하나라도 어긋나면 기동 실패한다. JSONB 컬럼은 반드시 `@JdbcTypeCode(SqlTypes.JSON)`을 붙인다.

### D1-2. ✅ JWT 인증 — 완료

- [x] `common/security/UserPrincipal`
- [x] `auth/jwt/JwtProvider` — access 30분 / refresh 14일, `typ` 클레임으로 종류 구분
- [x] `auth/jwt/JwtAuthenticationFilter`
- [x] `SecurityConfig`에 필터 결선 (TODO 제거)

`@AuthenticationPrincipal UserPrincipal`을 그대로 쓰면 되어 별도 애노테이션은 만들지 않았다.

**`typ` 클레임을 둔 이유** — refresh 토큰으로 API를 호출하거나 access 토큰으로 갱신을 시도하는 것을 막는다. 실제로 검증에서 access 토큰으로 refresh를 시도하면 401이 나오는 것을 확인했다.

### D1-3. ✅ user 도메인 — 완료

- [x] `user/entity/User` — 정적 팩터리 `ofLocal` / `ofGoogle`
- [x] `user/repository/UserRepository` — soft delete 제외 조회 4종
- [x] `subscription/entity/Subscription` + repository
- [x] `SubscriptionRepository.consumeCredit` — **단일 UPDATE 차감** (D2-6에서 사용)

### D1-4. ✅ auth 엔드포인트 — 완료

- [x] `AuthDtos` — Signup / Login / Refresh / TokenResponse / UserSummary
- [x] `AuthController` — signup · login · refresh · logout
- [x] `AuthService` — BCrypt, 중복 시 `AUTH_EMAIL_DUPLICATED`
- [x] 가입 시 `Subscription(TRIAL, 1회, +1개월)` 생성 — 같은 트랜잭션
- [x] **이메일 소문자 정규화** — 대소문자만 다른 중복 가입 차단

로그아웃은 무상태라 서버가 폐기할 토큰이 없다. 토큰 블랙리스트는 범위 밖이며 엔드포인트만 열어뒀다.

### D1-5. ✅ GET /users/me — 완료

- [x] `UserController` + `UserService` + `MeResponse`
- [x] `analysisCredits` · `subscription`(canAnalyze / canCreateRoutine) 포함
- [x] `hasProfile` — D1-7에서 **실제 조회로 교체 완료**

> `hasProfile`은 프론트의 최초 진입 라우팅 기준이다. ([API.md](../API.md) C-1)

### D1-6. ✅ storage presigned — 완료

- [x] `storage/UploadPurpose` — `PROFILE_PHOTO` · `REFERENCE_IMAGE` · `INBODY_DOCUMENT`
- [x] `storage/ObjectKeyFactory` — key 생성 + **소유권 검증** + 허용 이미지 타입 제한
- [x] `storage/StorageService` — PUT 5분 / GET 10분 presign, 삭제
- [x] `storage/UploadController` — `POST /uploads/presigned`
- [x] `common/config/StorageConfig` — AWS면 endpointOverride 생략, MinIO·NCP는 적용

**키 경로 변경** — ERD §8은 참고 사진 키를 `references/{userId}/{analysisId}/...`로 정의했으나, presign 시점에는 분석이 아직 생성되지 않아 `analysisId`를 알 수 없다. `references/{userId}/{uuid}.{ext}`로 단순화했다.

**허용 타입** — JPG · PNG · HEIC · WEBP만 URL을 발급한다. 그 외는 `400 VALIDATION_ERROR`.

### D1-7. ✅ profile 도메인 — 완료

- [x] `profile/entity/Profile` — JSONB 3개에 `@JdbcTypeCode(SqlTypes.JSON)`
- [x] `Inbody`(6종) · `ProfileAnalysisSummary` 레코드
- [x] `@Priorities` 커스텀 검증 — 정확히 3개 · 중복 없음 · 3종 전부
- [x] `POST /profiles` · `GET /profiles/me` · `PATCH /profiles/me` · `PATCH /profiles/me/priorities` · `DELETE /profiles/me/photo`
- [x] photoKey 소유 검증 → 기존 활성 프로필 비활성화 → 새 행 생성
- [x] **`GET /users/me`의 `hasProfile`을 실제 조회로 교체** (D1-5에서 남겨둔 항목)

**`analysisSummary`는 여전히 `null`이다.** D2-2에서 AI가 채운다. 가짜 값을 넣지 않는다.

**고친 결함 2건**

1. **`photo_key`가 `NOT NULL`이라 사진 삭제가 500으로 실패**했다. PRD §10과 ERD §7은 "삭제 시 NULL"을 규정하는데 V1 스키마가 어긋나 있었다. `V3__profile_photo_key_nullable.sql`로 맞췄다.
2. **`Inbody.isEmpty()`가 JSONB에 `"empty": false`로 저장**됐다. Jackson이 파생 메서드를 속성으로 본 것이다. `@JsonIgnore`로 차단했다.

### D1-8. ✅ AI 스파이크 — 완료

- [x] `ai/OpenAiClient` — D2-1에서 정식 구현 (재시도·타임아웃 포함)
- [x] 키워드 추출 스키마로 **실제 호출 성공** — §0-1 스파이크(8/13) · D2 검증(8/14) 양쪽에서 확인
- [x] `json_schema` strict 준수 · 한국어 출력 품질 · 지연 3.7초 확인
- [x] 대응 불필요 — 스키마 단순화도 모델 변경도 하지 않았다

> **다만 strict 스키마의 `maxLength`가 문장을 중간에서 자르는 문제를 나중에 만났다.**
> 스파이크에서는 드러나지 않았다. ([AGENTS.md](../AGENTS.md) N-1)

### D1 검증 (8/14)

로컬 PostgreSQL 18.4 · 실제 HTTP 요청으로 12개 시나리오 확인.

| # | 시나리오 | 결과 |
| --- | --- | --- |
| 1 | 회원가입 | 201, TRIAL 구독 동시 생성 |
| 2 | 이메일 중복 | 409 `AUTH_EMAIL_DUPLICATED` |
| 3 | 대소문자만 다른 이메일 | 409 (정규화 동작) |
| 4 | 로그인 | 200 |
| 5 | 비밀번호 오류 | 401 `AUTH_INVALID_CREDENTIALS` |
| 6 | `GET /users/me` | 200, credits=1 · TRIAL/ACTIVE · canAnalyze=true |
| 7 | 토큰 없음 | 401 |
| 8 | 위조 토큰 | 401 |
| 9 | 토큰 갱신 | 200, 새 토큰 발급 |
| 10 | access 토큰으로 refresh | 401 `AUTH_TOKEN_EXPIRED` |
| 11 | 입력 검증 실패 | 400 `VALIDATION_ERROR` + 필드별 사유 |
| 12 | 깨진 JSON 본문 | 400 `VALIDATION_ERROR` |

**DB 확인** — 한글 닉네임 정상 저장, BCrypt 해시 60자, `users=1 subscriptions=1`(트랜잭션 정합성).

**추가로 고친 것** — 12번 시나리오에서 깨진 JSON이 **500으로 나가는 결함**을 발견해 `HttpMessageNotReadableException` 핸들러를 추가했다. 클라이언트 잘못을 서버 장애로 보이게 하는 문제였다.

### D1-6·D1-7 검증 (8/14)

실제 S3와 PostgreSQL로 확인.

| 시나리오 | 결과 |
| --- | --- |
| presigned 발급 → **실제 S3 PUT** | 200 |
| 10MB 초과 | 413 `FILE_TOO_LARGE` |
| PDF 등 비허용 타입 | 400 + 사유 |
| **B가 A의 photoKey로 프로필 등록** | **403 `FORBIDDEN_RESOURCE`** |
| priorities 2개 / 중복 / FACE 포함 | 400 전부 차단 |
| 키 300cm | 400 + 필드 사유 |
| 프로필 등록 → 조회 | photoUrl presigned 발급 확인 |
| 우선순위 변경 | 순서 반영 확인 |
| **사진 삭제 → S3 객체** | 200 → 403 = **즉시 삭제 확인** |
| `hasProfile` | 등록 전 false → 등록 후 true |

**S3 삭제 판정 주의** — 최소 권한 정책이라 `ListBucket`이 없다. 이 경우 S3는 없는 객체에 **404가 아니라 403**을 반환한다. `200 → 403` 전이가 삭제 확인이다.

**DB 확인** — `priorities` 순서 보존, 인바디 6종 JSONB 정상, 사용자별 활성 프로필 1행, `analysis_summary` 전부 null(D2-2 전이므로 정상).

**금요일 총 13h.** 많다. D1-8을 지키기 위해 D1-6·D1-7이 밀리면 토요일 오전으로 넘긴다.

---

## 토 (8/15) — 분석 파이프라인

목표: **고점 텍스트를 넣으면 키워드가 나오고, 고르면 결과가 완성된다.**

> **D2-1 ~ D2-7 완료 (8/14).** 실제 PostgreSQL · 실제 S3 · 실제 OpenAI로 51개 시나리오를 통과했다.
> 검증 결과는 이 절 끝의 «D2 검증» 참조. 남은 범위는 «D2에서 하지 않은 것»에 적었다.

### D2-1. ✅ ai 모듈 정식화 — 완료

- [x] `ai/OpenAiClient` — 429·5xx만 2회 재시도(지수 백오프), 4xx는 재시도 안 함
- [x] `ai/schema/JsonSchemas` — [API.md](../API.md) §7.2~7.3 스키마 상수화
- [x] `ai/prompt/SystemPrompts` — 가드레일 G-1~G-8 공통 블록
- [x] `ai/prompt/KeywordExtractionPrompt` · `ResultGenerationPrompt` · `ProfileAnalysisPrompt`
- [x] `ai/job/AiJob` 엔티티 + `AiJobRecorder` — stage·model·토큰·지연·에러코드 기록
- [x] **프롬프트 원문과 사진을 저장하지 않는다** (PRD §9)

**설계 판단 3가지**

1. **`temperature`·`max_tokens`를 보내지 않는다.** 모델 세대마다 지원 파라미터가 달라(`max_tokens` vs `max_completion_tokens`) 모델 핀을 바꾸면 400이 난다. 필수 파라미터만 보내면 핀을 바꿔도 코드가 그대로 돈다.
2. **read 타임아웃은 재시도하지 않는다.** 60초를 기다린 뒤 또 60초를 기다리면 좀비 정리(3분)와 폴링 상한(60초)을 모두 넘긴다. `ANALYSIS_TIMEOUT`(미차감)으로 바로 실패시킨다.
3. **`AiJobRecorder`는 `TransactionTemplate`으로 `REQUIRES_NEW`를 직접 잡는다.** `@Transactional`은 같은 클래스 내부 호출에서 프록시를 안 타고, 예외를 안에서 삼켜도 커밋 때 `UnexpectedRollbackException`이 다시 나온다. 기록 실패가 파이프라인을 죽이면 안 되는 자리라 경계를 직접 잡았다.

**호출 1건 = `ai_jobs` 1행.** 재시도하면 시도마다 쌓인다. 실패 행 수가 곧 제공자 안정성 지표다.

### D2-2. ✅ 프로필 분석 — 완료

- [x] `PROFILE_ANALYSIS` 단계 — 사진(presigned GET URL) + 신체정보 → `analysis_summary`
- [x] 프로필 생성 후 비동기로 채움 (`ProfileCreatedEvent` → `AFTER_COMMIT` → `@Async`)
- [x] **점수·등급 필드를 만들지 않는다** (G-1) — 스키마에 아예 없다. 단위 테스트로 고정

**API.md에 없던 스키마를 하나 만들었다.** `profile_analysis`(§7.2~7.4에 없음). ERD.md §5.3의 `analysis_summary` 구조에서 역으로 도출했다. `modelVersion`·`analyzedAt`은 서버가 채우므로 AI에게 요구하지 않는다.

**실패해도 프로필 등록은 성공이다.** 요약은 분석 품질을 높이는 보조 정보다. 실패하면 `analysis_summary`가 null로 남고 이후 단계는 요약 없이 진행한다. 가짜 값을 넣지 않는다. (규칙 15)

> **이 요약이 뒤 단계의 사진을 대신한다.** 키워드 추출과 결과 생성은 사진을 다시 보내지 않고 이 텍스트를 받는다. 덕분에 두 단계가 3~4초로 끝난다.

### D2-3. ✅ analysis 엔티티 — 완료

- [x] `Analysis` · `AnalysisReferenceImage` · `AnalysisKeyword` · `AnalysisResult` + repository 4종
- [x] JSONB 5개에 `@JdbcTypeCode(SqlTypes.JSON)` · `CategoryChange` · `DailyCare` 레코드

**상태 전이를 메서드로만 열었다.** setter가 있으면 비동기 파이프라인과 사용자 액션이 같은 행을 아무 상태로나 건너뛰게 만든다. `markGenerating()`이 `KEYWORDS_READY`인지 직접 확인하고 아니면 `409`를 던진다.

### D2-4. ✅ 분석 생성 · 키워드 추출 — 완료

- [x] `AnalysisController` — `POST /analyses` → **202**
- [x] `AnalysisService` — 프로필 존재(`PROFILE_REQUIRED`) · 분석권(`NO_ANALYSIS_CREDIT`) 사전 검증
- [x] **`AnalysisTxService`** — `@Transactional` 메서드만 가진 별도 빈
- [x] `AnalysisPipeline` — `@Async("analysisExecutor")`, **OpenAI 호출은 트랜잭션 밖**
- [x] `@TransactionalEventListener(AFTER_COMMIT)`로 비동기 시작
- [x] 상태 전이 `CREATED → EXTRACTING → KEYWORDS_READY`
- [x] 참고 사진 key **소유권 검증** · 중복 제거 · **최대 5장 제한**

**참고 사진 5장 상한은 계약에 없는 서버 측 제한이다.** API.md는 상한을 두지 않았는데, 무제한이면 한 요청이 토큰·지연을 얼마든지 끌어올린다. [API.md](../API.md)에 반영이 필요하다.

**`@TransactionalEventListener`와 `@Async`를 한 메서드에 겹치지 않았다.** 리스너는 커밋 시점만 잡고, 스레드 전환은 `AnalysisPipeline`의 `@Async`가 한다. 두 애노테이션이 서로 다른 빈에 있어야 프록시가 순서대로 걸린다.

### D2-5. ✅ 상태 폴링 — 완료

- [x] `GET /analyses/{id}` — `status` · `imageStatus` · `progress` · `message` · `failureCode` · `pollAfterMs`
- [x] 상태별 `progress` 매핑 (CREATED 5 · EXTRACTING 25 · KEYWORDS_READY 50 · GENERATING 75 · DONE 100)

**종료 상태에서 `pollAfterMs`를 null로 준다.** 프론트가 폴링을 멈출 근거를 서버가 준다. `KEYWORDS_READY`에서는 값을 유지한다 — 시안 14는 분석이 진행되는 동안 키워드를 고르는 구조다. (API.md C-4)

**전역 Jackson 설정이 `non_null`이라 `failureCode: null`이 응답에서 사라진다.** 폴링·결과 응답 레코드에 `@JsonInclude(ALWAYS)`를 걸어 API.md가 null을 명시한 자리의 키가 남게 했다.

### D2-6. ✅ 키워드 선택 · 결과 생성 — 완료

- [x] `GET /analyses/{id}/keywords` — `minSelect`·`maxSelect` 포함
- [x] `POST /analyses/{id}/keywords/selection` — 상태 검증(`ANALYSIS_INVALID_STATE`), 1~4개, 중복·타 분석 ID 차단
- [x] `RESULT_GENERATION` → `AnalysisResult` 저장 → `DONE`
- [x] **`categoryChanges` 3건이 SKIN·BODY·HEALTH 각 1건인지 검증**, 아니면 재생성
- [x] **`profiles.priorities` 순서로 정렬**해 저장
- [x] **분석권 차감** — 결과 저장과 같은 트랜잭션, 단일 UPDATE
- [x] `GET /analyses/{id}/result` — `viewState=FRESH`, **`disclaimer` 항상 포함**
- [x] `imageStatus=SKIPPED`

> **`imageStatus`는 참고 사진 유무로 갈린다.** 참고 사진이 없으면 `SKIPPED`(만들지 않음), 있으면 `PENDING`으로 저장하고 별도 풀에서 생성한다. 비교 이미지 구현은 아래 «D2-8» 참조.

**차감 → 로드 → 저장 순서를 지켜야 한다.** `consumeCredit`은 `clearAutomatically`가 걸린 벌크 UPDATE라 영속성 컨텍스트를 비운다. 엔티티를 먼저 로드하면 `markDone()`이 사라진다. ([AGENTS.md](../AGENTS.md) N-4)

### D2-7. ✅ 가드레일 · 실패 처리 — 완료

- [x] `ai/guardrail/OutputValidator` — 점수 패턴(`\d+점`, `상위 \d+%`, `\d+등급`) · 금지어(`치료`·`시술받`·`진단`·`처방`)
- [x] 위반 시 **1회 재생성**(위반 사유를 프롬프트에 덧붙임), 재차 위반 시 `AI_PROVIDER_ERROR`
- [x] `AnalysisSweeper` — `@Scheduled(fixedDelay=60s)`, 3분 초과 진행 건 `FAILED` 전환
- [x] 실패 코드별 **분석권 미차감** 확인 (`ANALYSIS_TIMEOUT`·`CONTENT_POLICY_BLOCKED`)
- [x] `OutputValidator` 단위 테스트 18건 (Docker 불필요)

**`\d+\s*위`(순위)는 일부러 넣지 않았다.** 프롬프트가 우선순위를 알려주므로 모델이 "1순위 피부"처럼 정상적으로 쓴다. 그때마다 재생성이 돌면 실패율만 오른다. 외모를 순위로 매기는 것과 카테고리 우선순위는 다르다.

**좀비 정리는 `KEYWORDS_READY`를 건드리지 않는다.** 사용자가 키워드를 고르는 동안 머무는 상태라 시간 제한을 걸면 멀쩡한 분석이 죽는다. 대상은 `CREATED`·`EXTRACTING`·`GENERATING` 3종뿐이다.

### D2-8. ✅ 비교 이미지 생성 — 완료 〔범위 확대〕

원래 스프린트 범위 밖이었으나(§0-1 "`gpt-image-1` 미검증"), 결과 화면의 핵심 블록이라 구현했다.

**만드는 것은 합성 이미지다.** 사용자 본인 사진을 기준으로 두고, 참고 사진(추구미)의
**헤어스타일과 피부 상태**를 입힌다. 얼굴 생김새는 사용자 것을 그대로 지킨다.

| 참고 사진에서 가져오는 것 | 사용자 사진에서 지키는 것 |
| --- | --- |
| 헤어스타일 (길이·형태·앞머리·컬·색) | 이목구비의 형태와 배치 |
| 피부 **상태** (결·균일함·생기) | 얼굴 골격과 윤곽 |
| 전체 분위기와 색감 | 나이·인종·성별·체형·**고유 피부색** |

> **G-2와의 경계** — "타인의 얼굴을 복제하지 않는다"가 막는 것은 **이목구비와 골격**,
> 즉 그 사람이 누구인지를 결정하는 부분이다. 헤어스타일은 정체성이 아니라 바꿀 수
> 있는 요소이고, 애초에 사용자가 그걸 참고하려고 올린 사진이다.

> **피부는 "상태"만 가져오고 "피부색"은 바꾸지 않는다.** 피부색은 인종과 얽혀 있어
> "인종을 바꾸지 않는다"와 정면 충돌한다. 실제로 톤 자체를 옮기라고 했더니 모델이
> 충돌을 피해 **아무것도 하지 않았다.** ([AGENTS.md](../AGENTS.md) N-9)
> 이 제품이 말하는 피부도 색이 아니라 상태다 — 우선순위의 SKIN도 결과지의 피부
> 제안도 수분·결·톤의 균일함을 다룬다. **팀이 다르게 원하면 프롬프트만 고치면 된다.**

- [x] `ai/image/ImageEditService` — `/v1/images/edits` multipart 호출
- [x] `ai/prompt/ImageGenerationPrompt` — **G-2 얼굴 복제 금지** 명시
- [x] `analysis/service/ImagePipeline` — `@Async("imageExecutor")` **별도 풀**
- [x] base64 → 디코딩 → S3 업로드 → `comparison_image_key` 기록
- [x] 정책 거부는 예외가 아니라 `image_status = FAILED`
- [x] 멈춘 `PENDING` 이미지를 8분 후 `FAILED`로 정리 (`AnalysisSweeper`)

**`gpt-image-1` 스파이크 결과** — 200 OK, **33~35초**, PNG 1.7MB, `input_fidelity=high` 수용됨.

| 판단 | 이유 |
| --- | --- |
| **`input_fidelity=high`** | 기본값(low)이면 모델이 인물을 새로 그려 "다른 사람"이 나온다. 정체성 유지가 이 단계의 전제다 |
| **생성하는 것은 고점 한 장뿐** | "현재"는 사용자가 올린 프로필 사진이 정답이다. AI로 다시 만들면 사실과 다른 "현재"를 보여주게 된다. 덕분에 `comparison_image_key` 컬럼 1개로 충분해져 마이그레이션이 필요 없다 |
| **참고 사진은 앞 2장만** | 장수만큼 입력 토큰과 지연이 늘어난다. 분위기 참고는 두 장이면 충분하다 |
| **텍스트 결과 커밋 후 시작** | 이미지를 기다리면 사용자가 33초를 더 본다. 결과 화면을 먼저 띄우고 이미지 자리만 나중에 교체한다 |
| **별도 스레드 풀** | 이미지가 텍스트 파이프라인의 스레드를 붙잡으면 뒤따르는 분석 요청이 밀린다 (§5.3) |

**이미지 실패는 분석권 미차감 사유가 아니다.** 텍스트 결과가 이미 나왔으므로 차감은 유지된다. (PRD §8.3)

---

### D2 검증 (8/14)

로컬 PostgreSQL 18.4 · 실제 S3 · 실제 OpenAI(`gpt-5.4-mini`)로 **51개 시나리오 전부 통과.**

| # | 시나리오 | 결과 |
| --- | --- | --- |
| 1 | 프로필 등록 → 비동기 `analysis_summary` 채움 | ERD §5.3 형태로 저장 확인 |
| 2 | 등록 응답 시점의 `analysisSummary` | `null` (비동기이므로 정상) |
| 3 | 고점 10자 미만 | 400 `VALIDATION_ERROR` |
| 4 | **남의 참고 사진 key** | **403 `FORBIDDEN_RESOURCE`** |
| 5 | `POST /analyses` | 202, `status=EXTRACTING` |
| 6 | 폴링 → `KEYWORDS_READY` | `progress` 50 · `pollAfterMs` 유지 |
| 7 | 키워드 후보 | 7개 (5~8 범위), 전부 미선택 |
| 8 | **`FACE` 키워드 실제 출현** | 확인 — V2 4종 분리가 실제로 동작 |
| 9 | 0개 / 5개 선택 | 400 전부 차단 |
| 10 | 타 분석의 키워드 ID | 400 `VALIDATION_ERROR` |
| 11 | 키워드 확정 | 202, `GENERATING` |
| 12 | **이미 확정된 분석에 재확정** | **409 `ANALYSIS_INVALID_STATE`** |
| 13 | 폴링 → `DONE` | `pollAfterMs=null` (폴링 중단 근거) |
| 14 | `categoryChanges` | 3건 · SKIN·BODY·HEALTH 각 1건 |
| 15 | **priorities 순서 정렬** | `["SKIN","HEALTH","BODY"]` 그대로 반영 |
| 16 | `disclaimer` | 항상 포함 |
| 17 | 가드레일 (점수 패턴·금지어) | 검출 0건 |
| 18 | **결과 성공 후 분석권** | 1 → **0** (같은 트랜잭션) |
| 19 | 분석권 소진 후 재시도 | 402 `NO_ANALYSIS_CREDIT` |
| 20 | 남의 분석 조회 / 결과 조회 | 403 둘 다 |
| 21 | 없는 분석 | 404 `NOT_FOUND` |
| 22 | 프로필 없이 분석 | 409 `PROFILE_REQUIRED` |
| 23 | **좀비 정리** — 10분 묵은 `EXTRACTING` | `FAILED` + `ANALYSIS_TIMEOUT` |
| 24 | **좀비 정리** — 10분 묵은 `KEYWORDS_READY` | **건드리지 않음** (의도대로) |

**지연** — `ai_jobs` 실측. P95 30초 목표에 여유가 크다.

| 단계 | 지연 | 입력/출력 토큰 |
| --- | --- | --- |
| `PROFILE_ANALYSIS` | 2.0~2.6초 | 1332 / 127~163 |
| `KEYWORD_EXTRACTION` | 3.3~3.8초 | 1548~1583 / 371~429 |
| `RESULT_GENERATION` | 3.7~5.3초 | 1336~1436 / 501~694 |

**`ai_jobs` 확인** — 프롬프트 원문·사진 미저장, 제공자가 실제 사용한 모델(`gpt-5.4-mini-2026-03-17`)이 기록됨.

**단위 테스트 38건** (Docker 불필요) — `./gradlew test`

**검증 중 발견해 고친 결함 3건** — 상세는 [AGENTS.md](../AGENTS.md) §4-1 오답 노트.

1. **`maxLength`가 문장을 중간에서 잘랐다.** `emphasizePoints`에 `"피부는 맑고 균일한 결로 정리하면 단정한 인상이 더 또"`가 저장됐다. 프롬프트를 "짧은 라벨"로 고치고 `TruncationDetector`를 붙였다. (N-1)
2. **로컬 로깅이 사진 key와 고점 원문을 남기고 있었다.** `application-local.yml`의 `jdbc.bind: TRACE`. 규칙 9·PRD §9 위반이라 제거했다. (N-2)
3. **Docker가 없어 `./gradlew test`가 통째로 죽었다.** `@Tag("integration")`으로 갈라 단위 테스트만 따로 돌게 했다. (N-3)

### D2에서 하지 않은 것

| 항목 | 왜 |
| --- | --- |
| `POST /profiles/inbody/scan` (INBODY_OCR) | D2 목록에 없다. `AiStage` enum에만 자리를 뒀고 스키마·엔드포인트는 미구현 |
| `DELETE /analyses` (전체 삭제) | F-13. D3 범위 |
| 결과의 `saved` 필드 | 서랍(D3-1) 전에는 저장된 결과가 존재할 수 없어 `false`가 사실이다. `saved_results`가 생기면 실제 조회로 교체 |
| `PATCH /profiles/me` 후 요약 재생성 | 신체 정보를 고쳐도 `analysis_summary`는 그대로다. 재생성 정책 미정 |

**토요일 총 12h.** 가장 빡빡한 날이다. 밀리면 D2-2를 먼저 버린다.

---

## 일 (8/16) — 서랍 · 목표 · 마무리

목표: **데모 경로 E2E 통과.**

> **D3-1 ~ D3-4 완료 (8/14).** 데모 경로 E2E를 실제 DB·S3·OpenAI로 78개 시나리오 통과했다.
> 검증 결과는 이 절 끝의 «D3 검증» 참조.

### D3-1. ✅ 서랍 — 완료

- [x] `POST /analyses/{id}/result/save` — `SavedResult` 생성, 중복 저장 409
- [x] `GET /saved-results` — **3섹션**(`inProgress`·`recent`·`all`) 분류
- [x] `GET /saved-results/{id}` — 결과와 **동일 스키마**, `viewState=SAVED`
- [x] 썸네일 없으면 `thumbnailUrl: null`
- [x] `DELETE /saved-results/{id}` — 204 (API #23. 서랍에서만 빼고 분석은 남긴다)
- [x] **결과 응답의 `saved` 필드를 실제 조회로 교체** (D2에서 남겨둔 항목)

**`ResultAssembler`로 조립을 하나로 묶었다.** `GET /analyses/{id}/result`와 `GET /saved-results/{id}`가 같은 조립기를 쓴다. 프론트가 결과 화면 컴포넌트를 재사용하려면 두 응답이 글자 하나까지 같아야 하는데, 조립을 두 곳에 복사해두면 언젠가 갈라진다. E2E가 **`viewState` 외 모든 키·값이 동일한지** 직접 비교한다.

**3섹션을 쿼리 3번으로 나누지 않았다.** 세 섹션이 같은 모수의 부분집합이라 한 번 읽어 자바에서 가른다. 진행률은 태스크 집계 쿼리 1번으로 끝낸다 — 목록 화면이라 N+1이 그대로 체감된다.

### D3-2. ✅ 목표 생성 — 완료 (경로 A·B 모두)

- [x] `routine/entity/Routine` · `RoutineTask` + repository
- [x] `ai/prompt/RoutineGenerationPrompt` + 스키마 2종
- [x] `POST /routines` — `sourceType=FROM_ANALYSIS`
- [x] 태스크 `title` / `timing` / `durationLabel` / `amountLabel` 생성
- [x] `source_type` CHECK 제약 만족 (정적 팩터리가 조합을 고정한다)
- [x] **경로 B(`STANDALONE`)도 구현** — 아래 참조

> **경로 B는 D3-2 목록에 없었지만 함께 만들었다.** `POST /routines`는 `sourceType`으로 분기하는 **단일 엔드포인트**다. 절반만 구현하면 문서에 정의된 정상 요청에 서버가 오류를 낸다. PRD F-09·API.md C-14/C-15·`ck_routine_source` 제약이 모두 2경로를 전제하므로, B를 빼면 `routines.source_type='STANDALONE'`이 죽은 스키마가 된다.

**AI 호출은 트랜잭션 밖이다.** `RoutineTxService`를 분리해 분석 파이프라인과 같은 구조를 썼다.

```text
create  (트랜잭션 없음)
  ├─ routineTx.load...()      ← 짧은 트랜잭션 (소유권 검증 · 프롬프트 재료)
  ├─ aiTextService.generate() ← 트랜잭션 밖 (실측 2.2초)
  └─ routineTx.persist...()   ← 짧은 트랜잭션
```

**단, 응답은 `202`가 아니라 `201` 동기다.** API.md §6.6이 생성된 목표를 바로 돌려주도록 계약했다. ARCHITECTURE.md A-2("AI 호출은 전부 비동기, 202")와 어긋나지만 계약 정본을 따랐다. 실측 2.2초라 폴링을 붙일 만큼 길지 않다. → «미해결» 참조

**경로 B는 카테고리 3개를 한 번의 AI 호출로 만든다.** 카테고리마다 따로 부르면 3개 선택 시 응답이 15초에 육박한다. 한 번에 받으면 모델이 카테고리 간 중복 태스크도 피한다.

**태스크 배치 규칙 — ERD E-3 미결에 대한 해석**

| 경로 | 배치 | 근거 |
| --- | --- | --- |
| `FROM_ANALYSIS` | 태스크 4~6건을 **`startDate` 하루에** | 기간 개념이 없다. ERD E-3의 "AI 출력 태스크를 start_date 기준으로 배치"를 그대로 따랐고, API.md 예시 `progress {done:2, total:5}`와 개수가 맞는다 |
| `STANDALONE` | 태스크 묶음을 **주 단위로 반복** | `taskCount = 태스크 수 × durationWeeks`. API.md 예시(4주 · `taskCount: 24`)가 주당 6건 구성과 정확히 맞아떨어진다. 매일 반복하면 4주에 168건이 되어 화면이 무너진다 |

> E-3은 여전히 **미결**이다. 위는 두 문서의 예시 숫자를 모두 만족시키는 해석일 뿐이다. 팀이 다른 주기를 원하면 `RoutineTxService.persistStandalone`의 반복 단위만 바꾸면 된다.

**태스크의 `category`는 AI 출력을 믿지 않고 목표의 카테고리로 덮어쓴다.** 어긋나면 목표 화면 분류가 깨진다.

**`durationLabel`·`amountLabel`은 null을 허용한다.** 분량 개념이 없는 태스크("수면 30분 앞당기기")에 억지로 채우게 하면 "1회" 같은 값이 화면에 붙는다.

### D3-3. ✅ 목표 조회 · 완료 체크 — 완료

- [x] `GET /routines/{id}` — `overview` + `tasks` + `progress` + `notification`
- [x] `PATCH /routine-tasks/{id}` — 완료 체크 + 진행률 재계산
- [x] 소유권 검증 (목표·태스크·서랍 전부)
- [x] `GET /routines` (API #25) · `DELETE /routines/{id}` (API #27)

**`MISSED`는 사용자가 지정할 수 없다.** 미수행 재배치가 미설계라(PRD O-9) 상태값만 정의되어 있다. 사용자 액션은 `PENDING` ↔ `DONE` 토글뿐이며, `MISSED`를 보내면 400이다.

**목표 상태를 진행률에 맞춰 자동 전이시킨다.** 전부 완료하면 `COMPLETED`, 체크를 풀면 다시 `ACTIVE`다. 이게 없으면 완주한 목표가 서랍의 "현재 진행중인 목표"에 영원히 남는다. **문서에 없는 규칙이라 «미해결»에 적어뒀다.**

**`notification` 블록은 `notification_settings` 테이블을 실제로 읽는다.** 설정 엔드포인트(D3-5)가 없어 지금은 행이 없고 문서상 기본값(`enabled=false`·`21:00`)이 나가지만, 상수를 박지 않았으므로 설정 기능이 붙을 때 이 코드를 고칠 필요가 없다.

### D3-4. ✅ E2E 통과 — 완료

데모 경로를 처음부터 끝까지 한 번에 통과시켰다.

```text
회원가입 → 로그인 → presigned 업로드 → 프로필 등록
  → 고점 입력 → 폴링 → 키워드 선택 → 결과 확인
  → 서랍 저장 → 서랍 열람 → 목표 생성 → 태스크 완료 체크
```

---

### D3 검증 (8/14)

로컬 PostgreSQL 18.4 · 실제 S3 · 실제 OpenAI로 **78개 시나리오 전부 통과.**

| # | 시나리오 | 결과 |
| --- | --- | --- |
| 1 | 서랍 저장 | 200, `savedResultId`·`savedAt` |
| 2 | **중복 저장** | 409 `ANALYSIS_INVALID_STATE` |
| 3 | 저장 후 결과의 `saved` | `false` → **`true`** |
| 4 | 서랍 3섹션 | `all` 1 · `recent` 1 · `inProgress` 0 |
| 5 | `thumbnailUrl` · `progressRate` | 둘 다 `null` (이미지 없음 · 목표 없음) |
| 6 | **서랍 상세 vs 결과지** | **`viewState` 외 모든 키·값 동일** |
| 7 | 경로 A 생성 | 201, 목표 1개, `category`·`durationWeeks`·`endDate` 전부 null |
| 8 | 경로 A `taskCount` | 5건 (4~6 범위) |
| 9 | 목표 생성 후 서랍 | `inProgress` **1건**, `progressRate` 0.0 |
| 10 | 목표 상세 (경로 A) | `overview` 존재 · 키워드 3개 · 태스크 3종 카테고리 |
| 11 | `notification` | `{enabled:false, time:"21:00"}` |
| 12 | 완료 체크 | 진행률 재계산 확인 |
| 13 | **`MISSED` 지정 시도** | **400** (사용자 지정 불가) |
| 14 | 체크 해제 | 진행률 되돌아감 |
| 15 | 전부 완료 | `rate` 100.0 · 서랍 `inProgress`에서 **빠짐** |
| 16 | 하나 체크 해제 | 다시 `inProgress`로 **복귀** |
| 17 | 경로 B 생성 (피부 4주 · 건강 3주) | 201, **목표 2개** |
| 18 | 경로 B `endDate` | `startDate + 4주 - 1일` 정확 |
| 19 | 경로 B 태스크 배치 | **4개 날짜** (주 단위 4주치) |
| 20 | 경로 B `overview` | `null` (분석 결과 없음) |
| 21 | 경로 B 태스크 카테고리 | 전부 목표 카테고리와 일치 |
| 22 | 카테고리 중복 / 13주 / 과거 시작일 | 400 전부 차단 |
| 23 | `items` 없는 STANDALONE / 결과 ID 없는 FROM_ANALYSIS | 400 |
| 24 | 없는 결과 ID | 404 `NOT_FOUND` |
| 25 | **남의 목표·서랍·태스크·결과** | **403 전부** |
| 26 | 타인 서랍 | 비어 있음 |
| 27 | 목표 삭제 → 재조회 | 204 → 404 |
| 28 | 서랍 항목 삭제 | 204, **분석 결과는 남음** |
| 29 | 가드레일 (목표 텍스트) | 점수 패턴·금지어 0건 |

**AI 호출 실측** — 4단계 25건 전부 성공, 실패 0건.

| 단계 | 평균 지연 | 평균 출력 토큰 |
| --- | --- | --- |
| `PROFILE_ANALYSIS` | 2.4초 | 88 |
| `KEYWORD_EXTRACTION` | 3.7초 | 402 |
| `RESULT_GENERATION` | 4.1초 | 546 |
| `ROUTINE_GENERATION` | **2.2초** | 269 |

**D2 회귀 확인** — `ResultAssembler` 리팩터링 후 D2 51개 시나리오 재실행, 전부 통과.

**로그 재확인** — 스토리지 key 0건 · 고점 원문 0건 · 잘림 경고 0건.

**단위 테스트 41건** (Docker 불필요) — `./gradlew test`

**검증 중 발견해 고친 결함 1건**

- **알림 시각이 `"21:00:00"`으로 나갔다.** API.md §6.7 계약은 `"21:00"`이다. `LocalTime` 기본 직렬화 문제로, 값은 맞고 형식만 틀려 기능 테스트로는 안 잡힌다. `@JsonFormat`으로 고치고 `RoutineDtosSerializationTest`로 고정했다. ([AGENTS.md](../AGENTS.md) N-6)

### D3-5. ✅ 마무리 — 완료

- [x] 모든 Service 진입부에 **소유권 검증** 누락 없는지 확인
- [x] 에러 응답이 [API.md](../API.md) §4 코드·문구와 일치하는지 확인
- [x] 로그에 사진 URL·프롬프트·토큰이 남지 않는지 확인
- [x] **코드에 고정 응답·더미 데이터가 없는지 확인**
- [x] 남는 시간 → 알림 설정 · 인바디 OCR · 분석 전체 삭제 · 계정 삭제
      (`PATCH /profiles/me/priorities`는 D1-7, `DELETE /routines/{id}`는 D3-3에서 완료)

**감사 결과**

| 항목 | 방법 | 결과 |
| --- | --- | --- |
| 에러 코드·HTTP·문구 | API.md §4 표를 파싱해 `ErrorCode` enum과 기계 대조 | **21개 전부 일치** |
| 소유권 검증 | 리소스 ID를 받는 서비스 메서드 전수 + 실제 403 시나리오 7종 | 누락 없음 |
| 로그 PII | 전체 `log.*` 호출 검토 + 실행 로그 문자열 검색 | 사진 key·고점 원문 **0건** |
| 잘못된 본문 로깅 | 비밀번호를 담은 깨진 JSON을 보내고 로그 검색 | 본문 **미유출** (Jackson이 source를 REDACTED 처리) |
| 고정 응답·더미 | `TODO`·`mock`·`dummy`·`fixture` 및 상수 반환 지점 검토 | 없음 |
| 모델 ID 하드코딩 | 소스 전체에서 모델 문자열 검색 | 없음 (설정 주입만) |

**추가로 만든 엔드포인트**

| 엔드포인트 | 근거 |
| --- | --- |
| `GET · PATCH /notifications/settings` | API #29·30 · PRD F-11 |
| `POST /notifications/device-tokens` | API #31 |
| `POST /profiles/inbody/scan` | API #14 · PRD F-03 |
| `DELETE /analyses` | API #32 · PRD F-13 |
| `DELETE /users/me` | API #7 · PRD §10 |

**인바디 OCR** — 실제 결과지 형태 이미지로 **6종 전부 정확히 판독**(1.7초). 인바디가 아닌 사진을 넣으면 추측하지 않고 `422 INBODY_SCAN_FAILED`가 난다(G-8). 응답은 저장되지 않는다.

**분석 전체 삭제** — 스토리지 객체를 즉시 지우고 서랍도 함께 비운다. **목표는 남는다** — 시안 11 모달의 "*계정, 목표 정보는 삭제되지 않아요" 약속을 지키기 위해 `V4`로 `routines.analysis_result_id`를 `ON DELETE SET NULL`로 바꿨다. 근거 분석이 사라진 목표는 고점 요약 카드만 빠지고 태스크·완료 기록은 그대로다.

**계정 삭제** — 계정 행은 soft delete, **사진은 즉시 하드 삭제**(비활성 프로필 이력까지). 30일 후 하드 삭제 배치(`AccountPurger`)는 미구현이다.

**발견해 고친 결함 1건** — 탈퇴한 이메일로 재가입하면 **500**이 났다. `users.email`이 전체 UNIQUE인데 soft delete라 행이 남아, 앱의 중복 검사(`deleted_at IS NULL`)와 DB 기준이 어긋났다. `V5`에서 부분 유니크 인덱스로 맞췄다. ([AGENTS.md](../AGENTS.md) N-8)

> ### 🚫 구독·결제는 구현하지 않는다 〔2026-08-14 팀 결정〕
>
> 비즈니스 모델(PRD §11 · F-12 · API.md §5 33~35번)은 **이번 범위 밖이다.**
> `GET /subscriptions/me` · `checkout` · `webhook`을 만들지 않고, **구독 만료 시
> 신규 분석·목표 생성을 차단하는 로직도 만들지 않는다.**
>
> 단 **분석권 차감은 유지한다** — 구독 기능이 아니라 분석 파이프라인의 일부다.
> 가입 시 `TRIAL` + 분석권 1회 발급, 결과 생성 성공 시 1회 차감, 소진 시
> `NO_ANALYSIS_CREDIT`은 계속 동작한다.
>
> 근거와 범위는 [AGENTS.md](../AGENTS.md) §4 참조.

### D3-6. Google 소셜 로그인 (2.5h) — **맨 마지막**

**위 작업이 전부 끝난 뒤에 한다.** 데모 경로는 이메일 로그인만으로 완성되므로, 이걸 먼저 하면 핵심 기능이 밀린다. 시간이 없으면 스프린트 이후로 넘긴다.

**미뤄도 재작업이 없다.** 스키마(`provider` · `provider_user_id` · nullable `password_hash`)와 API 계약이 이미 소셜 로그인을 수용하도록 설계되어 있다. 붙일 때 기존 코드를 고칠 필요가 없고, 새로 짜는 것은 검증기와 엔드포인트 하나뿐이다.

전제: **0-6 콘솔 설정 완료.** 이메일 로그인 코드를 재사용한다.

- [x] `build.gradle`에 `com.google.api-client:google-api-client` 추가
- [x] `auth/oauth/GoogleTokenVerifier` — `GoogleIdTokenVerifier`로 서명·issuer·audience·만료 검증
- [x] `POST /auth/oauth/google` — body `{ "idToken": "..." }`
- [x] `sub` → `provider_user_id`, `email`·`name` 추출
- [x] **사용자 조회 순서: `(provider, providerUserId)` → 없으면 `email` → 없으면 신규 생성**
- [x] 신규 생성 시 `provider=GOOGLE`, `passwordHash=null`, `Subscription(TRIAL)` 발급
- [x] 응답은 이메일 로그인과 **동일한 `TokenResponse`**

> ### ⚠️ 코드는 완성됐지만 **성공 경로를 검증하지 못했다**
>
> **0-6 Google 콘솔 설정이 아직 안 됐다.** `.env`의 `GOOGLE_CLIENT_ID`에 자리표시자가
> 들어 있다(23자, `.apps.googleusercontent.com` 접미 없음). 실제 클라이언트 ID가
> 없으면 audience 검증을 할 수 없어 **실제 Google 계정 로그인은 시험할 수 없다.**
>
> 검증한 것 — 잘못된 토큰으로 호출 시 동작, 빈 `idToken` 400,
> `GOOGLE_CLIENT_ID`가 없을 때 기동 경고.
>
> **설정이 없을 때 인증 실패로 위장하지 않는다.** `AUTH_INVALID_CREDENTIALS`를
> 주면 사용자가 자기 계정 문제로 오해하고 계속 재시도한다. 서버 설정 누락이므로
> `500 INTERNAL_ERROR` + 서버 로그 경고로 처리한다.
>
> **0-6이 끝나면 `.env`의 `GOOGLE_CLIENT_ID`만 채우면 된다.** 코드 수정은 없다.

**추가 판단 2가지**

1. **이메일 미인증 Google 계정을 거부한다.** `email_verified`가 false인 토큰을 받아들이면 "이메일 기준 1계정" 규칙 때문에 남의 계정을 가로챌 수 있다. 공격자가 피해자 이메일로 Google 계정을 만들고 인증하지 않은 채 로그인하면 2단계 조회에서 피해자 계정에 붙는다.
2. **기존 이메일 계정에 Google로 로그인해도 `provider`를 바꾸지 않는다.** 바꾸면 그 사람이 기존 비밀번호로 로그인할 수 없게 된다.

**완료 판정** — Google 계정으로 로그인해 받은 토큰으로 `GET /users/me` 200. 같은 이메일의 기존 계정이 있으면 새 계정이 생기지 않고 그 계정으로 로그인된다. **← 0-6 완료 후 확인 필요**

> ID 토큰 검증을 직접 구현하지 않는다. JWKS 캐싱·키 롤오버까지 라이브러리가 처리한다.

**일요일 총 11.5h** (Google 로그인 2.5h 포함. 빼면 9h).

---

## 완료 기준

일요일 밤에 전부 참이어야 한다.

- [x] 데모 경로 E2E 통과 — D3 검증 78건
- [x] 결과 응답에 `disclaimer` 항상 포함
- [x] 결과 어디에도 점수·등급·순위 표현 없음 — 가드레일 후검증 + 단위 테스트 18건
- [x] 실패 시 정확한 에러 코드와 한국어 문구
- [x] 고정 응답·더미 데이터·fixture 없음
- [x] 열려 있는 모든 엔드포인트가 실제 DB·실제 AI로 동작

**스프린트 범위 작업은 전부 끝났다.** 남은 것은 아래 3가지뿐이다.

| 항목 | 성격 |
| --- | --- |
| **0-6 Google 콘솔 설정** | 코드는 완성. `.env`의 `GOOGLE_CLIENT_ID`만 채우면 된다 |
| **구독·결제** | **구현하지 않기로 결정됨** (위 D3-5 참조) |

그 밖에 미구현으로 남긴 것 — `AccountPurger`(30일 후 하드 삭제 배치),
`NotificationScheduler`(발송 수단 미정), `GET /consents/terms`(동의 화면 미설계).

### 전체 검증 요약 (8/14)

실제 PostgreSQL 18.4 · 실제 S3 · 실제 OpenAI로 **E2E 172개 시나리오 + 단위 테스트 41건.**

| 스위트 | 범위 | 결과 |
| --- | --- | --- |
| D2 | 분석 파이프라인 | **51 / 51** |
| D3-1~4 | 서랍 · 목표 · 데모 경로 | **78 / 78** |
| D3-5~6 | 알림 · 인바디 OCR · 삭제 · Google | **43 / 43** |
| 단위 테스트 | 가드레일 · 스키마 · 직렬화 · 상태 매핑 | **41 / 41** |

**AI 호출 45건 전부 성공 (실패 0).**

| 단계 | 평균 지연 |
| --- | --- |
| `INBODY_OCR` | 1.7초 |
| `PROFILE_ANALYSIS` | 2.1초 |
| `ROUTINE_GENERATION` | 2.1초 |
| `KEYWORD_EXTRACTION` | 3.7초 |
| `RESULT_GENERATION` | 4.0초 |

---

## 시간 총계와 현실

| 날 | 예상 |
| --- | --- |
| 금 | 13h |
| 토 | 12h |
| 일 | 11.5h |
| **합계** | **36.5h** |

3일에 36시간은 빡빡하다. 밀릴 때 버리는 순서를 미리 정해둔다.

1. **D2-2 프로필 분석** — 결과 생성이 프로필 원본 데이터만으로도 가능하다
2. **D3-5 남는 시간 작업** — 부가 엔드포인트
3. **D3-0 Google 로그인** — 이메일 로그인이 있으므로 데모는 가능하다
4. **D3-2·D3-3 목표 생성** — 데모를 결과 화면까지로 축소

> D1-8(AI 스파이크)을 8/13에 미리 끝냈으므로 금요일은 실질 11h다.

**절대 버리지 않는 것** — D1-2 인증 · D1-7 프로필 · D2-4~D2-6 분석 파이프라인. 이게 서비스의 전부다.
