# Public SDK API for outgoing BR/EDR L2CAP client sockets

> **Filed as [b/510252114](https://issuetracker.google.com/issues/510252114).** This file is the body that was submitted; live discussion is on the tracker.

## Summary

Android has no public SDK API that lets a third-party app open an outgoing **BR/EDR L2CAP** client socket to a known PSM. The platform has this path internally via `BluetoothDevice.createInsecureL2capSocket(int)`, but the method is `@hide`. The new `BluetoothSocketSettings` API does not cover the case either: `Builder.setSocketType()` accepts only `TYPE_RFCOMM` and `TYPE_LE`, and `setL2capPsm()` is annotated `@IntRange(from=128, to=255)` — the LE-CoC dynamic range.

This blocks companion apps for devices like Apple AirPods / Beats, where the Apple Accessory Protocol (AAP) — the only path to per-bud + case battery, ANC mode, and ear detection on Android — runs over BR/EDR L2CAP on PSM `0x1001` (= 4097). No public Bluetooth profile API exposes this vendor protocol.

**Related:** the transport-layer side of this was already fixed in [b/371713238](https://issuetracker.google.com/issues/371713238) (the kernel/stack handshake on PSM `0x1001` to AirPods). This ticket is the API-surface counterpart — without a public entry point the transport fix is unreachable for third-party apps.

## Current public alternatives

| Public API | Why it does not work |
|---|---|
| `BluetoothDevice.createL2capChannel(int)` / `createInsecureL2capChannel(int)` | LE-only by construction. AOSP source uses `TYPE_L2CAP_LE`; javadoc states *"The supported Bluetooth transport is LE only."* |
| `BluetoothSocketSettings.Builder` | Rejects `TYPE_L2CAP`; `setL2capPsm(0x1001)` rejected as outside `[128, 255]`. |
| RFCOMM socket APIs | Wrong transport. The protocol is L2CAP, not RFCOMM. |

The only working path today is reflection on hidden `BluetoothDevice.createInsecureL2capSocket(int)` plus a `dalvik.system.VMRuntime.setHiddenApiExemptions` bypass implemented by swapping ART method pointers via `sun.misc.Unsafe`. This is not viable for Play-distributed apps.

## Ask

**Preferred:** extend `BluetoothSocketSettings` to support outgoing BR/EDR L2CAP client sockets.

1. Allow `BluetoothSocketSettings.Builder.setSocketType()` to accept `BluetoothSocket.TYPE_L2CAP` (= 3).
2. Allow valid BR/EDR L2CAP PSMs for that socket type, including `0x1001`. Either widen `setL2capPsm()` and validate against the final socket type at `build()` time, or add a BR/EDR-specific PSM setter while preserving the existing LE-CoC range contract.
3. Route `BluetoothDevice.createUsingSocketSettings()` for `TYPE_L2CAP` through the existing BR/EDR L2CAP client socket path.

**Alternative:** promote the existing hidden methods `BluetoothDevice.createInsecureL2capSocket(int)` and `BluetoothDevice.createL2capSocket(int)` to public SDK.

Expected permission gate: `BLUETOOTH_CONNECT` (matching the existing public socket APIs). Restricting to bonded peers is acceptable.

## Minimal repro

Demo app: <https://github.com/d4rken/google-issue-l2capsocket>

Single-Activity reproducer. Given a paired BR/EDR device, runs each path below and reports stage outcomes (builder → method lookup → socket creation → connect → AAP read-back). Run against AirPods with PSM `0x1001`:

| # | Technique | Expected outcome on current Android |
|---|---|---|
| (a) | `BluetoothDevice.createL2capChannel(0x1001)` then `connect()` | Fails — LE-only API against BR/EDR-only peer. |
| (b) | `BluetoothDevice.createInsecureL2capChannel(0x1001)` then `connect()` | Same failure. |
| (c) | `BluetoothSocketSettings.Builder().setSocketType(TYPE_L2CAP)` | `IllegalArgumentException`. |
| (d) | `Builder().setSocketType(TYPE_LE).setL2capPsm(0x1001)` | `IllegalArgumentException` — PSM outside `[128, 255]`. |
| (e) | Reflection on hidden `createInsecureL2capSocket(0x1001)`, without bypass | `NoSuchMethodException` (non-SDK-interface restriction). |
| (f) | Reflection on hidden `createInsecureL2capSocket(0x1001)`, with `setHiddenApiExemptions` bypass | Succeeds, connects, reads bytes via AAP handshake. Diagnostic only. |

Sources verified against AOSP `packages/modules/Bluetooth/framework/java/android/bluetooth/{BluetoothDevice,BluetoothSocket,BluetoothSocketSettings}.java`, branch `main`.
