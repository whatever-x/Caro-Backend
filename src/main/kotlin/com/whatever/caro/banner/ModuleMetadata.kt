package com.whatever.caro.banner

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(displayName = "Banner", allowedDependencies = ["common", "card :: library"])
class ModuleMetadata
