# GCam APK reverse-engineering workspace

Place the legally obtained APK to analyze at `reverse/base.apk`.

The `APK Reverse Engineering` workflow uses JADX for DEX analysis and Apktool for APK decoding/rebuilding. JADX supports APK/Dex input and source search; Apktool supports decoding APKs and rebuilding decoded directories. The workflow only analyzes the supplied APK and does not modify or redistribute it automatically.

For the GCam Tint project, the intended targets are the existing ManualWhiteBalance implementation, `tint=` parameters, and Camera2 color-correction request construction. Any modified APK must be rebuilt and signed with a test key before installation.
