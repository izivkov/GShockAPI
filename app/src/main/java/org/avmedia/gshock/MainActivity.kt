package org.avmedia.gshock

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshock.ui.theme.GShockAPITheme
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.IGShockAPI
import org.avmedia.gshockapi.NotificationType
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GShockPairingManager
import org.avmedia.gshockapi.io.AppNotificationIO
import org.avmedia.gshockapi.io.IO
import org.avmedia.services.GShockScanService
import java.time.ZoneId
import java.util.TimeZone
import kotlin.system.measureTimeMillis

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {

    private val api = GShockAPI(this)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val customEventName =
        "************** My Oun Event Generated from the App.!!!! ************"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

            LaunchedEffect(Unit) {
                listenToProgressEvents(viewModel)

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    startService(Intent(this@MainActivity, GShockScanService::class.java))
                } else {
                    GShockPairingManager.getAssociations(this@MainActivity).forEach { address ->
                        api.startObservingDevicePresence(this@MainActivity, address)
                    }
                }
            }

            CheckPermissions {
                GShockAPITheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                        Box(Modifier.padding(padding)) {
                            MainScreen(viewModel)
                            Run(viewModel)
                        }
                    }
                }
            }
        }
    }


    private fun listenToProgressEvents(viewModel: MainScreenViewModel) {

        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
                println("Got \"ConnectionSetupComplete\" event")
            },
            EventAction("DeviceAppeared") {
                val address = ProgressEvents.getPayload("DeviceAppeared") as String
                println("Found a paired device: $address. Connecting...")
                scope.launch {
                    api.waitForConnection(address)
                    viewModel.updateAssociations(this@MainActivity)
                    api.waitForConnection(address)
                }
            },
            EventAction("Disconnect") {
                println("Got \"Disconnect\" event")
            },
            EventAction("ConnectionFailed") {
                println("Got \"ConnectionFailed\" event")
            },
            EventAction(customEventName) {
                println("Got \"$customEventName\" event")
            },
            EventAction("WatchInitializationCompleted") {
                println("Got \"WatchInitializationCompleted\" event")
            },
        )

        ProgressEvents.runEventActions(this.javaClass.simpleName, eventActions)
    }

    // ViewModel to hold the updatable text
    class MainScreenViewModel : ViewModel() {
        var dynamicText by mutableStateOf("...") // Mutable state to hold dynamic text
        val associations = mutableStateListOf<IGShockAPI.Association>()

        fun updateAssociations(context: android.content.Context) {
            associations.clear()
            associations.addAll(GShockPairingManager.getAssociationsWithNames(context))
        }
    }

    @Composable
    fun MainScreen(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
        val context = LocalContext.current
        val associations = viewModel.associations

        val pairingLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
            onResult = { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    viewModel.updateAssociations(context)
                    // Start observing presence for all associated devices
                    GShockPairingManager.getAssociations(context).forEach { address ->
                        api.startObservingDevicePresence(context, address)
                    }
                }
            }
        )

        LaunchedEffect(Unit) {
            viewModel.updateAssociations(context)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Companion Device Pairing",
                style = MaterialTheme.typography.headlineMedium
            )

            Button(onClick = {
                GShockPairingManager.associate(context, { intentSender ->
                    pairingLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    )
                }, { error ->
                    println("Pairing error: $error")
                })
            }) {
                Text("Pair New Watch")
            }

            HorizontalDivider()

            Text(
                text = "Paired Devices:",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(associations) { association ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = association.name ?: "Unknown")
                            Text(
                                text = association.address,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = {
                            api.stopObservingDevicePresence(context, association.address)
                            GShockPairingManager.disassociate(context, association.address)
                            viewModel.updateAssociations(context)
                        }) {
                            Text("Unpair")
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Status: ${viewModel.dynamicText}",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Long-press the BOTTOM-LEFT button on your watch to connect.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    // Example: Updating the text from another part of the app
    private fun updateDynamicText(viewModel: MainScreenViewModel, newText: String) {
        viewModel.dynamicText = newText
    }

    @Composable
    private fun Run(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {

        val eventActions = arrayOf(
            EventAction("WatchInitializationCompleted") {
                scope.launch {
                    updateDynamicText(viewModel, "Connected...")
                    updateDynamicText(viewModel, "Running tests...Take a look at your debug logs.")

                    runCommands()

                    if (api.supportsAppNotifications()) {
                        runAppNotificationTest()
                    }

                    api.disconnect()
                    updateDynamicText(viewModel, "Disconnected")
                    updateDynamicText(viewModel, "Tests Ended..")
                }
            }
        )

        LaunchedEffect(Unit) {
            ProgressEvents.runEventActions("Run", eventActions)
        }
    }

    private suspend fun runCommands() {
        println("Button pressed: ${api.getPressedButton()}")
        println("Name returned: ${api.getWatchName()}")

        println("Battery Level: ${api.getBatteryLevel()}")
        println("Timer: ${api.getTimer()}")
        println("App Info: ${api.getAppInfo()}")

        if (WatchInfo.hasHomeTime) {
            println("Home Time: ${api.getHomeTime()}")
        }
        if (WatchInfo.hasTemperature) {
            println("Temperature: ${api.getWatchTemperature()}")
        }

        getDSTState()

        if (WatchInfo.hasWorldCities) {
            getWorldCities()
        }
        getDSTForWorldCities()

        generateCustomEvent()

        val currentTZ = TimeZone.getDefault().id
        api.setTime("Europe/Sofia")
        api.setTime("Asia/Kolkata")
        api.setTime(currentTZ)

        val alarms = api.getAlarms()
        println("Alarm model: $alarms")

        alarms[0] = Alarm(6, 45, enabled = true, hasHourlyChime = false)
        alarms[4] = Alarm(9, 25, enabled = false)
        api.setAlarms(alarms)

        if (WatchInfo.hasReminders) {
            handleReminders()
        }
        handleSettings()
    }

    private suspend fun runAppNotificationTest() {
        val calendarNotification = AppNotification(
            type = NotificationType.CALENDAR,
            timestamp = "20231001T121000",
            app = "Calendar",
            title = "This is a very long Meeting with Team",
            text = " 9:20 - 10:15 AM"
        )

        val calendarNotificationAllDay = AppNotification(
            type = NotificationType.CALENDAR,
            timestamp = "20250516T233000",
            app = "Calendar",
            title = "Full day event 3",
            text = "Tomorrow"
        )

        val emailNotification2 = AppNotification(
            type = NotificationType.EMAIL_SMS,
            timestamp = "20250516T211520",
            app = "Gmail",
            title = "me",
            text = """\u5f7c\u5973\u306f\u30d4\u30a2\n\u5f7c\u5973\u306f\u30d4\u30a2\u30ce\u3092\u5f3e\u3044\u305f\u308a\u3001\u7d75\u3092\u63cf\u304f\u306e\u304c\u597d\u304d\u3067\u3059\u3002\u30ea\u30a2\u5145\u3067\u3059\n
            \u5f7c\u5973\u306f\u30d4\u30a2\n\u5f7c\u5973\u306f\u30d4\u30a2\u30ce\u3092\u5f3e\u3044\u305f\u308a\u3001\u7d75\u3092\u63cf\u304f\u306e\u304c\u597d\u304d\u3067\u3059\u3002\u30ea\u30a2\u5145\u3067\u3059\n
            \u5f7c\u5973\u306f\u30d4\u30a2\n\u5f7c\u5973\u306f\u30d4\u30a2\u30ce\u3092\u5f3e\u3044\u305f\u308a\u3001\u7d75\u3092\u63cf\u304f\u306e\u304c\u597d\u304d\u3067\u3059\u3002\u30ea\u30a2\u5145\u3067\u3059\n""",
            shortText = """\u5f7c\u5973\u306f\u30d4\u30a2\n\u5f7c\u5973\u306f\u30d4\u30a2\u30ce\u3092\u5f3e\u3044\u305f\u308a\u3001\u7d75\u3092\u63cf\u304f\u306e\u304c\u597d\u304d\u3067\u3059\u3002\u30ea\u30a2\u5145\u3067\u3059\n
            \u5f7c\u5973\u306f\u30d4\u30a2\n\u5f7c\u5973\u306f\u30d4\u30a2\u30ce\u3092\u5f3e\u3044\u305f\u308a\u3001\u7d75\u3092\u63cf\u304f\u306e\u304c\u597d\u304d\u3067\u3059\u3002\u30ea\u30a2\u5145\u3067\u3059\n
            \u5f7c\u5973\u306f\u30d4\u30a2\n\u5f7c\u5973\u306f\u30d4\u30a2\u30ce\u3092\u5f3e\u3044\u305f\u308a\u3001\u7d75\u3092\u63cf\u304f\u306e\u304c\u597d\u304d\u3067\u3059\u3002\u30ea\u30a2\u5145\u3067\u3059\n"""
        )

        val emailNotificationArabic = AppNotification(
            type = NotificationType.EMAIL_SMS,
            timestamp = "20250516T211520",
            app = "Gmail",
            title = "me",
            text = "الساعة\n"
        )

        val emailNotification = AppNotification(
            type = NotificationType.EMAIL,
            timestamp = "20231001T120000",
            app = "EmailApp",
            title = "Ivo",
            shortText = "And this is a short message up to 40 chars",
            text = "This is the message up to 193 characters, combined up to 206 characters"
        )

        api.sendAppNotification(emailNotification)
    }

    @Suppress("unused")
    private fun runTimezonesTest(items: MutableList<String>) { // NOSONAR
        val all = ZoneId.getAvailableZoneIds().size
        var current = 0

        suspend fun runAllTimezones() {
            for (tz in ZoneId.getAvailableZoneIds()) {
                api.setTime(tz)
                ++current
                println("tz: $tz, $current of $all")
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            api.waitForConnection()
            runAllTimezones()
            api.disconnect()
            println("--------------- End of runTimezonesTest ------------------")
        }
    }

    private fun generateCustomEvent() {
        ProgressEvents.onNext(customEventName)
    }

    private suspend fun getDSTForWorldCities() {
        println("World DST City 0: ${api.getDSTForWorldCities(0)}")
        println("World DST City 1: ${api.getDSTForWorldCities(1)}")

        if (WatchInfo.model == WatchInfo.WatchModel.GW) {
            println("World DST City 2: ${api.getDSTForWorldCities(2)}")
            println("World DST City 3: ${api.getDSTForWorldCities(3)}")
            println("World DST City 4: ${api.getDSTForWorldCities(4)}")
            println("World DST City 5: ${api.getDSTForWorldCities(5)}")
        }
    }

    private suspend fun getWorldCities() {
        println("World City 0: ${api.getWorldCities(0)}")
        println("World City 1: ${api.getWorldCities(1)}")

        if (WatchInfo.model == WatchInfo.WatchModel.GW) {
            println("World City 2: ${api.getWorldCities(2)}")
            println("World City 3: ${api.getWorldCities(3)}")
            println("World City 4: ${api.getWorldCities(4)}")
            println("World City 5: ${api.getWorldCities(5)}")
        }
    }

    private suspend fun getDSTState() {
        println("DST STATE ZERO: ${api.getDSTWatchState(IO.DstState.ZERO)}")

        if (WatchInfo.model == WatchInfo.WatchModel.GW) {
            println("DST STATE TWO: ${api.getDSTWatchState(IO.DstState.TWO)}")
            println("DST STATE FOUR: ${api.getDSTWatchState(IO.DstState.FOUR)}")
        }
    }

    private suspend fun handleReminders() {
        val originalEvents = api.getEventsFromWatch()
        println("""Clear all events ${api.clearEvents()}""")

        var watchEvents = api.getEventsFromWatch()
        println("After clearing: $watchEvents")

        // must call get before set
        api.getEventsFromWatch()

        api.setEvents(originalEvents)
        watchEvents = api.getEventsFromWatch()
        println("After setting original events: $watchEvents")

        val timeTaken = measureTimeMillis {
            repeat(1000) {
                api.getEventsFromWatch()
            }
        }
        println("Time taken to execute 1000 getEventsFromWatch: $timeTaken ms")

        println("At the end events are: ${api.getEventsFromWatch()}")
    }

    private suspend fun handleSettings() {
        val settings: Settings = api.getSettings()
        println("Settings: ${settings.dateFormat}, ${settings.timeAdjustment}, ${settings.adjustmentTimeMinutes}, ${settings.language}")
        settings.dateFormat = "MM:DD"
        api.setSettings(settings)
    }

    @Suppress("unused")
    private suspend fun handleTimer() { // NOSONAR
        val timerValue = api.getTimer()
        api.setTimer(timerValue)
    }

    fun testAppNotification() {
        val bufferSMS =
            "fdfffffffffef9cdcfcdcacfcaceccabcec7cfcdcdcef7ffb29a8c8c9e989a8cf1ffd7cbcec9d6dfc7ccccd2cdcfc8c7ffffe6ffab97968cdf968cdf9edf8c96928f939adf929a8c8c9e989adf"
        val bufferSMS2 =
            "fcfffffffffef9cdcfcdcacfcaceccabcec7cfcdcac6f7ffb29a8c8c9e989a8cf1ffd7cbcec9d6dfc7ccccd2cdcfc8c7fffff4ffbe91908b979a8ddf90919a"
        val bufferGmail =
            "fffffffffffef9cdcfcdcacfcaceceabcfc8cbcccecffaffb8929e9693fdff929affff05ffab97968cdf968cdf9edf899a8d86df93909198df8c8a9d959a9c8bd1dfb29e86899adf9a899a91df8b9090df93909198d1dfbd8a8bdf979a8d9adf968bdf968cd1d1d1f5a8979e8bdf968cdf968bc0f5b6df8b97969194df889adf9c9e91df9b90df9d9a8b8b9a8ddf8b979e91df8b979adf909999969c969e93dfbc9e8c9690dfb8d2ac97909c94dfbe8f8fdedfab97968cdf9e8f8fdf8f8d9089969b9a8cdf8b979adf999093939088969198df9a878b8d9edf999a9e8b8a8d9a8cc5f5ac9a8b8cdf889e8b9c97d88cdf8d9a9296919b"
        val bufferGmailJapanese =
            "fefffffffffef9cdcfcdcacfcacec9abcdcececacdcffaffb8929e9693fdff929affff9aff1a42431a5a4c1c7e501c7c6b1c7d5df51a42431a5a4c1c7e501c7c6b1c7d5d1c7c711c7d6d1a43411c7e7b1c7e601c7d751c7f7e184a4a1c7d6d1970701c7e701c7e511c7e731a5a421c7e721c7e581c7e661c7f7d1c7c551c7d5d1a7a7a1c7e581c7e66f5"
        val bufferCalendar =
            "f7fffffffffefacdcfcdcacfcaceceabcdcdcecfcfcff7ffbc9e939a919b9e8debff1d7f711d7f55b0919ad28b96929a1d7f531d7f71ffffe1ff1d7f711d7f55cecfc5cbcfdf1d7f6cdfcecec5cbcfdfafb21d7f531d7f71"
        val bufferCalendar2 =
            "f9fffffffffefacdcfcdcacfcaceceabcdcdcfc6cfcbf7ffbc9e939a919b9e8ddaff1d7f711d7f55b290919b9e86dfba899a8d86df889a9a94df99908d9a899a8d1d7f531d7f71ffffe1ff1d7f711d7f55cecfc5cccfdf1d7f6cdfcecec5cccfdfafb21d7f531d7f71"
        val bufferAllDayEvent =
            "fdfffffffffefacdcfcdcacfcacec9abcdcccccfcfcff7ffbc9e939a919b9e8de3ff1d7f711d7f55b98a9393df9b9e86df9a899a918bdfcc1d7f531d7f71ffffebff1d7f711d7f55ab9092908d8d90881d7f531d7f71"

        // Decode buffer and create notification
        val decodedBuffer = AppNotificationIO.xorDecodeBuffer(
            bufferGmailJapanese.hexToByteArray().joinToString("") { "%02x".format(it) })
        val notification = AppNotificationIO.decodeNotificationPacket(decodedBuffer)
        println("Decoded notification: $notification")

        // Rebuild buffer from notification
        val rebuiltBuffer = AppNotificationIO.encodeNotificationPacket(notification)

        // Compare buffers
        compareBuffers(rebuiltBuffer, decodedBuffer)
    }

    private fun compareBuffers(buf1: ByteArray, buf2: ByteArray) {
        val minLen = minOf(buf1.size, buf2.size)
        for (i in 0 until minLen) {
            if (buf1[i] != buf2[i] && i > 0) { // ignore the first byte of the header, could be "ff" or "fe"
                println("Difference at byte $i:")
                println("  buf1: ${buf1[i].toUByte().toString(16)}")
                println("  buf2: ${buf2[i].toUByte().toString(16)}")
                println(
                    "  Context buf1: ... ${
                        buf1.slice(
                            maxOf(0, i - 5) until minOf(
                                buf1.size,
                                i + 5
                            )
                        ).joinToString("") { "%02x".format(it) }
                    } ..."
                )
                println(
                    "  Context buf2: ... ${
                        buf2.slice(
                            maxOf(0, i - 5) until minOf(
                                buf2.size,
                                i + 5
                            )
                        ).joinToString("") { "%02x".format(it) }
                    } ..."
                )
                return
            }
        }
        if (buf1.size != buf2.size) {
            println("Buffers have different lengths: ${buf1.size} vs ${buf2.size}")
        } else {
            println("No differences found.")
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
