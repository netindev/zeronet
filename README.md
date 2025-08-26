# Zeronet

A simple Android application with a clean, AMOLED black interface.

## Features

- **Operator Selection**: Dropdown menu to select from available operators
- **Payload Selection**: Dropdown menu to select from available payloads
- **Start Button**: Initiates the operation with selected parameters
- **Collapsible Log**: Expandable log area at the bottom to view operation messages
- **AMOLED Black Theme**: Dark interface optimized for AMOLED displays

## UI Components

- **Header**: App icon and title "Zeronet"
- **Operator Dropdown**: Select from Operator 1-4
- **Payload Dropdown**: Select from Payload A-D
- **Start Button**: Blue button with play icon
- **Log Section**: Collapsible area showing operation messages

## Technical Details

- Built with Jetpack Compose
- Material 3 Design System
- AMOLED black color scheme
- Kotlin programming language
- Android API level support

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew installDebug
```

The app provides a simple, direct interface for selecting operators and payloads, then starting operations with logging capabilities.
