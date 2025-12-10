package com.example.krishimitra.models

//data class CropDetail(
//    val documentId: String,
//    val cropName: String,
//    val predictedDisease: String,
//    val imageThumbnail: String? = null,
//    val quickAdvice: List<String>,
//    // disease details
//    val diseaseNameLocalized: String? = null,
//    val transmissionMethod: TransmissionMethod? = null,
//    val commonSymptoms: List<String>? = null,
//    // treatment & advice
//    val immediateAction: List<String>? = null,
//    val recommendedTreatment: List<Treatment>? = null,
//    val precautions: List<String>? = null,
//    val naturalRemedies: List<String>? = null,
//    val weatherBasedAdvice: List<String>? = null,
//    // follow-up
//    val expectedRecoveryTime: String? = null,
//    val signsOfImprovement: List<String>? = null
//)

data class CropDetail(
    val documentId: String = "",
    val cropName: String = "",
    val predictedDisease: String = "",
    val quickAdvice: List<String> = emptyList(),
    val diseaseNameLocalized: String? = null,
    val transmissionMethod: TransmissionMethod? = null,
    val commonSymptoms: List<String> = emptyList(),
    val immediateAction: List<String> = emptyList(),
    val recommendedTreatment: List<Treatment> = emptyList(),
    val precautions: List<String> = emptyList(),
    val naturalRemedies: List<String> = emptyList(),
    val weatherBasedAdvice: List<String> = emptyList(),
    val expectedRecoveryTime: String? = null,
    val signsOfImprovement: List<String> = emptyList()
)