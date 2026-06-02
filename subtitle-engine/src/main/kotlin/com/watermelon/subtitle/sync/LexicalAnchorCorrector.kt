package com.watermelon.subtitle.sync

import com.watermelon.subtitle.sync.LinearDriftCorrector.Cue
import kotlin.math.max

/**
 * Lexical Anchor corrector (opt-in — Manifest §6.3). Handles non-linear drift by aligning
 * subtitle cues to detected speech segments ("anchors"). Between consecutive anchors the
 * offset is interpolated linearly, so drift that changes over the runtime is corrected
 * piecewise rather than with a single global shift.
 *
 * The .srt/.ass file is never modified; results are applied at render time only.
 */
class LexicalAnchorCorrector {

    /** A detected alignment point: the subtitle time and the true speech time it maps to. */
    data class Anchor(val subtitleMs: Long, val speechMs: Long)

    /**
     * Apply piecewise-linear correction defined by sorted [anchors]. With zero anchors the
     * cues are returned unchanged; with one anchor it degrades to a constant offset.
     */
    fun apply(cues: List<Cue>, anchors: List<Anchor>): List<Cue> {
        if (anchors.isEmpty()) return cues
        val sorted = anchors.sortedBy { it.subtitleMs }
        return cues.map { cue ->
            cue.copy(
                startMs = max(0L, correct(cue.startMs, sorted)),
                endMs = max(0L, correct(cue.endMs, sorted))
            )
        }
    }

    /** Map a subtitle timestamp to corrected time via interpolation between anchors. */
    private fun correct(timeMs: Long, anchors: List<Anchor>): Long {
        // Before the first anchor: shift by the first anchor's offset.
        val first = anchors.first()
        if (timeMs <= first.subtitleMs) {
            return timeMs + (first.speechMs - first.subtitleMs)
        }
        // After the last anchor: shift by the last anchor's offset.
        val last = anchors.last()
        if (timeMs >= last.subtitleMs) {
            return timeMs + (last.speechMs - last.subtitleMs)
        }
        // Between two anchors: linearly interpolate the offset.
        for (i in 0 until anchors.lastIndex) {
            val a = anchors[i]
            val b = anchors[i + 1]
            if (timeMs in a.subtitleMs..b.subtitleMs) {
                val span = (b.subtitleMs - a.subtitleMs).toDouble().coerceAtLeast(1.0)
                val t = (timeMs - a.subtitleMs) / span
                val offsetA = a.speechMs - a.subtitleMs
                val offsetB = b.speechMs - b.subtitleMs
                val offset = offsetA + ((offsetB - offsetA) * t)
                return timeMs + offset.toLong()
            }
        }
        return timeMs
    }
}
