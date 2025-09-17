package com.example.krishimitra

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.krishimitra.databinding.ActivityCropDetailBinding
import com.example.krishimitra.databinding.ItemBulletTextBinding
import com.example.krishimitra.databinding.ItemTreatmentBinding
import com.example.krishimitra.models.CropDetail
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CropDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCropDetailBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.theme)

        val docId = intent.getStringExtra("docId")
        val disease = intent.getStringExtra("predictedDiseaseName")

        Log.d("CropDetailActivity", "Received docId: $docId, disease: $disease")

        if (docId.isNullOrBlank() && disease.isNullOrBlank()) {
            Toast.makeText(this, "No crop ID or disease provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up Know More button click listener
//        binding.btnKnowMore.setOnClickListener {
//            Toast.makeText(this, "Know More clicked - Implement further action", Toast.LENGTH_SHORT).show()
//            // TODO: Implement navigation or action (e.g., open web page, show detailed info)
//        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cropData = loadCropDetails(docId, disease)
                withContext(Dispatchers.Main) {
                    if (cropData != null) {
                        Log.d("CropDetailActivity", "Loaded cropData: $cropData")
                        populateUI(cropData)
                    } else {
                        Log.w("CropDetailActivity", "No crop data found for docId: $docId, disease: $disease")
                        Toast.makeText(this@CropDetailActivity, "No details found for this crop", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("CropDetailActivity", "Error loading crop details: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropDetailActivity, "Error loading details: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private suspend fun loadCropDetails(docId: String?, disease: String?): CropDetail? {
        // 1) Try direct document lookup
        docId?.let { id ->
            Log.d("CropDetailActivity", "Fetching document with docId: $id")
            val snapshot = db.collection("crop_details").document(id).get().await()
            if (snapshot.exists()) {
                Log.d("CropDetailActivity", "Found document: ${snapshot.data}")
                return snapshot.toObject(CropDetail::class.java)
            } else {
                Log.w("CropDetailActivity", "No document found for docId: $id")
            }
        }

        // 2) Fallback query by predictedDisease
        disease?.let { dis ->
            Log.d("CropDetailActivity", "Querying for predictedDisease: $dis")
            val querySnapshot = db.collection("crop_details")
                .whereEqualTo("predictedDisease", dis)
                .limit(1)
                .get()
                .await()

            val document = querySnapshot.documents.firstOrNull()
            if (document != null) {
                Log.d("CropDetailActivity", "Found document for disease: ${document.data}")
                return document.toObject(CropDetail::class.java)
            } else {
                Log.w("CropDetailActivity", "No document found for disease: $dis")
            }
        }

        Log.w("CropDetailActivity", "No data found for docId: $docId, disease: $disease")
        return null
    }

    private fun populateUI(c: CropDetail) {
        // 1) Image & Title
        binding.cropImage.load(intent.getStringExtra("cropimage").orEmpty()) {
            placeholder(R.drawable.cropplaceholder)
            error(R.drawable.cropplaceholder)
        }
        binding.tvCropName.text = c.cropName.orEmpty()

        // 2) Meta information (from Intent extras)
        binding.tvLocation.text = intent.getStringExtra("location")?.let { "Location: $it" } ?: "N/A"
        binding.tvCapturedOn.text = intent.getStringExtra("captured_on")?.let { "Scanned: $it" } ?: "N/A"
        binding.tvCoordinates.text = intent.getStringExtra("coordinates")?.let { "Coordinates: $it" } ?: "N/A"

        // 3) Weather details
        binding.tvTemperature.text = intent.getStringExtra("temperature")?.let { "Temp: $it °C" } ?: "N/A"
        binding.tvHumidity.text = intent.getStringExtra("humidity")?.let { "Humidity: $it%" } ?: "N/A"
        binding.tvPressure.text = intent.getStringExtra("pressure")?.let { "Pressure: $it hPa" } ?: "N/A"

        // 4) Disease details
        binding.tvPredictedDisease.text = c.predictedDisease.orEmpty()
        binding.tvDiseaseLocalized.text = c.diseaseNameLocalized.orEmpty()
        binding.tvTransmission.text = c.transmissionMethod?.name
            ?.lowercase()
            ?.replaceFirstChar { it.titlecase() }
            ?: "-"

        // 5) Symptoms
        binding.symptomsContainer.removeAllViews()
        c.commonSymptoms.forEach { symptom ->
            val bulletBinding = ItemBulletTextBinding.inflate(LayoutInflater.from(this), binding.symptomsContainer, false)
            bulletBinding.tvBullet.text = symptom
            binding.symptomsContainer.addView(bulletBinding.root)
        }

        // 6) Quick Advice
        binding.quickAdviceContainer.removeAllViews()
        c.quickAdvice.forEach { advice ->
            val bulletBinding = ItemBulletTextBinding.inflate(LayoutInflater.from(this), binding.quickAdviceContainer, false)
            bulletBinding.tvBullet.text = advice
            binding.quickAdviceContainer.addView(bulletBinding.root)
        }

        // 7) Immediate Action
        binding.immediateActionContainer.removeAllViews()
        c.immediateAction.forEach { action ->
            val bulletBinding = ItemBulletTextBinding.inflate(LayoutInflater.from(this), binding.immediateActionContainer, false)
            bulletBinding.tvBullet.text = action
            binding.immediateActionContainer.addView(bulletBinding.root)
        }

        // 8) Recommended Treatments
        binding.treatmentsContainer.removeAllViews()
        c.recommendedTreatment.forEach { treatment ->
            val treatmentBinding = ItemTreatmentBinding.inflate(LayoutInflater.from(this), binding.treatmentsContainer, false)
            treatmentBinding.tvBrand.text = treatment.localBrandName.orEmpty()
            treatmentBinding.tvScientific.text = treatment.scientificName.orEmpty()
            treatmentBinding.tvDosage.text = treatment.dosage.orEmpty()
            treatmentBinding.tvMethod.text = treatment.applicationMethod.orEmpty()
            treatmentBinding.tvNotes.text = treatment.notes?.joinToString("\n") { "• $it" } ?: "-"
            binding.treatmentsContainer.addView(treatmentBinding.root)
        }

        // 9) Precautions
        binding.precautionsContainer.removeAllViews()
        c.precautions.forEach { precaution ->
            val bulletBinding = ItemBulletTextBinding.inflate(LayoutInflater.from(this), binding.precautionsContainer, false)
            bulletBinding.tvBullet.text = precaution
            binding.precautionsContainer.addView(bulletBinding.root)
        }

        // 10) Natural Remedies
        binding.naturalRemediesContainer.removeAllViews()
        c.naturalRemedies.forEach { remedy ->
            val bulletBinding = ItemBulletTextBinding.inflate(LayoutInflater.from(this), binding.naturalRemediesContainer, false)
            bulletBinding.tvBullet.text = remedy
            binding.naturalRemediesContainer.addView(bulletBinding.root)
        }

        // 11) Weather-based Advice
        binding.weatherAdviceContainer.removeAllViews()
        c.weatherBasedAdvice.forEach { advice ->
            val bulletBinding = ItemBulletTextBinding.inflate(LayoutInflater.from(this), binding.weatherAdviceContainer, false)
            bulletBinding.tvBullet.text = advice
            binding.weatherAdviceContainer.addView(bulletBinding.root)
        }

        // 12) Follow-Up & Monitoring
        binding.tvExpectedRecovery.text = "Expected Recovery: ${c.expectedRecoveryTime ?: "-"}"
        binding.signsOfImprovementContainer.removeAllViews()
        c.signsOfImprovement.forEach { sign ->
            val bulletBinding = ItemBulletTextBinding.inflate(LayoutInflater.from(this), binding.signsOfImprovementContainer, false)
            bulletBinding.tvBullet.text = sign
            binding.signsOfImprovementContainer.addView(bulletBinding.root)
        }
    }
}