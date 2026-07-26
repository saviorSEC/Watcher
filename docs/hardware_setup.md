# Hardware Setup Guide

## Setting up cameras for Watcher testing

---

## 1. Android Phone Setup

### Option A: ADB (wired, low FPS but reliable)

**Requirements:**
- Android device with USB debugging
- USB cable
- `adb` command-line tool installed

**Steps:**
1. Enable Developer Options:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging → ON
3. Connect via USB:
   ```bash
   adb devices
   # Accept "Allow USB debugging?" prompt on phone
   # Run again to verify:
   adb devices
   ```
4. Keep screen ON during testing (disable screen timeout temporarily)

**Known working devices:**
- Google Pixel 7/8 — Excellent camera, USB 3.0, fast ADB transfers
- Samsung Galaxy S22/S23 — Good cameras, but USB 2.0 (slower ADB)
- OnePlus — Good, but may need additional drivers

### Option B: IP Webcam (wireless, high FPS)

**Requirements:**
- Android device on same WiFi as testing machine
- IP Webcam app (free on Play Store)

**Steps:**
1. Install [IP Webcam](https://play.google.com/store/apps/details?id=com.pas.webcam)
2. Open app → Start server
3. Note the URL shown (typically `http://<phone-ip>:8080/video`)
4. Test:
   ```bash
   python run_test.py --camera http://192.168.1.100:8080/video --trials 10
   ```

**Pro tip:** Set video quality to 720p for balanced resolution and speed.

---

## 2. USB Webcam Setup

**Recommended webcams for testing:**

| Camera | Resolution | FPS | Notes |
|--------|-----------|-----|-------|
| Logitech C920/C922 | 1080p | 30 | Industry standard, reliable |
| Logitech Brio | 4K | 30/60 | High-end, good for detailed testing |
| ELP USB Camera | 1080p | 60 | Global shutter option available |
| Raspberry Pi Camera Module 3 | Various | Varies | Good for embedded testing |

**Setup:**
```bash
# Check available cameras
ls /dev/video*
v4l2-ctl --list-devices

# Test with OpenCV
python3 -c "import cv2; cap=cv2.VideoCapture(0); print('OK' if cap.isOpened() else 'FAIL')"
```

**Pro tip:** Use `v4l2-ctl` to set manual exposure for consistent testing:
```bash
# Disable auto-exposure
v4l2-ctl -d /dev/video0 -c exposure_auto=1
# Set manual exposure value
v4l2-ctl -d /dev/video0 -c exposure_absolute=500
```

---

## 3. IP / Surveillance Camera Setup

**Recommended cameras for testing:**
- Hikvision DS-2CD2xx — Most common surveillance camera globally
- Dahua IPC-HFW — Common in Asia/EMEA
- Reolink RLC-520A — Popular consumer option
- Amcrest ProHD — Common US market camera
- Any ONVIF-compatible camera

**Connection:**
```bash
# RTSP URL formats by brand:
# Hikvision:   rtsp://user:pass@ip:554/Streaming/Channels/101
# Dahua:       rtsp://user:pass@ip:554/cam/realmonitor?channel=1&subtype=0
# Reolink:     rtsp://user:pass@ip:554/h264Preview_01_main
# Amcrest:     rtsp://user:pass@ip:554/cam/realmonitor?channel=1&subtype=0

python run_test.py --camera "rtsp://admin:password@192.168.1.200:554/Streaming/Channels/101" \
  --persona alice --trials 100
```

**Important:** IP cameras add compression artifacts (H.264/H.265 blocking) that can reduce pattern effectiveness. This is actually *realistic* — real surveillance systems use high compression.

---

## 4. Controlled Testing Environment

### Recommended Setup

```
                Camera
                  |
                  |  1-2m
                  |
            [Subject]
                  |
             Lux meter
```

**Lighting:**
- Use studio lights or bright LED panels (500-1000 lux at face)
- Diffuse the light to avoid harsh shadows
- Test three positions: front-lit, side-lit, back-lit

**Background:**
- Plain wall (no patterns competing with test pattern)
- Distance: at least 1m behind subject

**Markings:**
- Tape marks on floor for distances (0.5m, 1m, 2m, 5m)
- Tape marks for angles (0°, 15°, 30°, 45°) on floor

---

## 5. Pattern Printing for Fabric Tests

**Recommended:**
- Dye sublimation on polyester fabric (best color accuracy)
- Direct-to-garment (DTG) — acceptable
- Iron-on transfer paper — cheapest but lowest quality
- Screen printing — good but expensive for one-off tests

**Resolution:** 300 DPI minimum
**Size:** Pattern should cover face-facing area (~20cm × 25cm on a t-shirt)

---

## 6. Multi-Camera Setup

For simultaneous testing against multiple cameras:

```
        Cam 1 (webcam)     Cam 2 (Android)     Cam 3 (IP camera)
              |                  |                     |
              +-----------+-----+----------+----------+
                          |                |
                      [Subject]       Lux meter
```

```bash
# Run tests sequentially on same subject
for cam in 0 android rtsp://192.168.1.200:554/stream1; do
    python run_test.py --persona alice --camera "$cam" \
      --pattern pattern.png --output results/multi_cam/ --trials 50
done
```
