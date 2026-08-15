# 백엔드 개발 계획 — 3일 스프린트

| 항목 | 내용 |
| --- | --- |
| 기간 | **2026-08-14(금) ~ 08-16(일)** |
| 전제 | 프론트는 [API.md](../API.md) 명세대로 병렬 개발된다. 백엔드는 계약을 지키는 데 집중한다. |
| 기준 문서 | [PRD.md](../PRD.md) · [API.md](../API.md) · [ERD.md](../ERD.md) · [ARCHITECTURE.md](ARCHITECTURE.md) |

---

## 1. 시작 지점

이미 끝난 것 — 다시 손대지 않는다.

- 프로젝트 골격 (Spring Boot 3.3.5 · Java 21 · Gradle Wrapper)
- 공통 계층: `ApiResponse` · `ErrorCode` 20종 · `GlobalExceptionHandler`
- 설정: 분석/이미지 분리 스레드 풀 · CORS · Security 골격 · Properties 5종
- **Flyway `V1__init.sql` 적용 검증 완료** (테이블 15 · 인덱스 35 · 제약 동작 확인)
- 로컬 PostgreSQL 18.4 연결 확인

---

## 2. 범위 결정

**36개 엔드포인트를 3일에 다 만들 수 없다.** AI 파이프라인 하나만으로도 하루가 간다. 아래처럼 자른다.

### 반드시 (데모 필수 경로)

PRD의 데모 경로 — `로그인 → 프로필 등록 → 고점 입력 → 키워드 선택 → 결과 확인 → 서랍 저장 → 서랍 열람 → 목표 설정 → 완료 체크` — 를 끊김 없이 통과시키는 것이 유일한 성공 기준이다.

| # | 엔드포인트 |
| --- | --- |
| 1 | `POST /auth/signup` · `POST /auth/login` · `POST /auth/refresh` · **`POST /auth/oauth/google`** |
| 2 | `GET /users/me` |
| 3 | `POST /uploads/presigned` |
| 4 | `POST /profiles` · `GET /profiles/me` |
| 5 | `POST /analyses` · `GET /analyses/{id}` |
| 6 | `GET /analyses/{id}/keywords` · `POST /analyses/{id}/keywords/selection` |
| 7 | `GET /analyses/{id}/result` · `POST /analyses/{id}/result/save` |
| 8 | `GET /saved-results` · `GET /saved-results/{id}` |
| 9 | `POST /routines` (경로 A) · `GET /routines/{id}` · `PATCH /routine-tasks/{id}` |

### 시간이 남으면

`PATCH /profiles/me/priorities` · `DELETE /routines/{id}` · `DELETE /analyses` · 알림 설정 CRUD · `POST /routines` 경로 B(STANDALONE)

### 이번엔 안 한다

| 제외 | 사유 |
| --- | --- |
| 구독·결제 (F-12) | 화면 미설계(PRD O-3). **분석권 차감·검증은 실제로 구현한다.** 결제 연동만 뺀다 |
| 인바디 OCR (F-03) | 선택 기능. 직접 입력으로 대체 가능 |
| **비교 이미지 생성** | 제공자 정책 거부 가능성 + 시간. `image_status = SKIPPED`로 시작. §6 참조 |
| 알림 발송 스케줄러 | 설정 저장까지만. 실제 푸시는 제외 |
| 미수행 재배치 | PRD O-9로 이미 보류 |

---

## 3. 개발 원칙 — 목업 금지

> **가짜를 만들지 않는다. 실제 서비스를 만든다.**

