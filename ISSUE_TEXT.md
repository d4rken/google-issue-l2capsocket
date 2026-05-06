# Public SDK API for outgoing BR/EDR L2CAP client sockets

> **Filing instructions (not part of the ticket body):** copy from "Summary" through "Minimal repro" into a new issue under *Android Public Tracker → Frameworks → Bluetooth*. Reference [b/371713238](https://issuetracker.google.com/issues/371713238) so the reviewer can see the transport-fix half is already done and this ticket is the API half. Severity P3 (feature request). Attach the demo APK + repo link.

## Summary

There is no public Android SDK API that lets a third-party app open an outgoing **BR/EDR L2CAP** client socket to a known PSM. The functionality exists internally (`BluetoothDevice.createInsecureL2capSocket(int)`) and works at the transport layer (b/371713238 was fixed in upcoming Android), but it is `@hide` and therefore reachable only via reflection plus a non-SDK-interface bypass.

The new `BluetoothSocketSettings` API (`@FlaggedApi(FLAG_SOCKET_SETTINGS_API)`) does **not** close this gap: `Builder.setSocketType()` accepts only `TYPE_RFCOMM` and `TYPE_LE`, and `setL2capPsm()` is annotated `@IntRange(from=128, to=255)` — the LE-CoC dynamic PSM range. BR/EDR PSMs (e.g. Apple's `0x1001` = 4097) cannot be expressed.

## Use case

Companion apps for true-wireless earbuds need a BR/EDR L2CAP channel on a vendor-specific PSM to access features that no Bluetooth profile exposes:

- **Apple AirPods / Beats** — Apple Accessory Protocol (AAP) on **PSM 0x1001**. Surfaces per-bud + case battery, ear-detection, ANC / transparency / conversation-awareness mode, stem-press events, firmware version, device rename. None of this is available via HFP, A2DP, AVRCP, HID, GATT, or LE Audio profiles.
- The same class of vendor-specific BR/EDR L2CAP companion protocols is used by other earbud vendors.

Open-source Android apps already depending on this transport include [CAPod](https://github.com/d4rken-org/capod), MaterialPods, OpenPods, and AirGuard (for tracker-detection signals from AirPods).

The platform supports the transport. The only thing missing is a sanctioned API to reach it.

## Why public alternatives don't substitute

| Suggested alternative | Why it doesn't fit |
|---|---|
| `BluetoothDevice.createL2capChannel(int)` / `createInsecureL2capChannel(int)` | LE-only by construction. Source uses `TYPE_L2CAP_LE`; javadoc states *"The supported Bluetooth transport is LE only."* AirPods advertise the PSM on BR/EDR. |
| `BluetoothSocketSettings.Builder` (TYPE_LE) | PSM range capped at 128..255 (LE dynamic range). 0x1001 is rejected at build time. |
| HFP vendor commands (`BluetoothHeadset.sendVendorSpecificResultCode`) | AT-command channel scoped to `+ANDROID` events; not a transport for arbitrary vendor protocols. |
| A2DP / AVRCP | Media + control profiles. No public vendor command channel. |
| GATT / LE Audio | Different transport and protocol stack. AAP is BR/EDR L2CAP, not GATT. |
| `createRfcommSocketToServiceRecord(UUID)` | Wrong transport (RFCOMM, not L2CAP). The vendor protocol is on L2CAP. |

The current path: reflection on `BluetoothDevice.createInsecureL2capSocket(int)` plus a `dalvik.system.VMRuntime.setHiddenApiExemptions` bypass implemented by swapping ART method pointers via `sun.misc.Unsafe` (LSPosed-style). It is fragile — every change to ART internals risks breaking it — and it's exactly the pattern Google's non-SDK-interface guidance asks third-party apps to file public-API requests for instead.

Reference implementation in production: [`L2capSocketFactory.kt`](https://github.com/d4rken-org/capod/blob/main/app/src/main/java/eu/darken/capod/common/bluetooth/l2cap/L2capSocketFactory.kt).

## Ask

### Primary (preferred): extend `BluetoothSocketSettings` to BR/EDR L2CAP

Aligns with the API direction the team is already shipping under `FLAG_SOCKET_SETTINGS_API`.

1. `BluetoothSocketSettings.Builder.setSocketType()`: also accept `BluetoothSocket.TYPE_L2CAP` (= 3). Validate the PSM/auth/encrypt combination in `build()` (or at `BluetoothDevice.createUsingSocketSettings()` time) so builder-call ordering doesn't matter.
2. PSM accepted for `TYPE_L2CAP` should cover valid BR/EDR L2CAP PSMs, **including 0x1001 (4097)**. Either widen `setL2capPsm()` and validate against the final socket type, or add a distinct setter for BR/EDR PSM to keep the LE-CoC contract intact.
3. `BluetoothDevice.createUsingSocketSettings()` should route `TYPE_L2CAP` into the existing internal BR/EDR L2CAP path (the same path that today's hidden `createInsecureL2capSocket` uses).

This adds one socket type, one PSM-range path, and one routing branch — and keeps everything inside the new API surface the team is already documenting and CTS-ing.

### Alternative: promote the existing hidden methods

If extending `BluetoothSocketSettings` is undesirable, promote `BluetoothDevice.createInsecureL2capSocket(int)` (and its secure twin `createL2capSocket(int)`) from `@hide` to public SDK. The implementation already exists, is exercised in production via reflection, and works now that b/371713238 is fixed. Acknowledging this still requires API council review, javadoc, CTS, and naming work.

### Nice-to-haves (only if cheap)

- Public listening side for BR/EDR L2CAP — `BluetoothAdapter.listenUsingInsecureL2capOn(int)` is also `@hide`. Not blocking the AirPods use case (always client) but a natural symmetry.
- A connection timeout knob on `BluetoothSocketSettings` — BR/EDR L2CAP has no timeout configuration today.

## Pre-empted concerns

- **Permission model:** a public BR/EDR L2CAP socket should require `BLUETOOTH_CONNECT` (the permission gate already in place for existing socket APIs). Limiting to bonded peers would be acceptable to us if the team prefers it.
- **Insecure variant:** mirrors the existing `createInsecureRfcommSocketToServiceRecord` and `createInsecureL2capChannel` semantics. Devices in this class typically expose the protocol on the insecure variant only. Apps are asking for transport access; Android does not need to parse, validate, or bless the vendor payload riding on it.
- **Surface area:** zero new transport implementation. The native path already exists and is in use today via the hidden API.

## Minimal repro

A single-Activity demo app reproducing the gap: <https://github.com/d4rken/google-issue-l2capsocket>. Given a paired BR/EDR device, it runs each of the techniques below and reports stage-by-stage outcomes (builder construction → method lookup → socket creation → connect → minimal AAP read for verification). Run against AirPods with PSM 0x1001:

| # | Technique | Expected outcome on current Android |
|---|---|---|
| (a) | `BluetoothDevice.createL2capChannel(0x1001)` then `connect()` (with timeout) | Fails — LE-only transport against a BR/EDR-only peer. |
| (b) | `BluetoothDevice.createInsecureL2capChannel(0x1001)` then `connect()` (with timeout) | Same failure. |
| (c) | `BluetoothSocketSettings.Builder().setSocketType(TYPE_L2CAP)` | `IllegalArgumentException` at builder time. |
| (d) | `Builder().setSocketType(TYPE_LE).setL2capPsm(0x1001)` | `IllegalArgumentException` — PSM out of `[128, 255]`. |
| (e) | Reflection on hidden `createInsecureL2capSocket(0x1001)`, **without** hidden-API bypass | `NoSuchMethodException` (non-SDK interface restriction). |
| (f) | Reflection on hidden `createInsecureL2capSocket(0x1001)`, **with** `setHiddenApiExemptions` bypass via ART method-pointer swap | Succeeds, connects, reads bytes back via the AAP handshake. **Diagnostic only** — illustrates that the platform supports this; not a recommended production path. |

Sources verified: AOSP `packages/modules/Bluetooth/framework/java/android/bluetooth/{BluetoothDevice,BluetoothSocket,BluetoothSocketSettings}.java`, branch `main`.
