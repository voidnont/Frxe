package com.frxe.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frxe.music.BuildConfig
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.components.GlassPanel
import com.frxe.music.ui.gestures.PlayerGesturePreferences
import com.frxe.music.updates.DependencyUpdateMode
import com.frxe.music.updates.FrxeSupportLinks
import com.frxe.music.updates.RuntimeHealthStore
import com.frxe.music.updates.YtDlpRuntimeUpdater
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: FrxeViewModel,
    isTv: Boolean
) {
    val state by
        viewModel.updaterState
            .collectAsState()

    val islandState by
        viewModel.islandHubState
            .collectAsState()

    val runtimeHealth by
        RuntimeHealthStore.state
            .collectAsState()

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var updatingRuntime by
        remember {
            mutableStateOf(false)
        }

    var playerGesturesEnabled by
        remember(context) {
            mutableStateOf(
                PlayerGesturePreferences.enabled(context)
            )
        }

    val overlayPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .StartActivityForResult()
        ) {
            viewModel
                .refreshIslandHubSettings()
        }

    LaunchedEffect(Unit) {
        viewModel
            .refreshIslandHubSettings()

        if (
            state.appStatus ==
            "Not checked"
        ) {
            viewModel
                .checkForAppUpdate()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    start =
                        if (isTv) {
                            56.dp
                        } else {
                            20.dp
                        },
                    end =
                        if (isTv) {
                            56.dp
                        } else {
                            20.dp
                        },
                    top = 54.dp,
                    bottom = 190.dp
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            )
    ) {
        Text(
            "Settings",
            fontSize =
                if (isTv) {
                    42.sp
                } else {
                    32.sp
                },
            fontWeight =
                FontWeight.Black,
            color =
                MaterialTheme
                    .colorScheme
                    .onBackground
        )

        Text(
            "Frxe ${BuildConfig.VERSION_NAME} · components, updates and diagnostics",
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        GlassPanel(
            Modifier.fillMaxWidth(),
            radius = 28.dp,
            strong = true
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        13.dp
                    )
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    Icon(
                        Icons.Default
                            .PictureInPictureAlt,
                        contentDescription = null,
                        tint = Color.White
                    )

                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(
                            "Island Hub",
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                        )
                        Text(
                            "Top-center playback controls over other Android apps while FRXE is in the background.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(
                            "In-app Island",
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                        )
                        Text(
                            "Disabled so the Island never covers FRXE itself",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    Switch(
                        checked =
                            islandState.inAppEnabled,
                        onCheckedChange =
                            viewModel::setInAppIslandEnabled
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(
                            "Floating Island",
                            fontWeight =
                                FontWeight.SemiBold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                        )
                        Text(
                            if (
                                islandState
                                    .overlayPermissionGranted
                            ) {
                                "Can float above other apps while music is active"
                            } else {
                                "Requires Android display-over-other-apps permission"
                            },
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    Switch(
                        checked =
                            islandState.floatingEnabled,
                        onCheckedChange =
                            { enabled ->
                                viewModel
                                    .setFloatingIslandEnabled(
                                        enabled
                                    )

                                if (
                                    enabled &&
                                    !islandState
                                        .overlayPermissionGranted
                                ) {
                                    val intent =
                                        Intent(
                                            AndroidSettings
                                                .ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse(
                                                "package:${context.packageName}"
                                            )
                                        )

                                    overlayPermissionLauncher
                                        .launch(intent)
                                }
                            }
                    )
                }

                if (
                    !islandState
                        .overlayPermissionGranted
                ) {
                    OutlinedButton(
                        onClick = {
                            overlayPermissionLauncher
                                .launch(
                                    Intent(
                                        AndroidSettings
                                            .ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse(
                                            "package:${context.packageName}"
                                        )
                                    )
                                )
                        },
                        colors =
                            ButtonDefaults
                                .outlinedButtonColors(
                                    contentColor =
                                        Color.White
                                )
                    ) {
                        Text(
                            "Allow floating overlay"
                        )
                    }
                }
            }
        }

        GlassPanel(
            Modifier.fillMaxWidth(),
            radius = 28.dp,
            strong = true
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Player gestures",
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                    )
                    Text(
                        if (isTv) {
                            "Touch gestures are disabled on Android TV."
                        } else {
                            "Swipe left/right for next/previous and swipe down to close the full player."
                        },
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                Switch(
                    checked =
                        playerGesturesEnabled,
                    enabled =
                        !isTv,
                    onCheckedChange =
                        { enabled ->
                            playerGesturesEnabled =
                                enabled
                            PlayerGesturePreferences
                                .setEnabled(
                                    context,
                                    enabled
                                )
                        }
                )
            }
        }

        GlassPanel(
            Modifier.fillMaxWidth(),
            radius = 28.dp,
            strong = true
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    )
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(
                            "Components & Updates",
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                        )
                        Text(
                            "Runtime components update in place; compiled libraries require a Frxe update.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }

                StatusRow(
                    "Frxe",
                    BuildConfig.VERSION_NAME
                )
                StatusRow(
                    "App release",
                    state.appStatus
                )
                state.latestAppVersion
                    ?.let {
                        StatusRow(
                            "Latest detected",
                            it
                        )
                    }

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    Button(
                        onClick =
                            viewModel::checkForAppUpdate,
                        enabled =
                            !state.checkingApp,
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        Color.White
                                            .copy(
                                                alpha = 0.16f
                                            )
                                )
                    ) {
                        if (state.checkingApp) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.height(
                                        18.dp
                                    ),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null
                            )
                        }
                        Text("  Check release")
                    }

                    state.releasePageUrl
                        ?.let { page ->
                            OutlinedButton(
                                onClick = {
                                    openExternal(
                                        context,
                                        page
                                    )
                                },
                                colors =
                                    ButtonDefaults
                                        .outlinedButtonColors(
                                            contentColor =
                                                Color.White
                                        )
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null
                                )
                                Text("  Releases")
                            }
                        }
                }

                Spacer(
                    Modifier.height(4.dp)
                )

                Text(
                    "yt-dlp runtime",
                    fontWeight =
                        FontWeight.SemiBold,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                )
                Text(
                    "Stable channel · checks automatically at most once every 24 hours without blocking playback.",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                StatusRow(
                    "Installed",
                    runtimeHealth
                        .ytDlpVersion
                        ?: "Unknown"
                )
                StatusRow(
                    "Initialization",
                    runtimeHealth
                        .ytDlpInitStatus
                )
                StatusRow(
                    "Stable update",
                    runtimeHealth
                        .ytDlpUpdateStatus
                )
                StatusRow(
                    "Last check",
                    formatTimestamp(
                        runtimeHealth
                            .lastYtDlpUpdateAttemptMs
                    )
                )

                OutlinedButton(
                    onClick = {
                        if (!updatingRuntime) {
                            scope.launch {
                                updatingRuntime = true
                                try {
                                    YtDlpRuntimeUpdater
                                        .updateNow(
                                            context
                                        )
                                } finally {
                                    updatingRuntime = false
                                }
                            }
                        }
                    },
                    enabled =
                        !updatingRuntime,
                    colors =
                        ButtonDefaults
                            .outlinedButtonColors(
                                contentColor =
                                    Color.White
                            )
                ) {
                    if (updatingRuntime) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.height(
                                    18.dp
                                ),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null
                        )
                    }
                    Text(
                        "  Check/update runtime now"
                    )
                }
            }
        }

        GlassPanel(
            Modifier.fillMaxWidth(),
            radius = 28.dp,
            strong = true
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Column(
                        Modifier.weight(1f)
                    ) {
                        Text(
                            "Compiled dependencies",
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                        )
                        Text(
                            "Checks Media3, Compose, Room, NewPipeExtractor, FFmpegKit and other libraries. Android cannot safely hot-swap these inside an installed APK.",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick =
                        viewModel::checkDependencyReleases,
                    enabled =
                        !state.checkingDependencies,
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    Color.White
                                        .copy(
                                            alpha = 0.16f
                                        )
                            )
                ) {
                    if (
                        state.checkingDependencies
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier.height(
                                    18.dp
                                ),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null
                        )
                    }
                    Text(
                        "  Check dependency updates"
                    )
                }

                state.dependencies
                    .forEach { dependency ->
                        val status =
                            when {
                                dependency.error != null ->
                                    dependency.error

                                dependency.latestVersion == null ->
                                    "No latest version returned"

                                dependency.updateAvailable &&
                                    dependency.updateMode ==
                                    DependencyUpdateMode
                                        .RequiresAppUpdate ->
                                    "${dependency.currentVersion} → ${dependency.latestVersion} · Requires Frxe update"

                                dependency.updateAvailable ->
                                    "${dependency.currentVersion} → ${dependency.latestVersion}"

                                else ->
                                    "${dependency.currentVersion} · current"
                            }

                        StatusRow(
                            dependency.name,
                            status
                        )
                    }
            }
        }

        GlassPanel(
            Modifier.fillMaxWidth(),
            radius = 28.dp,
            strong = true
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                Text(
                    "Resolver diagnostics",
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                )

                StatusRow(
                    "Last success",
                    runtimeHealth
                        .lastSuccessfulResolver
                        ?: "None yet"
                )
                StatusRow(
                    "Success time",
                    formatTimestamp(
                        runtimeHealth
                            .lastResolverSuccessMs
                    )
                )

                if (
                    runtimeHealth
                        .recentResolverFailures
                        .isEmpty()
                ) {
                    Text(
                        "No recent resolver failures recorded.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                } else {
                    Text(
                        "Recent failures",
                        fontWeight =
                            FontWeight.SemiBold,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                    )

                    runtimeHealth
                        .recentResolverFailures
                        .takeLast(5)
                        .forEach { failure ->
                            Text(
                                "• $failure",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }
                }
            }
        }

        GlassPanel(
            Modifier.fillMaxWidth(),
            radius = 28.dp,
            strong = true
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                Text(
                    "Support Frxe",
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                )

                Text(
                    "Support void or follow Frxe development.",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    Button(
                        onClick = {
                            openExternal(
                                context,
                                FrxeSupportLinks.KO_FI
                            )
                        },
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        Color.White
                                            .copy(
                                                alpha = 0.16f
                                            )
                                )
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null
                        )
                        Text("  Ko-fi")
                    }

                    OutlinedButton(
                        onClick = {
                            openExternal(
                                context,
                                FrxeSupportLinks.GITHUB
                            )
                        },
                        colors =
                            ButtonDefaults
                                .outlinedButtonColors(
                                    contentColor =
                                        Color.White
                                )
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null
                        )
                        Text("  GitHub")
                    }
                }

                Text(
                    "ko-fi.com/voidnont · github.com/voidnont/frxe",
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }

        Spacer(
            Modifier.height(12.dp)
        )
    }
}

private fun openExternal(
    context: android.content.Context,
    url: String
) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    }
}

private fun formatTimestamp(
    timestampMs: Long
): String =
    if (timestampMs <= 0L) {
        "Never"
    } else {
        DateFormat
            .getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
            )
            .format(
                Date(timestampMs)
            )
    }

@Composable
private fun StatusRow(
    label: String,
    value: String
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {
        Text(
            label,
            modifier =
                Modifier.weight(0.42f),
            fontWeight =
                FontWeight.SemiBold,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurface
        )
        Text(
            value,
            modifier =
                Modifier.weight(0.58f),
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}
