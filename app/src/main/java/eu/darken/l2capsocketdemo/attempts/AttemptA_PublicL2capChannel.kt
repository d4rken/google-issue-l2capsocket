package eu.darken.l2capsocketdemo.attempts

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import eu.darken.l2capsocketdemo.aap.MinimalAapProbe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * (a) Public API: BluetoothDevice.createL2capChannel(int psm)
 *
 * Available since API 29. Looks like the right method by name, but the AOSP source
 * hardcodes TYPE_L2CAP_LE — javadoc states "The supported Bluetooth transport is LE only."
 * Connecting to a BR/EDR-only peer (e.g. AirPods) on this socket fails at connect time.
 */
@SuppressLint("MissingPermission")
class AttemptA_PublicL2capChannel : Attempt {
    override val id = "a"
    override val title = "createL2capChannel(psm)"
    override val description =
        "Public API since API 29. Hardcoded TYPE_L2CAP_LE in AOSP — LE-only by construction."

    override fun run(device: BluetoothDevice, psm: Int): Flow<StageOutcome> = flow {
        val createStart = SystemClock.elapsedRealtime()
        val socket = try {
            device.createL2capChannel(psm)
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
