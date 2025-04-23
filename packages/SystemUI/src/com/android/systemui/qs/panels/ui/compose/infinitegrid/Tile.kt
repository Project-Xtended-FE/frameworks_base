/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalFoundationApi::class)

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.content.Context
import android.content.res.Resources
import android.os.Trace
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.Tile.STATE_UNAVAILABLE
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.Expandable
import com.android.compose.animation.bounceable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.Dependency
import com.android.systemui.Flags
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModelFactoryProvider
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.tiles.impl.ringer.QSTileRingerSlider
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CustomColorScheme
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileEndPadding
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileStartPadding
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabel
import com.android.systemui.qs.panels.ui.compose.BounceableInfo
import com.android.systemui.qs.panels.ui.viewmodel.AccessibilityUiState
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineScope

private const val TEST_TAG_SMALL = "qs_tile_small"
private const val TEST_TAG_LARGE = "qs_tile_large"

@Composable
fun TileLazyGrid(
    columns: GridCells,
    columnSpacing: Dp,
    rowSpacing: Dp,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = columns,
        verticalArrangement = spacedBy(rowSpacing),
        horizontalArrangement = spacedBy(columnSpacing),
        contentPadding = contentPadding,
        modifier = modifier,
        content = content,
    )
}

private val TileViewModel.traceName
    get() = spec.toString().takeLast(Trace.MAX_SECTION_NAME_LEN)

/**
 * This composable function is responsible for rendering a tile based on the provided
 * [TileViewModel]. It handles different states of the tile (e.g., available, unavailable),
 * interactions (click, long click), and visual styles (icon only or large tile).
 *
 * @param tile The [TileViewModel] containing the data and logic for the tile.
 * @param iconOnly A boolean indicating whether to display only the icon of the tile or the full
 *   tile content (false for large tiles).
 * @param squishiness The float value representing the current squishiness factor of the tile, used
 *   for animations.
 * @param coroutineScope The [CoroutineScope] to launch coroutines for animations.
 * @param tileHapticsViewModelFactoryProvider A provider for creating a [TileHapticsViewModel]
 *   instance, used for haptic feedback.
 * @param interactionSource An optional [MutableInteractionSource] to track user interactions with
 *   the tile, used by the parent composable to animate a bounce effect. Tiles may or may not use
 *   this interaction source to control whether they should bounce or not.
 * @param modifier An optional [Modifier] to be applied to the root composable of the tile.
 * @param isVisible Whether the tile is currently visible. Defaults to true.
 * @param requestToggleTextFeedback A lambda function that is invoked when a toggleable icon only
 *   tile is clicked, used to request the feedback text.
 * @param detailsViewModel An optional [DetailsViewModel] used to handle navigation to a detailed
 *   view when a tile is clicked, if applicable.
 */
