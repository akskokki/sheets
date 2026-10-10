package dev.axu.sheets.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/**
 * Draws a pressed thing as a recess in a thin layer of water: a narrow wall at its rim, shadowed on
 * the side away from the light, and a floor a little darker than the surface, with the rim's shadow
 * across it and the mark settled into it. Waves spread from where it was pressed, bending what they
 * pass over (red and blue a little more and less than green, like a lens) and catching the light on
 * their crests, until they fade at its edge.
 */
internal fun liquidPress(): PressShader? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LiquidPress() else null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LiquidPress : PressShader {
    private val shader = RuntimeShader(SHADER)

    override fun effect(size: Size, room: Float, density: Float, depth: Float, waves: List<Wave>): ComposeRenderEffect {
        shader.setFloatUniform("size", size.width / density, size.height / density)
        shader.setFloatUniform("room", room)
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("depth", depth)
        for (i in 0 until MAX_WAVES) {
            val wave = waves.getOrNull(i)
            shader.setFloatUniform(
                "wave$i",
                (wave?.origin?.x ?: 0f) / density,
                (wave?.origin?.y ?: 0f) / density,
                wave?.age?.value ?: 0f,
                if (wave == null) 0f else WAVE_STRENGTH,
            )
        }
        // A render effect keeps the uniforms it was made with, so it's made again each frame.
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/*
 * Tunables. Lengths are in dp, times in ms as the app plays them (before the system's animation
 * scale), and the rest are fractions.
 */

/** How fast a wave spreads, in dp per ms. */
private const val WAVE_SPEED = 0.0375f
private const val WAVE_LENGTH = 12f

/** How wide a wave's packet of crests is. */
private const val WAVE_WIDTH = 5.5f

/** How strongly a wave bends and lights what it passes over. */
private const val WAVE_STRENGTH = 0.9f

/** How long a wave takes to fall to about a third, and to swell at the start. */
private const val WAVE_DECAY_MILLIS = 1720f
private const val WAVE_ONSET_MILLIS = 96f

/** How far a wave spreads before it's half as strong. */
private const val WAVE_FALLOFF = 70f

/** Until a wave is down to a twentieth. */
internal const val WAVE_MILLIS = (WAVE_DECAY_MILLIS * 3).toInt()

/** How far a wave bends what's under it, per unit of slope, and how far red and blue part from green. */
private const val REFRACTION = 3.5f
private const val FRINGE = 0.4f

/** How steep the surface looks to the light, and how much light and shade show. */
private const val BUMP = 0.75f
private const val SHADE = 0.55f
private const val GLINT = 0.7f
private const val GLINT_SHARPNESS = 70f

/** From the rim down to the floor of a pressed thing, and how steep that wall looks. */
private const val WALL = 1.6f
private const val WALL_TILT = 0.7f

/** How much darker the floor is, and the rim's shadow on it, in a cool grey. */
private const val FLOOR = 0.075f
private const val SHADOW = 0.14f
private const val SHADOW_LENGTH = 3f
private const val SHADOW_GREEN = 0.06f
private const val SHADOW_BLUE = 0.16f

/** How far the mark settles into the floor. */
private const val SINK = 0.6f

private val SHADER = """
uniform shader content;
uniform float2 size;      // dp
uniform float room;       // px from the layer's edge to the pressed thing's
uniform float density;    // px per dp
uniform float depth;      // 0 at rest, 1 pressed in
uniform float4 wave0;     // origin x and y in dp, age in ms, strength
uniform float4 wave1;

const float TAU = 6.2831853;

float box(float2 p) {
    float2 q = abs(p - size * 0.5) - size * 0.5 + ${PressCorner.value};
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - ${PressCorner.value};
}

float packet(float d) {
    return sin(d * TAU / $WAVE_LENGTH) * exp(-d * d / (2.0 * $WAVE_WIDTH * $WAVE_WIDTH));
}

// The slope of the surface that a wave gives at p.
float2 slope(float2 p, float4 wave) {
    if (wave.w <= 0.0) return float2(0.0);
    float2 v = p - wave.xy;
    float r = max(length(v), 0.001);
    float d = r - wave.z * $WAVE_SPEED;
    float amp = wave.w * exp(-wave.z / $WAVE_DECAY_MILLIS) * smoothstep(0.0, $WAVE_ONSET_MILLIS, wave.z)
        / (1.0 + r / $WAVE_FALLOFF);
    return v / r * (packet(d + 0.5) - packet(d - 0.5)) * amp;
}

half4 main(float2 at) {
    float3 light = normalize(float3(-0.45, -0.75, 0.5)); // from the top left; y grows downward
    float3 halfway = normalize(light + float3(0.0, 0.0, 1.0));
    float2 p = (at - room) / density;
    float sd = box(p);
    float inside = 1.0 - smoothstep(-1.0, 0.5, sd * density);
    float2 g = (slope(p, wave0) + slope(p, wave1)) * inside;

    // The recess: a wall from the rim to the floor, sloping toward the outside.
    float t = clamp(-sd / $WALL, 0.0, 1.0);
    float floorness = t * t * (3.0 - 2.0 * t);
    float2 outward = float2(box(p + float2(0.5, 0.0)) - box(p - float2(0.5, 0.0)),
                            box(p + float2(0.0, 0.5)) - box(p - float2(0.0, 0.5)));
    outward /= max(length(outward), 0.0001);
    float2 wall = outward * depth * 6.0 * t * (1.0 - t) / $WALL * $WALL_TILT;
    float floorDepth = depth * floorness;
    float shadow = floorDepth * smoothstep(-1.2, 1.0, box(p + normalize(light.xy) * $SHADOW_LENGTH * depth));

    float2 bend = -g * $REFRACTION - float2(0.0, $SINK * floorDepth);
    float4 r = float4(content.eval(at + bend * (1.0 + $FRINGE) * density));
    float4 c = float4(content.eval(at + bend * density));
    float4 b = float4(content.eval(at + bend * (1.0 - $FRINGE) * density));
    float4 color = float4(r.r, c.g, b.b, max(c.a, max(r.a, b.a)));

    float dim = 1.0 - (1.0 - $FLOOR * floorDepth) * (1.0 - $SHADOW * shadow);
    color = color * (1.0 - dim) + float4(0.0, $SHADOW_GREEN, $SHADOW_BLUE, 1.0) * dim;

    float3 n = normalize(float3(-(g + wall) * $BUMP, 1.0));
    float shade = dot(n, light) - light.z;
    float glint = max(pow(max(dot(n, halfway), 0.0), $GLINT_SHARPNESS) - pow(halfway.z, $GLINT_SHARPNESS), 0.0);
    float dark = max(-shade, 0.0) * $SHADE;
    float lit = min(max(shade, 0.0) * $SHADE + glint * $GLINT, 1.0);
    color = color * (1.0 - dark) + float4(0.0, 0.0, 0.0, dark);
    color = color * (1.0 - lit) + float4(lit);
    return half4(color);
}
"""
