package de.luckymcdev.foundryengine.intellij

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.nio.file.Path

class FoundryToolWindowFactory : ToolWindowFactory, DumbAware {

    /** Only show the window in projects that look like a bundle project. */
    override fun shouldBeAvailable(project: Project): Boolean {
        val base = project.basePath ?: return false
        return BundleInfo.read(Path.of(base)) != null
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(FoundryPanel(project), null, false)
        toolWindow.contentManager.addContent(content)
    }
}
