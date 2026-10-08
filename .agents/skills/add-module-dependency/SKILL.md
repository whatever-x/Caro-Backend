---
name: add-module-dependency
description: 모듈 간 의존(`*Api` 호출, 이벤트 구독)을 새로 만들거나 모듈을 신설할 때의 절차와 완료 조건을 제공한다. `allowedDependencies`·NamedInterface를 고치거나 `ModularityTests`의 경계 위반을 고칠 때도 사용.
---

# 모듈 의존 추가

`*Api`를 호출하거나 이벤트를 구독하는 쪽을 호출하는 모듈이라 한다. 먼저 호출하는 모듈과 상대 모듈의 AGENTS.md를 읽는다.

1. 모듈을 새로 만들면 다음을 먼저 한다.
   - `ModuleMetadata.kt`에 `@PackageInfo`와 `@ApplicationModule(displayName = ...)`을 두고, 같은 디렉터리에 `AGENTS.md`를 만든다. 없으면 루트 AGENTS.md 작업 순서 1번의 Read가 실패한다.
   - `ModularityTests`의 `moduleCount shouldBe` 값을 올린다.
   - `user_id` 데이터가 있으면 `withdrawal/AGENTS.md`의 새 모듈 항목을 따르고, `withdrawal`에서 새 모듈로 가는 의존에도 2~5단계를 적용한다.
2. 의존 방향을 확인한다. 상대 모듈이 쓰는 모듈을 `grep -rhoE '^import com\.whatever\.caro\.[a-z]+' src/main/kotlin/com/whatever/caro/<상대 모듈>/ | sort -u`로 찾는다.
   나온 모듈을 같은 방식으로 따라가 호출하는 모듈이 나오면 순환이고, 그대로 추가하면 `ModularityTests`가 `Cycle detected`로 실패한다.
   순환이면 조회는 `bff`에서 조합하고, 상태 변경은 호출하려던 모듈이 이벤트를 발행해 상대 모듈이 구독하게 한다. 둘 다 맞지 않으면 사용자에게 확인받는다.
3. 상대 모듈에 이미 있는 `*Api` 메서드나 이벤트를 먼저 찾는다. 필요한 타입이 `internal/`에만 있으면 루트 AGENTS.md 규약의 공개 위치에 정의한다.
   공개 위치에는 `*Api` 인터페이스와 그 DTO·이벤트·예외만 두고 구현 클래스·엔티티·리포지토리는 `internal/`에 둔다.
   새 NamedInterface 패키지를 만들면 그 패키지의 `ModuleMetadata.kt`에 `@PackageInfo`와 `@NamedInterface("<이름>")`를 둔다.
   `@PackageInfo`가 없으면 그 클래스 하나만 NamedInterface에 들어가, 그 패키지의 다른 타입을 쓰는 모듈이 `ModularityTests`에서 실패한다.
4. 호출하는 모듈의 `src/main/kotlin/com/whatever/caro/<module>/ModuleMetadata.kt`의 `allowedDependencies`에 추가한다.
   - 모듈 이름만 적으면 그 모듈 루트 패키지의 타입만 허용된다. NamedInterface 패키지의 타입을 쓰면 `<module> :: <이름>`으로 적는다(`card :: deck`, `user :: exception`).
   - `allowedDependencies`를 선언한 모듈은 쓰는 모듈을 `common`까지 모두 적는다. `common`이 `Type.OPEN`이어도 이 목록 검사는 면제되지 않아 빠뜨리면 `ModularityTests`가 실패한다.
   - 선언이 없던 모듈에 처음 선언하면 목록 밖 모듈이 모두 금지되므로, 2단계의 grep을 호출하는 모듈에 실행해 나온 모듈을 함께 적는다.
5. 호출하는 모듈의 빈이 상대 모듈의 `*Api`를 생성자로 받게 되면, 호출하는 모듈의 `@CaroModuleTest` 스펙이 STANDALONE에서 `NoSuchBeanDefinitionException`으로 실패한다.
   `src/test/kotlin/com/whatever/caro/AGENTS.md`의 STANDALONE 항목대로, 그 모듈이 이미 쓰는 방식에 맞춰 스펙 전부를 같은 설정으로 고친다.
6. 호출하는 모듈과 상대 모듈의 AGENTS.md에 이 의존을 나열하는 문장(허용된 NamedInterface, 소비자 목록, `--tests` 대상)이 있으면 같이 고친다.
7. `./gradlew test --tests "com.whatever.caro.ModularityTests" --tests "com.whatever.caro.<호출하는 모듈>.*"`로 검증한다.
   새 모듈이면 `com.whatever.caro.<새 모듈>.*`을, 파기 경로를 붙였으면 `com.whatever.caro.withdrawal.*`을 넣고, 공개 타입을 바꿨으면 상대 모듈 AGENTS.md에 `--tests` 대상이 있는 경우 그것도 넣는다.
   끝나면 `make check`를 통과시킨다.

## 완료 조건
- `ModularityTests`와 7단계 테스트, `make check`를 통과한다
- `allowedDependencies`에 추가한 항목마다 그 모듈이나 NamedInterface를 import하는 코드가 있다. 쓰지 않는 항목은 `ModularityTests`가 잡지 않는다
- 새 모듈이면 1단계 항목을 반영했고, 6단계 대상 문장이 있으면 고쳤다
- PR 설명에 2단계 사용자 확인 결과와, 경계를 어기면 실패할 테스트 이름을 적는다
