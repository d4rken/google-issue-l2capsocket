package eu.darken.l2capsocketdemo.attempts

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * (d) BluetoothSocketSettings.Builder().setSocketType(TYPE_LE).setL2capPsm(psm)
 *
 * Even if the type is set to TYPE_LE (the only L2CAP type accepted by the Builder),
 * setL2capPsm() is annotated @IntRange(from=128, to=255) and validates:
 *
 *   if (l2capPsm < 128 || l2capPsm > 255) {
 *       throw new IllegalArgumentException("invalid L2cap PSM - " + l2capPsm);
 *   }
 *
 * AirPods' BR/EDR PSM is 0x1001 = 4097 — outside the LE-CoC dynamic range and rejected.
 */
class AttemptD_SettingsLePsm : Attempt {
    override val id = "d"
    override val title = "Settings.Builder.setSocketType(TYPE_LE).setL2capPsm(psm)"
    override val description =
        "Public API. setL2capPsm() is restricted to 128..255 (LE-CoC dynamic). 0x1001 = 4097 is rejected."

    override fun run(device: BluetoothDevice, psm: Int): Flow<StageOutcome> = flow {
        val start = SystemClock.elapsedRealtime()
        try {
            val builderClass = Class.forName("android.bluetooth.BluetoothSocketSettings\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            // TYPE_LE = 4 — public, the only L2CAP-style type the Builder accepts.
            builderClass
                .getMethod("setSocketType", Int::class.javaPrimitiveType)
                .invoke(builder, 4)
            builderClass
                .getMethod("setL2capPsm", Int::class.javaPrimitiveType)
                .invoke(builder, psm)
            emit(
                StageOutcome(
                    Stage.BUILDER, ok = true,
                    detail = "setL2capPsm($psm) accepted (unexpected — contract changed)",
                    durationMs = SystemClock.elapsedRealtime() - start,
                )
            )
        } catch (e: Throwable) {
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
