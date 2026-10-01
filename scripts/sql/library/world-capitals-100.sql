-- 국가와 수도 100장, 2026-10-01 Wikidata API 조회 결과로 재구성한 초안.
-- 기존 서브채팅 자료를 복구한 결과가 아니다. 검수 전에는 게시하지 않는다.
-- Source: https://www.wikidata.org/ (Korean labels and current P36 statements)
-- License: CC0 1.0, https://www.wikidata.org/wiki/Wikidata:Licensing
-- Requires MySQL 8.4 and card V4 migration. Run with the mysql client without --force.
-- This file is intentionally outside Flyway migration and seed locations.

SET NAMES utf8mb4;
SET @capitals_seed_lock = GET_LOCK('caro.library.world-capitals-100.v1', 30);

CREATE TEMPORARY TABLE capitals_seed_assertion (
    rule VARCHAR(100) NOT NULL,
    valid BOOLEAN NOT NULL CHECK (valid = TRUE)
);
INSERT INTO capitals_seed_assertion VALUES ('seed lock acquired', @capitals_seed_lock = 1);

CREATE TEMPORARY TABLE capitals_seed_cards (
    position INT PRIMARY KEY,
    front VARCHAR(255) NOT NULL,
    back VARCHAR(255) NOT NULL,
    UNIQUE KEY uk_capitals_seed_country (front)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO capitals_seed_cards (position, front, back) VALUES
    (0, '대한민국', '서울특별시') /* Q884 P36 Q8684 */,
    (1, '일본', '도쿄도') /* Q17 P36 Q1490 */,
    (2, '중화인민공화국', '베이징시') /* Q148 P36 Q956 */,
    (3, '미국', '워싱턴 D.C.') /* Q30 P36 Q61 */,
    (4, '캐나다', '오타와') /* Q16 P36 Q1930 */,
    (5, '영국', '런던') /* Q145 P36 Q84 */,
    (6, '독일', '베를린') /* Q183 P36 Q64 */,
    (7, '프랑스', '파리') /* Q142 P36 Q90 */,
    (8, '이탈리아', '로마') /* Q38 P36 Q220 */,
    (9, '스페인', '마드리드') /* Q29 P36 Q2807 */,
    (10, '덴마크', '코펜하겐') /* Q35 P36 Q1748 */,
    (11, '스웨덴', '스톡홀름') /* Q34 P36 Q1754 */,
    (12, '노르웨이', '오슬로') /* Q20 P36 Q585 */,
    (13, '핀란드', '헬싱키') /* Q33 P36 Q1757 */,
    (14, '네덜란드', '암스테르담') /* Q55 P36 Q727 */,
    (15, '벨기에', '브뤼셀') /* Q31 P36 Q239 */,
    (16, '룩셈부르크', '룩셈부르크') /* Q32 P36 Q1842 */,
    (17, '오스트리아', '빈') /* Q40 P36 Q1741 */,
    (18, '헝가리', '부다페스트') /* Q28 P36 Q1781 */,
    (19, '루마니아', '부쿠레슈티') /* Q218 P36 Q19660 */,
    (20, '불가리아', '소피아') /* Q219 P36 Q472 */,
    (21, '그리스', '아테네') /* Q41 P36 Q1524 */,
    (22, '아일랜드', '더블린') /* Q27 P36 Q1761 */,
    (23, '태국', '방콕') /* Q869 P36 Q1861 */,
    (24, '말레이시아', '쿠알라룸푸르') /* Q833 P36 Q1865 */,
    (25, '싱가포르', '싱가포르') /* Q334 P36 Q334 */,
    (26, '브루나이', '반다르스리브가완') /* Q921 P36 Q9279 */,
    (27, '라오스', '비엔티안') /* Q819 P36 Q9326 */,
    (28, '네팔', '카트만두') /* Q837 P36 Q3037 */,
    (29, '부탄', '팀부') /* Q917 P36 Q9270 */,
    (30, '방글라데시', '다카') /* Q902 P36 Q1354 */,
    (31, '세르비아', '베오그라드') /* Q403 P36 Q3711 */,
    (32, '크로아티아', '자그레브') /* Q224 P36 Q1435 */,
    (33, '슬로바키아', '브라티슬라바') /* Q214 P36 Q1780 */,
    (34, '체코', '프라하') /* Q213 P36 Q1085 */,
    (35, '슬로베니아', '류블랴나') /* Q215 P36 Q437 */,
    (36, '북마케도니아', '스코페') /* Q221 P36 Q384 */,
    (37, '몬테네그로', '포드고리차') /* Q236 P36 Q23564 */,
    (38, '카자흐스탄', '아스타나') /* Q232 P36 Q1520 */,
    (39, '우크라이나', '키이우') /* Q212 P36 Q1899 */,
    (40, '조지아', '트빌리시') /* Q230 P36 Q994 */,
    (41, '아제르바이잔', '바쿠') /* Q227 P36 Q9248 */,
    (42, '러시아', '모스크바') /* Q159 P36 Q649 */,
    (43, '아르헨티나', '부에노스아이레스') /* Q414 P36 Q1486 */,
    (44, '브라질', '브라질리아') /* Q155 P36 Q2844 */,
    (45, '칠레', '산티아고') /* Q298 P36 Q2887 */,
    (46, '에콰도르', '키토') /* Q736 P36 Q2900 */,
    (47, '콜롬비아', '보고타') /* Q739 P36 Q2841 */,
    (48, '페루', '리마') /* Q419 P36 Q2868 */,
    (49, '베네수엘라', '카라카스') /* Q717 P36 Q1533 */,
    (50, '파라과이', '아순시온') /* Q733 P36 Q2933 */,
    (51, '멕시코', '멕시코시티') /* Q96 P36 Q1489 */,
    (52, '도미니카 공화국', '산토도밍고') /* Q786 P36 Q34820 */,
    (53, '아이티', '포르토프랭스') /* Q790 P36 Q34261 */,
    (54, '코스타리카', '산호세') /* Q800 P36 Q3070 */,
    (55, '파나마', '파나마시티') /* Q804 P36 Q3306 */,
    (56, '온두라스', '테구시갈파') /* Q783 P36 Q3238 */,
    (57, '엘살바도르', '산살바도르') /* Q792 P36 Q3110 */,
    (58, '니카라과', '마나과') /* Q811 P36 Q3274 */,
    (59, '알제리', '알제') /* Q262 P36 Q3561 */,
    (60, '모로코', '라바트') /* Q1028 P36 Q3551 */,
    (61, '나이지리아', '아부자') /* Q1033 P36 Q3787 */,
    (62, '가나', '아크라') /* Q117 P36 Q3761 */,
    (63, '감비아', '반줄') /* Q1005 P36 Q3726 */,
    (64, '코트디부아르', '야무수크로') /* Q1008 P36 Q3768 */,
    (65, '르완다', '키갈리') /* Q1037 P36 Q3859 */,
    (66, '우간다', '캄팔라') /* Q1036 P36 Q3894 */,
    (67, '탄자니아', '도도마') /* Q924 P36 Q3866 */,
    (68, '케냐', '나이로비') /* Q114 P36 Q3870 */,
    (69, '에티오피아', '아디스아바바') /* Q115 P36 Q3624 */,
    (70, '콩고 민주 공화국', '킨샤사') /* Q974 P36 Q3838 */,
    (71, '나미비아', '빈트후크') /* Q1030 P36 Q3935 */,
    (72, '모잠비크', '마푸투') /* Q1029 P36 Q3889 */,
    (73, '짐바브웨', '하라레') /* Q954 P36 Q3921 */,
    (74, '잠비아', '루사카') /* Q953 P36 Q3881 */,
    (75, '보츠와나', '가보로네') /* Q963 P36 Q3919 */,
    (76, '안도라', '안도라라벨랴') /* Q228 P36 Q1863 */,
    (77, '모나코', '모나코') /* Q235 P36 Q235 */,
    (78, '리히텐슈타인', '파두츠') /* Q347 P36 Q1844 */,
    (79, '키프로스', '니코시아') /* Q229 P36 Q3856 */,
    (80, '카타르', '도하') /* Q846 P36 Q3861 */,
    (81, '아랍에미리트', '아부다비') /* Q878 P36 Q1519 */,
    (82, '사우디아라비아', '리야드') /* Q851 P36 Q3692 */,
    (83, '요르단', '암만') /* Q810 P36 Q3805 */,
    (84, '쿠웨이트', '쿠웨이트 시') /* Q817 P36 Q35178 */,
    (85, '바레인', '마나마') /* Q398 P36 Q3882 */,
    (86, '이란', '테헤란') /* Q794 P36 Q3616 */,
    (87, '튀르키예', '앙카라') /* Q43 P36 Q3640 */,
    (88, '인도', '뉴델리') /* Q668 P36 Q987 */,
    (89, '몽골', '울란바타르') /* Q711 P36 Q23430 */,
    (90, '오스트레일리아', '캔버라') /* Q408 P36 Q3114 */,
    (91, '뉴질랜드', '웰링턴') /* Q664 P36 Q23661 */,
    (92, '피지', '수바') /* Q712 P36 Q38807 */,
    (93, '통가', '누쿠알로파') /* Q678 P36 Q38834 */,
    (94, '바누아투', '포트빌라') /* Q686 P36 Q37806 */,
    (95, '키리바시', '사우스타라와') /* Q710 P36 Q131233 */,
    (96, '파푸아뉴기니', '포트모르즈비') /* Q691 P36 Q36526 */,
    (97, '솔로몬 제도', '호니아라') /* Q685 P36 Q40921 */,
    (98, '투발루', '푸나푸티') /* Q672 P36 Q34126 */,
    (99, '팔라우', '응게룰무드') /* Q695 P36 Q515229 */;

SELECT COUNT(*), MIN(position), MAX(position)
INTO @capitals_seed_input_count, @capitals_seed_input_min, @capitals_seed_input_max
FROM capitals_seed_cards;

START TRANSACTION;
SELECT COUNT(*), MIN(id) INTO @capitals_seed_count, @capitals_seed_id
FROM library_decks WHERE name = '세계 수도 100개';

-- Existing source rows use the same lock as LibraryCopyService.
SELECT id FROM library_decks WHERE id = @capitals_seed_id FOR UPDATE;

SET @capitals_seed_matches = (
    SELECT COUNT(DISTINCT expected.position)
    FROM capitals_seed_cards expected
    JOIN library_cards actual
      ON actual.library_deck_id = @capitals_seed_id
     AND actual.position = expected.position
     AND CAST(actual.front AS BINARY) = CAST(expected.front AS BINARY)
     AND CAST(actual.back AS BINARY) = CAST(expected.back AS BINARY)
);
SET @capitals_seed_valid = (
    @capitals_seed_lock = 1 AND @capitals_seed_count <= 1
    AND @capitals_seed_input_count = 100
    AND @capitals_seed_input_min = 0
    AND @capitals_seed_input_max = 99
    AND (@capitals_seed_count = 0 OR (
        @capitals_seed_matches = 100
        AND (SELECT COUNT(*) FROM library_cards WHERE library_deck_id = @capitals_seed_id) = 100
    ))
);
INSERT INTO capitals_seed_assertion VALUES ('existing deck is absent or matches exactly', @capitals_seed_valid);

INSERT INTO library_decks (name, description, published, sort_order, created_at, updated_at)
SELECT '세계 수도 100개', '국가 이름을 보고 수도를 떠올려 보세요. Wikidata의 국가와 수도 100쌍으로 구성했습니다. (2026-10-01 기준)',
       FALSE, 100, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
WHERE @capitals_seed_valid AND @capitals_seed_count = 0;
SET @capitals_seed_created = (@capitals_seed_valid AND @capitals_seed_count = 0 AND ROW_COUNT() = 1);
SET @capitals_seed_id = IF(@capitals_seed_created, LAST_INSERT_ID(), @capitals_seed_id);

INSERT INTO library_cards (library_deck_id, front, back, position, created_at, updated_at)
SELECT @capitals_seed_id, front, back, position, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM capitals_seed_cards WHERE @capitals_seed_created ORDER BY position;

SET @capitals_seed_final_valid = (
    @capitals_seed_valid
    AND (SELECT COUNT(*) FROM library_cards WHERE library_deck_id = @capitals_seed_id) = 100
    AND (SELECT COUNT(DISTINCT expected.position)
         FROM capitals_seed_cards expected JOIN library_cards actual
           ON actual.library_deck_id = @capitals_seed_id
          AND actual.position = expected.position
          AND CAST(actual.front AS BINARY) = CAST(expected.front AS BINARY)
          AND CAST(actual.back AS BINARY) = CAST(expected.back AS BINARY)) = 100
);
-- Roll back incomplete data even if a SQL editor continues after an earlier error.
SET @capitals_seed_finish = IF(@capitals_seed_final_valid, 'COMMIT', 'ROLLBACK');
PREPARE capitals_seed_finish FROM @capitals_seed_finish;
EXECUTE capitals_seed_finish;
DEALLOCATE PREPARE capitals_seed_finish;

DROP TEMPORARY TABLE capitals_seed_cards;
DROP TEMPORARY TABLE capitals_seed_assertion;
DO RELEASE_LOCK('caro.library.world-capitals-100.v1');

SELECT id, name, published,
       (SELECT COUNT(*) FROM library_cards WHERE library_deck_id = library_decks.id) AS card_count
FROM library_decks WHERE id = @capitals_seed_id;
