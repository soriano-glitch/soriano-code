package com.sorianocode.runtime

import com.sorianocode.core.api.ConfiguredProvider
import com.sorianocode.core.api.McpAuthRemoval
import com.sorianocode.core.api.McpAuthStart
import com.sorianocode.core.api.McpAuthStatus
import com.sorianocode.core.api.McpServer
import com.sorianocode.core.api.OpenCodeAgent
import com.sorianocode.core.api.OpenCodeCommand
import com.sorianocode.core.api.OpenCodeEvent
import com.sorianocode.core.api.OpenCodeFileChange
import com.sorianocode.core.api.OpenCodeFileContent
import com.sorianocode.core.api.OpenCodeFileNode
import com.sorianocode.core.api.OpenCodeHealth
import com.sorianocode.core.api.OpenCodeMessage
import com.sorianocode.core.api.OpenCodePathInfo
import com.sorianocode.core.api.OpenCodeProject
import com.sorianocode.core.api.OpenCodeSearchMatch
import com.sorianocode.core.api.OpenCodeSession
import com.sorianocode.core.api.OpenCodeSkill
import com.sorianocode.core.api.OpenCodeTodo
import com.sorianocode.core.api.OpenCodeVcsInfo
import com.sorianocode.core.api.PromptRequest
import com.sorianocode.core.api.ProviderAuthAuthorization
import com.sorianocode.core.api.ProviderAuthMethod
import com.sorianocode.core.api.ProviderCatalog
import com.sorianocode.core.api.QuestionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class BackendKind {
    LOCAL,
    REMOTE,
}

enum class PermissionResponse(val apiValue: String) {
    ONCE("once"),
    ALWAYS("always"),
    REJECT("reject"),
}

interface OpenCodeBackend {
    val id: String
    val displayName: String
    val kind: BackendKind

    suspend fun health(): OpenCodeHealth

    suspend fun listSessions(directory: String? = null): List<OpenCodeSession>

    suspend fun session(sessionId: String): OpenCodeSession =
        listSessions().firstOrNull { it.id == sessionId }
            ?: error("Session not found: $sessionId")

    suspend fun createSession(
        title: String? = null,
        directory: String? = null,
    ): OpenCodeSession

    suspend fun listMessages(sessionId: String): List<OpenCodeMessage>

    /**
     * Deletes a message (and its parts) out of a session's transcript. See
     * [RuntimeCapabilities.editMessages] for which backends actually support this; the others fall
     * through to [unsupported].
     */
    suspend fun deleteMessage(
        sessionId: String,
        messageId: String,
    ): Boolean = unsupported("message delete")

    suspend fun listProviders(): ProviderCatalog

    suspend fun listAgents(): List<OpenCodeAgent>

    suspend fun providerAuthMethods(): Map<String, List<ProviderAuthMethod>> = unsupported("provider auth methods")

    suspend fun authorizeProvider(
        providerId: String,
        methodIndex: Int,
        inputs: Map<String, String> = emptyMap(),
    ): ProviderAuthAuthorization = unsupported("provider OAuth authorization")

    suspend fun setProviderApiKey(
        providerId: String,
        apiKey: String,
        metadata: Map<String, String> = emptyMap(),
    ): Boolean = unsupported("provider API authentication")

    suspend fun removeProviderAuth(providerId: String): Boolean = unsupported("provider authentication removal")

    suspend fun completeProviderOAuth(
        providerId: String,
        methodIndex: Int,
        code: String?,
    ): Boolean = unsupported("provider OAuth callback")

    suspend fun listProjects(directory: String? = null): List<OpenCodeProject> = unsupported("projects")

    suspend fun currentProject(directory: String? = null): OpenCodeProject = unsupported("current project")

    suspend fun pathInfo(directory: String? = null): OpenCodePathInfo = unsupported("path info")

    suspend fun listFiles(
        directory: String,
        path: String = ".",
    ): List<OpenCodeFileNode> = unsupported("file list")

    suspend fun readFile(
        directory: String,
        path: String,
    ): OpenCodeFileContent = unsupported("file read")

    /**
     * The exact text of the file at [path] under [directory], as the editor should start from it.
     *
     * Unlike [readFile] this never trims or re-encodes anything, and it refuses what [writeFile]
     * could not save back (binary, not UTF-8, too large, read-only) by throwing [FileEditException].
     * Only backends with [RuntimeCapabilities.fileEditing] implement it.
     */
    suspend fun readFileForEditing(
        directory: String,
        path: String,
    ): String = unsupported("file edit")

