---
name: add-module-dependency
description: 새 모듈 간 의존이나 Modulith 모듈을 추가할 때 경계와 검증 기준을 제공한다. API 호출·이벤트 구독으로 의존 경계가 바뀌거나 모듈을 신설할 때 사용.
---

# 모듈 의존 추가

먼저 루트 AGENTS.md의 규약과 양쪽 모듈의 AGENTS.md를 읽는다.

1. 방향을 확인한다. `study` → `card`는 있고 `card` → `study`는 순환이다. 양쪽이 필요하면 `bff`에서 조합한다. `common`은 `Type.OPEN`이라 선언 없이 쓴다.
2. 결합 방식을 고른다. 동기 조회는 그 모듈의 `*Api` 인터페이스, 상태 변화 전파는 Modulith 이벤트(`@ApplicationModuleListener`). 다른 모듈의 데이터는 `*Api`로만 조회한다.
3. 호출하는 쪽 `<module>/ModuleMetadata.kt`의 `allowedDependencies`에 추가한다. `card`는 NamedInterface 단위(`card :: deck`, `card :: event`, `card :: card`)로 적는다.
4. 필요한 기능이 `internal/`에만 있으면 모듈 루트나 `api/<name>/`(card)에 공개 계약을 정의한다. 외부 호출에 필요한 인터페이스·DTO·이벤트만 노출하고 구현은 내부에 둔다.
5. 이벤트 리스너를 추가·변경하면 같은 이벤트를 두 번 받아도 안전하게 만들고 재전달을 테스트한다. 중복 처리 방식은 해당 도메인의 상태와 제약에 맞춰 정한다.
6. 새 모듈을 만드는 경우: `<module>/ModuleMetadata.kt`에 `@ApplicationModule(displayName, allowedDependencies)`, 테이블이 있으면 `db/migration/<module>/V1__init_<module>_module_table.sql`, 사용자 데이터가 있으면 `withdrawal`에 파기 경로 추가, `ModularityTests`의 모듈 수 상수를 올린다.
7. 검증: `./gradlew test --tests "com.whatever.caro.ModularityTests"`.

## 완료 조건
- `ModularityTests` 통과(경계 검증 + 모듈 수)
- `allowedDependencies` 변경이 실제 import와 일치
- 새 모듈이면 모듈 수 검사 갱신. 테이블을 도입하면 DB 마이그레이션, 사용자 데이터를 저장하면 파기 경로 반영
