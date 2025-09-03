# Jezail Android Pentesting Toolkit

Jezail is a **powerful, all-in-one Android APK** that **runs entirely on your rooted device**, providing comprehensive pentesting capabilities without needing external tools or a PC. It transforms your Android phone or tablet into a fully autonomous security testing platform with a rich set of features accessible through an embedded web UI served locally from the device.

With Jezail, everything happens *from within Android* - no complicated setups, no dependency on external hosts,exi deep system access and dynamic analysis powered by a bundled REST API and an interactive Flutter-based web interface.

## Documentation Overview

To keep this repository clean and organized, detailed documentation is separated into dedicated markdown files:

- [FEATURES.md](./FEATURES.md) - Complete list of core and planned capabilities  
- [UI.md](./UI.md) - Details about the Flutter-based embedded user interface (`jezail_ui`)  
- [ROADMAP.md](./ROADMAP.md) - Upcoming features and development goals  

## Media & Tutorials

Get started quickly by exploring demos and user interface previews:

- YouTube Demo: TODO: [Jezail Android Pentesting Demo Video](TODO: insert link)  
- Screenshots showing features and UI are available in the [`docs/screenshots/`](./docs/screenshots) directory  

## Requirements

- **Rooted Android Device:** Jezail requires the device to be rooted for full access to system features and pentesting capabilities.
   - For rooting, [Magisk](https://github.com/topjohnwu/Magisk) is the recommended tool for modern Android devices.
   - If using an emulator, ensure it is rooted as well. For rooting: [rootAVD](https://gitlab.com/newbit/rootAVD).

- **Running on Emulator:**
   - When running Jezail on an emulator, you must forward the emulator’s port to your host machine using ADB:
     ```
     adb forward tcp:8080 tcp:8080
     ```  
   - The web interface will then be accessible at `http://localhost:8080` instead of the device’s IP address.

- **Network Access:**
   - When running on a physical device, access the interface via `http://<device-ip>:8080/` from any device on the same network.


## Quickstart

1. Download the latest Jezail APK from the Releases  
2. Install on your rooted Android device using:  
   ```shell
   adb install -g -r jezail.apk
   ```  
3. Launch Jezail — it automatically starts an internal HTTP server  
4. Access the embedded web sever
   ```shell
   http://<device-ip>:<port>/ # for flutter webui
   http://<device-ip>:<port>/api/json # for a complete api json
   http://<device-ip>:<port>/api/swagger # for api swagger webui
   ```

## Get Involved

Your feedback and contributions are invaluable. Get involved, report issues, or propose features.

## About Jezail

Jezail is a traditional long-barreled rifle from Afghanistan used mainly in the 19th century. It was handmade and often decorated, featuring a long, heavy barrel that made it very accurate and powerful over long distances. Its unique curved wooden stock allowed fighters to shoot comfortably from different positions, including on horseback.

The Jezail rifle was key in historic battles because it could hit targets much farther away than typical guns of that time. The Jezail Android Pentesting Toolkit takes its name from this powerful and precise weapon, reflecting its goal to provide strong, accurate, and versatile security tools that run fully on Android devices.

## Disclaimer

Jezail is currently under active development and may contain incomplete features or bugs. It is provided "as is" without any warranty of any kind, either expressed or implied. Users assume full responsibility for any risks or issues that arise from using the toolkit. Jezail is intended for use by experienced security professionals and researchers who understand the risks associated with running powerful tools on rooted Android devices.


## License

This project is open-source under the MIT License. See [LICENSE](./LICENSE) for details.
