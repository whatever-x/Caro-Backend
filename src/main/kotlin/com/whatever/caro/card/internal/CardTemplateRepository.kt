package com.whatever.caro.card.internal

import org.springframework.data.jpa.repository.JpaRepository

interface CardTemplateRepository : JpaRepository<CardTemplate, Long>
