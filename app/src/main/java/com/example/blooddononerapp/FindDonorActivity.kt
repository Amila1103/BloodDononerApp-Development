
package com.example.blooddononerapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class FindDonorActivity : AppCompatActivity() {

    private lateinit var dropBloodGroup: AutoCompleteTextView
    private lateinit var dropArea: AutoCompleteTextView
    private lateinit var dropDistance: AutoCompleteTextView

    private lateinit var switchCurrentLocation: SwitchMaterial
    private lateinit var switchAvailableOnly: SwitchMaterial

    private lateinit var txtCurrentLocation: TextView
    private lateinit var txtLocationSummary: TextView

    private val bloodGroups = listOf(
        "A+", "A-", "B+", "B-",
        "AB+", "AB-", "O+", "O-"
    )

    private val areas = listOf(
        "Colombo", "Colombo 01", "Colombo 02",
        "Colombo 03", "Colombo 04", "Colombo 05",
        "Colombo 06", "Colombo 07", "Colombo 08",
        "Colombo 09", "Colombo 10", "Colombo 11",
        "Colombo 12", "Colombo 13", "Colombo 14",
        "Colombo 15", "Maharagama", "Nugegoda",
        "Kottawa", "Homagama", "Dehiwala",
        "Mount Lavinia", "Boralesgamuwa",
        "Piliyandala", "Battaramulla", "Rajagiriya",
        "Kaduwela", "Malabe", "Gampaha", "Negombo",
        "Wattala", "Ja-Ela", "Kadawatha", "Kelaniya",
        "Kiribathgoda", "Minuwangoda", "Kalutara",
        "Panadura", "Horana", "Bandaragama",
        "Kandy", "Peradeniya", "Katugastota",
        "Kundasale", "Digana", "Kurunegala",
        "Kuliyapitiya", "Narammala", "Wariyapola",
        "Galle", "Hikkaduwa", "Ambalangoda",
        "Matara", "Weligama", "Anuradhapura",
        "Polonnaruwa", "Badulla", "Nuwara Eliya",
        "Ratnapura", "Kegalle", "Jaffna",
        "Trincomalee", "Batticaloa", "Ampara"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_find_donor)

        dropBloodGroup =
            findViewById(R.id.dropBloodGroup)

        dropArea =
            findViewById(R.id.dropArea)

        dropDistance =
            findViewById(R.id.dropDistance)

        switchCurrentLocation =
            findViewById(R.id.switchCurrentLocation)

        switchAvailableOnly =
            findViewById(R.id.switchAvailableOnly)

        txtCurrentLocation =
            findViewById(R.id.txtCurrentLocation)

        txtLocationSummary =
            findViewById(R.id.txtLocationSummary)

        setupDropdowns()
        setupLocation()
        setupSearch()
        setupNavigation()

        val useSavedLocation =
            savedInstanceState?.getBoolean(
                "useSavedLocation"
            ) ?: true

        switchCurrentLocation.isChecked =
            useSavedLocation

        switchAvailableOnly.isChecked =
            savedInstanceState?.getBoolean(
                "availableOnly"
            ) ?: true

        dropBloodGroup.setText(
            savedInstanceState?.getString(
                "bloodGroup"
            ).orEmpty(),
            false
        )

        dropArea.setText(
            savedInstanceState?.getString(
                "area"
            ).orEmpty(),
            false
        )

        updateLocationUI()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putBoolean(
            "useSavedLocation",
            switchCurrentLocation.isChecked
        )

        outState.putBoolean(
            "availableOnly",
            switchAvailableOnly.isChecked
        )

        outState.putString(
            "bloodGroup",
            dropBloodGroup.text.toString()
        )

        outState.putString(
            "area",
            dropArea.text.toString()
        )

        super.onSaveInstanceState(outState)
    }

    private fun setupDropdowns() {

        val groupAdapter = ArrayAdapter(
            this,
            R.layout.item_compact_dropdown,
            R.id.txtDropdownItem,
            bloodGroups
        )

        dropBloodGroup.setAdapter(groupAdapter)
        dropBloodGroup.setOnClickListener {
            dropBloodGroup.showDropDown()
        }

        val areaAdapter = ArrayAdapter(
            this,
            R.layout.item_compact_dropdown,
            R.id.txtDropdownItem,
            areas
        )

        dropArea.setAdapter(areaAdapter)
        dropArea.threshold = 1

        dropArea.setOnItemClickListener { _, _, _, _ ->
            updateLocationUI()
        }

        // No donor GPS coordinates exist in this
        // project, so a kilometre radius cannot
        // be calculated safely.
        dropDistance.setText(
            "Area-based search only",
            false
        )

        dropDistance.isEnabled = false
        dropDistance.alpha = 0.65f
    }

    private fun setupLocation() {

        switchCurrentLocation
            .setOnCheckedChangeListener { _, _ ->
                updateLocationUI()
            }

        findViewById<TextView>(
            R.id.txtChangeLocation
        ).setOnClickListener {

            switchCurrentLocation.isChecked = false

            dropArea.requestFocus()
            dropArea.showDropDown()
        }
    }

    private fun updateLocationUI() {

        val useSaved =
            switchCurrentLocation.isChecked

        dropArea.isEnabled = !useSaved

        dropArea.alpha =
            if (useSaved) 0.5f else 1f

        txtCurrentLocation.text =
            if (useSaved) {
                "Uses saved profile area — not GPS"
            } else {
                "Choose an area or district"
            }

        txtLocationSummary.text =
            if (useSaved) {
                "Search: Saved profile area/district"
            } else {
                val area = dropArea.text
                    .toString()
                    .trim()

                if (area.isBlank()) {
                    "Search: Select an area"
                } else {
                    "Search: $area"
                }
            }
    }

    private fun setupSearch() {

        findViewById<MaterialButton>(
            R.id.btnSearchDonors
        ).setOnClickListener {

            val bloodGroup =
                dropBloodGroup.text
                    .toString()
                    .trim()
                    .uppercase()

            val usingSaved =
                switchCurrentLocation.isChecked

            val location =
                if (usingSaved) {
                    "Current Location"
                } else {
                    dropArea.text
                        .toString()
                        .trim()
                }

            if (bloodGroup !in bloodGroups) {

                showWarning(
                    "Select Blood Group",
                    "Please select a valid blood group."
                )

                dropBloodGroup.requestFocus()
                return@setOnClickListener
            }

            if (
                !usingSaved &&
                location.isBlank()
            ) {

                dropArea.error =
                    "Please enter an area or district"

                dropArea.requestFocus()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Search Blood Donors")
                .setMessage(
                    "Blood group: $bloodGroup\n" +
                            "Location: " +
                            if (usingSaved) {
                                "Your saved profile area\n\n"
                            } else {
                                "$location\n\n"
                            } +
                            "Results use saved areas or districts. " +
                            "Exact GPS distance is not available. " +
                            "Donor availability is self-reported " +
                            "and requires staff screening."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Find Donors") { _, _ ->

                    startActivity(
                        Intent(
                            this,
                            DonorResultsActivity::class.java
                        ).apply {

                            putExtra(
                                "bloodGroup",
                                bloodGroup
                            )

                            putExtra(
                                "location",
                                location
                            )

                            putExtra(
                                "distance",
                                "Area-based"
                            )

                            putExtra(
                                "availableOnly",
                                switchAvailableOnly.isChecked
                            )
                        }
                    )
                }
                .show()
        }
    }

    private fun setupNavigation() {

        findViewById<TextView>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<LinearLayout>(R.id.navHome)
            .setOnClickListener {
                startActivity(
                    Intent(
                        this,
                        HomeActivity::class.java
                    ).apply {
                        flags =
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                finish()
            }

        findViewById<LinearLayout>(R.id.navSearch)
            .setOnClickListener {
                // Already on Search.
            }

        findViewById<LinearLayout>(R.id.navRequests)
            .setOnClickListener {
                openScreen(MyRequestsActivity::class.java)
            }

        findViewById<LinearLayout>(R.id.navNotifications)
            .setOnClickListener {
                openScreen(NotificationsActivity::class.java)
            }

        findViewById<LinearLayout>(R.id.navProfile)
            .setOnClickListener {
                openScreen(ProfileActivity::class.java)
            }
    }

    private fun openScreen(destination: Class<*>) {
        startActivity(Intent(this, destination))
    }

    private fun showWarning(
        title: String,
        message: String
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
