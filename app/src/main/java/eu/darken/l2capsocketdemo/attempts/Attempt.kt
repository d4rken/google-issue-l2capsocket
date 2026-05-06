package eu.darken.l2capsocketdemo.attempts

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.Flow

/**
 * One technique for opening a BR/EDR L2CAP socket. Each attempt emits stage outcomes
 * as it progresses so the UI can show *where* the technique fails, not just *that* it fails.
 *
 * Stages roughly follow: builder construction → method lookup → socket creation → connect → AAP probe.
 * Not every attempt has every stage — e.g. attempt (c) fails at builder time and never gets further.
 */
interface Attempt {
    val id: String
    val title: String
    val description: String

    fun run(device: BluetoothDevice, psm: Int): Flow<StageOutcome>
}

enum class Stage(val label: String) {
    BUILDER("builder"),
    METHOD_LOOKUP("method lookup"),
    SOCKET_CREATE("socket created"),
    CONNECT("connect"),
    AAP_VERIFY("AAP verify"),
}

data class StageOutcome(
    val stage: Stage,
    val ok: Boolean,
    val detail: String,
    val durationMs: Long,
) {
    val symbol: String get() = if (ok) "✓" else "✗"
}
