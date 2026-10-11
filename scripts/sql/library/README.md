# 라이브러리 콘텐츠 등록 SQL

`world-capitals-100.sql`은 국가를 앞면, 수도를 뒷면으로 둔 한국어 카드 100장을 등록하는 검토용 초안이다. 기존 서브채팅의 덱을 복구한 결과가 아니라 사용자가 기억한 주제로 재구성했다.

- 출처: [Wikidata](https://www.wikidata.org/), 2026-10-01 API 조회. 한국어 label과 종료되지 않은 P36 claim을 사용했다. preferred rank를 우선하고 현재 수도가 여러 개로 남은 항목은 제외했다. 선택한 100개는 전체 국가 목록이나 중요도 순위가 아니다.
- 이용 조건: 구조화된 데이터는 [CC0 1.0](https://www.wikidata.org/wiki/Wikidata:Licensing)이다. 이미지, 사전 문장과 타사 학습 덱은 포함하지 않았다. 각 SQL row의 주석에서 국가와 수도 entity를 확인할 수 있다.
- 실행: MySQL 8.4에서 card V4 migration 적용 후 `mysql --default-character-set=utf8mb4 --database=<DB> < scripts/sql/library/world-capitals-100.sql`을 실행한다. 오류 시 중단하도록 `--force`를 사용하지 않는다. Flyway가 자동 실행하는 경로에 넣지 않았다.
- 최초 등록은 **미게시 상태**다. 학습용 표기와 수도를 검수한 뒤 운영 절차에 따라 게시한다. 이 SQL 자체는 배포하거나 기존 개인 덱을 변경하지 않는다.
- 같은 이름과 같은 100장으로 재실행하면 기존 ID와 게시 상태를 유지한다. 동일 이름의 덱이 여러 개거나 카드가 수정돼 있으면 오류로 중단하며 덮어쓰지 않는다. 최초 등록은 named lock, 기존 원본은 row lock으로 직렬화하고 전체 transaction으로 저장한다. 미완성 데이터는 rollback한다.

원본 덱의 이름을 바꾼 뒤 재실행하면 기존 seed를 식별할 수 없으므로 운영자가 기존 ID를 먼저 확인해야 한다. 추가 콘텐츠도 출처와 실제 카드 내용을 확인한 뒤 별도 SQL로 등록한다.
