# Jezail

**Jezail** is an Android application that enables remote control of your rooted Android device, extraction of system and application information, and execution of dynamic penetration testing tasks. It supports environment management via a REST API and a browser-based web interface.

The application **requires root access** (via [`libsu`](https://github.com/topjohnwu/libsu)) for many operations.

## Quick Setup

1. **Download the APK**  
   Get the latest release from:  
   [jezail Releases](https://github.com/zahidaz/jezail/releases)

2. **Install via ADB**  
   Connect your device with ADB and install the APK:
   ```bash
   adb install /path/to/jezail.apk
   ```
   For granting all required permissions upon installation:
   ```bash
   adb install -g -r /path/to/jezail.apk
   ```
If you are running the app inside emulator then you would need to run this command:
```shell
adb forward tcp:8080 tcp:8080
```

3. **Permissions**
    - The app requires **notification permission** to run in the background.
    - Additional **system permissions** (e.g., access to device information) can be enabled from system settings or during installation using ADB.

4. **Run the App**  
   Launch the app. If notification permissions are granted:
    - The built-in HTTP server starts automatically.
    - The app can be sent to the background and will continue running.

   On the main page, you’ll see a URL in the format:
   ```
   http://<IP>:8080/
   ```
   (IP may vary if the device is behind a proxy or NAT.)

5. **Access Interfaces**
    - **Web Interface:** [http://<IP>:8080/](http://<IP>:8080/)
    - **JSON Endpoint List:** [http://<IP>:8080/api/json](http://<IP>:8080/api/json)
    - **Swagger UI Documentation:** [http://<IP>:8080/api/swagger](http://<IP>:8080/api/swagger)


## Use Cases

This API is designed for:
- **Mobile App Testing**: Integration with automated testing frameworks and CI/CD pipelines
- **Security Research**: Dynamic penetration testing and exploitation workflows
- **Development Tools**: IDE integrations and debugging utilities
- **Device Management**: Enterprise-level mobile device management solutions
- **Reverse Engineering**: Runtime monitoring of Android applications
- **Quality Assurance**: Automated device farm setup and test execution


# Feature Overview

## Core System Management

### ADB (Android Debug Bridge)
- Start/stop ADB server
- Check ADB server status
- Install public keys for ADB authentication

### Frida Integration
- Start/stop Frida server
- Check Frida installation and status
- Install or update Frida


## Device Information & Control

### Device Information
- Build properties and system info
- SELinux status (enforcing/permissive) toggle
- Battery, CPU, RAM, storage stats (summary & detailed)
- Network information

### Device Control
- Capture screenshots
- Clipboard management (read, write, clear)
- Simulate hardware keys: Home, Back, Menu, Recent Apps, Power, Volume
- Send custom keycodes


## System Monitoring

### Logging
- Access system, kernel, radio, crash, and event logs
- Filter and limit log output
- Clear logs

### System Properties
- Get and set system properties

### Process Management
- List running processes
- Fetch details by PID
- Kill processes (PID or name)


## Application Management

### Package Operations
- List installed apps (all, user, system)
- Get package details (basic/detailed)
- Install APKs with options: force install, auto-grant permissions
- Uninstall or launch apps (custom activities supported)
- Stop running apps
- Sandbox dumping (via file endpoints)

### Application Control
- Check if an app is running
- Fetch process information
- Clear app data and cache
- Check `debuggable` flag
- Extract app signatures

### Permission Management
- List granted/denied permissions per app
- Grant/revoke specific permissions


## File System Operations

### File Management
- Inspect files and directories (attributes, metadata)
- Download and upload files
- Read/write text files
- Rename, move, create, or delete items

### File Permissions
- Modify Unix permissions: chmod, chown, chgrp


## API Utilities

- Status and health checks


# Roadmap

Planned features include:
- Dynamic intent building to target exported components of other apps
- Load/customize Frida scripts directly from the app
- Remote input injection: type directly from browser/PC into device input fields
- Search and dump application memory
- Automated OWASP MAS dynamic testing from device
- Extended 3rd-party tools management
- Proxy and certificate management utilities
- Ability to spoof Frida name/version


**Disclaimer**: This project is still in its early stages of development and has not been extensively tested across a wide range of devices or Android versions. While most core features work as intended, some functions may behave inconsistently or encounter minor issues depending on the specific device environment. Users should expect experimental behavior.



## License

This project is licensed under the **MIT License**