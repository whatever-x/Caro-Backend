-- 푸시 문구를 기기 언어로 보내기 위해 기기별 Locale(Accept-Language)을 저장한다.
-- 기존 행은 timezone 기본값(Asia/Seoul)과 맞춰 ko로 채운다. 다음 토큰 등록 시 실제 값으로 갱신된다.
ALTER TABLE `device_tokens`
    ADD COLUMN locale VARCHAR(35) NOT NULL DEFAULT 'ko' COMMENT '기기 언어(Locale)' AFTER timezone;
