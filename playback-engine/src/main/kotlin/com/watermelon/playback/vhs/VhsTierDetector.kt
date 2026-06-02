package com.watermelon.playback.vhs

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.watermelon.common.model.VhsTier

/**
 * Runtime probe that resolves the VHS rendering tier once at startup. Frozen contract —
 * Handover §2.3 / Manifest §4.3. The result is immutable for the session lifetime.
 *
 * - API < 33 (no AGSL)            → Tier C (PNG overlay)
 * - API 33+ and low-memory device → Tier B (single-pass AGSL)
 * - API 33+ and ample RAM         → Tier A (full multi-pass AGSL)
 */
object VhsTierDetector {

    fun resolveVhsTier(context: Context): VhsTier {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return VhsTier.C

        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return when {
            memoryInfo.lowMemory -> VhsTier.B
            else -> VhsTier.A
        }
    }
}
