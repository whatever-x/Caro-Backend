# 무료 영어 텍스트 덱

현재 Backend PR 85의 BASIC 카드에 맞춘 `front`(영어 단어), `back`(한국어 뜻) 콘텐츠다. 음성, 문법 설명과 예문은 포함하지 않는다. 콘텐츠 작성일은 2026-10-14이며 2026-10-15 사용자 결정에 따라 두 덱 모두 무료로 제공한다. 운영 DB에는 등록하지 않았다.

| 파일 | 용도 | 구성 |
|---|---|---|
| `english-basic-100.tsv` | 무료 영어 기초 덱 | 학습과 생각, 일상 행동, 사람과 장소, 시간과 생활, 상태와 성질을 각각 20장 |
| `toeic-work-200.tsv` | 무료 토익 업무 어휘 덱 | 채용, 회의, 사무, 회계, 유통, 계약, 설비, 출장, 숙박, 고객 응대의 10개 주제를 각각 20장 |

TSV가 편집 기준이며 첫 줄은 `category`, `front`, `back`이다. 같은 이름의 JSON에는 덱 이름과 설명, 출처, 라이선스, 원본 순위와 0부터 시작하는 카드 position을 함께 보관했다. JSON은 검토 및 변환용 형식이며 현재 API의 업로드 계약이 아니다. `category`와 `sourceRank`는 편집용 정보로, 앱에 주제별 학습 기능이 구현됐다는 의미는 아니다.

## 선정과 한국어 뜻

영어 기초 100장은 NGSL 1.2의 2,809개 표제어에서 일상 학습에 사용할 단어를 골랐다. 토익 200장은 TSL 1.2의 1,250개 단어에서 업무 및 여행 상황의 단어를 골랐다. 두 덱 모두 원본 순위대로 선택한 상위 목록이 아니며, 수능 빈출 검증이나 토익 전체 범위의 학습 효과를 주장하지 않는다. 각 주제 안의 순서는 편집 순서다.

한국어 뜻은 이번 초안에서 직접 작성했다. 한 카드에 대표 의미를 적되 업무 상황에서 의미를 좁힌 항목이 있다. 예를 들어 `outstanding`은 미지급 대금, `complimentary`는 무료 제공, `suite`는 호텔 객실 의미다. 다의어 전체나 품사별 용례를 담은 사전이 아니므로 출시 전에 학습용 의미와 표기를 최종 검수한다. TOEIC은 시험명 설명을 위해 사용하며 ETS 공식 또는 제휴 자료를 뜻하지 않는다.

## 출처와 이용 조건

- 영어 기초: Charles Browne, Brent Culligan, Joseph Phillips, [New General Service List 1.2](https://www.newgeneralservicelist.com/new-general-service-list), [원본 CSV](https://www.newgeneralservicelist.com/s/NGSL_12_stats.csv).
- 토익 업무 어휘: Charles Browne, Brent Culligan, [TOEIC Service List 1.2](https://www.newgeneralservicelist.com/toeic-service-list), [원본 CSV](https://www.newgeneralservicelist.com/s/TSL_12_stats.csv).
- 두 목록의 이용 조건은 [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)이다. 이번 변경은 일부 단어 선정, 주제별 재배열과 한국어 뜻 작성이다. 이 디렉터리의 두 덱 콘텐츠도 CC BY-SA 4.0으로 제공한다. 재배포 시 원저작자와 출처, 라이선스, 변경 사실을 표시하고 적용되는 동일조건변경허락을 유지한다.
- 단어별 원본 순위와 다운로드한 CSV의 SHA-256은 JSON에 기록했다. 순위는 각 원본 목록 안의 순위이며 두 목록 사이에서 비교하는 지표가 아니다.

## 검증과 다음 단계

100장과 200장, 덱당 200장 이하, 빈 앞면 및 뒷면 없음, 덱 내부 및 덱 사이의 동일 영어 표제어 없음, 원본 목록의 표제어와 순위 일치, 연속 position과 TSV/JSON 내용 일치를 검사했다. 이는 구조 및 출처 검증이며 외부 전문가의 어휘 감수를 대신하지 않는다.

JSON의 `intendedUse`는 `free_content`이며 `published: true`는 등록할 때의 초기 상태를 나타낸다. 파일을 저장하는 것만으로 운영 DB에 게시되지는 않는다. 실제 등록은 부모 디렉터리의 [무료 덱 등록 SQL](../README.md)을 사용한다. SQL은 TSV와 JSON의 단어, 뜻과 position을 동일하게 담고 기존 수도 100장도 함께 등록한다.

현재 라이브러리의 전체 미리보기와 snapshot 복사 흐름으로 세 덱을 무료로 제공한다. 별도 구매와 entitlement 기능을 추가하지 않으며 운영 등록 전 학습용 뜻과 표기를 검수한다. 카드 내용을 편집할 때는 TSV, JSON과 `free-decks.sql`의 해당 행을 함께 맞춘다.
