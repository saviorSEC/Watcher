#!/usr/bin/env python3
"""
Watcher Result Evaluator — Compare baseline and adversarial test results.

Generates:
- Detection rate comparison (bar chart)
- Evasion rate per detector (table + chart)
- Confidence suppression analysis
- Summary report (JSON + HTML)

Usage:
    # Compare baseline vs test
    python evaluate_results.py --baseline results/baseline --test results/test_shirt_v1

    # With output
    python evaluate_results.py --baseline results/baseline \\
        --test results/test_shirt_v1 \\
        --output results/report_shirt_v1

    # Plot only
    python evaluate_results.py --baseline results/baseline \\
        --test results/test_shirt_v1 --plot
"""

import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.pipeline import RecognitionPipeline


def load_results(path: str) -> dict:
    """Load results from a test run."""
    results_file = Path(path) / "results.json"
    if not results_file.exists():
        print(f"ERROR: Results file not found: {results_file}")
        sys.exit(1)

    with open(results_file) as f:
        return json.load(f)


def print_comparison(baseline: dict, test: dict, output_dir: str = None):
    """Print and optionally save comparison results."""
    pipeline = RecognitionPipeline([])  # Just for the compare method

    # Compare
    bl_agg = baseline.get("aggregate", {})
    test_agg = test.get("aggregate", {})

    comparison = pipeline.compare_baseline(bl_agg, test_agg)

    print("\n" + "=" * 70)
    print("WATCHER — RESULTS COMPARISON")
    print("=" * 70)
    print(f"Baseline: {baseline.get('session_info', {}).get('pattern', 'none')}")
    print(f"Test:     {test.get('session_info', {}).get('pattern', 'unknown')}")
    print(f"Trials:   baseline={bl_agg.get('total_trials', 'N/A')}  test={test_agg.get('total_trials', 'N/A')}")
    print("=" * 70)

    print("\nDETECTION EVASION RATES:")
    print("-" * 70)
    print(f"{'Detector':<20} {'Baseline DR':<15} {'Test DR':<15} {'Evasion':<12} {'Grade':<8}")
    print("-" * 70)

    for det_name, ev_data in comparison.get("evasion_rates", {}).items():
        print(
            f"{det_name:<20} {ev_data['baseline_dr']:<15.3f} {ev_data['test_dr']:<15.3f} "
            f"{ev_data['evasion_rate']:<12.3f} {ev_data['grade']:<8}"
        )

    print("\nCONFIDENCE SUPPRESSION:")
    print("-" * 70)
    print(f"{'Detector':<20} {'Baseline Conf':<15} {'Test Conf':<15} {'Suppression':<15}")
    print("-" * 70)

    for det_name, cs_data in comparison.get("confidence_suppression", {}).items():
        print(
            f"{det_name:<20} {cs_data['baseline_conf']:<15.3f} {cs_data['test_conf']:<15.3f} "
            f"{cs_data['suppression_rate']:<15.3f}"
        )

    # Save if output dir provided
    if output_dir:
        out = Path(output_dir)
        out.mkdir(parents=True, exist_ok=True)

        report = {
            "baseline_info": baseline.get("session_info", {}),
            "test_info": test.get("session_info", {}),
            "comparison": comparison,
            "grade_summary": {
                det: ev["grade"]
                for det, ev in comparison.get("evasion_rates", {}).items()
            },
        }

        report_file = out / "comparison_report.json"
        with open(report_file, "w") as f:
            json.dump(report, f, indent=2)

        print(f"\nReport saved to: {report_file}")

        # Generate simple HTML report
        html = _generate_html_report(report)
        html_file = out / "report.html"
        with open(html_file, "w") as f:
            f.write(html)
        print(f"HTML report: {html_file}")

    return comparison


