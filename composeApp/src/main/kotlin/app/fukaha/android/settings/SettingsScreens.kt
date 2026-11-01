package app.fukaha.android.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import app.fukaha.android.components.dismissFocusOnOutsideTap
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.fukaha.BuildConfig
import app.fukaha.EmbedCatalog
import app.fukaha.EmbedHealthPolicy
import app.fukaha.EmbedHealthProgress
import app.fukaha.EmbedHealthSnapshot
import app.fukaha.EmbedHealthStatus
import app.fukaha.FukahaSettings
import app.fukaha.R
import app.fukaha.ShareAction
import app.fukaha.UrlCleaner
import app.fukaha.android.ShareActivity
import app.fukaha.android.components.SettingsSection
import app.fukaha.android.theme.asLtrUrl
import app.fukaha.android.theme.onSelectedBadgeGold
import app.fukaha.android.theme.selectedBadgeGold
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TEST_SHARE_URL =
    "https://x.com/makkahregion/status/1902619525532512361" +
        "?utm_source=share&utm_medium=android_app&fbclid=IwAR0fukaha_test"

/** Credited project name paired with the URL it links to. */
private val CREDIT_SOURCES: List<Pair<Int, Int>> = listOf(
    R.string.credits_lexedia to R.string.credits_lexedia_url,
    R.string.credits_fixtweetbot to R.string.credits_fixtweetbot_url,
    R.string.credits_mohsreg to R.string.credits_mohsreg_url,
    R.string.credits_meqativ to R.string.credits_meqativ_url,
    R.string.credits_postrediori to R.string.credits_postrediori_url,
    R.string.credits_embedfixer to R.string.credits_embedfixer_url,
)

