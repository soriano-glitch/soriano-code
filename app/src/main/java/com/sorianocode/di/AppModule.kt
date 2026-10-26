package com.sorianocode.di

import android.os.Build
import com.sorianocode.SorianoCodeApplication
import com.sorianocode.core.api.GitHubApiClient
import com.sorianocode.core.notification.RuntimeNotificationHelper
import com.sorianocode.data.connection.SecureSettingsRepository
import com.sorianocode.data.repository.AndroidRuntimeActivityMessages
import com.sorianocode.data.repository.AndroidRuntimeCatalogMessages
import com.sorianocode.data.repository.PullRequestStatusRepository
import com.sorianocode.data.repository.RuntimeActivityRepository
import com.sorianocode.data.repository.RuntimeCatalogRepository
import com.sorianocode.data.settings.AppPreferencesRepository
import com.sorianocode.data.settings.DraftRepository
import com.sorianocode.data.settings.DraftStore
import com.sorianocode.feature.wakeword.VoskModelStore
import com.sorianocode.runtime.RuntimeRegistry
import com.sorianocode.runtime.local.AndroidLocalRuntimeMessages
import com.sorianocode.runtime.local.AntigravityRuntime
import com.sorianocode.runtime.local.AntigravityTarget
import com.sorianocode.runtime.local.CustomProviderStore
import com.sorianocode.runtime.local.DefaultLocalRuntimeUpdateEngine
import com.sorianocode.runtime.local.GitCredentialHelper
import com.sorianocode.runtime.local.LocalProviderCredentialStore
import com.sorianocode.runtime.local.LocalRuntimeAccessCoordinator
import com.sorianocode.runtime.local.LocalRuntimeCommandRunner
import com.sorianocode.runtime.local.LocalRuntimeInstaller
import com.sorianocode.runtime.local.LocalRuntimeManager
import com.sorianocode.runtime.local.LocalRuntimeMessages
import com.sorianocode.runtime.local.LocalRuntimeProcessLauncher
import com.sorianocode.runtime.local.LocalRuntimeReleaseClient
import com.sorianocode.runtime.local.LocalRuntimeServiceController
import com.sorianocode.runtime.local.LocalRuntimeTarget
import com.sorianocode.runtime.local.LocalRuntimeUpdater
import com.sorianocode.runtime.local.StartupCommandsRunner
import com.sorianocode.runtime.local.VerifiedRuntimeDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val appModule =
    module {

        single<File> { File(androidContext().filesDir, "runtime") }

        single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

        single { SecureSettingsRepository(androidContext()) }

        single { AppPreferencesRepository(get()) }

        single<DraftStore> { DraftRepository(androidContext()) }

        single { RuntimeNotificationHelper(androidContext()) }

        single { AndroidRuntimeActivityMessages(androidContext()) }

        single { AndroidRuntimeCatalogMessages(androidContext()) }

        single { LocalProviderCredentialStore(get()) }

        single { CustomProviderStore(get()) }

        single { VoskModelStore(androidContext(), get(), get()) }

        single { OkHttpClient() }

        single { LocalRuntimeAccessCoordinator() }

        single<LocalRuntimeMessages> { AndroidLocalRuntimeMessages(androidContext()) }

        single {
            val runtimeDirectory: File = get()
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            LocalRuntimeInstaller(
                context = androidContext(),
                runtimeDirectory = runtimeDirectory,
                abi = abi,
                accessCoordinator = get(),
            )
        }

        single {
            val settings: SecureSettingsRepository = get()
            val providerCredentials: LocalProviderCredentialStore = get()
            val customProviders: CustomProviderStore = get()
            val runtimeDirectory: File = get()
            // Guest startup commands (issue #379): read from the workspace on every runtime start,
            // launched detached so they can neither block nor fail the start itself.
            val startupCommandsRunner =
                StartupCommandsRunner(
                    runtimeDirectory = runtimeDirectory,
                    githubToken = { settings.githubToken },
                )
            LocalRuntimeProcessLauncher(
                runtimeDirectory = runtimeDirectory,
                portProbe = LocalRuntimeManager::defaultPortProbe,
                githubToken = { settings.githubToken },
                beforeStart = { installed ->
                    runCatching { providerCredentials.syncToRuntime(installed.rootfs) }
                    runCatching { customProviders.syncToRuntime(installed.rootfs) }
                    runCatching {
                        GitCredentialHelper(installed.rootfs) { settings.githubToken }.let { helper ->
                            if (settings.githubToken.isNullOrBlank()) helper.remove() else helper.install()
                        }
                    }
                    runCatching { startupCommandsRunner.schedule(installed) }
                },
            )
        }

        single {
            val runtimeDirectory: File = get()
            val installer: LocalRuntimeInstaller = get()
            LocalRuntimeCommandRunner(
                runtimeDirectory = runtimeDirectory,
                installedRuntimeProvider = installer::installedRuntime,
                accessCoordinator = get(),
                messages = get(),
            )
        }

        single {
            val runtimeDirectory: File = get()
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val httpClient: OkHttpClient = get()
            val commandRunner: LocalRuntimeCommandRunner = get()
            val verifiedDownloader = VerifiedRuntimeDownloader(httpClient)
            val updater =
                LocalRuntimeUpdater(
                    runtimeDirectory = runtimeDirectory,
                    abi = abi,
                    downloadAsset = { asset, destination, progress ->
                        verifiedDownloader.download(
                            url = asset.url,
                            destination = destination,
                            expectedSha256 = asset.sha256,
                            expectedSizeBytes = asset.sizeBytes,
                            onProgress = progress,
                        )
                    },
                    candidateVersionProvider = { candidate ->
                        val result =
                            commandRunner.runShell(
                                commandText = "/usr/local/bin/${candidate.name} --version",
                                timeoutSeconds = 30L,
                            )
                        require(result.exitCode == 0) {
                            "OpenCode update candidate validation failed: ${result.output}"
                        }
                        result.output.lineSequence().firstOrNull(String::isNotBlank)
                            ?: error("OpenCode update candidate returned no version")
                    },
                    accessCoordinator = get(),
                    messages = get(),
                )
            val updateEngine =
                DefaultLocalRuntimeUpdateEngine(
                    releaseClient = LocalRuntimeReleaseClient(httpClient),
                    updater = updater,
                )
            LocalRuntimeManager(
                runtimeDirectory = runtimeDirectory,
                abi = abi,
                installer = get(),
                processLauncher = get(),
                updateEngine = updateEngine,
                messages = get(),
            )
        }

        single { LocalRuntimeServiceController(androidContext()) }

        single {
            RuntimeRegistry(
                store = get(),
                localTarget = LocalRuntimeTarget(get(), messages = get()),
                additionalTargets =
                    listOf(
                        AntigravityTarget(
                            AntigravityRuntime(get(), (get<LocalRuntimeInstaller>())::installedRuntime),
                        ),
                        // The application's own instance, not a second CodexTarget: Codex runs one
                        // long-lived app-server whose approvals and threads a second runtime would
                        // not see. Resolved lazily, after SorianoCodeApplication.onCreate has built it.
                        (androidContext().applicationContext as SorianoCodeApplication).codexTarget,
                    ),
            )
        }

        single {
            val settings: SecureSettingsRepository = get()
            GitHubApiClient(token = { settings.githubToken }, client = get())
        }

        single {
            PullRequestStatusRepository(api = get(), scope = get())
        }

        single {
            RuntimeCatalogRepository(get(), get(), messages = get<AndroidRuntimeCatalogMessages>())
        }

        single {
            val notifications: RuntimeNotificationHelper = get()
            RuntimeActivityRepository(
                registry = get(),
                scope = get(),
                onPermissionAsked = { request, title, runtimeId ->
                    notifications.notifyPermission(request, title, runtimeId)
                },
                onSessionIdle = { sessionId, title, runtimeId ->
                    notifications.notifySessionComplete(sessionId, title, runtimeId)
                },
                onSessionError = { sessionId, message, _, runtimeId ->
                    notifications.notifySessionError(sessionId, message, runtimeId)
                },
                onQuestionAsked = { request, title, runtimeId ->
                    notifications.notifyQuestion(request, title, runtimeId)
                },
                messages = get<AndroidRuntimeActivityMessages>(),
            )
        }
    }
