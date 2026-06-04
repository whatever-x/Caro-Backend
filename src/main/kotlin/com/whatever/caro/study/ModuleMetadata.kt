package com.whatever.caro.study

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    displayName = "Study",
    allowedDependencies = ["card :: deck", "card :: event", "common", "auth"],
)
class ModuleMetadata
