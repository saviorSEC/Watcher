# Watcher 👁️

> **Adversarial Pattern Field Testing Framework — Validate anti-AI facial recognition garments in real-world conditions.**

Watcher is the testing counterpart to [NoRecognition](https://github.com/hevnsnt/norecognition). While NoRecognition **generates** adversarial patterns, Watcher **validates them** — running controlled field tests against real facial recognition pipelines on Android phones, IP cameras, and webcams to measure how well those patterns actually work.

---

## Philosophy

NoRecognition finds patterns that confuse AI. Watcher proves they work in the wild.

| System | Role |
|--------|------|
| **NoRecognition** (Bill Swearingen) | Pattern generation fuzzer — evolutionary adversarial pattern design |
| **DarkCogswell / Sandbox** | Pattern validation against 11 simulated detectors |
| **Watcher** (this project) | **Real-world field testing** — mobile, camera, and pipeline validation |

Watcher bridges the gap between digital simulation and physical reality. A pattern that scores 90% in simulation may fail in the wild due to lighting, fabric texture, camera angle, or compression artifacts. Watcher catches that.

---

## What It Does

- ✅ **Real-time facial detection** — Runs YOLO, MTCNN, RetinaFace, FaceNet, and ArcFace against live camera feeds
- ✅ **Controlled field testing** — Test shirts/patterns against a known pipeline with repeatable protocols
- ✅ **Android camera integration** — Use any Android phone as a test camera via ADB/IP Webcam
- ✅ **IP camera support** — Test against CCTV/doorbell cameras (RTSP/ONVIF)
- ✅ **Metric collection** — Detection rate, confidence scores, bounding box stability, identification rate
- ✅ **Batched testing** — Run N trials per pattern/persona/angle/lighting condition
- ✅ **Result visualization** — Confusion matrices, ROC curves, detection heatmaps

---

## Repository Structure

```
watcher/
├── app/                      # Core testing engine
│   ├── watcher.py            # Main facial recognition testing app
│   ├── detectors/            # Facial detection backends
│   │   ├── yolo_detector.py
│   │   ├── mtcnn_detector.py
│   │   ├── retinaface_detector.py
│   │   └── facenet_recognizer.py
│   └── pipeline.py           # Recognition pipeline orchestrator
├── camera_pipeline/          # Camera integration
│   ├── android_capture.py    # Android phone as camera (ADB)
│   ├── ip_camera.py          # RTSP/ONVIF camera support
│   └── webcam_capture.py     # Local webcam
├── datasets/                 # Test subject management
│   ├── persona_manager.py    # Persona registry & ground truth
│   └── personas/             # Reference face images
├── docs/                     # Documentation
│   ├── methodology.md        # Full testing methodology
│   ├── testing_protocol.md   # Step-by-step test protocols
│   └── hardware_setup.md     # Camera & phone setup guide
├── results/                  # Test results (gitignored raw data)
├── scripts/                  # Utility scripts
│   ├── run_test.py           # Single/multi test runner
│   └── evaluate_results.py   # Result analysis & visualization
├── test_protocols/           # Protocol definitions
│   ├── standard_protocol.yaml
│   └── adversarial_protocol.yaml
├── requirements.txt
└── .gitignore
```

---

## Quick Start

```bash
git clone https://github.com/saviorSEC/Watcher.git
cd Watcher
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt

# List connected cameras
python scripts/run_test.py --list-cameras

# Run a baseline test (no pattern)
python scripts/run_test.py --persona alice --camera 0 --output results/baseline/

# Run with adversarial pattern
python scripts/run_test.py --persona alice --pattern patterns/my_shirt.png --camera android --output results/test_01/
```

---

## What You Need

### Hardware
- **Android phone** (any with ADB debugging or IP Webcam, for pattern testing on real mobile cameras)
- **USB webcam** (for controlled lab testing)
- **IP camera** (optional, for testing against surveillance-style hardware)
- **Printer** (to print adversarial patterns on fabric or paper)

### Software
- Python 3.10+
- OpenCV
- PyTorch (for detector models)
- ADB (Android Debug Bridge)
- CUDA-capable GPU recommended for real-time testing

---

## Testing Methodology (TL;DR)

1. **Establish baseline** — Run detection on bare face, collect metrics
2. **Apply pattern** — Subject wears the adversarial shirt/pattern
3. **Run trials** — Vary angle, distance, lighting per protocol
4. **Collect metrics** — Detection rate, confidence, identification
5. **Compare** — Calculate evasion rate vs baseline
6. **Report** — Generate pass/fail per detector model

Full methodology: [`docs/methodology.md`](docs/methodology.md)

---

## Integration with NoRecognition

Watcher slots into the NoRecognition pipeline at the validation stage:

```
NoRecognition Fuzzer → Generates Pattern → Watcher Validates on Real Hardware → Results Feed Back → Next Epoch
```

Import patterns from NoRecognition exports:
```bash
python scripts/run_test.py \
  --persona alice \
  --pattern /path/to/norecognition/exports/pattern_epoch_42.png \
  --detectors yolo11n,retinaface,facenet \
  --protocol adversarial
```

---

## Detector Models Supported

| Model | Type | Purpose |
|-------|------|---------|
| YOLO11n / YOLOv8 | Detection | Real-time face detection (Ultralytics) |
| MTCNN | Detection | Lightweight face detection |
| RetinaFace | Detection | High-accuracy face detection |
| FaceNet | Recognition | Face embedding & identification |
| ArcFace | Recognition | Accurate face recognition |
| MediaPipe | Detection | Mobile-optimized detection |
| OpenCV Haar | Detection | Legacy baseline detector |

---

## Example: Testing a Shirt

```bash
# 1. Capture baseline (no shirt)
python scripts/run_test.py --persona alice --camera android --trials 50 --tag baseline

# 2. Test with adversarial shirt
python scripts/run_test.py --persona alice --camera android --pattern /path/to/shirt_pattern.png --trials 50 --tag shirt_v1

# 3. Compare results
python scripts/evaluate_results.py --baseline results/baseline --test results/shirt_v1 \
  --output results/report_shirt_v1.html
```

---

## Contributing

This project is for research and educational purposes — testing adversarial patterns against facial recognition systems in authorized environments. PRs welcome for:

- New detector model integrations
- Camera platform backends
- Testing protocol definitions
- Result analysis improvements

---

## Related Projects

- **[NoRecognition](https://github.com/hevnsnt/norecognition)** — The pattern generator (Bill Swearingen/SecKC)
- **[DarkCogswell Sandbox](https://sandbox.norecognition.org)** — Digital pattern validation dashboard
- **[Ultralytics YOLO](https://www.ultralytics.com)** — Detection model used in testing

---

## License

Research & educational use. Subject to applicable laws regarding facial recognition and biometric testing.

**Watcher** — *Validate before you trust the pattern.*
