package com.sorianocode.feature.browser

import com.sorianocode.core.storage.DeviceStorage
import com.sorianocode.feature.workspace.WorkspaceFolders
import com.sorianocode.runtime.LocalAgent
import org.json.JSONObject
import java.io.File
import java.net.URI

/** Resolves the active guest workspace to the same host folder its PRoot process sees. */
internal fun consumeBrowserOpenCommand(
    workspacePath: String?,
    agent: LocalAgent?,
    runtimeDirectory: File,
    deviceStorage: DeviceStorage.Mounts,
): String? {
    // A remote workspace can share a path with a local one, but its commands are not on this phone.
    if (workspacePath == null || agent == null) return null
    return runCatching {
        val rootfs =
            File(runtimeDirectory, "environment/antigravity-rootfs")
                .takeIf { agent == LocalAgent.ANTIGRAVITY && it.isDirectory }
                ?: File(runtimeDirectory, "environment/rootfs")
        val workspace =
            WorkspaceFolders.hostDirectory(rootfs, File(runtimeDirectory, "workspace"), workspacePath, deviceStorage)
                ?: return null
        val commandFile = File(workspace, ".soriano-code/browser-command.json")
        if (!commandFile.isFile) return null
        val command = JSONObject(commandFile.readText())
        val url = command.optString("url")
        val uri = URI(url)
        val valid = command.optString("action") == "open" && uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
        // The agent writes this file directly. Deleting before parsing can discard a partial write;
        // only consume a complete, valid command, and do not reopen it if deletion fails.
        url.takeIf { valid && commandFile.delete() }
    }.getOrNull()
}
