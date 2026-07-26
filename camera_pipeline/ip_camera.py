"""
IP Camera Capture — RTSP/ONVIF camera integration for surveillance-grade testing.
"""

import logging

import numpy as np

log = logging.getLogger("watcher.camera.ip")


class IPCameraCapture:
    """
    IP Camera / surveillance camera capture interface.

    Connects to RTSP or HTTP streams typical of surveillance cameras
    (Hikvision, Dahua, Reolink, Amcrest, etc.).

    Useful for testing patterns against the types of cameras used in
    real surveillance deployments.
    """

    def __init__(self, stream_url: str, username: str = "", password: str = ""):
        """
        Args:
            stream_url: RTSP or HTTP stream URL
            username: Camera username (if required)
            password: Camera password (if required)
        """
        self.stream_url = stream_url
        self.username = username
        self.password = password
        self.cap = None

        import cv2

        # Embed credentials in URL if provided
        url = stream_url
        if username and password:
            # Insert credentials into RTSP URL
            # rtsp://user:pass@host:port/path
            if "@" not in url:
                url = url.replace("://", f"://{username}:{password}@")

        log.info("Connecting to IP camera: %s", url.replace(password, "****") if password else url)

        self.cap = cv2.VideoCapture(url)

        # Try to reduce buffering for lower latency
        self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

        if not self.cap.isOpened():
            log.warning(
                "Failed to open camera stream. "
                "Check URL, credentials, and network connectivity."
            )
        else:
            # Print stream info
            width = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            height = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            fps = self.cap.get(cv2.CAP_PROP_FPS)
            log.info("Camera stream opened: %dx%d @ %.1f fps", width, height, fps)

    def capture(self) -> np.ndarray:
        """
        Capture a single frame from the IP camera.

        Returns:
            BGR image as numpy array, or None on failure
        """
        import cv2

        if self.cap is None or not self.cap.isOpened():
            return None

        ret, frame = self.cap.read()
        if not ret or frame is None:
            log.warning("Failed to read frame from IP camera")
            return None

        return frame

    def set_resolution(self, width: int, height: int) -> bool:
        """Attempt to set camera resolution."""
        import cv2

        if self.cap is None:
            return False

        ok_w = self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        ok_h = self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
        return bool(ok_w and ok_h)

    def release(self):
        """Release camera resources."""
        import cv2

        if self.cap is not None:
            self.cap.release()
            log.info("Camera stream released")

    def __str__(self):
        sanitized = self.stream_url
        if self.password:
            sanitized = sanitized.replace(self.password, "****")
        return f"IPCameraCapture({sanitized})"
