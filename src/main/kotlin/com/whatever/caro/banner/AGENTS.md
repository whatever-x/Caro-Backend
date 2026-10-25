# banner

- 홈 운영 이미지의 설정 검증과 조회를 담당한다. DB와 관리자 쓰기 API를 두지 않는다.
- 공개 라이브러리 확인은 `card :: library`의 공개 API로 수행한다. 다른 모듈의 internal 타입을 참조하지 않는다.
- 기간 비교는 주입된 Clock의 UTC Instant를 사용하고 잘못된 설정 묶음은 기동 시 거부한다.
