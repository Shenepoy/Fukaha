package app.fukaha.android.share

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.key
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.fukaha.EmbedHealthStatus
import app.fukaha.PreparedLink
import app.fukaha.R
import app.fukaha.android.theme.asLtrUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    loading: Boolean,
    downloading: Boolean,
    error: String?,
    prepared: PreparedLink?,
    mediaDownloadEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onShareCleaned: () -> Unit,
    onShareEmbed: () -> Unit,
    onShareMedia: () -> Unit,
    onCopyOriginal: () -> Unit,
    onCopyCleaned: () -> Unit,
    onCopyEmbed: () -> Unit,
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
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = dismissSheet)

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = dismissSheet,
                    modifier = Modifier.offset(y = (-4).dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                    )
                    Text(
                        text = stringResource(
                            if (mediaDownloadEnabled) {
                                R.string.share_sheet_subtitle
                            } else {
                                R.string.share_sheet_subtitle_no_media
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            when {
                loading || downloading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                        Text(
                            if (downloading) {
                                stringResource(R.string.downloading)
                            } else {
                                stringResource(R.string.preparing)
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                error != null -> {
                    Surface(
                        color = colors.errorContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = error,
                            color = colors.onErrorContainer,
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    TextButton(onClick = dismissSheet) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
                prepared != null -> {
                    val link = prepared
                    val platform = link.detected.platformName
                        ?: stringResource(R.string.unknown_platform)

                    OriginalLinkBlock(
                        url = link.detected.originalUrl,
                        platform = platform,
                        onCopy = onCopyOriginal,
                    )

                    LinkActionRow(
                        title = stringResource(R.string.cleaned_preview),
                        url = link.detected.cleanedUrl,
                        sectionIcon = Icons.Outlined.CleaningServices,
                        onShare = onShareCleaned,
                        onCopy = onCopyCleaned,
                    )

                    if (link.embedUrl != null) {
                        LinkActionRow(
                            title = if (link.embedHealth == EmbedHealthStatus.Dead) {
                                stringResource(R.string.embed_preview_unreachable)
                            } else {
                                stringResource(R.string.embed_preview)
                            },
                            url = link.embedUrl!!,
                            sectionIcon = Icons.Outlined.Visibility,
                            onShare = onShareEmbed,
                            onCopy = onCopyEmbed,
                            titleTrailing = if (link.embedHealth == EmbedHealthStatus.Dead) {
                                stringResource(R.string.embed_health_dead)
                            } else {
                                null
                            },
                        )
                    }

                    link.embedUrl?.let { embed ->
                        key(embed) { EmbedPreviewCheckControl(link.detected.cleanedUrl, embed) }
                    }

                    // Without a Cobalt URL the download can only fail, so drop the
                    // button rather than showing a dead control with an excuse.
                    if (mediaDownloadEnabled) {
                        FilledTonalButton(
                            onClick = onShareMedia,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.share_media),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
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
                containerColor = colors.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                content = { content(Modifier) },
            )
        } else {
            Dialog(onDismissRequest = dismissSheet) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .heightIn(max = maxHeight * 0.9f)
                        .graphicsLayer {
                            alpha = dialogProgress.value
                            scaleX = 0.985f + (0.015f * dialogProgress.value)
                            scaleY = 0.985f + (0.015f * dialogProgress.value)
                        },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = colors.surfaceContainerLow,
                    shadowElevation = 6.dp,
                ) {
                    content(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    icon: ImageVector,
    trailing: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun OriginalLinkBlock(
    url: String,
    platform: String,
    onCopy: () -> Unit,
) {
    LinkActionRow(
        title = stringResource(R.string.original_preview),
        url = url,
        sectionIcon = Icons.Outlined.Public,
        titleTrailing = platform,
        onShare = null,
        onCopy = onCopy,
    )
}

@Composable
private fun LinkActionRow(
    title: String,
    url: String,
    sectionIcon: ImageVector,
    onShare: (() -> Unit)?,
    onCopy: () -> Unit,
    titleTrailing: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shareShape = RoundedCornerShape(14.dp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(text = title, icon = sectionIcon, trailing = titleTrailing)

        // Keep share on the physical right in both LTR and RTL locales.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = colors.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        sectionIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = if (onShare != null) (-56).dp else 8.dp)
                            .size(72.dp)
                            .alpha(0.07f),
                        tint = colors.primary,
                    )
                    Row(
                        // Size to the tallest child so the share button can match the
                        // height of however many lines the URL wraps to.
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(onClick = onCopy)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy_short),
                                modifier = Modifier.size(20.dp),
                                tint = colors.primary,
                            )
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodyMedium.asLtrUrl(),
                                color = colors.onSurface,
                                softWrap = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (onShare != null) {
                            Button(
                                onClick = onShare,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .heightIn(min = 52.dp)
                                    .widthIn(min = 64.dp),
                                shape = shareShape,
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary,
                                ),
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = stringResource(R.string.action_share_short),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoActionProgress() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
        Text(
            text = stringResource(R.string.preparing),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
