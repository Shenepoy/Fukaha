package app.fukaha.android.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.graphicsLayer
import app.fukaha.EmbedHealthSnapshot
import app.fukaha.SourceCheckDetail
import app.fukaha.SourceCheckReason
import app.fukaha.EmbedHealthStatus
import app.fukaha.EmbedService
import app.fukaha.R
import app.fukaha.android.theme.onSelectedBadgeGold
import app.fukaha.android.theme.selectedBadgeGold
import kotlinx.coroutines.launch

@Composable
fun PreferredFixerRow(
    platformName: String,
    serviceName: String,
    host: String,
    healthStatus: EmbedHealthStatus = EmbedHealthStatus.Unknown,
    infoUrl: String?,
    onClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val hostLabel = host.removePrefix("https://").removePrefix("http://").trimEnd('/')
    val statusLabel = when (healthStatus) {
        EmbedHealthStatus.Alive -> null
        EmbedHealthStatus.Dead -> stringResource(R.string.embed_health_dead)
        EmbedHealthStatus.Unknown -> stringResource(R.string.embed_health_unknown)
    }
    val hostLine = if (statusLabel != null) "$hostLabel · $statusLabel" else hostLabel

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HealthStatusDot(status = healthStatus)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$platformName · $serviceName",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hostLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (!infoUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.fixer_info_link),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { uriHandler.openUri(infoUrl) },
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.preferred_fixer_pick_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferredFixerPickerSheet(
    platformName: String,
    services: List<EmbedService>,
    selectedHost: String,
    health: EmbedHealthSnapshot = EmbedHealthSnapshot(),
    onSelect: (EmbedService) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val dialogProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        dialogProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        )
    }
    val dismissSheet = {
        if (!dismissing) {
            dismissing = true
            val sheetWasVisible = sheetState.isVisible
            scope.launch {
                sheetState.hide()
                if (!sheetWasVisible) {
                    dialogProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 140,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                onDismiss()
            }
        }
        Unit
    }
    BackHandler(onBack = dismissSheet)

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconButton(onClick = dismissSheet) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.preferred_fixer_pick_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.preferred_fixer_pick_subtitle, platformName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .selectableGroup()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    val target = selectedHost.trim().trimEnd('/')
                    items(services, key = { it.normalizedHost() }) { service ->
                        val selected = service.normalizedHost().equals(target, ignoreCase = true) ||
                            service.alternateHosts.any {
                                it.trim().trimEnd('/').equals(target, ignoreCase = true)
                            }
                        FixerOptionCard(
                            service = service,
                            selected = selected,
                            healthStatus = health.statusOf(service.normalizedHost()),
                            detail = health.details[app.fukaha.EmbedHealthKeys.normalize(service.normalizedHost())],
                            onClick = {
                                onSelect(service)
                                dismissSheet()
                            },
                        )
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            ModalBottomSheet(
                onDismissRequest = dismissSheet,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                content = { content() },
            )
        } else {
            Dialog(onDismissRequest = dismissSheet) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .heightIn(max = maxHeight * 0.9f)
                        .graphicsLayer {
                            alpha = dialogProgress.value
                            scaleX = 0.985f + (0.015f * dialogProgress.value)
                            scaleY = 0.985f + (0.015f * dialogProgress.value)
                        },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = 6.dp,
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun FixerOptionCard(
    service: EmbedService,
    selected: Boolean,
    healthStatus: EmbedHealthStatus,
    detail: SourceCheckDetail?,
    onClick: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val infoUrl = service.repo?.takeIf { it.isNotBlank() }
        ?: service.normalizedHost().takeIf { it.startsWith("http") }
    val isDead = healthStatus == EmbedHealthStatus.Dead
    val hostLabel = service.normalizedHost()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "fixerCardContainer",
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "fixerCardBorder",
    )
    val onContainer = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }.copy(alpha = if (isDead) 0.72f else 1f)
    val muted = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }.copy(alpha = if (isDead) 0.65f else 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = MaterialTheme.shapes.medium,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                    ),
            )

            Icon(
                imageVector = if (selected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.Circle
                },
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier
                    .padding(start = 12.dp, end = 10.dp)
                    .size(26.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 2.dp),
            ) {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = hostLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    HealthStatusChip(status = healthStatus)
                }

                detail?.let { evidence ->
                    val description = when (evidence.reason) {
                        SourceCheckReason.Responded -> null
                        SourceCheckReason.Redirected -> stringResource(R.string.source_redirected) + " " + evidence.finalHost.orEmpty()
                        SourceCheckReason.Blocked -> stringResource(R.string.preview_blocked)
                        SourceCheckReason.HttpError -> stringResource(R.string.preview_http_error)
                        SourceCheckReason.TimedOut -> stringResource(R.string.preview_timed_out)
                        SourceCheckReason.NetworkError -> stringResource(R.string.preview_network_error)
                    }
                    description?.let {
                        Text(it + (evidence.httpStatus?.let { code -> " (HTTP $code)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }

                service.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                service.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = stringResource(R.string.fixer_by_author, author),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (selected) {
                Surface(
                    color = selectedBadgeGold,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.padding(end = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.fixer_selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSelectedBadgeGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (infoUrl != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.fixer_info_link),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(18.dp)
                        .clickable { uriHandler.openUri(infoUrl) },
                )
            }
        }
    }
}
