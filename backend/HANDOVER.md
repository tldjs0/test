# 인수인계 — 2026-08-14 (금) 작업 종료

내일(8/15) 작업을 이어받는 사람을 위한 문서.

**먼저 읽을 것** — [AGENTS.md](../AGENTS.md) §3 규칙 · §4-1 오답 노트 → 이 문서 → [TASKS.md](TASKS.md)

---

## 1. 한 줄 요약

**백엔드는 데모 경로가 끝까지 동작한다. 프론트는 화면이 다 있고 백엔드에 붙어 있다.**
남은 것은 대부분 **화면 연결**과 **콘솔·서버 설정**이다.

| | 상태 |
| --- | --- |
| 백엔드 | 엔드포인트 22개 · AI 6단계 전부 실동작 · 마이그레이션 V1~V5 |
| 프론트 | 화면 22개 · HTTP 어댑터 연결 완료 · 웹 번들 빌드 통과 |
| 검증 | E2E **218건** · 단위 테스트 **41건** · AI 호출 실패 0 |

---

## 2. 실행 방법

### 백엔드

```bash
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`.env`는 자동으로 읽히지 않는다. 셸 환경 변수로 주입하거나 IDE 실행 구성에 넣는다.
`backend/.env`에 실제 값이 다 들어 있다 (gitignore 대상이라 저장소에는 없다).

### 프론트

```bash
cd frontend && npm install
echo "EXPO_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1" > .env
npx expo start --web
```

- **`EXPO_PUBLIC_API_BASE_URL`이 비어 있으면 mock 모드**로 돈다 (서버 없이 화면만).
- **Expo 웹은 8081 포트**를 쓴다. 백엔드 `CORS_ALLOWED_ORIGINS`에 `http://localhost:8081`을 추가해야 한다.
- **실기기는 `localhost`가 아니라 PC의 LAN IP**를 넣는다.
- ⚠️ **웹 브라우저에서는 사진 업로드가 막힌다.** S3 버킷 CORS의 `AllowedOrigins`에 5173만 있어서다. **실기기·시뮬레이터는 CORS 대상이 아니라 그대로 동작한다.**

### 테스트

```bash
cd backend
./gradlew test              # Docker 불필요. 41건
./gradlew integrationTest   # Docker 필요 (현재 환경에는 없음)

cd frontend
npm run typecheck && npm run lint
```

---

## 3. 내일 할 일 — 우선순위 순

### 🔴 1순위 — 막고 있는 것 (코딩 아님)

| # | 할 일 | 왜 막고 있나 |
| --- | --- | --- |
| 1 | **Google 콘솔 설정 (0-6)** → `.env`의 `GOOGLE_CLIENT_ID` | 백엔드 코드는 완성됐다. **성공 경로만 검증 못 했다.** 설정 후 값만 넣으면 끝. 코드 수정 없음 |
| 2 | **S3 버킷 CORS에 `http://localhost:8081` 추가** | 웹 브라우저에서 사진 업로드 테스트가 불가능하다 |
| 3 | **가비아 서버 확인 (0-3)** | 배포 주소가 정해져야 CORS 양쪽에 넣는다 |

### 🟡 2순위 — 짧고 효과 큰 것

| # | 할 일 | 규모 |
| --- | --- | --- |
| 4 | `ProfileResponse.analysisSummary`에 `@JsonInclude(ALWAYS)` | **한 줄.** 계약 위반 하나가 사라진다 |
| 5 | 프론트 **서랍 화면**을 `getDrawer()`에 연결 | 어댑터 함수는 이미 있다. 3섹션 렌더링만 |
| 6 | 프론트 **설정 화면**의 알림 토글을 API에 연결 | `getNotificationSettings` / `updateNotificationSettings` 이미 있음 |
| 7 | 프론트 **인바디 스캔 버튼** 추가 (시안 08) | `scanInbody()` 이미 있음. 폼 자동 채우기만 |

### 🟢 3순위 — 화면이 없는 기능

