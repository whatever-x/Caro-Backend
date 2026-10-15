-- 무료 제공 덱: 영어 기초 100장, 토익 업무 어휘 200장, 세계 수도 100장.
-- English: NGSL 1.2 / TSL 1.2, CC BY-SA 4.0. See content/README.md for attribution.
-- Capitals: Wikidata, 2026-10-01 Korean labels and current P36 statements, CC0 1.0.
-- Requires MySQL 8.4 and card V4 migration. Run in a fresh mysql batch session without --force.
-- First registration publishes the decks. Replay preserves existing metadata and publication state.
-- Kept outside Flyway so deployment does not publish or overwrite content automatically.

SET NAMES utf8mb4;
SET @library_seed_lock = GET_LOCK('caro.library.free-decks.v1', 30);

CREATE TEMPORARY TABLE library_seed_assertion (
    rule VARCHAR(100) NOT NULL,
    valid BOOLEAN NOT NULL CHECK (valid = TRUE)
);
INSERT INTO library_seed_assertion VALUES ('seed lock acquired', @library_seed_lock = 1);

CREATE TEMPORARY TABLE library_seed_decks (
    seed_key VARCHAR(50) PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL,
    expected_count INT NOT NULL CHECK (expected_count BETWEEN 1 AND 200),
    input_count INT NOT NULL DEFAULT 0,
    input_min INT NULL,
    input_max INT NULL,
    existing_count INT NOT NULL DEFAULT 0,
    target_id BIGINT NULL,
    actual_count INT NOT NULL DEFAULT 0,
    matching_count INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_library_seed_name (name)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TEMPORARY TABLE library_seed_cards (
    seed_key VARCHAR(50) NOT NULL,
    position INT NOT NULL CHECK (position >= 0),
    front VARCHAR(255) NOT NULL CHECK (CHAR_LENGTH(TRIM(front)) > 0),
    back VARCHAR(255) NOT NULL CHECK (CHAR_LENGTH(TRIM(back)) > 0),
    target_id BIGINT NULL,
    PRIMARY KEY (seed_key, position),
    UNIQUE KEY uk_library_seed_front (seed_key, front)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO library_seed_decks (seed_key, name, description, sort_order, expected_count) VALUES
    ('english-basic-100', '영어 기초 어휘 100', '학습과 일상에 쓰는 영어 기초 어휘 100장. NGSL 1.2에서 선정하고 한국어 뜻을 작성했습니다. Charles Browne, Brent Culligan, Joseph Phillips. 출처: https://www.newgeneralservicelist.com/new-general-service-list | CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/', 10, 100),
    ('toeic-work-200', '토익 업무 어휘 200', '업무와 여행 상황의 토익 어휘 200장. TSL 1.2에서 선정하고 한국어 뜻을 작성했습니다. Charles Browne, Brent Culligan. 출처: https://www.newgeneralservicelist.com/toeic-service-list | CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/', 20, 200),
    ('world-capitals-100', '세계 수도 100개', '국가 이름을 보고 수도를 떠올려 보세요. Wikidata의 국가와 수도 100쌍으로 구성했습니다. (2026-10-01 기준) 출처: https://www.wikidata.org/ | CC0 1.0: https://www.wikidata.org/wiki/Wikidata:Licensing', 100, 100);

-- 영어 기초 어휘 100
INSERT INTO library_seed_cards (seed_key, position, front, back) VALUES
    ('english-basic-100', 0, 'study', '공부하다'), /* source rank 187 */
    ('english-basic-100', 1, 'learn', '배우다'), /* source rank 261 */
    ('english-basic-100', 2, 'read', '읽다'), /* source rank 207 */
    ('english-basic-100', 3, 'write', '쓰다'), /* source rank 121 */
    ('english-basic-100', 4, 'speak', '말하다'), /* source rank 284 */
    ('english-basic-100', 5, 'listen', '귀 기울여 듣다'), /* source rank 573 */
    ('english-basic-100', 6, 'understand', '이해하다'), /* source rank 243 */
    ('english-basic-100', 7, 'remember', '기억하다'), /* source rank 351 */
    ('english-basic-100', 8, 'forget', '잊다'), /* source rank 651 */
    ('english-basic-100', 9, 'think', '생각하다'), /* source rank 47 */
    ('english-basic-100', 10, 'know', '알다'), /* source rank 41 */
    ('english-basic-100', 11, 'ask', '묻다'), /* source rank 134 */
    ('english-basic-100', 12, 'answer', '대답하다'), /* source rank 355 */
    ('english-basic-100', 13, 'explain', '설명하다'), /* source rank 489 */
    ('english-basic-100', 14, 'teach', '가르치다'), /* source rank 539 */
    ('english-basic-100', 15, 'practice', '연습하다'), /* source rank 484 */
    ('english-basic-100', 16, 'improve', '개선하다, 향상시키다'), /* source rank 508 */
    ('english-basic-100', 17, 'decide', '결정하다'), /* source rank 359 */
    ('english-basic-100', 18, 'choose', '선택하다'), /* source rank 447 */
    ('english-basic-100', 19, 'try', '시도하다'), /* source rank 125 */
    ('english-basic-100', 20, 'start', '시작하다'), /* source rank 136 */
    ('english-basic-100', 21, 'finish', '끝내다'), /* source rank 609 */
    ('english-basic-100', 22, 'open', '열다'), /* source rank 214 */
    ('english-basic-100', 23, 'close', '닫다'), /* source rank 239 */
    ('english-basic-100', 24, 'make', '만들다'), /* source rank 48 */
    ('english-basic-100', 25, 'use', '사용하다'), /* source rank 68 */
    ('english-basic-100', 26, 'need', '필요로 하다'), /* source rank 90 */
    ('english-basic-100', 27, 'want', '원하다'), /* source rank 76 */
    ('english-basic-100', 28, 'find', '찾다'), /* source rank 81 */
    ('english-basic-100', 29, 'look', '보다'), /* source rank 75 */
    ('english-basic-100', 30, 'watch', '지켜보다'), /* source rank 326 */
    ('english-basic-100', 31, 'see', '눈으로 보다'), /* source rank 50 */
    ('english-basic-100', 32, 'hear', '소리를 듣다'), /* source rank 178 */
    ('english-basic-100', 33, 'give', '주다'), /* source rank 77 */
    ('english-basic-100', 34, 'take', '가져가다'), /* source rank 59 */
    ('english-basic-100', 35, 'bring', '가져오다'), /* source rank 196 */
    ('english-basic-100', 36, 'buy', '사다'), /* source rank 242 */
    ('english-basic-100', 37, 'pay', '지불하다'), /* source rank 220 */
    ('english-basic-100', 38, 'help', '돕다'), /* source rank 133 */
    ('english-basic-100', 39, 'wait', '기다리다'), /* source rank 403 */
    ('english-basic-100', 40, 'person', '사람'), /* source rank 263 */
    ('english-basic-100', 41, 'friend', '친구'), /* source rank 208 */
    ('english-basic-100', 42, 'family', '가족'), /* source rank 172 */
    ('english-basic-100', 43, 'child', '아이'), /* source rank 102 */
    ('english-basic-100', 44, 'parent', '부모'), /* source rank 297 */
    ('english-basic-100', 45, 'teacher', '교사'), /* source rank 496 */
    ('english-basic-100', 46, 'student', '학생'), /* source rank 248 */
    ('english-basic-100', 47, 'neighbor', '이웃'), /* source rank 1222 */
    ('english-basic-100', 48, 'home', '집'), /* source rank 161 */
    ('english-basic-100', 49, 'school', '학교'), /* source rank 146 */
    ('english-basic-100', 50, 'office', '사무실'), /* source rank 343 */
    ('english-basic-100', 51, 'store', '상점'), /* source rank 617 */
    ('english-basic-100', 52, 'street', '거리'), /* source rank 598 */
    ('english-basic-100', 53, 'road', '도로'), /* source rank 521 */
    ('english-basic-100', 54, 'city', '도시'), /* source rank 269 */
    ('english-basic-100', 55, 'country', '나라'), /* source rank 143 */
    ('english-basic-100', 56, 'room', '방'), /* source rank 254 */
    ('english-basic-100', 57, 'door', '문'), /* source rank 490 */
    ('english-basic-100', 58, 'window', '창문'), /* source rank 734 */
    ('english-basic-100', 59, 'table', '탁자'), /* source rank 450 */
    ('english-basic-100', 60, 'day', '하루'), /* source rank 85 */
    ('english-basic-100', 61, 'week', '주'), /* source rank 157 */
    ('english-basic-100', 62, 'month', '달, 월'), /* source rank 209 */
    ('english-basic-100', 63, 'year', '해, 년'), /* source rank 58 */
    ('english-basic-100', 64, 'morning', '아침'), /* source rank 434 */
    ('english-basic-100', 65, 'afternoon', '오후'), /* source rank 948 */
    ('english-basic-100', 66, 'evening', '저녁'), /* source rank 679 */
    ('english-basic-100', 67, 'night', '밤'), /* source rank 270 */
    ('english-basic-100', 68, 'today', '오늘'), /* source rank 246 */
    ('english-basic-100', 69, 'tomorrow', '내일'), /* source rank 726 */
    ('english-basic-100', 70, 'yesterday', '어제'), /* source rank 703 */
    ('english-basic-100', 71, 'time', '시간'), /* source rank 49 */
    ('english-basic-100', 72, 'minute', '분'), /* source rank 366 */
    ('english-basic-100', 73, 'hour', '한 시간'), /* source rank 247 */
    ('english-basic-100', 74, 'food', '음식'), /* source rank 367 */
    ('english-basic-100', 75, 'water', '물'), /* source rank 322 */
    ('english-basic-100', 76, 'money', '돈'), /* source rank 201 */
    ('english-basic-100', 77, 'work', '일'), /* source rank 67 */
    ('english-basic-100', 78, 'health', '건강'), /* source rank 475 */
    ('english-basic-100', 79, 'travel', '여행'), /* source rank 503 */
    ('english-basic-100', 80, 'good', '좋은'), /* source rank 56 */
    ('english-basic-100', 81, 'bad', '나쁜'), /* source rank 268 */
    ('english-basic-100', 82, 'easy', '쉬운'), /* source rank 408 */
    ('english-basic-100', 83, 'difficult', '어려운'), /* source rank 470 */
    ('english-basic-100', 84, 'important', '중요한'), /* source rank 190 */
    ('english-basic-100', 85, 'necessary', '필요한'), /* source rank 676 */
    ('english-basic-100', 86, 'possible', '가능한'), /* source rank 290 */
    ('english-basic-100', 87, 'different', '다른'), /* source rank 149 */
    ('english-basic-100', 88, 'same', '같은'), /* source rank 122 */
    ('english-basic-100', 89, 'new', '새로운'), /* source rank 79 */
    ('english-basic-100', 90, 'old', '오래된'), /* source rank 144 */
    ('english-basic-100', 91, 'young', '어린'), /* source rank 238 */
    ('english-basic-100', 92, 'early', '이른'), /* source rank 197 */
    ('english-basic-100', 93, 'late', '늦은'), /* source rank 147 */
    ('english-basic-100', 94, 'ready', '준비된'), /* source rank 774 */
    ('english-basic-100', 95, 'busy', '바쁜'), /* source rank 1233 */
    ('english-basic-100', 96, 'happy', '행복한'), /* source rank 513 */
    ('english-basic-100', 97, 'careful', '주의 깊은'), /* source rank 1637 */
    ('english-basic-100', 98, 'small', '작은'), /* source rank 186 */
    ('english-basic-100', 99, 'large', '큰'); /* source rank 210 */

-- 토익 업무 어휘 200
INSERT INTO library_seed_cards (seed_key, position, front, back) VALUES
    ('toeic-work-200', 0, 'applicant', '지원자'), /* source rank 28 */
    ('toeic-work-200', 1, 'résumé', '이력서'), /* source rank 26 */
    ('toeic-work-200', 2, 'occupation', '직업'), /* source rank 64 */
    ('toeic-work-200', 3, 'supervisor', '관리자, 상사'), /* source rank 13 */
    ('toeic-work-200', 4, 'coworker', '직장 동료'), /* source rank 85 */
    ('toeic-work-200', 5, 'recruiter', '채용 담당자'), /* source rank 533 */
    ('toeic-work-200', 6, 'recruitment', '채용'), /* source rank 645 */
    ('toeic-work-200', 7, 'intern', '인턴'), /* source rank 307 */
    ('toeic-work-200', 8, 'internship', '인턴 근무'), /* source rank 345 */
    ('toeic-work-200', 9, 'trainee', '훈련생'), /* source rank 237 */
    ('toeic-work-200', 10, 'trainer', '훈련 담당자'), /* source rank 228 */
    ('toeic-work-200', 11, 'workforce', '전체 직원, 노동 인력'), /* source rank 561 */
    ('toeic-work-200', 12, 'payroll', '급여 지급 명부'), /* source rank 581 */
    ('toeic-work-200', 13, 'paycheck', '급여, 급여 수표'), /* source rank 320 */
    ('toeic-work-200', 14, 'bonus', '상여금'), /* source rank 195 */
    ('toeic-work-200', 15, 'overtime', '초과 근무'), /* source rank 164 */
    ('toeic-work-200', 16, 'resignation', '사직'), /* source rank 473 */
    ('toeic-work-200', 17, 'relocate', '이전하다'), /* source rank 236 */
    ('toeic-work-200', 18, 'layoff', '정리 해고'), /* source rank 1060 */
    ('toeic-work-200', 19, 'expertise', '전문 지식'), /* source rank 311 */
    ('toeic-work-200', 20, 'workshop', '실무 연수, 워크숍'), /* source rank 16 */
    ('toeic-work-200', 21, 'seminar', '세미나'), /* source rank 29 */
    ('toeic-work-200', 22, 'attendee', '참석자'), /* source rank 102 */
    ('toeic-work-200', 23, 'venue', '행사 장소'), /* source rank 175 */
    ('toeic-work-200', 24, 'banquet', '연회'), /* source rank 117 */
    ('toeic-work-200', 25, 'boardroom', '이사회 회의실'), /* source rank 437 */
    ('toeic-work-200', 26, 'presenter', '발표자'), /* source rank 250 */
    ('toeic-work-200', 27, 'organizer', '주최자'), /* source rank 347 */
    ('toeic-work-200', 28, 'coordinator', '조정 담당자'), /* source rank 460 */
    ('toeic-work-200', 29, 'delegate', '대표'), /* source rank 274 */
    ('toeic-work-200', 30, 'assignment', '업무, 과제'), /* source rank 100 */
    ('toeic-work-200', 31, 'submission', '제출'), /* source rank 157 */
    ('toeic-work-200', 32, 'feedback', '의견, 피드백'), /* source rank 128 */
    ('toeic-work-200', 33, 'confirmation', '확인'), /* source rank 309 */
    ('toeic-work-200', 34, 'newsletter', '소식지'), /* source rank 178 */
    ('toeic-work-200', 35, 'bulletin', '공지문'), /* source rank 158 */
    ('toeic-work-200', 36, 'upcoming', '다가오는'), /* source rank 86 */
    ('toeic-work-200', 37, 'postpone', '연기하다'), /* source rank 65 */
    ('toeic-work-200', 38, 'reschedule', '일정을 변경하다'), /* source rank 77 */
    ('toeic-work-200', 39, 'finalize', '최종 확정하다'), /* source rank 390 */
    ('toeic-work-200', 40, 'memo', '메모'), /* source rank 6 */
    ('toeic-work-200', 41, 'memorandum', '업무 연락문'), /* source rank 259 */
    ('toeic-work-200', 42, 'paperwork', '서류 작업'), /* source rank 222 */
    ('toeic-work-200', 43, 'correspondence', '업무 서신'), /* source rank 284 */
    ('toeic-work-200', 44, 'enclose', '동봉하다'), /* source rank 27 */
    ('toeic-work-200', 45, 'notify', '통지하다'), /* source rank 50 */
    ('toeic-work-200', 46, 'signature', '서명'), /* source rank 252 */
    ('toeic-work-200', 47, 'stationery', '문구류'), /* source rank 205 */
    ('toeic-work-200', 48, 'folder', '서류철'), /* source rank 122 */
    ('toeic-work-200', 49, 'cabinet', '수납장'), /* source rank 87 */
    ('toeic-work-200', 50, 'photocopy', '복사본'), /* source rank 123 */
    ('toeic-work-200', 51, 'copier', '복사기'), /* source rank 132 */
    ('toeic-work-200', 52, 'cartridge', '프린터용 잉크 또는 토너 용기'), /* source rank 180 */
    ('toeic-work-200', 53, 'toner', '레이저 프린터용 분말 잉크'), /* source rank 798 */
    ('toeic-work-200', 54, 'stapler', '스테이플러'), /* source rank 792 */
    ('toeic-work-200', 55, 'spreadsheet', '전자 계산표'), /* source rank 791 */
    ('toeic-work-200', 56, 'confidential', '기밀의'), /* source rank 381 */
    ('toeic-work-200', 57, 'authorize', '권한을 부여하다, 승인하다'), /* source rank 213 */
    ('toeic-work-200', 58, 'proofread', '교정하다'), /* source rank 532 */
    ('toeic-work-200', 59, 'documentation', '문서 자료'), /* source rank 675 */
    ('toeic-work-200', 60, 'invoice', '청구서'), /* source rank 19 */
    ('toeic-work-200', 61, 'receipt', '영수증'), /* source rank 33 */
    ('toeic-work-200', 62, 'refund', '환불'), /* source rank 14 */
    ('toeic-work-200', 63, 'transaction', '거래'), /* source rank 238 */
    ('toeic-work-200', 64, 'accountant', '회계사'), /* source rank 22 */
    ('toeic-work-200', 65, 'audit', '회계 감사'), /* source rank 187 */
    ('toeic-work-200', 66, 'auditor', '감사 담당자'), /* source rank 1030 */
    ('toeic-work-200', 67, 'deduction', '공제'), /* source rank 363 */
    ('toeic-work-200', 68, 'deduct', '공제하다'), /* source rank 518 */
    ('toeic-work-200', 69, 'reimburse', '비용을 상환하다'), /* source rank 375 */
    ('toeic-work-200', 70, 'reimbursement', '비용 상환'), /* source rank 941 */
    ('toeic-work-200', 71, 'overdue', '지불 기한이 지난'), /* source rank 348 */
    ('toeic-work-200', 72, 'outstanding', '미지급된(대금)'), /* source rank 167 */
    ('toeic-work-200', 73, 'payable', '지불해야 하는'), /* source rank 686 */
    ('toeic-work-200', 74, 'debit', '차변, 계좌 인출액'), /* source rank 901 */
    ('toeic-work-200', 75, 'dividend', '배당금'), /* source rank 416 */
    ('toeic-work-200', 76, 'banker', '은행 업무 종사자'), /* source rank 316 */
    ('toeic-work-200', 77, 'broker', '중개인'), /* source rank 379 */
    ('toeic-work-200', 78, 'voucher', '상품권, 교환권'), /* source rank 315 */
    ('toeic-work-200', 79, 'fiscal', '회계의, 재정의'), /* source rank 980 */
    ('toeic-work-200', 80, 'client', '고객'), /* source rank 3 */
    ('toeic-work-200', 81, 'merchandise', '상품'), /* source rank 71 */
    ('toeic-work-200', 82, 'goods', '상품, 물품'), /* source rank 15 */
    ('toeic-work-200', 83, 'inventory', '재고'), /* source rank 60 */
    ('toeic-work-200', 84, 'warehouse', '창고'), /* source rank 131 */
    ('toeic-work-200', 85, 'shipment', '배송 화물'), /* source rank 38 */
    ('toeic-work-200', 86, 'freight', '화물'), /* source rank 716 */
    ('toeic-work-200', 87, 'cargo', '화물'), /* source rank 413 */
    ('toeic-work-200', 88, 'courier', '배송 기사, 택배 업체'), /* source rank 242 */
    ('toeic-work-200', 89, 'carrier', '운송 업체'), /* source rank 254 */
    ('toeic-work-200', 90, 'vendor', '판매 업체'), /* source rank 454 */
    ('toeic-work-200', 91, 'retailer', '소매업자'), /* source rank 338 */
    ('toeic-work-200', 92, 'distributor', '유통업체'), /* source rank 815 */
    ('toeic-work-200', 93, 'provider', '서비스 제공업체'), /* source rank 133 */
    ('toeic-work-200', 94, 'purchaser', '구매자'), /* source rank 485 */
    ('toeic-work-200', 95, 'recipient', '수령인'), /* source rank 137 */
    ('toeic-work-200', 96, 'container', '화물 용기, 컨테이너'), /* source rank 154 */
    ('toeic-work-200', 97, 'parcel', '소포'), /* source rank 729 */
    ('toeic-work-200', 98, 'consignment', '탁송 화물'), /* source rank 1113 */
    ('toeic-work-200', 99, 'shortage', '부족'), /* source rank 398 */
    ('toeic-work-200', 100, 'lease', '임대차 계약'), /* source rank 48 */
    ('toeic-work-200', 101, 'tenant', '임차인'), /* source rank 111 */
    ('toeic-work-200', 102, 'landlord', '임대인'), /* source rank 578 */
    ('toeic-work-200', 103, 'warranty', '보증'), /* source rank 42 */
    ('toeic-work-200', 104, 'renew', '갱신하다'), /* source rank 110 */
    ('toeic-work-200', 105, 'renewal', '갱신'), /* source rank 295 */
    ('toeic-work-200', 106, 'expire', '만료되다'), /* source rank 121 */
    ('toeic-work-200', 107, 'expiration', '만료'), /* source rank 546 */
    ('toeic-work-200', 108, 'valid', '유효한'), /* source rank 94 */
    ('toeic-work-200', 109, 'eligible', '자격이 있는'), /* source rank 150 */
    ('toeic-work-200', 110, 'mandatory', '의무적인'), /* source rank 421 */
    ('toeic-work-200', 111, 'optional', '선택 가능한'), /* source rank 319 */
    ('toeic-work-200', 112, 'comply', '준수하다'), /* source rank 595 */
    ('toeic-work-200', 113, 'compliance', '준수'), /* source rank 1038 */
    ('toeic-work-200', 114, 'prohibit', '금지하다'), /* source rank 191 */
    ('toeic-work-200', 115, 'violation', '위반'), /* source rank 696 */
    ('toeic-work-200', 116, 'terminate', '종료하다'), /* source rank 475 */
    ('toeic-work-200', 117, 'termination', '종료'), /* source rank 949 */
    ('toeic-work-200', 118, 'authorization', '승인'), /* source rank 661 */
    ('toeic-work-200', 119, 'consent', '동의'), /* source rank 669 */
    ('toeic-work-200', 120, 'renovation', '보수 공사, 새 단장'), /* source rank 79 */
    ('toeic-work-200', 121, 'renovate', '보수하다'), /* source rank 124 */
    ('toeic-work-200', 122, 'installation', '설치'), /* source rank 227 */
    ('toeic-work-200', 123, 'machinery', '기계류'), /* source rank 249 */
    ('toeic-work-200', 124, 'appliance', '가전제품'), /* source rank 88 */
    ('toeic-work-200', 125, 'mechanic', '정비사'), /* source rank 140 */
    ('toeic-work-200', 126, 'technician', '기술자'), /* source rank 30 */
    ('toeic-work-200', 127, 'malfunction', '오작동'), /* source rank 445 */
    ('toeic-work-200', 128, 'defect', '결함'), /* source rank 278 */
    ('toeic-work-200', 129, 'defective', '결함이 있는'), /* source rank 290 */
    ('toeic-work-200', 130, 'replacement', '교체품'), /* source rank 82 */
    ('toeic-work-200', 131, 'inspection', '검사'), /* source rank 136 */
    ('toeic-work-200', 132, 'inspect', '검사하다'), /* source rank 139 */
    ('toeic-work-200', 133, 'contractor', '도급업체'), /* source rank 188 */
    ('toeic-work-200', 134, 'architect', '건축가'), /* source rank 148 */
    ('toeic-work-200', 135, 'electrician', '전기 기사'), /* source rank 520 */
    ('toeic-work-200', 136, 'plumber', '배관공'), /* source rank 359 */
    ('toeic-work-200', 137, 'outage', '전기 등의 공급 중단'), /* source rank 1156 */
    ('toeic-work-200', 138, 'generator', '발전기'), /* source rank 914 */
    ('toeic-work-200', 139, 'durable', '내구성이 있는'), /* source rank 442 */
    ('toeic-work-200', 140, 'reservation', '예약'), /* source rank 7 */
    ('toeic-work-200', 141, 'itinerary', '여행 일정'), /* source rank 146 */
    ('toeic-work-200', 142, 'destination', '목적지'), /* source rank 63 */
    ('toeic-work-200', 143, 'departure', '출발'), /* source rank 113 */
    ('toeic-work-200', 144, 'depart', '출발하다'), /* source rank 76 */
    ('toeic-work-200', 145, 'airport', '공항'), /* source rank 5 */
    ('toeic-work-200', 146, 'airfare', '항공 요금'), /* source rank 493 */
    ('toeic-work-200', 147, 'fare', '운임'), /* source rank 32 */
    ('toeic-work-200', 148, 'passport', '여권'), /* source rank 84 */
    ('toeic-work-200', 149, 'visa', '비자'), /* source rank 559 */
    ('toeic-work-200', 150, 'luggage', '수하물'), /* source rank 37 */
    ('toeic-work-200', 151, 'baggage', '수하물'), /* source rank 152 */
    ('toeic-work-200', 152, 'suitcase', '여행 가방'), /* source rank 62 */
    ('toeic-work-200', 153, 'terminal', '터미널'), /* source rank 193 */
    ('toeic-work-200', 154, 'shuttle', '셔틀버스'), /* source rank 204 */
    ('toeic-work-200', 155, 'commuter', '통근자'), /* source rank 176 */
    ('toeic-work-200', 156, 'commute', '통근하다'), /* source rank 220 */
    ('toeic-work-200', 157, 'transit', '운송, 통과'), /* source rank 327 */
    ('toeic-work-200', 158, 'overnight', '하룻밤 동안'), /* source rank 174 */
    ('toeic-work-200', 159, 'aboard', '탑승하여'), /* source rank 655 */
    ('toeic-work-200', 160, 'lobby', '로비'), /* source rank 24 */
    ('toeic-work-200', 161, 'receptionist', '접수 담당자'), /* source rank 72 */
    ('toeic-work-200', 162, 'reception', '접수처'), /* source rank 46 */
    ('toeic-work-200', 163, 'suite', '여러 방이 딸린 호텔 객실'), /* source rank 261 */
    ('toeic-work-200', 164, 'occupancy', '객실 사용, 점유'), /* source rank 293 */
    ('toeic-work-200', 165, 'vacancy', '빈방'), /* source rank 339 */
    ('toeic-work-200', 166, 'housekeeper', '객실 관리 직원'), /* source rank 344 */
    ('toeic-work-200', 167, 'concierge', '호텔 안내 직원'), /* source rank 668 */
    ('toeic-work-200', 168, 'complimentary', '무료로 제공되는'), /* source rank 231 */
    ('toeic-work-200', 169, 'amenity', '편의시설'), /* source rank 845 */
    ('toeic-work-200', 170, 'cafeteria', '구내식당'), /* source rank 43 */
    ('toeic-work-200', 171, 'beverage', '음료'), /* source rank 134 */
    ('toeic-work-200', 172, 'refreshment', '간단한 음식과 음료'), /* source rank 211 */
    ('toeic-work-200', 173, 'ingredient', '재료'), /* source rank 144 */
    ('toeic-work-200', 174, 'recipe', '조리법'), /* source rank 210 */
    ('toeic-work-200', 175, 'chef', '요리사'), /* source rank 44 */
    ('toeic-work-200', 176, 'waiter', '웨이터'), /* source rank 40 */
    ('toeic-work-200', 177, 'waitress', '웨이트리스'), /* source rank 200 */
    ('toeic-work-200', 178, 'appetizer', '전채 요리'), /* source rank 1100 */
    ('toeic-work-200', 179, 'dessert', '후식'), /* source rank 170 */
    ('toeic-work-200', 180, 'deadline', '마감 기한'), /* source rank 18 */
    ('toeic-work-200', 181, 'inquire', '문의하다'), /* source rank 173 */
    ('toeic-work-200', 182, 'inconvenience', '불편'), /* source rank 116 */
    ('toeic-work-200', 183, 'apology', '사과'), /* source rank 434 */
    ('toeic-work-200', 184, 'availability', '이용 가능 여부'), /* source rank 229 */
    ('toeic-work-200', 185, 'subscription', '구독'), /* source rank 53 */
    ('toeic-work-200', 186, 'subscribe', '구독하다'), /* source rank 206 */
    ('toeic-work-200', 187, 'subscriber', '구독자'), /* source rank 350 */
    ('toeic-work-200', 188, 'cancellation', '취소'), /* source rank 380 */
    ('toeic-work-200', 189, 'verify', '확인하다, 검증하다'), /* source rank 240 */
    ('toeic-work-200', 190, 'accurately', '정확하게'), /* source rank 303 */
    ('toeic-work-200', 191, 'promptly', '신속히, 즉시'), /* source rank 337 */
    ('toeic-work-200', 192, 'affordable', '가격이 부담되지 않는'), /* source rank 218 */
    ('toeic-work-200', 193, 'convenient', '편리한'), /* source rank 54 */
    ('toeic-work-200', 194, 'accommodate', '요구를 들어주다, 수용하다'), /* source rank 217 */
    ('toeic-work-200', 195, 'coordinate', '조율하다'), /* source rank 221 */
    ('toeic-work-200', 196, 'productivity', '생산성'), /* source rank 234 */
    ('toeic-work-200', 197, 'efficiently', '효율적으로'), /* source rank 366 */
    ('toeic-work-200', 198, 'streamline', '간소화하다'), /* source rank 793 */
    ('toeic-work-200', 199, 'prospective', '장래의, 잠재적인'); /* source rank 449 */

-- 세계 수도 100개
INSERT INTO library_seed_cards (seed_key, position, front, back) VALUES
    ('world-capitals-100', 0, '대한민국', '서울특별시'), /* Q884 P36 Q8684 */
    ('world-capitals-100', 1, '일본', '도쿄도'), /* Q17 P36 Q1490 */
    ('world-capitals-100', 2, '중화인민공화국', '베이징시'), /* Q148 P36 Q956 */
    ('world-capitals-100', 3, '미국', '워싱턴 D.C.'), /* Q30 P36 Q61 */
    ('world-capitals-100', 4, '캐나다', '오타와'), /* Q16 P36 Q1930 */
    ('world-capitals-100', 5, '영국', '런던'), /* Q145 P36 Q84 */
    ('world-capitals-100', 6, '독일', '베를린'), /* Q183 P36 Q64 */
    ('world-capitals-100', 7, '프랑스', '파리'), /* Q142 P36 Q90 */
    ('world-capitals-100', 8, '이탈리아', '로마'), /* Q38 P36 Q220 */
    ('world-capitals-100', 9, '스페인', '마드리드'), /* Q29 P36 Q2807 */
    ('world-capitals-100', 10, '덴마크', '코펜하겐'), /* Q35 P36 Q1748 */
    ('world-capitals-100', 11, '스웨덴', '스톡홀름'), /* Q34 P36 Q1754 */
    ('world-capitals-100', 12, '노르웨이', '오슬로'), /* Q20 P36 Q585 */
    ('world-capitals-100', 13, '핀란드', '헬싱키'), /* Q33 P36 Q1757 */
    ('world-capitals-100', 14, '네덜란드', '암스테르담'), /* Q55 P36 Q727 */
    ('world-capitals-100', 15, '벨기에', '브뤼셀'), /* Q31 P36 Q239 */
    ('world-capitals-100', 16, '룩셈부르크', '룩셈부르크'), /* Q32 P36 Q1842 */
    ('world-capitals-100', 17, '오스트리아', '빈'), /* Q40 P36 Q1741 */
    ('world-capitals-100', 18, '헝가리', '부다페스트'), /* Q28 P36 Q1781 */
    ('world-capitals-100', 19, '루마니아', '부쿠레슈티'), /* Q218 P36 Q19660 */
    ('world-capitals-100', 20, '불가리아', '소피아'), /* Q219 P36 Q472 */
    ('world-capitals-100', 21, '그리스', '아테네'), /* Q41 P36 Q1524 */
    ('world-capitals-100', 22, '아일랜드', '더블린'), /* Q27 P36 Q1761 */
    ('world-capitals-100', 23, '태국', '방콕'), /* Q869 P36 Q1861 */
    ('world-capitals-100', 24, '말레이시아', '쿠알라룸푸르'), /* Q833 P36 Q1865 */
    ('world-capitals-100', 25, '싱가포르', '싱가포르'), /* Q334 P36 Q334 */
    ('world-capitals-100', 26, '브루나이', '반다르스리브가완'), /* Q921 P36 Q9279 */
    ('world-capitals-100', 27, '라오스', '비엔티안'), /* Q819 P36 Q9326 */
    ('world-capitals-100', 28, '네팔', '카트만두'), /* Q837 P36 Q3037 */
    ('world-capitals-100', 29, '부탄', '팀부'), /* Q917 P36 Q9270 */
    ('world-capitals-100', 30, '방글라데시', '다카'), /* Q902 P36 Q1354 */
    ('world-capitals-100', 31, '세르비아', '베오그라드'), /* Q403 P36 Q3711 */
    ('world-capitals-100', 32, '크로아티아', '자그레브'), /* Q224 P36 Q1435 */
    ('world-capitals-100', 33, '슬로바키아', '브라티슬라바'), /* Q214 P36 Q1780 */
    ('world-capitals-100', 34, '체코', '프라하'), /* Q213 P36 Q1085 */
    ('world-capitals-100', 35, '슬로베니아', '류블랴나'), /* Q215 P36 Q437 */
    ('world-capitals-100', 36, '북마케도니아', '스코페'), /* Q221 P36 Q384 */
    ('world-capitals-100', 37, '몬테네그로', '포드고리차'), /* Q236 P36 Q23564 */
    ('world-capitals-100', 38, '카자흐스탄', '아스타나'), /* Q232 P36 Q1520 */
    ('world-capitals-100', 39, '우크라이나', '키이우'), /* Q212 P36 Q1899 */
    ('world-capitals-100', 40, '조지아', '트빌리시'), /* Q230 P36 Q994 */
    ('world-capitals-100', 41, '아제르바이잔', '바쿠'), /* Q227 P36 Q9248 */
    ('world-capitals-100', 42, '러시아', '모스크바'), /* Q159 P36 Q649 */
    ('world-capitals-100', 43, '아르헨티나', '부에노스아이레스'), /* Q414 P36 Q1486 */
    ('world-capitals-100', 44, '브라질', '브라질리아'), /* Q155 P36 Q2844 */
    ('world-capitals-100', 45, '칠레', '산티아고'), /* Q298 P36 Q2887 */
    ('world-capitals-100', 46, '에콰도르', '키토'), /* Q736 P36 Q2900 */
    ('world-capitals-100', 47, '콜롬비아', '보고타'), /* Q739 P36 Q2841 */
    ('world-capitals-100', 48, '페루', '리마'), /* Q419 P36 Q2868 */
    ('world-capitals-100', 49, '베네수엘라', '카라카스'), /* Q717 P36 Q1533 */
    ('world-capitals-100', 50, '파라과이', '아순시온'), /* Q733 P36 Q2933 */
    ('world-capitals-100', 51, '멕시코', '멕시코시티'), /* Q96 P36 Q1489 */
    ('world-capitals-100', 52, '도미니카 공화국', '산토도밍고'), /* Q786 P36 Q34820 */
    ('world-capitals-100', 53, '아이티', '포르토프랭스'), /* Q790 P36 Q34261 */
    ('world-capitals-100', 54, '코스타리카', '산호세'), /* Q800 P36 Q3070 */
    ('world-capitals-100', 55, '파나마', '파나마시티'), /* Q804 P36 Q3306 */
    ('world-capitals-100', 56, '온두라스', '테구시갈파'), /* Q783 P36 Q3238 */
    ('world-capitals-100', 57, '엘살바도르', '산살바도르'), /* Q792 P36 Q3110 */
    ('world-capitals-100', 58, '니카라과', '마나과'), /* Q811 P36 Q3274 */
    ('world-capitals-100', 59, '알제리', '알제'), /* Q262 P36 Q3561 */
    ('world-capitals-100', 60, '모로코', '라바트'), /* Q1028 P36 Q3551 */
    ('world-capitals-100', 61, '나이지리아', '아부자'), /* Q1033 P36 Q3787 */
    ('world-capitals-100', 62, '가나', '아크라'), /* Q117 P36 Q3761 */
    ('world-capitals-100', 63, '감비아', '반줄'), /* Q1005 P36 Q3726 */
    ('world-capitals-100', 64, '코트디부아르', '야무수크로'), /* Q1008 P36 Q3768 */
    ('world-capitals-100', 65, '르완다', '키갈리'), /* Q1037 P36 Q3859 */
    ('world-capitals-100', 66, '우간다', '캄팔라'), /* Q1036 P36 Q3894 */
    ('world-capitals-100', 67, '탄자니아', '도도마'), /* Q924 P36 Q3866 */
    ('world-capitals-100', 68, '케냐', '나이로비'), /* Q114 P36 Q3870 */
    ('world-capitals-100', 69, '에티오피아', '아디스아바바'), /* Q115 P36 Q3624 */
    ('world-capitals-100', 70, '콩고 민주 공화국', '킨샤사'), /* Q974 P36 Q3838 */
    ('world-capitals-100', 71, '나미비아', '빈트후크'), /* Q1030 P36 Q3935 */
    ('world-capitals-100', 72, '모잠비크', '마푸투'), /* Q1029 P36 Q3889 */
    ('world-capitals-100', 73, '짐바브웨', '하라레'), /* Q954 P36 Q3921 */
    ('world-capitals-100', 74, '잠비아', '루사카'), /* Q953 P36 Q3881 */
    ('world-capitals-100', 75, '보츠와나', '가보로네'), /* Q963 P36 Q3919 */
    ('world-capitals-100', 76, '안도라', '안도라라벨랴'), /* Q228 P36 Q1863 */
    ('world-capitals-100', 77, '모나코', '모나코'), /* Q235 P36 Q235 */
    ('world-capitals-100', 78, '리히텐슈타인', '파두츠'), /* Q347 P36 Q1844 */
    ('world-capitals-100', 79, '키프로스', '니코시아'), /* Q229 P36 Q3856 */
    ('world-capitals-100', 80, '카타르', '도하'), /* Q846 P36 Q3861 */
    ('world-capitals-100', 81, '아랍에미리트', '아부다비'), /* Q878 P36 Q1519 */
    ('world-capitals-100', 82, '사우디아라비아', '리야드'), /* Q851 P36 Q3692 */
    ('world-capitals-100', 83, '요르단', '암만'), /* Q810 P36 Q3805 */
    ('world-capitals-100', 84, '쿠웨이트', '쿠웨이트 시'), /* Q817 P36 Q35178 */
    ('world-capitals-100', 85, '바레인', '마나마'), /* Q398 P36 Q3882 */
    ('world-capitals-100', 86, '이란', '테헤란'), /* Q794 P36 Q3616 */
    ('world-capitals-100', 87, '튀르키예', '앙카라'), /* Q43 P36 Q3640 */
    ('world-capitals-100', 88, '인도', '뉴델리'), /* Q668 P36 Q987 */
    ('world-capitals-100', 89, '몽골', '울란바타르'), /* Q711 P36 Q23430 */
    ('world-capitals-100', 90, '오스트레일리아', '캔버라'), /* Q408 P36 Q3114 */
    ('world-capitals-100', 91, '뉴질랜드', '웰링턴'), /* Q664 P36 Q23661 */
    ('world-capitals-100', 92, '피지', '수바'), /* Q712 P36 Q38807 */
    ('world-capitals-100', 93, '통가', '누쿠알로파'), /* Q678 P36 Q38834 */
    ('world-capitals-100', 94, '바누아투', '포트빌라'), /* Q686 P36 Q37806 */
    ('world-capitals-100', 95, '키리바시', '사우스타라와'), /* Q710 P36 Q131233 */
    ('world-capitals-100', 96, '파푸아뉴기니', '포트모르즈비'), /* Q691 P36 Q36526 */
    ('world-capitals-100', 97, '솔로몬 제도', '호니아라'), /* Q685 P36 Q40921 */
    ('world-capitals-100', 98, '투발루', '푸나푸티'), /* Q672 P36 Q34126 */
    ('world-capitals-100', 99, '팔라우', '응게룰무드'); /* Q695 P36 Q515229 */

UPDATE library_seed_decks seed
JOIN (
    SELECT seed_key, COUNT(*) AS card_count, MIN(position) AS min_position, MAX(position) AS max_position
    FROM library_seed_cards
    GROUP BY seed_key
) input ON input.seed_key = seed.seed_key
SET seed.input_count = input.card_count, seed.input_min = input.min_position, seed.input_max = input.max_position;

SELECT COUNT(*) INTO @library_seed_input_count FROM library_seed_cards;

-- Read the operator's latest committed content, then use the same source row lock as LibraryService.copy.
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT id FROM library_decks
WHERE name IN (SELECT name FROM library_seed_decks)
ORDER BY id FOR UPDATE;

UPDATE library_seed_decks seed
SET seed.existing_count = (SELECT COUNT(*) FROM library_decks WHERE name = seed.name),
    seed.target_id = (SELECT MIN(id) FROM library_decks WHERE name = seed.name);

UPDATE library_seed_cards card
JOIN library_seed_decks deck ON deck.seed_key = card.seed_key
SET card.target_id = deck.target_id;

UPDATE library_seed_decks seed
SET seed.actual_count = (SELECT COUNT(*) FROM library_cards WHERE library_deck_id = seed.target_id);

UPDATE library_seed_decks seed
LEFT JOIN (
    SELECT expected.seed_key, COUNT(DISTINCT expected.position) AS matching_count
    FROM library_seed_cards expected
    JOIN library_cards actual
      ON actual.library_deck_id = expected.target_id
     AND actual.position = expected.position
     AND CAST(actual.front AS BINARY) = CAST(expected.front AS BINARY)
     AND CAST(actual.back AS BINARY) = CAST(expected.back AS BINARY)
    GROUP BY expected.seed_key
) matched ON matched.seed_key = seed.seed_key
SET seed.matching_count = COALESCE(matched.matching_count, 0);

SELECT @library_seed_lock = 1 AND COUNT(*) = 3 AND SUM(input_count) = @library_seed_input_count AND MIN(
    input_count = expected_count AND input_min = 0 AND input_max = expected_count - 1
    AND existing_count <= 1
    AND (existing_count = 0 OR (actual_count = expected_count AND matching_count = expected_count))
) INTO @library_seed_valid
FROM library_seed_decks;
INSERT INTO library_seed_assertion VALUES ('existing decks are absent or match exactly', @library_seed_valid);

INSERT INTO library_decks (name, description, published, sort_order, created_at, updated_at)
SELECT name, description, TRUE, sort_order, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM library_seed_decks
WHERE @library_seed_valid AND existing_count = 0
ORDER BY sort_order;

UPDATE library_seed_decks seed
SET seed.target_id = (SELECT MIN(id) FROM library_decks WHERE name = seed.name);

INSERT INTO library_cards (library_deck_id, front, back, position, created_at, updated_at)
SELECT deck.target_id, card.front, card.back, card.position, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM library_seed_cards card
JOIN library_seed_decks deck ON deck.seed_key = card.seed_key
WHERE @library_seed_valid AND deck.existing_count = 0
ORDER BY deck.sort_order, card.position;

UPDATE library_seed_cards card
JOIN library_seed_decks deck ON deck.seed_key = card.seed_key
SET card.target_id = deck.target_id;

UPDATE library_seed_decks seed
SET seed.actual_count = (SELECT COUNT(*) FROM library_cards WHERE library_deck_id = seed.target_id);

UPDATE library_seed_decks seed
LEFT JOIN (
    SELECT expected.seed_key, COUNT(DISTINCT expected.position) AS matching_count
    FROM library_seed_cards expected
    JOIN library_cards actual
      ON actual.library_deck_id = expected.target_id
     AND actual.position = expected.position
     AND CAST(actual.front AS BINARY) = CAST(expected.front AS BINARY)
     AND CAST(actual.back AS BINARY) = CAST(expected.back AS BINARY)
    GROUP BY expected.seed_key
) matched ON matched.seed_key = seed.seed_key
SET seed.matching_count = COALESCE(matched.matching_count, 0);

UPDATE library_seed_decks seed
SET seed.existing_count = (SELECT COUNT(*) FROM library_decks WHERE name = seed.name);

SELECT @library_seed_valid AND COUNT(*) = 3 AND MIN(
    existing_count = 1 AND actual_count = expected_count AND matching_count = expected_count
) INTO @library_seed_final_valid
FROM library_seed_decks;
INSERT INTO library_seed_assertion VALUES ('all decks and cards registered exactly', @library_seed_final_valid);

-- SQL editors may continue after an error. Incomplete or conflicting data must never commit.
SET @library_seed_finish = IF(@library_seed_final_valid, 'COMMIT', 'ROLLBACK');
PREPARE library_seed_finish FROM @library_seed_finish;
EXECUTE library_seed_finish;
DEALLOCATE PREPARE library_seed_finish;

SELECT actual.id, actual.name, actual.published, seed.actual_count AS card_count
FROM library_seed_decks seed
JOIN library_decks actual ON actual.id = seed.target_id
WHERE @library_seed_final_valid
ORDER BY actual.sort_order, actual.id;

DROP TEMPORARY TABLE library_seed_cards;
DROP TEMPORARY TABLE library_seed_decks;
DROP TEMPORARY TABLE library_seed_assertion;
DO RELEASE_LOCK('caro.library.free-decks.v1');
