package com.watermelon.playback.vhs

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.watermelon.common.model.VhsTier

/**
 * Tier B — single-pass AGSL [RuntimeShader] for low-memory API 33+ devices.
 * Scanlines + light noise + basic jitter only; a single `eval` per pixel (no second sample).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AGSLLiteRenderer(context: Context) : VhsRenderer(VhsTier.B) {

    private val shader = RuntimeShader(LITE_SHADER)
    private var resolutionW = 1f
    private var resolutionH = 1f

    fun setResolution(width: Float, height: Float) {
        resolutionW = width
        resolutionH = height
        shader.setFloatUniform("resolution", width, height)
    }

    override fun onFrame(timeSeconds: Float) {
        shader.setFloatUniform("time", timeSeconds)
        // Tier B caps intensity to keep the cost low.
        shader.setFloatUniform("intensity", intensity * 0.6f)
        shader.setFloatUniform("resolution", resolutionW, resolutionH)
    }

    fun buildRenderEffect(): RenderEffect =
        RenderEffect.createRuntimeShaderEffect(shader, "inputShader")

    override fun release() { /* no native handle */ }

    companion object {
        const val LITE_SHADER = """
uniform shader inputShader;
uniform float time;
uniform float intensity;
uniform float2 resolution;

float rand(float2 co) {
    return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    half4 color = inputShader.eval(fragCoord);   // single pass — no second eval

    // Scanlines
    float scan = fract(uv.y * 240.0);
    if (scan < 0.5) { color *= 0.94; }

    // Light noise
    float noise = rand(uv * 10.0 + time * 6.0) * intensity * 0.08;
    color.rgb += noise;

    return color;
}
"""
    }
}
