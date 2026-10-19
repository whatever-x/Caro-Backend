# Caro-Backend

Caro(플래시카드 학습 앱)의 백엔드. Kotlin, Spring Boot, Spring Modulith, MySQL, Redis.

## 처음 실행하기

- 필요한 것
  - Docker Desktop
  - JDK 17 이상(빌드용 JDK 25는 Gradle이 자동 설치)
  - Infisical CLI
  - make
  - lefthook

```bash
brew install infisical/get-cli/infisical lefthook
infisical login      # Infisical 계정으로 1회
lefthook install     # pre-commit 포맷, pre-push 테스트 훅
make doctor          # 도구, 로그인, 시크릿 키 점검
make run             # MySQL, Redis 기동 후 앱 실행
```

- 앱: http://localhost:8081/swagger
- 헬스체크: http://localhost:9090/actuator/health

IDE에서 실행하려면 `make ide`.
MySQL, Redis를 띄우고 Infisical 값으로 `.env`를 만든다.
Run Configuration이 `.env`를 읽도록 지정한다(IntelliJ는 EnvFile 플러그인 등).
`.env`는 gitignore 대상이다.

## 자주 쓰는 명령

전체 목록은 `make help`.

| 명령 | 설명 |
|------|------|
| `make run` | 인프라 기동 후 앱 실행. Infisical 시크릿 주입, 컨테이너와 같은 UTC |
| `make ide` | 인프라 기동 + `.env` 생성 |
| `make test` | 테스트. Testcontainers를 쓰므로 Docker만 있으면 되고 Infisical은 필요 없다 |
| `make check` | CI의 테스트·포맷·커버리지 검증(Docker 이미지 빌드 검사는 제외) |
| `make format` | 포맷 자동 수정 |
| `make mysql`, `make redis` | 로컬 컨테이너 shell 접속 |
| `make reset` | DB 초기화(볼륨 삭제) |

좁은 범위만 돌리기: `./gradlew test --tests "com.whatever.caro.study.*"`

## 환경변수 (Infisical dev, 경로 `/application`)

> 앱 환경변수는 저장소에 두지 않고 Infisical에서 주입한다.

local 프로파일이 기본값 없이 요구하는 키와 로컬 값:

| 키 | 값 |
|----|----|
| `DB_URL` | `jdbc:mysql://localhost:3306/carolocal?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true` |
| `DB_USERNAME` | `caro` |
| `DB_PASSWORD` | `caro` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PASSWORD` | `caro` |

`REDIS_USERNAME`은 비워 두거나 `default`로 설정한다.
값은 `docker-compose.local.yml`의 고정값과 같아야 하며, DB 계정 값을 바꿨다면 `make reset`으로 볼륨을 지운 뒤 다시 띄운다.
선택 키 `OTLP_ENDPOINT`(기본 `http://localhost:4318`)는 수집기가 없으면 export 실패 로그가 주기적으로 남는다.

## 포트

| 포트 | 용도 |
|------|------|
| 8081 | 앱(local 프로파일). staging/prod는 8080 |
| 9090 | actuator |
| 3306, 6379 | MySQL, Redis 컨테이너. 겹치면 `CARO_MYSQL_PORT`, `CARO_REDIS_PORT`로 바꾼다 |

## 코딩 에이전트로 작업할 때

저장소 규약은 `AGENTS.md`에 있다.
Claude Code는 `CLAUDE.md`를 통해 같은 파일을 읽는다.
모듈 코드를 고치기 전에 그 모듈 디렉터리의 `AGENTS.md`를 먼저 읽는다.
반복 작업 레시피는 `.agents/skills/`에 있다.

- 브랜치는 `develop`에서 분기하고 PR의 base도 `develop`으로 한다. 이름은 `feat/`, `fix/`, `chore/`, `refactor/` 중 하나로 시작한다.
- 에이전트 세션 하나는 PR 하나 분량으로 끝낸다. 대화가 10턴을 넘으면 초기 지시가 희석되므로 새 세션을 연다.
