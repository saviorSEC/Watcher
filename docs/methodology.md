# Watcher Testing Methodology

## Rigorous validation of adversarial patterns against real-world facial recognition systems.

---

## 1. Core Principles

### 1.1 Controlled Variable Testing
Every test isolates one variable at a time to produce scientifically valid results.

**Variables to control:**
- Lighting (lux level, color temperature, direction)
- Distance (meters from camera)
- Angle (yaw, pitch, roll in degrees)
- Camera hardware (sensor, lens, resolution)
- Camera software (compression, auto-exposure, white balance)
- Subject identity
- Pattern placement and orientation

### 1.2 Statistical Significance
- Minimum **50 trials** per variable combination
- Use 95% confidence intervals for detection rates
- Report both detection rate **and** mean confidence score
- Include baseline measurements in every test session

### 1.3 Reproducibility
- Full test parameters recorded in YAML metadata
- Camera settings frozen where possible (manual mode)
- Environment conditions logged
- Raw frames saved for audit

---

## 2. The Facial Recognition Pipeline (Under Test)

Modern facial recognition follows this pipeline:

```
Raw Image → Face Detection → Alignment → Feature Extraction → Embedding → Matching
```

Watcher tests **each stage** independently:

| Pipeline Stage | What We Measure | How to Evade |
|---------------|----------------|--------------|
| **Detection** | Is the face found? (bounding box) | Pattern disrupts objectness score |
| **Alignment** | Are landmarks detected correctly? | Pattern distorts keypoint regression |
| **Feature Extraction** | Is embedding meaningful? | Pattern pushes embedding to noise |
| **Matching** | Does it match the right identity? | Pattern changes embedding to wrong identity |

### 2.1 Detection Models We Test Against

| Detector | Architecture | Notes |
|----------|-------------|-------|
| YOLO11n | Ultralytics YOLOv11 | Most common real-time detector |
| YOLOv8 | Ultralytics YOLOv8 | Widely deployed in production |
| RetinaFace | ResNet + FPN | High accuracy, common in research |
| MTCNN | Cascade CNN | Lightweight, mobile-friendly |
| MediaPipe | BlazeFace | Google's mobile detector |
| Haar Cascade | Viola-Jones | Legacy baseline |

### 2.2 Recognition Models We Test Against

| Model | Embedding Size | Notes |
|-------|---------------|-------|
| FaceNet | 128/512 | Google's face embedding |
| ArcFace | 512 | State-of-the-art recognition |
| VGGFace2 | 512 | Research baseline |
| DeepFace | varies | Meta's recognition model |

---

## 3. Test Variables

### 3.1 Environmental

| Variable | Values | Notes |
|----------|--------|-------|
| Lighting | 50, 200, 500, 1000 lux | Measured at face position |
| Light direction | Front, side (45°), backlit | |
| Background | Plain, cluttered, moving | Wall vs busy scene |
| Camera distance | 0.5m, 1m, 2m, 5m | |
| Camera angle | 0°, 15°, 30°, 45° yaw | Frontal to profile |
| Camera tilt | 0°, ±15° pitch | Up/down angle |

### 3.2 Pattern Variables

| Variable | Values | Notes |
|----------|--------|-------|
| Pattern type | Print, digital overlay, physical fabric | |
| Coverage | Face only, face+neck, full upper body | |
| Color | Grayscale, RGB, IR-safe | |
| Scale | How large the pattern is applied | |
| Stretch | Fabric stretch distortion | Important for fabric tests |
| Repeats | Tiled or single image | |
| Material | Paper, cotton, polyester, reflective | |

### 3.3 Subject Variables

| Variable | Values | Notes |
|----------|--------|-------|
| Identity | Per persona dataset | Multiple test subjects |
| Expression | Neutral, smile, talking | |
| Glasses | None, clear, sunglasses | |
| Headwear | None, hat, hood | |

---

## 4. Test Protocols

### 4.1 Standard Protocol

The most common test: does the pattern prevent detection?

```
Phase 1: Baseline Collection
  - 50 frames bare-faced at each distance/angle
  - Record detection rate and confidence per model

Phase 2: Pattern Applied
  - 50 frames with pattern at each distance/angle
  - Same conditions as baseline

Phase 3: Comparison
  - Calculate evasion rate = 1 - (pattern_detections / baseline_detections)
  - Per model, per distance, per angle
```

### 4.2 Adversarial Protocol

Tests whether the pattern actively confuses the model (not just hides).

```
Phase 1: Baseline
  - Collect ground truth embeddings for each persona

Phase 2: Pattern Test
  - Collect embeddings with pattern applied
  - Compare to ground truth

Metrics:
  - Embedding shift (L2 distance from baseline)
  - False negative rate (face detected but not recognized)
  - False positive rate (wrong identity match)
```

### 4.3 Temporal Stability Protocol

Tests whether the pattern works consistently over time.

```
- Record 5 minutes of continuous video
- Analyze per-frame detection
- Measure flicker rate (detected → not detected → detected)
- Report: stable evasion, periodic evasion, or failure
```

### 4.4 Multi-Camera Protocol

Tests the same pattern against multiple camera systems simultaneously.

```
- Set up 3+ cameras at different positions
- Subject performs scripted movement
- All cameras record synchronously
- Compare detection rates across hardware
```

