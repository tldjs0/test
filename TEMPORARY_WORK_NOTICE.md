# Temporary backend work

이 저장소는 IntelliJ 연동 전까지 `GO.` 백엔드 작업을 임시 보관하기 위한 저장소입니다.

- 원본 저장소: <https://github.com/Ohhaeseo/Gojeom_AAC.git>
- 포함 범위: `backend/`만 포함
- 제외 범위: `frontend/`, 실제 `.env`, 빌드 결과물 및 IDE 캐시
- 다시 작업할 때는 이 임시 저장소보다 원본 저장소의 최신 문서, 브랜치 정책 및 작업 지시를 우선합니다.
- 이 임시 저장소의 코드는 Java 21 환경에서 전체 컴파일 및 테스트가 완료된 상태가 아닙니다.

IntelliJ에서는 `backend/build.gradle`을 Gradle 프로젝트로 열고 Project SDK와 Gradle JVM을 Java 21로 설정합니다.
