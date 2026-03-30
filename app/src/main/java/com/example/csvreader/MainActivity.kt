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

    private lateinit var chart: LineChart
    private lateinit var resultTextView: TextView

    private val openCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { readCsvAndRenderGraph(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart = findViewById(R.id.lineChartCsv)
        resultTextView = findViewById(R.id.tvResult)

        setupChart()

        val selectButton: Button = findViewById(R.id.btnSelectCsv)
        selectButton.setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel"))
        }
    }

    private fun setupChart() {
        chart.setNoDataText(getString(R.string.default_guide))
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.description = Description().apply { text = getString(R.string.chart_description) }
    }

    private fun readCsvAndRenderGraph(uri: Uri) {
        val values = mutableListOf<Float>()
        var totalRows = 0

        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    totalRows++
                    val numeric = line
                        .split(",")
                        .firstNotNullOfOrNull { it.trim().toFloatOrNull() }

                    if (numeric != null) values.add(numeric)
                }
            }
        }

        if (values.isEmpty()) {
            resultTextView.text = getString(R.string.no_numeric_data)
            chart.clear()
            chart.invalidate()
            return
        }

        val entries = values.mapIndexed { index, value ->
            Entry(index.toFloat(), value)
        }

        val dataSet = LineDataSet(entries, getString(R.string.chart_label)).apply {
            color = Color.parseColor("#1E88E5")
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        chart.data = LineData(dataSet)
        chart.invalidate()

        resultTextView.text = getString(
            R.string.load_summary,
            totalRows,
            values.size
        )
    }
}