| # | 할 일 |
| --- | --- |
| 8 | 목표 **경로 B**(카테고리+기간 선택) 화면 — 백엔드 `createStandaloneRoutine` 준비됨 |
| 9 | 결과 **서랍 열람**(`viewState=SAVED`) 화면 — `getSavedResult` 준비됨 |
| 10 | 소셜 로그인 버튼 연결 (1번 완료 후) |

> **어댑터에는 있는데 화면이 안 쓰는 함수** — `getDrawer` · `getSavedResult` ·
> `deleteSavedResult` · `scanInbody` · `createStandaloneRoutine` · `listRoutines` ·
> `deleteRoutine` · `updateNotificationSettings` · `deleteAllAnalyses`.
> 전부 백엔드에서 검증된 것들이라 **화면에서 부르기만 하면 된다.**

---

## 4. 구현 완료 — 백엔드

### 엔드포인트 22개

```text
POST   /auth/signup · /auth/login · /auth/refresh · /auth/logout
POST   /auth/oauth/google                 Google 로그인 (콘솔 설정 대기)
GET    /users/me
PATCH  /users/me                          닉네임 변경 (API.md에 없던 추가)
DELETE /users/me                          계정 삭제 · 사진 즉시 삭제
POST   /uploads/presigned
POST   /profiles · GET · PATCH /profiles/me
PATCH  /profiles/me/priorities
DELETE /profiles/me/photo
POST   /profiles/inbody/scan              인바디 OCR (저장 안 함)
POST   /analyses                          202
GET    /analyses/{id}                     상태 폴링
GET    /analyses/{id}/keywords
POST   /analyses/{id}/keywords/selection  202
GET    /analyses/{id}/result
POST   /analyses/{id}/result/save
DELETE /analyses                          전체 삭제 (목표는 남는다)
GET    /saved-results · GET · DELETE /saved-results/{id}
POST   /routines · GET · GET · DELETE /routines/{id}
PATCH  /routine-tasks/{id}
GET · PATCH /notifications/settings
POST   /notifications/device-tokens
```

### AI 6단계 — 전부 실동작 (실측 지연)

| 단계 | 지연 | 비고 |
| --- | --- | --- |
| `INBODY_OCR` | 1.7초 | 6종 정확 판독 확인 |
| `PROFILE_ANALYSIS` | 2.1초 | `analysis_summary` 자동 생성 |
| `ROUTINE_GENERATION` | 2.1초 | 경로 A·B |
| `KEYWORD_EXTRACTION` | 3.7초 | 5~8개, FACE 분류 동작 |
| `RESULT_GENERATION` | 4.0초 | categoryChanges 3종 검증 |
| `IMAGE_GENERATION` | **33초** | 별도 풀. 합성 |

### 설계상 반드시 지켜야 할 것

| # | 내용 |
| --- | --- |
| 1 | **AI 호출은 트랜잭션 밖.** `~TxService`(짧은 트랜잭션)와 `~Pipeline`(AI 호출)을 **별도 빈**으로 분리했다. 같은 클래스면 프록시를 안 타 트랜잭션이 안 걸린다 |
| 2 | **비동기 시작은 `@TransactionalEventListener(AFTER_COMMIT)`.** 커밋 전에 시작하면 없는 행을 조회한다 |
| 3 | **분석권 차감은 결과 저장과 같은 트랜잭션.** 순서는 **차감 → 로드 → 저장** (AGENTS.md N-4) |
| 4 | **이미지는 별도 풀(`imageExecutor`).** 33초짜리가 텍스트 파이프라인 스레드를 잡으면 분석이 밀린다 |
| 5 | **좀비 정리** — 분석 3분 · 이미지 8분. `KEYWORDS_READY`는 제외(사용자 대기 상태) |

---

## 5. 구현 완료 — 프론트

| 계층 | 파일 |
| --- | --- |
| HTTP 래퍼 | `services/api.ts` — 봉투 해제 · 토큰 주입 · **401 시 refresh 후 1회 재시도** · 70초 타임아웃 |
| 세션 | `services/session.ts` — AsyncStorage |
| 어댑터 | `services/backend.ts` — 엔드포인트별 타입 함수 |
| 상태 | `state/AppState.tsx` — `mode: 'mock' \| 'server'` 분기 |

