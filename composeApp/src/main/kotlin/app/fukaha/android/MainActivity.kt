package app.fukaha.android

import app.fukaha.applyChangesFrom

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fukaha.AppRelease
import app.fukaha.AppTheme
import app.fukaha.AppUpdateChecker
import app.fukaha.AppUpdatePolicy
import app.fukaha.BuildConfig
import app.fukaha.EmbedHealthSnapshot
import app.fukaha.FukahaSettings
import app.fukaha.PlatformClock
import app.fukaha.R
import app.fukaha.UpdateCheckResult
import app.fukaha.android.components.LanguageMenuButton
import app.fukaha.android.components.ThemeCycleButton
import app.fukaha.android.onboarding.TutorialScreen
import app.fukaha.android.settings.SettingsScreen
import app.fukaha.android.settings.UpdateAvailableDialog
import app.fukaha.android.theme.FukahaTheme
import app.fukaha.android.theme.applyWindowSurface
import app.fukaha.android.update.ApkUpdateUiState
import app.fukaha.android.update.ApkUpdater
import app.fukaha.fukaha
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application.fukaha()
        val animatorDurationScale = runCatching {
            android.provider.Settings.Global.getFloat(
                contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)

        setContent {
            var settings by remember { mutableStateOf(FukahaSettings()) }
            var settingsLoaded by remember { mutableStateOf(false) }
            var tutorialOpen by remember { mutableStateOf(false) }
            var pendingRelease by remember { mutableStateOf<AppRelease?>(null) }
            var updateChecking by remember { mutableStateOf(false) }
            var languageChangeInFlight by remember { mutableStateOf(false) }
            var apkUpdateState by remember { mutableStateOf<ApkUpdateUiState>(ApkUpdateUiState.Idle) }
            var apkUpdateJob by remember { mutableStateOf<Job?>(null) }
            val scope = rememberCoroutineScope()
            val snackbar = remember { SnackbarHostState() }
            val uriHandler = LocalUriHandler.current
            val updateChecker = remember { AppUpdateChecker() }

            fun cancelApkUpdate() {
                apkUpdateJob?.cancel()
                apkUpdateJob = null
                apkUpdateState = ApkUpdateUiState.Idle
            }

            suspend fun installRelease(release: AppRelease) {
                val apkUrl = release.apkUrl
                if (apkUrl.isNullOrBlank()) {
                    uriHandler.openUri(release.htmlUrl)
                    pendingRelease = null
                    return
                }
                try {
                    apkUpdateState = ApkUpdateUiState.Downloading(0f)
                    val apk = ApkUpdater.download(this@MainActivity, release) { progress ->
                        apkUpdateState = ApkUpdateUiState.Downloading(progress)
                    }
                    apkUpdateState = ApkUpdateUiState.Installing
                    withContext(Dispatchers.IO) {
                        ApkUpdater.install(this@MainActivity, apk)
                    }
                    apkUpdateState = ApkUpdateUiState.Idle
                    pendingRelease = null
                } catch (e: CancellationException) {
                    apkUpdateState = ApkUpdateUiState.Idle
                    throw e
                } catch (e: Exception) {
                    apkUpdateState = ApkUpdateUiState.Failed(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.update_failed),
                    )
                }
            }

            val unknownSourcesLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {
                val release = pendingRelease ?: return@rememberLauncherForActivityResult
                if (ApkUpdater.canRequestInstalls(this@MainActivity)) {
                    apkUpdateJob?.cancel()
                    apkUpdateJob = scope.launch { installRelease(release) }
                }
            }

            fun startInstall(release: AppRelease) {
                if (release.apkUrl.isNullOrBlank()) {
                    uriHandler.openUri(release.htmlUrl)
                    pendingRelease = null
                    return
                }
                if (!ApkUpdater.canRequestInstalls(this@MainActivity)) {
                    scope.launch {
                        snackbar.showSnackbar(getString(R.string.update_allow_unknown))
                    }
                    unknownSourcesLauncher.launch(ApkUpdater.unknownSourcesIntent(this@MainActivity))
                    return
                }
                apkUpdateJob?.cancel()
                apkUpdateJob = scope.launch { installRelease(release) }
            }
            val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

            fun persist(next: FukahaSettings) {
                val before = settings
                val languageChanged = next.language != settings.language
                if (languageChanged && languageChangeInFlight) return
                if (languageChanged) languageChangeInFlight = true
                settings = next
                scope.launch {
                    app.settingsStore.update { it.applyChangesFrom(before, next) }
                    if (languageChanged) {
                        // Let the old content begin fading before AppCompat performs its usual
                        // locale recreation. A zero system animation scale skips this entirely.
                        if (animatorDurationScale > 0f) {
                            delay((140f * animatorDurationScale.coerceAtMost(2f)).toLong())
                        }
                        withContext(Dispatchers.Main.immediate) {
                            LocaleHelper.apply(next.language)
                        }
                        // Usually locale application recreates the activity. This also releases
                        // the guard on implementations that update resources in place.
                        delay(450)
                        languageChangeInFlight = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                settings = app.settingsStore.get()
                if (settings.deleteCacheAfterShare) {
                    withContext(Dispatchers.IO) { MediaCache.deleteExpired(File(cacheDir, "fukaha")) }
                }
                LocaleHelper.apply(settings.language)
                settingsLoaded = true
                // Opening the app is the only moment the 6h check may kick in, so a
                // share never waits on probes.
                app.healthController.startAutoIfDue()
            }

            suspend fun runUpdateCheck(manual: Boolean) {
                if (BuildConfig.IS_TESTING) {
                    if (manual) uriHandler.openUri("https://github.com/Shenepoy/Fukaha/actions/workflows/testing.yml")
                    return
                }
                if (updateChecking) return
                updateChecking = true
                val result = try {
                    val checked = withContext(Dispatchers.IO) {
                        updateChecker.check(BuildConfig.VERSION_NAME)
                    }
                    val checkedAt = PlatformClock.epochMillis()
                    app.settingsStore.update { it.copy(lastUpdateCheckEpochMs = checkedAt) }
                    settings = settings.copy(lastUpdateCheckEpochMs = checkedAt)
                    checked
                } finally {
                    updateChecking = false
                }
                when (result) {
                    is UpdateCheckResult.Available -> {
                        if (manual || AppUpdatePolicy.shouldPrompt(result.release, settings.skippedUpdateVersion)) {
                            cancelApkUpdate()
                            pendingRelease = result.release
                        }
                    }
                    is UpdateCheckResult.UpToDate -> if (manual) {
                        snackbar.showSnackbar(getString(R.string.update_up_to_date))
                    }
                    is UpdateCheckResult.Failed -> if (manual) {
                        snackbar.showSnackbar(getString(R.string.update_check_failed))
                    }
                }
            }

            LaunchedEffect(
                settingsLoaded,
                settings.onboardingCompleted,
                settings.checkUpdatesOnLaunch,
                tutorialOpen,
            ) {
                if (!settingsLoaded || !settings.onboardingCompleted || !settings.checkUpdatesOnLaunch) {
                    return@LaunchedEffect
                }
                if (tutorialOpen) return@LaunchedEffect
                if (!AppUpdatePolicy.isLaunchCheckDue(settings.lastUpdateCheckEpochMs)) {
                    return@LaunchedEffect
                }
                runUpdateCheck(manual = false)
            }

            FukahaTheme(theme = settings.theme) {
                // Wait for the stored flag so upgrading users never flash the tour.
                val firstRun = settingsLoaded && !settings.onboardingCompleted
                if (firstRun || tutorialOpen) {
                    TutorialScreen(
                        firstRun = firstRun,
                        language = settings.language,
                        theme = settings.theme,
                        onLanguageSelect = { persist(settings.copy(language = it)) },
                        onThemeSelect = { persist(settings.copy(theme = it)) },
                        onFinish = {
                            tutorialOpen = false
                            if (!settings.onboardingCompleted) {
                                persist(settings.copy(onboardingCompleted = true))
                            }
                        },
                    )
                } else {
                    val barColors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        containerColor = MaterialTheme.colorScheme.surface,
                        topBar = {
                            TopAppBar(
                                title = { FukahaBrandTitle() },
                                actions = {
                                    IconButton(onClick = { tutorialOpen = true }) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.HelpOutline,
                                            contentDescription = stringResource(R.string.help),
                                        )
                                    }
                                    LanguageMenuButton(
                                        language = settings.language,
                                        onSelect = { persist(settings.copy(language = it)) },
                                    )
                                    ThemeCycleButton(
                                        theme = settings.theme,
                                        onSelect = { persist(settings.copy(theme = it)) },
                                    )
                                },
                                expandedHeight = 96.dp,
                                scrollBehavior = scrollBehavior,
                                colors = barColors,
                            )
                        },
                        snackbarHost = { SnackbarHost(snackbar) },
                    ) { padding ->
                        AnimatedContent(
                            targetState = LocaleHelper.resolve(settings.language),
                            transitionSpec = {
                                (
                                    fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                        slideInHorizontally(
                                            animationSpec = tween(360, easing = FastOutSlowInEasing),
                                            initialOffsetX = { it / 24 },
                                        )
                                    ).togetherWith(
                                    fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                                        slideOutHorizontally(
                                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                                            targetOffsetX = { -it / 32 },
                                        ),
                                )
                            },
                            label = "localeContent",
                        ) { resolvedLanguage ->
                            key(resolvedLanguage) {
                                SettingsTab(
                                    controller = app.healthController,
                                    padding = padding,
                                    settings = settings,
                                    onChange = { persist(it) },
                                    onClearCache = {
                                        File(cacheDir, "fukaha").listFiles()?.forEach { it.delete() }
                                        scope.launch {
                                            snackbar.showSnackbar(getString(R.string.cache_cleared))
                                        }
                                    },
                                    onOpenTutorial = { tutorialOpen = true },
                                    onCheckUpdates = {
                                        scope.launch { runUpdateCheck(manual = true) }
                                    },
                                    updateChecking = updateChecking,
                                )
                            }
                        }
                    }
                    pendingRelease?.let { release ->
                        UpdateAvailableDialog(
                            release = release,
                            state = apkUpdateState,
                            onUpdate = { startInstall(release) },
                            onCancelDownload = { cancelApkUpdate() },
                            onViewRelease = {
                                cancelApkUpdate()
                                uriHandler.openUri(release.htmlUrl)
                                pendingRelease = null
                            },
                            onLater = {
                                cancelApkUpdate()
                                pendingRelease = null
                            },
                            onSkip = {
                                cancelApkUpdate()
                                persist(settings.copy(skippedUpdateVersion = release.version))
                                pendingRelease = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FukahaBrandTitle() {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactLayout = screenWidthDp <= 380
    val wideLayout = screenWidthDp > 520
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            when {
                wideLayout -> 12.dp
                compactLayout -> 8.dp
                else -> 10.dp
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_fukaha_brand),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(
                when {
                    wideLayout -> 48.dp
                    compactLayout -> 40.dp
                    else -> 44.dp
                },
            ),
        )
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            style = when {
                wideLayout -> MaterialTheme.typography.displaySmall
                compactLayout -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Owns the embedder-health state so a probe run, which reports progress every few
 * hundred milliseconds, only recomposes the settings list and not the app shell.
 */
@Composable
private fun SettingsTab(
    controller: EmbedHealthController,
    padding: PaddingValues,
    settings: FukahaSettings,
    onChange: (FukahaSettings) -> Unit,
    onClearCache: () -> Unit,
    onOpenTutorial: () -> Unit,
    onCheckUpdates: () -> Unit,
    updateChecking: Boolean,
) {
    val health by controller.observeSnapshot().collectAsState(initial = EmbedHealthSnapshot())
    val checking by controller.inProgress.collectAsState()
    val progress by controller.progress.collectAsState()
    val unreachable by controller.lastRunUnreachable.collectAsState()

    SettingsScreen(
        padding = padding,
        settings = settings,
        onChange = onChange,
        onClearCache = onClearCache,
        health = health,
        healthChecking = checking,
        healthProgress = progress,
        healthUnreachable = unreachable,
        onRefreshHealth = controller::refresh,
        onOpenTutorial = onOpenTutorial,
        onCheckUpdates = onCheckUpdates,
        updateChecking = updateChecking,
    )
}
