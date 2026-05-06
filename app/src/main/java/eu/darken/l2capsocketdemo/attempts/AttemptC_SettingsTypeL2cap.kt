package eu.darken.l2capsocketdemo.attempts

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * (c) BluetoothSocketSettings.Builder().setSocketType(TYPE_L2CAP)
 *
 * BluetoothSocketSettings is the team's new direction for socket creation
 * (FlaggedApi(Flags.FLAG_SOCKET_SETTINGS_API)). Builder.setSocketType()
 * accepts only TYPE_RFCOMM (1) and TYPE_LE (4); TYPE_L2CAP (3) for BR/EDR is rejected:
 *
 *   if (socketType != BluetoothSocket.TYPE_RFCOMM
 *           && socketType != BluetoothSocket.TYPE_LE) {
 *       throw new IllegalArgumentException("invalid socketType - " + socketType);
 *   }
 *
 * On Android versions without BluetoothSocketSettings, this fails at class lookup instead.
 * Reflection used so the demo runs across versions without compileSdk pinning.
 */
class AttemptC_SettingsTypeL2cap : Attempt {
    override val id = "c"
    override val title = "Settings.Builder.setSocketType(TYPE_L2CAP)"
    override val description =
        "Public API. Builder rejects TYPE_L2CAP — only TYPE_RFCOMM and TYPE_LE are accepted."

    override fun run(device: BluetoothDevice, psm: Int): Flow<StageOutcome> = flow {
        val start = SystemClock.elapsedRealtime()
        try {
            val builderClass = Class.forName("android.bluetooth.BluetoothSocketSettings\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            // TYPE_L2CAP = 3 — see android.bluetooth.BluetoothSocket
            builderClass
                .getMethod("setSocketType", Int::class.javaPrimitiveType)
                .invoke(builder, 3)
            // If we reach this line on a real device, the API contract has changed.
            emit(
                StageOutcome(
                    Stage.BUILDER, ok = true,
                    detail = "setSocketType(TYPE_L2CAP) accepted (unexpected — contract changed)",
                    durationMs = SystemClock.elapsedRealtime() - start,
                )
            )
        } catch (e: Throwable) {
            // Unwrap InvocationTargetException so the IllegalArgumentException surfaces in the UI.
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            emit(
                StageOutcome(
                    Stage.BUILDER, ok = false,
                    detail = "${cause::class.java.simpleName}: ${cause.message}",
                    durationMs = SystemClock.elapsedRealtime() - start,
                )
            )
        }
    }
}
