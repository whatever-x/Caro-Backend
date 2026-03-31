package com.whatever.caro.common.exception

import com.whatever.caro.common.response.ErrorCodeSpec

open class BusinessException(
    val errorCode: ErrorCodeSpec,
    val args: Array<Any>? = null,
    override val message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
