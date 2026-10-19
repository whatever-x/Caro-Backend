-- 스트릭 리마인더를 유저 현지 시각에 보내기 위해 기기별 타임존(ZoneId)을 저장한다.
-- 기존 행은 서비스 주 사용 지역인 Asia/Seoul로 채운다. 다음 토큰 등록 시 실제 값으로 갱신된다.
ALTER TABLE `device_tokens`
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul' COMMENT '기기 타임존(ZoneId)' AFTER platform,
    ADD KEY idx_device_tokens_timezone (timezone);
