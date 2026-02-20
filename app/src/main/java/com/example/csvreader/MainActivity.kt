package com.example.csvreader

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var resultTextView: TextView

    private val openCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { readCsvFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.tvResult)
        val selectButton: Button = findViewById(R.id.btnSelectCsv)

        selectButton.setOnClickListener {
            openCsvLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel"))
        }
    }

    private fun readCsvFile(uri: Uri) {
        val content = StringBuilder()

        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                var lineCount = 0

                while (reader.readLine().also { line = it } != null) {
                    val columns = line!!.split(",")
                    content.append(columns.joinToString(" | ")).append("\n")
                    lineCount++

                    if (lineCount >= 30) {
                        content.append("... (미리보기는 30줄까지만 표시)")
                        break
                    }
                }
            }

            resultTextView.text = if (content.isNotEmpty()) {
                content.toString()
            } else {
                "CSV 파일이 비어 있습니다."
            }
        } ?: run {
            resultTextView.text = "파일을 열 수 없습니다."
        }
    }
}
