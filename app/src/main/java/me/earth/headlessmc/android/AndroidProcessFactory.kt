package me.earth.headlessmc.android

import me.earth.headlessmc.api.config.HasConfig
import me.earth.headlessmc.launcher.download.DownloadService
import me.earth.headlessmc.launcher.files.FileManager
import me.earth.headlessmc.launcher.java.Java
import me.earth.headlessmc.launcher.launch.InMemoryLauncher
import me.earth.headlessmc.launcher.launch.JavaLaunchCommandBuilder
import me.earth.headlessmc.launcher.launch.LaunchOptions
import me.earth.headlessmc.launcher.launch.ProcessFactory
import me.earth.headlessmc.launcher.os.OS
import me.earth.headlessmc.launcher.version.Version
import me.earth.headlessmc.logging.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class AndroidProcessFactory(
    downloadService: DownloadService,
    files: FileManager,
    config: HasConfig,
    os: OS
) : ProcessFactory(downloadService, files, config, os) {
    override fun inMemoryLaunch(iml: InMemoryLauncher) {
        val androidLauncher = AndroidInMemoryLauncher(iml.options, iml.command, iml.version, iml.java)
        androidLauncher.launch()
    }

    class AndroidInMemoryLauncher(
        options: LaunchOptions,
        command: JavaLaunchCommandBuilder,
        version: Version,
        java: Java
    ) : InMemoryLauncher(options, command, version, java) {
        private val nativeLibraryPaths = ArrayList<String>()

        init {
            setClassLoaderFactory { classpathUrls ->
                val nativeLibs: String? = if (nativeLibraryPaths.isEmpty()) {
                    null
                } else {
                    nativeLibraryPaths.joinToString(File.pathSeparator)
                }

                ResourceDexClassloader.create(classpathUrls, nativeLibs)
            }
        }

        override fun addLibraryPath(libraryPath: Path) {
            val logger = LoggerFactory.getLogger("AndroidInMemoryLauncher")
            logger.info("Adding library path $libraryPath")
            nativeLibraryPaths.add(libraryPath.toAbsolutePath().toString())
            Files.list(libraryPath).use {
                it.forEach { lib -> logger.info("Library: $lib") }
            }

            super.addLibraryPath(libraryPath)
        }
    }

}