/** One settings row, resolved from the catalog plus the current health snapshot. */
private data class FixerRowState(
    val platformKey: String,
    val platformName: String,
    /** Null when no service could be resolved; the caller supplies the label. */
    val serviceName: String?,
    val host: String,
    val healthStatus: EmbedHealthStatus,
    val infoUrl: String?,
)

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    settings: FukahaSettings,
    onChange: (FukahaSettings) -> Unit,
    onClearCache: () -> Unit,
    health: EmbedHealthSnapshot = EmbedHealthSnapshot(),
    healthChecking: Boolean = false,
    healthProgress: EmbedHealthProgress? = null,
    healthUnreachable: Boolean = false,
    onRefreshHealth: () -> Unit = {},
    onOpenTutorial: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    updateChecking: Boolean = false,
) {
    val context = LocalContext.current
    val catalog = remember { EmbedCatalog.bundled() }
    var fixerPlatform by remember { mutableStateOf<String?>(null) }
    var cobaltExpanded by remember { mutableStateOf(false) }
    // Hoisted out of the lazy item so a typed link survives scrolling it off screen.
    var linkInput by rememberSaveable { mutableStateOf("") }
    val platforms = remember {
        catalog.platformKeys().filter { catalog.activeServices(it).isNotEmpty() }
    }
    // Derived once per settings/health change instead of on every recomposition, so
    // the per-host progress ticks during a probe run do not redo ~19 lookups each.
    val fixerRows = remember(platforms, settings.preferredFixers, health.statuses) {
        platforms.map { key ->
            val chosenHost = settings.preferredFixers[key]
                ?: catalog.defaultFixerHost(key).orEmpty()
            // Match link rewriting: health only changes automatic selections.
            val inUse = catalog.effectiveService(key, settings.preferredFixers[key], health.statuses)
            val currentHost = inUse?.normalizedHost() ?: chosenHost
            FixerRowState(
                platformKey = key,
                platformName = catalog.platform(key)?.name ?: key,
                serviceName = inUse?.name,
                host = currentHost,
                healthStatus = health.statusOf(currentHost),
                infoUrl = inUse?.repo?.takeIf { it.isNotBlank() }
                    ?: currentHost.takeIf { it.startsWith("http") },
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 4.dp,
            end = 16.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            QuickLinkSection(
                value = linkInput,
                onValueChange = { linkInput = it },
                onOpen = { context.openShareScreen(it) },
                onOpenSample = { context.openShareScreen(TEST_SHARE_URL) },
            )
        }

        item {
            SettingsSection(title = stringResource(R.string.default_action)) {
                Text(
                    text = stringResource(R.string.default_action_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DefaultActionPicker(
                    settings = settings,
                    onChange = onChange,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.preferred_fixers)) {
                EmbedHealthRow(
                    health = health,
                    checking = healthChecking,
                    progress = healthProgress,
                    unreachable = healthUnreachable,
                    onRefresh = onRefreshHealth,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val unknownFixer = stringResource(R.string.preferred_fixer_unknown)
                fixerRows.forEachIndexed { index, row ->
                    PreferredFixerRow(
                        platformName = row.platformName,
                        serviceName = row.serviceName ?: unknownFixer,
                        host = row.host,
                        healthStatus = row.healthStatus,
                        infoUrl = row.infoUrl,
                        onClick = { fixerPlatform = row.platformKey },
                    )
                    if (index != fixerRows.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        item {
            AboutContent(
                onOpenTutorial = onOpenTutorial,
                onCheckUpdates = onCheckUpdates,
                updateChecking = updateChecking,
            )
        }

        item {
            SettingsSection(title = stringResource(R.string.section_sharing)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.resolve_short_links)) },
                    supportingContent = { Text(stringResource(R.string.resolve_short_links_hint)) },
                    leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = settings.resolveShortLinks,
                            onCheckedChange = { onChange(settings.copy(resolveShortLinks = it)) },
                        )
                    },
                    colors = transparentListColors(),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.delete_cache)) },
                    supportingContent = { Text(stringResource(R.string.delete_cache_hint)) },
                    leadingContent = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = settings.deleteCacheAfterShare,
                            onCheckedChange = { onChange(settings.copy(deleteCacheAfterShare = it)) },
                        )
                    },
                    colors = transparentListColors(),
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.section_updates)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.check_updates_on_launch)) },
                    supportingContent = { Text(stringResource(R.string.check_updates_on_launch_hint)) },
                    leadingContent = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                    trailingContent = {
                        androidx.compose.material3.Switch(
                            checked = settings.checkUpdatesOnLaunch,
                            onCheckedChange = { onChange(settings.copy(checkUpdatesOnLaunch = it)) },
                        )
                    },
                    colors = transparentListColors(),
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val expandLabel = stringResource(
                    if (cobaltExpanded) {
                        R.string.section_cobalt_collapse
                    } else {
                        R.string.section_cobalt_expand
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { cobaltExpanded = !cobaltExpanded }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .semantics { contentDescription = expandLabel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.section_cobalt),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (cobaltExpanded) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (!cobaltExpanded) {
                            ListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.section_cobalt_collapsed_hint))
                                },
                                modifier = Modifier.clickable { cobaltExpanded = true },
                                colors = transparentListColors(),
                            )
                        }
                        AnimatedVisibility(visible = cobaltExpanded) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    text = stringResource(R.string.cobalt_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                                OutlinedTextField(
                                    value = settings.cobaltBaseUrl,
                                    onValueChange = { url ->
                                        val next = settings.copy(cobaltBaseUrl = url)
                                        onChange(next.withDownloadClamped())
                                    },
                                    modifier = Modifier.fillMaxWidth().dismissFocusOnOutsideTap(),
                                    textStyle = MaterialTheme.typography.bodyLarge.asLtrUrl(),
                                    label = { Text(stringResource(R.string.cobalt_url)) },
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.cobalt_url_placeholder),
                                            style = MaterialTheme.typography.bodyLarge.asLtrUrl(),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Storage, contentDescription = null)
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = settings.cobaltApiKey,
                                    onValueChange = { onChange(settings.copy(cobaltApiKey = it)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                        .dismissFocusOnOutsideTap(),
                                    label = { Text(stringResource(R.string.cobalt_api_key)) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Key, contentDescription = null)
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.section_storage)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_cache)) },
                    supportingContent = { Text(stringResource(R.string.clear_cache_hint)) },
                    leadingContent = {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onClearCache),
                    colors = transparentListColors(),
                )
            }
        }
    }

    fixerPlatform?.let { key ->
        val services = catalog.activeServices(key)
        val chosenHost = settings.preferredFixers[key]
            ?: catalog.defaultFixerHost(key).orEmpty()
        // Mark the fixer the row shows, which is the one links go through.
        val selectedHost = catalog.effectiveService(key, settings.preferredFixers[key], health.statuses)
            ?.normalizedHost()
            ?: chosenHost
        PreferredFixerPickerSheet(
            platformName = catalog.platform(key)?.name ?: key,
            services = services,
            selectedHost = selectedHost,
            health = health,
            onSelect = { service ->
                onChange(
                    settings.copy(
                        preferredFixers = settings.preferredFixers +
                            (key to service.normalizedHost()),
                    ),
                )
            },
            onDismiss = { fixerPlatform = null },
        )
    }
}

