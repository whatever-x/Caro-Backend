# .github

- 워크플로우 yaml은 spotless 대상이 아니라(`build.gradle.kts`의 `spotless` 블록) `make check`가 검사하지 않고, 저장소에 워크플로우를 로컬에서 실행하는 설정도 없다.
  문법은 `actionlint`로 검사하고, 설치돼 있지 않으면 검증하지 못한 항목으로 보고한다.
  `ci.yml`의 PR 실행 부분은 draft PR을 만들기 전에 사용자에게 확인받고 그 CI 결과로 검증한다. draft PR을 만들면 `opened` 이벤트로 `pr-open-v2.yml`도 실행된다.
- `release.yml`(`v*` 태그 push)·`slack-test.yml`(수동 실행) 전체와 `ci.yml`의 push 전용 부분(`notify-ci-failure` 잡, `docker-build-check`의 `cache-to`)은 PR에서 실행되지 않으니, 고치면 PR에서 검증하지 못한 항목으로 보고한다.
  PR 검사를 통과해도 병합 뒤 develop·main push나 `v*` 태그 push에서 처음 실패할 수 있고, `release.yml`은 태그 push 때 staging 배포까지 실행한다.
- `auto-assign.yml`·`pr-open-v2.yml`·`pr-ready-for-review.yml`·`pr-merge-v2.yml`은 `on.pull_request.types`의 이벤트에서만 실행돼, PR에 커밋을 추가해도 다시 실행되지 않는다.
  다시 실행하려면 reopen(보드 상태 변경)이나 ready_for_review 전환(보드 상태 변경, 리뷰어 지정, 팀 Slack 알림)이 필요하므로 사용자에게 확인받는다.
  `pr-merge-v2.yml`은 병합될 때만 실행되므로 검증하지 못한 항목으로 보고한다.
- `release.yml`의 `Deploy via SSH` 스텝에서 ssh 명령 문자열의 `~`를 `$HOME`으로 바꾸지 않고, `actionlint`의 SC2088 경고를 이유로 이 줄을 수정하지 않는다.
  `$HOME`은 runner에서 runner의 홈 경로로 확장돼 서버에서 `deploy.sh`를 찾지 못하고, `~`는 원격 셸에서 `deploy` 계정의 홈으로 확장된다.
- `ci.yml`에 잡을 추가하면 `notify-ci-failure.needs`에도 추가하고, `release.yml`의 `deploy` 잡에 스텝을 추가하면 `Notify Slack (성공)`·`Notify Slack (실패)` 스텝보다 앞에 둔다.
  `needs` 의존 체인 밖의 잡은 실패해도 알림이 전송되지 않고, 알림 스텝 뒤의 스텝은 실패해도 성공 알림만 전송된다. 두 알림은 PR에서 실행되지 않아 PR CI로 확인할 수 없다.
- Slack 채널을 바꿀 때는 `grep -rn CHANNEL_ID .github/workflows`로 찾은 위치를 모두 바꾼다. 하드코딩된 `SLACK_CHANNEL_ID`는 파일에서 고치고, secret `SLACK_GIT_NOTIFY_CHANNEL_ID`의 값 변경은 사용자에게 요청한다.
  고치지 않은 위치는 이전 채널로 계속 전송되고, `release.yml`은 Slack API가 오류를 응답해도 `::warning`만 남겨 잡이 실패하지 않는다.
  `slack-test.yml`은 하드코딩된 채널만 검사하며, 수동 실행은 사용자에게 요청한다.
- `ci.yml`의 `mi-kas/kover-report` 설정값(`min-coverage-overall`·`min-coverage-changed-files`)은 PR 코멘트의 통과·실패 아이콘만 바꾸고 잡을 실패시키지 않는다.
  커버리지 통과 여부는 `test` 잡의 `koverVerify` 결과로 판단하고, 이 설정값과 `koverVerify` 하한은 사용자에게 확인받고 바꾼다.
