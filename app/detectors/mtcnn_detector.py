"""
MTCNN Detector — Lightweight face detection using MTCNN.
"""

import logging

import numpy as np

log = logging.getLogger("watcher.detectors.mtcnn")


class MTCNNDetector:
    """Face detection using MTCNN (Multi-Task Cascaded Convolutional Networks)."""

    def __init__(self, device: str = "cpu"):
        self.name = "mtcnn"
        self.device = device

        try:
            from facenet_pytorch import MTCNN as MTCNNModel

            self.detector = MTCNNModel(
                keep_all=True,
                device=device,
                post_process=False,
            )
            log.info("MTCNN detector loaded on %s", device)

        except ImportError:
            log.error(
                "facenet_pytorch not installed. Run: pip install facenet-pytorch"
            )
            raise
        except Exception as e:
            log.error("Failed to load MTCNN: %s", e)
            raise

    def detect(self, frame: np.ndarray) -> dict:
        """
        Detect faces in frame using MTCNN.

        Args:
            frame: BGR image as numpy array

        Returns:
            dict with face count, confidence, bboxes, landmarks
        """
        try:
            boxes, probs, landmarks = self.detector.detect(frame, landmarks=True)
        except Exception as e:
            log.warning("MTCNN detection failed: %s", e)
            return {"face_count": 0, "confidence": 0.0, "bboxes": [], "landmarks": []}

        face_count = 0
        bboxes = []
        lm_list = []
        max_conf = 0.0

        if boxes is not None and probs is not None:
            for box, prob in zip(boxes, probs):
                if prob is not None and prob > 0.5:
                    face_count += 1
                    bboxes.append([float(x) for x in box])
                    max_conf = max(max_conf, float(prob))

            if landmarks is not None:
                for lm in landmarks:
                    if lm is not None:
                        lm_list.append([[float(p) for p in point] for point in lm])

        return {
            "face_count": face_count,
            "confidence": max_conf if face_count > 0 else 0.0,
            "bboxes": bboxes,
            "landmarks": lm_list,
        }
