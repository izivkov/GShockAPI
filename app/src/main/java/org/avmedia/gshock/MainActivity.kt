package org.avmedia.gshockapi

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val api = org.avmedia.gshockapi.GShockAPI(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        run(this)
    }

    private fun run(context: Context) {

        GlobalScope.launch {
            api.waitForConnection(context)
            // api().init ()

            println("Button pressed: ${api().getPressedButton()}")

            println("Name returned: ${api().getWatchName()}")

            println("Battery Level: ${api().getBatteryLevel()}")
            println("Timer: ${api().getTimer()}")
            println("App Info: ${api().getAppInfo()}")

            println("Home Time: ${api().getHomeTime()}")

            getDTSState()
            getWorldCities()
            getRTSForWorldCities()

            api().setTime()

            val alarms = api().getAlarms()
            var model = AlarmsModel
            model.alarms.clear()
            model.alarms.addAll(alarms)
            println("Alarm model: ${model.toJson()}")

            model.alarms[0] = Alarm(6, 46, enabled = true, hasHourlyChime = false)
            model.alarms[4] = Alarm(9, 25, enabled = false)
            api().setAlarms(model.alarms)

            handleReminders()

            handleSettings()

            println("--------------- END ------------------")
        }
    }
}