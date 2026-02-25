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

            val panTompkinsResult = runPanTompkins(ecgValues)

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

    private fun runPanTompkins(ecg: List<Float>): PanTompkinsResult {
        val mean = ecg.average().toFloat()
        val centered = ecg.map { it - mean }

        val derivative = MutableList(centered.size) { 0f }
        for (i in 1 until centered.size) {
            derivative[i] = centered[i] - centered[i - 1]
        }

        val squared = derivative.map { it * it }

        val windowSize = 12
        val integrated = MutableList(squared.size) { 0f }
        var runningSum = 0f
        for (i in squared.indices) {
            runningSum += squared[i]
            if (i >= windowSize) {
                runningSum -= squared[i - windowSize]
            }
            val divisor = if (i + 1 < windowSize) i + 1 else windowSize
            integrated[i] = runningSum / divisor
        }

        val threshold = (integrated.maxOrNull() ?: 0f) * 0.35f
        val refractory = 20
        val peakIndices = mutableListOf<Int>()
        var lastPeak = -refractory

        for (i in 1 until integrated.size - 1) {
            val isLocalPeak = integrated[i] > integrated[i - 1] && integrated[i] >= integrated[i + 1]
            val passesThreshold = integrated[i] > threshold
            val outsideRefractory = i - lastPeak >= refractory

            if (isLocalPeak && passesThreshold && outsideRefractory) {
                peakIndices.add(i)
                lastPeak = i
            }
        }

        val rrIntervals = peakIndices.zipWithNext { a, b -> b - a }
        val avgRr = rrIntervals.averageOrNull() ?: 0.0
        val estimatedHeartRate = if (avgRr > 0) {
            (60.0 * 250.0 / avgRr).roundToInt()
        } else {
            0
        }

        return PanTompkinsResult(
            integratedSignal = integrated,
            peakIndices = peakIndices,
            threshold = threshold,
            estimatedHeartRate = estimatedHeartRate
        )
    }

    private fun buildPanTompkinsSummary(result: PanTompkinsResult): String {
        val peaksPreview = if (result.peakIndices.isEmpty()) {
            getString(R.string.no_detected_peak)
        } else {
            result.peakIndices.take(20).joinToString(", ")
        }

        return """
            [Pan & Tompkins 결과]
            - Threshold: ${"%.5f".format(result.threshold)}
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
    val estimatedHeartRate: Int
)

private fun List<Int>.averageOrNull(): Double? {
    if (isEmpty()) return null
    return average()
}
