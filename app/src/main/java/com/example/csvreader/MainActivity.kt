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

class MainActivity : AppCompatActivity() {

    private lateinit var resultTextView: TextView
    private lateinit var lineChart: LineChart

    private val openCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { readCsvFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.tvResult)
        lineChart = findViewById(R.id.lineChart)
        setupChart()

        val selectButton: Button = findViewById(R.id.btnSelectCsv)
        selectButton.setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel"))
        }
    }

    private fun setupChart() {
        lineChart.setNoDataText(getString(R.string.default_guide))
        lineChart.axisRight.isEnabled = false
        lineChart.description = Description().apply { text = getString(R.string.chart_description) }
        lineChart.legend.isEnabled = false
    }

    private fun readCsvFile(uri: Uri) {
        val textContent = StringBuilder()
        val graphEntries = mutableListOf<Entry>()

        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                var lineIndex = 0
                var previewCount = 0

                while (reader.readLine().also { line = it } != null) {
                    val row = line ?: continue
                    val columns = row.split(",")

                    if (previewCount < 100) {
                        textContent.append(columns.joinToString(" | ")).append("\n")
                        previewCount++
                    }

                    extractNumericValue(columns)?.let { value ->
                        graphEntries.add(Entry(lineIndex.toFloat(), value))
                    }
                    lineIndex++
                }
            }

            resultTextView.text = if (textContent.isNotEmpty()) {
                textContent.toString()
            } else {
                getString(R.string.empty_csv)
            }

            renderChart(graphEntries)
        } ?: run {
            resultTextView.text = getString(R.string.cannot_open_file)
            lineChart.clear()
            lineChart.invalidate()
        }
    }

    private fun extractNumericValue(columns: List<String>): Float? {
        return columns.firstNotNullOfOrNull { cell ->
            cell.trim().toFloatOrNull()
        }
    }

    private fun renderChart(entries: List<Entry>) {
        if (entries.isEmpty()) {
            lineChart.clear()
            lineChart.invalidate()
            return
        }

        val dataSet = LineDataSet(entries, "CSV Data").apply {
            color = Color.parseColor("#1E88E5")
            valueTextColor = Color.parseColor("#111111")
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
    }
}
