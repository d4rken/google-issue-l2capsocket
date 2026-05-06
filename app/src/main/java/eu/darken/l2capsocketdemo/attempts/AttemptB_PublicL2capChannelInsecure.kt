package eu.darken.l2capsocketdemo.attempts

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import eu.darken.l2capsocketdemo.aap.MinimalAapProbe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * (b) Public API: BluetoothDevice.createInsecureL2capChannel(int psm)
 *
 * Insecure variant of (a). Same TYPE_L2CAP_LE hardcoding in AOSP, same LE-only outcome.
 * Demonstrates that opting out of auth/encrypt does not change the transport.
 */
@SuppressLint("MissingPermission")
class AttemptB_PublicL2capChannelInsecure : Attempt {
    override val id = "b"
    override val title = "createInsecureL2capChannel(psm)"
    override val description =
        "Public API since API 29. Same LE-only transport as (a); auth/encrypt off does not help."

    override fun run(device: BluetoothDevice, psm: Int): Flow<StageOutcome> = flow {
        val createStart = SystemClock.elapsedRealtime()
        val socket = try {
            device.createInsecureL2capChannel(psm)
        } catch (t: Throwable) {
            emit(
                StageOutcome(
                    Stage.SOCKET_CREATE, ok = false,
                    detail = "${t::class.java.simpleName}: ${t.message}",
                    durationMs = SystemClock.elapsedRealtime() - createStart,
                )
            )
            return@flow
        }
        emit(
            StageOutcome(
                Stage.SOCKET_CREATE, ok = true,
                detail = "type=${socket.connectionType} (TYPE_L2CAP_LE=4)",
                durationMs = SystemClock.elapsedRealtime() - createStart,
            )
        )

        coroutineScope {
            val connectStart = SystemClock.elapsedRealtime()
            val connectResult = connectWithTimeout(socket, timeoutMillis = 5000L, scope = this)
            val connectDuration = SystemClock.elapsedRealtime() - connectStart
            connectResult.fold(
                onSuccess = {
                    emit(
                        StageOutcome(
                            Stage.CONNECT, ok = true,
                            detail = "connected (unexpected — peer must support LE-CoC)",
                            durationMs = connectDuration,
                        )
                    )
                    val probeStart = SystemClock.elapsedRealtime()
                    val probe = MinimalAapProbe.probe(socket, this)
                    emit(
                        StageOutcome(
                            Stage.AAP_VERIFY,
                            ok = probe.isSuccess,
                            detail = probe.getOrElse { it.message ?: "probe failed" }.toString(),
                            durationMs = SystemClock.elapsedRealtime() - probeStart,
                        )
                    )
                },
                onFailure = { e ->
                    emit(
                        StageOutcome(
                            Stage.CONNECT, ok = false,
                            detail = "${e::class.java.simpleName}: ${e.message}",
                            durationMs = connectDuration,
                        )
                    )
                },
            )
            runCatching { socket.close() }
        }
    }
}