**붙어 있는 흐름** — 회원가입 · 로그인 · 로그아웃 · 계정삭제 / 닉네임 / 사진 업로드 → 프로필 등록 / 고점 분석(생성 → 폴링 → 키워드 선택 → 결과) / 서랍 저장 / 목표 생성 · 완료 체크 / 비교 이미지 4분기 렌더링

**mock 모드는 그대로 살아 있다.** 서버 없이도 데모 가능.

---

## 6. 미구현 — 안 된 것

### 의도적으로 안 만든 것

| 항목 | 이유 |
| --- | --- |
| **구독·결제** (`/subscriptions/*`, 만료 차단) | **팀 결정.** AGENTS.md §4 참조. 분석권 차감은 유지 |
| `AccountPurger` (30일 후 하드 삭제) | 사진은 삭제 시점에 이미 지워져 법적 요구는 충족. 파괴적 배치라 보류 |
| `NotificationScheduler` (발송) | FCM vs Web Push 미정. 설정·토큰은 이미 쌓임 |
| `GET /consents/terms` | 동의 화면 미설계 (PRD O-2) |

### 알려진 구멍 3가지

| # | 내용 | 규모 |
| --- | --- | --- |
| 1 | **`ProfileResponse.analysisSummary`가 null일 때 키째로 사라짐.** 다른 응답은 고쳤는데 프로필만 남음 | 한 줄 |
| 2 | **인바디 서류 사진이 S3에 영구 잔존.** key를 DB에 안 남기는 설계라 지울 대상을 알 수 없다 | 버킷 수명 주기 정책 |
| 3 | **`PATCH /profiles/me` 후 `analysis_summary` 미갱신.** 신체 정보를 고쳐도 AI 요약은 그대로 | 정책 결정 필요 |

### 검증 못 한 것

| 항목 | 왜 |
| --- | --- |
| **Google 로그인 성공 경로** | `GOOGLE_CLIENT_ID`가 자리표시자. 실패 경로만 확인 |
| **실제 인물 사진의 이미지 합성** | 검증은 전부 합성 이미지로 했다. 실사진은 제공자가 거부할 수 있다(→ `FAILED`로 정상 처리) |
| **Testcontainers 통합 테스트** | Docker 없음 |
| **실기기 동작** | 웹 번들만 확인 |

---

## 7. 이미 물린 함정 — 다시 밟지 말 것

**오답 노트 9건이 [AGENTS.md](../AGENTS.md) §4-1에 있다.** 요약만 적는다.

| # | 한 줄 |
| --- | --- |
| N-1 | strict 스키마의 `maxLength`는 문장을 **중간에서 자른다**. 에러가 아니라 200으로 온다 |
| N-2 | `jdbc.bind: TRACE`를 켜면 로그에 사진 key와 고점 원문이 샌다 |
| N-3 | Docker 없다고 테스트를 통째로 죽이지 않는다 — 태그로 갈랐다 |
| N-4 | `@Modifying(clearAutomatically)`는 영속성 컨텍스트를 비운다. **차감 → 로드 → 저장** |
| N-5 | 한글 든 SQL을 셸에서 psql로 파이프하지 않는다 (인코딩 깨짐) |
| N-6 | `LocalTime`이 `"21:00:00"`으로 나간다. 계약은 `"21:00"` |
| N-7 | 전역 `non_null`이 문서에 명시된 `null` 키를 지운다 |
| N-8 | soft delete 테이블의 UNIQUE는 `WHERE deleted_at IS NULL`로 범위를 좁혀야 한다 |
| N-9 | **프롬프트 안에서 부딪히는 지시는 조용히 무시된다.** 바뀌길 기대한 게 안 바뀌면 충돌부터 의심 |

**D1에서 물린 것도 유효하다** — `ddl-auto: validate`라 엔티티가 스키마와 정확히 일치해야 앱이 뜬다 / Auditing provider를 건드리지 말 것 / 레코드에 `isXxx()`를 만들면 `@JsonIgnore` / S3 삭제 판정은 `200 → 403`.

