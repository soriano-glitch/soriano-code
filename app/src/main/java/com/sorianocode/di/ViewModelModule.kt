package com.sorianocode.di

import com.sorianocode.SorianoCodeApplication
import com.sorianocode.data.connection.SecureSettingsRepository
import com.sorianocode.data.settings.AppPreferencesRepository
import com.sorianocode.feature.activity.ActivityViewModel
import com.sorianocode.feature.chat.ChatViewModel
import com.sorianocode.feature.settings.SettingsViewModel
import com.sorianocode.feature.workspace.WorkspaceViewModel
import com.sorianocode.runtime.local.CustomProviderModelFetcher
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

val viewModelModule =
    module {

        viewModel {
            val app = androidContext().applicationContext as SorianoCodeApplication
            val preferences: AppPreferencesRepository = get()
            val settings: SecureSettingsRepository = get()
            ChatViewModel(
                draftRepo = get(),
                pullRequestStatuses = get(),
                // Same Codex fast-mode wiring as the factory in ui/SorianoCodeApp.kt; keep the two in step.
                onCodexFastModeChanged = preferences::setCodexFastMode,
                initialCodexFastMode = settings.codexFastMode,
                // Kept in step with the hand-rolled factory in ui/SorianoCodeApp.kt: both construction
                // paths have to park the connection probe and stall watchdog while the app is
                // backgrounded, or whichever one the screen happens to use decides whether those
                // 30-second loops keep polling out of sight.
                awaitForeground = { app.appForeground.foreground.first { visible -> visible } },
                videoNotSupportedMessage = androidContext().getString(com.sorianocode.R.string.error_video_not_supported),
                workspaceAttachmentNoteTemplate =
                    androidContext().getString(
                        com.sorianocode.R.string.chat_workspace_attachment_note,
                    ),
            )
        }

        viewModel {
            WorkspaceViewModel(
                registry = get(),
                catalog = get(),
                localRuntimeManager = get(),
                localRuntimeController = get(),
                settings = get(),
                workspaceHostDir = File(androidContext().filesDir, "runtime/workspace"),
                incompleteConnectionMessage = androidContext().getString(com.sorianocode.R.string.connection_info_incomplete),
            )
        }

        viewModel {
            SettingsViewModel(
                catalog = get(),
                preferences = get(),
                credentials = get(),
                customProviders = get(),
                settings = get(),
                registry = get(),
                voskModels = get(),
                providerDisconnectRejectedMessage =
                    androidContext().getString(com.sorianocode.R.string.provider_disconnect_rejected),
                customProviderInvalidMessage =
                    androidContext().getString(com.sorianocode.R.string.provider_custom_invalid),
                modelFetcher = CustomProviderModelFetcher(get()),
                customProviderFetchNeedsBaseUrlMessage =
                    androidContext().getString(com.sorianocode.R.string.provider_custom_fetch_needs_base_url),
                customProviderFetchEmptyMessage =
                    androidContext().getString(com.sorianocode.R.string.provider_custom_fetch_empty),
                customProviderFetchFailedMessage =
                    androidContext().getString(com.sorianocode.R.string.provider_custom_fetch_failed),
            )
        }

        viewModel {
            ActivityViewModel(
                catalog = get(),
                activity = get(),
            )
        }
    }
