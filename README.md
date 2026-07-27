# Watcher

**Open this on your phone:** https://saviorsec.github.io/Watcher/

Browser-based facial detection testing for anti-AI adversarial garments.

## How to Test a Shirt

1. Open the link above on your phone in Chrome
2. Grant camera permission
3. Tap **Quick Scan** -- see real-time face detection with green boxes
4. Tap **Start Test** -- enters structured test mode
5. Tap **Run Baseline** -- subject stands in front of camera without the garment (50 trials)
6. Subject puts on the adversarial shirt/garment
7. Tap **Run Pattern Test** -- same subject, same position, with garment
8. Get your grade (S through F) and evasion rate

## Why This Works

- Runs entirely in your phone's browser -- no app install, no laptop needed
- Uses TensorFlow.js with MediaPipe face detection (same tech as real surveillance systems)
- All processing happens on your device -- nothing is uploaded
- Works offline after first load

## The Grading System

| Grade | Evasion | Meaning |
|-------|---------|---------|
| S     | > 95%   | Model-blind |
| A     | > 80%   | Strong evasion |
| B     | > 60%   | Moderate |
| C     | > 40%   | Weak |
| D     | > 20%   | Minimal |
| F     | < 20%   | Ineffective |

## How Anti-FR Garments Work

The camera points at the person wearing the garment. The pattern on the shirt/scarf/headband interferes with how the AI model detects facial features -- confusing landmarks, reducing confidence scores, or hiding the face entirely from the model while remaining visible to the human eye.
