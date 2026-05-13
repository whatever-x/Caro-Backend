package com.whatever.caro.card.internal.note.exception

import com.whatever.caro.common.exception.BusinessException

class NoteNotFoundException(
    message: String,
) : BusinessException(errorCode = NoteErrorCode.NOT_FOUND, message = message)
