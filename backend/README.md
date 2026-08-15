# GO. — Backend

Spring Boot 기반 REST API 서버.

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| Framework | Spring Boot 3.3.5 · **Java 21** |
| 빌드 | Gradle 8.10 (Wrapper) |
| ORM | Spring Data JPA · Hibernate 6 |
| DB | PostgreSQL 16 |
| 마이그레이션 | Flyway |
| 인증 | Spring Security · JWT |
| 스토리지 | S3 호환 오브젝트 스토리지 (국내 리전) |
| AI | OpenAI API (Structured Outputs · Image Edits) |

## 작업 브랜치

이 폴더의 작업은 `backend` 브랜치에서 진행한다.

```bash
git switch backend
```

## 구조 · 아키텍처

**[ARCHITECTURE.md](ARCHITECTURE.md)** 가 정본이다. 패키지 구조, 계층 규칙, 비동기 분석 파이프라인, OpenAI 연동, 트랜잭션 경계, 설정, 테스트 전략을 담고 있다.

```text
backend/src/main/java/com/gojeom/
├─ common/        공통 응답 · 에러 코드 · 설정
├─ auth/ user/    JWT 인증 · 계정
├─ profile/       사진 · 우선순위 · 신체정보 · 인바디 OCR
├─ analysis/      ★ 고점 분석 파이프라인
├─ drawer/        서랍 (저장된 결과)
├─ routine/       목표 생성 2경로 · 완료 체크
├─ notification/  알림 설정 · 발송 스케줄러
├─ subscription/  구독 · 분석권
├─ ai/            ★ OpenAI 클라이언트 · 프롬프트 · 스키마 · 가드레일
└─ storage/       presigned URL
```

특히 아래 세 가지는 구현 전에 읽어야 한다.

- **§5 비동기 분석 파이프라인** — 트랜잭션 밖에서 OpenAI를 호출하는 이유와 방법, 좀비 분석 정리
- **§6.3 가드레일 후검증** — 프롬프트만으로 G-1~G-8을 보장하지 않는다
- **§7 분석권 차감** — 단일 UPDATE로 중복 차감을 막는다

## 로컬 실행

**1. 환경 변수 준비** — `.env.example`을 복사해 값을 채운다. `.env`는 커밋하지 않는다(저장소 공개).

```bash
cp backend/.env.example backend/.env
```

**2. PostgreSQL 기동**

```bash
docker compose -f backend/docker-compose.yml up -d
```

**3. 앱 실행** — Flyway가 `V1__init.sql`을 자동 적용한다.

```bash
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

**4. 확인**

```bash
curl http://localhost:8080/actuator/health
```

### 테스트

Testcontainers가 PostgreSQL을 띄우므로 **Docker가 실행 중이어야 한다.**

```bash
cd backend && ./gradlew test
```

### 검증 상태

로컬 PostgreSQL 18.4로 검증 완료 (2026-08-13).

| 항목 | 결과 |
| --- | --- |
| `compileJava` · `compileTestJava` | ✅ |
| `build` (bootJar) | ✅ |
| 앱 기동 | ✅ 5.0초 |
| Flyway `V1__init.sql` 적용 | ✅ 테이블 15개 · 인덱스 35개 |
| `ck_priorities_len` 제약 동작 | ✅ 3개 아닌 배열 거부 확인 |
| `/actuator/health` | ✅ `{"status":"UP"}` |
| 보호된 경로 인증 | ✅ `401` (JWT 필터 미구현 상태의 의도된 동작) |
| `./gradlew test` (Testcontainers) | ⚠️ **미실행** — Docker 없음 |

**로컬은 PostgreSQL 18, `docker-compose.yml`과 테스트는 16을 쓴다.** Flyway가 아래 경고를 남기지만 마이그레이션은 정상 적용된다.

```text
Flyway upgrade recommended: PostgreSQL 18.4 is newer than this version of Flyway
and support has not been tested. The latest supported version of PostgreSQL is 16.
```

CI/테스트와 버전을 맞추려면 로컬도 16으로 낮추거나, `docker-compose.yml`과 Testcontainers 이미지를 18로 올린다.

## 환경 변수

`application.yml`에 값을 직접 적지 않는다. 전부 환경 변수로 주입한다. 목록은 [.env.example](.env.example) 참조.

| 변수 | 비고 |
| --- | --- |
| `DB_URL` `DB_USERNAME` `DB_PASSWORD` | |
| `JWT_SECRET` | HS256용, 32바이트 이상 |
| `OPENAI_API_KEY` | **절대 커밋 금지** |
| `OPENAI_MODEL_TEXT` `OPENAI_MODEL_IMAGE` | 모델 ID 핀 고정 |
| `STORAGE_*` | S3 호환. 리전은 국내 |
| `CORS_ALLOWED_ORIGINS` | 프론트 오리진 |

## 구현 시 반드시 지킬 것

- **OpenAI 모델 ID는 하드코딩하지 않는다.** `openai.model.text` / `openai.model.image` 설정으로 핀 고정하고, 실제 사용값을 `ai_jobs.model`에 기록한다.
- 텍스트 단계는 **strict JSON Schema(Structured Outputs)** 로 강제한다. 자유 서술 파싱 금지.
- 가드레일(PRD §8.2)은 프롬프트만으로 보장하지 않는다. **서버 측 후검증**을 반드시 거친다. ([API.md](../API.md) §7.4)
- 분석권 차감은 **결과 생성 성공 시점**에 단일 UPDATE 문으로 처리한다. (PRD O-7)
- 이미지 생성 실패는 예외가 아니라 **정상 시나리오**다. `image_status = FAILED`로 저장하고 텍스트 결과만 응답한다.
- **인바디 OCR 결과를 바로 저장하지 않는다.** 사용자 확인을 거친 뒤 저장 API로 반영한다. (PRD G-8)
- `categoryChanges` 3건은 `SKIN`·`BODY`·`HEALTH` 각 1건인지 서버가 검증하고, `profiles.priorities` 순서로 정렬해 저장한다.
- `ai_jobs`에 **프롬프트 원문과 사용자 사진을 저장하지 않는다.** (PRD §9)

## 관련 문서

- [HANDOVER.md](HANDOVER.md) — **작업을 이어받는다면 여기부터** (현재 상태 · 함정 · 다음 작업)
- [PLAN.md](PLAN.md) — **개발 계획 (8/14~16 스프린트) · 범위 · 우선순위**
- [TASKS.md](TASKS.md) — **작업 체크리스트 · 시작 전 블로커**
- [ARCHITECTURE.md](ARCHITECTURE.md) — **백엔드 기술 아키텍처 · 패키지 구조**
- [PRD.md](../PRD.md) — 제품 요구사항
- [design.md](../design.md) — 디자인 토큰 · 컴포넌트
- [API.md](../API.md) — API 계약 (엔드포인트 36종 · OpenAI 연동 규격)
- [ERD.md](../ERD.md) — 데이터 모델 · Flyway 초기 마이그레이션
