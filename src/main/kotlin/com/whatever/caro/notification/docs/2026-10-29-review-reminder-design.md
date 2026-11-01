# 복습 알림 (Review Reminder) 설계 — BE 기준 문서

- 대표 이슈: whatever-x/Caro-Workflow#19
- 단계: Step3 설계 (초안)
- 담당자: Ahn-seokjoo / 개발 완료일: 미정
- 이 문서가 공통 계약의 **기준 문서**다. FE 화면 상세는 Caro-Mobile `feature/setting/docs/2026-10-29-review-reminder-design.md`.

## 참조 버전 (2026-10-29 고정)

| 원본 | 브랜치 | SHA | 최신성 |
|---|---|---|---|
| Caro-Backend | develop | c718c19842ecc77b9dce2d1d150a553d12e61ef2 | 직접 clone해 읽음 (Workflow 미러 5a57b83은 뒤처짐) |
| Caro-Mobile | develop | 02615d330587916ae0e8d7e7199647e5d7353b78 | 직접 clone해 읽음 (미러 e272308은 뒤처짐) |
| Caro-Planning | main | 43fb43c6f3fa9e60e7263bd81fcd8831187a1a20 | Workflow 미러와 동일, 미러로 읽음 |

적용한 Planning 정책:
- `shared/ads-and-notifications.md`: **"1.0에는 알림이 없다. 설정에 알림 항목을 두지 않는다."** 권한 요청 화면은 "🚧 예정". → 복습 알림은 **현재 기획에 없는 신규 정책**이다. 야간 광고성 발송은 개인정보 처리방침상 별도 동의 필요.
- `domains/learning.md`: 하루 경계 0시, 기준 시간대 = 앱이 보내는 기기 시간대. 휴식 = NEW 없고 오늘까지 예정 REVIEW 없음. SM-2 복습 간격.

## 핵심 판단: 신규 구현이 아니라 기존 스트릭 리마인더의 일반화다

origin develop에는 이미 **연속학습(streak) 리마인더**가 동작한다. 복습 알림은 이 뼈대를 그대로 재사용하고 "발송 시각"과 "발송 조건"만 바꾼다.

