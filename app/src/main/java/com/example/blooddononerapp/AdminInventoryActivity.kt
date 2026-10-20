
package com.example.blooddononerapp

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminInventoryActivity : AppCompatActivity() {

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private var inventoryListener: ListenerRegistration? = null

    private lateinit var txtTotalUnits: TextView
    private lateinit var txtLowStockCount: TextView

    private lateinit var bloodGroupViews: Map<String, TextView>

    private val bloodGroupKeys = linkedMapOf(
        "A+" to "APlus",
        "A-" to "AMinus",
        "B+" to "BPlus",
        "B-" to "BMinus",
        "AB+" to "ABPlus",
        "AB-" to "ABMinus",
        "O+" to "OPlus",
        "O-" to "OMinus"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_admin_inventory)

        txtTotalUnits = findViewById(R.id.txtTotalUnits)
        txtLowStockCount = findViewById(R.id.txtLowStockCount)

        bloodGroupViews = linkedMapOf(
            "A+" to findViewById(R.id.txtAPlus),
            "A-" to findViewById(R.id.txtAMinus),
            "B+" to findViewById(R.id.txtBPlus),
            "B-" to findViewById(R.id.txtBMinus),
            "AB+" to findViewById(R.id.txtABPlus),
            "AB-" to findViewById(R.id.txtABMinus),
            "O+" to findViewById(R.id.txtOPlus),
            "O-" to findViewById(R.id.txtOMinus)
        )

        findViewById<MaterialCardView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<MaterialCardView>(R.id.btnRefresh)
            .setOnClickListener {
                loadInventory()
            }

        showLoading()
    }

    override fun onStart() {
        super.onStart()
        loadInventory()
    }

    override fun onStop() {
        inventoryListener?.remove()
        inventoryListener = null
        super.onStop()
    }

    private fun showLoading() {
        txtTotalUnits.text = "Loading..."
        txtLowStockCount.text = "Loading..."

        for (view in bloodGroupViews.values) {
            view.text = "Loading..."
        }
    }

    private fun loadInventory() {

        inventoryListener?.remove()

        inventoryListener = db
            .collection("hospitals")
            .addSnapshotListener { snapshot, error ->

                if (isFinishing || isDestroyed) {
                    return@addSnapshotListener
                }

                if (error != null) {
                    showError(error.localizedMessage)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val totals = mutableMapOf<String, Long>()

                for (group in bloodGroupKeys.keys) {
                    totals[group] = 0L
                }

                for (hospital in snapshot.documents) {

                    val stock = hospital.get("bloodStock")
                            as? Map<*, *>

                    for ((group, key) in bloodGroupKeys) {

                        val quantity = (
                                stock?.get(key) as? Number
                                )?.toLong()?.coerceAtLeast(0L) ?: 0L

                        totals[group] =
                            (totals[group] ?: 0L) + quantity
                    }
                }

                updateInventory(
                    totals,
                    snapshot.size()
                )
            }
    }

    private fun updateInventory(
        totals: Map<String, Long>,
        hospitalCount: Int
    ) {

        var totalUnits = 0L
        var lowStockCount = 0

        for ((group, view) in bloodGroupViews) {

            val units = totals[group] ?: 0L

            totalUnits += units

            if (units <= 4L) {
                lowStockCount++
            }

            view.text = "$units Units"

            updateStockStatus(
                view,
                units
            )
        }

        txtTotalUnits.text = "$totalUnits Units"

        txtLowStockCount.text =
            if (hospitalCount == 0) {
                "No Hospitals"
            } else {
                "$lowStockCount Blood Groups"
            }
    }

    private fun updateStockStatus(
        unitsTextView: TextView,
        units: Long
    ) {

        // Status label is the next TextView
        // in the existing blood group card XML.
        val parent = unitsTextView.parent as? ViewGroup
            ?: return

        val index = parent.indexOfChild(unitsTextView)

        val statusView = parent.getChildAt(index + 1)
                as? TextView ?: return

        when {

            units == 0L -> {

                statusView.text = "Out of Stock"

                statusView.setTextColor(
                    Color.parseColor("#EF2637")
                )
            }

            units <= 4L -> {

                statusView.text = "Low Stock"

                statusView.setTextColor(
                    Color.parseColor("#EF2637")
                )
            }

            else -> {

                statusView.text = "Available"

                statusView.setTextColor(
                    Color.parseColor("#15803D")
                )
            }
        }
    }

    private fun showError(message: String?) {

        txtTotalUnits.text = "Unavailable"
        txtLowStockCount.text = "Unavailable"

        for (view in bloodGroupViews.values) {
            view.text = "--"

            val parent = view.parent as? ViewGroup
            val index = parent?.indexOfChild(view) ?: -1

            val statusView = if (index >= 0) {
                parent?.getChildAt(index + 1) as? TextView
            } else {
                null
            }

            statusView?.text = "Unavailable"
        }

        Toast.makeText(
            this,
            "Inventory loading failed: ${message ?: "Firebase error"}",
            Toast.LENGTH_LONG
        ).show()
    }
}
