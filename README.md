# Watcher - Android App

> **Adversarial pattern field testing — right on your phone.**
>
> Open the app, point the camera at a shirt, and see if it evades ML Kit face detection in real-time.

Watcher is the testing counterpart to [NoRecognition](https://github.com/hevnsnt/norecognition). While NoRecognition **generates** adversarial patterns in simulation, Watcher is an **Android app** that validates those patterns against real on-device facial recognition.

**No laptop needed.** Everything runs on your phone — camera, detection, metrics, report export.

---

## What It Does

| Feature | How |
|---------|-----|
| **Quick Scan** | Real-time face detection with live bounding boxes and confidence overlay |
| **Structured Test** | Runs baseline (bare face) + pattern test (with shirt), compares evasion rate |
| **Pattern Import** | Load any adversarial pattern image from your gallery |
| **Multiple Detectors** | ML Kit Face Detection (fast mode + accurate mode), FaceNet embedder (optional) |
| **Export Reports** | Saves HTML reports with evasion rate, grade, and per-detector metrics to your Documents |
| **Front/Back Camera** | Flip between selfie cam (testing your own shirt) and back cam (testing others) |

---

## How to Install

### Quick Install (APK)
1. Download the APK from the [Releases page](https://github.com/saviorSEC/Watcher/releases)
2. On your phone: Settings → Security → Install from Unknown Sources → enable
3. Open the APK → Install → Open

### Build from Source
```bash
git clone https://github.com/saviorSEC/Watcher.git
cd Watcher
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

**Requires:** Android Studio (or Gradle 8.5+), Android SDK 26+

---

## How to Test Your Shirt

### 1. Save your adversarial pattern to your phone
Export a pattern from NoRecognition (or download from DarkCogswell sandbox) and save it as an image on your phone.

### 2. Open Watcher → Start Test
```
Persona Name: [your_name]
Trials: 50
Select Pattern: [choose shirt pattern image]
```

### 3. Baseline Phase
App runs 50 trials on your bare face. Stand still, look at the camera.

### 4. Put on the shirt
The app shows a dialog — that's your cue to put on the adversarial shirt.

### 5. Pattern Phase
App runs another 50 trials with the pattern overlaid/detected on your face.

### 6. Get Your Grade
The app shows:
```
Grade: A
Evasion Rate: 87.3%
Baseline DR: 98.2%
Pattern DR: 12.4%
Confidence Suppression: 76.1%
```

Reports are saved to `Documents/Watcher/` on your phone.

---

## Architecture

```
Watcher Android App
├── CameraX                   ← Camera preview + frame capture
├── ML Kit Face Detection      ← On-device face detection (Google Play Services)
├── FaceNet TFLite (optional)  ← Face embedding extraction for recognition testing
├── Pattern Overlay Engine     ← Applies adversarial patterns via GPU compositing
├── Test Session Manager       ← Controls trial flow, metrics collection
├── Metric Aggregator          ← Computes detection rate, confidence, evasion rate
└── Report Exporter            ← Saves HTML/JSON results to Documents/
```

### Detection Pipeline
```
Camera Frame (YUV) → Bitmap → ML Kit Face Detection → Bounding Box + Landmarks
                                 ↓
                          Face Detected? → Yes → Record confidence, count landmarks
                                 ↓
                          No → Record "no face", increment trial counter
```

---

## The FaceNet Model

The app **fully works without it** — ML Kit handles detection on its own. FaceNet adds recognition-level testing (embedding distance, identity confusion).

To enable embeddings, download a FaceNet TFLite model and place it at:
```
app/src/main/assets/facenet.tflite
```

Recommended sources:
- [TFLite FaceNet](https://github.com/serengil/deepface) — Convert the Keras model
- [tflite-face-recognition](https://github.com/thanhtbt/tflite-face-recognition) — Pre-converted models

---

## Grading System

| Grade | Evasion | Meaning |
|-------|---------|---------|
| **S** | > 95% | Model-blind — holy grail |
| **A** | > 80% | Strong — the shirt works |
| **B** | > 60% | Moderate — partial effect |
| **C** | > 40% | Weak |
| **D** | > 20% | Minimal |
| **F** | < 20% | Ineffective |

---

## Results on Your Phone

Exported reports go to:
```
Internal Storage/Documents/Watcher/
├── watcher_baseline_alice_20260726_143000.json
├── watcher_test_alice_20260726_143500.json
├── comparison_abc123_20260726_143500.json
└── report_abc123_20260726_143500.html   ← Open this in Chrome
```

Open the HTML report directly in Chrome for full metrics with grading tables.

---

## NoRecognition Integration Workflow

```
[NoRecognition Fuzzer]
  ↓ generates pattern PNG
[Save to phone gallery]
  ↓
[Watcher Android App]
  Load pattern → Run baseline → Wear shirt → Run test → Get grade
  ↓
[Export report]
  ↓
[Feed results back into NoRecognition fuzzer]
  Next epoch learns from real-world validation
```

---

## Requirements

- **Android 8.0+** (API 26)
- **Google Play Services** (for ML Kit — pre-installed on all Google-certified devices)
- **Camera** (front or back)
- **Internet** (only for first ML Kit model download, works offline after)

---

## License

Research & educational use. Built for testing adversarial patterns in authorized environments against on-device facial recognition.

**Watcher** - Your phone. Your patterns. Your data. No cloud.
