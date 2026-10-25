package de.luckymcdev.foundryengine.intellij

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Finds and starts Prism Launcher through its command line (`prismlauncher --launch <instance id>`). */
object PrismLauncher {

    /** [override] is the path from the UI; blank means auto-detect. */
    fun findExecutable(override: String): Path? {
        if (override.isNotBlank()) {
            try {
                Path.of(override.trim()).takeIf { Files.isRegularFile(it) }?.let { return it }
            } catch (e: RuntimeException) {
                // invalid path text: fall through to auto-detect
            }
        }

        val candidates = mutableListOf<Path>()
        when {
            SystemInfo.isWindows -> {
                System.getenv("LOCALAPPDATA")?.let {
                    candidates += Path.of(it, "Programs", "PrismLauncher", "prismlauncher.exe").toMutableList()
                }
                System.getenv("ProgramFiles")?.let {
                    candidates += Path.of(it, "PrismLauncher", "prismlauncher.exe").toMutableList()
                }
            }

            SystemInfo.isMac -> {
                candidates += Path.of("/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher").toMutableList()
            }
        }
        candidates.firstOrNull { Files.isRegularFile(it) }?.let { return it }

        return PathEnvironmentVariableUtil.findInPath("prismlauncher")?.toPath()
    }

    /**
     * Starts the instance and returns immediately; [onDone] gets null on success or an error message.
     * Output is discarded so an unread pipe can never block Prism.
     */
    fun launch(exe: Path, instanceId: String, onDone: (error: String?) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(Runnable {
            try {
                ProcessBuilder(exe.toString(), "-l", instanceId)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                onDone(null)
            } catch (e: IOException) {
                onDone(e.message ?: e.toString())
            }
        })
    }
}