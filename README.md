# GShockAPI
This library provides an API to communicate to the Casio G-Shock watches via the Bluetooth interface. It can perform the following tasks:

- Set watch's time
- Set Home Time (Home City)
- Set Alarms
- Set Reminders
- Set watch's settings.
- Get watch's name
- Get watch's battery level
- Get Watch's temperature
- Get/Set watch's Timer

## Supported Watch Models

The library works with many Bluetooth-enabled G-Shock, Edifice, and Pro Trek models.

| Series | Compatible Models (Examples) | Note |
|:---|:---|:---|
| **Square** | GW-B5600, GMW-B5000, GW-B5000, DW-B5600, TRN-50 | Classic square design support |
| **CasiOak** | GA-B2100, GBM-2100, GMC-B2100, MRG-B2100 | Octagonal bezel models |
| **G-Steel** | GST-B100 to B1000, GST-W1000, ECB-900 | Metal series |
| **Edifice** | ECB-10 to ECB-2300, EQB-500 to EQB-2000 | Bluetooth Edifice series |
| **MT-G / MR-G** | MTG-B1000/B3000/B3100, MRG-B5000/B2100 | Premium metal and carbon models |
| **Others** | ABL-100WE, GBD-100/200, GBD-H1000/H2000, GPR-B1000 | Step trackers, GPS, and sensors |

> **Note**: While these watches connect, not all specific hardware features (like fitness tracking steps on GBD models) may be fully supported yet.


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
    implementation 'com.github.izivkov:GShockAPI:1.7.1'
}
```

[![](https://jitpack.io/v/izivkov/GShockAPI.svg)](https://jitpack.io/#izivkov/GShockAPI)

## Who is using it?

The [Casio GShock Smart Sync](https://github.com/izivkov/CasioGShockSmartSync) app integrates G-Shock B5000/B5600/B2100 watches with Google services such as `Google Calendar` events. `Google Alarm Clock`, etc.

The [WristVault](https://github.com/remigius-labs/WristVault) project turns your Casio G-Shock into encrypted micro-storage for passwords & keys up to 39 characters.

If you like us to list your project which uses this library, [contact us](mailto:izivkov@gmail.com) and we will include a link.

## Related Project
If you rather not use a mobile app, but still like to set the time on your G-Shock to the correct time, 
we have also developed a [Python program](https://github.com/izivkov/GShockTimeServer) which can run as a server on a regular PC or Raspberry PI with Bluetooth interface.
The project is still WIP, but you can give it a try.