| # | 원칙 |
| --- | --- |
| 1 | **고정 응답 컨트롤러를 만들지 않는다.** 열려 있는 엔드포인트는 전부 실제 로직으로 동작한다 |
| 2 | **AI 호출을 fixture로 대체하지 않는다.** OpenAI가 안 되면 되게 만들거나, 그 기능을 범위에서 뺀다 |
| 3 | **더미 데이터를 DB에 넣지 않는다.** 화면에 보이는 값은 전부 사용자 입력이나 AI 출력에서 나온다 |
| 4 | **미구현은 목업으로 덮지 않는다.** 아직 없는 엔드포인트는 없는 채로 두고, 무엇이 없는지 프론트에 알린다 |
| 5 | 에러를 삼키지 않는다. 실패는 [API.md](../API.md) §4의 정확한 코드로 사용자에게 전달한다 |
| 6 | 하드코딩된 값을 두지 않는다. 설정은 `application.yml`, 비밀은 환경 변수 |

**범위를 줄이는 것은 괜찮다. 있는 척하는 것이 안 된다.** §2에서 잘라낸 기능들은 목업으로 흉내 내지 않고 그대로 비워 둔다.

### 프론트는 어떻게 막히지 않게 하는가

목업 없이 해결한다 — **의존 순서대로 실제 엔드포인트를 빠르게 인도한다.**

| 시점 | 인도되는 것 | 프론트가 할 수 있는 것 |
| --- | --- | --- |
| 금 오전 | `signup` · `login` · `refresh` · `users/me` | 로그인 화면 실연동 |
| 금 오후 | `uploads/presigned` · `profiles` | 프로필 등록 실연동 |
| 토 오후 | `analyses` · `keywords` · `result` | 고점 입력 · 폴링 · 결과 화면 |
| 일 오전 | `saved-results` · `routines` · `routine-tasks` | 서랍 · 목표 |

- 프론트는 아직 안 열린 화면을 **[API.md](../API.md) 스키마 기준으로 UI만 먼저** 만들고, 엔드포인트가 열리는 대로 연결한다.
- 백엔드는 **매일 작업 종료 시 "오늘 열린 엔드포인트" 목록을 공유**한다. 이게 목업을 대신하는 장치다.

---

## 4. 일자별 계획

### 금 (8/14) — 인증 · 프로필 · 리스크 제거

| 시간 | 작업 | 산출물 |
| --- | --- | --- |
| 오전 1 | JWT 인증 | `JwtProvider` · `JwtAuthenticationFilter` · `UserPrincipal`<br>`SecurityConfig`에 필터 결선 (현재 TODO 제거) |
| 오전 2 | `auth` · `user` 도메인 | signup · login · refresh · `GET /users/me`<br>가입 시 `Subscription(TRIAL)` 생성 · **실동작 배포** |
| 오후 2 | `storage` | `S3Presigner` 기반 업로드/조회 URL 발급<br>`ObjectKeyFactory` · **소유 경로 검증** |
| 오후 3 | `profile` | `POST /profiles` · `GET /profiles/me`<br>`priorities` 3개 검증 · 인바디 6종 JSONB |
| 저녁 | 🔴 **AI 스파이크** | OpenAI Structured Output **1회 성공**시키기 |

**저녁 스파이크가 이 날의 핵심이다.** 토요일 전체가 AI 파이프라인에 걸려 있으므로, 금요일 밤에 "strict JSON Schema로 원하는 형태가 실제로 나온다"를 확인해야 한다. 스파이크는 파이프라인 없이 단독 `main()` 이나 테스트로 돌린다.

- 확인할 것: 모델 ID가 유효한가 / `json_schema` strict가 먹는가 / 한국어 출력 품질 / 응답 지연
- 실패하면 토요일 계획을 §6 대안으로 바꾼다

**금요일 종료 기준** — 프론트가 회원가입·로그인·프로필 등록을 실제 API로 할 수 있다.

---

### 토 (8/15) — 분석 파이프라인 (가장 큰 덩어리)

