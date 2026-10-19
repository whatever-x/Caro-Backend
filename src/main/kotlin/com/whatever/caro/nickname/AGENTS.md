# nickname

- 이 모듈은 DB를 보지 않아 이미 쓰이는 닉네임도 그대로 추천한다. 중복과 형식 판정은 `user`의 `isNicknameAvailable`에 맡기고, 클라이언트가 `/users/nicknames/{nickname}/availability`로 확인한 뒤 가입 확정이나 `PATCH /users/me/nickname`으로 저장한다.
- 단어 파일은 `src/main/resources/nickname/<locale>/<category>.txt`에 UTF-8로 한 줄에 단어 하나만 담는다. `WordDictionary`가 공백 줄만 걸러서 주석처럼 적은 줄도 단어로 뽑히고, 사전 파일이 모두 끝 개행 없이 끝나 `>>`로 덧붙이면 마지막 단어와 한 단어로 붙는다.
- 영어 파일은 `[A-Za-z]`만, 한국어 파일은 완성형 `[가-힣]`만 담고 구분자는 `_`나 `-`만 쓴다. `NicknameApiTest`가 생성 결과를 이 두 정규식으로 단정하고 `user`의 `NicknameValidator`가 공백을 포함한 나머지 문자를 거른다.
- 영어 단어는 형용사 9자, 명사 계열 10자를 넘지 않게 고른다. 형용사 + `_` + 명사의 최대 길이 20자가 `NicknameValidator`의 상한과 같아 여유가 없으므로, 더 긴 단어를 넣으면 그 단어가 뽑힌 추천 닉네임이 가입 확정과 `PATCH /users/me/nickname`에서 거부된다.
- 카테고리 파일은 지원 로케일 디렉터리 전부에 같은 이름으로 둔다. 한쪽에만 있으면 그 로케일 요청이 `WordDictionary.randomWord`의 `Unknown category`로 500이 된다.
- 카테고리를 추가하면 `NicknameBuilder`에 메서드를 더하고, 구현체(`NicknameBuilderImpl`)가 쓰는 카테고리 이름은 사전 파일명(확장자 제외)과 같게 한다. 명사 계열이면 `NicknameService.kt`의 `NOUN_CATEGORIES`에도 넣는다.
  이름이 어긋나면 컴파일은 통과하고 `WordDictionary.randomWord`가 `Unknown category`로 500을 낸다.
  파일만 추가하면 어느 경로에서도 그 카테고리를 고르지 못하고, `NOUN_CATEGORIES`에서 빠지면 `anyNoun()`과 `randomName()`이 그 카테고리를 뽑지 않는다.
- 카테고리 개수를 바꾸면 `WordDictionaryTest`의 `categories.size` 단언과 `expectedCategories`, `NicknameApiTest`의 모든 카테고리를 체이닝하는 테스트를 같이 고친다. 세 곳 모두 카테고리 구성이 하드코딩돼 있다.
- 로케일을 추가하려면 `NicknameLocales.SUPPORTED`에 `Locale`을 넣고 모든 카테고리 파일을 같은 커밋에 만든다.
  파일 없이 `SUPPORTED`만 늘리면 `loadAllLocales`가 그 로케일을 건너뛰고 `resolveLocaleKey`가 영어로 폴백해, 그 로케일 요청에 오류 없이 영어 닉네임을 응답한다. `Accept-Language` 값을 그대로 넘기는 `common/config/LocaleConfig.kt`는 고칠 필요가 없다.
- 한글과 영문 밖 문자를 쓰는 로케일은 `user`의 `NicknameValidator` 정규식을 먼저 넓힌 뒤에 추가한다. 정규식이 허용하지 않는 문자(일본어 가나, 한자 등)로 만든 닉네임은 생성만 되고 확정 단계에서 전부 거부된다.
- `/nicknames` 아래 엔드포인트를 더할 때는 규칙을 새로 넣는 대신 `SecurityConfigAccessControlTest`의 `API_POLICY`에 경로를 추가한다. `SecurityConfig`의 `/nicknames/**` 매처가 이미 `SUSPENDED`와 `ACTIVE`를 덮는다.
- 고친 뒤 `./gradlew test --tests "com.whatever.caro.nickname.*"`를 돌린다.
  사전을 건드렸으면 `src/main/resources/nickname`에서 `awk 'length>9' en/adjectives.txt`, `awk 'length>10' en/*.txt`, `grep -vE '^[A-Za-z]+$' en/*.txt`, `grep -vE '^[가-힣]+$' ko/*.txt`가 모두 빈 출력인지 확인한다.
  테스트는 무작위로 뽑은 단어만 검사해서 사전에 섞인 잘못된 한 줄을 놓친다.
