# Jezail Feature Overview (Current Capabilities)

Jezail is an advanced Android pentesting toolkit that transforms rooted devices into comprehensive security testing platforms. It offers remote device control, system monitoring, application management, and dynamic analysis via REST API and web interface.

## Device Control and Information
- Retrieve essential device info (hardware, build, battery, CPU, RAM, storage, network).
- Capture screenshots.
- Manage clipboard: read, write, clear.
- Simulate hardware keys (Home, Back, Menu, Recent, Power, Volume, etc.).
- Get and set system properties.
- View and toggle SELinux status.
- 
## ADB & Frida Integration
- Start/stop/check status of ADB and Frida servers.
- Install and update Frida on the device.
- Customize Frida server binary and ports.
- Install ADB public keys for authentication.

## Application Management
- List installed packages (user, system, all).
- Install/uninstall apps, including force install and auto-grant permissions.
- Launch and stop applications with activity options.
- Check app debug status and running state.
- Manage app permissions (list, grant, revoke).
- Clear app data and cache.
- Retrieve app signatures and process info.

## System Monitoring and Logging
- Access and clear multiple log buffers (main, kernel, radio, system, crash, events).
- Filter and limit log output.
- Monitor device environment variables.

## File System Operations
- Browse directories and inspect files.
- Download/upload files and directories (including zip).
- Read/write text files.
- Rename, move, create, and delete files or directories.
- Change file permissions, ownership, and group (chmod, chown, chgrp).

## Networking and Proxy
- Get network status and info.
- Set global device proxy (planned feature).
- Manage VPN connections and packet capture (planned).

## Planned Capabilities And Features [ROADMAP.md](ROADMAP.md)

The features listed above reflect Jezail's current capabilities. Additional advanced features and enhancements are actively planned and tracked in the [ROADMAP.md](ROADMAP.md) file, including: