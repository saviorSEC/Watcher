#!/usr/bin/env python3
"""
Watcher — Adversarial Pattern Field Testing Framework

Validates anti-facial-recognition garments/patterns against real-world
facial recognition pipelines on Android phones, IP cameras, and webcams.

Usage:
    python watcher.py --persona alice --camera 0 --output results/test_01/
    python watcher.py --list-personas
    python watcher.py --list-detectors
"""

import argparse
import json
import logging
import sys
from pathlib import Path
from typing import Optional

# Add parent to path for module imports
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.detectors.yolo_detector import YOLODetector
from app.detectors.mtcnn_detector import MTCNNDetector
from app.detectors.retinaface_detector import RetinaFaceDetector
from app.detectors.facenet_recognizer import FaceNetRecognizer
from app.pipeline import RecognitionPipeline


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(message)s",
)
log = logging.getLogger("watcher")


def build_pipeline(detectors: list[str], device: str = "cpu") -> RecognitionPipeline:
    """Build a recognition pipeline from named detector backends."""
    instances = []
    for name in detectors:
        name = name.strip().lower()
        if name in ("yolo11n", "yolov8", "yolo"):
            instances.append(YOLODetector(model_name=name, device=device))
        elif name == "mtcnn":
            instances.append(MTCNNDetector(device=device))
        elif name == "retinaface":
            instances.append(RetinaFaceDetector(device=device))
        elif name == "facenet":
            instances.append(FaceNetRecognizer(model_name="facenet", device=device))
        elif name == "arcface":
            instances.append(FaceNetRecognizer(model_name="arcface", device=device))
        else:
            log.warning("Unknown detector '%s' — skipping", name)
    return RecognitionPipeline(detectors=instances)


def list_detectors():
    """Print available detector backends."""
    detectors = [
        ("yolo11n", "Ultralytics YOLO11n — real-time face detection"),
        ("yolov8", "Ultralytics YOLOv8 — real-time face detection"),
        ("mtcnn", "MTCNN — lightweight face detection"),
        ("retinaface", "RetinaFace — high-accuracy face detection"),
        ("facenet", "FaceNet — face recognition (embedding-based)"),
        ("arcface", "ArcFace — face recognition (embedding-based)"),
    ]
    print("\nAvailable Detectors:")
    print("=" * 70)
    for name, desc in detectors:
        print(f"  {name:<15}  {desc}")
    print()


def list_personas(persona_dir: str | Path = "datasets/personas"):
    """List available test personae (reference face images)."""
    pdir = Path(persona_dir)
    if not pdir.exists():
        print(f"\nNo persona directory found at {pdir}")
        print("Create reference images in datasets/personas/")
        print("  Format: <persona_name>.<ext> (e.g., alice.jpg)")
        return

    images = list(pdir.glob("*"))
    print(f"\nAvailable Personae ({pdir}):")
    print("=" * 70)
    for img in sorted(images):
        if img.suffix.lower() in (".jpg", ".jpeg", ".png", ".bmp"):
            print(f"  {img.stem:<15}  {img.name}")
    print()


