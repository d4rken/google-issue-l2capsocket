package eu.darken.l2capsocketdemo.aap

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal "the connection actually works" check. Writes the AAP handshake (16 bytes,
 * documented by MagicPodsCore / LibrePods) and reads up to 64 bytes of whatever the
 * device sends back within [readTimeoutMillis]. We do not parse the response — the
 * point is only to prove a real bidirectional channel.
 *
 * Read-only with respect to device state: the handshake is the same one CAPod / OpenPods
 * / MaterialPods send on every connect; it does not mutate any setting.
 */
object MinimalAapProbe {

    private val HANDSHAKE = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    suspend fun probe(
        socket: BluetoothSocket,
        scope: CoroutineScope,
        readTimeoutMillis: Long = 2000L,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            socket.outputStream.write(HANDSHAKE)
            socket.outputStream.flush()

            val watchdog: Job = scope.launch(Dispatchers.IO) {
                delay(readTimeoutMillis)
                runCatching { socket.close() }
            }
            val buf = ByteArray(64)
            val n = runCatching { socket.inputStream.read(buf) }
            watchdog.cancel()

            n.fold(
                onSuccess = { count ->
                    if (count <= 0) "wrote handshake; stream returned EOF"
                    else "wrote handshake; read $count bytes: " +
                        buf.copyOfRange(0, count.coerceAtMost(buf.size))
                            .joinToString(" ") { "%02X".format(it) }
                },
                onFailure = { e ->
                    "wrote handshake; no inbound bytes within ${readTimeoutMillis}ms (${e.message})"
                },
            )
        }
    }
}
