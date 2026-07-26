"""
RetinaFace Detector — High-accuracy face detection.
"""

import logging

import numpy as np

log = logging.getLogger("watcher.detectors.retinaface")


class RetinaFaceDetector:
    """Face detection using RetinaFace."""

    def __init__(self, device: str = "cpu"):
        self.name = "retinaface"
        self.device = device

        try:
            from retinaface import RetinaFace as RetinaFaceModel

            self.detector = RetinaFaceModel
            log.info("RetinaFace detector loaded")
        except ImportError:
            log.error(
                "retinaface not installed. Run: pip install retina-face"
            )
            raise
        except Exception as e:
            log.error("Failed to load RetinaFace: %s", e)
            raise

    def detect(self, frame: np.ndarray) -> dict:
        """
        Detect faces using RetinaFace.

        Args:
            frame: BGR image as numpy array

        Returns:
            dict with face count, confidence, bboxes, landmarks
        """
        try:
            import cv2

            # RetinaFace expects RGB
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            faces = self.detector.detect_faces(rgb)
        except Exception as e:
            log.warning("RetinaFace detection failed: %s", e)
            return {"face_count": 0, "confidence": 0.0, "bboxes": [], "landmarks": []}

        face_count = 0
        bboxes = []
        landmarks = []
        max_conf = 0.0

        if isinstance(faces, dict):
            for face_id, face_data in faces.items():
                if not isinstance(face_data, dict):
                    continue

                facial_area = face_data.get("facial_area", [])
                confidence = face_data.get("confidence", 0.0)

                if confidence > 0.5 and len(facial_area) == 4:
                    face_count += 1
                    bboxes.append([float(x) for x in facial_area])
                    max_conf = max(max_conf, float(confidence))

                    # RetinaFace provides 5 facial landmarks
                    if "landmarks" in face_data:
                        lm = face_data["landmarks"]
                        landmarks.append(
                            {
                                "left_eye": list(lm.get("left_eye", [0, 0])),
                                "right_eye": list(lm.get("right_eye", [0, 0])),
                                "nose": list(lm.get("nose", [0, 0])),
                                "mouth_left": list(lm.get("mouth_left", [0, 0])),
                                "mouth_right": list(lm.get("mouth_right", [0, 0])),
                            }
                        )

        return {
            "face_count": face_count,
            "confidence": max_conf if face_count > 0 else 0.0,
            "bboxes": bboxes,
            "landmarks": landmarks,
        }
