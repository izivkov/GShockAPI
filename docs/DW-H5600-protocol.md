# DW-H5600 protocol notes (branch `h5600-sound-mode`)

Everything here was decoded from Android Bluetooth (HCI snoop) captures of the official
**CASIO WATCHES** app talking to a DW-H5600-1 (module 3516), plus izivkov's own DW-H5600
captures in the Python `gshock_api` repository. "Confirmed" means verified on the watch.

## Transport

| Role | Characteristic | ATT handle (DW-H5600) | Write type |
|---|---|---|---|
| Read request (1 byte = packet code) | `26eb002c` | 0x16 | without response (`GetSetMode.GET`) |
| Settings data / notifications | `26eb002d` | 0x18 (CCCD 0x19) | with response (`GetSetMode.SET`) |
| LifeLog data request (DRSP) | `26eb0023` | 0x10 | with response (`GetSetMode.DATA_REQUEST`) |
| LifeLog bulk (CONVOY) | `26eb0024` | 0x13 | without response (`GetSetMode.CONVOY`, added here) |

A read is: write `<code>` to `26eb002c`, the watch notifies `<code> …` on `26eb002d`.
The official app changes settings by **read-modify-write** of the whole packet.
The watch answers `FF 81 <code>` when it rejects a command (e.g. when busy, or 0x2D which it
does not implement); `RawSettingIO.onRejected` fails the pending read at once.

## Packets

| Code | Size | Meaning | Status |
|---|---|---|---|
| `0x39` | 2 | Connection handshake: app requests `39`, watch `39 04`, app writes `39 04` back, watch `47 01`. Without it the watch drops the link after ~45 s. | Confirmed |
| `0x42` | 6 | Sound / notification mode. byte 1: `00` all off, `02` sound only, `05` custom, `08` vibe only, `0A` sound + vibe. bytes 2–5: per-function CUSTOM table (byte 5 bit `0x10` = low-battery alert sound on); rest unmapped. | Byte 1 confirmed |
| `0x51` | 6 | Watch face. byte 1: `01` face 1 (simple, weekday), `02` face 2 (customisable). bytes 2–5 (all equal): face-2 item `00` steps, `0A` heart rate, `0B` heart rate min/max, `0E` sleep evaluation, `12` sunrise/sunset, `13` moon. | Confirmed |
| `0x50` | 5 | Smart alarm (one slot): `50 <hour> <minute> <flags> <window>`; flags `0x01` on, `0x02` snooze; window = minutes before (15/30/45/60). | Confirmed |
| `0x24` | 20 | Location: `24 <slot> 01 <lat f64 BE> <lon f64 BE> <flag>`; slot 0 = home (sunrise/sunset), slot 1 = world city 2. Current official app uses flag `04` (an older capture had `01`). | Confirmed |
| `0x13` | 17 | Basic settings. byte 1 bit `0x01` = 24 h; byte 12 bit `0x20` = hourly time signal. bytes 13–16 (`17 00 08 14`) look like a sleep schedule (23:00 / 08:20). | Bits confirmed; 13–16 likely |
| `0x15` / `0x16` | 5 / 17 | Alarms 1 / 2–5: `<flags> <00> <hour> <minute>`; flags `0x40` on, `0x10` snooze, `0x20` one-time, `0x80` hourly chime (alarm 1). The watch keeps byte 1 = `00`. | Confirmed |
| `0x18` | 15 | Countdown timer: `18 <h> <m> <s> 00 03 …`; byte 5 = `03` meaning unknown (kept). | Length confirmed |
| `0x1D` / `0x1E` / `0x1F` | | DST / world-city rules / city names. | (library) |
| `0x3D` | 17–18 | Status report the watch sends after DST/city writes (`3D 02/11/12 …`) and on request `3D 30`. The official app only reads it. | Not a request |
| `0x4F` | 10 | `4F 02 17 00 08 14 00 be 37 35`: probably sleep/wake schedule (23:00 / 08:20). | Unconfirmed |
| `0x43` | 17 | `43 10 27 …`: probably the daily step goal (0x2710 = 10 000). | Unconfirmed |
| `0x45` | 11 | User profile (name from the Python library); content not decoded. | Unconfirmed |
| `0x21` | 3 | `21 00 04` / `21 01 04` written by the official app while browsing its screens. | Unknown |
| `0x25`, `0x2F`, `0x3A`, `0x3B`, `0x40`, `0x41`, `0x52`, `0x5A` | | Read / echoed by the official app on connect. | Unknown |

## Time sync and sunrise/sunset

The watch recalculates sunrise/sunset when the time or cities are written. Writing the
DST/city packets clears the stored location, so a time sync must include it. The official app
writes, in this order: `1D` → `1E` → **`24 00`** (+ `24 01`) → `1F` names → … → `09` time.
`TimeIO.initializeForSettingTime` now calls `H5600SettingsIO.writeLocationInTimeSync()` at that
point; the coordinates come from `H5600SettingsIO.homeLocationProvider` (supplied by the app).

The watch's own sunrise/sunset formula matches the standard sunrise equation with a horizon
angle of about **−1.0°** (standard −0.833°), fitted from two readings; the app uses this to aim
"fake" coordinates precisely (see the app's `TempSun.kt`).

## LifeLog (steps)

Ported from izivkov's `h5600_health.py` (`io/H5600LifeLog.kt`, `io/H5600LifeLogIO.kt`):
setup requests `23 10 11 3B 3A 26 28 20 2F 43 13 45` (replies to `11 3B 3A 2F 13 45` echoed),
early START `00 2e ff ff ff ff ff` on DRSP, poll `00 00 00` on CONVOY until ready, setup
`04 …` / `04 01 18 00 18 00 00 00 dc 05` / `06 …`, echo of the 9-byte `06` reply, START, then
each `05 …` CONVOY fragment is acked with `07 00 …` and index announcements `00 2e <idx> <len>` /
`07 2e …` / END `04 2e …` are echoed on DRSP. Decoding: strip `05` + 2-byte checksum, concatenate,
XOR `0xFF`; 20-byte header (time UTC, day steps int32 at 14), frame `50` = 5-minute step slots.
Verified: izivkov's capture -4 → 624 steps (= official app); the user's own sync → 351.

## Safer encoders (shared code)

`SettingsIO`, `AlarmsIO` and `TimerIO` now encode from the last packet read from the watch and
change only the bits they manage, instead of building packets from zeros (which wiped unknown
bytes such as the alarm snooze bit and the DW-H5600's 0x13 bytes 12–16).
