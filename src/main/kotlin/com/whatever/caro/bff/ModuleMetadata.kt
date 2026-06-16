package com.whatever.caro.bff

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    displayName = "BackendForFrontend",
    allowedDependencies = ["common", "study", "card :: card", "card :: deck", "auth"],
)
class ModuleMetadata
