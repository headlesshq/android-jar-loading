package me.earth.headlessmc.android

import android.content.Context
import android.util.Log
import me.earth.headlessmc.api.HeadlessMcImpl
import me.earth.headlessmc.api.command.CopyContext
import me.earth.headlessmc.api.command.line.BufferedCommandLineReader
import me.earth.headlessmc.api.command.line.CommandLine
import me.earth.headlessmc.api.config.ConfigImpl
import me.earth.headlessmc.api.config.HmcProperties
import me.earth.headlessmc.api.exit.ExitManager
import me.earth.headlessmc.launcher.Launcher
import me.earth.headlessmc.launcher.LauncherProperties
import me.earth.headlessmc.launcher.auth.AccountManager
import me.earth.headlessmc.launcher.auth.AccountStore
import me.earth.headlessmc.launcher.auth.AccountValidator
import me.earth.headlessmc.launcher.auth.OfflineChecker
import me.earth.headlessmc.launcher.command.FabricCommand
import me.earth.headlessmc.launcher.command.LaunchContext
import me.earth.headlessmc.launcher.command.forge.ForgeCommand
import me.earth.headlessmc.launcher.download.ChecksumService
import me.earth.headlessmc.launcher.download.DownloadService
import me.earth.headlessmc.launcher.files.ConfigService
import me.earth.headlessmc.launcher.files.FileManager
import me.earth.headlessmc.launcher.files.MinecraftFinder
import me.earth.headlessmc.launcher.os.OSFactory
import me.earth.headlessmc.launcher.plugin.PluginManager
import me.earth.headlessmc.launcher.specifics.VersionSpecificModManager
import me.earth.headlessmc.launcher.specifics.VersionSpecificMods
import me.earth.headlessmc.launcher.util.UuidUtil
import me.earth.headlessmc.launcher.version.VersionService
import me.earth.headlessmc.launcher.version.VersionUtil
import me.earth.headlessmc.logging.LoggerFactory
import me.earth.headlessmc.logging.LoggingProperties
import me.earth.headlessmc.logging.LoggingService
import me.earth.headlessmc.lwjgl.LwjglProperties
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream


class HmcAndroidLauncher {
    fun launch(context: Context, input: PipedInputStream, output: PipedOutputStream) {
        configureProperties()

        val commandLine = CommandLine()
        val inAndOutProvider = commandLine.inAndOutProvider
        inAndOutProvider.setIn { input }
        val outputPrintStream = PrintStream(output)
        inAndOutProvider.setOut { outputPrintStream }
        inAndOutProvider.setErr { outputPrintStream }
        System.setOut(outputPrintStream)

        val loggingService = LoggingService()
        loggingService.setFileHandler(false)
        loggingService.setStreamFactory { outputPrintStream }
        loggingService.init()
        val logger = LoggerFactory.getLogger("HeadlessMc")
        logger.info("Initializing HeadlessMc")

        val files = FileManager(context.filesDir.absolutePath)
        logger.info("HeadlessMc directory: " + files.base)
        System.setProperty(LauncherProperties.GAME_DIR.name, files.getDir("mc").absolutePath)
        System.setProperty(LauncherProperties.MC_DIR.name, files.getDir("mc").absolutePath)

        val configs = ConfigService(files)
        configs.config = ConfigImpl.empty()

        val hmc = HeadlessMcImpl(configs, commandLine, ExitManager(), loggingService)
        hmc.exitManager.setExitManager { i -> logger.info("HeadlessMc exited with code $i") }

        try {
            val os = OSFactory.detect(configs.config)
            logger.info("OS: $os")
            val mcFiles = MinecraftFinder.find(configs.config, os)
            val versions = VersionService(mcFiles)
            versions.refresh()

            val javas = Java8Service(configs)
            val accountStore = AccountStore(files, configs)
            val accounts = AccountManager(AccountValidator(), OfflineChecker(configs), accountStore)

            //accounts.load(configs.config)
            val downloadService = DownloadService()
            val versionSpecificModManager = VersionSpecificModManager(downloadService, FileManager(""))
            versionSpecificModManager.addRepository(VersionSpecificMods.HMC_SPECIFICS)
            versionSpecificModManager.addRepository(VersionSpecificMods.MC_RUNTIME_TEST)

            val launcher = Launcher(
                hmc,
                versions,
                mcFiles,
                mcFiles,
                ChecksumService(),
                downloadService,
                files,
                AndroidProcessFactory(downloadService, mcFiles, configs, os),
                configs,
                javas,
                accounts,
                versionSpecificModManager,
                PluginManager()
            )

            val launchContext = LaunchContext(launcher)
            commandLine.setAllContexts(launchContext)
            val copyContext = CopyContext(hmc, true)
            commandLine.setAllContexts(copyContext)
            for (command in launchContext) {
                if (command is FabricCommand) {
                    command.inMemoryLauncher.setClassLoaderFactory { urls -> ResourceDexClassloader.create(urls, null) }
                } else if (command is ForgeCommand) {
                    command.installer.inMemoryLauncher.setClassLoaderFactory { urls -> ResourceDexClassloader.create(urls, null) }
                }
            }

            deleteOldFiles(launcher)
            launcher.log(VersionUtil.makeTable(VersionUtil.releases(versions.contents)))

            BufferedCommandLineReader().read(hmc)
        } catch (t: Throwable) {
            logger.error("Exception while running launcher", t)
        }
    }

    private fun deleteOldFiles(launcher: Launcher) {
        if (launcher.config.get(LauncherProperties.KEEP_FILES, false)) {
            return
        }

        for (file in launcher.fileManager.listFiles()) {
            if (file.isDirectory() && UuidUtil.isUuid(file.getName())) {
                try {
                    launcher.fileManager.delete(file)
                } catch (ioe: IOException) {
                    Log.e("hmc", "Could not delete " + file.getName() + " : " + ioe.message)
                }
            }
        }
    }

    private fun configureProperties() {
        System.setProperty(HmcProperties.LOGLEVEL.name, "INFO")
        System.setProperty(LauncherProperties.IN_MEMORY_REQUIRE_CORRECT_JAVA.name, "false")
        System.setProperty(LoggingProperties.FILE_HANDLER_ENABLED, "false")
        System.setProperty(LwjglProperties.NO_AWT, "true")
        System.setProperty(LauncherProperties.HTTP_USER_AGENT_ENABLED.name, "false")
    }

}