    /**
     * Replaces the existing text file at [path] under [directory] with [content].
     *
     * OpenCode's server has no write endpoint, so this exists only for backends whose workspace is a
     * plain directory on this device - see [RuntimeCapabilities.fileEditing]; every other backend
     * falls through to [unsupported]. [directory] is the root the write must stay inside.
     *
     * [expectedContent] is what [readFileForEditing] returned. When it is given and the file no
     * longer holds exactly that, nothing is written. Refusals throw [FileEditException] carrying a
     * [FileEditFailure].
     */
    suspend fun writeFile(
        directory: String,
        path: String,
        content: String,
        expectedContent: String? = null,
    ) {
        unsupported("file write")
    }

    suspend fun fileStatus(directory: String): List<OpenCodeFileChange> = unsupported("file status")

    suspend fun searchText(
        directory: String,
        pattern: String,
    ): List<OpenCodeSearchMatch> = unsupported("text search")

    suspend fun findFiles(
        directory: String,
        query: String,
        includeDirectories: Boolean? = null,
        type: String? = null,
        limit: Int? = null,
    ): List<String> = unsupported("file search")

    suspend fun vcsInfo(directory: String): OpenCodeVcsInfo = unsupported("VCS info")

    suspend fun vcsStatus(directory: String): List<OpenCodeFileChange> = unsupported("VCS status")

    suspend fun vcsDiff(
        directory: String,
        mode: String = "git",
        context: Int? = null,
    ): List<OpenCodeFileChange> = unsupported("VCS diff")

    suspend fun sessionDiff(
        sessionId: String,
        directory: String? = null,
        messageId: String? = null,
    ): List<OpenCodeFileChange> = unsupported("session diff")

    suspend fun sessionTodo(
        sessionId: String,
        directory: String? = null,
    ): List<OpenCodeTodo> = unsupported("session todo")

    suspend fun sendMessage(
        sessionId: String,
        request: PromptRequest,
    )

    suspend fun summarizeSession(
        sessionId: String,
        providerId: String,
        modelId: String,
    ): Boolean = unsupported("session summarization")

    suspend fun abortSession(sessionId: String): Boolean

    suspend fun renameSession(
        sessionId: String,
        title: String,
    ): OpenCodeSession = unsupported("session rename")

    suspend fun deleteSession(sessionId: String): Boolean = unsupported("session delete")

    suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: PermissionResponse,
        remember: Boolean,
    ): Boolean

    suspend fun answerQuestion(
        requestId: String,
        answers: List<List<String>>,
        directory: String?,
    ): Boolean = unsupported("question answers")

    suspend fun rejectQuestion(
        requestId: String,
        directory: String?,
    ): Boolean = unsupported("question rejection")

    suspend fun pendingQuestions(directory: String?): List<QuestionRequest> = emptyList()

    suspend fun archiveSession(sessionId: String): OpenCodeSession = unsupported("session archive")

    suspend fun mcpServers(): List<McpServer> = unsupported("MCP servers")

    suspend fun addMcpServer(body: JsonObject): McpServer = unsupported("MCP add server")

    suspend fun connectMcpServer(name: String): Boolean = unsupported("MCP connect")

    suspend fun disconnectMcpServer(name: String): Boolean = unsupported("MCP disconnect")

    suspend fun removeMcpAuth(name: String): McpAuthRemoval = unsupported("MCP remove auth")

    suspend fun mcpAuth(name: String): McpAuthStart = unsupported("MCP auth")

    suspend fun mcpAuthCallback(
        name: String,
        code: String,
    ): McpAuthStatus = unsupported("MCP auth callback")

    suspend fun config(): JsonElement = unsupported("config")

    suspend fun updateConfig(patch: JsonObject): JsonElement = unsupported("config update")

    suspend fun configProviders(): List<ConfiguredProvider> = unsupported("config providers")

    suspend fun commands(): List<OpenCodeCommand> = unsupported("commands")

    suspend fun skills(): List<OpenCodeSkill> = unsupported("skills")

    /**
     * Executes a slash command or skill by name in [sessionId].
     *
     * OpenCode expands the command's template server-side and runs it as a prompt. For runtimes
     * without a command endpoint (Claude Code, Antigravity) the name is sent as a message instead,
     * letting the CLI's own slash-command handling pick it up.
     */
    suspend fun executeCommand(
        sessionId: String,
        command: String,
        arguments: String,
    ) {
        sendMessage(sessionId, PromptRequest(text = "/$command${arguments.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"))
    }

    fun events(): Flow<OpenCodeEvent>

    private fun unsupported(capability: String): Nothing =
        throw UnsupportedOperationException("OpenCode backend does not support $capability")
}
