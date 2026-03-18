package com.whatever.caro.study.internal

import org.springframework.data.jpa.repository.JpaRepository

interface StudySessionRepository : JpaRepository<StudySession, Long>
