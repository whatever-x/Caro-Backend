package com.whatever.caro.common

import org.jspecify.annotations.NullMarked
import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.ApplicationModule.Type
import org.springframework.modulith.PackageInfo

@PackageInfo
@NullMarked
@ApplicationModule(displayName = "Common", type = Type.OPEN)
class ModuleMetadata
