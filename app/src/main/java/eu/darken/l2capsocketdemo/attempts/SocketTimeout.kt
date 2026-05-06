package eu.darken.l2capsocketdemo.attempts

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BluetoothSocket.connect() is uncancelable. Standard pattern:
 *   - launch a watchdog that closes the socket after [timeoutMillis]
 *   - the close kicks connect() out of its blocking call (it throws IOException)
 *   - cancel the watchdog if connect returns first
 */
suspend fun connectWithTimeout(
    socket: BluetoothSocket,
    timeoutMillis: Long,
    scope: CoroutineScope,
): Result<Unit> = withContext(Dispatchers.IO) {
    val watchdog: Job = scope.launch(Dispatchers.IO) {
        delay(timeoutMillis)
        runCatching { socket.close() }
    }
    val result = runCatching { socket.connect() }
    watchdog.cancel()
    result
}
