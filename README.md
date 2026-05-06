# L2CAP Socket Demo — public-API gap reproducer

Demonstrates that **Android has no public SDK API for opening an outgoing BR/EDR L2CAP client socket to a known PSM**, and that the platform's own hidden method works once you bypass non-SDK-interface restrictions.

Companion to [b/510252114](https://issuetracker.google.com/issues/510252114) — a Google issue tracker request asking for a sanctioned API. The submitted issue text is in [`ISSUE_TEXT.md`](ISSUE_TEXT.md).

## TL;DR

Six techniques. Five fail. The sixth — reflection on the platform's own hidden method, after applying a fragile ART method-pointer bypass — succeeds, connects to AirPods on PSM `0x1001`, and exchanges Apple Accessory Protocol bytes. **The transport works; only the API is missing.**

| # | Technique | Outcome |
|---|---|---|
| (a) | `BluetoothDevice.createL2capChannel(0x1001)` (public, API 29+) | fails — LE-only by construction |
| (b) | `createInsecureL2capChannel(0x1001)` (public, API 29+) | same — LE-only |
| (c) | `BluetoothSocketSettings.Builder.setSocketType(TYPE_L2CAP)` | `IllegalArgumentException` at builder time |
| (d) | `Builder.setSocketType(TYPE_LE).setL2capPsm(0x1001)` | `IllegalArgumentException` — PSM out of `[128, 255]` |
| (e) | reflection on hidden `createInsecureL2capSocket` (no bypass) | `NoSuchMethodException` — non-SDK restriction |
| (f) | reflection on hidden `createInsecureL2capSocket` + `setHiddenApiExemptions` bypass | succeeds, connects, reads AAP bytes |

## Why this matters

Apple AirPods (and many other vendors' true-wireless earbuds) expose a proprietary protocol — the Apple Accessory Protocol (AAP) — over **classic Bluetooth (BR/EDR) L2CAP on PSM 0x1001**. AAP is the only path to per-bud + case battery readings, ANC / transparency / conversation-awareness mode, ear detection, stem-press events, firmware version, and device rename. None of this is on HFP, A2DP, AVRCP, HID, GATT, or LE Audio.

Existing third-party Android apps that depend on this transport — [CAPod](https://github.com/d4rken-org/capod), MaterialPods, OpenPods, AirGuard — currently use reflection plus a `dalvik.system.VMRuntime.setHiddenApiExemptions` bypass implemented by swapping ART method pointers via `sun.misc.Unsafe`. It is fragile, breaks with each ART internals change, and is exactly the pattern Google's non-SDK-interface guidance asks third-party apps to file public-API requests for instead.

The transport-level half is already done — Google fixed [issuetracker.google.com/issues/371713238](https://issuetracker.google.com/issues/371713238) so handshake on PSM `0x1001` to AirPods now works. This repo's ticket asks for the API-surface half.

## How to use

### Build

Standard Android Studio project. minSdk 26, targetSdk 36 (compileSdk 36 minorApiLevel 1 for `BluetoothSocketSettings` visibility).

```sh
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Run

1. Pair AirPods (or another BR/EDR earbud / accessory) via system Bluetooth settings.
2. Open the app, grant `BLUETOOTH_CONNECT`, pick the device, leave PSM at `1001`.
3. Tap **Run all sequentially**.
4. Tap **Copy diagnostics**. Paste the markdown block into the issue tracker comment.

The result is a single block like this:

```
## L2CAP Public API Demo — results

target device
- name: AirPods Pro 2
- address: BC:D0:74:XX:XX:XX
- bond: BONDED
- type: DUAL
...

(a) createL2capChannel(psm)
    ✓ socket created: type=4 (TYPE_L2CAP_LE=4) (3ms)
    ✗ connect: IOException 'read failed, socket closed' (5012ms)
(b) createInsecureL2capChannel(psm)
    ✓ socket created: type=4 (TYPE_L2CAP_LE=4) (2ms)
    ✗ connect: IOException 'read failed, socket closed' (5004ms)
(c) Settings.Builder.setSocketType(TYPE_L2CAP)
    ✗ builder: IllegalArgumentException 'invalid socketType - 3' (4ms)
(d) Settings.Builder.setSocketType(TYPE_LE).setL2capPsm(psm)
    ✗ builder: IllegalArgumentException 'invalid L2cap PSM - 4097' (5ms)
(e) reflection on createInsecureL2capSocket — no bypass
    ✗ method lookup: NoSuchMethodException (1ms)
(f) reflection on createInsecureL2capSocket + setHiddenApiExemptions bypass
    ✓ method lookup: bypass applied; method visible to reflection (12ms)
    ✓ socket created: type=3 (TYPE_L2CAP=3, BR/EDR) (1ms)
    ✓ connect (812ms)
    ✓ AAP verify: wrote handshake; read 16 bytes: 04 00 04 00 1B 00 ... (157ms)
```

## Source layout

```
app/src/main/java/eu/darken/l2capsocketdemo/
  MainActivity.kt              # Compose UI: device picker + 6 buttons + result panels
  DeviceInfo.kt                # bond/type/SDP UUIDs/build collection
  Diagnostics.kt               # markdown formatter for the copy-paste block
  attempts/                    # one Kotlin file per technique
    Attempt.kt                 # interface + StageOutcome data class
    SocketTimeout.kt           # close-from-watchdog helper
    AttemptA_PublicL2capChannel.kt
    AttemptB_PublicL2capChannelInsecure.kt
    AttemptC_SettingsTypeL2cap.kt
    AttemptD_SettingsLePsm.kt
    AttemptE_ReflectionNoBypass.kt
    AttemptF_ReflectionWithBypass.kt
  bypass/                      # LSPosed-style hidden-API bypass — diagnostic only
    HiddenApiBypass.kt
    ArtMirror.kt
    NeverCall.java
  aap/
    MinimalAapProbe.kt         # 16-byte AAP handshake + read-back, no state mutation
```

## Verifiable claims

All "the API rejects this" claims in `ISSUE_TEXT.md` are checked against AOSP `packages/modules/Bluetooth` `main`:

- [`BluetoothDevice.java`](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/framework/java/android/bluetooth/BluetoothDevice.java) — `createL2capChannel` / `createInsecureL2capChannel` use `TYPE_L2CAP_LE`; javadoc: *"The supported Bluetooth transport is LE only."* `createL2capSocket` / `createInsecureL2capSocket` are `@hide`.
- [`BluetoothSocketSettings.java`](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/framework/java/android/bluetooth/BluetoothSocketSettings.java) — `Builder.setSocketType()` throws `IllegalArgumentException` if the type isn't `TYPE_RFCOMM` or `TYPE_LE`. `setL2capPsm()` is `@IntRange(from=128, to=255)`.
- [`BluetoothSocket.java`](https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/Bluetooth/framework/java/android/bluetooth/BluetoothSocket.java) — `TYPE_L2CAP = 3` (BR/EDR), `TYPE_LE = TYPE_L2CAP_LE = 4` (LE-CoC).

## Licensing

Apache-2.0. The `bypass/` files are derived verbatim from [LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) (Apache-2.0); attribution is preserved in each file's header. See [`LICENSE`](LICENSE).
