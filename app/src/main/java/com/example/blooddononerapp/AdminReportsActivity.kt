
package com.example.blooddononerapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import java.io.IOException
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminReportsActivity : AppCompatActivity() {

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var btnGenerateReport: MaterialButton
    private lateinit var txtReportUsers: TextView
    private lateinit var txtReportDonations: TextView
    private lateinit var txtReportRequests: TextView
    private lateinit var txtReportHospitals: TextView

    private val overviewListeners =
        mutableListOf<ListenerRegistration>()

    private var loading = false
    private var pendingPdfReport: String? = null

    private val savePdfLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/pdf"
            )
        ) { uri ->

            val report = pendingPdfReport
            pendingPdfReport = null

            if (
                uri != null &&
                report != null
            ) {
                try {
                    val output =
                        contentResolver.openOutputStream(
                            uri,
                            "w"
                        ) ?: throw IOException(
                            "Cannot open selected file"
                        )

                    output.use {
                        writePdf(report, it)
                    }

                    Toast.makeText(
                        this,
                        "PDF report saved successfully",
                        Toast.LENGTH_LONG
                    ).show()

                } catch (error: Exception) {
                    Toast.makeText(
                        this,
                        "PDF saving failed: " +
                                error.localizedMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_reports
        )

        pendingPdfReport =
            savedInstanceState?.getString(
                "pendingPdfReport"
            )

        findViewById<MaterialCardView>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        btnGenerateReport =
            findViewById(
                R.id.btnGenerateReport
            )

        txtReportUsers =
            findViewById(R.id.txtReportUsers)

        txtReportDonations =
            findViewById(R.id.txtReportDonations)

        txtReportRequests =
            findViewById(R.id.txtReportRequests)

        txtReportHospitals =
            findViewById(R.id.txtReportHospitals)

        findViewById<MaterialCardView>(
            R.id.cardReportUsers
        ).setOnClickListener {
            loadReport("USERS")
        }

        findViewById<MaterialCardView>(
            R.id.cardReportDonations
        ).setOnClickListener {
            loadReport("DONATIONS")
        }

        findViewById<MaterialCardView>(
            R.id.cardReportRequests
        ).setOnClickListener {
            loadReport("REQUESTS")
        }

        findViewById<MaterialCardView>(
            R.id.cardReportHospitals
        ).setOnClickListener {
            loadReport("HOSPITALS")
        }

        btnGenerateReport.setOnClickListener {
            loadReport("SYSTEM")
        }
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(
            "pendingPdfReport",
            pendingPdfReport
        )

        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        startOverviewListeners()
    }

    override fun onStop() {

        overviewListeners.forEach {
            it.remove()
        }

        overviewListeners.clear()

        super.onStop()
    }

    private fun startOverviewListeners() {

        overviewListeners.forEach {
            it.remove()
        }

        overviewListeners.clear()

        observeCount(
            "users",
            txtReportUsers,
            usersOnly = true
        )

        observeCount(
            "donationRecords",
            txtReportDonations
        )

        observeCount(
            "emergencyRequests",
            txtReportRequests
        )

        observeCount(
            "hospitals",
            txtReportHospitals
        )
    }

    private fun observeCount(
        collection: String,
        textView: TextView,
        usersOnly: Boolean = false
    ) {

        textView.text = "…"

        val listener = db
            .collection(collection)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {
                    return@addSnapshotListener
                }

                if (
                    error != null ||
                    snapshot == null
                ) {
                    textView.text = "—"

                    Toast.makeText(
                        this,
                        "Cannot load $collection: " +
                                (
                                        error?.localizedMessage
                                            ?: "Unknown error"
                                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addSnapshotListener
                }

                if (
                    snapshot.metadata.isFromCache
                ) {
                    textView.text = "…"
                    return@addSnapshotListener
                }

                val count = if (usersOnly) {

                    snapshot.documents.count {
                        it.getString("role").equals(
                            "USER",
                            ignoreCase = true
                        )
                    }

                } else {

                    snapshot.size()
                }

                textView.text =
                    NumberFormat.getIntegerInstance(
                        Locale.getDefault()
                    ).format(count)
            }

        overviewListeners.add(listener)
    }

    private fun loadReport(
        type: String
    ) {

        if (loading) return

        setLoading(true)

        db.collection("users")
            .get(Source.SERVER)
            .addOnSuccessListener { users ->

                db.collection("hospitals")
                    .get(Source.SERVER)
                    .addOnSuccessListener { hospitals ->

                        db.collection(
                            "emergencyRequests"
                        )
                            .get(Source.SERVER)
                            .addOnSuccessListener { requests ->

                                db.collection(
                                    "donationRecords"
                                )
                                    .get(Source.SERVER)
                                    .addOnSuccessListener { donations ->

                                        setLoading(false)

                                        if (
                                            !isFinishing &&
                                            !isDestroyed
                                        ) {
                                            val report =
                                                buildReport(
                                                    type,
                                                    users,
                                                    hospitals,
                                                    requests,
                                                    donations
                                                )

                                            if (
                                                type == "SYSTEM"
                                            ) {
                                                requestPdfSave(
                                                    type,
                                                    report
                                                )
                                            } else {
                                                showReport(
                                                    type,
                                                    report
                                                )
                                            }
                                        }
                                    }
                                    .addOnFailureListener {
                                        showError(
                                            it.message
                                        )
                                    }
                            }
                            .addOnFailureListener {
                                showError(it.message)
                            }
                    }
                    .addOnFailureListener {
                        showError(it.message)
                    }
            }
            .addOnFailureListener {
                showError(it.message)
            }
    }

    private fun buildReport(
        type: String,
        users: QuerySnapshot,
        hospitals: QuerySnapshot,
        requests: QuerySnapshot,
        donations: QuerySnapshot
    ): String {

        val date = SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.getDefault()
        ).format(Date())

        val output = StringBuilder(
            "Blood Donor App\n" +
                    "Report generated: $date\n"
        )

        if (
            type == "SYSTEM" ||
            type == "USERS"
        ) {

            val regularUsers =
                users.documents.count {
                    it.getString("role").equals(
                        "USER",
                        ignoreCase = true
                    )
                }

            val activeUsers =
                users.documents.count {
                    it.getString("role").equals(
                        "USER",
                        ignoreCase = true
                    ) &&
                            (
                                    it.getString("status")
                                        ?: "Active"
                                    ).equals(
                                    "Active",
                                    ignoreCase = true
                                )
                }

            val staff =
                users.documents.count {
                    it.getString("role").equals(
                        "STAFF",
                        ignoreCase = true
                    )
                }

            val admins =
                users.documents.count {
                    it.getString("role").equals(
                        "ADMIN",
                        ignoreCase = true
                    )
                }

            output.append(
                "\nUSERS\n" +
                        "Registered users: $regularUsers\n" +
                        "Active users: $activeUsers\n" +
                        "Suspended/inactive users: " +
                        "${regularUsers - activeUsers}\n" +
                        "Staff accounts: $staff\n" +
                        "Admin accounts: $admins\n"
            )
        }

        if (
            type == "SYSTEM" ||
            type == "HOSPITALS"
        ) {

            val activeHospitals =
                hospitals.documents.count {
                    (
                            it.getString("status")
                                ?: "Active"
                            ).equals(
                            "Active",
                            ignoreCase = true
                        )
                }

            val totalStock =
                hospitals.documents.sumOf { hospital ->

                    val stock =
                        hospital.get("bloodStock")
                                as? Map<*, *>

                    stock?.values?.sumOf {
                        (it as? Number)?.toLong()
                            ?: 0L
                    } ?: 0L
                }

            output.append(
                "\nHOSPITALS AND INVENTORY\n" +
                        "Registered hospitals: " +
                        "${hospitals.size()}\n" +
                        "Active hospitals: " +
                        "$activeHospitals\n" +
                        "Inactive hospitals: " +
                        "${hospitals.size() - activeHospitals}\n" +
                        "Total recorded blood units: " +
                        "$totalStock\n"
            )
        }

        if (
            type == "SYSTEM" ||
            type == "REQUESTS"
        ) {

            output.append(
                "\nEMERGENCY REQUESTS\n" +
                        "Total requests: " +
                        "${requests.size()}\n"
            )

            val counts =
                requests.documents.groupingBy {
                    it.getString("status")
                        ?: "Unknown"
                }.eachCount().toSortedMap()

            for ((status, count) in counts) {
                output.append(
                    "$status: $count\n"
                )
            }
        }

        if (
            type == "SYSTEM" ||
            type == "DONATIONS"
        ) {

            val completed =
                donations.documents.count {
                    it.getString("status").equals(
                        "Completed",
                        ignoreCase = true
                    )
                }

            val unitsAdded =
                donations.documents.sumOf {
                    (
                            it.get("inventoryUnitsAdded")
                                    as? Number
                            )?.toLong() ?: 0L
                }

            output.append(
                "\nDONATIONS\n" +
                        "Total records: " +
                        "${donations.size()}\n" +
                        "Completed donations: " +
                        "$completed\n" +
                        "Other statuses: " +
                        "${donations.size() - completed}\n" +
                        "Recorded units added to inventory: " +
                        "$unitsAdded\n"
            )
        }

        output.append(
            "\nSource: Firebase Firestore (server)"
        )

        return output.toString()
    }

    private fun showReport(
        type: String,
        report: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("$type Report")
            .setMessage(report)
            .setNegativeButton(
                "Close",
                null
            )
            .setNeutralButton(
                "Share Text"
            ) { _, _ ->

                val intent = Intent(
                    Intent.ACTION_SEND
                ).apply {

                    this.type = "text/plain"

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "Blood Donor App Report"
                    )

                    putExtra(
                        Intent.EXTRA_TEXT,
                        report
                    )
                }

                startActivity(
                    Intent.createChooser(
                        intent,
                        "Share Report"
                    )
                )
            }
            .setPositiveButton(
                "Save PDF"
            ) { _, _ ->

                requestPdfSave(
                    type,
                    report
                )
            }
            .show()
    }

    private fun requestPdfSave(
        type: String,
        report: String
    ) {

        pendingPdfReport = report

        val fileName =
            "BloodDonor_${type}_Report_" +
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date()) +
                    ".pdf"

        savePdfLauncher.launch(
            fileName
        )
    }

    private fun writePdf(
        report: String,
        output: OutputStream
    ) {

        val pdf = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842

        val margin = 48f

        val contentRight =
            pageWidth - margin

        val colorRed =
            Color.rgb(239, 38, 55)

        val colorDark =
            Color.rgb(30, 30, 35)

        val colorMuted =
            Color.rgb(105, 105, 112)

        val colorLine =
            Color.rgb(229, 231, 235)

        val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        )

        var currentPage: PdfDocument.Page? = null
        var pageNumber = 0
        var y = 0f

        fun drawText(
            text: String,
            x: Float,
            baseline: Float,
            size: Float,
            color: Int,
            bold: Boolean = false
        ) {

            paint.color = color
            paint.textSize = size

            paint.typeface = if (bold) {
                Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
            } else {
                Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.NORMAL
                )
            }

            paint.style =
                Paint.Style.FILL

            currentPage?.canvas?.drawText(
                text,
                x,
                baseline,
                paint
            )
        }

        fun closePage() {

            val page = currentPage ?: return

            paint.color = colorLine
            paint.strokeWidth = 1f

            page.canvas.drawLine(
                margin,
                798f,
                contentRight,
                798f,
                paint
            )

            drawText(
                "Blood Donor App | Firebase Firestore Report",
                margin,
                815f,
                8f,
                colorMuted
            )

            drawText(
                "Page $pageNumber",
                contentRight - 35f,
                815f,
                8f,
                colorMuted
            )

            pdf.finishPage(page)

            currentPage = null
        }

        fun openPage() {

            closePage()

            pageNumber += 1

            val pageInfo =
                PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    pageNumber
                ).create()

            val page =
                pdf.startPage(pageInfo)

            currentPage = page

            page.canvas.drawColor(
                Color.WHITE
            )

            paint.color = colorRed
            paint.style = Paint.Style.FILL

            page.canvas.drawRect(
                0f,
                0f,
                pageWidth.toFloat(),
                130f,
                paint
            )

            drawText(
                "BLOOD DONOR APP",
                margin,
                61f,
                23f,
                Color.WHITE,
                bold = true
            )

            drawText(
                "SYSTEM REPORT & ANALYTICS",
                margin,
                92f,
                12f,
                Color.WHITE
            )

            y = 159f
        }

        fun ensureSpace(
            height: Float
        ) {
            if (
                currentPage == null ||
                y + height > 785f
            ) {
                openPage()
            }
        }

        try {

            openPage()

            val sections = setOf(
                "USERS",
                "HOSPITALS AND INVENTORY",
                "EMERGENCY REQUESTS",
                "DONATIONS"
            )

            for (original in report.lines()) {

                val line = original.trim()

                if (
                    line.isEmpty() ||
                    line == "Blood Donor App"
                ) {
                    continue
                }

                if (line in sections) {

                    ensureSpace(64f)

                    y += 23f

                    paint.color = colorRed

                    currentPage?.canvas?.drawRect(
                        margin,
                        y - 18f,
                        margin + 4f,
                        y + 5f,
                        paint
                    )

                    drawText(
                        line,
                        margin + 14f,
                        y,
                        14f,
                        colorDark,
                        bold = true
                    )

                    y += 23f

                    continue
                }

                if (
                    line.startsWith(
                        "Report generated:"
                    )
                ) {

                    ensureSpace(28f)

                    drawText(
                        line,
                        margin,
                        y,
                        10f,
                        colorMuted
                    )

                    y += 14f

                    continue
                }

                if (
                    line.startsWith("Source:")
                ) {

                    ensureSpace(35f)

                    y += 17f

                    drawText(
                        line,
                        margin,
                        y,
                        9f,
                        colorMuted
                    )

                    y += 17f

                    continue
                }

                ensureSpace(34f)

                val colon =
                    line.indexOf(':')

                if (colon > 0) {

                    val label =
                        line.substring(
                            0,
                            colon
                        ).trim()

                    val value =
                        line.substring(
                            colon + 1
                        ).trim()

                    drawText(
                        label,
                        margin + 7f,
                        y,
                        11f,
                        colorDark
                    )

                    paint.textSize = 11f

                    paint.typeface =
                        Typeface.create(
                            Typeface.DEFAULT,
                            Typeface.BOLD
                        )

                    val valueWidth =
                        paint.measureText(value)

                    drawText(
                        value,
                        contentRight -
                                valueWidth - 7f,
                        y,
                        11f,
                        colorDark,
                        bold = true
                    )

                } else {

                    drawText(
                        line,
                        margin + 7f,
                        y,
                        11f,
                        colorDark
                    )
                }

                y += 9f

                paint.color = colorLine
                paint.strokeWidth = 0.7f

                currentPage?.canvas?.drawLine(
                    margin,
                    y,
                    contentRight,
                    y,
                    paint
                )

                y += 21f
            }

            closePage()

            pdf.writeTo(output)

        } finally {
            pdf.close()
        }
    }

    private fun showError(
        message: String?
    ) {

        setLoading(false)

        if (
            !isFinishing &&
            !isDestroyed
        ) {
            Toast.makeText(
                this,
                "Unable to generate report: " +
                        (message ?: "Firebase error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setLoading(
        isLoading: Boolean
    ) {

        loading = isLoading

        btnGenerateReport.isEnabled =
            !isLoading

        btnGenerateReport.text =
            if (isLoading) {
                "Loading Firebase data..."
            } else {
                "Generate System Report"
            }
    }
}