---

## 8. 팀 확인 대기 — 문서·구현 불일치 17건

임의로 정하지 않고 남긴 것들이다. **대부분 문서만 고치면 된다.**

| # | 내용 | 성격 |
| --- | --- | --- |
| 1 | `POST /analyses`에 `retriedFrom` 필드가 없는데 "서버가 기록한다"고 적혀 있다 → 선택 필드로 추가 구현 | 문서 |
| 2 | `referenceImageKeys` 상한 없음 → 서버가 5장 제한 | 문서 |
| 3 | `profile_analysis` JSON Schema가 문서에 없다 → ERD §5.3에서 역산 | 문서 |
| 4 | 금지어가 API.md 3개 / ARCHITECTURE 4개 → 넓은 쪽 채택 | 문서 |
| 5 | **`ProfileResponse.analysisSummary` null 키 소실** | **코드 (§6-1)** |
| 6 | 결과 `overview.keywords`가 선택분인지 전체 후보인지 모호 → 예시대로 선택분만 | **FE 협의** |
| 8 | ERD §6의 V1 전문에 `profiles.updated_at` 누락 | 문서 |
| 10 | 사진 없는 프로필로 분석 시도할 전용 에러 코드 없음 → `PROFILE_REQUIRED` 대체 | 문서 |
| 12 | 목표 생성 **201 동기 vs 202 비동기** 문서 충돌 → API.md 따라 201 | 문서 |
| 13 | `GET /routines` 응답 미정의 → `RoutineSummary` 재사용 | 문서 |
| 14 | 목표 완주 시 `COMPLETED` 전이 규칙이 문서에 없음 → 구현함 | 문서 |
| 15 | `routine_tasks` 배치 주기 미결 (ERD E-3) → A는 하루, B는 주 단위 | 팀 결정 |
| 16 | 경로 A가 "저장된 결과"만 허용하는지 불명확 → 소유권만 검증 | 팀 결정 |
| 17 | `*계정, 목표 정보는 삭제되지 않아요` 문구가 `DELETE /routines/{id}`에 붙어 있다 | 문서 |
| 18 | `ScanConfidence` 값 미정의 → 판독 개수로 HIGH/MEDIUM/LOW | 문서 |
| 19 | 알림 응답만 공통 봉투 없이 예시됨 → §1 봉투 적용 | 문서 |
| 20 | **인바디 서류 사진 스토리지 잔존** | **운영 (§6-2)** |

**해소된 것** — #7(`comparison_image_key` 1개 vs URL 2장), #9(`.env.example` UTF-8 복구), #11(구독 만료 에러 코드), G-2 문구.

**PRD 미결도 그대로** — **O-1**(수치 대시보드 ↔ G-1 충돌, 최우선) · **O-2**(동의 화면·생년월일, 법적) · O-4 · O-5.

### 프론트 스택이 문서와 다르다

PRD §12·AGENTS.md §2는 **React · TypeScript · Vite (모바일 웹)**, 실제는 **Expo React Native**(`expo-router`, RN 0.81, `react-native-web`)다. 폴더 구조도 다르다. **문서 수정이 필요하다.**
패키지 이름도 `chugumi-producer`인데, AGENTS.md 규칙 1이 "추구미를 쓰지 않는다"고 정하고 있다.

---

## 9. 저장소 상태

| 항목 | 값 |
| --- | --- |
| 원격 | https://github.com/Ohhaeseo/Gojeom_AAC |
| 기본 브랜치 | `codex/readme` (⚠️ `main`이 아니다) |
| 작업 브랜치 | `backend` — **백엔드 + 프론트가 합쳐진 최신 브랜치** |
| 마이그레이션 | V1 → V5 |

- `backend` 브랜치가 `origin/codex/readme`(프론트)를 머지한 상태다. 여기서 이어가면 된다.
- 커밋·푸시는 **사용자가 요청할 때만** 한다.
- ⚠️ **현재 커밋되지 않은 변경이 있다.** 이번 세션의 프론트 어댑터·이미지 생성·문서 수정.
