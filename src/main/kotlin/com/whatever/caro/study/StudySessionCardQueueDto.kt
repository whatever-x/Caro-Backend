package com.whatever.caro.study

data class StudySessionCardQueueDto(
    val newQueue: List<CardLearningStateDto>,
    val reviewQueue: List<CardLearningStateDto>,
)
