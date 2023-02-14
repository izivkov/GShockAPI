# Module casio-g-shock-smart-sync-app

# Package org.avmedia.gshockapi
Casio G-Shock 5000/5600 API

## Description
This library provides an API to communicate and issue commands to the Casio G-Shock 5000 and 5600
series of watches via the Bluetooth interface. It can perform the following tasks:


- Set watch's time
- Set Home Time (Home City)
- Set Alarms
- Set Reminders
- Set watch's settings.
- Get watch's name
- Get watch's battery level
- Set watch's Timer


*All the API functions are located in the **[GShockAPI]** class.*

For a quick start, you can take a look at the `/app` directory of this project for an example app.

## Dependency

Add the following to your **build.gradle** file:
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.izivkov:GShockAPI:1.0.2'
}

Here is a complete app using this library:

[Casio GShock Smart Sync](https://github.com/izivkov/CasioGShockSmartSync)
