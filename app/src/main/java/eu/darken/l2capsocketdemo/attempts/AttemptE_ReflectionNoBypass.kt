package eu.darken.l2capsocketdemo.attempts

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * (e) Reflection on BluetoothDevice.createInsecureL2capSocket(int) without any non-SDK bypass.
 *
 * The hidden method is on the Blocked list. On Android 9+ the runtime hides such methods from
 * reflection: getDeclaredMethod() throws NoSuchMethodException (a regular reflective exception),
 * not NoSuchMethodError (a linkage error). On older or relaxed-enforcement Android versions
 * the lookup may succeed and invoke() may then throw IllegalAccessException — we capture
 * whichever happens.
 */
@SuppressLint("MissingPermission")
class AttemptE_ReflectionNoBypass : Attempt {
    override val id = "e"
    override val title = "reflection on createInsecureL2capSocket — no bypass"
    override val description =
        "Direct reflection on BluetoothDevice.createInsecureL2capSocket(int). " +
            "Blocked by non-SDK-interface restrictions on modern Android."

    override fun run(device: BluetoothDevice, psm: Int): Flow<StageOutcome> = flow {
        val lookupStart = SystemClock.elapsedRealtime()
        val method = try {
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
                detail = "method object obtained (lookup not blocked on this device)",
                durationMs = SystemClock.elapsedRealtime() - lookupStart,
            )
        )

        val invokeStart = SystemClock.elapsedRealtime()
        val socket = try {
            method.invoke(device, psm) as BluetoothSocket
        } catch (e: Throwable) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            emit(
                StageOutcome(
                    Stage.SOCKET_CREATE, ok = false,
                    detail = "${cause::class.java.simpleName}: ${cause.message ?: "(no message)"}",
                    durationMs = SystemClock.elapsedRealtime() - invokeStart,
                )
            )
            return@flow
        }
        runCatching { socket.close() }
        emit(
            StageOutcome(
                Stage.SOCKET_CREATE, ok = true,
                detail = "socket created (no bypass needed on this device — non-SDK enforcement loose?)",
                durationMs = SystemClock.elapsedRealtime() - invokeStart,
            )
        )
    }
}
