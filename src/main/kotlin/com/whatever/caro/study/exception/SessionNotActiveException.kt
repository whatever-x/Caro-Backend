package com.whatever.caro.study.exception

import com.whatever.caro.common.exception.BusinessException

class SessionNotActiveException : BusinessException(StudyErrorCode.SESSION_NOT_ACTIVE)