@Composable
fun Tile(
    tile: TileViewModel,
    iconOnly: Boolean,
    squishiness: () -> Float,
    coroutineScope: CoroutineScope,
    bounceableInfo: BounceableInfo?,
    tileHapticsViewModelFactoryProvider: TileHapticsViewModelFactoryProvider,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean = { true },
    requestToggleTextFeedback: (TileSpec) -> Unit = {},
    detailsViewModel: DetailsViewModel?,
) {
    trace(tile.traceName) {
        val currentBounceableInfo by rememberUpdatedState(bounceableInfo)
        val resources = resources()

        /*
         * Use produce state because [QSTile.State] doesn't have well defined equals (due to
         * inheritance). This way, even if tile.state changes, uiState may not change and lead to
         * recomposition.
         */
        val uiState by
            produceState(tile.currentState.toUiState(resources), tile, resources) {
                tile.state.collect { value = it.toUiState(resources) }
            }
        val isClickable = uiState.state != STATE_UNAVAILABLE

        val icon by
            produceState(tile.currentState.toIconProvider(), tile) {
                tile.state.collect { value = it.toIconProvider() }
            }

        val colors = TileDefaults.getColorForState(uiState, iconOnly)
        val hapticsViewModel: TileHapticsViewModel? =
            rememberViewModel(traceName = "TileHapticsViewModel") {
                tileHapticsViewModelFactoryProvider.getHapticsViewModelFactory()?.create(tile)
            }

        if (tile.spec.spec == "sound" && !iconOnly) {
            QSTileRingerSlider()
            return@trace
        }

        BoxWithConstraints {
            val spacing = dimensionResource(R.dimen.qs_tile_margin_horizontal)
            val tileHeight = (maxWidth / 2) - (spacing / 2)

            // TODO(b/361789146): Draw the shapes instead of clipping
            val tileShape = RoundedCornerShape(
                if (iconOnly) maxWidth / 2
                else tileHeight / 2
            )
            
            val animatedColor by animateColorAsState(
                colors.background, 
                label = "QSTileBackgroundColor"
            )
            val animatedAlpha by animateFloatAsState(
                colors.alpha, 
                label = "QSTileAlpha"
            )

            val context = LocalContext.current
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val EFFECT_CLICK = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)

            val s = squishiness()
            
            val targetBgColor = if (s < 0.83f) Color.Transparent else animatedColor
            val displayColor by animateColorAsState(
                targetBgColor, 
                label = "QSTileBackgroundColor"
            )

            val isDualTarget = uiState.handlesSecondaryClick

            TileExpandable(
                color = { displayColor },
                shape = tileShape,
                squishiness = squishiness,
                hapticsViewModel = hapticsViewModel,
                modifier = modifier
                    .borderOnFocus(color = MaterialTheme.colorScheme.secondary, tileShape.topEnd)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = animatedAlpha }
                    .thenIf(currentBounceableInfo != null) {
                        Modifier.bounceable(
                            bounceable = currentBounceableInfo!!.bounceable,
                            previousBounceable = currentBounceableInfo!!.previousTile,
                            nextBounceable = currentBounceableInfo!!.nextTile,
                            orientation = Orientation.Horizontal,
                            bounceEnd = currentBounceableInfo!!.bounceEnd,
                        )
                    },
            ) { expandable ->
                // Use main click on long press for small, available dual target tiles.
                // Open settings otherwise.
                val useLongClickToSettings = !(iconOnly && isDualTarget && isClickable)
                val longClick: (() -> Unit)? =
                    {
                            hapticsViewModel?.setTileInteractionState(
                                TileHapticsViewModel.TileInteractionState.LONG_CLICKED
                            )
                            tile.onLongClick(expandable)
                        }
                        .takeIf { !useLongClickToSettings || uiState.handlesLongClick }

                // Bounce the tile's container if it is toggleable and is not a large
                // dual target tile. These don't toggle on main click.
                val bounceContainer = uiState.accessibilityUiState.accessibilityRole == Role.Switch && 
                                      (iconOnly || !isDualTarget)
                val contentBounceable =
                    remember(currentBounceableInfo) {
                        currentBounceableInfo?.bounceable ?: BounceableTileViewModel()
                    }

                TileContainer(
                    interactionSource = interactionSource.takeIf { bounceContainer },
                    onClick = onClick@{
                        if (!isClickable) return@onClick

                        if (iconOnly && uiState.handlesSecondaryClick) {
                            vibrator.vibrate(EFFECT_CLICK)
                            tile.onSecondaryClick()
                        } else {
                            var hasDetails = false
                            if (QsDetailedView.isEnabled) {
                                hasDetails = detailsViewModel?.onTileClicked(tile.spec) == true
                            }
                            if (!Flags.msdlFeedback()) {
                                vibrator.vibrate(EFFECT_CLICK)
                            }
                            if (!hasDetails) {
                                // For those tile's who doesn't have a detailed view, process with their
                                // `onClick` behavior.
                                tile.onClick(expandable)
                                hapticsViewModel?.setTileInteractionState(
                                    TileHapticsViewModel.TileInteractionState.CLICKED
                                )
                            }
                        }

                        // Side effects of the click
                        coroutineScope.launch {
                            // Bounce the tile's container if it is toggleable and is not a large
                            // dual target tile. These don't toggle on main click. Otherwise bounce
                            // the content of the tile.
                            if (bounceContainer) {
                                // Only bounce the container ourselves if a BounceableInfo was given
                                currentBounceableInfo?.bounceable?.animateContainerBounce()
                            } else {
                                contentBounceable.animateContentBounce(iconOnly)
                            }
                        }
                        if (uiState.accessibilityUiState.accessibilityRole == Role.Switch && iconOnly) {
                            // And show footer text feedback for icons
                            requestToggleTextFeedback(tile.spec)
                        }
                    },
                    onLongClick = longClick,
                    uiState = uiState,
                    iconOnly = iconOnly,
                    isDualTarget = isDualTarget,
                ) {
                    val iconProvider: Context.() -> Icon = { getTileIcon(icon = icon) }
                    if (iconOnly) {
                        SmallTileContent(
                            iconProvider = iconProvider,
                            color = colors.icon,
                            modifier = Modifier.align(Alignment.Center)
                                .bounceScale { contentBounceable.iconBounceScale },
                        )
                    } else {
                        val secondaryClick: (() -> Unit)? =
                            {
                                    vibrator.vibrate(EFFECT_CLICK)
                                    tile.onSecondaryClick()
                                }
                                .takeIf { uiState.handlesSecondaryClick }
                        LargeTileContent(
                            label = uiState.label,
                            secondaryLabel = uiState.secondaryLabel,
                            iconProvider = iconProvider,
                            sideDrawable = uiState.sideDrawable,
                            colors = colors,
                            iconShape = tileShape,
                            toggleClick = secondaryClick,
                            onLongClick = longClick,
                            accessibilityUiState = uiState.accessibilityUiState,
                            squishiness = squishiness,
                            isVisible = isVisible,
                            textScale = { contentBounceable.textBounceScale },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileExpandable(
    color: () -> Color,
    shape: Shape,
    squishiness: () -> Float,
    hapticsViewModel: TileHapticsViewModel?,
    modifier: Modifier = Modifier,
    content: @Composable (Expandable) -> Unit,
) {
    val s = squishiness()
    Expandable(
        controller = rememberExpandableController(color = color, shape = shape),
        modifier = modifier
            .clip(shape)
            .squishy(s),
        useModifierBasedImplementation = true,
    ) {
        content(hapticsViewModel?.createStateAwareExpandable(it) ?: it)
    }
}

@Composable
fun TileContainer(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    uiState: TileUiState,
    iconOnly: Boolean,
    isDualTarget: Boolean,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints {
        val spacing = dimensionResource(R.dimen.qs_tile_margin_horizontal)
        val tileHeight = with(LocalDensity.current) {
            (maxWidth.toPx() / 2) - (spacing.toPx() / 2)
        }
        val aspect = if (iconOnly) {
            1f
        } else {
            with(LocalDensity.current) {
                maxWidth.toPx() / tileHeight
            }
        }

        Box(
            modifier = 
                Modifier.fillMaxWidth()
                    .aspectRatio(aspect)
                    .tileCombinedClickable(
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                        accessibilityUiState = uiState.accessibilityUiState,
                        iconOnly = iconOnly,
                        isDualTarget = isDualTarget,
                        interactionSource = interactionSource,
                    )
                    .sysuiResTag(if (iconOnly) TEST_TAG_SMALL else TEST_TAG_LARGE)
                    .thenIf(!iconOnly) { Modifier.largeTilePadding(uiState) }, // Icon tiles are center aligned
            content = content,
        )
    }
}

@Composable
fun LargeStaticTile(
    uiState: TileUiState,
    iconProvider: IconProvider,
    modifier: Modifier = Modifier,
) {
    val colors = TileDefaults.getColorForState(uiState = uiState, iconOnly = false)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .background(colors.background)
            .largeTilePadding(uiState)
    ) {
        Box(modifier.clip(RoundedCornerShape(maxWidth / 2))) {
            LargeTileContent(
                label = uiState.label,
                secondaryLabel = "",
                iconProvider = { getTileIcon(icon = iconProvider) },
                sideDrawable = null,
                colors = colors,
                squishiness = { 1f },
            )
        }
    }
}

private fun Context.getTileIcon(icon: IconProvider): Icon {
    return icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) {
            Icon.Resource(it.resId, null)
        } else {
            Icon.Loaded(it.getDrawable(this), null)
        }
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)
}

fun tileHorizontalArrangement(): Arrangement.Horizontal {
    return spacedBy(space = CommonTileDefaults.TileArrangementPadding, alignment = Alignment.Start)
}

fun Modifier.largeTilePadding(): Modifier {
    return padding(start = TileStartPadding + CommonTileDefaults.TileArrangementPadding, end = TileEndPadding)
}

fun Modifier.largeTilePadding(uiState: TileUiState): Modifier {
    return this.then(
        if (!uiState.handlesSecondaryClick) {
            padding(start = TileStartPadding + CommonTileDefaults.TileArrangementPadding, end = TileEndPadding)
        } else {
            padding(start = TileStartPadding, end = TileEndPadding)
        }
    )
}

@Composable
fun Modifier.tileCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    iconOnly: Boolean,
    isDualTarget: Boolean,
    interactionSource: MutableInteractionSource?,
): Modifier {
    val longPressLabel = longPressLabel()
    
    val baseModifier = if (interactionSource != null) {
        combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
            onClickLabel = accessibilityUiState.clickLabel,
            onLongClickLabel = longPressLabel,
            hapticFeedbackEnabled = !Flags.msdlFeedback(),
        )
    } else {
        combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onClickLabel = accessibilityUiState.clickLabel,
            onLongClickLabel = longPressLabel,
            hapticFeedbackEnabled = !Flags.msdlFeedback(),
        )
    }
    
    return baseModifier
        .semantics {
            val accessibilityRole =
                if (iconOnly && isDualTarget) {
                    Role.Switch
                } else {
                    accessibilityUiState.accessibilityRole
                }
            if (accessibilityRole == Role.Switch) {
                accessibilityUiState.toggleableState?.let { toggleableState = it }
            }
            role = accessibilityRole
            stateDescription = accessibilityUiState.stateDescription
        }
        .thenIf(iconOnly) {
            Modifier.semantics { contentDescription = accessibilityUiState.contentDescription }
        }
}

