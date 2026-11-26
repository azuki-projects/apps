# Calc Float

A lightweight native Android floating calculator built with Java. Designed for Android 6.0+ and compatible with CodeAssist ADE projects.

## Features
- Floating calculator overlay that can stay above other apps
- Draggable calculator panel and compact bubble mode
- Remembers the overlay position and compact state
- Live expression preview while typing
- Multi-digit and decimal calculations
- Parentheses, percentage, square root, powers, and +, −, ×, ÷
- Correct operator precedence and division-by-zero handling
- Foreground service and partial wakelock
- No third-party libraries or internet permission required

## Build
Open the `calc-float` folder in CodeAssist ADE and build the Android application. The project uses Android Gradle Plugin 8.7.3, compile SDK 35, min SDK 23, and target SDK 28.

## First launch
Allow **Display over other apps** when Android asks. After permission is granted, the launcher activity immediately starts the floating calculator and closes. Tap the minus button to collapse it into a bubble; tap the bubble to expand it.

## Package
`com.azuki.calculatorfloat`
