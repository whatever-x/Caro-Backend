package com.whatever.caro.bff.internal.web

import com.whatever.caro.study.StudyApi
import org.springframework.web.bind.annotation.RestController

@RestController("/v1/decks")
class DeckBFFController(
    private val studyApi: StudyApi,
) {
    // TODO card 모듈 merge 후 추가구현
}
