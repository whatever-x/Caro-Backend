package com.whatever.caro.card.internal.library

import com.whatever.caro.card.api.library.LibraryCatalogApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LibraryCatalogApiImpl(
    private val repository: LibraryDeckRepository,
) : LibraryCatalogApi {
    @Transactional(readOnly = true)
    override fun publishedDeckIds(): Set<Long> =
        repository.findByPublishedTrueOrderBySortOrderAscIdAsc().map { it.id }.toSet()
}
