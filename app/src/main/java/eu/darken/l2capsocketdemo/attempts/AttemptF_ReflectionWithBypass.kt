package eu.darken.l2capsocketdemo.attempts

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import eu.darken.l2capsocketdemo.aap.MinimalAapProbe
import eu.darken.l2capsocketdemo.bypass.HiddenApiBypass
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * (f) Reflection on BluetoothDevice.createInsecureL2capSocket(int) WITH non-SDK-interface bypass.
 *
 * Same hidden method as (e), but first invokes [HiddenApiBypass] to call
 * VMRuntime.setHiddenApiExemptions("Landroid/bluetooth/") via an ART method-pointer
 * swap (LSPosed-style). After the bypass, the method is reachable via plain reflection
 * and the resulting socket connects and exchanges AAP frames.
 *
 * This is the demonstration that the platform supports the transport — only the API is
 * missing. It is DIAGNOSTIC ONLY and not a recommended production path.
 */
@SuppressLint("MissingPermission")
class AttemptF_ReflectionWithBypass : Attempt {
    override val id = "f"
    override val title = "reflection on createInsecureL2capSocket + setHiddenApiExemptions bypass"
    override val description =
        "Diagnostic only — proves the platform's transport already supports BR/EDR L2CAP on this PSM."

    override fun run(device: BluetoothDevice, psm: Int): Flow<StageOutcome> = flow {
        // --- Bypass + method lookup (one combined stage; either succeeds or aborts) ---
        val lookupStart = SystemClock.elapsedRealtime()
        val method = try {
            HiddenApiBypass.setExemptions("Landroid/bluetooth/")
            BluetoothDevice::class.java.getDeclaredMethod(
                "createInsecureL2capSocket",
                Int::class.javaPrimitiveType,
            )
        } catch (e: Throwable) {
            emit(
                StageOutcome(
                    Stage.METHOD_LOOKUP, ok = false,
                    detail = "${e::class.java.simpleName}: ${e.message ?: "(no message)"}",
                    durationMs = SystemClock.elapsedRealtime() - lookupStart,
                )
            )
            return@flow
        }
        emit(
            StageOutcome(
                Stage.METHOD_LOOKUP, ok = true,
                detail = "bypass applied; method visible to reflection",
                durationMs = SystemClock.elapsedRealtime() - lookupStart,
            )
        )

        // --- Socket creation (invoke the hidden method) ---
        val createStart = SystemClock.elapsedRealtime()
        val socket = try {
            method.invoke(device, psm) as BluetoothSocket
        } catch (e: Throwable) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            emit(
                StageOutcome(
                    Stage.SOCKET_CREATE, ok = false,
                    detail = "${cause::class.java.simpleName}: ${cause.message ?: "(no message)"}",
                    durationMs = SystemClock.elapsedRealtime() - createStart,
                )
            )
            return@flow
        }
        emit(
            StageOutcome(
                Stage.SOCKET_CREATE, ok = true,
                detail = "type=${socket.connectionType} (TYPE_L2CAP=3, BR/EDR)",
                durationMs = SystemClock.elapsedRealtime() - createStart,
            )
        )

        // --- Connect ---
        coroutineScope {
            val connectStart = SystemClock.elapsedRealtime()
            val connectResult = connectWithTimeout(socket, timeoutMillis = 5000L, scope = this)
            val connectDuration = SystemClock.elapsedRealtime() - connectStart
            connectResult.fold(
                onFailure = { e ->
                    emit(
                        StageOutcome(
                            Stage.CONNECT, ok = false,
                            detail = "${e::class.java.simpleName}: ${e.message}",
                            durationMs = connectDuration,
                        )
                    )
                    runCatching { socket.close() }
                    return@coroutineScope
                },
                onSuccess = {
                    emit(
                        StageOutcome(
                            Stage.CONNECT, ok = true,
                            detail = "connected",
                            durationMs = connectDuration,
                        )
                    )
                },
            )

            // --- AAP probe (write handshake, read whatever comes back) ---
            val probeStart = SystemClock.elapsedRealtime()
            val probe = MinimalAapProbe.probe(socket, this)
            val probeDuration = SystemClock.elapsedRealtime() - probeStart
            probe.fold(
                onSuccess = { detail ->
                    val gotBytes = detail.contains("read") &&
                        !detail.contains("read 0 bytes") &&
                        !detail.contains("EOF")
                    emit(
                        StageOutcome(
                            Stage.AAP_VERIFY, ok = gotBytes,
                            detail = detail,
                            durationMs = probeDuration,
                        )
                    )
                },
                onFailure = { e ->
                    emit(
                        StageOutcome(
                            Stage.AAP_VERIFY, ok = false,
                            detail = "${e::class.java.simpleName}: ${e.message}",
                            durationMs = probeDuration,
                        )
                    )
                },
            )
            runCatching { socket.close() }
        }
    }
}
