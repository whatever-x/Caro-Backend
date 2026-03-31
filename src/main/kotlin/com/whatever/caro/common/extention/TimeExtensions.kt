package com.whatever.caro.common.extention

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

fun Instant.toKst(): ZonedDateTime = this.atZone(ZoneId.of("Asia/Seoul"))

fun Instant.daysUntil(other: Instant): Long = ChronoUnit.DAYS.between(this, other)
