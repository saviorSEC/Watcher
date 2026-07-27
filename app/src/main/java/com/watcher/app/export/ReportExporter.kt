package com.watcher.app.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.watcher.app.models.TestComparison
import com.watcher.app.models.TestRun
import com.watcher.app.models.TestRunSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles export of test results to JSON and HTML formats.
 * Files are saved in the device's Documents/Watcher/ directory.
 */
class ReportExporter(private val context: Context) {

    companion object {
        private const val TAG = "Watcher.Exporter"
        private const val DIR_NAME = "Watcher"
    }

    private val baseDir: File
        get() {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docsDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Export a single test run as JSON.
     * @return URI to the saved file
     */
    fun exportJson(run: TestRun): Uri? {
        return try {
            val json = TestRunSerializer.toJson(run)
            val filename = buildFilename(run, "json")
            val file = File(baseDir, filename)
            file.writeText(json)
            Log.i(TAG, "Exported: ${file.absolutePath}")
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}")
            null
        }
    }

    /**
     * Export a comparison (baseline vs pattern test) as JSON.
     */
    fun exportComparisonJson(comparison: TestComparison): Uri? {
        return try {
            val json = TestRunSerializer.toJson(comparison)
            val filename = "comparison_${comparison.testId}_${timestamp()}.json"
            val file = File(baseDir, filename)
            file.writeText(json)
            Log.i(TAG, "Comparison exported: ${file.absolutePath}")
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Comparison export failed: ${e.message}")
            null
        }
    }

    /**
     * Export a comparison as an HTML report with visual tables and grading.
     */
    fun exportHtmlReport(comparison: TestComparison): Uri? {
        return try {
            val html = buildHtmlReport(comparison)
            val filename = "report_${comparison.testId}_${timestamp()}.html"
            val file = File(baseDir, filename)
            file.writeText(html)
            Log.i(TAG, "HTML report exported: ${file.absolutePath}")
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "HTML report failed: ${e.message}")
            null
        }
    }

    fun listExports(): List<File> {
        return baseDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun buildFilename(run: TestRun, ext: String): String {
        val type = if (run.isBaseline) "baseline" else "test"
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(run.timestamp))
        return "watcher_${type}_${run.personaName}_${ts}.$ext"
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun buildHtmlReport(comparison: TestComparison): String {
        val ev = comparison.overall
        val textColor = "#C9D1D9"
        val accentColor = "#58A6FF"
        val borderColor = "#30363D"
        val surfaceColor = "#161B22"
        val bg = "#0D1117"

        val gradeStyle = when (ev.grade) {
            "S" -> "color: #FFD700; font-weight: bold;"
            "A" -> "color: #00CC00; font-weight: bold;"
            "B" -> "color: #66CC00;"
            "C" -> "color: #CCCC00;"
            "D" -> "color: #CC6600;"
            "F" -> "color: #CC0000;"
            else -> ""
        }

        val detectorRows = comparison.perDetector.map { (name, m) ->
            val gs = when (m.grade) {
                "S" -> "color: #FFD700; font-weight: bold;"
                "A" -> "color: #00CC00; font-weight: bold;"
                "B" -> "color: #66CC00;"
                "C" -> "color: #CCCC00;"
                "D" -> "color: #CC6600;"
                "F" -> "color: #CC0000;"
                else -> ""
            }
            """
            <tr>
                <td>$name</td>
                <td>${"%.3f".format(m.baselineDr)}</td>
                <td>${"%.3f".format(m.testDr)}</td>
                <td>${"%.3f".format(m.evasionRate)}</td>
                <td style="$gs">${m.grade}</td>
                <td>${"%.3f".format(m.confidenceSuppression)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        return """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Watcher - Adversarial Pattern Test Report</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Segoe UI', -apple-system, sans-serif; background: $bg; color: $textColor; padding: 20px; }
h1 { color: $accentColor; font-size: 24px; margin-bottom: 20px; }
h2 { color: $accentColor; font-size: 18px; margin: 20px 0 10px; }
table { width: 100%; border-collapse: collapse; margin: 10px 0 20px; font-size: 14px; }
th, td { border: 1px solid $borderColor; padding: 10px; text-align: left; }
th { background: $surfaceColor; color: $accentColor; font-weight: 600; }
tr:nth-child(even) { background: $surfaceColor; }
tr:nth-child(odd) { background: $bg; }
.card { background: $surfaceColor; border-radius: 8px; padding: 16px; margin: 10px 0; }
.grade { font-size: 48px; text-align: center; padding: 20px; ${gradeStyle} }
.metric-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 10px 0; }
.metric { background: $bg; padding: 12px; border-radius: 6px; border: 1px solid $borderColor; }
.metric-label { font-size: 12px; color: #8B949E; }
.metric-value { font-size: 20px; font-weight: bold; margin-top: 4px; }
.footer { margin-top: 30px; padding-top: 10px; border-top: 1px solid $borderColor; font-size: 12px; color: #8B949E; text-align: center; }
@media (max-width: 600px) { .metric-grid { grid-template-columns: 1fr; } }
</style></head><body>
<h1>Watcher - Pattern Test Report</h1>

<div class="card">
    <h2>Overall Grade</h2>
    <div class="grade">${ev.grade}</div>
    <div class="metric-grid">
        <div class="metric"><div class="metric-label">Evasion Rate</div>
            <div class="metric-value">${"%.1f".format(ev.evasionRate * 100)}%</div></div>
        <div class="metric"><div class="metric-label">Confidence Suppression</div>
            <div class="metric-value">${"%.1f".format(ev.confidenceSuppression * 100)}%</div></div>
        <div class="metric"><div class="metric-label">Baseline Detection Rate</div>
            <div class="metric-value">${"%.1f".format(ev.baselineDr * 100)}%</div></div>
        <div class="metric"><div class="metric-label">Test Detection Rate</div>
            <div class="metric-value">${"%.1f".format(ev.testDr * 100)}%</div></div>
    </div>
</div>

<div class="card">
    <h2>Per-Detector Results</h2>
    <table>
        <tr><th>Detector</th><th>Baseline DR</th><th>Test DR</th><th>Evasion</th><th>Grade</th><th>Conf Sup.</th></tr>
        $detectorRows
    </table>
</div>

<div class="card">
    <h2>Grading Scale</h2>
    <table>
        <tr><th>Grade</th><th>Evasion</th><th>Meaning</th></tr>
        <tr><td style="color:#FFD700;font-weight:bold">S</td><td>> 95%</td><td>Model-blind — defeats all detectors</td></tr>
        <tr><td style="color:#00CC00;font-weight:bold">A</td><td>> 80%</td><td>Strong evasion</td></tr>
        <tr><td style="color:#66CC00">B</td><td>> 60%</td><td>Moderate evasion</td></tr>
        <tr><td style="color:#CCCC00">C</td><td>> 40%</td><td>Weak evasion</td></tr>
        <tr><td style="color:#CC6600">D</td><td>> 20%</td><td>Minimal evasion</td></tr>
        <tr><td style="color:#CC0000">F</td><td>< 20%</td><td>Ineffective</td></tr>
    </table>
</div>

<div class="footer">
    Generated by <strong>Watcher</strong> — github.com/saviorSEC/Watcher<br>
    ${timestamp()}
</div>
</body></html>
        """.trimIndent()
    }
}
