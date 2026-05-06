package eu.darken.l2capsocketdemo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import eu.darken.l2capsocketdemo.attempts.Attempt
import eu.darken.l2capsocketdemo.attempts.AttemptA_PublicL2capChannel
import eu.darken.l2capsocketdemo.attempts.AttemptB_PublicL2capChannelInsecure
import eu.darken.l2capsocketdemo.attempts.AttemptC_SettingsTypeL2cap
import eu.darken.l2capsocketdemo.attempts.AttemptD_SettingsLePsm
import eu.darken.l2capsocketdemo.attempts.AttemptE_ReflectionNoBypass
import eu.darken.l2capsocketdemo.attempts.AttemptF_ReflectionWithBypass
import eu.darken.l2capsocketdemo.attempts.StageOutcome
import eu.darken.l2capsocketdemo.ui.theme.L2CAPSocketDemoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            L2CAPSocketDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DemoScreen()
                }
            }
        }
    }
}

private val ALL_ATTEMPTS: List<Attempt> = listOf(
    AttemptA_PublicL2capChannel(),
    AttemptB_PublicL2capChannelInsecure(),
    AttemptC_SettingsTypeL2cap(),
    AttemptD_SettingsLePsm(),
    AttemptE_ReflectionNoBypass(),
    AttemptF_ReflectionWithBypass(),
)

