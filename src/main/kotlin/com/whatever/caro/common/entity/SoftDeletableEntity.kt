package com.whatever.caro.common.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.LocalDateTime

@MappedSuperclass
abstract class SoftDeletableEntity : BaseTimeEntity() {
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    fun softDelete() {
        this.deletedAt = LocalDateTime.now()
    }

    val isDeleted: Boolean
        get() = deletedAt != null
}