- JDK 버전을 바꿀 때는 `build.gradle.kts`의 `java.toolchain`, `Dockerfile`의 두 `FROM` 이미지 태그(빌드·런타임 스테이지), `ci.yml`(`format-check`·`test` 잡)과 `release.yml`(`verify` 잡)의 `java-version`을 모두 바꾼다.
  `java.toolchain`만 올리면 foojay 플러그인이 JDK를 내려받아 CI와 `docker-build-check`는 통과하지만, 런타임 JRE가 낮아 배포한 컨테이너가 `UnsupportedClassVersionError`로 기동에 실패한다.
- 빌드 인자(`RELEASE_VERSION`·`GIT_COMMIT`·`BUILD_TIME`)나 Gradle 속성(`version`·`gitCommit`·`buildTime`) 이름을 바꿀 때는 아래 위치를 모두 바꾼다.
  `release.yml`의 `Build & Push image` 스텝 `build-args`, `Dockerfile`의 `ARG`와 `-P` 옵션, `build.gradle.kts`의 `version`·`springBoot.buildInfo`다.
  `build-args` 이름이 `Dockerfile`의 `ARG`와 다르면 Docker가 그 인자를 무시해 이미지가 오류 없이 `0.0.1-SNAPSHOT`·`unknown`으로 만들어지고, `gitCommit`·`buildTime` 이름이 다르면 `unknown`이 기록된다.
  `Dockerfile`의 `grep`은 `build.version`만 비교하고 PR의 `docker-build-check`는 `build-args` 없이 빌드하므로, 바꾼 뒤에는 검증하지 못한 항목으로 보고한다.
- `release.yml`의 `verify` 잡 `actions/checkout`은 `fetch-depth: 0`을 유지한다. 기본값(1)이면 `origin/develop` ref가 없어 `Check tag commit`의 `git merge-base`가 실패하고, 이미지 빌드와 배포가 실행되지 않는다.
  `v*` 태그 push는 staging 배포와 Slack 알림을 실행하므로 사용자에게 확인받고, develop에 병합된 커밋에만 만든다. 다른 커밋이면 `Check tag commit`이 실패해 배포되지 않는다.
- `release.yml`의 `Deploy via SSH`는 서버의 `~/caro/deploy.sh`에 `build-push`의 `image_tag` 출력을 전달한다. 이 스크립트는 저장소에 없으므로, 전달 값이나 배포 절차를 바꾸려면 서버 변경이 필요하다고 사용자에게 보고한다.
  `image_tag`는 `Compute build args` 스텝의 `short_sha`에 `sha-`를 붙여 `Docker metadata`와 따로 만든 값이라, `type=sha`의 `prefix`나 `format`만 바꾸면 GHCR에 없는 태그가 `deploy.sh`에 전달된다.
- 앱이 쓰는 새 환경변수는 GitHub Secrets나 워크플로우 `env`에 넣지 않는다. `Deploy via SSH`는 서버에 이미지 태그만 전달해 GitHub Secrets 값이 컨테이너에 전달되지 않고, 컨테이너는 `docker/entrypoint.sh`의 `infisical run`으로 값을 읽는다.
- 보드 필드를 바꾸는 스텝은 `Generate GitHub App Token` 스텝의 토큰으로 `update-project-action`을 실행하고, `field`·`value`를 org 보드(whatever-x/26)의 필드명·옵션명과 이모지까지 같은 문자열로 쓴다.
  필드명이 다르면 `Field not found`, 단일 선택 값이 다르면 `Option not found`로 그 스텝이 실패한다.
  PR의 보드 추가와 Done 전환은 저장소 밖 보드의 built-in workflow가 담당하므로, 바꾸려면 보드 설정 변경이 필요하다고 사용자에게 보고한다.
- 새 이슈 템플릿에는 기존 템플릿과 같은 `projects: ["whatever-x/26"]`를 넣고, `type`에는 org에 정의된 Issue type 이름을 이모지까지 같게 쓴다. `projects`가 없으면 그 템플릿으로 만든 이슈가 보드에 자동 추가되지 않을 수 있다.
  템플릿을 확인하려고 이슈를 만드는 것은 사용자에게 확인받는다.
