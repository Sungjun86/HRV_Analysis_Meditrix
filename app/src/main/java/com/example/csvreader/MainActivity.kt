package com.example.csvreader

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.PI
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var resultTextView: TextView
    private lateinit var panTompkinsTextView: TextView
    private lateinit var rawChart: LineChart
    private lateinit var processedChart: LineChart

    private val openCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { readCsvFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.tvResult)
        panTompkinsTextView = findViewById(R.id.tvPanTompkins)
        rawChart = findViewById(R.id.lineChartRaw)
        processedChart = findViewById(R.id.lineChartProcessed)

        setupChart(rawChart, getString(R.string.raw_chart_description))
        setupChart(processedChart, getString(R.string.processed_chart_description))

        val selectButton: Button = findViewById(R.id.btnSelectCsv)
        selectButton.setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel"))
        }
    }

    private fun setupChart(chart: LineChart, descriptionText: String) {
        chart.setNoDataText(getString(R.string.default_guide))
        chart.axisRight.isEnabled = false
        chart.description = Description().apply { text = descriptionText }
        chart.legend.isEnabled = false
    }

    private fun readCsvFile(uri: Uri) {
        val textContent = StringBuilder()
        val ecgValues = mutableListOf<Float>()

        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                var previewCount = 0

                while (reader.readLine().also { line = it } != null) {
                    val row = line ?: continue
                    val columns = row.split(",")

                    if (previewCount < 150) {
                        textContent.append(columns.joinToString(" | ")).append("\n")
                        previewCount++
                    }

                    extractNumericValue(columns)?.let { ecgValues.add(it) }
                }
            }

            resultTextView.text = if (textContent.isNotEmpty()) {
                textContent.toString()
            } else {
                getString(R.string.empty_csv)
            }

            if (ecgValues.isEmpty()) {
                panTompkinsTextView.text = getString(R.string.no_numeric_data)
                rawChart.clear()
                processedChart.clear()
                rawChart.invalidate()
                processedChart.invalidate()
                return
            }

            renderChart(
                chart = rawChart,
                entries = ecgValues.mapIndexed { index, value -> Entry(index.toFloat(), value) },
                lineColor = Color.parseColor("#1E88E5"),
                label = "Raw ECG"
            )

            val panTompkinsResult = runPanTompkins(ecgValues, samplingRateHz = 500)

            renderChart(
                chart = processedChart,
                entries = panTompkinsResult.integratedSignal.mapIndexed { index, value -> Entry(index.toFloat(), value) },
                lineColor = Color.parseColor("#D81B60"),
                label = "Pan-Tompkins Integrated"
            )

            panTompkinsTextView.text = buildPanTompkinsSummary(panTompkinsResult)
        } ?: run {
            resultTextView.text = getString(R.string.cannot_open_file)
            panTompkinsTextView.text = getString(R.string.cannot_open_file)
            rawChart.clear()
            processedChart.clear()
            rawChart.invalidate()
            processedChart.invalidate()
        }
    }

    private fun extractNumericValue(columns: List<String>): Float? {
        return columns.firstNotNullOfOrNull { cell ->
            cell.trim().toFloatOrNull()
        }
    }

    private fun renderChart(chart: LineChart, entries: List<Entry>, lineColor: Int, label: String) {
        if (entries.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        val dataSet = LineDataSet(entries, label).apply {
            color = lineColor
            valueTextColor = Color.parseColor("#111111")
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    private fun runPanTompkins(ecg: List<Float>, samplingRateHz: Int): PanTompkinsResult {
        if (ecg.size < samplingRateHz / 2) {
            return PanTompkinsResult(
                integratedSignal = emptyList(),
                peakIndices = emptyList(),
                threshold = 0f,
                estimatedHeartRate = 0,
                samplingRateHz = samplingRateHz
            )
        }

        // Pre-processing (Wikipedia Pan-Tompkins flow):
        // 1) Band-pass (5~15 Hz) to suppress baseline wander + high-frequency noise
        val lowPassed = lowPassFilter(ecg, cutoffHz = 15.0, samplingRateHz = samplingRateHz)
        val bandPassed = highPassFilter(lowPassed, cutoffHz = 5.0, samplingRateHz = samplingRateHz)

        // 2) Derivative: slope emphasis (QRS has steep slopes)
        val derivative = derivativeFilter(bandPassed, samplingRateHz)

        // 3) Squaring: make all values positive and emphasize large slopes
        val squared = derivative.map { it * it }

        // 4) Moving-window integration: ~150 ms window
        val windowSize = (0.15 * samplingRateHz).roundToInt().coerceAtLeast(1)
        val integrated = movingAverage(squared, windowSize)

        // 5) Adaptive thresholding (SPKI/NPKI style)
        val peakCandidates = findLocalPeaks(integrated)
        val initialSegment = integrated.take((2.0 * samplingRateHz).roundToInt().coerceAtMost(integrated.size))
        var spki = (initialSegment.maxOrNull() ?: 0f) * 0.25f
        var npki = initialSegment.average().toFloat() * 0.5f
        var thresholdI1 = npki + 0.25f * (spki - npki)

        val refractorySamples = (0.2 * samplingRateHz).roundToInt()
        val detectedPeaks = mutableListOf<Int>()
        var lastPeakIndex = -refractorySamples

        for (idx in peakCandidates) {
            val peakValue = integrated[idx]
            val outsideRefractory = idx - lastPeakIndex >= refractorySamples

            if (peakValue >= thresholdI1 && outsideRefractory) {
                detectedPeaks.add(idx)
                spki = 0.125f * peakValue + 0.875f * spki
                lastPeakIndex = idx
            } else {
                npki = 0.125f * peakValue + 0.875f * npki
            }

            thresholdI1 = npki + 0.25f * (spki - npki)
        }

        val rrIntervals = detectedPeaks.zipWithNext { a, b -> b - a }
        val avgRr = rrIntervals.averageOrNull() ?: 0.0
        val estimatedHeartRate = if (avgRr > 0) {
            (60.0 * samplingRateHz / avgRr).roundToInt()
        } else {
            0
        }

        return PanTompkinsResult(
            integratedSignal = integrated,
            peakIndices = detectedPeaks,
            threshold = thresholdI1,
            estimatedHeartRate = estimatedHeartRate,
            samplingRateHz = samplingRateHz
        )
    }

    private fun lowPassFilter(signal: List<Float>, cutoffHz: Double, samplingRateHz: Int): List<Float> {
        val dt = 1.0 / samplingRateHz
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = (dt / (rc + dt)).toFloat()

        val out = MutableList(signal.size) { 0f }
        if (signal.isEmpty()) return out
        out[0] = signal[0]
        for (i in 1 until signal.size) {
            out[i] = out[i - 1] + alpha * (signal[i] - out[i - 1])
        }
        return out
    }

    private fun highPassFilter(signal: List<Float>, cutoffHz: Double, samplingRateHz: Int): List<Float> {
        val dt = 1.0 / samplingRateHz
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = (rc / (rc + dt)).toFloat()

        val out = MutableList(signal.size) { 0f }
        if (signal.isEmpty()) return out
        out[0] = signal[0]
        for (i in 1 until signal.size) {
            out[i] = alpha * (out[i - 1] + signal[i] - signal[i - 1])
        }
        return out
    }

    private fun derivativeFilter(signal: List<Float>, samplingRateHz: Int): List<Float> {
        val out = MutableList(signal.size) { 0f }
        if (signal.size < 5) return out

        val t = 1f / samplingRateHz
        for (i in 4 until signal.size) {
            // Causal form of Pan-Tompkins derivative approximation
            out[i] = (2 * signal[i] + signal[i - 1] - signal[i - 3] - 2 * signal[i - 4]) / (8f * t)
        }
        return out
    }

    private fun movingAverage(signal: List<Float>, windowSize: Int): List<Float> {
        val out = MutableList(signal.size) { 0f }
        if (signal.isEmpty()) return out

        var runningSum = 0f
        for (i in signal.indices) {
            runningSum += signal[i]
            if (i >= windowSize) {
                runningSum -= signal[i - windowSize]
            }
            val divisor = if (i + 1 < windowSize) i + 1 else windowSize
            out[i] = runningSum / divisor
        }
        return out
    }

    private fun findLocalPeaks(signal: List<Float>): List<Int> {
        if (signal.size < 3) return emptyList()
        val peaks = mutableListOf<Int>()
        for (i in 1 until signal.lastIndex) {
            if (signal[i] > signal[i - 1] && signal[i] >= signal[i + 1]) {
                peaks.add(i)
            }
        }
        return peaks
    }

    private fun buildPanTompkinsSummary(result: PanTompkinsResult): String {
        val peaksPreview = if (result.peakIndices.isEmpty()) {
            getString(R.string.no_detected_peak)
        } else {
            result.peakIndices.take(20).joinToString(", ")
        }

        return """
            [Pan & Tompkins 결과]
            - Sampling Rate: ${result.samplingRateHz} Hz
            - Pre-Processing: Band-pass (HPF 5Hz + LPF 15Hz), Derivative, Squaring, MWI(150ms)
            - Adaptive Threshold: ${"%.5f".format(result.threshold)}
            - 검출된 R-peak 수: ${result.peakIndices.size}
            - 추정 심박수(BPM): ${result.estimatedHeartRate}
            - R-peak Index(최대 20개): $peaksPreview
        """.trimIndent()
    }
}

data class PanTompkinsResult(
    val integratedSignal: List<Float>,
    val peakIndices: List<Int>,
    val threshold: Float,
    val estimatedHeartRate: Int,
    val samplingRateHz: Int
)

private fun List<Int>.averageOrNull(): Double? {
    if (isEmpty()) return null
    return average()
}
