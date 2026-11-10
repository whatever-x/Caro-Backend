package com.whatever.caro.study.internal.cardlearningstate

import java.time.Instant

interface CardLearningStateBulkRepository {
    /**
     * 입력 순서대로 id가 붙어야 NEW 큐의 출제 순서가 카드 생성 순서와 같다.
     * 트랜잭션 밖에서 부르면 여러 문장으로 나뉜 묶음이 문장마다 autocommit돼 일부만 커밋되므로 호출을 거절한다.
     * JDBC 문장은 Hibernate auto-flush를 일으키지 않으므로 같은 트랜잭션의 JPA 변경이 필요하면 호출 전에 flush해야 한다.
     * 넘긴 엔티티는 값을 옮기는 데만 쓰여 영속성 컨텍스트에 들어가지 않고 id도 0으로 남는다.
     */
    fun insertAll(
        states: List<CardLearningState>,
        now: Instant,
    )
}