def _generate_html_report(report: dict) -> str:
    """Generate a simple HTML summary report."""
    styles = {
        "S": "color: #FFD700; font-weight: bold;",  # Gold
        "A": "color: #00CC00; font-weight: bold;",  # Green
        "B": "color: #66CC00;",  # Light green
        "C": "color: #CCCC00;",  # Yellow
        "D": "color: #CC6600;",  # Orange
        "F": "color: #CC0000;",  # Red
    }

    rows = ""
    for det, ev in report.get("comparison", {}).get("evasion_rates", {}).items():
        grade_style = styles.get(ev["grade"], "")
        rows += f"""
        <tr>
            <td>{det}</td>
            <td>{ev['baseline_dr']:.3f}</td>
            <td>{ev['test_dr']:.3f}</td>
            <td>{ev['evasion_rate']:.3f}</td>
            <td style="{grade_style}">{ev['grade']}</td>
        </tr>"""

    cs_rows = ""
    for det, cs in report.get("comparison", {}).get("confidence_suppression", {}).items():
        cs_rows += f"""
        <tr>
            <td>{det}</td>
            <td>{cs['baseline_conf']:.3f}</td>
            <td>{cs['test_conf']:.3f}</td>
            <td>{cs['suppression_rate']:.3f}</td>
        </tr>"""

    bl_info = report.get("baseline_info", {})
    test_info = report.get("test_info", {})

    html = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8">
<title>Watcher — Test Report</title>
<style>
body {{ font-family: 'Segoe UI', sans-serif; margin: 40px; background: #0d1117; color: #c9d1d9; }}
h1, h2 {{ color: #58a6ff; }}
table {{ border-collapse: collapse; width: 100%; margin: 20px 0; }}
th, td {{ border: 1px solid #30363d; padding: 12px; text-align: left; }}
th {{ background: #161b22; color: #58a6ff; }}
tr:nth-child(even) {{ background: #161b22; }}
tr:nth-child(odd) {{ background: #0d1117; }}
.s-{{ color: #FFD700; font-weight: bold; }}
.a-{{ color: #00CC00; font-weight: bold; }}
.summary {{ background: #161b22; padding: 20px; border-radius: 8px; }}
</style></head><body>
<h1>👁️ Watcher — Adversarial Pattern Test Report</h1>
<div class="summary">
<h2>Test Info</h2>
<table>
<tr><th>Property</th><th>Baseline</th><th>Test</th></tr>
<tr><td>Persona</td><td>{bl_info.get('persona', 'N/A')}</td><td>{test_info.get('persona', 'N/A')}</td></tr>
<tr><td>Pattern</td><td>None</td><td>{test_info.get('pattern', 'N/A')}</td></tr>
<tr><td>Camera</td><td>{bl_info.get('camera', 'N/A')}</td><td>{test_info.get('camera', 'N/A')}</td></tr>
<tr><td>Trials</td><td>{bl_info.get('trials', 'N/A')}</td><td>{test_info.get('trials', 'N/A')}</td></tr>
</table>
</div>

<h2>Evasion Rates</h2>
<table>
<tr><th>Detector</th><th>Baseline DR</th><th>Test DR</th><th>Evasion Rate</th><th>Grade</th></tr>
{rows}
</table>

<h2>Confidence Suppression</h2>
<table>
<tr><th>Detector</th><th>Baseline Conf</th><th>Test Conf</th><th>Suppression</th></tr>
{cs_rows}
</table>

<h2>Grading Scale</h2>
<table>
<tr><th>Grade</th><th>Evasion Rate</th><th>Meaning</th></tr>
<tr><td style="{styles['S']}">S</td><td>> 0.95</td><td>Model-blind — pattern defeats all tested detectors</td></tr>
<tr><td style="{styles['A']}">A</td><td>> 0.80</td><td>Strong evasion</td></tr>
<tr><td style="{styles['B']}">B</td><td>> 0.60</td><td>Moderate evasion</td></tr>
<tr><td style="{styles['C']}">C</td><td>> 0.40</td><td>Weak evasion</td></tr>
<tr><td style="{styles['D']}">D</td><td>> 0.20</td><td>Minimal evasion</td></tr>
<tr><td style="{styles['F']}">F</td><td>< 0.20</td><td>Ineffective</td></tr>
</table>
<p><em>Generated by Watcher — github.com/saviorSEC/Watcher</em></p>
</body></html>"""

    return html


def parse_args():
    parser = argparse.ArgumentParser(
        description="Watcher — Compare baseline and adversarial test results"
    )
    parser.add_argument("--baseline", required=True, help="Baseline results directory")
    parser.add_argument("--test", required=True, help="Test results directory")
    parser.add_argument("--output", default=None, help="Output directory for report")
    parser.add_argument("--plot", action="store_true", help="Generate plots")
    return parser.parse_args()


def main():
    args = parse_args()

    baseline = load_results(args.baseline)
    test = load_results(args.test)

    print_comparison(baseline, test, output_dir=args.output)


if __name__ == "__main__":
    main()
