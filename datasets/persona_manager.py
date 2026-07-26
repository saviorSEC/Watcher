#!/usr/bin/env python3
"""
Persona Manager — Test subject management.

Manages reference face images (personae) used as ground truth for
testing facial recognition evasion. Each persona represents one test
subject with multiple reference images from different angles.
"""

import json
import logging
from pathlib import Path
from typing import Optional

import cv2
import numpy as np

log = logging.getLogger("watcher.persona")


class PersonaManager:
    """
    Manages test subject personae.

    Each persona has:
    - Name (identifier)
    - Reference images (for ground truth embedding)
    - Metadata (optional: glasses, skin tone notes, etc.)

    Persona images are stored in datasets/personas/<name>/
    """

    def __init__(self, persona_dir: str = "datasets/personas"):
        self.persona_dir = Path(persona_dir)
        self.persona_dir.mkdir(parents=True, exist_ok=True)
        self._scan_personas()

    def _scan_personas(self):
        """Scan the persona directory and index available personae."""
        self.personae = {}

        for item in self.persona_dir.iterdir():
            if item.is_dir():
                # Directory-based persona: datasets/personas/<name>/*.jpg
                images = sorted(
                    [
                        str(p)
                        for p in item.glob("*")
                        if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".bmp", ".webp")
                    ]
                )
                if images:
                    self.personae[item.name] = images
            elif item.suffix.lower() in (".jpg", ".jpeg", ".png", ".bmp", ".webp"):
                # Single file persona: datasets/personas/<name>.jpg
                name = item.stem
                if name not in self.personae:
                    self.personae[name] = []
                self.personae[name].append(str(item))

        log.info("Found %d persona(s)", len(self.personae))
        for name, images in self.personae.items():
            log.info("  %s: %d image(s)", name, len(images))

    def list_personae(self) -> list[str]:
        """List all available persona names."""
        return sorted(self.personae.keys())

    def get_images(self, persona_name: str) -> list[str]:
        """Get reference image paths for a persona."""
        return self.personae.get(persona_name, [])

    def get_reference_embedding(
        self, persona_name: str, recognizer
    ) -> Optional[np.ndarray]:
        """
        Generate a reference embedding for a persona using the first reference image.

        Args:
            persona_name: Name of the persona
            recognizer: FaceNetRecognizer instance

        Returns:
            Embedding vector or None if face not found
        """
        images = self.get_images(persona_name)
        if not images:
            log.warning("No reference images for persona '%s'", persona_name)
            return None

        img_path = images[0]
        frame = cv2.imread(img_path)
        if frame is None:
            log.warning("Failed to load reference image: %s", img_path)
            return None

        # Detect face
        det_result = recognizer.detect(frame)
        if det_result.get("face_count", 0) == 0:
            log.warning("No face found in reference image for '%s'", persona_name)
            return None

        # Get embedding
        rec_result = recognizer.recognize(frame, det_result.get("bboxes", []))
        embeddings = rec_result.get("embeddings", [])

        if not embeddings:
            log.warning("No embedding extracted for '%s'", persona_name)
            return None

        # Return first embedding
        return np.array(embeddings[0])

    def register_persona(
        self, name: str, image_paths: list[str], metadata: Optional[dict] = None
    ):
        """
        Register a new persona by copying images to the persona directory.

        Args:
            name: Persona name
            image_paths: Paths to reference images
            metadata: Optional metadata dict
        """
        persona_dir = self.persona_dir / name
        persona_dir.mkdir(exist_ok=True)

        for i, src_path in enumerate(image_paths):
            src = Path(src_path)
            if not src.exists():
                log.warning("Image not found: %s", src_path)
                continue

            dst = persona_dir / f"ref_{i:02d}{src.suffix}"
            import shutil

            shutil.copy2(str(src), str(dst))
            log.info("Registered %s → %s", src.name, dst)

        if metadata:
            meta_file = persona_dir / "metadata.json"
            with open(meta_file, "w") as f:
                json.dump(metadata, f, indent=2)

        # Rescan
        self._scan_personas()

    def add_reference_image(self, persona_name: str, image: np.ndarray):
        """
        Add a reference image for an existing persona.

        Args:
            persona_name: Persona name
            image: BGR image (will be saved as JPEG)
        """
        persona_dir = self.persona_dir / persona_name
        persona_dir.mkdir(exist_ok=True)

        # Find next available index
        existing = list(persona_dir.glob("ref_*.jpg"))
        next_idx = len(existing)

        dst = persona_dir / f"ref_{next_idx:02d}.jpg"
        cv2.imwrite(str(dst), image)
        log.info("Added reference image: %s", dst)

        self._scan_personas()

    def __str__(self):
        return f"PersonaManager({len(self.personae)} persona(s))"
