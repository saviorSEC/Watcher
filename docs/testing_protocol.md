# Testing Protocol — Step-by-Step

## How to run a full test session with Watcher

---

## Setup

### 1. Install Dependencies

```bash
git clone https://github.com/saviorSEC/Watcher.git
cd Watcher
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Create Test Personae

Place reference images of each test subject in `datasets/personas/`:

```
datasets/personas/
├── alice/
│   ├── ref_00.jpg
│   ├── ref_01.jpg
│   └── ref_02.jpg
├── bob/
│   ├── ref_00.jpg
│   └── ref_01.jpg
└── charlie.jpg          # Single-file persona
```

Capture reference images:
- Front-facing, neutral expression
- Good lighting (500+ lux)
- Plain background
- No pattern/garment being tested

### 3. Connect Cameras

**Android phone (ADB mode):**
```bash
# Enable Developer Options + USB Debugging on phone
adb devices
# Should show: <serial>  device
python run_test.py --list-cameras
```

**Android phone (IP Webcam mode):**
1. Install "IP Webcam" from Play Store
2. Start server on phone
3. Note the URL (e.g., http://192.168.1.100:8080/video)

**USB webcam:**
Plug in and check:
```bash
ls /dev/video*
python run_test.py --camera 0 --detectors yolo11n --trials 5
```

**IP/surveillance camera:**
```bash
python run_test.py --camera rtsp://192.168.1.200:554/stream1
```

---

## Running a Test

### Phase 1: Baseline (No Pattern)

```bash
# Standard test — 50 trials per variable combo
python run_test.py \
  --persona alice \
  --camera 0 \
  --trials 50 \
  --detectors yolo11n mtcnn retinaface \
  --output results/baseline_alice_webcam/
```

### Phase 2: Pattern Test

```bash
python run_test.py \
  --persona alice \
  --pattern /path/to/adversarial_pattern.png \
  --camera 0 \
  --trials 50 \
  --detectors yolo11n mtcnn retinaface \
  --output results/test_alice_shirt_v1/
```

### Phase 3: Evaluate

```bash
python evaluate_results.py \
  --baseline results/baseline_alice_webcam/ \
  --test results/test_alice_shirt_v1/ \
  --output results/report_alice_shirt_v1/
```

---

## Testing Variables (One at a Time)

### Distance Test
```bash
for dist in 0.5 1.0 2.0 5.0; do
  python run_test.py --persona alice --camera 0 --trials 50 \
    --output "results/distance_${dist}m/" \
    --pattern pattern.png
done
```

### Angle Test
```bash
for angle in 0 15 30 45; do
  python run_test.py --persona alice --camera 0 --trials 50 \
    --output "results/angle_${angle}deg/" \
    --pattern pattern.png
done
```

### Lighting Test
```bash
# Use lux meter at face position
# Adjust room lighting
for lux in 200 500 1000; do
  python run_test.py --persona alice --camera 0 --trials 50 \
    --output "results/lux_${lux}/" \
    --pattern pattern.png
done
```

---

## Physical Pattern Testing (Fabric)

### Printing Patterns
1. Export pattern from NoRecognition (PNG, 300+ DPI)
2. Print on fabric transfer paper or direct-to-garment
3. Apply to garment (t-shirt, scarf, headband)
4. Ensure pattern covers face-facing area of garment

### Setup for Fabric Test
- **Lighting:** 500+ lux, diffused (avoid harsh shadows)
- **Position:** Subject 1m from camera
- **Posture:** Natural standing, looking at camera
- **Garment:** Worn normally (don't stretch or adjust better than natural fit)

### Recording
```bash
# Continuous video capture for temporal analysis
python run_test.py --persona alice --camera 0 --trials 300 \
  --pattern /path/to/fabric_pattern.png \
  --detectors yolo11n retinaface facenet \
  --output results/physical_test_alice/
```

---

## Interpreting Results

### Key Metrics

| Detection Rate | What It Means |
|---------------|---------------|
| 0.0 - 0.20    | **Excellent** — Pattern is highly effective |
| 0.20 - 0.40   | **Good** — Most frames evade detection |
| 0.40 - 0.60   | **Moderate** — Pattern has some effect |
| 0.60 - 0.80   | **Weak** — Pattern barely works |
| 0.80 - 1.0    | **Failed** — Pattern does nothing |

### Comparing Against Different Models

A good pattern works across multiple detectors:
- **YOLO goal:** Evasion rate > 0.80
- **RetinaFace goal:** Evasion rate > 0.60 (harder detector)
- **FaceNet/ArcFace goal:** Evasion rate > 0.50 PLUS embedding shift > 0.5

### Sanity Checks

- Run baseline same-day as pattern test
- Test at least 2 different personae per pattern
- Run at least 50 trials per variable (100+ for publication-grade)
- Check raw frames — make sure pattern is visible and correctly positioned

---

## Example: Full Test Pipeline for One Shirt

```bash
#!/bin/bash
# Full test pipeline for one adversarial shirt

SHIRT="norecognition_shirt_v3"
PERSONA="alice"

echo "=== Phase 1: Baseline ==="
python run_test.py --persona $PERSONA --camera 0 --trials 100 \
  --detectors yolo11n yolo11s mtcnn retinaface facenet \
  --output "results/baseline_${PERSONA}/"

echo "=== Phase 2: Pattern Test ==="
python run_test.py --persona $PERSONA --camera 0 --trials 100 \
  --pattern "patterns/${SHIRT}.png" \
  --detectors yolo11n yolo11s mtcnn retinaface facenet \
  --output "results/${SHIRT}_${PERSONA}/"

echo "=== Phase 3: Evaluation ==="
python evaluate_results.py \
  --baseline "results/baseline_${PERSONA}/" \
  --test "results/${SHIRT}_${PERSONA}/" \
  --output "results/report_${SHIRT}_${PERSONA}/"

echo "=== Done! Report: results/report_${SHIRT}_${PERSONA}/report.html ==="
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| No faces detected in baseline | Check lighting, camera focus, subject position |
| Pattern not visible in frames | Check overlay code, pattern path, image format |
| ADB not working | `adb kill-server && adb start-server && adb devices` |
| Low FPS on Android | Use IP Webcam mode instead of ADB screencap |
| Camera not opening | Check USB connection, `ls /dev/video*`, permissions |
| MTCNN crashes on large images | Resize camera feed to 640x480 |
