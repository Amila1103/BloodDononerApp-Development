package com.example.blooddononerapp

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

// Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import java.util.Calendar
import java.util.Locale

class ProfileSetupActivity : AppCompatActivity() {

    private val dropdownItemHeightDp = 42
    private val maxDropdownItems = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_profile_setup)


        // ==========================================
        // CONNECT XML COMPONENTS
        // ==========================================

        val dob =
            findViewById<TextInputEditText>(R.id.edtDOB)

        val gender =
            findViewById<AutoCompleteTextView>(R.id.dropGender)

        val district =
            findViewById<AutoCompleteTextView>(R.id.dropDistrict)

        val area =
            findViewById<AutoCompleteTextView>(R.id.dropArea)

        val radius =
            findViewById<AutoCompleteTextView>(R.id.dropRadius)

        val completeButton =
            findViewById<MaterialButton>(R.id.btnCompleteProfile)

        val switchAvailable =
            findViewById<SwitchMaterial>(R.id.switchAvailable)

        val switchLocation =
            findViewById<SwitchMaterial>(R.id.switchLocation)


        // ==========================================
        // GENDER
        // ==========================================

        val genders = arrayOf(
            "Male",
            "Female",
            "Prefer not to say"
        )

        val genderAdapter = ArrayAdapter(
            this,
            R.layout.item_profile_dropdown,
            R.id.txtDropdownItem,
            genders
        )

        gender.setAdapter(genderAdapter)

        gender.keyListener = null

        setDropdownHeight(
            gender,
            genders.size
        )

        gender.setOnClickListener {

            setDropdownHeight(
                gender,
                genders.size
            )

            gender.showDropDown()
        }


        // ==========================================
        // SRI LANKA DISTRICTS
        // ==========================================

        val districts = arrayOf(
            "Ampara",
            "Anuradhapura",
            "Badulla",
            "Batticaloa",
            "Colombo",
            "Galle",
            "Gampaha",
            "Hambantota",
            "Jaffna",
            "Kalutara",
            "Kandy",
            "Kegalle",
            "Kilinochchi",
            "Kurunegala",
            "Mannar",
            "Matale",
            "Matara",
            "Monaragala",
            "Mullaitivu",
            "Nuwara Eliya",
            "Polonnaruwa",
            "Puttalam",
            "Ratnapura",
            "Trincomalee",
            "Vavuniya"
        )

        val districtAdapter = ArrayAdapter(
            this,
            R.layout.item_profile_dropdown,
            R.id.txtDropdownItem,
            districts
        )

        district.setAdapter(districtAdapter)

        // Suggest after 1 character
        district.threshold = 1


        // ==========================================
        // DYNAMIC DISTRICT DROPDOWN HEIGHT
        // ==========================================

        district.addTextChangedListener(

            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    val typedText =
                        s?.toString()?.trim().orEmpty()

                    val matchCount =
                        countMatches(
                            districts,
                            typedText
                        )

                    setDropdownHeight(
                        district,
                        matchCount
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )


        // ==========================================
        // ALL AREA / CITY SUGGESTIONS
        // ==========================================

        val allAreas = arrayOf(

            // Colombo
            "Colombo",
            "Dehiwala",
            "Mount Lavinia",
            "Moratuwa",
            "Maharagama",
            "Kottawa",
            "Nugegoda",
            "Homagama",
            "Kaduwela",
            "Battaramulla",
            "Rajagiriya",
            "Boralesgamuwa",
            "Piliyandala",
            "Kesbewa",
            "Padukka",
            "Hanwella",

            // Gampaha
            "Gampaha",
            "Negombo",
            "Ja-Ela",
            "Wattala",
            "Kelaniya",
            "Kadawatha",
            "Ragama",
            "Minuwangoda",
            "Kiribathgoda",
            "Divulapitiya",

            // Kalutara
            "Kalutara",
            "Panadura",
            "Horana",
            "Beruwala",
            "Bandaragama",
            "Matugama",

            // Kandy
            "Kandy",
            "Peradeniya",
            "Katugastota",
            "Kundasale",
            "Akurana",
            "Digana",
            "Gampola",

            // Galle
            "Galle",
            "Hikkaduwa",
            "Ambalangoda",
            "Baddegama",
            "Elpitiya",

            // Matara
            "Matara",
            "Weligama",
            "Akuressa",
            "Dikwella",

            // Kurunegala
            "Kurunegala",
            "Kuliyapitiya",
            "Narammala",
            "Mawathagama",

            // Anuradhapura
            "Anuradhapura",
            "Kekirawa",
            "Medawachchiya",
            "Mihintale",

            // Badulla
            "Badulla",
            "Bandarawela",
            "Ella",
            "Haputale",
            "Mahiyanganaya",

            // Ratnapura
            "Ratnapura",
            "Balangoda",
            "Embilipitiya",
            "Pelmadulla",

            // Kegalle
            "Kegalle",
            "Mawanella",
            "Warakapola",
            "Rambukkana",

            // Jaffna
            "Jaffna",
            "Chavakachcheri",
            "Point Pedro",

            // Batticaloa
            "Batticaloa",
            "Kattankudy",
            "Eravur",

            // Trincomalee
            "Trincomalee",
            "Kinniya",
            "Kantale",

            // Ampara
            "Ampara",
            "Kalmunai",
            "Akkaraipattu",

            // Hambantota
            "Hambantota",
            "Tangalle",
            "Tissamaharama",

            // Matale
            "Matale",
            "Dambulla",

            // Nuwara Eliya
            "Nuwara Eliya",
            "Hatton",

            // Polonnaruwa
            "Polonnaruwa",
            "Hingurakgoda",

            // Puttalam
            "Puttalam",
            "Chilaw",
            "Wennappuwa",

            // Monaragala
            "Monaragala",
            "Wellawaya",

            // Other
            "Mannar",
            "Kilinochchi",
            "Mullaitivu",
            "Vavuniya"
        )


        // ==========================================
        // AREA / CITY
        // ==========================================

        var currentAreaSuggestions: Array<String> =
            allAreas

        var areaAdapter = ArrayAdapter(
            this,
            R.layout.item_profile_dropdown,
            R.id.txtDropdownItem,
            currentAreaSuggestions
        )

        area.setAdapter(areaAdapter)

        area.threshold = 1


        // ==========================================
        // DYNAMIC AREA DROPDOWN HEIGHT
        // ==========================================

        area.addTextChangedListener(

            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    val typedText =
                        s?.toString()?.trim().orEmpty()

                    val matchCount =
                        countMatches(
                            currentAreaSuggestions,
                            typedText
                        )

                    setDropdownHeight(
                        area,
                        matchCount
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )


        // ==========================================
        // DISTRICT SELECTED
        // UPDATE AREA SUGGESTIONS
        // ==========================================

        district.setOnItemClickListener { _, _, _, _ ->

            val selectedDistrict =
                district.text
                    .toString()
                    .trim()

            val districtAreas =
                getAreasForDistrict(
                    selectedDistrict
                )

            currentAreaSuggestions =
                if (districtAreas.isNotEmpty()) {

                    districtAreas

                } else {

                    allAreas
                }


            // Clear previous area
            area.setText(
                "",
                false
            )


            // Update Area Adapter
            areaAdapter = ArrayAdapter(
                this,
                R.layout.item_profile_dropdown,
                R.id.txtDropdownItem,
                currentAreaSuggestions
            )

            area.setAdapter(areaAdapter)

            area.threshold = 1
        }


        // ==========================================
        // TRAVEL DISTANCE
        // ==========================================

        val distances = arrayOf(
            "Within 5 km",
            "Within 10 km",
            "Within 25 km",
            "Within 50 km",
            "50+ km"
        )

        val radiusAdapter = ArrayAdapter(
            this,
            R.layout.item_profile_dropdown,
            R.id.txtDropdownItem,
            distances
        )

        radius.setAdapter(radiusAdapter)

        radius.keyListener = null

        radius.setOnClickListener {

            setDropdownHeight(
                radius,
                distances.size
            )

            radius.showDropDown()
        }


        // ==========================================
        // DATE OF BIRTH
        // ==========================================

        dob.setOnClickListener {

            showDateOfBirthDialog(
                dob
            )
        }


        // ==========================================
        // COMPLETE PROFILE
        // ==========================================

        completeButton.setOnClickListener {

            val dobText = dob.text.toString().trim()
            val genderText = gender.text.toString().trim()
            val districtText = district.text.toString().trim()
            val areaText = area.text.toString().trim()
            val radiusText = radius.text.toString().trim()

            val availableToDonate = switchAvailable.isChecked
            val shareApproximateLocation = switchLocation.isChecked

            when {
                dobText.isEmpty() -> {
                    Toast.makeText(this, "Please select your date of birth", Toast.LENGTH_SHORT).show()
                }

                genderText.isEmpty() -> {
                    Toast.makeText(this, "Please select your gender", Toast.LENGTH_SHORT).show()
                }

                districtText.isEmpty() -> {
                    Toast.makeText(this, "Please enter or select your district", Toast.LENGTH_SHORT).show()
                    district.requestFocus()
                }

                areaText.isEmpty() -> {
                    area.error = "Please enter or select your area / city"
                    area.requestFocus()
                }

                radiusText.isEmpty() -> {
                    Toast.makeText(this, "Please select how far you can travel", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser

                    if (currentUser == null) {
                        Toast.makeText(
                            this,
                            "User session not found. Please login again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    val userId = currentUser.uid
                    val firestore = FirebaseFirestore.getInstance()

                    val profileData = hashMapOf<String, Any>(
                        "dateOfBirth" to dobText,
                        "gender" to genderText,
                        "district" to districtText,
                        "area" to areaText,
                        "travelDistance" to radiusText,
                        "availableToDonate" to availableToDonate,
                        "shareApproximateLocation" to shareApproximateLocation,
                        "profileCompleted" to true
                    )

                    completeButton.isEnabled = false
                    completeButton.text = "Saving..."

                    firestore
                        .collection("users")
                        .document(userId)
                        .update(profileData)
                        .addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Profile setup completed",
                                Toast.LENGTH_SHORT
                            ).show()

                            val intent = Intent(
                                this@ProfileSetupActivity,
                                LoginActivity::class.java
                            )

                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK

                            startActivity(intent)

                            overridePendingTransition(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                            )

                            finish()
                        }
                        .addOnFailureListener { exception ->
                            completeButton.isEnabled = true
                            completeButton.text = "Complete Profile"

                            Toast.makeText(
                                this,
                                "Failed to save profile: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        }
    }

    // ==========================================
    // COUNT MATCHING SUGGESTIONS
    // ==========================================

    private fun countMatches(
        items: Array<String>,
        typedText: String
    ): Int {

        if (typedText.isEmpty()) {

            return items.size
        }


        return items.count {

            it.contains(
                typedText,
                ignoreCase = true
            )
        }
    }


    // ==========================================
    // AUTOMATIC DROPDOWN HEIGHT
    // ==========================================

    private fun setDropdownHeight(
        dropdown: AutoCompleteTextView,
        itemCount: Int
    ) {

        val visibleItems =
            when {

                itemCount <= 0 ->
                    1

                itemCount > maxDropdownItems ->
                    maxDropdownItems

                else ->
                    itemCount
            }


        dropdown.dropDownHeight =
            dpToPx(
                dropdownItemHeightDp *
                        visibleItems
            )
    }


    // ==========================================
    // AREAS BY DISTRICT
    // ==========================================

    private fun getAreasForDistrict(
        district: String
    ): Array<String> {

        return when (district) {

            "Colombo" -> arrayOf(
                "Colombo",
                "Dehiwala",
                "Mount Lavinia",
                "Moratuwa",
                "Maharagama",
                "Kottawa",
                "Nugegoda",
                "Homagama",
                "Kaduwela",
                "Battaramulla",
                "Rajagiriya",
                "Boralesgamuwa",
                "Piliyandala",
                "Kesbewa",
                "Padukka",
                "Hanwella"
            )


            "Gampaha" -> arrayOf(
                "Gampaha",
                "Negombo",
                "Ja-Ela",
                "Wattala",
                "Kelaniya",
                "Kadawatha",
                "Ragama",
                "Minuwangoda",
                "Kiribathgoda",
                "Divulapitiya"
            )


            "Kalutara" -> arrayOf(
                "Kalutara",
                "Panadura",
                "Horana",
                "Beruwala",
                "Bandaragama",
                "Matugama"
            )


            "Kandy" -> arrayOf(
                "Kandy",
                "Peradeniya",
                "Katugastota",
                "Kundasale",
                "Akurana",
                "Digana",
                "Gampola"
            )


            "Galle" -> arrayOf(
                "Galle",
                "Hikkaduwa",
                "Ambalangoda",
                "Baddegama",
                "Elpitiya"
            )


            "Matara" -> arrayOf(
                "Matara",
                "Weligama",
                "Akuressa",
                "Dikwella"
            )


            "Kurunegala" -> arrayOf(
                "Kurunegala",
                "Kuliyapitiya",
                "Narammala",
                "Mawathagama"
            )


            "Anuradhapura" -> arrayOf(
                "Anuradhapura",
                "Kekirawa",
                "Medawachchiya",
                "Mihintale"
            )


            "Badulla" -> arrayOf(
                "Badulla",
                "Bandarawela",
                "Ella",
                "Haputale",
                "Mahiyanganaya"
            )


            "Ratnapura" -> arrayOf(
                "Ratnapura",
                "Balangoda",
                "Embilipitiya",
                "Pelmadulla"
            )


            "Kegalle" -> arrayOf(
                "Kegalle",
                "Mawanella",
                "Warakapola",
                "Rambukkana"
            )


            "Jaffna" -> arrayOf(
                "Jaffna",
                "Chavakachcheri",
                "Point Pedro"
            )


            "Batticaloa" -> arrayOf(
                "Batticaloa",
                "Kattankudy",
                "Eravur"
            )


            "Trincomalee" -> arrayOf(
                "Trincomalee",
                "Kinniya",
                "Kantale"
            )


            "Ampara" -> arrayOf(
                "Ampara",
                "Kalmunai",
                "Akkaraipattu"
            )


            "Hambantota" -> arrayOf(
                "Hambantota",
                "Tangalle",
                "Tissamaharama"
            )


            "Matale" -> arrayOf(
                "Matale",
                "Dambulla"
            )


            "Nuwara Eliya" -> arrayOf(
                "Nuwara Eliya",
                "Hatton"
            )


            "Polonnaruwa" -> arrayOf(
                "Polonnaruwa",
                "Hingurakgoda"
            )


            "Puttalam" -> arrayOf(
                "Puttalam",
                "Chilaw",
                "Wennappuwa"
            )


            "Monaragala" -> arrayOf(
                "Monaragala",
                "Wellawaya"
            )


            "Mannar" ->
                arrayOf(
                    "Mannar"
                )


            "Kilinochchi" ->
                arrayOf(
                    "Kilinochchi"
                )


            "Mullaitivu" ->
                arrayOf(
                    "Mullaitivu"
                )


            "Vavuniya" ->
                arrayOf(
                    "Vavuniya"
                )


            else ->
                emptyArray()
        }
    }


    // ==========================================
    // CUSTOM DATE OF BIRTH DIALOG
    // ==========================================

    private fun showDateOfBirthDialog(
        dobField: TextInputEditText
    ) {

        val dialog =
            Dialog(this)


        dialog.requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )


        dialog.setContentView(
            R.layout.dialog_date_of_birth
        )


        dialog.window?.setBackgroundDrawable(
            ColorDrawable(
                Color.TRANSPARENT
            )
        )


        // ==========================================
        // CONNECT DOB COMPONENTS
        // ==========================================

        val dayDropdown =
            dialog.findViewById<AutoCompleteTextView>(
                R.id.dropDay
            )

        val monthDropdown =
            dialog.findViewById<AutoCompleteTextView>(
                R.id.dropMonth
            )

        val yearDropdown =
            dialog.findViewById<AutoCompleteTextView>(
                R.id.dropYear
            )

        val errorText =
            dialog.findViewById<TextView>(
                R.id.txtDateError
            )

        val cancelButton =
            dialog.findViewById<MaterialButton>(
                R.id.btnCancelDate
            )

        val selectButton =
            dialog.findViewById<MaterialButton>(
                R.id.btnSelectDate
            )


        // ==========================================
        // DAYS
        // ==========================================

        val days =
            (1..31).map {

                String.format(
                    Locale.getDefault(),
                    "%02d",
                    it
                )
            }


        val dayAdapter =
            ArrayAdapter(
                this,
                R.layout.item_profile_dropdown,
                R.id.txtDropdownItem,
                days
            )


        dayDropdown.setAdapter(
            dayAdapter
        )

        dayDropdown.keyListener =
            null


        dayDropdown.setOnClickListener {

            setDropdownHeight(
                dayDropdown,
                days.size
            )

            dayDropdown.showDropDown()
        }


        // ==========================================
        // MONTHS
        // ==========================================

        val months =
            arrayOf(
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December"
            )


        val monthAdapter =
            ArrayAdapter(
                this,
                R.layout.item_profile_dropdown,
                R.id.txtDropdownItem,
                months
            )


        monthDropdown.setAdapter(
            monthAdapter
        )

        monthDropdown.keyListener =
            null


        monthDropdown.setOnClickListener {

            setDropdownHeight(
                monthDropdown,
                months.size
            )

            monthDropdown.showDropDown()
        }


        // ==========================================
        // YEARS
        // ==========================================

        val currentYear =
            Calendar.getInstance()
                .get(Calendar.YEAR)


        val years =
            (currentYear downTo 1920)
                .map {

                    it.toString()
                }


        val yearAdapter =
            ArrayAdapter(
                this,
                R.layout.item_profile_dropdown,
                R.id.txtDropdownItem,
                years
            )


        yearDropdown.setAdapter(
            yearAdapter
        )

        yearDropdown.keyListener =
            null


        yearDropdown.setOnClickListener {

            setDropdownHeight(
                yearDropdown,
                years.size
            )

            yearDropdown.showDropDown()
        }


        // ==========================================
        // CANCEL
        // ==========================================

        cancelButton.setOnClickListener {

            dialog.dismiss()
        }


        // ==========================================
        // SELECT DATE
        // ==========================================

        selectButton.setOnClickListener {

            errorText.visibility =
                View.GONE


            val dayText =
                dayDropdown.text
                    .toString()
                    .trim()


            val monthText =
                monthDropdown.text
                    .toString()
                    .trim()


            val yearText =
                yearDropdown.text
                    .toString()
                    .trim()


            // ==========================================
            // EMPTY CHECK
            // ==========================================

            if (
                dayText.isEmpty() ||
                monthText.isEmpty() ||
                yearText.isEmpty()
            ) {

                errorText.text =
                    "Please select day, month and year"

                errorText.visibility =
                    View.VISIBLE

                return@setOnClickListener
            }


            val selectedDay =
                dayText.toInt()


            val selectedMonth =
                months.indexOf(
                    monthText
                ) + 1


            val selectedYear =
                yearText.toInt()


            // ==========================================
            // VALIDATE DATE
            // ==========================================

            val selectedCalendar =
                Calendar.getInstance()


            selectedCalendar.isLenient =
                false


            try {

                selectedCalendar.set(
                    selectedYear,
                    selectedMonth - 1,
                    selectedDay,
                    0,
                    0,
                    0
                )


                selectedCalendar.set(
                    Calendar.MILLISECOND,
                    0
                )


                // Force validation
                selectedCalendar.time


                val today =
                    Calendar.getInstance()


                // ==========================================
                // FUTURE DATE CHECK
                // ==========================================

                if (
                    selectedCalendar.after(
                        today
                    )
                ) {

                    errorText.text =
                        "Date of birth cannot be in the future"

                    errorText.visibility =
                        View.VISIBLE

                    return@setOnClickListener
                }


                // ==========================================
                // SET DOB
                // ==========================================

                val selectedDate =
                    String.format(
                        Locale.getDefault(),
                        "%02d/%02d/%04d",
                        selectedDay,
                        selectedMonth,
                        selectedYear
                    )


                dobField.setText(
                    selectedDate
                )


                dialog.dismiss()


            } catch (_: IllegalArgumentException) {

                errorText.text =
                    "Please select a valid date"

                errorText.visibility =
                    View.VISIBLE
            }
        }


        // ==========================================
        // SHOW DIALOG
        // ==========================================

        dialog.show()


        dialog.window?.setLayout(
            (
                    resources.displayMetrics.widthPixels *
                            0.92
                    ).toInt(),

            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }


    // ==========================================
    // DP TO PX
    // ==========================================

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
                dp *
                        resources.displayMetrics.density
                ).toInt()
    }
}