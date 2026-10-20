
package com.example.blooddononerapp

import java.util.Calendar
import java.util.TimeZone

/**
 * Screen-side age safeguard.
 * Actual donation requires hospital screening.
 */
object DonorEligibility {

    private val sriLankaZone =
        TimeZone.getTimeZone("Asia/Colombo")

    // DOB format: dd/MM/yyyy
    fun isAdult(dateOfBirth: String?): Boolean {

        val value = dateOfBirth?.trim().orEmpty()

        if (!Regex("""\d{2}/\d{2}/\d{4}""").matches(value)) {
            return false
        }

        val parts = value.split('/')

        val day = parts[0].toIntOrNull()
            ?: return false

        val month = parts[1].toIntOrNull()
            ?: return false

        val year = parts[2].toIntOrNull()
            ?: return false

        if (year < 1900 || month !in 1..12 || day !in 1..31) {
            return false
        }

        val birthday = Calendar.getInstance(
            sriLankaZone
        ).apply {

            isLenient = false
            clear()

            set(year, month - 1, day)
        }

        try {
            birthday.timeInMillis
        } catch (_: IllegalArgumentException) {
            return false
        }

        val today = Calendar.getInstance(
            sriLankaZone
        )

        if (birthday.after(today)) {
            return false
        }

        var age =
            today.get(Calendar.YEAR) -
                    birthday.get(Calendar.YEAR)

        val currentMonth = today.get(Calendar.MONTH)
        val birthMonth = birthday.get(Calendar.MONTH)

        if (
            currentMonth < birthMonth ||
            (
                    currentMonth == birthMonth &&
                            today.get(Calendar.DAY_OF_MONTH) <
                            birthday.get(Calendar.DAY_OF_MONTH)
                    )
        ) {
            age--
        }

        return age >= 18
    }
}
