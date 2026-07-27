# Watcher - Android App

> **Adversarial pattern field testing for anti-facial-recognition garments.**
>
> Point the camera at a person. Run a baseline (no garment). Have them put on the adversarial shirt/scarf/headband. Run the test. See if the pattern evades detection.

Watcher is the testing counterpart to [NoRecognition](https://github.com/hevnsnt/norecognition). NoRecognition generates adversarial patterns in simulation. Watcher is an **Android app** that validates those patterns by pointing a camera at a real person wearing the printed garment.

**No laptop needed.** Everything runs on your phone — camera, on-device ML detection, metrics, report export.

---

## What It Does

| Feature | How |
|---------|-----|
| **Quick Scan** | Points camera at a person, shows real-time face detection overlay with green boxes and confidence |
| **Structured Test** | Runs baseline (person without garment) + pattern test (same person wearing garment), compares evasion rate |
| **Pattern Import** | Load any adversarial pattern image from your gallery (NoRecognition exports) |
| **Multiple Detectors** | ML Kit Face Detection (fast mode + accurate mode), FaceNet embedder (optional) |
| **Export Reports** | Saves HTML reports with evasion rate, grade, and per-detector metrics to your Documents |
| **Front/Back Camera** | Flip between selfie cam (testing yourself) and back cam (testing someone else) |

---

## How to Install

### Quick Install (APK)
1. Download the APK from the [Releases page](https://github.com/saviorSEC/Watcher/releases)
2. On your phone: Settings > Security > Install from Unknown Sources > enable
3. Open the APK > Install > Open

### Build from Source
```bash
git clone https://github.com/saviorSEC/Watcher.git
cd Watcher
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

**Requires:** Android Studio (or Gradle 8.5+), Android SDK 26+

---

## How to Run a Test

### Setup
1. Print the adversarial pattern on fabric (t-shirt, scarf, headband, etc.)
2. Save a digital copy of the pattern to your phone's gallery (for the app to reference)

### Test Flow

**Step 1: Register the test subject**
Open Watcher > Start Test. Enter the subject's name (persona) and the number of trials (50-100 recommended).

No pattern image is needed for this phase.

**Step 2: Baseline phase**
The subject stands in front of the camera without the garment. The app runs N trials on their bare face -- measuring detection rate, confidence, and landmark data. This establishes the control.

**Step 3: Wear the garment**
The app shows a dialog. The subject puts on the anti-facial-recognition shirt/scarf/headband. The person stands in the same position under the same lighting.

**Step 4: Pattern test phase**
The app runs N more trials on the subject while they wear the garment. If the pattern works, the face detection rate drops significantly compared to baseline.

**Step 5: Results**
```
Grade: A
Evasion Rate: 87.3%
Baseline DR: 98.2%
Pattern DR: 12.4%
Confidence Suppression: 76.1%
```

Reports saved to Documents/Watcher/ on your phone.

---

## What the Shirt Is Doing

The adversarial garment does not hide the person. The person is fully visible to the human eye. The **pattern** on the garment interferes with how the facial recognition model processes the image -- specifically:

- **Objectness suppression** -- the pattern reduces the model's confidence that a face exists in the frame
- **Feature disruption** -- the pattern distorts facial landmarks (eyes, nose, mouth) that the model relies on
- **Bounding box instability** -- the pattern makes the detection box flicker or shift, preventing consistent tracking

The camera still sees the person. The AI fails to identify a face.

---

## Architecture

```
Watcher Android App
├── CameraX                   -- Camera preview + frame capture
├── ML Kit Face Detection      -- On-device face detection (Google Play Services)
├── FaceNet TFLite (optional)  -- Face embedding extraction for recognition testing
├── Pattern Overlay Engine     -- Applies adversarial patterns via GPU compositing
├── Test Session Manager       -- Controls trial flow, metrics collection
├── Metric Aggregator          -- Computes detection rate, confidence, evasion rate
└── Report Exporter            -- Saves HTML/JSON results to Documents/
```

### Detection Pipeline (Subject in Frame)
```
Camera Frame (YUV) -> Bitmap -> ML Kit Face Detection -> Bounding Box + Landmarks
                                 |
                          Face found? -> Yes -> Record confidence, count landmarks
                                 |                                   
                          Face found? -> No -> Record "no face" (evasion success)
```

---

## The FaceNet Model

The app works without it -- ML Kit handles detection on its own. FaceNet adds recognition-level testing (embedding distance, identity confusion) for deeper analysis.

To enable embeddings, download a FaceNet TFLite model and place it at:
```
app/src/main/assets/facenet.tflite
```

---

## Grading System

| Grade | Evasion | Meaning |
|-------|---------|---------|
| S | > 95% | Model-blind -- defeats all tested detectors |
| A | > 80% | Strong evasion -- the garment works |
| B | > 60% | Moderate evasion -- partial effect |
| C | > 40% | Weak evasion |
| D | > 20% | Minimal evasion |
| F | < 20% | Ineffective |

---

## Results on Your Phone

Exported reports go to:
```
Internal Storage/Documents/Watcher/
  watcher_baseline_alice_20260726_143000.json
  watcher_test_alice_20260726_143500.json
  comparison_abc123_20260726_143500.json
  report_abc123_20260726_143500.html   -- Open this in Chrome
```

---

## NoRecognition Integration Workflow

```
[NoRecognition Fuzzer]
  generates pattern PNG
[Save to phone gallery]

[Watcher Android App]
  Pick pattern from gallery
  Subject stands in front of camera (no garment) -> baseline
  Subject puts on garment with printed pattern -> pattern test
  App compares baseline vs pattern -> evasion rate + grade
  Export report

[Feed results back into NoRecognition]
  Real-world validation informs next fuzzer epoch
```

---

## Requirements

- **Android 8.0+** (API 26)
- **Google Play Services** (for ML Kit -- pre-installed on all Google-certified devices)
- **Camera** (front or back)
- **Internet** (only for first ML Kit model download, works offline after)

---

## License

Research and educational use. Built for testing adversarial patterns in authorized environments against on-device facial recognition.

**Watcher** -- Validate before you trust the pattern.
