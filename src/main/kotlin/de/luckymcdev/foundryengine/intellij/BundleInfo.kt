package de.luckymcdev.foundryengine.intellij

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

data class BundleInfo(val bundleId: String, val version: String, val displayName: String) {

    val folderName: String get() = "$bundleId-$version"

    companion object {
        private val KEY_VALUE = Regex("^\\s*(bundleId|version|displayName)\\s*=\\s*\"([^\"]*)\"")

        fun read(projectDir: Path): BundleInfo? {
            val resources = projectDir.resolve("src/main/resources")
            if (!Files.isDirectory(resources)) return null

            return try {
                Files.newDirectoryStream(resources, "*.bundles.toml").use { stream ->
                    val toml = stream.firstOrNull() ?: return null
                    val values = HashMap<String, String>()
                    for (line in Files.readAllLines(toml)) {
                        KEY_VALUE.find(line)?.let { values.putIfAbsent(it.groupValues[1], it.groupValues[2]) }
                    }
                    BundleInfo(
                        values["bundleId"] ?: "?",
                        values["version"] ?: "?",
                        values["displayName"] ?: "?",
                    )
                }
            } catch (e: IOException) {
                null
            }
        }
    }
}
