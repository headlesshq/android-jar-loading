package me.earth.headlessmc.android

import com.android.tools.r8.D8
import dalvik.system.PathClassLoader
import me.earth.headlessmc.launcher.launch.LaunchException
import me.earth.headlessmc.logging.LoggerFactory
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.io.path.exists

class ResourceDexClassloader(
    private val jarPath: Array<URL>,
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader) : PathClassLoader(dexPath, librarySearchPath, parent) {

    /**
     * For some reason getResourceAsStream does not work on a PathClassLoader, so instead we just open the jar files to find it.
     */
    override fun getResourceAsStream(name: String): InputStream {
        val logger = LoggerFactory.getLogger("ResourceDexClassloader")
        val inputStream = super.getResourceAsStream(name)
        if (inputStream == null) {
            logger.warn("Failed to find resource $name in DexClassloader")
            for (url in jarPath) {
                val file = File(url.toURI())
                val jar = JarFile(file)
                val entry = jar.getEntry(name)
                if (entry != null) {
                    logger.info("Found resource $name in jar ${file.name}")
                    return jar.getInputStream(entry)
                }
            }
        }

        return inputStream
    }

    companion object {
        fun create(classpathUrls: Array<URL>, librarySearchPath: String?): ResourceDexClassloader {
            val logger = LoggerFactory.getLogger("ResourceDexClassloader")
            // convert all jars to dex
            val dexClasspath = ArrayList<String>()
            for (url in classpathUrls) {
                logger.info("Converting $url to dex")
                val file = File(url.toURI())
                val outputDir = file.parentFile!!.toPath().resolve(file.nameWithoutExtension)
                if (!outputDir.exists()) {
                    Files.createDirectories(outputDir)
                } else if (!Files.isDirectory(outputDir)) {
                    throw LaunchException("$outputDir is not a directory!")
                }

                val outputFile = outputDir.resolve("classes.dex").toFile()
                for (i in 1..Integer.MAX_VALUE) {
                    val classesDex = if (i == 1) {
                        file.parentFile!!.toPath().resolve("classes.dex").toFile()
                    } else {
                        file.parentFile!!.toPath().resolve("classes$i.dex").toFile()
                    }

                    if (classesDex.exists()) {
                        Files.delete(classesDex.toPath())
                    } else {
                        break
                    }
                }

                if (outputFile.exists()) {
                    logger.info("Already converted $url to dex")
                } else {
                    D8.main(arrayOf("--min-api", "26", "--output", outputDir.toAbsolutePath().toString(), file.absolutePath))
                    System.gc()
                }

                if (!outputFile.exists()) {
                    logger.warn("Failed to convert $url to dex!")
                } else {
                    for (i in 1..Integer.MAX_VALUE) {
                        val classesDex = if (i == 1) {
                            outputDir.resolve("classes.dex").toFile()
                        } else {
                            outputDir.resolve("classes$i.dex").toFile()
                        }

                        if (classesDex.exists()) {
                            classesDex.setReadOnly() // otherwise -> java.lang.SecurityException: Writable dex file <...> is not allowed.
                            dexClasspath.add(classesDex.absolutePath)
                        } else {
                            break
                        }
                    }
                }
            }

            return ResourceDexClassloader(classpathUrls, dexClasspath.joinToString(File.pathSeparator), librarySearchPath, Companion::class.java.classLoader!!)
        }
    }

}