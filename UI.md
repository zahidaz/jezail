## User Interface (`jezail_ui`)

The Jezail UI is a separate Flutter web project embedded within the APK during the build process. It is compiled into static web assets served by Jezail’s internal HTTP server, providing a responsive and interactive browser-based interface.

### Features
- Flutter-based web UI compiled to static assets
- Embedded in `assets/` folder of Jezail APK
- Served locally via internal HTTP server (default port `8080`)
- Interacts with Jezail REST API for dynamic control
- Accessible from any device on the same network

### Development Workflow
1. Clone [`jezail_ui`](https://github.com/zahidaz/jezail_ui)
2. Build using `flutter build web --release`
3. Copy output files into `jezail/app/src/main/assets/web/`
4. Rebuild Jezail APK to embed updated UI

Access the UI by navigating to:  
```
http://<device-ip>:8080/
```