@Composable
fun Modifier.bounceScale(scale: () -> Float): Modifier {
    return graphicsLayer {
        val s = scale()
        scaleX = s
        scaleY = s
    }
}

data class TileColors(
    val background: Color,
    val iconBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val icon: Color,
    val alpha: Float = 1f,
)

private object TileDefaults {
    /** An active icon tile uses the active color as background */
    @Composable
    @ReadOnlyComposable
    fun activeIconTileColors(): TileColors =
        TileColors(
            background = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onPrimary,
            secondaryLabel = MaterialTheme.colorScheme.onPrimary,
            icon = MaterialTheme.colorScheme.onPrimary,
        )

    /** An active tile with dual target only show the active color on the icon */
    @Composable
    @ReadOnlyComposable
    fun activeDualTargetTileColors(): TileColors =
        TileColors(
            background = CustomColorScheme.current.qsTileColor,
            iconBackground = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onPrimary,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveDualTargetTileColors(): TileColors =
        TileColors(
            background = CustomColorScheme.current.qsTileColor,
            iconBackground = LocalAndroidColorScheme.current.surfaceEffect3,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveTileColors(): TileColors =
        TileColors(
            background = CustomColorScheme.current.qsTileColor,
            iconBackground = Color.Transparent,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
        )

    @Composable
    @ReadOnlyComposable
    fun unavailableTileColors(): TileColors {
        return TileColors(
            background = CustomColorScheme.current.qsTileColor,
            iconBackground = LocalAndroidColorScheme.current.surfaceEffect2,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
            alpha = .38f,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun getColorForState(uiState: TileUiState, iconOnly: Boolean): TileColors {
        return when (uiState.state) {
            STATE_ACTIVE -> {
                if (uiState.handlesSecondaryClick && !iconOnly) {
                    activeDualTargetTileColors()
                } else {
                    activeIconTileColors()
                }
            }

            STATE_INACTIVE -> {
                if (uiState.handlesSecondaryClick && !iconOnly) {
                    inactiveDualTargetTileColors()
                } else {
                    inactiveTileColors()
                }
            }

            else -> unavailableTileColors()
        }
    }
}

private fun Modifier.squishy(squishiness: Float): Modifier {
    return if (squishiness < 0.95f) {
        graphicsLayer {
            scaleX = squishiness
            scaleY = squishiness
            alpha = if (squishiness < 0.83f) {
                0f
            } else {
                ((squishiness - 0.83f) / (1f - 0.83f)).coerceIn(0f, 1f)
            }
        }
    } else {
        this
    }
}

/**
 * A composable function that returns the [Resources]. It will be recomposed when [Configuration]
 * gets updated.
 */
@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalResources.current
}