| 시간 | 작업 | 산출물 |
| --- | --- | --- |
| 오전 1 | `ai` 모듈 | `OpenAiClient` (타임아웃 60초 · 429/5xx 2회 재시도)<br>`JsonSchemas` · `SystemPrompts` · `AiJobRecorder` |
| 오전 2 | 프로필 분석 | `PROFILE_ANALYSIS` → `profiles.analysis_summary` |
| 오후 1 | 키워드 추출 | `POST /analyses` → 202<br>`AFTER_COMMIT` + `@Async` → `EXTRACTING` → `KEYWORDS_READY` |
| 오후 2 | 상태 폴링 | `GET /analyses/{id}` — `status` · `progress` · `pollAfterMs` |
| 오후 3 | 결과 생성 | 키워드 확정 → `GENERATING` → `DONE`<br>`categoryChanges` 3건 검증 + `priorities` 순서 정렬 |
| 저녁 | 가드레일 · 실패 처리 | `OutputValidator` (점수 패턴 · 금지어) 1회 재생성<br>`AnalysisSweeper` (3분 초과 → FAILED) |

**주의점 3가지**

1. **트랜잭션 밖에서 OpenAI를 호출한다.** `@Transactional` 메서드만 가진 별도 빈(`AnalysisTxService`)을 두고 상태 갱신만 짧게 처리한다. 같은 클래스 내부 호출은 프록시를 안 타므로 반드시 빈을 분리한다. ([ARCHITECTURE.md](ARCHITECTURE.md) §5.2)
2. **`@Async`는 `AFTER_COMMIT` 이벤트로 시작한다.** 커밋 전에 시작하면 비동기 스레드가 없는 행을 조회한다.
3. **분석권 차감은 결과 저장과 같은 트랜잭션**에서 단일 UPDATE로 한다.

**토요일 종료 기준** — 고점 텍스트를 넣으면 키워드가 나오고, 선택하면 결과 JSON이 완성된다.

---

### 일 (8/16) — 서랍 · 목표 · 마무리

| 시간 | 작업 | 산출물 |
| --- | --- | --- |
| 오전 1 | `drawer` | `POST .../result/save` · `GET /saved-results` (3섹션) · 상세 |
| 오전 2 | `routine` 경로 A | `ROUTINE_GENERATION` → `Routine` + `RoutineTask` 생성 |
| 오전 3 | 목표 조회 · 체크 | `GET /routines/{id}` · `PATCH /routine-tasks/{id}` + 진행률 |
| 오후 1 | **E2E 통과** | 데모 경로를 처음부터 끝까지 한 번에 통과 |
| 오후 2 | 정리 | 에러 응답 점검 · 소유권 검증 누락 확인 · 로그 정리 |
| 저녁 | 여유분 | 남은 시간에 §2 "시간이 남으면" 항목 또는 버그 |

**일요일 종료 기준** — 데모 경로 E2E 통과. 이게 안 되면 다른 걸 다 만들어도 실패다.

---

## 5. 매일 지킬 것

| # | 규칙 |
| --- | --- |
| 1 | **[API.md](../API.md)의 응답 스키마를 바꾸지 않는다.** 바꿔야 하면 문서를 먼저 고치고 프론트에 알린다 |
| 2 | 하루가 끝나면 **배포된 API가 동작하는 상태**로 둔다. 반쯤 고친 채로 두지 않는다 |
| 3 | **오늘 열린 엔드포인트 목록을 매일 공유한다.** 목업이 없으므로 이 공유가 프론트의 유일한 진행 신호다 |
| 4 | 소유권 검증을 Service 진입부에 넣는다. 남의 리소스는 `403 FORBIDDEN_RESOURCE` |
| 5 | 커밋은 도메인 단위로 자주. `backend` 브랜치에 올린다 |

---

## 6. 리스크와 대안

