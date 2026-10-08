# card

- 덱 관련 코드는 `internal/deck/service/DeckService.kt`, 카드·노트 관련 코드는 `internal/card/CardService.kt`에서 읽기 시작하고, 새 파일은 그 하위 도메인 안에서 같은 종류(컨트롤러, 서비스, 리포지토리 등)의 기존 파일이 있는 패키지에 만든다.
  note를 만들고 지우는 코드도 `CardService`에 있다.
- `Deck`·`Card`에 컬럼을 더할 때는 `db/migration/card/`에 다음 버전 SQL을 함께 추가한다.
  다른 모듈이 읽어야 하는 `Deck` 값은 `api/deck/DeckInfoResponse`, `DeckResponseInfoMapper.kt`의 `Deck.toInfo()`, `bff`의 `DeckListItem`·`DeckListResponse`와 이를 채우는 `DeckBFFService`(목록·상세 두 곳)·`DeckBFFController.kt`의 `DeckListItem.toResponse()`에 모두 필드를 추가한다. 하나라도 빠지면 덱 목록·상세 응답에 그 필드가 나오지 않는다.
  `Card` 값은 `api/card/CardContentDto`와 이를 만드는 `CardService.getCardsByIds`, 그리고 `getCardContentsByDeck`이 변환하는 `CardResponseDto`를 고친다.
- 다른 모듈이 참조할 타입은 참조하는 모듈이 `allowedDependencies`에 이미 선언한 `api/` 하위 패키지에 넣는다.
- 덱 자체를 읽는 엔드포인트(`GET /decks`, `GET /decks/{deckId}`)는 `bff`의 `DeckBFFController`에 둔다.
  `DeckController`에 같은 path, method, version을 다시 매핑하면 기동 시 `Ambiguous mapping` 예외로 컨텍스트 로딩이 실패한다.
- `card`는 덱·카드·노트·템플릿과 덱 프리셋(`deck_presets`의 일일 한도, 복습 간격·난이도 계수, leech·HARD 배지 임계값)을 소유하고, 학습 상태·일일 목표·streak은 `study`가 소유한다. 카드에 복습 관련 컬럼을 더하면 `cards`와 `study`의 `card_learning_states`가 같은 값을 따로 갱신해 서로 달라질 수 있다.
- 시드 행의 id를 바꿀 때는 그 id를 하드코딩한 `CardType.BASIC`(note type 1)·`DeckPresetEventListener`(preset 1)와 `db/seed/card/R__seed_default_data.sql`을 같이 고친다.
  시드가 없는 DB에서는 카드 생성이 `NoteTypeNotFoundException`(404)으로 실패하고, `DeckPresetEventListener`는 preset 1이 없으면 예외 없이 끝나 새 덱에 프리셋이 연결되지 않는다.
- 프리셋이 없는 덱이 있을 수 있다고 전제하고 코드를 짠다. `DeckPresetEventListener`가 `DeckCreatedEvent`를 커밋 뒤 비동기로 받아 연결하므로 그 전까지 `decks.deck_preset_id`는 NULL이다.
  `DeckRepository.findAllByIdInWithPreset`은 inner join이라 프리셋이 없는 덱을 결과에서 제외하고, `DeckPresetService.getLatestDeckPresetByUser`는 그 덱에 `DeckPresetNotFoundException`(404)을 던진다.
- 카드를 만들거나 지우는 `CardService.createCards`·`deleteCard`는 같은 트랜잭션에서 `DeckRepository.increaseCardCount`/`decreaseCardCount`를 부른다. 탈퇴 purge는 덱까지 hard delete 하므로 예외다.
  `decks.card_count`는 비정규화 값이고 `chk_deck_card_count`(`card_count >= 0`)가 정의돼 있다. 증가를 빠뜨리면 이후 삭제에서 값이 음수가 돼 UPDATE가 CHECK 위반으로 실패하고 삭제 요청이 롤백되며, 감소를 빠뜨리면 카드 수가 실제보다 큰 값으로 유지된다.
- 카드 삭제(`DELETE /cards`, `CardService.deleteCard`)는 삭제 후 참조하는 카드가 없는 note도 함께 soft delete 한다. 한 note를 여러 카드가 참조할 수 있으므로, 같은 note를 참조하는 다른 카드가 남아 있으면 note를 지우지 않는다(`CardServiceUnitTest`).
- `card_templates.required_fields`를 바꿀 때는 그 템플릿을 쓰는 기존 카드와 클라이언트 요청까지 확인한다.
  응답은 `required_fields`에 있는 키만 담고 note에 값이 없는 키는 빈 문자열로 채운다. 생성 요청에 `required_fields`의 키가 없거나 수정 요청에 `required_fields`에 없는 키가 있으면 `CardInvalidFieldsException`이다.
  이 값을 바꾸는 코드는 없고 시드는 기존 행을 갱신하지 않으므로, 기존 DB의 값은 `db/migration/card/`에 새 `V<n>` UPDATE로 바꾸고 새 DB용으로 `R__seed_default_data.sql`의 값도 같이 고친다. versioned가 시드보다 먼저 실행돼 새 DB에서는 UPDATE가 0건에 적용된다.
- 카드 삭제(`DELETE /cards`)는 `Client-Timezone` 헤더를 필수로 받아 덱마다 `CardsDeletedEvent`를 발행하고 `deletedCardIds`와 `clientTimezone`을 채운다.
  `study`의 `CardLearningStateEventListener`는 `deletedCardIds`로 학습 상태를 soft delete 하고, 지운 상태가 있을 때만 `clientTimezone`으로 오늘 세션을 찾아 그 세션이 ACTIVE일 때 목표를 다시 계산한다.
- 덱 삭제는 덱만 soft delete 하고 `DeckDeletedEvent`를 받는 listener가 없으므로, 삭제된 덱의 카드·`decks.card_count`·`study`의 학습 상태가 남아 있다고 전제하고 코드를 짠다.
- 바꾼 뒤 `./gradlew test --tests "com.whatever.caro.card.*" --tests "com.whatever.caro.ModularityTests"`를 먼저 돌린다.
  `api/`의 타입이나 이벤트를 바꿨으면 `com.whatever.caro.bff.*`, `com.whatever.caro.study.*`, `com.whatever.caro.withdrawal.*`를 `--tests` 대상에 추가한다.
  `WithdrawnUserPurgeServiceTest`는 `card.internal`의 엔티티와 리포지토리를 직접 쓰므로, 그 생성자나 리포지토리를 바꿨을 때도 `com.whatever.caro.withdrawal.*`를 추가한다.
