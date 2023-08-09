package org.avmedia.gshock

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.avmedia.gshockapi.*
import org.avmedia.gshockapi.casio.CasioTimeZoneHelper
import org.avmedia.gshockapi.io.CasioIO
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.system.measureTimeMillis

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    private val api = GShockAPI(this)
    private lateinit var permissionManager: PermissionManager
    private val customEventName =
        "************** My Oun Event Generated from the App.!!!! ************"

    init {}

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionManager = PermissionManager(this)
        permissionManager.setupPermissions()

        listenToProgressEvents()

        run(this)
        // runDownBattery(this)
        // testTimeZones()
        // runTimezonesTest()
    }

    private fun listenToProgressEvents() {
        ProgressEvents.subscriber.start(this.javaClass.simpleName,

            {
                when (it) {
                    ProgressEvents["ConnectionSetupComplete"] -> {
                        println("Got \"ConnectionSetupComplete\" event")
                    }

                    ProgressEvents["Disconnect"] -> {
                        println("Got \"Disconnect\" event")
                    }

                    ProgressEvents["ConnectionFailed"] -> {
                        println("Got \"ConnectionFailed\" event")
                    }

                    ProgressEvents["WatchInitializationCompleted"] -> {
                        println("Got \"WatchInitializationCompleted\" event")
                    }

                    ProgressEvents[customEventName] -> {
                        println("Got \"$customEventName\" event")
                    }
                    else -> {
                        println("Got \"${it.javaClass}\" event")
                    }
                }
            }, { throwable ->
                println("Got error on subscribe: $throwable")
                throwable.printStackTrace()
            })
    }

    private fun run(context: Context) {

        CoroutineScope(Dispatchers.Default).launch {
            api.waitForConnection()

            runCommands()

            api.disconnect(this@MainActivity)
            println("--------------- END ------------------")
        }
    }

    private fun runDownBattery(context: Context, toPercent: Int = -1) {

        CoroutineScope(Dispatchers.Default).launch {
            api.waitForConnection()

            while (true) {
                if (api.getBatteryLevel() <= toPercent) {
                    break
                }
                runCommands()
            }

            api.disconnect(this@MainActivity)
            println("--------------- END ------------------")
        }
    }

    private suspend fun runCommands() {
        println("Button pressed: ${api.getPressedButton()}")

        println("Name returned: ${api.getWatchName()}")

        println("Battery Level: ${api.getBatteryLevel()}")
        println("Timer: ${api.getTimer()}")
        println("App Info: ${api.getAppInfo()}")

        println("Home Time: ${api.getHomeTime()}")

        getDSTState()
        getWorldCities()
        getDSTForWorldCities()

        generateCustomEvent()

        val currentTZ = TimeZone.getDefault().id
        api.setTime("Europe/Sofia")
        api.setTime("Asia/Kolkata")
        api.setTime("Pacific/Kiritimati")
        api.setTime("UTC")
        api.setTime(currentTZ)

        val alarms = api.getAlarms()
        println("Alarm model: $alarms")

        alarms[0] = Alarm(6, 45, enabled = true, hasHourlyChime = false)
        alarms[4] = Alarm(9, 25, enabled = false)
        api.setAlarms(alarms)

        handleReminders()
        handleSettings()
    }

    private fun runTimezonesTest () {
        val all = ZoneId.getAvailableZoneIds().size
        var current = 0

        suspend fun runAllTimezones() {
            for (tz in ZoneId.getAvailableZoneIds()) {
                api.setTime(tz)
                ++current
                println ("tz: $tz, $current of $all")
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            api.waitForConnection()
            runAllTimezones()
            api.disconnect(this@MainActivity)
            println("--------------- End of runTimezonesTest ------------------")
        }
    }

    private fun generateCustomEvent() {
        ProgressEvents.onNext(customEventName)
    }

    private suspend fun getDSTForWorldCities() {
        println("World DST City 0: ${api.getDSTForWorldCities(0)}")
        println("World DST City 1: ${api.getDSTForWorldCities(1)}")

        if (WatchInfo.model == WatchInfo.WATCH_MODEL.B5600) {
            println("World DST City 2: ${api.getDSTForWorldCities(2)}")
            println("World DST City 3: ${api.getDSTForWorldCities(3)}")
            println("World DST City 4: ${api.getDSTForWorldCities(4)}")
            println("World DST City 5: ${api.getDSTForWorldCities(5)}")
        }
    }

    private suspend fun getWorldCities() {
        println("World City 0: ${api.getWorldCities(0)}")
        println("World City 1: ${api.getWorldCities(1)}")

        if (WatchInfo.model == WatchInfo.WATCH_MODEL.B5600) {
            println("World City 2: ${api.getWorldCities(2)}")
            println("World City 3: ${api.getWorldCities(3)}")
            println("World City 4: ${api.getWorldCities(4)}")
            println("World City 5: ${api.getWorldCities(5)}")
        }
    }

    private suspend fun getDSTState() {
        println("DST STATE ZERO: ${api.getDSTWatchState(CasioIO.DTS_STATE.ZERO)}")

        if (WatchInfo.model == WatchInfo.WATCH_MODEL.B5600) {
            println("DST STATE TWO: ${api.getDSTWatchState(CasioIO.DTS_STATE.TWO)}")
            println("DST STATE FOUR: ${api.getDSTWatchState(CasioIO.DTS_STATE.FOUR)}")
        }
    }

    private fun testTimeZones() {
//        var totalCount = 0
//        var unknown = 0
//        val elapsed = measureTimeMillis {
//            for (tz in ZoneId.getAvailableZoneIds()) {
//                val foundTZ = CasioTimeZoneHelper.findTimeZone(tz)
//                ++totalCount
//                if (foundTZ.name == "UNKNOWN") {
//                    ++unknown
//                }
//            }
//        }
//
//        println("elapsed time: size: ${ZoneId.getAvailableZoneIds().size}, $elapsed ms., total: $totalCount, unknown: $unknown")

//        for (tz in CasioTimeZoneHelper.timeZoneMap.values) {
//            println("$tz")
//        }
    }

    private suspend fun handleReminders() {
        var events = ArrayList<Event>()

        events.addAll(api.getEventsFromWatch())
        println("Events from Watch: $events")
    }

    private suspend fun handleSettings() {
        val settings: Settings = api.getSettings()
        settings.dateFormat = "MM:DD"
        api.setSettings(settings)
    }

    private suspend fun handleTimer() {
        var timerValue = api.getTimer()
        api.setTimer(timerValue)
    }
}