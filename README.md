# GShockAPI
This library provides an API to communicate to the Casio G-Shock [B5600](https://amzn.to/439cb29), [B5000](https://amzn.to/3zShYMt) 
and [B2100](https://amzn.to/3MUDCGY) series of watches via the Bluetooth interface. It can perform the following tasks:

- Set watch's time
- Set Home Time (Home City)
- Set Alarms
- Set Reminders
- Set watch's settings.
- Get watch's name
- Get watch's battery level
- Set watch's Timer

## Documentation

The API documentation can be found [here](https://izivkov.github.io/GShockAPI/api/org.avmedia.gshockapi/index.html)

## Dependency

Add the following to your **build.gradle** file:
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.izivkov:GShockAPI:1.2.5'
}
```

[![](https://jitpack.io/v/izivkov/GShockAPI.svg)](https://jitpack.io/#izivkov/GShockAPI)

## Who is using it?

The [Casio GShock Smart Sync](https://github.com/izivkov/CasioGShockSmartSync) app integrates G-Shock B5000/B5600/B2100 watches with Google services such as `Google Calendar` events. `Google Alarm Clock`, etc.

## Related Project
If you rather not use a mobile app, but still like to set the time on your G-Shock to the correct time, 
we have also developed a [Python program](https://github.com/izivkov/GShockTimeServer) which can run as a server on a regular PC or Raspberry PI with Bluetooth interface.
The project is still WIP, but you can give it a try.

## Credits
- The BLE-related code in this app is based on the `ble-starter-android` https://github.com/PunchThrough/ble-starter-android, but with many modifications.
- Some if the Casio specific code is loosely based on the `Gadgetbridge` https://github.com/Freeyourgadget/Gadgetbridge project
