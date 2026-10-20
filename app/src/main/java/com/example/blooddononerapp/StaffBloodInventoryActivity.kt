
package com.example.blooddononerapp

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.util.Locale

class StaffBloodInventoryActivity : AppCompatActivity() {

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private lateinit var txtHospitalName: TextView
    private lateinit var btnUpdateStock: MaterialButton

    private lateinit var stockViews:
            Map<String, Pair<String, TextView>>

    private var profileListener: ListenerRegistration? = null
    private var hospitalListener: ListenerRegistration? = null

    private var screenStarted = false
    private var staffHospital = ""
    private var hospitalDocumentId = ""

    private var ready = false
    private var saving = false

    private val currentStock =
        mutableMapOf<String, Long>()

    private val lowStockLimit = 2L
    private val maxStock = 100000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_staff_blood_inventory
        )

        txtHospitalName =
            findViewById(R.id.txtHospitalName)

        btnUpdateStock =
            findViewById(R.id.btnUpdateStock)

        stockViews = linkedMapOf(
            "A+" to (
                    "APlus" to
                            findViewById<TextView>(R.id.txtAPlus)
                    ),

            "A-" to (
                    "AMinus" to
                            findViewById<TextView>(R.id.txtAMinus)
                    ),

            "B+" to (
                    "BPlus" to
                            findViewById<TextView>(R.id.txtBPlus)
                    ),

            "B-" to (
                    "BMinus" to
                            findViewById<TextView>(R.id.txtBMinus)
                    ),

            "AB+" to (
                    "ABPlus" to
                            findViewById<TextView>(R.id.txtABPlus)
                    ),

            "AB-" to (
                    "ABMinus" to
                            findViewById<TextView>(R.id.txtABMinus)
                    ),

            "O+" to (
                    "OPlus" to
                            findViewById<TextView>(R.id.txtOPlus)
                    ),

            "O-" to (
                    "OMinus" to
                            findViewById<TextView>(R.id.txtOMinus)
                    )
        )

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        btnUpdateStock.setOnClickListener {
            showUpdateDialog()
        }

        showUnavailable(
            "Loading hospital inventory..."
        )
    }

    override fun onStart() {
        super.onStart()

        screenStarted = true

        listenToStaffProfile()
    }

    override fun onStop() {

        screenStarted = false

        profileListener?.remove()
        profileListener = null

        hospitalListener?.remove()
        hospitalListener = null

        ready = false

        super.onStop()
    }

    private fun activeScreen(): Boolean {

        return screenStarted &&
                !isFinishing &&
                !isDestroyed
    }

    // ==========================================
    // VERIFY STAFF ACCOUNT
    // ==========================================

    private fun listenToStaffProfile() {

        val user = auth.currentUser

        if (user == null) {
            showUnavailable(
                "Staff sign-in required"
            )
            return
        }

        profileListener?.remove()

        profileListener = db.collection("users")
            .document(user.uid)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { doc, error ->

                if (!activeScreen()) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    closeHospitalListener()

                    showUnavailable(
                        "Staff verification failed"
                    )

                    return@addSnapshotListener
                }

                if (
                    doc == null ||
                    doc.metadata.isFromCache
                ) {

                    if (!ready) {
                        showUnavailable(
                            "Verifying staff account..."
                        )
                    }

                    return@addSnapshotListener
                }

                val activeStaff =
                    doc.exists() &&
                            doc.getString("role") == "STAFF" &&
                            doc.getString("status")
                                .equals("Active", true)

                val assignedHospital =
                    doc.getString("hospital")
                        ?.trim()
                        .orEmpty()

                if (
                    !activeStaff ||
                    assignedHospital.isBlank() ||
                    assignedHospital.equals(
                        "Not Assigned",
                        true
                    )
                ) {

                    closeHospitalListener()

                    staffHospital = ""

                    showUnavailable(
                        "Active staff and assigned hospital required"
                    )

                    return@addSnapshotListener
                }

                if (
                    staffHospital != assignedHospital ||
                    hospitalListener == null
                ) {

                    staffHospital = assignedHospital

                    listenToHospital(
                        assignedHospital
                    )
                }
            }
    }

    private fun closeHospitalListener() {

        hospitalListener?.remove()
        hospitalListener = null

        hospitalDocumentId = ""
        ready = false

        currentStock.clear()
    }

    // ==========================================
    // REALTIME INVENTORY
    // ==========================================

    private fun listenToHospital(name: String) {

        closeHospitalListener()

        showUnavailable(
            "Loading $name inventory..."
        )

        hospitalListener = db
            .collection("hospitals")
            .whereEqualTo("name", name)
            .limit(2)
            .addSnapshotListener(
                MetadataChanges.INCLUDE
            ) { snapshot, error ->

                if (
                    !activeScreen() ||
                    staffHospital != name
                ) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    closeHospitalListener()

                    showUnavailable(
                        "Cannot read hospital stock: " +
                                error.localizedMessage
                    )

                    return@addSnapshotListener
                }

                if (
                    snapshot == null ||
                    snapshot.metadata.isFromCache ||
                    snapshot.metadata.hasPendingWrites()
                ) {

                    if (!saving) {
                        showUnavailable(
                            "Waiting for verified Firebase stock..."
                        )
                    }

                    return@addSnapshotListener
                }

                if (snapshot.size() != 1) {

                    showUnavailable(
                        "Hospital missing or duplicate name; contact Admin"
                    )

                    return@addSnapshotListener
                }

                val hospitalDoc =
                    snapshot.documents.first()

                val hospitalStatus =
                    hospitalDoc.getString("status").orEmpty()

                if (
                    !hospitalStatus.equals(
                        "Active",
                        true
                    )
                ) {

                    showUnavailable(
                        "Hospital is inactive; contact Admin"
                    )

                    return@addSnapshotListener
                }

                val rawStock =
                    hospitalDoc.get("bloodStock") as? Map<*, *>

                if (rawStock == null) {

                    showUnavailable(
                        "Blood stock data is missing; contact Admin"
                    )

                    return@addSnapshotListener
                }

                val updated =
                    mutableMapOf<String, Long>()

                for ((_, details) in stockViews) {

                    val key = details.first

                    val value =
                        readUnits(rawStock[key])

                    if (value == null) {

                        showUnavailable(
                            "Invalid blood stock for $key; contact Admin"
                        )

                        return@addSnapshotListener
                    }

                    updated[key] = value
                }

                hospitalDocumentId =
                    hospitalDoc.id

                currentStock.clear()
                currentStock.putAll(updated)

                ready = true

                txtHospitalName.text = name

                displayStock()
                updateButton()
            }
    }

    // ==========================================
    // STOCK DATA VALIDATION
    // ==========================================

    private fun readUnits(value: Any?): Long? {

        val number = when (value) {
            is Long -> value
            is Int -> value.toLong()
            else -> return null
        }

        return number.takeIf {
            it in 0L..maxStock
        }
    }

    private fun showUnavailable(message: String) {

        ready = false
        hospitalDocumentId = ""

        currentStock.clear()

        txtHospitalName.text = message

        displayStock()
        updateButton()
    }

    // ==========================================
    // UPDATE STOCK CARD LABELS
    // ==========================================

    private fun displayStock() {

        for ((_, details) in stockViews) {

            val (key, textView) = details

            val value =
                if (ready) {
                    currentStock[key]
                } else {
                    null
                }

            textView.text = when (value) {

                null -> "—"

                1L -> "1 Unit"

                else -> "$value Units"
            }

            val parent =
                textView.parent as? ViewGroup

            val childPosition =
                parent?.indexOfChild(textView) ?: -1

            val statusView =
                if (childPosition >= 0) {
                    parent?.getChildAt(
                        childPosition + 1
                    ) as? TextView
                } else {
                    null
                }

            val description = when {

                value == null ->
                    "Unavailable"

                value == 0L ->
                    "Out of Stock"

                value <= lowStockLimit ->
                    "Low Stock"

                else ->
                    "Available"
            }

            val statusColor = when {

                value == null ->
                    Color.GRAY

                value == 0L ->
                    Color.rgb(220, 38, 38)

                value <= lowStockLimit ->
                    Color.rgb(217, 119, 6)

                else ->
                    Color.rgb(21, 128, 61)
            }

            statusView?.text = description

            statusView?.setTextColor(
                statusColor
            )
        }
    }

    private fun updateButton() {

        btnUpdateStock.isEnabled =
            ready && !saving

        btnUpdateStock.text =
            if (saving) {
                "Updating inventory..."
            } else {
                "Update Blood Stock"
            }
    }

    // ==========================================
    // INVENTORY EDIT DIALOG
    // ==========================================

    private fun showUpdateDialog() {

        if (
            !ready ||
            saving ||
            hospitalDocumentId.isBlank()
        ) {

            showMessage(
                "Verified hospital inventory is not available"
            )

            return
        }

        val baselineStock =
            currentStock.toMap()

        val originalHospitalDocumentId =
            hospitalDocumentId

        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_update_blood_stock,
                null
            )

        val edtBloodGroup =
            dialogView.findViewById<TextInputEditText>(
                R.id.edtBloodGroup
            )

        val edtUnits =
            dialogView.findViewById<TextInputEditText>(
                R.id.edtUnits
            )

        edtBloodGroup.isFocusable = false
        edtBloodGroup.isClickable = true

        edtBloodGroup.hint =
            "Tap to select blood group"

        edtUnits.hint =
            "New total available units"

        edtBloodGroup.setOnClickListener {

            val groups =
                stockViews.keys.toTypedArray()

            MaterialAlertDialogBuilder(this)
                .setTitle("Choose blood group")
                .setItems(groups) { _, index ->

                    val group = groups[index]

                    val key =
                        stockViews[group]
                            ?.first
                            .orEmpty()

                    edtBloodGroup.setText(group)

                    edtUnits.setText(
                        baselineStock[key]
                            ?.toString()
                            .orEmpty()
                    )
                }
                .show()
        }

        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    "Manual Blood Stock Correction"
                )
                .setMessage(
                    "Set the TOTAL stock for a blood group. " +
                            "Do not use this form to record a donation. " +
                            "Only enter independently verified inventory counts."
                )
                .setView(dialogView)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Review Change",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE
            ).setOnClickListener {

                val group =
                    edtBloodGroup.text
                        ?.toString()
                        ?.trim()
                        ?.uppercase(Locale.ROOT)
                        .orEmpty()

                val key =
                    stockViews[group]?.first

                val newUnits =
                    edtUnits.text
                        ?.toString()
                        ?.trim()
                        ?.toLongOrNull()

                val originalUnits =
                    if (key == null) {
                        null
                    } else {
                        baselineStock[key]
                    }

                when {

                    !ready ||
                            saving ||
                            originalHospitalDocumentId !=
                            hospitalDocumentId -> {

                        showMessage(
                            "Inventory changed; close and reopen this form"
                        )
                    }

                    key == null -> {

                        edtBloodGroup.error =
                            "Choose a valid blood group"
                    }

                    newUnits == null ||
                            newUnits !in 0L..maxStock -> {

                        edtUnits.error =
                            "Enter 0 to $maxStock whole units"
                    }

                    originalUnits == null -> {

                        showMessage(
                            "Original stock unavailable; reload inventory"
                        )
                    }

                    newUnits == originalUnits -> {

                        edtUnits.error =
                            "Stock is already $newUnits units"
                    }

                    else -> {

                        confirmStockChange(
                            group = group,
                            key = key,
                            originalUnits = originalUnits,
                            newUnits = newUnits,
                            hospitalId =
                                originalHospitalDocumentId,
                            editDialog = dialog
                        )
                    }
                }
            }
        }

        dialog.show()
    }

    // ==========================================
    // PROFESSIONAL CONFIRMATION
    // ==========================================

    private fun confirmStockChange(
        group: String,
        key: String,
        originalUnits: Long,
        newUnits: Long,
        hospitalId: String,
        editDialog: androidx.appcompat.app.AlertDialog
    ) {

        val changeLabel =
            if (newUnits > originalUnits) {
                "+${newUnits - originalUnits}"
            } else {
                "${newUnits - originalUnits}"
            }

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            "Confirm Inventory Correction",

            "Hospital: $staffHospital\n" +
                    "Blood Group: $group\n" +
                    "Current Stock: $originalUnits units\n" +
                    "New Total: $newUnits units\n" +
                    "Change: $changeLabel units\n\n" +
                    "This is a manual inventory correction, NOT a donation. " +
                    "Only proceed after independently verifying the stock count.",

            positiveText = "Confirm Update",
            negativeText = "Back",

            onPositive = {
                saveStockCorrection(
                    key,
                    originalUnits,
                    newUnits,
                    hospitalId,
                    editDialog
                )
            }
        )
    }

    // ==========================================
    // SAFE FIRESTORE TRANSACTION
    // ==========================================

    private fun saveStockCorrection(
        key: String,
        originalUnits: Long,
        newUnits: Long,
        hospitalId: String,
        editDialog: androidx.appcompat.app.AlertDialog
    ) {

        val staffUser =
            auth.currentUser ?: run {

                showMessage(
                    "Staff sign-in required"
                )

                return
            }

        if (
            !ready ||
            saving ||
            hospitalDocumentId != hospitalId
        ) {

            showMessage(
                "Hospital inventory changed; reopen the form"
            )

            return
        }

        saving = true
        updateButton()

        val staffRef =
            db.collection("users")
                .document(staffUser.uid)

        val hospitalRef =
            db.collection("hospitals")
                .document(hospitalId)

        val intendedHospital =
            staffHospital

        db.runTransaction { tx ->

            val staffDoc =
                tx.get(staffRef)

            val hospitalDoc =
                tx.get(hospitalRef)

            val validStaff =
                staffDoc.exists() &&
                        staffDoc.getString("role") == "STAFF" &&
                        staffDoc.getString("status")
                            .equals("Active", true) &&
                        staffDoc.getString("hospital") ==
                        intendedHospital

            if (!validStaff) {

                throw IllegalStateException(
                    "Active staff permission required"
                )
            }

            val validHospital =
                hospitalDoc.exists() &&
                        hospitalDoc.getString("name") ==
                        intendedHospital &&
                        hospitalDoc.getString("status")
                            .equals("Active", true)

            if (!validHospital) {

                throw IllegalStateException(
                    "Hospital is no longer active or assigned"
                )
            }

            val stock =
                hospitalDoc.get("bloodStock")
                        as? Map<*, *>
                    ?: throw IllegalStateException(
                        "Missing blood stock map"
                    )

            val serverUnits =
                readUnits(stock[key])
                    ?: throw IllegalStateException(
                        "Invalid current stock value"
                    )

            if (serverUnits != originalUnits) {

                throw IllegalStateException(
                    "Stock changed from $originalUnits " +
                            "to $serverUnits. Reopen the form " +
                            "and verify the latest count"
                )
            }

            if (newUnits == serverUnits) {

                throw IllegalStateException(
                    "No inventory change needed"
                )
            }

            if (newUnits !in 0L..maxStock) {

                throw IllegalStateException(
                    "Stock amount is out of range"
                )
            }

            tx.update(
                hospitalRef,
                mapOf(
                    "bloodStock.$key" to newUnits,
                    "updatedAt" to
                            FieldValue.serverTimestamp()
                )
            )

            true
        }
            .addOnSuccessListener {

                if (!activeScreen()) {
                    return@addOnSuccessListener
                }

                saving = false
                updateButton()

                editDialog.dismiss()

                StaffFeedbackDialogs.show(
                    this,
                    StaffFeedbackDialogs.Type.SUCCESS,
                    "Inventory Updated",
                    "Manual stock correction saved to Firebase. " +
                            "The inventory will refresh with the latest values."
                )
            }
            .addOnFailureListener { error ->

                if (!activeScreen()) {
                    return@addOnFailureListener
                }

                saving = false
                updateButton()

                StaffFeedbackDialogs.show(
                    this,
                    StaffFeedbackDialogs.Type.ERROR,
                    "Inventory Update Failed",
                    error.localizedMessage
                        ?: "The stock correction was not saved.",
                    positiveText = "OK"
                )
            }
    }

    // ==========================================
    // PROFESSIONAL WARNING DIALOG
    // ==========================================

    private fun showMessage(message: String) {

        if (!activeScreen()) {
            return
        }

        StaffFeedbackDialogs.show(
            this,
            StaffFeedbackDialogs.Type.WARNING,
            "Inventory Review",
            message,
            positiveText = "OK"
        )
    }
}