| 리스크 | 신호 | 대안 |
| --- | --- | --- |
| **AI 출력이 스키마를 안 지킴** | 금요일 스파이크 실패 | 재시도 2회 → **스키마를 단순화**한다. 필드 수를 줄이고 중첩을 없앤다. 그래도 안 되면 모델을 바꾼다. **fixture로 대체하지 않는다** (§3) |
| **AI 응답이 너무 느림** (>60초) | 스파이크에서 지연 확인 | 프롬프트 축소 · 키워드 수 8→5 · 참고 사진 1장만 사용 · 타임아웃 상향 |
| **이미지 생성 정책 거부** | 실제 인물 편집 거부 | 이미 범위에서 제외. `image_status = SKIPPED`가 기본이고 프론트는 이미지 블록을 렌더링하지 않는다 |
| **토요일에 파이프라인이 안 끝남** | 토 저녁까지 `DONE` 미도달 | 일요일 오전을 파이프라인에 쓰고 **목표 생성(일 오전 2~3)을 범위에서 뺀다.** 데모는 결과 화면까지로 줄인다 |
| OpenAI 비용·레이트리밋 | 429 빈발 | `ai_jobs`에서 토큰 추적. 개발 중 반복 호출은 **입력을 바꿔가며 실제로** 하되 횟수를 관리한다 |

**범위 축소가 유일한 대안이다.** 어느 리스크에서도 가짜 응답으로 메우지 않는다. 못 만든 기능은 못 만든 채로 두고 데모 시나리오를 줄인다.

**가장 큰 리스크는 AI 파이프라인이다.** 그래서 금요일 저녁에 스파이크를 넣었다. 토요일 아침에 처음 시도하면 늦다.

---

## 7. 완료 기준 (DoD)

일요일 밤에 아래가 전부 참이어야 한다.

- [ ] 회원가입 → 로그인 → 토큰으로 보호된 API 호출이 된다
- [ ] 사진·우선순위 3개·키·몸무게로 프로필이 생성된다
- [ ] 고점 텍스트를 넣으면 키워드 5~8개가 나온다
- [ ] 키워드를 고르면 결과가 생성된다 (`categoryChanges` 3건 · `dailyCares` 3건)
- [ ] 결과를 서랍에 저장하고 다시 꺼내볼 수 있다 (`viewState` 분기 포함)
- [ ] 저장된 결과로 목표를 만들고 태스크를 완료 체크할 수 있다
- [ ] 실패 시 [API.md](../API.md) §4의 코드와 한국어 문구가 그대로 내려온다
- [ ] **결과 응답에 `disclaimer`가 항상 포함된다** (PRD F-07 배포 조건)
- [ ] 결과 어디에도 점수·등급·순위 표현이 없다 (PRD G-1)
- [ ] **고정 응답·더미 데이터·fixture가 코드에 하나도 없다** (§3)
- [ ] 열려 있는 모든 엔드포인트가 실제 DB와 실제 AI 호출로 동작한다

---

## 8. 프론트와의 접점

프론트가 병렬 개발되므로 아래만 지키면 충돌하지 않는다.

| 항목 | 약속 |
| --- | --- |
| 응답 봉투 | `{ success, data }` / `{ success, error }` 고정 |
| 에러 문구 | 서버가 소유. `error.message`를 그대로 노출하면 된다 |
| 폴링 | `GET /analyses/{id}` 하나. `KEYWORDS_READY`에서도 폴링 유지 |
| 이미지 | 업로드·조회 모두 presigned URL. 조회 URL 10분 만료 |
| 스키마 변경 | **문서 수정 → 공유 → 구현** 순서. 역순 금지 |

배포 주소와 계정은 별도로 공유한다. 로컬만 쓸 경우 `http://localhost:8080/api/v1`.

---

## 9. 남는 것 (스프린트 이후)

| 항목 | 사유 |
| --- | --- |
| 구독·결제 | 화면 미설계 (PRD O-3) |
| 인바디 OCR | 선택 기능 |
| 비교 이미지 생성 | 정책 리스크 · 시간 |
| 알림 발송 | 스케줄러 · FCM 미정 |
| 미수행 재배치 | PRD O-9 |
| Testcontainers 테스트 | Docker 설치 후 |
| 동의·생년월일 | PRD O-2, 화면 미설계. **만 14세 확인은 법적 요구사항이므로 출시 전 필수** |
