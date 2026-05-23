## Title

Improve EQB-501 support and add legacy Casio alert notifications

## Problem

Some older Casio/Edifice watches, including the EQB-501 observed locally, expose the
all-features GATT characteristic (`26eb002d`) but do not expose the newer
notification characteristic (`26eb0030`). The existing `sendAppNotification` path
only writes the newer XOR-encoded notification packet to `26eb0030`, so these
watches report no app-notification support even though the Casio apps use a
legacy alert packet.

The current-time encoder also wrote raw milliseconds into the fractional-second
byte. Casio's current-time payload uses Fractions256, so millisecond values above
255 wrap when converted to a byte.

## Technical Summary

- Add `CASIO_NEW_ALERT` (`0x07`) to the all-features command enum.
- Add `CasioAlertNotificationIO` to encode the legacy alert payload:
  `0x07, category, count, text...`.
- Map existing `NotificationType` values to Casio alert categories:
  email, incoming call, SMS/MMS, schedule, and SNS/generic.
- Keep the modern `26eb0030` notification path unchanged when available.
- Fall back to the all-features alert path on watches that only expose
  `26eb002d`.
- Encode current-time fractional seconds as `nanos * 256 / 1_000_000_000`.
- Add unit coverage for the legacy alert byte format and UTF-8-safe text
  truncation, current-time byte layout, Sunday mapping, and EQB-501D model
  recognition.

## Evidence

Reverse engineering of the Casio apps showed a legacy New Alert command with class
`0x07`, category/count payload, and an 18-byte text field. Local EQB-501 BLE
probing confirmed that this watch exposes `26eb002d` but not `26eb0030`.

## Files Changed

- `api/src/main/java/org/avmedia/gshockapi/GShockAPI.kt`
- `api/src/main/java/org/avmedia/gshockapi/casio/CasioConstants.kt`
- `api/src/main/java/org/avmedia/gshockapi/io/CasioAlertNotificationIO.kt`
- `api/src/main/java/org/avmedia/gshockapi/io/TimeIO.kt`
- `api/src/test/java/org/avmedia/gshockapi/ExampleUnitTest.kt`

## Tests

- Added unit tests for legacy alert encoding.
- Attempted to run:
  `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :api:testDebugUnitTest --no-daemon`
- Local run is blocked because no Android SDK is configured:
  `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or sdk.dir in local.properties.`

## Known Limitations

- This PR only implements the legacy New Alert notification path.
- It does not implement Watch+ convoy reads for battery history/state; that needs
  notification handling on `26eb0024` and should be a separate, smaller PR.
- The legacy watch display supports only a short alert text field, so full modern
  notification details are reduced to a short display string.
