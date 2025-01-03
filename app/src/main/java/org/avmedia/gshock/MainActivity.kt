package org.avmedia.gshock

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshock.ui.theme.GShockAPITheme
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.GShockAPI
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.IO
import java.time.ZoneId
import java.util.TimeZone
import kotlin.system.measureTimeMillis

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {

    private val api = GShockAPI(this)
    private lateinit var permissionManager: PermissionManager
    private val customEventName =
        "************** My Oun Event Generated from the App.!!!! ************"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        listenToProgressEvents()

        setContent {
            GShockAPITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(Modifier.padding(padding)) {
                        MainScreen()
                        Run()
                    }
                }
            }
        }
    }

    private fun listenToProgressEvents() {

        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {
                println("Got \"ConnectionSetupComplete\" event")
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
        )

        ProgressEvents.runEventActions(this.javaClass.simpleName, eventActions)
    }

    // ViewModel to hold the updatable text
    class MainScreenViewModel : ViewModel() {
        var dynamicText by mutableStateOf("...") // Mutable state to hold dynamic text
    }

    @Composable
    fun MainScreen(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
        // Observe the state from the ViewModel
        val dynamicText by viewModel::dynamicText

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), // Margin around the entire column
            verticalArrangement = Arrangement.SpaceEvenly, // Equal vertical space between items
            horizontalAlignment = Alignment.CenterHorizontally // Center align items horizontally
        ) {
            Text(
                text = "Long-press the BOTTOM-LEFT button on your watch to connect and run tests.",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = dynamicText, // Bind the text to the ViewModel's state
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    // Example: Updating the text from another part of the app
    private fun updateDynamicText(viewModel: MainScreenViewModel, newText: String) {
        viewModel.dynamicText = newText
    }

    @Composable
    private fun Run(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {

        LaunchedEffect(Unit) {
            while (true) {
                api.waitForConnection()

                updateDynamicText(viewModel,"Connected...")
                updateDynamicText(viewModel,"Running tests...Take a look at your debug logs.")

                runCommands()

                api.disconnect()
                updateDynamicText(viewModel,"Disconnected")
                updateDynamicText(viewModel,"Tests Ended..")
            }
        }
    }

    private suspend fun runCommands() {
        // private suspend fun runCommands() {
        println("Button pressed: ${api.getPressedButton()}")
        println("Name returned: ${api.getWatchName()}")

        println("Battery Level: ${api.getBatteryLevel()}")
        println("Timer: ${api.getTimer()}")
        println("App Info: ${api.getAppInfo()}")

        println("Home Time: ${api.getHomeTime()}")
        println("Temperature: ${api.getWatchTemperature()}")

        getDSTState()
        getWorldCities()
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

        handleReminders()
        handleSettings()
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
}
