# user

- 계정 자체에 속한 데이터(소셜 계정, 이메일, 닉네임, status 등)는 이 모듈이 저장하고 바꾼다. 토큰 발급과 권한 스냅샷은 `auth`, 추천 닉네임 단어는 `nickname`, 탈퇴자 데이터의 모듈 간 파기 순서는 `withdrawal`에 있고, `users`·`social_accounts` 행 삭제는 `UserService.hardDeleteUser`에서 한다.
- 이메일은 필드 이름과 달리 평문 그대로 `User.encryptedPrimaryEmail`과 `SocialAccount.encryptedEmail`에만 넣는다. DB에 쓰고 읽을 때 `EmailCryptoConverter`가 암복호화한다.
  이미 암호화한 값을 넣으면 이중 암호화돼 복호화가 깨지고, 읽은 값을 다시 `decrypt`하면 평문을 Base64로 읽지 못해 `EmailDecryptionException`이 난다. 나란히 있는 `social_accounts.email`은 초기 스키마에 남은 평문 컬럼이라 값을 넣으면 암호화를 우회한다.
- 이메일로 행을 찾거나 같은 이메일인지 판정할 때는 `EmailHasher.hash`(trim + lowercase) 값으로 `hashed_primary_email`과 `hashed_email`을 조회한다. `EmailEncryptor`가 저장마다 랜덤 IV를 붙여 암호문 비교는 항상 미스고, 복호화한 값끼리 비교하면 대소문자와 앞뒤 공백으로 갈린다.
  `social_accounts.hashed_email`에는 인덱스가 없으므로, 이 컬럼으로 조회를 추가하면 인덱스 마이그레이션을 같은 변경에 넣는다.
- `EmailHasher`의 정규화·알고리즘·인코딩이나 `EMAIL_ENCRYPTION_KEY`·`EMAIL_BLIND_INDEX_KEY`는 바꾸지 않고, 바꿔야 하면 설계 결정으로 올린다. 기존 행을 새 방식으로 다시 계산해야 하는데 MySQL에는 HMAC·AES-GCM 함수가 없어 SQL 마이그레이션으로는 할 수 없다.
  해시를 바꾸면 같은 이메일이라도 기존 행의 `hashed_*` 값과 달라져 조회되지 않고, 출력 길이는 `CHAR(44)` 컬럼에 묶여 있다.
  암호문 앞 `KEY_VERSION` 바이트는 아직 읽고 버려서, 키를 바꾸면 이메일이 있는 기존 사용자 행을 읽을 때마다 `EmailDecryptionException`이 난다.
- 소셜 플랫폼마다 계정을 구분하므로, 같은 이메일로 여러 계정이 가입되는 것을 허용한다. 해시 컬럼에는 유니크 없이 `idx_users_hashed_primary_email`만 있다.
- `UserApi.findById`와 `findBySocialProvider`를 쓰는 쪽은 `UserInfo.isDeleted`를 직접 확인한다. 두 조회가 soft delete된 사용자도 그대로 돌려줘서, 로그인과 토큰 재발급의 탈퇴자 차단은 `AuthService`의 `isDeleted` 검사 두 곳뿐이다.
- `deleteMe`가 `users.deleted_at`만 채우고 `social_accounts` 행을 남긴다는 전제로 재가입 흐름을 짠다. `uk_provider_user` 때문에 같은 소셜 계정은 `withdrawal` 파기 전까지 기존 탈퇴 유저로만 조회된다.
- 다른 모듈이 쓰지 않는 `GET /users/me/info` 응답 필드는 `MyInfo`·`MyInfoResponse`·`UserService.getUserInfo`에 추가하고 `UserInfo`에는 넣지 않는다. `UserInfo`는 로그인과 토큰 재발급마다 `User.toInfo()`로 만들어 `auth`에 넘기는 값이라, `socialProvider`처럼 `SocialAccount`가 필요한 필드를 넣으면 그 조회가 모든 재발급에 붙는다.
- 닉네임 형식은 `UserService.kt`의 `NicknameValidator.regex`(2~20자, 한글·영문·숫자와 중간 `-`/`_`) 한 곳에서 고친다. 상한을 줄이면 `nickname`의 최장 추천 조합이 20자라 추천받은 닉네임이 `PATCH /users/me/nickname`에서 거부된다.
- 닉네임은 바뀌고(`PATCH /users/me/nickname`) soft delete된 사용자의 것이 재사용될 수 있으므로 사용자 식별에 쓰지 않는다.
  중복 검사는 `isNicknameAvailable`의 `existsByNicknameAndDeletedAtIsNull` 조회로 하고, 비교는 테이블 collation(`utf8mb4_unicode_ci`)을 따라 대소문자를 구분하지 않는다.
- 사용자 status를 바꾸는 경로는 `User.completeRegistration`(`SUSPENDED`→`ACTIVE`) 하나로 유지한다. 전이를 늘리면 권한이 토큰 발급 시점 스냅샷이라 `auth`의 재발급 경로도 같이 고쳐야 한다.
- 닉네임 규칙을 바꾸면 `UserServiceTest`의 `닉네임 regex 검증` 블록을 같이 고치고, 이메일 쪽을 바꾸면 `EmailHasherTest`(정규화·44자 고정)와 `EmailEncryptorTest`(랜덤 IV·변조 탐지)를 먼저 돌린다.
