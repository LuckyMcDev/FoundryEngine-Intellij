package de.luckymcdev.foundryengine.intellij

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Per-project settings, shared with the Gradle plugin through
 * `<project>/.foundryengine/instance.properties`.
 *
 * @property modsPath absolute path of the mods folder whose jars go on the classpath
 * @property bundlesDir bundles deploy folder; blank means the Gradle default (run/FoundryEngine/bundles)
 * @property modJars selected jar file names inside [modsPath]
 * @property prismExe optional path to the Prism Launcher executable (blank = auto-detect)
 */
class FoundryConfig(
    var modsPath: String = "",
    var bundlesDir: String = "",
    var modJars: List<String> = emptyList(),
    var prismExe: String = "",
) {

    @Throws(IOException::class)
    fun save(projectDir: Path) {
        val file = file(projectDir)
        Files.createDirectories(file.parent)

        val props = Properties()
        props.setProperty("modsPath", modsPath.replace('\\', '/'))
        if (bundlesDir.isNotBlank()) {
            props.setProperty("bundlesDir", bundlesDir.replace('\\', '/'))
        }
        props.setProperty("modJars", modJars.joinToString(","))
        if (prismExe.isNotBlank()) {
            props.setProperty("prismExe", prismExe.replace('\\', '/'))
        }

        Files.newOutputStream(file).use {
            props.store(it, "Managed by the FoundryEngine IntelliJ plugin. Do not commit.")
        }
    }

    companion object {
        private const val FILE = ".foundryengine/instance.properties"

        fun file(projectDir: Path): Path = projectDir.resolve(FILE)

        fun load(projectDir: Path): FoundryConfig {
            val file = file(projectDir)
            if (!Files.isRegularFile(file)) return FoundryConfig()

            val props = Properties()
            try {
                Files.newInputStream(file).use { props.load(it) }
            } catch (e: IOException) {
                return FoundryConfig()
            }

            return FoundryConfig(
                modsPath = props.getProperty("modsPath", ""),
                bundlesDir = props.getProperty("bundlesDir", ""),
                modJars = props.getProperty("modJars", "")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                prismExe = props.getProperty("prismExe", ""),
            )
        }

        @Throws(IOException::class)
        fun delete(projectDir: Path) {
            Files.deleteIfExists(file(projectDir))
        }
    }
}