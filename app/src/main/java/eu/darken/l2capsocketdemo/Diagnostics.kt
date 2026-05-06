package eu.darken.l2capsocketdemo

import eu.darken.l2capsocketdemo.attempts.Attempt
import eu.darken.l2capsocketdemo.attempts.StageOutcome

/**
 * Formats the demo's results as a markdown block ready to paste into the Google
 * issue tracker comment. Self-contained — includes host + target device context.
 */
object Diagnostics {

    fun format(
        host: HostInfo,
        device: DeviceInfo,
        psm: Int,
        results: List<AttemptResult>,
    ): String = buildString {
        appendLine("## L2CAP Public API Demo — results")
        appendLine()
        appendLine("**target device**")
        appendLine("- name: ${device.name ?: "(unknown)"}")
        appendLine("- address: ${device.address}")
        appendLine("- bond: ${device.bondState}")
        appendLine("- type: ${device.type}")
        appendLine("- SDP UUIDs: " + (device.sdpUuids.takeIf { it.isNotEmpty() }?.joinToString() ?: "(none)"))
        appendLine("- target PSM: 0x${"%X".format(psm)} ($psm)")
        appendLine()
        appendLine("**host**")
        appendLine("- ${host.manufacturer} ${host.model} (${host.device})")
        appendLine("- Android ${host.androidRelease} / API ${host.sdkInt} (codename ${host.sdkCodename})")
        appendLine("- fingerprint: `${host.fingerprint}`")
        appendLine()
        appendLine("**results**")
        appendLine()

        results.forEach { result ->
            appendLine("(${result.attempt.id}) ${result.attempt.title}")
            if (result.outcomes.isEmpty()) {
                appendLine("    (not run)")
            } else {
                result.outcomes.forEach { o ->
                    appendLine("    ${o.symbol} ${o.stage.label}: ${o.detail} (${o.durationMs}ms)")
                }
            }
            appendLine()
        }
    }

    data class AttemptResult(
        val attempt: Attempt,
        val outcomes: List<StageOutcome>,
    )
}