---

## 5. Metrics & Scoring

### 5.1 Primary Metrics

| Metric | Formula | Meaning |
|--------|---------|---------|
| **Detection Rate (DR)** | detections / total_frames | How often is the face found? |
| **Evasion Rate (ER)** | 1 - (DR_pattern / DR_baseline) | How much better is the pattern vs nothing? |
| **Mean Confidence** | avg(confidence) for detections | How confident is the detector? |
| **Bounding Box Jitter** | std(bbox_center) over frames | Is the detection unstable? (good sign) |

### 5.2 Secondary Metrics

| Metric | Description |
|--------|-------------|
| **Identification Rate** | % of detections correctly identified |
| **False Positive ID Rate** | % of detections matched to wrong identity |
| **Embedding Distance** | L2 distance from baseline embedding |
| **Landmark Error** | NME (Normalized Mean Error) of detected landmarks |

### 5.3 Scoring

```
PASS_CONDITION = evasion_rate > 0.80 AND mean_confidence < 0.40
PARTIAL = evasion_rate > 0.50
FAIL = evasion_rate < 0.50

Grade:
  S : evasion_rate > 0.95 (model-blind)
  A : evasion_rate > 0.80 (strong)
  B : evasion_rate > 0.60 (moderate)
  C : evasion_rate > 0.40 (weak)
  D : evasion_rate > 0.20 (minimal)
  F : evasion_rate < 0.20 (ineffective)
```

---

## 6. Camera Hardware Testing

### 6.1 Android Phone Cameras

Android devices use varying camera hardware and processing pipelines:
- Different sensors (Sony IMX, Samsung ISOCELL, etc.)
- Different ISP processing (Qualcomm, MediaTek, Exynos)
- Different post-processing (HDR, noise reduction, sharpening)

**Testing via ADB:**
```
# Capture raw frame
adb shell screencap /sdcard/frame.png
adb pull /sdcard/frame.png

# Or stream via IP Webcam app
http://<phone-ip>:8080/video
```

### 6.2 IP Cameras

Surveillance cameras add additional challenges:
- Lower resolution (typically 1080p or lower)
- Higher compression (H.264/H.265 artifacts)
- IR mode (pattern may behave differently in IR)
- Auto-exposure adjustments

**Testing via RTSP:**
```
rtsp://<camera-ip>:554/stream1
```

### 6.3 USB Webcams

Most controlled testing environment:
- Known sensor and fixed settings
- Manual exposure control
- No compression artifacts (raw feed)

---

## 7. Pattern Import from NoRecognition

### 7.1 Digital Pattern Testing

```
1. Export pattern from NoRecognition fuzzer (PNG)
2. Overlay pattern on face region in video feed (digital simulation)
3. Run standard protocol
4. Score pattern
```

### 7.2 Physical Pattern Testing

```
1. Export pattern from NoRecognition
2. Print on fabric transfer paper or direct fabric
3. Apply to shirt/headband/scarf
4. Subject wears garment
5. Run standard protocol
6. Score pattern
7. Note: physical results will differ from digital (fabric drape, stretch)
```

---

## 8. Result Storage Format

Results stored as YAML + raw frames:

```yaml
test_session:
  id: "2026-07-26_watcher_001"
  timestamp: "2026-07-26T14:30:00Z"
  operator: "ek0ms"

protocol:
  name: "standard"
  trials_per_variable: 50

subject:
  persona: "alice"
  pattern: "norecognition_epoch_42.png"
  pattern_placement: ["face", "neck"]
  pattern_type: "digital_overlay"

environment:
  lighting_lux: 500
  distance_m: 1.0
  angle_yaw: 0
  background: "plain"

camera:
  type: "android"
  device: "pixel_7"
  resolution: [1920, 1080]
  fps: 30

detectors:
  yolo11n:
    detection_rate: 0.12
    mean_confidence: 0.31
    evasion_rate: 0.88
    grade: "A"
  retinaface:
    detection_rate: 0.45
    mean_confidence: 0.52
    evasion_rate: 0.55
    grade: "C"
  facenet:
    detection_rate: 0.22
    mean_confidence: 0.38
    evasion_rate: 0.78
    grade: "B"

overall_grade: "B"
```

---

## 9. Ethical Testing Guidelines

1. **Test on yourselves only** — All test subjects are project members who have given explicit informed consent
2. **No third-party surveillance** — Only test camera feeds you own or have explicit permission to use
3. **No retention of bystander data** — If testing in public, ensure no non-consenting persons are in frame
4. **Results kept internal** — Specific pattern designs and evasion rates are shared judiciously
5. **Use sandbox environment** — Initial validation on [sandbox.norecognition.org](https://sandbox.norecognition.org)

---

## References

- [NoRecognition - Adversarial Textile Research](https://github.com/hevnsnt/norecognition)
- [DarkCogswell Sandbox](https://sandbox.norecognition.org) — Digital validation dashboard
- [Ultralytics YOLO](https://www.ultralytics.com) — Face detection pipeline
- [NIST Face Recognition Vendor Test (FRVT)](https://www.nist.gov/programs-projects/face-recognition-vendor-test-frvt)
- [Adam Harvey - CV Dazzle](https://adam.harvey.studio/) — Pioneering adversarial fashion
