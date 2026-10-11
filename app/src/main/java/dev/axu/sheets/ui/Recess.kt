package dev.axu.sheets.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/** Draws something pressed in as a recess in the surface, where the system can run shaders. */
internal interface Recess {
    /** The effect for something of [size] px, drawn [room] px in from its layer's edge, [depth] pressed in. */
    fun effect(size: Size, room: Float, density: Float, depth: Float): ComposeRenderEffect
}

/**
 * A narrow wall at the rim, lit on the side toward the light and shaded on the side away from it,
 * and a floor a little darker than the surface, with the rim's shadow across it and the mark settled
 * into it.
 */
internal fun recess(): Recess? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ShaderRecess() else null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShaderRecess : Recess {
    private val shader = RuntimeShader(SHADER)

    override fun effect(size: Size, room: Float, density: Float, depth: Float): ComposeRenderEffect {
        shader.setFloatUniform("size", size.width / density, size.height / density)
        shader.setFloatUniform("room", room)
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("depth", depth)
        // A render effect keeps the uniforms it was made with, so it's made again each frame.
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/* Tunables. Lengths are in dp, and the rest are fractions. */

/** From the rim down to the floor, and how steep that wall looks to the light. */
private const val WALL = 1.6f
private const val WALL_TILT = 0.525f

/** How much light and shade the wall catches. */
private const val SHADE = 0.55f
private const val GLINT = 0.7f
private const val GLINT_SHARPNESS = 70f

/** How much darker the floor is, and the rim's shadow on it, in a cool grey. */
private const val FLOOR = 0.075f
private const val SHADOW = 0.14f
private const val SHADOW_LENGTH = 3f
private const val SHADOW_GREEN = 0.06f
private const val SHADOW_BLUE = 0.16f

/** How far the mark settles into the floor, and how far red and blue part from green as it does. */
private const val SINK = 0.6f
private const val FRINGE = 0.4f

private val SHADER = """
uniform shader content;
uniform float2 size;      // dp
uniform float room;       // px from the layer's edge to the pressed thing's
uniform float density;    // px per dp
uniform float depth;      // 0 at rest, 1 pressed in

float box(float2 p) {
    float2 q = abs(p - size * 0.5) - size * 0.5 + ${PressCorner.value};
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - ${PressCorner.value};
}

half4 main(float2 at) {
    float3 light = normalize(float3(-0.45, -0.75, 0.5)); // from the top left; y grows downward
    float3 halfway = normalize(light + float3(0.0, 0.0, 1.0));
    float2 p = (at - room) / density;
    float sd = box(p);

    // A wall from the rim to the floor, sloping toward the outside.
    float t = clamp(-sd / $WALL, 0.0, 1.0);
    float floorness = t * t * (3.0 - 2.0 * t);
    float2 outward = float2(box(p + float2(0.5, 0.0)) - box(p - float2(0.5, 0.0)),
                            box(p + float2(0.0, 0.5)) - box(p - float2(0.0, 0.5)));
    outward /= max(length(outward), 0.0001);
    float2 wall = outward * depth * 6.0 * t * (1.0 - t) / $WALL * $WALL_TILT;
    float floorDepth = depth * floorness;
    float shadow = floorDepth * smoothstep(-1.2, 1.0, box(p + normalize(light.xy) * $SHADOW_LENGTH * depth));

    // Red and blue settle a little further and less far than green, like through a lens.
    float2 sink = float2(0.0, -$SINK * floorDepth * density);
    float4 r = float4(content.eval(at + sink * (1.0 + $FRINGE)));
    float4 c = float4(content.eval(at + sink));
    float4 b = float4(content.eval(at + sink * (1.0 - $FRINGE)));
    float4 color = float4(r.r, c.g, b.b, max(c.a, max(r.a, b.a)));
    float dim = 1.0 - (1.0 - $FLOOR * floorDepth) * (1.0 - $SHADOW * shadow);
    color = color * (1.0 - dim) + float4(0.0, $SHADOW_GREEN, $SHADOW_BLUE, 1.0) * dim;

    float3 n = normalize(float3(-wall, 1.0));
    float shade = dot(n, light) - light.z;
    float glint = max(pow(max(dot(n, halfway), 0.0), $GLINT_SHARPNESS) - pow(halfway.z, $GLINT_SHARPNESS), 0.0);
    float dark = max(-shade, 0.0) * $SHADE;
    float lit = min(max(shade, 0.0) * $SHADE + glint * $GLINT, 1.0);
    color = color * (1.0 - dark) + float4(0.0, 0.0, 0.0, dark);
    color = color * (1.0 - lit) + float4(lit);
    return half4(color);
}
"""
