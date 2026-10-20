package com.example.blooddononerapp

data class Donor(

    val uid: String = "",

    val fullName: String = "",

    val email: String = "",

    val phone: String = "",

    val bloodGroup: String = "",

    val area: String = "",

    val district: String = "",

    val dateOfBirth: String = "",

    val gender: String = "",

    val role: String = "USER",

    val status: String = "Active",

    val availableToDonate: Boolean = false,

    val profileCompleted: Boolean = false,

    val travelDistance: String = "",

    val shareApproximateLocation: Boolean = false
)