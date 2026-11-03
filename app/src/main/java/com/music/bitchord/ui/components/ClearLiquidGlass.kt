/*
 * A test style for Liquid Glass, switched on from Settings → Liquid Glass →
 * "Clear glass (test)". It is a Compose/AGSL port of the look of
 * nordify/css-liquid-glass (MIT, Copyright (c) 2026 Nordify,
 * https://github.com/nordify/css-liquid-glass): the refraction profile
 * measured from Apple's controls, the brightened fold band, the lit caps and
 * darker rim, the additive edge reflection, the caustic and the veil.
 *
 * Everything for the test lives in this file plus one branch at the top of
 * [liquidGlass] and the switch in Settings, so it can be dropped by deleting
 * this file and those two spots.
 *
 * Not ported: the reflection layer (it mirrors content beside the glass into
 * the rim and needs a second backdrop capture bigger than the surface).
 */
package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.runtimeShaderEffect
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import org.intellij.lang.annotations.Language
import kotlin.math.pow

/** The frost under the clear glass: barely any, so the middle stays 1:1. */
private const val CLEAR_BLUR_DP = 2f
private const val CLEAR_SATURATION = 1.1f

/** The reference pill the CSS is tuned for is 60px tall; widths scale from it. */
private const val REFERENCE_HEIGHT_DP = 60f

/** Extra pull along a pill's long straight run, so its long edges bend too. */
private const val REFR_TILT = 0.2f

/** How far the edge-light angles follow the aspect ratio (CSS ARC_SHAPE). */
private const val ARC_SHAPE = 0.4f

/** The CSS shadow colour, oklch(0.24 0.05 236.8). */
private val CLEAR_SHADOW_COLOR = Color(0xFF13252F)

@Composable
private fun isDarkGlass(): Boolean = MaterialTheme.colorScheme.surface.luminance() <= 0.5f

/** The clear glass's own fill: a trace of white over light pages, less over dark ones. */
@Composable
internal fun clearGlassVeil(): Color =
    Color.White.copy(alpha = if (isDarkGlass()) 0.03f else 0.14f)

/**
 * [liquidGlass] in the clear test style. Called from [liquidGlass] only, after
 * its support and reduce-blur checks, so it is never reached where the stock
 * glass would not draw.
 */
@Composable
internal fun Modifier.clearLiquidGlass(
    shape: CornerBasedShape,
    backdrop: Backdrop,
    exportedBackdrop: LayerBackdrop?,
    /** The veil with the user's tint and darkness laid over it; see [clearGlassVeil]. */
    surfaceColor: Color,
): Modifier {
    val darkF = if (isDarkGlass()) 1f else 0f
    val shadowAlpha = 0.35f * (1f - darkF) * 0.6f

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = CLEAR_SATURATION)
            blur(CLEAR_BLUR_DP.dp.toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val w = size.width
                val h = size.height
                val short = minOf(w, h)
                val maxR = short / 2f
                val ltr = layoutDirection == LayoutDirection.Ltr
                fun corner(start: Boolean, top: Boolean): Float {
                    val c = when {
                        top && (start == ltr) -> shape.topStart
                        top -> shape.topEnd
                        start == ltr -> shape.bottomStart
                        else -> shape.bottomEnd
                    }
                    return c.toPx(size, this).coerceAtMost(maxR)
                }
                val tl = corner(start = true, top = true)
                val tr = corner(start = false, top = true)
                val br = corner(start = false, top = false)
                val bl = corner(start = true, top = false)
                // Size factor against the 60dp reference pill, held in a sane
                // range so a tall drawer does not get a thumb-thick rim.
                val s = (short / REFERENCE_HEIGHT_DP.dp.toPx()).coerceIn(0.6f, 1.4f)
                val unit = REFERENCE_HEIGHT_DP.dp.toPx() / 60f * s
                val r = maxOf(tl, tr, br, bl).coerceAtLeast(0.01f)
                val straight = (((maxOf(w, h) / 2f) - r) / r).coerceIn(0f, 1f)
                val ax = 1f / (maxOf(1f, w) / 2f).pow(ARC_SHAPE)
                val by = 1f / (maxOf(1f, h) / 2f).pow(ARC_SHAPE)
                runtimeShaderEffect("ClearLiquidGlass", ClearGlassShader, "content") {
                    setFloatUniform("size", w, h)
                    setFloatUniform("offset", -padding, -padding)
                    setFloatUniform("cornerRadii", tl, tr, br, bl)
                    setFloatUniform("unit", unit)
                    setFloatUniform("dark", darkF)
                    setFloatUniform("tilt", REFR_TILT * straight)
                    setFloatUniform("arcScale", ax, by)
                }
            }
        },
        // The shader draws the edge light itself.
        highlight = null,
        shadow = {
            Shadow(
                radius = 17.dp,
                offset = DpOffset(0.dp, 14.dp),
                color = CLEAR_SHADOW_COLOR,
                alpha = shadowAlpha,
            )
        },
        onDrawSurface = { drawRect(color = surfaceColor, size = size) },
        exportedBackdrop = exportedBackdrop,
        // Full resolution: there is next to no blur to hide an upscale, and
        // the rim light is a pixel or two wide.
        backdropScale = 1f,
    )
}

@Language("AGSL")
private const val ClearGlassShader = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float unit;
uniform float dark;
uniform float tilt;
uniform float2 arcScale;

float radiusAt(float2 p, float4 radii) {
    if (p.x >= 0.0) {
        if (p.y <= 0.0) return radii.y;
        return radii.z;
    }
    if (p.y <= 0.0) return radii.x;
    return radii.w;
}

