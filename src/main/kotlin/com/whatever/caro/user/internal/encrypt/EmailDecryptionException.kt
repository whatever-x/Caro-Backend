package com.whatever.caro.user.internal.encrypt

class EmailDecryptionException(
    message: String,
    throwable: Throwable,
) : RuntimeException(message, throwable)
