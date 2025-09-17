package com.example.krishimitra.models

data class CropCaptureModel(
    val docId: String = "",                     // Firestore Document ID (e.g., "Apple_Black_Rot")
    var predictedDiseaseName: String? = null,    // Name predicted by model (e.g., "Black Rot")
    var timestamp: String? = null,               // When the capture was made
    var cropImageUrl: String? = null,            // Image URL (fixed variable name casing)
    var captureLocation: String? = null,         // Textual location (e.g., "Delhi, India")
    var capturedBy: String? = null,              // Who captured (optional, can be future use)
    var temperature: Double? = null,             // Temperature in Celsius
    var coordinates: String? = null,             // "lat,lng" format
    var place: String? = null,                   // Specific place name (if any)
    var pressure: Int? = null,                   // Atmospheric pressure (hPa)
    var wind: Double? = null,                    // Wind speed (m/s)
    var humidity: Int? = null                    // Humidity percentage
) {
    // No-argument constructor for Firebase deserialization
    constructor() : this(
        docId = "",
        predictedDiseaseName = null,
        timestamp = null,
        cropImageUrl = null,
        captureLocation = null,
        capturedBy = null,
        temperature = null,
        coordinates = null,
        place = null,
        pressure = null,
        wind = null,
        humidity = null
    )
}