def run_test(
    persona: str,
    pattern_path: Optional[str],
    camera_source: str,
    trials: int,
    detectors: list[str],
    output_dir: str,
    device: str,
):
    """Execute a full test cycle."""
    log.info("=" * 60)
    log.info("Watcher Test Session")
    log.info("=" * 60)
    log.info("Persona      : %s", persona)
    log.info("Pattern      : %s", pattern_path or "NONE (baseline)")
    log.info("Camera       : %s", camera_source)
    log.info("Trials       : %d", trials)
    log.info("Detectors    : %s", ", ".join(detectors))
    log.info("Output dir   : %s", output_dir)
    log.info("Device       : %s", device)

    # Build pipeline
    pipeline = build_pipeline(detectors, device=device)
    log.info("Pipeline initialized with %d detector(s)", len(pipeline.detectors))

    # Resolve camera
    if camera_source in ("android", "adb"):
        from camera_pipeline.android_capture import AndroidCapture
        camera = AndroidCapture()
    elif camera_source.startswith("rtsp://") or camera_source.startswith("http://"):
        from camera_pipeline.ip_camera import IPCameraCapture
        camera = IPCameraCapture(camera_source)
    elif camera_source.isdigit() or camera_source == "webcam":
        from camera_pipeline.webcam_capture import WebcamCapture
        camera = WebcamCapture(source=int(camera_source) if camera_source.isdigit() else 0)
    else:
        log.error("Unknown camera source: %s", camera_source)
        sys.exit(1)

    log.info("Camera initialized: %s", camera)

    # Load pattern if provided
    pattern = None
    if pattern_path:
        import cv2
        pattern = cv2.imread(pattern_path)
        if pattern is None:
            log.error("Failed to load pattern from: %s", pattern_path)
            sys.exit(1)
        log.info("Pattern loaded: %s (%dx%d)", pattern_path, pattern.shape[1], pattern.shape[0])

    # Create output directory
    out_dir = Path(output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    frames_dir = out_dir / "frames"
    frames_dir.mkdir(exist_ok=True)

    # Run trials
    results = {
        "session_info": {
            "persona": persona,
            "pattern": pattern_path,
            "camera": str(camera),
            "trials": trials,
            "detectors": detectors,
            "device": device,
        },
        "trials": [],
    }

    for trial_idx in range(trials):
        log.info("Trial %d/%d", trial_idx + 1, trials)

        # Capture frame
        frame = camera.capture()
        if frame is None:
            log.warning("Failed to capture frame on trial %d", trial_idx)
            continue

        # Apply pattern overlay if present
        test_frame = frame.copy()
        if pattern is not None:
            # Simple pattern overlay (face region approximation)
            # Real implementation would use landmark detection or manual region
            h, w = test_frame.shape[:2]
            pattern_resized = cv2.resize(pattern, (w, h))
            test_frame = cv2.addWeighted(test_frame, 0.7, pattern_resized, 0.3, 0)

        # Run detection pipeline
        trial_result = pipeline.process(test_frame)

        # Store result
        trial_result["trial_id"] = trial_idx
        trial_result["persona"] = persona

        # Save frame periodically
        if trial_idx % 10 == 0 or trial_idx == trials - 1:
            cv2.imwrite(str(frames_dir / f"trial_{trial_idx:04d}.jpg"), test_frame)

        results["trials"].append(trial_result)

    # Aggregate results
    aggregate = pipeline.aggregate_results(results["trials"])
    results["aggregate"] = aggregate

    # Write results
    results_file = out_dir / "results.json"
    with open(results_file, "w") as f:
        json.dump(results, f, indent=2, default=str)

    # Print summary
    log.info("=" * 60)
    log.info("RESULTS SUMMARY")
    log.info("=" * 60)
    for det_name, det_results in aggregate.get("detectors", {}).items():
        dr = det_results.get("detection_rate", 0)
        mc = det_results.get("mean_confidence", 0)
        log.info("  %-15s  DR=%.3f  Conf=%.3f", det_name, dr, mc)

    log.info("Results saved to: %s", results_file)
    log.info("Frames saved to: %s", frames_dir)

    camera.release()
    return results


def parse_args():
    parser = argparse.ArgumentParser(
        description="Watcher — Adversarial Pattern Field Testing Framework"
    )
    parser.add_argument("--persona", help="Test subject persona name")
    parser.add_argument("--pattern", help="Path to adversarial pattern image (PNG)")
    parser.add_argument(
        "--camera",
        default="webcam",
        help='Camera source: "webcam", "android", "adb", RTSP URL, or device index (default: webcam)',
    )
    parser.add_argument("--trials", type=int, default=50, help="Number of trials per test (default: 50)")
    parser.add_argument(
        "--detectors",
        nargs="+",
        default=["yolo11n", "mtcnn", "retinaface"],
        help="Detector models to test against (default: yolo11n mtcnn retinaface)",
    )
    parser.add_argument("--output", default="results", help="Output directory (default: results)")
    parser.add_argument("--device", default="cpu", help='Torch device: "cpu" or "cuda" (default: cpu)')
    parser.add_argument("--list-detectors", action="store_true", help="List available detector models")
    parser.add_argument("--list-personas", action="store_true", help="List available test personas")
    return parser.parse_args()


def main():
    args = parse_args()

    if args.list_detectors:
        list_detectors()
        return

    if args.list_personas:
        list_personas()
        return

    if not args.persona:
        print("ERROR: --persona is required (use --list-personas to see available)")
        sys.exit(1)

    run_test(
        persona=args.persona,
        pattern_path=args.pattern,
        camera_source=args.camera,
        trials=args.trials,
        detectors=args.detectors,
        output_dir=args.output,
        device=args.device,
    )


if __name__ == "__main__":
    main()
