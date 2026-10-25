package de.luckymcdev.foundryengine.intellij

import com.intellij.openapi.util.SystemInfo
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Finds Minecraft mods folders (dev run, vanilla launcher, Prism, Modrinth, CurseForge). */
object InstanceDetector {

    /** [prismId] is the Prism instance ID (= instance folder name) when this is a Prism instance. */
    data class Candidate(
        val label: String,
        val modsDir: Path,
        val hasEngine: Boolean,
        val prismId: String? = null,
    ) {
        override fun toString(): String = label + if (hasEngine) "" else " (no FoundryEngine jar)"
    }

    fun isEngineJar(fileName: String): Boolean {
        val n = fileName.lowercase(Locale.ROOT)
        return n.endsWith(".jar") && "foundryengine" in n
    }

    fun detect(projectDir: Path): List<Candidate> {
        val out = mutableListOf<Candidate>()
        add(out, "Dev run", projectDir.resolve("run/mods"))
        add(out, "Minecraft", vanillaDir().resolve("mods"))

        for ((launcher, root) in launcherRoots()) {
            if (!Files.isDirectory(root)) continue
            try {
                Files.newDirectoryStream(root).use { instances ->
                    for (instance in instances) {
                        val mods = listOf("minecraft/mods", ".minecraft/mods", "mods")
                            .map { instance.resolve(it) }
                            .firstOrNull { Files.isDirectory(it) }
                        if (mods != null) {
                            add(
                                out,
                                "$launcher: ${instance.fileName}",
                                mods,
                                prismId = if (launcher == "Prism") instance.fileName.toString() else null,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                // unreadable launcher folder: skip it
            }
        }

        // Stable sort: instances that contain the engine come first.
        return out.sortedByDescending { it.hasEngine }
    }

    private fun add(out: MutableList<Candidate>, label: String, mods: Path, prismId: String? = null) {
        if (Files.isDirectory(mods)) {
            out.add(Candidate(label, mods, containsEngine(mods), prismId))
        }
    }

    private fun containsEngine(mods: Path): Boolean = try {
        Files.list(mods).use { files -> files.anyMatch { isEngineJar(it.fileName.toString()) } }
    } catch (e: IOException) {
        false
    }

    private fun home(): Path = Path.of(System.getProperty("user.home"))

    private fun vanillaDir(): Path {
        val appData = System.getenv("APPDATA")
        return when {
            SystemInfo.isWindows && appData != null -> Path.of(appData, ".minecraft")
            SystemInfo.isMac -> home().resolve("Library/Application Support/minecraft")
            else -> home().resolve(".minecraft")
        }
    }

    private fun launcherRoots(): List<Pair<String, Path>> {
        val home = home()
        val roots = mutableListOf("CurseForge" to home.resolve("curseforge/minecraft/Instances"))

        val appData = System.getenv("APPDATA")
        when {
            SystemInfo.isWindows && appData != null -> {
                val base = Path.of(appData)
                roots += "Prism" to base.resolve("PrismLauncher/instances")
                roots += "Modrinth" to base.resolve("ModrinthApp/profiles")
            }

            SystemInfo.isMac -> {
                val base = home.resolve("Library/Application Support")
                roots += "Prism" to base.resolve("PrismLauncher/instances")
                roots += "Modrinth" to base.resolve("com.modrinth.theseus/profiles")
            }

            else -> {
                val base = home.resolve(".local/share")
                roots += "Prism" to base.resolve("PrismLauncher/instances")
                roots += "Modrinth" to base.resolve("ModrinthApp/profiles")
            }
        }
        return roots
    }
}