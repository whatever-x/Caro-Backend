package com.whatever.caro.study.exception

import com.whatever.caro.common.exception.BusinessException

class SessionNotFoundException : BusinessException(StudyErrorCode.SESSION_NOT_FOUND)
