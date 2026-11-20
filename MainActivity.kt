package com.example.rxscaneditor

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : ComponentActivity() {
    private lateinit var preview: ImageView
    private lateinit var editor: EditText
    private var selectedUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            preview.setImageURI(uri)
            recognizeText(uri)
        }
    }

    private val createPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) writePdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        val title = TextView(this).apply {
            text = "Rx Scan Editor"
            textSize = 24f
            setTextColor(Color.rgb(25, 55, 90))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        root.addView(title, matchWrap())

        val pick = Button(this).apply {
            text = "প্রেসক্রিপশনের ছবি নির্বাচন করুন"
            setOnClickListener { pickImage.launch("image/*") }
        }
        root.addView(pick, matchWrap())

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 220
        ).apply { topMargin = 12; bottomMargin = 12 })

        val label = TextView(this).apply {
            text = "শনাক্ত করা লেখা — প্রয়োজনমতো সম্পাদনা করুন"
            textSize = 16f
            setTextColor(Color.DKGRAY)
        }
        root.addView(label, matchWrap())

        editor = EditText(this).apply {
            hint = "OCR থেকে পাওয়া লেখা এখানে দেখাবে..."
            gravity = Gravity.TOP or Gravity.START
            minLines = 10
            setTextSize(16f)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(editor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = 8; bottomMargin = 8 })

        val export = Button(this).apply {
            text = "সম্পাদিত ট্রান্সক্রিপশন PDF হিসেবে সংরক্ষণ"
            setOnClickListener {
                if (editor.text.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "আগে ছবি স্ক্যান করুন বা লেখা লিখুন", Toast.LENGTH_SHORT).show()
                } else createPdf.launch("prescription-transcription.pdf")
            }
        }
        root.addView(export, matchWrap())

        val note = TextView(this).apply {
            text = "নোট: এটি প্রেসক্রিপশনের লেখা শনাক্ত ও সম্পাদনার খসড়া। মূল প্রেসক্রিপশন, স্বাক্ষর বা সিল পরিবর্তন করে না। ওষুধ/ডোজের পরিবর্তন চিকিৎসকের অনুমোদন ছাড়া ব্যবহার করবেন না।"
            textSize = 12f
            setTextColor(Color.rgb(100, 70, 20))
            setPadding(4, 12, 4, 0)
        }
        root.addView(note, matchWrap())
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun recognizeText(uri: Uri) {
        Toast.makeText(this, "ছবি থেকে লেখা শনাক্ত করা হচ্ছে...", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    editor.setText(result.text)
                    if (result.text.isBlank()) {
                        Toast.makeText(this, "লেখা শনাক্ত হয়নি। হাতে লিখে সম্পাদনা করুন।", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "লেখা শনাক্ত হয়েছে। ভালোভাবে মিলিয়ে নিন।", Toast.LENGTH_LONG).show()
                    }
                    recognizer.close()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "OCR করা যায়নি: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    recognizer.close()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "ছবি খোলা যায়নি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun writePdf(uri: Uri) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val heading = Paint(paint).apply { textSize = 18f; isFakeBoldText = true }
        canvas.drawText("Prescription Text Transcription", 40f, 48f, heading)
        canvas.drawText("Editable text copy — verify all medicines and doses with the doctor.", 40f, 70f, paint)

        val lines = editor.text.toString().split("\n")
        var y = 105f
        for (line in lines) {
            val chunks = wrapLine(line, 82)
            for (chunk in chunks) {
                if (y > 800f) break
                canvas.drawText(chunk, 40f, y, paint)
                y += 18f
            }
            if (y > 800f) break
        }
        pdf.finishPage(page)
        try {
            contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
            Toast.makeText(this, "PDF সংরক্ষণ হয়েছে", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF সংরক্ষণ করা যায়নি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            pdf.close()
        }
    }

    private fun wrapLine(line: String, maxChars: Int): List<String> {
        if (line.length <= maxChars) return listOf(line)
        return line.chunked(maxChars)
    }
}
