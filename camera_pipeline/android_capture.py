"""
Android Camera Capture — Use Android phone as a test camera via ADB or IP Webcam.

Supports two modes:
1. ADB screencap — Pulls frames via Android Debug Bridge (reliable, lower FPS)
2. IP Webcam — Streams video from IP Webcam app (higher FPS, requires WiFi)
"""

import logging
import subprocess
import tempfile
import time
from pathlib import Path

import numpy as np

log = logging.getLogger("watcher.camera.android")


class AndroidCapture:
    """
    Android phone camera capture interface.

    Uses ADB for device communication. Requires ADB debugging enabled
    on the Android device and `adb` in PATH.

    Alternatively, uses IP Webcam HTTP stream for higher frame rates.
    """

    def __init__(self, mode: str = "adb", device_id: str = None, ip_webcam_url: str = None):
        """
        Args:
            mode: "adb" for ADB screencap, "ipwebcam" for IP Webcam streaming
            device_id: ADB device serial (auto-detect if None)
            ip_webcam_url: IP Webcam stream URL (e.g., http://192.168.1.100:8080/video)
        """
        self.mode = mode
        self.device_id = device_id
        self.ip_webcam_url = ip_webcam_url
        self.cap = None

        if mode == "adb":
            self._init_adb()
        elif mode == "ipwebcam":
            self._init_ipwebcam()
        else:
            raise ValueError(f"Unknown mode: {mode}")

    def _init_adb(self):
        """Initialize ADB connection."""
        try:
            result = subprocess.run(
                ["adb", "devices"],
                capture_output=True,
                text=True,
                timeout=5,
            )

            devices = []
            for line in result.stdout.strip().split("\n")[1:]:
                if line.strip() and "device" in line and "unauthorized" not in line:
                    devices.append(line.split("\t")[0])

            if self.device_id:
                if self.device_id not in devices:
                    log.warning(
                        "Specified device '%s' not found in adb devices",
                        self.device_id,
                    )
            else:
                if devices:
                    self.device_id = devices[0]
                    log.info("Using ADB device: %s", self.device_id)
                else:
                    log.error("No ADB devices found. Connect an Android device with USB debugging.")
                    raise RuntimeError("No ADB devices available")

            log.info("Android camera initialized (ADB mode)")

        except FileNotFoundError:
            log.error(
                "adb not found in PATH. Install Android platform tools or use IP Webcam mode."
            )
            raise
        except subprocess.TimeoutExpired:
            log.error("ADB command timed out")
            raise

    def _init_ipwebcam(self):
        """Initialize IP Webcam stream."""
        try:
            import cv2

            url = self.ip_webcam_url or "http://localhost:8080/video"
            self.cap = cv2.VideoCapture(url)

            if not self.cap.isOpened():
                log.error("Failed to open IP Webcam stream: %s", url)
                raise RuntimeError(f"IP Webcam connection failed: {url}")

            log.info("Android camera initialized (IP Webcam mode): %s", url)

        except ImportError:
            log.error("OpenCV not installed. Run: pip install opencv-python")
            raise

    def capture(self) -> np.ndarray:
        """
        Capture a single frame from the Android device.

        Returns:
            BGR image as numpy array, or None on failure
        """
        if self.mode == "adb":
            return self._capture_adb()
        else:
            return self._capture_ipwebcam()

    def _capture_adb(self) -> np.ndarray:
        """Capture frame via ADB screencap."""
        try:
            import cv2

            with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
                tmp_path = tmp.name

            # Capture screen on device
            subprocess.run(
                ["adb", "-s", self.device_id, "exec-out", "screencap", "-p"],
                capture_output=True,
                timeout=5,
            )

            # Alternative: pull via file
            subprocess.run(
                [
                    "adb", "-s", self.device_id,
                    "shell", "screencap", "-p", "/sdcard/watcher_frame.png",
                ],
                capture_output=True,
                timeout=5,
            )

            subprocess.run(
                ["adb", "-s", self.device_id, "pull", "/sdcard/watcher_frame.png", tmp_path],
                capture_output=True,
                timeout=5,
            )

            # Clean up device-side file
            subprocess.run(
                ["adb", "-s", self.device_id, "shell", "rm", "/sdcard/watcher_frame.png"],
                capture_output=True,
                timeout=3,
            )

            frame = cv2.imread(tmp_path)
            Path(tmp_path).unlink(missing_ok=True)

            if frame is None:
                log.warning("Failed to read captured frame")
                return None

            return frame

        except subprocess.TimeoutExpired:
            log.warning("ADB screencap timed out")
            return None
        except Exception as e:
            log.warning("ADB capture failed: %s", e)
            return None

    def _capture_ipwebcam(self) -> np.ndarray:
        """Capture frame from IP Webcam stream."""
        import cv2

        if self.cap is None:
            return None

        ret, frame = self.cap.read()
        if not ret or frame is None:
            log.warning("Failed to read from IP Webcam stream")
            return None

        return frame

    def release(self):
        """Release camera resources."""
        if self.cap is not None:
            self.cap.release()
            log.info("IP Webcam stream released")

    def __str__(self):
        if self.mode == "adb":
            return f"AndroidCapture(adb, device={self.device_id})"
        else:
            return f"AndroidCapture(ipwebcam, url={self.ip_webcam_url})"