/**
 * One-line embedder check: title, a single status line, and a refresh button that
 * stays disabled for an hour after a run that actually reached hosts.
 */
@Composable
private fun EmbedHealthRow(
    health: EmbedHealthSnapshot,
    checking: Boolean,
    progress: EmbedHealthProgress?,
    unreachable: Boolean,
    onRefresh: () -> Unit,
) {
    val checkedAt = health.checkedAtEpochMs
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Re-enable the button on its own when the cooldown runs out.
    LaunchedEffect(checkedAt, checking) {
        while (EmbedHealthPolicy.cooldownRemainingMs(checkedAt, System.currentTimeMillis()) > 0L) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    val remainingMs = EmbedHealthPolicy.cooldownRemainingMs(checkedAt, now)
    val status = when {
        checking -> progress?.takeIf { it.total > 0 }?.let {
            stringResource(R.string.embed_health_progress, it.currentIndex, it.total)
        } ?: stringResource(R.string.embed_health_checking)
        unreachable -> stringResource(R.string.embed_health_offline)
        checkedAt == null -> stringResource(R.string.embed_health_never)
        else -> {
            val counts = stringResource(
                R.string.embed_health_counts,
                health.aliveCount,
                health.deadCount,
                health.unknownCount,
            )
            val trailing = if (remainingMs > 0L) {
                val minutes = ((remainingMs + 59_999L) / 60_000L).toInt()
                stringResource(R.string.embed_health_cooldown, minutes)
            } else {
                timeFormat.format(Date(checkedAt))
            }
            "$counts · $trailing"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.embed_health_refresh),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (unreachable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (checking) {
                LinearProgressIndicator(
                    progress = { progress?.fraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 4.dp),
                )
            }
        }
        val context = LocalContext.current
        val blockedReason = when {
            checking -> stringResource(R.string.embed_health_busy_toast)
            remainingMs > 0L -> stringResource(
                R.string.embed_health_cooldown_toast,
                ((remainingMs + 59_999L) / 60_000L).toInt(),
            )
            else -> null
        }
        // Kept tappable while greyed out so a blocked tap can explain itself.
        IconButton(
            onClick = {
                if (blockedReason == null) {
                    onRefresh()
                } else {
                    Toast.makeText(context, blockedReason, Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.embed_health_refresh),
                tint = if (blockedReason == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

/**
 * Lets the user run a link through Fukaha without sharing into it from another app.
 * The pasted text is handed to [ShareActivity] exactly like a system share would,
 * so the saved default action is applied consistently.
 */
@Composable
private fun QuickLinkSection(
    value: String,
    onValueChange: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenSample: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardEmpty = stringResource(R.string.quick_use_clipboard_empty)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareable = remember(value) { shareableLink(value) }
    val showInvalid = value.isNotBlank() && shareable == null
    val appDirection = LocalLayoutDirection.current

    SettingsSection(title = stringResource(R.string.section_quick_use)) {
        // The field carries a URL, so Arabic gets the same left-to-right row as English:
        // sample and link icon, the link itself, then paste; share only after a real URL.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .dismissFocusOnOutsideTap(),
                textStyle = MaterialTheme.typography.bodyLarge.asLtrUrl(),
                placeholder = {
                    Text(
                        stringResource(R.string.quick_use_field_placeholder),
                        style = MaterialTheme.typography.bodyLarge.asLtrUrl(),
                    )
                },
                leadingIcon = {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Sample and paste only help while there is nothing to act on.
                        if (value.isEmpty()) {
                            QuickLinkAction(
                                icon = Icons.Outlined.Science,
                                label = stringResource(R.string.quick_use_sample),
                                onClick = onOpenSample,
                            )
                        }
                        Icon(Icons.Outlined.Link, contentDescription = null)
                    }
                },
                trailingIcon = {
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (value.isEmpty()) {
                            QuickLinkAction(
                                icon = Icons.Outlined.ContentPaste,
                                label = stringResource(R.string.quick_use_paste),
                                onClick = {
                                    scope.launch {
                                        val pasted = clipboard.getClipEntry()?.clipData
                                            ?.takeIf { it.itemCount > 0 }
                                            ?.getItemAt(0)
                                            ?.coerceToText(context)
                                            ?.toString()
                                        if (pasted.isNullOrBlank()) {
                                            Toast.makeText(
                                                context,
                                                clipboardEmpty,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            onValueChange(pasted.trim())
                                        }
                                    }
                                },
                            )
                        } else {
                            QuickLinkAction(
                                icon = Icons.Outlined.Close,
                                label = stringResource(R.string.quick_use_clear),
                                onClick = { onValueChange("") },
                            )
                        }
                        if (shareable != null) {
                            QuickLinkAction(
                                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                label = stringResource(R.string.quick_use_open),
                                filled = true,
                                onClick = { onOpen(shareable) },
                            )
                        }
                    }
                },
                // Always filled so swapping in the error keeps the row at one height.
                supportingText = {
                    // Prose rather than a URL, so it still reads in the app's direction.
                    CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
                        Text(
                            text = if (showInvalid) {
                                stringResource(R.string.quick_use_invalid)
                            } else {
                                stringResource(R.string.quick_use_hint)
                            },
                        )
                    }
                },
                isError = showInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { shareable?.let(onOpen) }),
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

/** In-field button, sized near the 48.dp touch target while still leaving room for two. */
@Composable
private fun QuickLinkAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
    }
    if (filled) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            enabled = enabled,
            content = { content() },
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            enabled = enabled,
            content = { content() },
        )
    }
}

private fun Context.openShareScreen(text: String) {
    startActivity(
        Intent(this, ShareActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        },
    )
}

/** Matches a scheme-less host with an optional path, e.g. `x.com/user/status/1`. */
private val bareHostRegex = Regex("""[\w-]+(\.[\w-]+)+(/\S*)?""")

/**
 * Returns text that [ShareActivity] can resolve to a link, or null when the input is
 * not usable yet. Typed input often lacks a scheme, so a bare host gets `https://`.
 */
internal fun shareableLink(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (UrlCleaner.extractFirstUrl(trimmed) != null) return trimmed
    val firstToken = trimmed.substringBefore(' ')
    return if (bareHostRegex.matches(firstToken)) "https://$firstToken" else null
}

@Composable
internal fun DefaultActionPicker(
    settings: FukahaSettings,
    onChange: (FukahaSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadEnabled = settings.hasValidCobaltBaseUrl
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ShareAction.entries.forEach { action ->
            val enabled = action != ShareAction.Download || downloadEnabled
            DefaultActionOption(
                action = action,
                selected = settings.defaultAction == action && enabled,
                enabled = enabled,
                hintOverride = if (action == ShareAction.Download && !downloadEnabled) {
                    stringResource(R.string.action_download_disabled_hint)
                } else {
                    null
                },
                onClick = { onChange(settings.copy(defaultAction = action)) },
            )
        }
    }
}

@Composable
private fun AboutContent(
    onOpenTutorial: () -> Unit,
    onCheckUpdates: () -> Unit = {},
    updateChecking: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val siteUrl = stringResource(R.string.developer_site_url)
    val githubUrl = stringResource(R.string.github_url)
    val donateUrl = stringResource(R.string.donate_url)
    val versionLabel = stringResource(R.string.version_value, BuildConfig.VERSION_NAME)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSection(title = stringResource(R.string.app_name)) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.app_name))
                },
                supportingContent = { Text(stringResource(R.string.about_body)) },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                colors = transparentListColors(),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.tutorial_open)) },
                supportingContent = { Text(stringResource(R.string.tutorial_open_hint)) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenTutorial),
                colors = transparentListColors(),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.version)) },
                supportingContent = {
                    Text(
                        if (updateChecking) {
                            stringResource(R.string.check_for_updates_checking)
                        } else {
                            versionLabel
                        },
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                trailingContent = {
                    IconButton(
                        onClick = onCheckUpdates,
                        enabled = !updateChecking,
                    ) {
                        if (updateChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.check_for_updates),
                            )
                        }
                    }
                },
                colors = transparentListColors(),
            )
        }
        SettingsSection(title = stringResource(R.string.developer_title)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.developer_name)) },
                supportingContent = { Text(stringResource(R.string.developer_site)) },
                leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.developer_site),
                    )
                },
                modifier = Modifier.clickable { uriHandler.openUri(siteUrl) },
                colors = transparentListColors(),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.github_title)) },
                supportingContent = { Text(stringResource(R.string.github_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.github_title),
                    )
                },
                modifier = Modifier.clickable { uriHandler.openUri(githubUrl) },
                colors = transparentListColors(),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.donate)) },
                supportingContent = { Text(stringResource(R.string.donate_subtitle)) },
                leadingContent = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.donate),
                    )
                },
                modifier = Modifier.clickable { uriHandler.openUri(donateUrl) },
                colors = transparentListColors(),
            )
        }
        SettingsSection(title = stringResource(R.string.credits_title)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.credits_headline)) },
                supportingContent = { Text(stringResource(R.string.credits)) },
                leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
                colors = transparentListColors(),
            )
            CREDIT_SOURCES.forEach { (labelRes, urlRes) ->
                val label = stringResource(labelRes)
                val url = stringResource(urlRes)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = label,
                        )
                    },
                    modifier = Modifier.clickable { uriHandler.openUri(url) },
                    colors = transparentListColors(),
                )
            }
        }
    }
}

