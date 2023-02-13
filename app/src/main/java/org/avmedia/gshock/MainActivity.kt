package org.avmedia.gshock

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.*
import org.avmedia.gshockapi.casio.BluetoothWatch

class MainActivity : AppCompatActivity() {

    private val api = GShockAPI(this)
    private lateinit var permissionManager: PermissionManager

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionManager = PermissionManager(this)
        permissionManager.setupPermissions()

        run(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun run(context: Context) {

        GlobalScope.launch {
            api.waitForConnection()

            println("Button pressed: ${api.getPressedButton()}")

            println("Name returned: ${api.getWatchName()}")

            println("Battery Level: ${api.getBatteryLevel()}")
            println("Timer: ${api.getTimer()}")
            println("App Info: ${api.getAppInfo()}")

            println("Home Time: ${api.getHomeTime()}")

            getDTSState()
            getWorldCities()
            getRTSForWorldCities()

            api.setTime()

            val alarms = api.getAlarms()
            println("Alarm model: $alarms")

            alarms[0] = Alarm(6, 46, enabled = true, hasHourlyChime = false)
            alarms[4] = Alarm(9, 25, enabled = false)
            api.setAlarms(alarms)

            handleReminders()

            handleSettings()

            api.disconnect(this@MainActivity)
            println("--------------- END ------------------")
        }
    }

    private suspend fun getRTSForWorldCities() {
        println("World DTS City 0: ${api.getDSTForWorldCities(0)}")
        println("World DTS City 1: ${api.getDSTForWorldCities(1)}")
        println("World DTS City 2: ${api.getDSTForWorldCities(2)}")
        println("World DTS City 3: ${api.getDSTForWorldCities(3)}")
        println("World DTS City 4: ${api.getDSTForWorldCities(4)}")
        println("World DTS City 5: ${api.getDSTForWorldCities(5)}")
    }

    private suspend fun getWorldCities() {
        println("World City 0: ${api.getWorldCities(0)}")
        println("World City 1: ${api.getWorldCities(1)}")
        println("World City 2: ${api.getWorldCities(2)}")
        println("World City 3: ${api.getWorldCities(3)}")
        println("World City 4: ${api.getWorldCities(4)}")
        println("World City 5: ${api.getWorldCities(5)}")
    }

    private suspend fun getDTSState() {
        println("TDS STATE ZERO: ${api.getDSTWatchState(BluetoothWatch.DTS_STATE.ZERO)}")
        println("TDS STATE TWO: ${api.getDSTWatchState(BluetoothWatch.DTS_STATE.TWO)}")
        println("TDS STATE FOUR: ${api.getDSTWatchState(BluetoothWatch.DTS_STATE.FOUR)}")
    }

    @RequiresApi(Build.VERSION_CODES.O)
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