float sdRoundedRect(float2 p, float2 hs, float r) {
    float2 c = abs(p) - (hs - float2(r));
    return length(max(c, 0.0)) - r + min(max(c.x, c.y), 0.0);
}

// Outward normal of the rounded rectangle at p.
float2 normalAt(float2 p, float2 hs, float r) {
    float2 c = abs(p) - (hs - float2(r));
    float2 sg = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    if (c.x >= 0.0 && c.y >= 0.0) return sg * normalize(c + float2(1e-4));
    if (c.x >= c.y) return float2(sg.x, 0.0);
    return float2(0.0, sg.y);
}

// How far inwards the glass samples, in corner radii, at q = 1 - depth / R.
// Centre 1:1 up to q = 0.61, a soft S-step to 0.49 R, then a steep lip that
// pulls content from deep inside out to the rim, softly capped.
float refrProfile(float q) {
    float u = clamp((q - 0.61) / 0.39, 0.0, 1.0);
    float v = clamp((q - 0.7) / 0.3, 0.0, 1.0);
    float v2 = v * v;
    float d = 0.49 * u * u * (3.0 - 2.0 * u) + 10.0 * v2 * v2 * v;
    if (q <= 1e-6 || d <= 0.0) return 0.0;
    float cap = q * 1.5;
    float x = d / cap;
    float x2 = x * x;
    return cap * x / sqrt(sqrt(1.0 + x2 * x2));
}

float3 saturateRgb(float3 c, float k) {
    float l = dot(c, float3(0.2126, 0.7152, 0.0722));
    return float3(l) + (c - float3(l)) * k;
}

float radialStop(float t) {
    // 0.58 at 0, 0.42 at 35%, 0.18 at 62%, 0 at 85%.
    if (t < 0.35) return mix(0.58, 0.42, t / 0.35);
    if (t < 0.62) return mix(0.42, 0.18, (t - 0.35) / 0.27);
    if (t < 0.85) return mix(0.18, 0.0, (t - 0.62) / 0.23);
    return 0.0;
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord + offset - hs;
    float r = min(radiusAt(p, cornerRadii), min(hs.x, hs.y));
    float rs = max(r, 1.0);
    float depth = max(-sdRoundedRect(p, hs, r), 0.0);

    // Refraction.
    float2 samplePos = coord;
    float q = 1.0 - depth / rs;
    if (q > 0.0) {
        float pr = refrProfile(q) * rs;
        samplePos -= normalAt(p, hs, r) * pr;
        if (size.x >= size.y) {
            float run = max(hs.x - r, 1.0);
            samplePos.x -= tilt * pr * clamp(p.x / run, -1.0, 1.0);
        } else {
            float run = max(hs.y - r, 1.0);
            samplePos.y -= tilt * pr * clamp(p.y / run, -1.0, 1.0);
        }
    }
    half4 c4 = content.eval(samplePos);
    float3 c = float3(c4.rgb);
    float a = float(c4.a);

    // Caustic: light bundled under the lens, a soft ring of the outline
    // shrunk to 86% and dropped 12 units, strongest top right and bottom left.
    // It sits behind the glass, so it is looked up where the glass samples.
    float light = 1.0 - dark;
    if (light > 0.0) {
        float2 sp = samplePos + offset - hs;
        float2 pc = (sp - float2(0.0, 12.0 * unit)) / 0.86;
        float rc = min(radiusAt(pc, cornerRadii), min(hs.x, hs.y));
        float sdc = sdRoundedRect(pc, hs, rc) * 0.86;
        float x = (sdc + 4.5 * unit) / (11.0 * unit);
        float ring = 0.36 * exp(-0.5 * x * x);
        float2 uv = (sp + hs) / size;
        float t1 = length((uv - float2(0.78, 0.0)) / float2(0.7, 1.2));
        float t2 = length((uv - float2(0.22, 1.0)) / float2(0.7, 1.2));
        float mask = min(1.0, 0.4 + radialStop(t1) + radialStop(t2));
        c = mix(c, float3(a), clamp(0.7 * light * mask * ring, 0.0, 1.0));
    }

    // The fold: brighten and saturate the whole folding band.
    float fold = clamp(1.0 - depth / (0.39 * rs), 0.0, 1.0);
    c = mix(c, saturateRgb(c * 1.08, 1.3), fold);

    // Edge light: a thin ring, lit at the caps (top right, bottom left) and
    // darker elsewhere, then an additive reflection on the lit caps.
    float rimW = 0.7 * 1.3 * unit;
    float rimFade = 2.0 * 1.3 * unit;
    float edge = clamp((rimFade - depth) / max(rimFade - rimW, 1e-3), 0.0, 1.0);
    if (edge > 0.0) {
        float n = atan(p.x * arcScale.x, -p.y * arcScale.y);
        float glint = 0.5 + 0.5 * cos(2.0 * (n - 0.7853982));
        float glintExp = 1.35 + 0.1 * dark;
        float glintSat = 3.1 - 1.1 * dark;
        float rimExp = 0.84 + 0.41 * dark;
        float rimSat = 2.2 - 0.9 * dark;
        c = mix(c, saturateRgb(c * glintExp, glintSat), edge * glint);
        c = mix(c, saturateRgb(c * rimExp, rimSat), edge * (1.0 - glint));
        float spec = 0.13 + 0.25 * dark;
        c = mix(c, float3(a), spec * edge * glint);
    }

    c = clamp(c, 0.0, a);
    return half4(half3(c), half(a));
}"""