@Composable
private fun actionLabel(action: ShareAction): String = when (action) {
    ShareAction.Ask -> stringResource(R.string.action_ask)
    ShareAction.Clean -> stringResource(R.string.action_clean)
    ShareAction.Embed -> stringResource(R.string.action_embed)
    ShareAction.Download -> stringResource(R.string.action_download)
}

@Composable
private fun actionHint(action: ShareAction): String = when (action) {
    ShareAction.Ask -> stringResource(R.string.action_ask_hint)
    ShareAction.Clean -> stringResource(R.string.action_clean_hint)
    ShareAction.Embed -> stringResource(R.string.action_embed_hint)
    ShareAction.Download -> stringResource(R.string.action_download_hint)
}

@Composable
private fun DefaultActionOption(
    action: ShareAction,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    hintOverride: String? = null,
) {
    val icon = when (action) {
        ShareAction.Ask -> Icons.Outlined.TouchApp
        ShareAction.Clean -> Icons.Outlined.CleaningServices
        ShareAction.Embed -> Icons.Outlined.Visibility
        ShareAction.Download -> Icons.Outlined.Download
    }
    val selectionMotion = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "defaultActionContainer",
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "defaultActionBorder",
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.992f,
        animationSpec = selectionMotion,
        label = "defaultActionScale",
    )
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val hintColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.72f)
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (enabled) border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actionLabel(action),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hintOverride ?: actionHint(action),
                    style = MaterialTheme.typography.bodySmall,
                    color = hintColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (selected) {
                Surface(
                    color = selectedBadgeGold,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = stringResource(R.string.fixer_selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSelectedBadgeGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun transparentListColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
fun HealthStatusDot(
    status: EmbedHealthStatus,
    modifier: Modifier = Modifier,
) {
    val color = when (status) {
        EmbedHealthStatus.Alive -> MaterialTheme.colorScheme.primary
        EmbedHealthStatus.Dead -> MaterialTheme.colorScheme.error
        EmbedHealthStatus.Unknown -> MaterialTheme.colorScheme.outline
    }
    val label = when (status) {
        EmbedHealthStatus.Alive -> stringResource(R.string.embed_health_alive)
        EmbedHealthStatus.Dead -> stringResource(R.string.embed_health_dead)
        EmbedHealthStatus.Unknown -> stringResource(R.string.embed_health_unknown)
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = label },
    )
}

@Composable
fun HealthStatusChip(
    status: EmbedHealthStatus,
    modifier: Modifier = Modifier,
) {
    val accent = when (status) {
        EmbedHealthStatus.Alive -> MaterialTheme.colorScheme.primary
        EmbedHealthStatus.Dead -> MaterialTheme.colorScheme.error
        EmbedHealthStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container = when (status) {
        EmbedHealthStatus.Alive -> MaterialTheme.colorScheme.primaryContainer
        EmbedHealthStatus.Dead -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        EmbedHealthStatus.Unknown -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val label = when (status) {
        EmbedHealthStatus.Alive -> stringResource(R.string.embed_health_alive)
        EmbedHealthStatus.Dead -> stringResource(R.string.embed_health_dead)
        EmbedHealthStatus.Unknown -> stringResource(R.string.embed_health_unknown)
    }

    Surface(
        modifier = modifier.semantics { contentDescription = label },
        color = container,
        shape = RoundedCornerShape(999.dp),
        contentColor = accent,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
