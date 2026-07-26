"""
Webcam Capture — Local USB webcam feed.
"""

import logging

import numpy as np

log = logging.getLogger("watcher.camera.webcam")


class WebcamCapture:
    """
    Local USB webcam / built-in camera capture interface.

    Provides controlled testing with manual exposure and settings
    where hardware supports it.
    """

    def __init__(self, source: int = 0, width: int = 1280, height: int = 720, fps: int = 30):
        """
        Args:
            source: Camera device index (0 = default webcam)
            width: Desired frame width
            height: Desired frame height
            fps: Desired frame rate
        """
        import cv2

        self.source = source
        self.cap = cv2.VideoCapture(source)

        if not self.cap.isOpened():
            log.error("Failed to open webcam (source=%s)", source)
            raise RuntimeError(f"Webcam {source} not available")

        # Attempt to set parameters
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
        self.cap.set(cv2.CAP_PROP_FPS, fps)

        # Reduce buffer for real-time performance
        self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

        actual_width = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        actual_height = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        actual_fps = self.cap.get(cv2.CAP_PROP_FPS)

        log.info(
            "Webcam initialized: source=%s, %dx%d @ %.1f fps",
            source,
            actual_width,
            actual_height,
            actual_fps,
        )

    def capture(self) -> np.ndarray:
        """
        Capture a single frame from the webcam.

        Returns:
            BGR image as numpy array, or None on failure
        """
        import cv2

        ret, frame = self.cap.read()
        if not ret or frame is None:
            log.warning("Failed to read frame from webcam")
            return None

        return frame

    def set_exposure(self, value: int) -> bool:
        """
        Set manual exposure value.

        Args:
            value: Exposure value (negative for auto, positive for manual, camera-dependent)

        Returns:
            True if setting was accepted
        """
        import cv2

        return bool(self.cap.set(cv2.CAP_PROP_AUTO_EXPOSURE, value))

    def set_focus(self, value: int) -> bool:
        """Set manual focus."""
        import cv2

        return bool(self.cap.set(cv2.CAP_PROP_FOCUS, value))

    def release(self):
        """Release camera resources."""
        import cv2

        if self.cap is not None:
            self.cap.release()
            log.info("Webcam released")

    def __str__(self):
        return f"WebcamCapture(source={self.source})"
