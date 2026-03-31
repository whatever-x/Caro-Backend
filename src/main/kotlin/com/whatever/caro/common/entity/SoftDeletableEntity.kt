package com.whatever.caro.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.Instant

@MappedSuperclass
abstract class SoftDeletableEntity : BaseTimeEntity() {
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
        protected set

    fun softDelete(deletedAt: Instant) {
        this.deletedAt = deletedAt
    }

    val isDeleted: Boolean
        get() = deletedAt != null
}
