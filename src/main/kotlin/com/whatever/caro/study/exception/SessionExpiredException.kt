package com.whatever.caro.study.exception

import com.whatever.caro.common.exception.BusinessException

class SessionExpiredException : BusinessException(StudyErrorCode.SESSION_EXPIRED)
