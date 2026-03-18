package com.whatever.caro.study.internal.studysession

import org.springframework.data.jpa.repository.JpaRepository

interface StudySessionRepository : JpaRepository<StudySession, Long>
