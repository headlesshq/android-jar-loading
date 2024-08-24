package me.earth.headlessmc.android

import me.earth.headlessmc.api.config.HasConfig
import me.earth.headlessmc.launcher.java.Java
import me.earth.headlessmc.launcher.java.JavaService

class Java8Service(cfg: HasConfig): JavaService(cfg) {
    override fun getCurrent(): Java {
        return Java("dummy", 8)
    }

}