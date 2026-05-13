package com.whatever.caro.card.internal.note.exception

import com.whatever.caro.common.exception.BusinessException

class NoteInvalidFieldsException(
    message: String,
) : BusinessException(errorCode = NoteErrorCode.INVALID_FIELDS, message = message)
