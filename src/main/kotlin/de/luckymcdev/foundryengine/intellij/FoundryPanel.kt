package de.luckymcdev.foundryengine.intellij

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CheckBoxList
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

private const val BUNDLE = "messages.FoundryBundle"

/** Content of the FoundryEngine tool window. */
internal class FoundryPanel(private val project: Project) : JBPanel<FoundryPanel>(BorderLayout()) {

    private val projectDir: Path = Path.of(requireNotNull(project.basePath))

    private val bundleLabel = JBLabel()
    private val status = JBLabel()
    private val instances = ComboBox<InstanceDetector.Candidate>()
    private val modsPath = TextFieldWithBrowseButton()
    private val bundlesDir = TextFieldWithBrowseButton()
    private val prismExe = TextFieldWithBrowseButton()
    private val jarList = CheckBoxList<String>()
    private val jarNames = mutableListOf<String>()
    private val runButton = JButton()

    private var detected: List<InstanceDetector.Candidate> = emptyList()
    private var adjusting = false
    private var pendingSelection: List<String>? = null

    init {
        modsPath.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(FoundryBundle.message("chooser.mods")),
        )
        bundlesDir.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(FoundryBundle.message("chooser.bundles")),
        )
        prismExe.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle(FoundryBundle.message("chooser.prism")),
        )
        (bundlesDir.textField as? JBTextField)?.emptyText?.text = FoundryBundle.message("field.bundles.hint")
        (prismExe.textField as? JBTextField)?.emptyText?.text = FoundryBundle.message("field.prism.hint")

        modsPath.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!adjusting) {
                    rescanJars()
                    selectMatchingInstance()
                }
            }
        })
        prismExe.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updateRunButton()
        })
        instances.addActionListener {
            val candidate = instances.selectedItem as? InstanceDetector.Candidate
            if (!adjusting && candidate != null) {
                modsPath.text = candidate.modsDir.toString()
            }
        }
        runButton.addActionListener { runClient() }

        val instanceRow = row(instances, button("button.detect") { detect() })
        val bundlesRow = row(bundlesDir, button("button.use.instance") { useInstanceBundlesDir() })
        val jarScroll = JBScrollPane(jarList).also { it.preferredSize = Dimension(0, JBUI.scale(160)) }

        val buttons = JPanel(GridLayout(0, 2, JBUI.scale(4), JBUI.scale(4)))
        buttons.add(button("button.apply") { applyAndSync() })
        buttons.add(button("button.reset") { reset() })
        buttons.add(button("button.build") { runGradle("buildBundle") })
        buttons.add(button("button.deploy") { runGradle("deployBundle") })
        buttons.add(runButton)
        buttons.add(button("button.open") { openBundlesFolder() })

        val form = FormBuilder.createFormBuilder()
            .addComponent(bundleLabel)
            .addSeparator(8)
            .addLabeledComponent(FoundryBundle.message("label.instances"), instanceRow, true)
            .addLabeledComponent(FoundryBundle.message("label.mods"), modsPath, true)
            .addLabeledComponent(FoundryBundle.message("label.jars"), jarScroll, true)
            .addComponent(status)
            .addSeparator(8)
            .addLabeledComponent(FoundryBundle.message("label.bundles"), bundlesRow, true)
            .addLabeledComponent(FoundryBundle.message("label.prism"), prismExe, true)
            .addSeparator(8)
            .addComponent(buttons)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        form.border = JBUI.Borders.empty(8)
        add(JBScrollPane(form), BorderLayout.CENTER)

        load()
    }

    private fun button(@PropertyKey(resourceBundle = BUNDLE) key: String, action: () -> Unit): JButton =
        JButton(FoundryBundle.message(key)).also { b -> b.addActionListener { action() } }

    private fun row(center: JComponent, east: JComponent): JPanel =
        JPanel(BorderLayout(JBUI.scale(4), 0)).also {
            it.add(center, BorderLayout.CENTER)
            it.add(east, BorderLayout.EAST)
        }

    private fun load() {
        val info = BundleInfo.read(projectDir)
        bundleLabel.text = if (info == null) {
            FoundryBundle.message("bundle.none")
        } else {
            FoundryBundle.message("bundle.label", info.displayName, info.folderName)
        }

        val cfg = FoundryConfig.load(projectDir)
        adjusting = true
        modsPath.text = cfg.modsPath
        bundlesDir.text = cfg.bundlesDir
        prismExe.text = cfg.prismExe
        adjusting = false

        pendingSelection = cfg.modJars.ifEmpty { null }
        rescanJars()
        updateRunButton()
        detect()
    }

    private fun modsDirOrNull(): Path? {
        val text = modsPath.text.trim()
        if (text.isEmpty()) return null
        return try {
            Path.of(text).takeIf { Files.isDirectory(it) }
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun currentSelection(): List<String> =
        jarNames.filterIndexed { index, _ -> jarList.isItemSelected(index) }

    private fun selectedPrismId(): String? =
        (instances.selectedItem as? InstanceDetector.Candidate)?.prismId

    /** The run button launches through Prism when a Prism instance is selected, otherwise through Gradle. */
    private fun updateRunButton() {
        runButton.text = if (selectedPrismId() != null) {
            FoundryBundle.message("button.launch.prism")
        } else {
            FoundryBundle.message("button.run")
        }
    }

    /** Keeps the instance combo box in sync when the mods path is typed or picked manually. */
    private fun selectMatchingInstance() {
        val current = modsDirOrNull()?.normalize()
        val match = detected.firstOrNull { current != null && it.modsDir.normalize() == current }
        adjusting = true
        try {
            instances.selectedItem = match
        } finally {
            adjusting = false
        }
        updateRunButton()
    }

    private fun rescanJars() {
        val keep = pendingSelection ?: currentSelection()
        pendingSelection = null

        jarNames.clear()
        jarList.clear()

        val dir = modsDirOrNull()
        if (dir != null) {
            try {
                val names = Files.list(dir).use { files -> files.map { it.fileName.toString() }.toList() }
                jarNames += names
                    .filter { it.endsWith(".jar", ignoreCase = true) }
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
            } catch (e: IOException) {
                // unreadable folder: show an empty list
            }
        }

        var selected = jarNames.filter { it in keep }.toSet()
        if (selected.isEmpty()) {
            selected = jarNames.filter { InstanceDetector.isEngineJar(it) }.toSet()
        }
        jarNames.forEach { jarList.addItem(it, it, it in selected) }

        val engine = jarNames.firstOrNull { InstanceDetector.isEngineJar(it) }
        status.text = when {
            dir == null && modsPath.text.isBlank() -> FoundryBundle.message("status.none")
            dir == null -> FoundryBundle.message("status.notfound")
            engine != null -> FoundryBundle.message("status.jars", jarNames.size, engine)
            else -> FoundryBundle.message("status.jars.noengine", jarNames.size)
        }
    }

    private fun detect() {
        ApplicationManager.getApplication().executeOnPooledThread(Runnable {
            val found = InstanceDetector.detect(projectDir)
            ApplicationManager.getApplication().invokeLater({
                detected = found
                adjusting = true
                try {
                    instances.removeAllItems()
                    found.forEach { instances.addItem(it) }
                } finally {
                    adjusting = false
                }
                selectMatchingInstance()
            }, ModalityState.any())
        })
    }

    private fun save(): Boolean {
        val cfg = FoundryConfig(
            modsPath = modsPath.text.trim(),
            bundlesDir = bundlesDir.text.trim(),
            modJars = currentSelection(),
            prismExe = prismExe.text.trim(),
        )
        return try {
            cfg.save(projectDir)
            true
        } catch (e: IOException) {
            showNotification(
                FoundryBundle.message("notify.write.failed", FoundryConfig.file(projectDir), e.message ?: ""),
                NotificationType.ERROR,
            )
            false
        }
    }

    private fun syncGradle() {
        ExternalSystemUtil.refreshProject(
            projectDir.toString(),
            ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).build(),
        )
    }

    private fun applyAndSync() {
        if (save()) syncGradle()
    }

    private fun reset() {
        try {
            FoundryConfig.delete(projectDir)
        } catch (e: IOException) {
            showNotification(FoundryBundle.message("notify.delete.failed", e.message ?: ""), NotificationType.ERROR)
            return
        }
        adjusting = true
        modsPath.text = ""
        bundlesDir.text = ""
        prismExe.text = ""
        adjusting = false
        pendingSelection = null
        rescanJars()
        selectMatchingInstance()
        detect()
        syncGradle()
    }

    private fun useInstanceBundlesDir() {
        val parent = modsDirOrNull()?.parent ?: return
        bundlesDir.text = parent.resolve("FoundryEngine").resolve("bundles").toString()
    }

    /** Runs a Gradle task; [afterSuccess] is invoked only if the task finished successfully. */
    private fun runGradle(task: String, afterSuccess: (() -> Unit)? = null) {
        if (!save()) return
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = projectDir.toString()
            taskNames = listOf(task)
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }
        if (afterSuccess == null) {
            ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID)
        } else {
            val callback = object : TaskCallback {
                override fun onSuccess() = afterSuccess()
                override fun onFailure() {}
            }
            ExternalSystemUtil.runTask(
                settings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID,
                callback,
                ProgressExecutionMode.IN_BACKGROUND_ASYNC,
            )
        }
    }

    /** Gradle `runClient` for dev runs; deploy + Prism command line for a selected Prism instance. */
    private fun runClient() {
        val instanceId = selectedPrismId()
        if (instanceId == null) {
            runGradle("runClient")
            return
        }

        val exe = PrismLauncher.findExecutable(prismExe.text)
        if (exe == null) {
            showNotification(FoundryBundle.message("notify.prism.missing"), NotificationType.WARNING)
            return
        }

        // The bundle has to land in the Prism instance, not in run/.
        if (bundlesDir.text.isBlank()) useInstanceBundlesDir()

        runGradle("deployBundle") {
            PrismLauncher.launch(exe, instanceId) { error ->
                if (error == null) {
                    showNotification(
                        FoundryBundle.message("notify.prism.launched", instanceId),
                        NotificationType.INFORMATION
                    )
                } else {
                    showNotification(FoundryBundle.message("notify.prism.failed", error), NotificationType.ERROR)
                }
            }
        }
    }

    private fun openBundlesFolder() {
        val text = bundlesDir.text.trim()
        val dir = if (text.isEmpty()) {
            projectDir.resolve("run/FoundryEngine/bundles")
        } else {
            projectDir.resolve(text).normalize()
        }
        if (Files.isDirectory(dir)) {
            RevealFileAction.openDirectory(dir)
        } else {
            showNotification(FoundryBundle.message("notify.folder.missing", dir), NotificationType.WARNING)
        }
    }

    private fun showNotification(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("FoundryEngine")
            .createNotification(message, type)
            .notify(project)
    }
}