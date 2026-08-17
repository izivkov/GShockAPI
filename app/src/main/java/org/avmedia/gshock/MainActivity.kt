package org.avmedia.gshock

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshock.ui.theme.GShockAPITheme
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.DeviceInfo
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.IGShockAPI
import org.avmedia.gshockapi.NotificationType
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.AppNotificationIO
import org.avmedia.gshockapi.io.IO
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
                println("Found a device: $address. Connecting...")
                scope.launch {
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
        val discoveredDevices = mutableStateListOf<DeviceInfo>()
        var isScanning by mutableStateOf(false)
        val logMessages = mutableStateListOf<String>()

        fun startScan(api: IGShockAPI, context: android.content.Context) {
            isScanning = true
            discoveredDevices.clear()
            api.scan(context, { info ->
                info.name.contains("CASIO", ignoreCase = true)
            }, { info ->
                if (discoveredDevices.none { it.address == info.address }) {
                    discoveredDevices.add(info)
                }
            })
        }

        fun stopScan(api: IGShockAPI) {
            isScanning = false
            api.stopScan()
        }

        fun addLog(message: String) {
            logMessages.add(message)
        }

        fun clearLogs() {
            logMessages.clear()
        }
    }

    @Composable
    fun MainScreen(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
        val context = LocalContext.current
        val discoveredDevices = viewModel.discoveredDevices
        val isScanning = viewModel.isScanning

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "G-Shock Scanner",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Find and connect to your watch",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Scan Button with Animation
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isScanning) 1.05f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Button(
                onClick = {
                    if (isScanning) viewModel.stopScan(api)
                    else viewModel.startScan(api, context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isScanning) Icons.Default.Refresh else Icons.Default.Bluetooth,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isScanning) "STOP SCANNING" else "START SCANNING",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Main Content: Either Discovered Devices or Logs
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val showLogs = viewModel.logMessages.isNotEmpty()
                    Text(
                        text = if (showLogs) "Test Logs" else "Discovered Devices",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                    )

                    if (showLogs) {
                        val listState = rememberLazyListState()
                        LaunchedEffect(viewModel.logMessages.size) {
                            if (viewModel.logMessages.isNotEmpty()) {
                                listState.animateScrollToItem(viewModel.logMessages.size - 1)
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(viewModel.logMessages) { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(8.dp)
                                )
                            }
                        }
                    } else if (discoveredDevices.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isScanning) "Searching for watches..." else "No watches found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(discoveredDevices) { device ->
                                DeviceItem(device) {
                                    viewModel.stopScan(api)
                                    viewModel.clearLogs()
                                    scope.launch {
                                        api.waitForConnection(device.address)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Status Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Status: ${viewModel.dynamicText}",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Hold BOTTOM-LEFT for 3s to connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    @Composable
    private fun DeviceItem(device: DeviceInfo, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Example: Updating the text from another part of the app
    private fun updateDynamicText(viewModel: MainScreenViewModel, newText: String) {
        viewModel.dynamicText = newText
        viewModel.addLog(newText)
    }

    @Composable
    private fun Run(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {

        val eventActions = arrayOf(
            EventAction("WatchInitializationCompleted") {
                scope.launch {
                    viewModel.clearLogs()
                    updateDynamicText(viewModel, "Connected...")
                    updateDynamicText(viewModel, "Running tests...")

                    runCommands(viewModel)

                    if (api.supportsAppNotifications()) {
                        runAppNotificationTest(viewModel)
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

    private suspend fun runCommands(viewModel: MainScreenViewModel) {
        viewModel.addLog("Button pressed: ${api.getPressedButton()}")
        viewModel.addLog("Name returned: ${api.getWatchName()}")

        viewModel.addLog("Battery Level: ${api.getBatteryLevel()}")
        viewModel.addLog("Timer: ${api.getTimer()}")
        viewModel.addLog("App Info: ${api.getAppInfo()}")

        if (WatchInfo.hasHomeTime) {
            viewModel.addLog("Home Time: ${api.getHomeTime()}")
        }
        if (WatchInfo.hasTemperature) {
            viewModel.addLog("Temperature: ${api.getWatchTemperature()}")
        }

        getDSTState(viewModel)

        if (WatchInfo.hasWorldCities) {
            getWorldCities(viewModel)
        }
        getDSTForWorldCities(viewModel)

        generateCustomEvent()

        val currentTZ = TimeZone.getDefault().id
        api.setTime("Europe/Sofia")
        api.setTime("Asia/Kolkata")
        api.setTime(currentTZ)

        val alarms = api.getAlarms()
        viewModel.addLog("Alarm model: $alarms")

        alarms[0] = Alarm(6, 45, enabled = true, hasHourlyChime = false)
        alarms[4] = Alarm(9, 25, enabled = false)
        api.setAlarms(alarms)

        if (WatchInfo.hasReminders) {
            handleReminders(viewModel)
        }
        handleSettings(viewModel)
    }

    private suspend fun runAppNotificationTest(viewModel: MainScreenViewModel) {
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

        viewModel.addLog("Sending calendar notification...")
        api.sendAppNotification(calendarNotification)

        viewModel.addLog("Sending email notification...")
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

    private suspend fun getDSTForWorldCities(viewModel: MainScreenViewModel) {
        viewModel.addLog("World DST City 0: ${api.getDSTForWorldCities(0)}")
        viewModel.addLog("World DST City 1: ${api.getDSTForWorldCities(1)}")

        if (WatchInfo.model == WatchInfo.WatchModel.GW) {
            viewModel.addLog("World DST City 2: ${api.getDSTForWorldCities(2)}")
            viewModel.addLog("World DST City 3: ${api.getDSTForWorldCities(3)}")
            viewModel.addLog("World DST City 4: ${api.getDSTForWorldCities(4)}")
            viewModel.addLog("World DST City 5: ${api.getDSTForWorldCities(5)}")
        }
    }

    private suspend fun getWorldCities(viewModel: MainScreenViewModel) {
        viewModel.addLog("World City 0: ${api.getWorldCities(0)}")
        viewModel.addLog("World City 1: ${api.getWorldCities(1)}")

        if (WatchInfo.model == WatchInfo.WatchModel.GW) {
            viewModel.addLog("World City 2: ${api.getWorldCities(2)}")
            viewModel.addLog("World City 3: ${api.getWorldCities(3)}")
            viewModel.addLog("World City 4: ${api.getWorldCities(4)}")
            viewModel.addLog("World City 5: ${api.getWorldCities(5)}")
        }
    }

    private suspend fun getDSTState(viewModel: MainScreenViewModel) {
        viewModel.addLog("DST STATE ZERO: ${api.getDSTWatchState(IO.DstState.ZERO)}")

        if (WatchInfo.model == WatchInfo.WatchModel.GW) {
            viewModel.addLog("DST STATE TWO: ${api.getDSTWatchState(IO.DstState.TWO)}")
            viewModel.addLog("DST STATE FOUR: ${api.getDSTWatchState(IO.DstState.FOUR)}")
        }
    }

    private suspend fun handleReminders(viewModel: MainScreenViewModel) {
        val originalEvents = api.getEventsFromWatch()
        viewModel.addLog("Clear all events ${api.clearEvents()}")

        var watchEvents = api.getEventsFromWatch()
        viewModel.addLog("After clearing: $watchEvents")

        // must call get before set
        api.getEventsFromWatch()

        api.setEvents(originalEvents)
        watchEvents = api.getEventsFromWatch()
        viewModel.addLog("After setting original events: $watchEvents")

        val timeTaken = measureTimeMillis {
            repeat(100) { // reduced from 1000 to keep log manageable
                api.getEventsFromWatch()
            }
        }
        viewModel.addLog("Time taken to execute 100 getEventsFromWatch: $timeTaken ms")

        viewModel.addLog("At the end events are: ${api.getEventsFromWatch()}")
    }

    private suspend fun handleSettings(viewModel: MainScreenViewModel) {
        val settings: Settings = api.getSettings()
        viewModel.addLog("Settings: ${settings.dateFormat}, ${settings.timeAdjustment}, ${settings.adjustmentTimeMinutes}, ${settings.language}")
        settings.dateFormat = "MM:DD"
        api.setSettings(settings)
    }

    @Suppress("unused")
    private suspend fun handleTimer() { // NOSONAR
        val timerValue = api.getTimer()
        api.setTimer(timerValue)
    }

    fun testAppNotification(viewModel: MainScreenViewModel) {
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
        viewModel.addLog("Decoded notification: $notification")

        // Rebuild buffer from notification
        val rebuiltBuffer = AppNotificationIO.encodeNotificationPacket(notification)

        // Compare buffers
        compareBuffers(rebuiltBuffer, decodedBuffer, viewModel)
    }

    private fun compareBuffers(buf1: ByteArray, buf2: ByteArray, viewModel: MainScreenViewModel) {
        val minLen = minOf(buf1.size, buf2.size)
        for (i in 0 until minLen) {
            if (buf1[i] != buf2[i] && i > 0) { // ignore the first byte of the header, could be "ff" or "fe"
                viewModel.addLog("Difference at byte $i:")
                viewModel.addLog("  buf1: ${buf1[i].toUByte().toString(16)}")
                viewModel.addLog("  buf2: ${buf2[i].toUByte().toString(16)}")
                viewModel.addLog(
                    "  Context buf1: ... ${
                        buf1.slice(
                            maxOf(0, i - 5) until minOf(
                                buf1.size,
                                i + 5
                            )
                        ).joinToString("") { "%02x".format(it) }
                    } ..."
                )
                viewModel.addLog(
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
            viewModel.addLog("Buffers have different lengths: ${buf1.size} vs ${buf2.size}")
        } else {
            viewModel.addLog("No differences found.")
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
