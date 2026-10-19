# 로컬 개발 진입점.
# 인프라(MySQL, Redis)는 docker-compose.local.yml, 앱은 호스트 Gradle로 실행한다.
# 앱 환경변수는 Infisical dev 환경의 /application 경로에서 주입한다 (docker/entrypoint.sh와 같은 경로).

COMPOSE        := docker compose -f docker-compose.local.yml
GRADLE         := ./gradlew
INFISICAL_ENV  ?= dev
INFISICAL_PATH ?= /application
INFISICAL_RUN  := infisical run --env=$(INFISICAL_ENV) --path=$(INFISICAL_PATH) --
# 미로그인 시 CLI가 안내 문구를 내보낸다.
INFISICAL_EXPORT := infisical export --env=$(INFISICAL_ENV) --path=$(INFISICAL_PATH) --format=dotenv --silent </dev/null

# local 프로파일이 기본값 없이 참조하는 키. application-local.yaml과 함께 유지한다
REQUIRED_SECRETS := DB_URL DB_USERNAME DB_PASSWORD REDIS_HOST REDIS_PASSWORD

.DEFAULT_GOAL := help
.PHONY: help doctor up down reset ps logs run ide env test check format build mysql redis

help: ## 명령 목록
	@echo "CLI로 실행: make doctor && make run  |  IDE로 실행: make ide"
	@grep -hE '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN { FS = ":.*## " } { printf "  \033[36m%-8s\033[0m %s\n", $$1, $$2 }'

doctor: ## 필수 도구, Infisical 로그인, 시크릿 키 존재 여부 점검
	@command -v docker >/dev/null || { echo "[x] docker 미설치"; exit 1; }
	@docker info >/dev/null 2>&1 || { echo "[x] Docker 데몬이 실행 중이 아님"; exit 1; }
	@docker compose version >/dev/null 2>&1 || { echo "[x] docker compose v2 필요"; exit 1; }
	@command -v java >/dev/null || { echo "[x] JDK 미설치 (Gradle 구동용 17+, 빌드용 25는 Gradle이 자동 설치)"; exit 1; }
	@command -v infisical >/dev/null || { echo "[x] infisical CLI 미설치 (brew install infisical/get-cli/infisical)"; exit 1; }
	@secrets=$$($(INFISICAL_EXPORT)) \
	  || { echo "[x] Infisical 접근 실패. 'infisical login' 후 재시도"; exit 1; }; \
	keys=$$(printf '%s\n' "$$secrets" | cut -d= -f1); missing=""; \
	for k in $(REQUIRED_SECRETS); do printf '%s\n' "$$keys" | grep -qx "$$k" || missing="$$missing $$k"; done; \
	[ -z "$$missing" ] || { echo "[x] Infisical $(INFISICAL_ENV):$(INFISICAL_PATH) 에 없는 키:$$missing"; exit 1; }
	@echo "[ok] docker, compose, java, infisical($(INFISICAL_ENV):$(INFISICAL_PATH)) 준비 완료"

up: ## MySQL, Redis 기동 (healthy까지 대기)
	$(COMPOSE) up -d --wait

down: ## MySQL, Redis 중지 (데이터 유지)
	$(COMPOSE) down

reset: ## MySQL, Redis 중지 + 데이터 볼륨 삭제 (DB 초기화)
	$(COMPOSE) down -v

ps: ## 인프라 컨테이너 상태
	$(COMPOSE) ps

logs: ## 인프라 로그 follow
	$(COMPOSE) logs -f

run: up ## 인프라 기동 후 앱 실행 (local 프로파일, http://localhost:8081/swagger)
	TZ=UTC $(INFISICAL_RUN) $(GRADLE) bootRun

ide: up env ## IDE 실행 준비 (MySQL, Redis 기동 + .env 생성)

env: ## Infisical 시크릿으로 .env 생성 (기존 파일은 .env.bak로 보관)
	@tmp=$$(mktemp) && $(INFISICAL_EXPORT) > "$$tmp" \
	  || { rm -f "$$tmp"; echo "[x] Infisical 접근 실패. 'infisical login' 후 재시도"; exit 1; }; \
	[ ! -f .env ] || cp -p .env .env.bak; \
	{ echo "# make env 가 Infisical $(INFISICAL_ENV):$(INFISICAL_PATH) 에서 생성. 직접 수정하지 말고 다시 make env"; cat "$$tmp"; } > .env; \
	rm -f "$$tmp"; chmod 600 .env; \
	echo "[ok] .env 생성 ($$(grep -c '=' .env)개 키, Infisical $(INFISICAL_ENV):$(INFISICAL_PATH))"

test: ## 테스트 (Testcontainers 사용, make up 불필요)
	$(GRADLE) test

check: ## CI의 테스트 + 포맷 검사 + 커버리지 검증 (Docker 이미지 빌드 검사는 제외)
	$(GRADLE) check

format: ## 포맷 자동 수정
	$(GRADLE) spotlessApply

build: ## 실행 JAR 빌드 (build/libs/app.jar)
	$(GRADLE) bootJar

mysql: ## MySQL shell 접속
	$(COMPOSE) exec mysql sh -c 'exec mysql -u"$$MYSQL_USER" -p"$$MYSQL_PASSWORD" "$$MYSQL_DATABASE"'

redis: ## Redis shell 접속
	$(COMPOSE) exec redis sh -c 'exec redis-cli -a "$$REDIS_PASSWORD" --no-auth-warning'
