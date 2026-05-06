package eu.darken.l2capsocketdemo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build

data class DeviceInfo(
    val name: String?,
    val address: String,
    val bondState: String,
    val type: String,
    val sdpUuids: List<String>,
)

@SuppressLint("MissingPermission")
fun BluetoothDevice.collectInfo(): DeviceInfo = DeviceInfo(
    name = runCatching { name }.getOrNull(),
    address = address,
    bondState = when (bondState) {
        BluetoothDevice.BOND_NONE -> "NONE"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        BluetoothDevice.BOND_BONDED -> "BONDED"
        else -> "UNKNOWN($bondState)"
    },
    type = when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
        BluetoothDevice.DEVICE_TYPE_LE -> "LE"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
        BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "UNKNOWN"
        else -> "UNKNOWN($type)"
    },
    sdpUuids = uuids?.map { it.uuid.toString() } ?: emptyList(),
)

data class HostInfo(
    val androidRelease: String = Build.VERSION.RELEASE,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val sdkCodename: String = Build.VERSION.CODENAME,
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val device: String = Build.DEVICE,
    val fingerprint: String = Build.FINGERPRINT,
)
