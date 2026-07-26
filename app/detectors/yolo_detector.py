"""
YOLO Detector — Ultralytics YOLO-based face detection backend.
"""

import logging

import numpy as np

log = logging.getLogger("watcher.detectors.yolo")


class YOLODetector:
    """Face detection using Ultralytics YOLO models (YOLOv8, YOLO11n, etc.)."""

    def __init__(self, model_name: str = "yolo11n", device: str = "cpu"):
        self.model_name = model_name
        self.device = device
        self.name = model_name

        try:
            from ultralytics import YOLO

            # Map short names to Ultralytics model identifiers
            model_map = {
                "yolo11n": "yolo11n.pt",
                "yolo11s": "yolo11s.pt",
                "yolov8n": "yolov8n.pt",
                "yolov8s": "yolov8s.pt",
                "yolo": "yolo11n.pt",
            }

            model_file = model_map.get(model_name, model_name)
            self.model = YOLO(model_file)
            log.info("YOLO detector '%s' loaded on %s", model_name, device)

            # Class indices for COCO person/face
            # YOLO doesn't have a dedicated "face" class, so we detect
            # "person" (class 0) and crop the upper region for face analysis
            self.person_class = 0

        except ImportError:
            log.error(
                "ultralytics not installed. Run: pip install ultralytics"
            )
            raise
        except Exception as e:
            log.error("Failed to load YOLO model '%s': %s", model_name, e)
            raise

    def detect(self, frame: np.ndarray) -> dict:
        """
        Detect faces in frame using YOLO person detection + face region heuristic.

        In a production version, this would use a dedicated face detection model
        or a YOLO variant fine-tuned on face detection (e.g., YOLOv8-face).

        Args:
            frame: BGR image as numpy array

        Returns:
            dict with face count, confidence, bboxes, landmarks
        """
        results = self.model(frame, verbose=False)

        detections = []
        max_conf = 0.0

        for r in results:
            if r.boxes is None:
                continue

            for box, conf, cls_id in zip(
                r.boxes.xyxy.cpu().numpy(),
                r.boxes.conf.cpu().numpy(),
                r.boxes.cls.cpu().numpy().astype(int),
            ):
                # Detect persons, then approximate face region from upper torso
                if cls_id == self.person_class:
                    x1, y1, x2, y2 = box
                    person_h = y2 - y1
                    person_w = x2 - x1

                    # Face region ≈ upper 30% of person bounding box
                    face_y1 = y1
                    face_y2 = y1 + person_h * 0.30
                    face_x1 = x1 + person_w * 0.15
                    face_x2 = x2 - person_w * 0.15

                    detection = {
                        "bbox": [float(face_x1), float(face_y1), float(face_x2), float(face_y2)],
                        "confidence": float(conf),
                        "person_bbox": [float(x1), float(y1), float(x2), float(y2)],
                    }
                    detections.append(detection)
                    max_conf = max(max_conf, float(conf))

        return {
            "face_count": len(detections),
            "confidence": max_conf,
            "bboxes": [d["bbox"] for d in detections],
            "landmarks": [],
            "raw_detections": detections,
        }
