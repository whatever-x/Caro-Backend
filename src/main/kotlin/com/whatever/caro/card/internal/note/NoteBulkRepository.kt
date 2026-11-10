package com.whatever.caro.card.internal.note

import java.time.Instant

interface NoteBulkRepository {
    /**
     * 반환 키는 입력 순서와 같고, 드라이버가 LAST_INSERT_ID와 auto_increment_increment로 계산한 값이다.
     * notes에 bulk insert나 mixed-mode insert가 함께 실행되거나 연결 뒤 세션 증가값이 바뀌면 이 키가 실제 id와 어긋나고 런타임 검사가 이를 잡지 못한다.
     * 트랜잭션 밖에서 부르면 여러 문장으로 나뉜 묶음이 문장마다 autocommit돼 일부만 커밋되므로 호출을 거절한다.
     * JDBC 문장은 Hibernate auto-flush를 일으키지 않으므로 같은 트랜잭션의 JPA 변경이 필요하면 호출 전에 flush해야 한다.
     */
    fun insertAll(
        rows: List<NoteInsertRow>,
        now: Instant,
    ): List<Long>
}

data class NoteInsertRow(
    val userId: Long,
    val fields: Map<String, String>,
)
