package com.example.krishimitra.models

data class Treatment(
    val localBrandName: String = "",
    val scientificName: String = "",
    val dosage: String = "",
    val applicationMethod: String = "",
    val notes: List<String>? = null
)