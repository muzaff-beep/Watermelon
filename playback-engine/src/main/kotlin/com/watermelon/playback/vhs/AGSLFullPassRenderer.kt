package com.watermelon.playback.vhs

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.watermelon.common.model.VhsTier

/**
 * Tier A — full multi-pass AGSL [RuntimeShader]: scanlines, tape noise + colour bleed,
 * tracking jitter, and a subtle reverse-tracking flicker. Applied as a post-processing
 * overlay on the player surface via [RenderEffect.createRuntimeShaderEffect]. API 33+.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AGSLFullPassRenderer(context: Context) : VhsRenderer(VhsTier.A) {

    private val shader = RuntimeShader(VHS_SHADER)
    private var resolutionW = 1f
    private var resolutionH = 1f

    fun setResolution(width: Float, height: Float) {
        resolutionW = width
        resolutionH = height
        shader.setFloatUniform("resolution", width, height)
    }

    override fun onFrame(timeSeconds: Float) {
        shader.setFloatUniform("time", timeSeconds)
        shader.setFloatUniform("intensity", intensity)
        shader.setFloatUniform("resolution", resolutionW, resolutionH)
    }

    /** Build the RenderEffect to assign to the player surface's graphicsLayer. */
    fun buildRenderEffect(): RenderEffect =
        RenderEffect.createRuntimeShaderEffect(shader, "inputShader")

    override fun release() { /* RuntimeShader holds no closable native handle */ }

    companion object {
        // Frozen AGSL source — Manifest §4.3 (full multi-pass capable, Tier A).
        const val VHS_SHADER = """
uniform shader inputShader;      // video frame
uniform float time;              // animated time (for noise/jitter)
uniform float intensity;         // 0.0–1.0 from settings
uniform float2 resolution;

// Simple rand helper (AGSL)
float rand(float2 co) {
    return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;

    // Sample original frame
    half4 color = inputShader.eval(fragCoord);

    // 1. Horizontal scanlines (~240 lines typical VHS)
    float scan = fract(uv.y * 240.0);
    if (scan < 0.5) {
        color *= 0.92;                          // darken even lines
    }

    // 2. Tape noise + colour bleed
    float noise = rand(uv * 12.0 + time * 8.0) * intensity * 0.15;
    color.r += noise * 0.3;                     // red bleed
    color.b += noise * 0.2;

    // 3. Tracking jitter (vertical offset)
    float jitter = sin(time * 25.0) * 0.003 * intensity;
    half4 jittered = inputShader.eval(fragCoord + float2(0.0, jitter * resolution.y));

    // 4. Final mix + subtle reverse-tracking flicker
    color = mix(color, jittered, 0.15);

    return color;
}
"""
    }
}
