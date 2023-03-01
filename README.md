# GShockAPI
This library provides an API to communicate and issue commands to the Casio G-Shock [5600](https://tinyurl.com/y4r5xf94) and [5000](https://tinyurl.com/yvnku2au) series of watches via the Bluetooth interface. It can perform the following tasks:

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
    implementation 'com.github.izivkov:GShockAPI:1.0.9'
}
```

## Who is using it?

The [Casio GShock Smart Sync](https://github.com/izivkov/CasioGShockSmartSync) app integrates G-Shock 5000/5600 watches with Google services such as `Google Calendar` events. `Google Alarm Clock`, etc.
