"""
FaceNet/ArcFace Recognizer — Face recognition (embedding-based) backend.
"""

import logging

import numpy as np

log = logging.getLogger("watcher.detectors.facenet")


class FaceNetRecognizer:
    """
    Face recognition using FaceNet or ArcFace models.

    Performs both detection (via MTCNN internally) and recognition (embedding extraction).
    """

    def __init__(self, model_name: str = "facenet", device: str = "cpu"):
        self.name = model_name
        self.model_name = model_name
        self.device = device

        try:
            import torch
            from facenet_pytorch import InceptionResnetV1, MTCNN

            # Load MTCNN for face detection/cropping
            self.mtcnn = MTCNN(
                keep_all=True,
                device=device,
                post_process=True,
                select_largest=False,
            )

            # Load FaceNet or ArcFace embedding model
            if model_name in ("facenet", "InceptionResnetV1"):
                self.embedder = InceptionResnetV1(
                    pretrained="vggface2",
                    classify=False,
                ).eval()
                self.embedding_size = 512
            elif model_name == "arcface":
                # ArcFace uses IR-SE50 or similar
                # For now, use the same InceptionResnet with different weights
                self.embedder = InceptionResnetV1(
                    pretrained="casia-webface",
                    classify=False,
                ).eval()
                self.embedding_size = 512
            else:
                raise ValueError(f"Unknown model: {model_name}")

            self.embedder = self.embedder.to(device)
            log.info("FaceNet recognizer '%s' loaded on %s", model_name, device)

        except ImportError:
            log.error(
                "facenet_pytorch not installed. Run: pip install facenet-pytorch"
            )
            raise
        except Exception as e:
            log.error("Failed to load FaceNet model '%s': %s", model_name, e)
            raise

    def detect(self, frame: np.ndarray) -> dict:
        """
        Detect faces using internal MTCNN.

        Args:
            frame: BGR image as numpy array

        Returns:
            dict with face count, confidence, bboxes
        """
        try:
            import cv2

            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            boxes, probs = self.mtcnn.detect(rgb)
        except Exception as e:
            log.warning("FaceNet detection failed: %s", e)
            return {"face_count": 0, "confidence": 0.0, "bboxes": [], "landmarks": []}

        face_count = 0
        bboxes = []
        max_conf = 0.0

        if boxes is not None and probs is not None:
            for box, prob in zip(boxes, probs):
                if prob is not None and prob > 0.5:
                    face_count += 1
                    bboxes.append([float(x) for x in box])
                    max_conf = max(max_conf, float(prob))

        return {
            "face_count": face_count,
            "confidence": max_conf if face_count > 0 else 0.0,
            "bboxes": bboxes,
            "landmarks": [],
        }

    def recognize(self, frame: np.ndarray, bboxes: list) -> dict:
        """
        Extract face embeddings for recognition.

        Args:
            frame: BGR image as numpy array
            bboxes: List of bounding boxes [x1, y1, x2, y2]

        Returns:
            dict with embeddings and identities
        """
        import torch

        if not bboxes:
            return {"embeddings": [], "identities": []}

        try:
            import cv2

            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            # Get aligned face crops from MTCNN
            faces = self.mtcnn(rgb)

            if faces is None:
                return {"embeddings": [], "identities": []}

            if faces.dim() == 3:
                faces = faces.unsqueeze(0)

            # Generate embeddings
            with torch.no_grad():
                embeddings = self.embedder(faces.to(self.device))

            embeddings_np = embeddings.cpu().numpy().tolist()

            return {
                "embeddings": embeddings_np,
                "identities": [],  # Would require a gallery database for identification
            }

        except Exception as e:
            log.warning("FaceNet recognition failed: %s", e)
            return {"embeddings": [], "identities": []}