| 구성요소 | 기존 (StreakReminderScheduler) | 복습 알림 (#19) | 재사용/신규 |
|---|---|---|---|
| 발송 트리거 | `@Scheduled` 매시 정각 cron | 동일 | **재사용** |
| 타임존 | DeviceToken.timezone 버케팅, 현지 시(hour) 매칭 | 동일 | **재사용** |
| 대표 기기 | 유저별 최근 lastSeenAt 기기의 tz/locale | 동일 | **재사용** |
| 발송 시각 | 고정 `REMINDER_HOUR = 20` | **유저가 설정한 시각** | **신규 저장소/API** |
| on/off | 없음(스트릭 위험 시 항상) | **지원** | **신규** |
| 발송 조건 | streakApi.findStreaksAtRisk (끊길 streak) | **복습할 카드 있음** | study Api **신규 노출** (판정 로직 자체는 재사용) |
| 발송 채널 | NotificationService.sendToUser + FCM | 동일 | **재사용** |
| 문구 | MessageSource 다국어 | 동일 | 메시지 키 **신규** |
| 딥링크 | data["type"]="STREAK_REMINDER" | data["type"]="REVIEW_REMINDER" + 오늘의 학습 | **신규 payload** |

> 기존 근거 코드: `notification/internal/StreakReminderScheduler.kt`, `notification/internal/NotificationService.kt`, `notification/internal/DeviceToken.kt`(이미 `timezone`,`locale` 보유), `notification/internal/DeviceTokenRepository.kt`(`findDistinctTimezones`,`findAllByTimezoneIn`,`findAllByUserIdIn`), `study/internal/streak/ExistsBasedRestDayCheckService.kt`, `study/internal/cardlearningstate/CardLearningStateRepository.kt`(`existsNewCardByUser`,`existsTodayReviewCardByUser`,`existsByUserIdAndDeletedAtIsNull`).

## 기능과 핵심 가치

- 무엇: 사용자가 정한 시각에, 오늘 복습할 거리가 있을 때만 푸시를 보내고 탭하면 오늘의 학습으로 보낸다.
- 추가되는 것: (1) 복습 알림 시각·on/off 설정, (2) 조건부(복습할 카드 있음) 발송, (3) 알림 → 오늘의 학습 딥링크.
- 기대 가치: 끊긴 복습 주기 재개. 복습할 카드가 없는 날은 생략해 불필요한 푸시를 줄인다(스트릭 리마인더가 매일 유도하는 것과 구분).

## 데이터 모델 (신규)

신규 엔티티 `ReviewReminderSetting` (notification 모듈), 테이블 `review_reminder_settings`.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK AI | |
| user_id | BIGINT, UNIQUE, NOT NULL | 유저당 1행 (upsert) |
| enabled | BOOLEAN, NOT NULL | 해제 시 false |
| reminder_hour | TINYINT (0~23), NOT NULL | 현지 시각 기준 발송 시(hour). granularity 결정은 아래 D2 |
| created_at / updated_at | (BaseTimeEntity) | |

- 소유자: 유저 본인. 생성·변경 주체: 인증된 유저만. 조회: 본인 것만.
- 발송 시각의 "현지"는 DeviceToken.timezone을 따른다(설정에 tz를 따로 저장하지 않는다 — 기기 tz가 바뀌면 발송도 따라감). D3 참고.
- 마이그레이션: `src/main/resources/db/migration/notification/V<n>__create_review_reminder_settings.sql` (origin AGENTS: `add-db-migration` 스킬 절차를 따른다. 병합된 V 파일은 수정 금지).

## API 계약 (신규 제안, notification 모듈)

origin에 복습 알림 설정 API가 없음을 확인했다(현재 notification/web은 DeviceTokenController, TestPushController뿐). 아래는 신규 제안이며 `add-endpoint` 스킬 절차로 추가한다. 공통 규약: `ApiResponse<T>` 래핑, `API-Version` 헤더(없으면 1.0, 새 매핑에 `version` 명시), 인증 `@AuthenticationPrincipal userId`, 오류는 notification `*ErrorCode` + `BusinessException`.

### GET /notifications/review-reminder (v1.0)
- 응답: `{ enabled: Boolean, reminderHour: Int }`
- 설정이 없으면 기본값 반환(예: enabled=false). 404로 처리하지 않는다.

### PUT /notifications/review-reminder (v1.0)
- 요청: `{ enabled: Boolean, reminderHour: Int(0..23) }`  (reminderHour는 `@Min(0) @Max(23)`)
- 동작: user_id 기준 upsert. 응답 204 또는 갱신된 설정.
- 해제: `enabled=false`로 PUT. (별도 DELETE를 두지 않고 enabled로 단일화 — D1 결정 대상)
- 멱등: 같은 값 재요청해도 결과 동일.

> 참고: 기기 토큰 등록/삭제는 기존 `PUT/DELETE /notifications/devices`를 그대로 쓴다. 복습 알림 설정과 기기 토큰은 별개다(토큰 없으면 발송 생략 — NotificationService.sendToUser가 이미 EMPTY 처리).

## 발송 로직 (신규 `ReviewReminderScheduler`, notification 모듈)

cron: `app.schedule.cron.review-reminder: "0 0 * * * *"` (매시 정각, streak과 동일 형식). application.yaml `app.schedule.cron`에 키 추가.

매 실행 시:
1. `deviceTokenRepository.findDistinctTimezones()`에서 각 tz의 현지 시(hour)를 구한다.
2. enabled=true인 `ReviewReminderSetting` 중, 유저의 **대표 기기(tz)** 기준 현지 시 == `reminderHour`인 유저를 대상 후보로 추린다.
   - 대표 기기 = 유저의 device token 중 최근 lastSeenAt (StreakReminderScheduler.remind와 동일 로직 재사용).
3. 후보를 chunk로 나눠, study에 **"오늘 복습할 카드 있음"**을 배치 질의한다(아래 study Api).
4. 복습할 카드가 있는 유저에게만 `NotificationService.sendToUser(userId, PushMessage(...))` 발송.
   - 문구: `MessageSource`로 locale별. 키 예: `notification.review_reminder.title/body` (신규).
   - data: `{"type":"REVIEW_REMINDER"}` (+ 필요한 라우팅 키). FE가 오늘의 학습(Home)으로 라우팅.
5. 한 유저 발송 실패가 전체를 막지 않도록 try/catch (streak과 동일).

### study 모듈 경계용 신규 Api (제안)
`RestDayCheckService`는 study `internal`이라 다른 모듈에서 직접 못 쓴다(ModularityTests). study의 `*Api`에 배치 메서드를 노출한다:

```kotlin
// study 모듈 public Api (StudyApi 또는 신규 StudyReminderApi)
fun findUserIdsWithStudyToday(
    userIds: Collection<Long>,
    now: Instant,
    timezone: ZoneId,
    dayCutoffHour: Int = 0,
): Set<Long>
```
- 내부 구현은 `existsNewCardByUser` / `existsTodayReviewCardByUser`를 배치화(IN 쿼리)해 N+1을 피한다. 단건 루프(`isRestDay` 유저별 호출)는 후보가 많을 때 병목이 되므로 배치 쿼리를 권장한다.
- "복습할 카드 있음"의 정의는 D1 결정 대상(엄격 REVIEW only vs 오늘 출제 대상 NEW+REVIEW).

## 수용 조건 → 검증

| ID | 조건 | 판정 근거 | 발송 | 검증 시나리오(기대) |
|---|---|---|---|---|
| AC1 | 복습할 카드 있음 + 현지 reminderHour 도달 + enabled | study Api true | 발송 | 고정 Clock으로 해당 hour·tz 세팅, 더미 PushSender(Logging)로 1건 발송 확인 |
| AC2 | 복습할 카드 없음 | study Api false | **생략** | 같은 조건에서 발송 0건 |
| AC3 | enabled=false(해제) | 후보 제외 | 생략 | PUT enabled=false 후 스케줄러 대상에서 빠짐 |
| AC4 | 시간 변경 | reminderHour 갱신 | 새 시각에 발송 | PUT로 hour 변경 → 이전 hour 미발송, 새 hour 발송 |
| AC5 | 기기 토큰 없음 | sendToUser EMPTY | 생략 | 토큰 0개 유저는 발송 결과 EMPTY |
| AC6 | 알림 탭 | data.type=REVIEW_REMINDER | — | FE 딥링크(오늘의 학습) — FE 문서에서 검증 |

검증은 `make check`(Spotless+테스트+Kover). 발송은 `LoggingPushSender`로 단위 검증하고 실제 FCM 발송은 staging에서 확인(개발 완료일·staging 미정이라 계획만 기록). `Clock` 빈 고정으로 시각 제어(`Instant.now()` 직접 호출 금지 — origin AGENTS 규약).

## 원본별 변경과 의존 순서

| 원본/모듈 | 변경 | 설계 경로 | 의존 |
|---|---|---|---|
| Caro-Backend `study` | `findUserIdsWithStudyToday` Api + 배치 쿼리 | 이 문서 | 1 (BE 내 먼저) |
| Caro-Backend `notification` | `ReviewReminderSetting` 엔티티+마이그레이션, 설정 API, `ReviewReminderScheduler`, 메시지 키, cron 설정 | 이 문서(기준) | 2 (study Api 뒤) |
| Caro-Mobile `feature/setting` 외 | 설정 화면(시각·on/off), 권한, 토큰 등록, 알림 수신·딥링크 | `feature/setting/docs/2026-10-29-review-reminder-design.md` | 3 (BE API 계약 뒤) |
| Caro-Planning `shared/ads-and-notifications.md` | "알림 없음" 정책을 복습 알림 도입으로 갱신 + 야간 발송 동의 | Step4 기획 실행 | 병행 |

## 필수 결정 (미확정)

| ID | 질문 | 선택지·영향 | 권장 | 근거/재개 조건 |
|---|---|---|---|---|
| D1 | ~~"복습할 카드"의 정의~~ **결정됨(2026-10-29, 담당자)**: (b) 오늘 출제 대상 NEW+REVIEW(= !휴식, 카드 보유 시) | — | **(b) 확정** | 알림이 "오늘의 학습"으로 연결되고 오늘의 학습은 NEW+REVIEW. → 판정은 study `isRestDay`의 역 = `findUserIdsWithStudyToday` 사용 |
| D2 | 발송 시각 granularity | (a) 시(hour)만, cron 매시 (b) 분까지, cron 매분/15분 | **(a) v1** | 스트릭 리마인더와 동일, 구현·부하 단순. 분 단위는 후속 |
| D3 | 타임존 처리 | 기기 tz(DeviceToken.timezone) 버케팅 재사용 | **기기 tz** | learning.md "기준 시간대=기기 시간대" + 기존 streak 패턴. 설정에 tz 저장 안 함 |
| D4 | ~~야간 발송 동의~~ **결정됨(2026-11-01, 담당자)**: 복습 알림을 **서비스성(비광고성) 알림으로 분류** | — | 야간 가드·별도 동의 **없음** | 유저가 직접 켜고 시각까지 설정한 학습 리마인더 → 영리목적 광고성 아님. 정보통신망법 야간(21~08시) 동의 요건 미적용. 현재 on/off + OS 권한으로 충분. **BE 추가 코드 없음**(현 스케줄러가 설정 시각 그대로 발송). 법무/기획 최종 확인 권장. Planning `ads-and-notifications.md` 갱신은 Step4 기획 실행 |
| D5 | 중복 발송 방지 | last-sent-date 가드 둘지 | 넣는 쪽 권장(스케줄러 재기동/중복 실행 대비) | 운영 안전성 |
| D6 | 딥링크 대상 | (a) Home(오늘 학습할 덱 목록) (b) 특정 덱 LearningEntry | **(a) Home** | 알림은 계정 단위, 오늘의 학습은 여러 덱. FE 문서 D-FE1과 연결 |
| D7 | 기기 tz/locale 획득 경로 확인 | 등록 시 헤더(Client-Timezone/Accept-Language)로 채우는지 origin 컨트롤러 재확인 | — | `RegisterDeviceTokenRequest`는 token+platform뿐이나 엔티티는 tz/locale 필수 → 헤더 경로 추정, 확인 필요 |

## 설계 검토와 전달

- 실행한 것: origin BE/FE clone·코드 정독, 모듈 경계·기존 스케줄러·판정 쿼리 확인. **제품 코드·마이그레이션·테스트는 아직 실행 안 함(Step4).**
- 미실행: 실제 구현, `make check`, staging 발송.
- 작업 위치: Caro-Backend `feat/review-reminder`(로컬, 미push), Caro-Mobile `feat/review-reminder`(로컬, 미push).
- 상태: **설계 정리 완료, 정책 반영 대기.** D1 결정됨(2026-10-29, (b)). 남은 D4(야간 발송 동의)는 Planning 정책 결정으로 Step4 기획 실행에서 반영한다(설계 구조는 바뀌지 않음 — 발송 허용 시간대 가드로 수용). D2/D5/D6/D7은 권장안 확정.
