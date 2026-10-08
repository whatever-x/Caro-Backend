package com.whatever.caro.card.internal.library

data class LibraryDeckResponse(
    val libraryDeckId: Long,
    val name: String,
    val description: String,
    val cardCount: Int,
)
data class LibraryCardResponse(
    val libraryCardId: Long,
    val front: String,
    val back: String,
    val position: Int,
)
data class LibraryDetailResponse(
    val libraryDeckId: Long,
    val name: String,
    val description: String,
    val cardCount: Int,
    val cards: List<LibraryCardResponse>,
)
data class LibraryCopyResponse(
    val deckId: Long,
    val name: String,
    val description: String,
    val cardCount: Int,
)
