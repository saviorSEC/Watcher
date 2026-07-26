"""
Recognition Pipeline — Orchestrates multiple detector backends across a single frame.
"""

import logging
import time

import numpy as np

log = logging.getLogger("watcher.pipeline")


class RecognitionPipeline:
    """
    Orchestrates face detection and recognition across multiple backends.

    Each frame is processed through every registered detector in sequence.
    Results are aggregated per detector and returned as structured data.
    """

    def __init__(self, detectors: list):
        self.detectors = detectors
        log.info(
            "Pipeline initialized with %d detectors: %s",
            len(detectors),
            [d.name for d in detectors],
        )

    def process(self, frame: np.ndarray) -> dict:
        """
        Process a single frame through all detector backends.

        Args:
            frame: BGR image as numpy array

        Returns:
            dict with per-detector results
        """
        result = {
            "timestamp": time.time(),
            "frame_shape": list(frame.shape),
            "detections": {},
            "faces": [],
        }

        for detector in self.detectors:
            try:
                det_start = time.time()
                det_result = detector.detect(frame)
                det_time = time.time() - det_start

                result["detections"][detector.name] = {
                    "face_count": det_result.get("face_count", 0),
                    "confidence": det_result.get("confidence", 0.0),
                    "bboxes": det_result.get("bboxes", []),
                    "landmarks": det_result.get("landmarks", []),
                    "inference_time_ms": round(det_time * 1000, 2),
                }

                # If recognizer, also get embeddings
                if hasattr(detector, "recognize") and det_result.get("face_count", 0) > 0:
                    rec_result = detector.recognize(frame, det_result.get("bboxes", []))
                    result["detections"][detector.name]["embeddings"] = rec_result.get(
                        "embeddings", []
                    )
                    result["detections"][detector.name]["identities"] = rec_result.get(
                        "identities", []
                    )

            except Exception as e:
                log.error("Detector '%s' failed: %s", detector.name, e)
                result["detections"][detector.name] = {
                    "error": str(e),
                    "face_count": 0,
                    "confidence": 0.0,
                }

        return result

    def aggregate_results(self, trials: list[dict]) -> dict:
        """
        Aggregate results across multiple trials.

        Computes per-detector: detection rate, mean confidence, etc.
        """
        if not trials:
            return {}

        detectors_seen = set()
        for trial in trials:
            detectors_seen.update(trial.get("detections", {}).keys())

        aggregate = {"detectors": {}, "total_trials": len(trials)}

        for det_name in sorted(detectors_seen):
            det_trials = []
            for trial in trials:
                if det_name in trial.get("detections", {}):
                    det_trials.append(trial["detections"][det_name])

            if not det_trials:
                continue

            face_counts = [t.get("face_count", 0) for t in det_trials]
            confidences = [
                t.get("confidence", 0.0)
                for t in det_trials
                if t.get("face_count", 0) > 0 and t.get("confidence", 0.0) > 0
            ]
            inference_times = [
                t.get("inference_time_ms", 0) for t in det_trials if t.get("inference_time_ms", 0) > 0
            ]

            detections = sum(1 for c in face_counts if c > 0)

            aggregate["detectors"][det_name] = {
                "detection_rate": round(detections / len(det_trials), 4) if det_trials else 0.0,
                "total_detections": detections,
                "total_trials": len(det_trials),
                "mean_confidence": round(float(np.mean(confidences)), 4) if confidences else 0.0,
                "max_confidence": round(float(np.max(confidences)), 4) if confidences else 0.0,
                "min_confidence": round(float(np.min(confidences)), 4) if confidences else 0.0,
                "mean_inference_time_ms": round(float(np.mean(inference_times)), 2)
                if inference_times
                else 0.0,
                "faces_per_frame_mean": round(float(np.mean(face_counts)), 2),
                "faces_per_frame_std": round(float(np.std(face_counts)), 2),
            }

        return aggregate

    def compare_baseline(self, baseline: dict, test: dict) -> dict:
        """
        Compare test results against a baseline (no-pattern) run.

        Args:
            baseline: aggregated results from baseline run
            test: aggregated results from adversarial pattern run

        Returns:
            Comparison dict with evasion rates per detector
        """
        comparison = {
            "evasion_rates": {},
            "confidence_suppression": {},
        }

        for det_name in test.get("detectors", {}):
            b_det = baseline.get("detectors", {}).get(det_name, {})
            t_det = test["detectors"][det_name]

            b_dr = b_det.get("detection_rate", 1.0)
            t_dr = t_det.get("detection_rate", 0.0)

            if b_dr > 0:
                evasion = round(1.0 - (t_dr / b_dr), 4)
            else:
                evasion = 0.0

            b_conf = b_det.get("mean_confidence", 1.0)
            t_conf = t_det.get("mean_confidence", 0.0)

            if b_conf > 0:
                suppression = round(1.0 - (t_conf / b_conf), 4)
            else:
                suppression = 0.0

            comparison["evasion_rates"][det_name] = {
                "evasion_rate": evasion,
                "baseline_dr": b_dr,
                "test_dr": t_dr,
                "grade": self._grade(evasion),
            }

            comparison["confidence_suppression"][det_name] = {
                "suppression_rate": suppression,
                "baseline_conf": b_conf,
                "test_conf": t_conf,
            }

        return comparison

    @staticmethod
    def _grade(evasion_rate: float) -> str:
        if evasion_rate > 0.95:
            return "S"
        elif evasion_rate > 0.80:
            return "A"
        elif evasion_rate > 0.60:
            return "B"
        elif evasion_rate > 0.40:
            return "C"
        elif evasion_rate > 0.20:
            return "D"
        else:
            return "F"