private const val DEFAULT_PSM_HEX = "1001"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val pairedDevices = remember(hasPermission) { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var psmText by remember { mutableStateOf(DEFAULT_PSM_HEX) }
    val outcomesByAttemptId: SnapshotStateMap<String, List<StageOutcome>> = remember { mutableStateMapOf() }
    val runningAttempts: SnapshotStateMap<String, Boolean> = remember { mutableStateMapOf() }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            pairedDevices.value = readPairedBredrDevices(context)
            if (selectedDevice == null) selectedDevice = pairedDevices.value.firstOrNull()
        }
    }

    val deviceInfo = selectedDevice?.let { runCatching { it.collectInfo() }.getOrNull() }
    val parsedPsm = psmText.trim().removePrefix("0x").toIntOrNull(16)

    fun runAttempt(attempt: Attempt) {
        val device = selectedDevice ?: return
        val psm = parsedPsm ?: return
        coroutineScope.launch {
            runningAttempts[attempt.id] = true
            val collected = mutableListOf<StageOutcome>()
            outcomesByAttemptId[attempt.id] = collected
            try {
                attempt.run(device, psm).collect { outcome ->
                    collected.add(outcome)
                    outcomesByAttemptId[attempt.id] = collected.toList()
                }
            } catch (t: Throwable) {
                collected.add(
                    StageOutcome(
                        stage = eu.darken.l2capsocketdemo.attempts.Stage.SOCKET_CREATE,
                        ok = false,
                        detail = "uncaught ${t::class.java.simpleName}: ${t.message}",
                        durationMs = 0L,
                    )
                )
                outcomesByAttemptId[attempt.id] = collected.toList()
            } finally {
                runningAttempts.remove(attempt.id)
            }
        }
    }

    fun runAll() {
        val device = selectedDevice ?: return
        val psm = parsedPsm ?: return
        coroutineScope.launch {
            for (attempt in ALL_ATTEMPTS) {
                runningAttempts[attempt.id] = true
                val collected = mutableListOf<StageOutcome>()
                outcomesByAttemptId[attempt.id] = collected
                try {
                    attempt.run(device, psm).collect { outcome ->
                        collected.add(outcome)
                        outcomesByAttemptId[attempt.id] = collected.toList()
                    }
                } catch (t: Throwable) {
                    collected.add(
                        StageOutcome(
                            stage = eu.darken.l2capsocketdemo.attempts.Stage.SOCKET_CREATE,
                            ok = false,
                            detail = "uncaught ${t::class.java.simpleName}: ${t.message}",
                            durationMs = 0L,
                        )
                    )
                    outcomesByAttemptId[attempt.id] = collected.toList()
                } finally {
                    runningAttempts.remove(attempt.id)
                }
            }
        }
    }

    fun copyDiagnostics() {
        val info = deviceInfo ?: return
        val psm = parsedPsm ?: return
        val results = ALL_ATTEMPTS.map { attempt ->
            Diagnostics.AttemptResult(
                attempt = attempt,
                outcomes = outcomesByAttemptId[attempt.id] ?: emptyList(),
            )
        }
        val md = Diagnostics.format(HostInfo(), info, psm, results)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("L2CAP demo diagnostics", md))
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Diagnostics copied — paste into the issue tracker comment")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("L2CAP Public API Demo", fontWeight = FontWeight.SemiBold)
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!hasPermission) {
            PermissionGate(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                onRequest = {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                },
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DevicePicker(
                devices = pairedDevices.value,
                selected = selectedDevice,
                onSelect = { selectedDevice = it },
            )

            OutlinedTextField(
                value = psmText,
                onValueChange = { psmText = it },
                label = { Text("PSM (hex, e.g. 1001)") },
                supportingText = {
                    if (parsedPsm == null) {
                        Text("Invalid hex.")
                    } else {
                        Text("= $parsedPsm decimal. AirPods AAP uses 0x1001.")
                    }
                },
                isError = parsedPsm == null,
                modifier = Modifier.fillMaxWidth(),
            )

            deviceInfo?.let { DeviceInfoCard(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = ::runAll,
                    enabled = selectedDevice != null && parsedPsm != null && runningAttempts.isEmpty(),
                ) { Text("Run all sequentially") }
                OutlinedButton(
                    onClick = ::copyDiagnostics,
                    enabled = selectedDevice != null && parsedPsm != null,
                ) { Text("Copy diagnostics") }
            }

            HorizontalDivider()

            ALL_ATTEMPTS.forEach { attempt ->
                AttemptCard(
                    attempt = attempt,
                    outcomes = outcomesByAttemptId[attempt.id] ?: emptyList(),
                    running = runningAttempts[attempt.id] == true,
                    enabled = selectedDevice != null && parsedPsm != null && runningAttempts.isEmpty(),
                    onRun = { runAttempt(attempt) },
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(modifier: Modifier, onRequest: () -> Unit) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "BLUETOOTH_CONNECT required",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Needed to read paired BR/EDR devices and to attempt socket connections.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePicker(
    devices: List<BluetoothDevice>,
    selected: BluetoothDevice?,
    onSelect: (BluetoothDevice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (devices.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("No paired BR/EDR devices found.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pair AirPods (or another BR/EDR earbud) via system Bluetooth settings, then return to this app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    @SuppressLint("MissingPermission")
    fun label(d: BluetoothDevice): String = (runCatching { d.name }.getOrNull() ?: "(unknown)") + " · ${d.address}"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextField(
            value = selected?.let { label(it) } ?: "Select a device",
            onValueChange = {},
            readOnly = true,
            label = { Text("Target device") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            devices.forEach { device ->
                DropdownMenuItem(
                    text = { Text(label(device)) },
                    onClick = {
                        onSelect(device)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(info: DeviceInfo) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Device info",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "hide" else "show")
                }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                MonoLine("name: ${info.name ?: "(unknown)"}")
                MonoLine("address: ${info.address}")
                MonoLine("bond: ${info.bondState}")
                MonoLine("type: ${info.type}")
                MonoLine("SDP UUIDs:")
                if (info.sdpUuids.isEmpty()) {
                    MonoLine("  (none cached — bond may not have completed SDP)")
                } else {
                    info.sdpUuids.forEach { MonoLine("  $it") }
                }
                Spacer(Modifier.height(4.dp))
                val host = HostInfo()
                MonoLine("host: ${host.manufacturer} ${host.model}")
                MonoLine("Android ${host.androidRelease} / API ${host.sdkInt}")
            }
        }
    }
}

@Composable
private fun AttemptCard(
    attempt: Attempt,
    outcomes: List<StageOutcome>,
    running: Boolean,
    enabled: Boolean,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "(${attempt.id}) ",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    attempt.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = onRun, enabled = enabled && !running) { Text("Run") }
            }
            Spacer(Modifier.height(4.dp))
            Text(attempt.description, style = MaterialTheme.typography.bodySmall)

            if (outcomes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                outcomes.forEach { o ->
                    val color = if (o.ok) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Row {
                        Text(
                            text = o.symbol,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(20.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${o.stage.label} (${o.durationMs}ms)",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                o.detail,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonoLine(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        style = MaterialTheme.typography.bodySmall,
    )
}

@SuppressLint("MissingPermission")
private fun readPairedBredrDevices(context: Context): List<BluetoothDevice> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
    val adapter: BluetoothAdapter = manager.adapter ?: return emptyList()
    if (!adapter.isEnabled) return emptyList()
    return adapter.bondedDevices?.filter { device ->
        // Show CLASSIC and DUAL — both can host BR/EDR L2CAP. LE-only devices can't.
        device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || device.type == BluetoothDevice.DEVICE_TYPE_DUAL
    }?.sortedBy { runCatching { it.name }.getOrNull() ?: it.address } ?: emptyList()
}
