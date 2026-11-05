package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.icons.BitChordIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * The round shuffle button that floats at the bottom right of the library
 * pages, over the list and above the tab bar.
 *
 * It wears the same material as the nav bar beside it: liquid glass while that
 * is on, the frosted haze otherwise, and a solid fill under "reduce dynamic
 * blur". It has to be composed outside the page's recorded backdrop layer, or
 * the glass would be sampling itself.
 */
@Composable
fun LibraryShuffleButton(
    onClick: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    LibraryFloatingButton(
        // The same arrows as the player's shuffle toggle.
        icon = BitChordIcons.Shuffle,
        contentDescription = stringResource(R.string.shuffle),
        onClick = onClick,
        hazeState = hazeState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun LibraryFloatingButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val shape = CircleShape
    val container = MaterialTheme.colorScheme.surface
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val useLiquidGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val contentColor = if (useLiquidGlass && !reduceDynamicBlur) {
        glassContentColor()
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .size(size)
            // The glass draws its own shadow; the other two need one to lift
            // off the page they float over.
            .then(if (useLiquidGlass) Modifier else Modifier.shadow(6.dp, shape))
            .clip(shape)
            .then(
                when {
                    useLiquidGlass -> Modifier.liquidGlass(shape)
                    reduceDynamicBlur || hazeState == null -> Modifier.background(container)
                    else -> Modifier.optimizedHazeEffect(
                        state = hazeState,
                        style = HazeMaterials.regular(container),
                    )
                },
            )
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = contentColor,
            )
        }
    }
}
