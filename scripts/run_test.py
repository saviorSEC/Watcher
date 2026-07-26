#!/usr/bin/env python3
"""
Watcher Test Runner — Execute a controlled adversarial pattern test.

Usage:
    # List available cameras
    python run_test.py --list-cameras

    # Run baseline (no pattern)
    python run_test.py --persona alice --camera 0 --output results/baseline/

    # Run with adversarial pattern
    python run_test.py --persona alice --pattern patterns/shirt_v1.png \\
        --camera android --trials 100 --detectors yolo11n mtcnn retinaface \\
        --output results/test_shirt_v1/

    # Export results for comparison
    python run_test.py --persona bob --camera rtsp://192.168.1.100:554/stream1 \\
        --trials 200 --output results/ip_cam_test/
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.watcher import main

if __name__ == "__main__":
    main()
