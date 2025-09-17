@file:Suppress("DEPRECATION")

package com.example.krishimitra

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.krishimitra.databinding.ActivityMainBinding
import com.example.krishimitra.models.CropDetail
import com.example.krishimitra.models.TransmissionMethod
import com.example.krishimitra.models.Treatment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import android.text.InputType
import androidx.appcompat.app.AlertDialog


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    companion object {
        const val REQ_CODE = 123
    }

    override fun onStart() {
        super.onStart()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // User is signed in (email/password or Google)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.statusBarColor = ContextCompat.getColor(this, R.color.theme)
        super.onCreate(savedInstanceState)
        // for seeding purpose in Firebase
//        seedCropDetails()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestForPermission()
        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()

        val appInfo: ApplicationInfo = applicationContext.packageManager
            .getApplicationInfo(applicationContext.packageName, PackageManager.GET_META_DATA)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(appInfo.metaData["default_web_client_id"].toString())
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // Email/Password login
        binding.loginBtn.setOnClickListener {
            handleEmailPasswordLogin()
        }

        // Google Sign-In
        binding.loginWithGoogleBtn.setOnClickListener {
            signInGoogle()
        }

        binding.signupBtn.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        // Reset Password
        binding.forgotPasswordTv.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()

            if (email.isEmpty()) {
                binding.emailEt.error = "Enter your registered email"
                return@setOnClickListener
            }

            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        AlertDialog.Builder(this)
                            .setTitle("Password Reset Email Sent")
                            .setMessage("Check your inbox to reset your password.")
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Failed to send reset email: ${task.exception?.message}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
        }

        val passwordEt = binding.passwordEt

        var isPasswordVisible = false

        passwordEt.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = 2 // Index for drawableEnd
                val drawable = passwordEt.compoundDrawables[drawableEnd]
                if (drawable != null &&
                    event.rawX >= (passwordEt.right - drawable.bounds.width() - passwordEt.paddingEnd)) {

                    // Toggle password visibility
                    isPasswordVisible = !isPasswordVisible
                    if (isPasswordVisible) {
                        passwordEt.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        passwordEt.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_open, 0)
                    } else {
                        passwordEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        passwordEt.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_closed, 0)
                    }

                    passwordEt.setSelection(passwordEt.text.length)

                    // ⚠️ Accessibility fix
                    v.performClick()

                    return@setOnTouchListener true
                }
            }
            false
        }


    }

    private fun signInGoogle() {
        val signInIntent: Intent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, REQ_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CODE) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleGoogleSignInResult(task)
        }
    }

    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account: GoogleSignInAccount? = completedTask.getResult(ApiException::class.java)
            if (account != null) {
                authenticateWithFirebase(account)
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("GoogleSignIn", "Google Sign-In error: ${e.message}", e)
        }
    }

    private fun authenticateWithFirebase(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.let {
                    saveUserToFirestore(
                        it.uid,
                        User(
                            name = account.displayName,
                            email = account.email,
                            prf_pic = account.photoUrl?.toString()
                        )
                    )
                }
                navigateToHome()
            } else {
                Toast.makeText(
                    this,
                    "Authentication Failed: ${task.exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("Auth", "Firebase Authentication error: ${task.exception?.message}")
            }
        }
    }

    private fun saveUserToFirestore(uid: String, user: User) {
        val userDocument = db.collection("users").document(uid)
        userDocument.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                // If user doesn't exist in Firestore, add them
                userDocument.set(user)
                    .addOnSuccessListener {
                        Log.d("Firestore", "User added to Firestore successfully")
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Failed to add user to Firestore: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("Firestore", "Error adding user to Firestore", e)
                    }
            } else {
                Log.d("Firestore", "User already exists in Firestore")
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error fetching user document: ${e.message}", Toast.LENGTH_SHORT)
                .show()
            Log.e("Firestore", "Error checking user existence", e)
        }
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun handleEmailPasswordLogin() {
        binding.apply {
            val email = emailEt.text.toString()
            val password = passwordEt.text.toString()

            if (email.isEmpty()) {
                emailEt.error = "Please fill your Email ID"
                return
            }

            if (password.isEmpty()) {
                passwordEt.error = "Please fill your Password"
                return
            }

            loader.visibility = View.VISIBLE
            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                loader.visibility = View.GONE
                if (task.isSuccessful) {
                    navigateToHome()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun requestForPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            100
        )
    }
}


// --- Helper to sanitize labels into Firestore-safe IDs ---
private fun sanitize(label: String): String {
    return label
        .substringAfter(" ") // Remove emoji and everything before first space
        .replace(Regex("[^A-Za-z0-9]+"), "_") // Replace non-alphanumerics with underscore
        .replace(Regex("_+"), "_") // Collapse multiple underscores into one
        .trim('_') // Remove leading/trailing underscores
}

fun seedCropDetails() {
    val db = FirebaseFirestore.getInstance()
    val col = db.collection("crop_details")

    // 1) Prebuild a map of all details keyed by the sanitized document ID.
    val detailsMap: Map<String, CropDetail> = getLabels().associateBy(
        keySelector = { sanitize(it) },
        valueTransform = { label ->
            // Split the label into ["🍅 Tomato", "Early Blight"]
            val (cropWithEmoji, diseaseOrHealthy) = label.split(" - ").let {
                it[0] to it.getOrElse(1) { "Healthy" }
            }

            // Create the CropDetail with documentId as a field
            if (diseaseOrHealthy == "Healthy") {
                // Healthy case
                CropDetail(
                    documentId = sanitize(label),
                    cropName = cropWithEmoji,
                    predictedDisease = "Healthy",
                    quickAdvice = listOf(
                        "Continue regular care",
                        "Maintain balanced nutrition & watering"
                    )
                )
            } else {
                // Diseased case — pick from a when-block
                when (label) {
                    "🍎 Apple - Apple Scab" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Apple Scab",
                        quickAdvice = listOf("Remove infected leaves", "Apply Mancozeb"),
                        diseaseNameLocalized = "सेब का स्कैब",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Dark spots on leaves", "Yellow halos"),
                        immediateAction = listOf("Destroy fallen leaves", "Thin canopy"),
                        recommendedTreatment = listOf(
                            Treatment("Dithane M-45", "Mancozeb", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Wear gloves", "Avoid spraying in rain"),
                        naturalRemedies = listOf("Neem oil spray"),
                        weatherBasedAdvice = listOf("Water at soil level"),
                        expectedRecoveryTime = "14–21 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new leaves")
                    )
                    "🍎 Apple - Black Rot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Black Rot",
                        quickAdvice = listOf("Prune cankered branches", "Apply Captan"),
                        diseaseNameLocalized = "काली सड़न",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Brown fruit lesions", "Leaf spots with red margins"),
                        immediateAction = listOf("Sanitize tools", "Remove mummified fruit"),
                        recommendedTreatment = listOf(
                            Treatment("Captan WP", "Captan", "1.5 g/L", "Spray pre- and post-bloom")
                        ),
                        precautions = listOf("Alternate fungicides", "Keep people away during spray"),
                        naturalRemedies = listOf("Baking soda spray"),
                        weatherBasedAdvice = listOf("Don’t spray before rain"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("Lesions stop enlarging")
                    )
                    "🍎 Apple - Cedar Apple Rust" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Cedar Apple Rust",
                        quickAdvice = listOf("Remove nearby cedar trees", "Apply Myclobutanil"),
                        diseaseNameLocalized = "सेदार सेब रस्ट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Yellow-orange spots on leaves", "Fruit deformation"),
                        immediateAction = listOf("Prune infected parts", "Remove galls from cedars"),
                        recommendedTreatment = listOf(
                            Treatment("Rally 40WSP", "Myclobutanil", "1.5 g/L", "Spray at bud break")
                        ),
                        precautions = listOf("Wear protective gear", "Avoid spraying during bloom"),
                        naturalRemedies = listOf("Sulfur spray"),
                        weatherBasedAdvice = listOf("Apply during dry weather"),
                        expectedRecoveryTime = "14–28 days",
                        signsOfImprovement = listOf("No new rust spots", "Healthy leaf growth")
                    )
                    "🥒 Bitter Gourd - Downy Mildew" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Downy Mildew",
                        quickAdvice = listOf("Improve air circulation", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "डाउनी मिल्ड्यू",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Yellow spots on upper leaves", "White mold on undersides"),
                        immediateAction = listOf("Remove infected leaves", "Avoid overhead watering"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying near harvest", "Use protective gear"),
                        naturalRemedies = listOf("Copper-based spray"),
                        weatherBasedAdvice = listOf("Avoid wet conditions"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new growth")
                    )
                    "🥒 Bitter Gourd - Jassid" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Jassid Infestation",
                        quickAdvice = listOf("Use sticky traps", "Apply Imidacloprid"),
                        diseaseNameLocalized = "जैसिड",
                        transmissionMethod = TransmissionMethod.INSECT,
                        commonSymptoms = listOf("Curling leaves", "Yellowing at edges"),
                        immediateAction = listOf("Remove affected leaves", "Introduce natural predators"),
                        recommendedTreatment = listOf(
                            Treatment("Confidor", "Imidacloprid", "0.5 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying during flowering", "Wear masks"),
                        naturalRemedies = listOf("Neem oil", "Soap water spray"),
                        weatherBasedAdvice = listOf("Spray early morning"),
                        expectedRecoveryTime = "5–10 days",
                        signsOfImprovement = listOf("Reduced insect presence", "New healthy leaves")
                    )
                    "🥒 Bitter Gourd - Leaf Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Copper fungicide"),
                        diseaseNameLocalized = "पत्ती धब्बा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Brown-black spots", "Yellowing around spots"),
                        immediateAction = listOf("Improve spacing", "Destroy infected debris"),
                        recommendedTreatment = listOf(
                            Treatment("Kocide 3000", "Copper hydroxide", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid overhead irrigation", "Wear gloves"),
                        naturalRemedies = listOf("Baking soda spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy leaf growth")
                    )
                    "🥒 Bitter Gourd - Nitrogen Deficiency" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Nitrogen Deficiency",
                        quickAdvice = listOf("Apply nitrogen fertilizer", "Use compost"),
                        diseaseNameLocalized = "नाइट्रोजन की कमी",
                        transmissionMethod = TransmissionMethod.NUTRIENT,
                        commonSymptoms = listOf("Yellowing older leaves", "Stunted growth"),
                        immediateAction = listOf("Soil test", "Apply urea or ammonium nitrate"),
                        recommendedTreatment = listOf(
                            Treatment("Urea", "Nitrogen", "20 g/plant", "Apply to soil")
                        ),
                        precautions = listOf("Avoid over-fertilization", "Water after application"),
                        naturalRemedies = listOf("Compost tea", "Manure"),
                        weatherBasedAdvice = listOf("Apply before rain"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("Greener leaves", "Improved growth")
                    )
                    "🥒 Bitter Gourd - Nitrogen & Magnesium Deficiency" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Nitrogen & Magnesium Deficiency",
                        quickAdvice = listOf("Apply balanced fertilizer", "Use Epsom salt"),
                        diseaseNameLocalized = "नाइट्रोजन और मैग्नीशियम की कमी",
                        transmissionMethod = TransmissionMethod.NUTRIENT,
                        commonSymptoms = listOf("Yellowing leaves", "Interveinal chlorosis"),
                        immediateAction = listOf("Soil test", "Apply magnesium sulfate"),
                        recommendedTreatment = listOf(
                            Treatment("Epsom Salt", "Magnesium sulfate", "10 g/L", "Foliar spray")
                        ),
                        precautions = listOf("Test soil pH", "Avoid overuse"),
                        naturalRemedies = listOf("Compost", "Seaweed extract"),
                        weatherBasedAdvice = listOf("Apply in mild weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("Greener leaves", "Reduced chlorosis")
                    )
                    "🥒 Bitter Gourd - Nitrogen & Potassium Deficiency" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Nitrogen & Potassium Deficiency",
                        quickAdvice = listOf("Apply NPK fertilizer", "Use potassium sulfate"),
                        diseaseNameLocalized = "नाइट्रोजन और पोटैशियम की कमी",
                        transmissionMethod = TransmissionMethod.NUTRIENT,
                        commonSymptoms = listOf("Yellowing leaves", "Weak stems"),
                        immediateAction = listOf("Soil test", "Apply balanced fertilizer"),
                        recommendedTreatment = listOf(
                            Treatment("Potassium sulfate", "Potassium", "15 g/plant", "Apply to soil")
                        ),
                        precautions = listOf("Avoid excessive potassium", "Monitor plant response"),
                        naturalRemedies = listOf("Wood ash", "Compost"),
                        weatherBasedAdvice = listOf("Apply before irrigation"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("Stronger stems", "Greener leaves")
                    )
                    "🥒 Bitter Gourd - Potassium Deficiency" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Potassium Deficiency",
                        quickAdvice = listOf("Apply potassium fertilizer", "Use compost"),
                        diseaseNameLocalized = "पोटैशियम की कमी",
                        transmissionMethod = TransmissionMethod.NUTRIENT,
                        commonSymptoms = listOf("Yellowing leaf edges", "Weak fruit development"),
                        immediateAction = listOf("Soil test", "Apply potassium sulfate"),
                        recommendedTreatment = listOf(
                            Treatment("Potassium sulfate", "Potassium", "15 g/plant", "Apply to soil")
                        ),
                        precautions = listOf("Avoid over-application", "Water after application"),
                        naturalRemedies = listOf("Banana peel compost", "Wood ash"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("Improved fruit quality", "Greener leaves")
                    )
                    "🥒 Bitter Gourd - Potassium & Magnesium Deficiency" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Potassium & Magnesium Deficiency",
                        quickAdvice = listOf("Apply balanced fertilizer", "Use Epsom salt"),
                        diseaseNameLocalized = "पोटैशियम और मैग्नीशियम की कमी",
                        transmissionMethod = TransmissionMethod.NUTRIENT,
                        commonSymptoms = listOf("Yellowing leaf edges", "Interveinal chlorosis"),
                        immediateAction = listOf("Soil test", "Apply magnesium sulfate"),
                        recommendedTreatment = listOf(
                            Treatment("Epsom Salt", "Magnesium sulfate", "10 g/L", "Foliar spray")
                        ),
                        precautions = listOf("Monitor soil pH", "Avoid overuse"),
                        naturalRemedies = listOf("Compost", "Seaweed extract"),
                        weatherBasedAdvice = listOf("Apply in mild weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("Greener leaves", "Reduced chlorosis")
                    )
                    "🍒 Cherry - Powdery Mildew" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Powdery Mildew",
                        quickAdvice = listOf("Improve air circulation", "Apply Sulfur"),
                        diseaseNameLocalized = "पाउडरी मिल्ड्यू",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("White powdery spots", "Leaf curling"),
                        immediateAction = listOf("Prune dense foliage", "Remove infected parts"),
                        recommendedTreatment = listOf(
                            Treatment("Microthiol Disperss", "Sulfur", "3 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying in high heat", "Wear protective gear"),
                        naturalRemedies = listOf("Milk spray", "Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new white spots", "Healthy leaf growth")
                    )
                    "🌽 Corn - Cercospora Leaf Spot / Gray Leaf Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Cercospora Leaf Spot",
                        quickAdvice = listOf("Rotate crops", "Apply Azoxystrobin"),
                        diseaseNameLocalized = "सर्कोस्पोरा पत्ती धब्बा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Grayish-white lesions", "Yellowing leaves"),
                        immediateAction = listOf("Remove crop debris", "Improve field drainage"),
                        recommendedTreatment = listOf(
                            Treatment("Quadris", "Azoxystrobin", "0.5 mL/L", "Spray at tasseling")
                        ),
                        precautions = listOf("Rotate fungicides", "Avoid spraying near harvest"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply before rain"),
                        expectedRecoveryTime = "14–21 days",
                        signsOfImprovement = listOf("No new lesions", "Healthy new leaves")
                    )
                    "🌽 Corn - Common Rust" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Common Rust",
                        quickAdvice = listOf("Plant resistant varieties", "Apply Propiconazole"),
                        diseaseNameLocalized = "सामान्य रस्ट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Orange pustules on leaves", "Reduced photosynthesis"),
                        immediateAction = listOf("Remove infected leaves", "Improve air circulation"),
                        recommendedTreatment = listOf(
                            Treatment("Tilt", "Propiconazole", "1 mL/L", "Spray at early infection")
                        ),
                        precautions = listOf("Avoid overhead irrigation", "Wear gloves"),
                        naturalRemedies = listOf("Sulfur spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new pustules", "Healthy leaf growth")
                    )
                    "🌽 Corn - Northern Leaf Blight" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Northern Leaf Blight",
                        quickAdvice = listOf("Rotate crops", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "उत्तरी पत्ती झुलसा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Gray-green lesions", "Wilting leaves"),
                        immediateAction = listOf("Remove infected debris", "Improve field drainage"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray at early symptoms")
                        ),
                        precautions = listOf("Rotate fungicides", "Avoid spraying near harvest"),
                        naturalRemedies = listOf("Copper-based spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "14–21 days",
                        signsOfImprovement = listOf("No new lesions", "Healthy new leaves")
                    )
                    "🍆 Eggplant - Aphids" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Aphid Infestation",
                        quickAdvice = listOf("Use insecticidal soap", "Introduce ladybugs"),
                        diseaseNameLocalized = "एफिड्स",
                        transmissionMethod = TransmissionMethod.INSECT,
                        commonSymptoms = listOf("Sticky leaves", "Curling leaves"),
                        immediateAction = listOf("Spray with water", "Remove heavily infested parts"),
                        recommendedTreatment = listOf(
                            Treatment("Safer Soap", "Potassium salts", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying during flowering", "Test on small area"),
                        naturalRemedies = listOf("Neem oil", "Garlic spray"),
                        weatherBasedAdvice = listOf("Apply in early morning"),
                        expectedRecoveryTime = "5–10 days",
                        signsOfImprovement = listOf("Reduced aphid presence", "Healthy new growth")
                    )
                    "🍆 Eggplant - Cercospora Leaf Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Cercospora Leaf Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Mancozeb"),
                        diseaseNameLocalized = "सर्कोस्पोरा पत्ती धब्बा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Grayish spots", "Yellow halos"),
                        immediateAction = listOf("Improve spacing", "Destroy infected debris"),
                        recommendedTreatment = listOf(
                            Treatment("Dithane M-45", "Mancozeb", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid overhead watering", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy leaf growth")
                    )
                    "🍆 Eggplant - Flea Beetles" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Flea Beetle Infestation",
                        quickAdvice = listOf("Use row covers", "Apply Spinosad"),
                        diseaseNameLocalized = "पिस्सू भृंग",
                        transmissionMethod = TransmissionMethod.INSECT,
                        commonSymptoms = listOf("Small holes in leaves", "Skeletonized leaves"),
                        immediateAction = listOf("Remove weeds", "Use sticky traps"),
                        recommendedTreatment = listOf(
                            Treatment("Entrust", "Spinosad", "1 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying near pollinators", "Wear protective gear"),
                        naturalRemedies = listOf("Diatomaceous earth", "Neem oil"),
                        weatherBasedAdvice = listOf("Apply in early morning"),
                        expectedRecoveryTime = "5–10 days",
                        signsOfImprovement = listOf("Reduced beetle presence", "Healthy new leaves")
                    )
                    "🍆 Eggplant - Leaf Wilt" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Wilt",
                        quickAdvice = listOf("Improve drainage", "Apply Trichoderma"),
                        diseaseNameLocalized = "पत्ती मुरझाना",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Drooping leaves", "Brown roots"),
                        immediateAction = listOf("Remove infected plants", "Sterilize soil"),
                        recommendedTreatment = listOf(
                            Treatment("Trichoderma", "Trichoderma spp.", "10 g/kg soil", "Apply to soil")
                        ),
                        precautions = listOf("Avoid waterlogging", "Use clean tools"),
                        naturalRemedies = listOf("Compost tea"),
                        weatherBasedAdvice = listOf("Avoid overwatering"),
                        expectedRecoveryTime = "14–21 days",
                        signsOfImprovement = listOf("Firm leaves", "Healthy root growth")
                    )
                    "🍆 Eggplant - Phytophthora Blight" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Phytophthora Blight",
                        quickAdvice = listOf("Improve drainage", "Apply Mefenoxam"),
                        diseaseNameLocalized = "फाइटोफ्थोरा ब्लाइट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Dark stem lesions", "Wilting plants"),
                        immediateAction = listOf("Remove infected plants", "Avoid waterlogging"),
                        recommendedTreatment = listOf(
                            Treatment("Ridomil Gold", "Mefenoxam", "1 mL/L", "Drench soil")
                        ),
                        precautions = listOf("Rotate crops", "Wear protective gear"),
                        naturalRemedies = listOf("Copper sulfate"),
                        weatherBasedAdvice = listOf("Avoid wet conditions"),
                        expectedRecoveryTime = "14–28 days",
                        signsOfImprovement = listOf("No new lesions", "Healthy new growth")
                    )
                    "🍆 Eggplant - Powdery Mildew" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Powdery Mildew",
                        quickAdvice = listOf("Improve air circulation", "Apply Sulfur"),
                        diseaseNameLocalized = "पाउडरी मिल्ड्यू",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("White powdery spots", "Yellowing leaves"),
                        immediateAction = listOf("Prune dense foliage", "Remove infected parts"),
                        recommendedTreatment = listOf(
                            Treatment("Microthiol Disperss", "Sulfur", "3 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying in high heat", "Wear protective gear"),
                        naturalRemedies = listOf("Milk spray", "Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new white spots", "Healthy leaf growth")
                    )
                    "🍆 Eggplant - Tobacco Mosaic Virus" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Tobacco Mosaic Virus",
                        quickAdvice = listOf("Remove infected plants", "Sanitize tools"),
                        diseaseNameLocalized = "तंबाकू मोज़ेक वायरस",
                        transmissionMethod = TransmissionMethod.VIRAL,
                        commonSymptoms = listOf("Mottled leaves", "Stunted growth"),
                        immediateAction = listOf("Destroy infected plants", "Avoid tobacco products"),
                        recommendedTreatment = listOf(), // No chemical treatment for viruses
                        precautions = listOf("Wash hands", "Use clean tools"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor during warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    "🍇 Grape - Black Rot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Black Rot",
                        quickAdvice = listOf("Remove mummified fruit", "Apply Myclobutanil"),
                        diseaseNameLocalized = "काली सड़न",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Black lesions on fruit", "Leaf spots"),
                        immediateAction = listOf("Prune infected parts", "Sanitize tools"),
                        recommendedTreatment = listOf(
                            Treatment("Rally 40WSP", "Myclobutanil", "1.5 g/L", "Spray at bloom")
                        ),
                        precautions = listOf("Avoid overhead watering", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "14–21 days",
                        signsOfImprovement = listOf("No new lesions", "Healthy fruit development")
                    )
                    "🍇 Grape - Esca (Black Measles)" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Esca (Black Measles)",
                        quickAdvice = listOf("Prune infected vines", "Improve vine health"),
                        diseaseNameLocalized = "एस्का (काली खसरा)",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Tiger-striped leaves", "Black spots on berries"),
                        immediateAction = listOf("Remove infected wood", "Avoid wounding vines"),
                        recommendedTreatment = listOf(), // No effective chemical treatment
                        precautions = listOf("Sterilize pruning tools", "Monitor older vines"),
                        naturalRemedies = listOf("Compost for vine vigor"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; manage symptoms",
                        signsOfImprovement = listOf("Reduced symptom spread")
                    )
                    "🍇 Grape - Leaf Blight (Isariopsis Leaf Spot)" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Blight",
                        quickAdvice = listOf("Remove infected leaves", "Apply Captan"),
                        diseaseNameLocalized = "पत्ती झुलसा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Dark spots on leaves", "Premature leaf drop"),
                        immediateAction = listOf("Improve air circulation", "Destroy debris"),
                        recommendedTreatment = listOf(
                            Treatment("Captan WP", "Captan", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid wet foliage", "Wear protective gear"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy leaf growth")
                    )
                    "🥬 Lettuce - Bacterial Infection" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Bacterial Infection",
                        quickAdvice = listOf("Remove infected plants", "Apply Copper"),
                        diseaseNameLocalized = "बैक्टीरियल संक्रमण",
                        transmissionMethod = TransmissionMethod.BACTERIAL,
                        commonSymptoms = listOf("Water-soaked spots", "Rotting leaves"),
                        immediateAction = listOf("Avoid overhead watering", "Destroy infected plants"),
                        recommendedTreatment = listOf(
                            Treatment("Kocide 3000", "Copper hydroxide", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Use clean tools", "Avoid working in wet conditions"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new infections", "Healthy new growth")
                    )
                    "🥬 Lettuce - Fungal Infection" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Fungal Infection",
                        quickAdvice = listOf("Improve air circulation", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "फंगल संक्रमण",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("White mold", "Yellowing leaves"),
                        immediateAction = listOf("Remove infected leaves", "Improve spacing"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid wet foliage", "Wear gloves"),
                        naturalRemedies = listOf("Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new mold", "Healthy leaf growth")
                    )
                    "🍊 Orange - Huanglongbing (Citrus Greening)" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Huanglongbing (Citrus Greening)",
                        quickAdvice = listOf("Remove infected trees", "Control psyllids"),
                        diseaseNameLocalized = "सिट्रस ग्रीनिंग",
                        transmissionMethod = TransmissionMethod.BACTERIAL,
                        commonSymptoms = listOf("Yellowing shoots", "Bitter fruit"),
                        immediateAction = listOf("Destroy infected trees", "Apply Imidacloprid"),
                        recommendedTreatment = listOf(
                            Treatment("Confidor", "Imidacloprid", "0.5 mL/L", "Spray for psyllids")
                        ),
                        precautions = listOf("Monitor for psyllids", "Use clean nursery stock"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    "🍈 Papaya - Anthracnose Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Anthracnose",
                        quickAdvice = listOf("Remove infected fruit", "Apply Mancozeb"),
                        diseaseNameLocalized = "एन्थ्रेक्नोज",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Dark sunken spots on fruit", "Leaf spots"),
                        immediateAction = listOf("Destroy infected fruit", "Improve air circulation"),
                        recommendedTreatment = listOf(
                            Treatment("Dithane M-45", "Mancozeb", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid wounding fruit", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy fruit")
                    )
                    "🍈 Papaya - Black Spot Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Black Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "काला धब्बा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Black spots on leaves", "Yellowing"),
                        immediateAction = listOf("Destroy infected debris", "Improve spacing"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid wet foliage", "Wear protective gear"),
                        naturalRemedies = listOf("Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy leaf growth")
                    )
                    "🍈 Papaya - Powdery Mildew Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Powdery Mildew",
                        quickAdvice = listOf("Improve air circulation", "Apply Sulfur"),
                        diseaseNameLocalized = "पाउडरी मिल्ड्यू",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("White powdery spots", "Leaf distortion"),
                        immediateAction = listOf("Prune dense foliage", "Remove infected parts"),
                        recommendedTreatment = listOf(
                            Treatment("Microthiol Disperss", "Sulfur", "3 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying in high heat", "Wear protective gear"),
                        naturalRemedies = listOf("Milk spray", "Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new white spots", "Healthy leaf growth")
                    )
                    "🍈 Papaya - Ring Spot Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Ring Spot Virus",
                        quickAdvice = listOf("Remove infected plants", "Control aphids"),
                        diseaseNameLocalized = "रिंग स्पॉट वायरस",
                        transmissionMethod = TransmissionMethod.VIRAL,
                        commonSymptoms = listOf("Ring-shaped spots", "Stunted growth"),
                        immediateAction = listOf("Destroy infected plants", "Remove weeds"),
                        recommendedTreatment = listOf(
                            Treatment("Confidor", "Imidacloprid", "0.5 mL/L", "Spray for aphids")
                        ),
                        precautions = listOf("Use clean tools", "Monitor for aphids"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    "🍈 Papaya - Phytophthora Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Phytophthora",
                        quickAdvice = listOf("Improve drainage", "Apply Mefenoxam"),
                        diseaseNameLocalized = "फाइटोफ्थोरा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Root rot", "Wilting plants"),
                        immediateAction = listOf("Remove infected plants", "Avoid waterlogging"),
                        recommendedTreatment = listOf(
                            Treatment("Ridomil Gold", "Mefenoxam", "1 mL/L", "Drench soil")
                        ),
                        precautions = listOf("Rotate crops", "Wear protective gear"),
                        naturalRemedies = listOf("Copper sulfate"),
                        weatherBasedAdvice = listOf("Avoid wet conditions"),
                        expectedRecoveryTime = "14–28 days",
                        signsOfImprovement = listOf("No new wilting", "Healthy new growth")
                    )
                    "🍑 Peach - Bacterial Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Bacterial Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Copper"),
                        diseaseNameLocalized = "बैक्टीरियल धब्बा",
                        transmissionMethod = TransmissionMethod.BACTERIAL,
                        commonSymptoms = listOf("Dark spots on fruit", "Leaf drop"),
                        immediateAction = listOf("Prune infected parts", "Avoid overhead watering"),
                        recommendedTreatment = listOf(
                            Treatment("Kocide 3000", "Copper hydroxide", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Use clean tools", "Avoid wet conditions"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy fruit")
                    )
                    "🫑 Bell Pepper - Bacterial Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Bacterial Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Copper"),
                        diseaseNameLocalized = "बैक्टीरियल धब्बा",
                        transmissionMethod = TransmissionMethod.BACTERIAL,
                        commonSymptoms = listOf("Water-soaked spots", "Yellow halos"),
                        immediateAction = listOf("Destroy infected debris", "Avoid overhead watering"),
                        recommendedTreatment = listOf(
                            Treatment("Kocide 3000", "Copper hydroxide", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Use clean tools", "Avoid wet foliage"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new growth")
                    )
                    "🌿 Pigeon Pea - Leaf Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Mancozeb"),
                        diseaseNameLocalized = "पत्ती धब्बा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Brown spots", "Yellowing leaves"),
                        immediateAction = listOf("Improve spacing", "Destroy infected debris"),
                        recommendedTreatment = listOf(
                            Treatment("Dithane M-45", "Mancozeb", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid overhead watering", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy leaf growth")
                    )
                    "🌿 Pigeon Pea - Leaf Webber" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Webber Infestation",
                        quickAdvice = listOf("Remove webbed leaves", "Apply Spinosad"),
                        diseaseNameLocalized = "पत्ती वेबर",
                        transmissionMethod = TransmissionMethod.INSECT,
                        commonSymptoms = listOf("Webbed leaves", "Skeletonized leaves"),
                        immediateAction = listOf("Remove infested leaves", "Use sticky traps"),
                        recommendedTreatment = listOf(
                            Treatment("Entrust", "Spinosad", "1 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying near pollinators", "Wear protective gear"),
                        naturalRemedies = listOf("Neem oil", "Diatomaceous earth"),
                        weatherBasedAdvice = listOf("Apply in early morning"),
                        expectedRecoveryTime = "5–10 days",
                        signsOfImprovement = listOf("Reduced webbing", "Healthy new leaves")
                    )
                    "🌿 Pigeon Pea - Sterility Mosaic" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Sterility Mosaic",
                        quickAdvice = listOf("Remove infected plants", "Control mites"),
                        diseaseNameLocalized = "बांझपन मोज़ेक",
                        transmissionMethod = TransmissionMethod.VIRAL,
                        commonSymptoms = listOf("Stunted plants", "No pod formation"),
                        immediateAction = listOf("Destroy infected plants", "Apply Acaricide"),
                        recommendedTreatment = listOf(
                            Treatment("Oberon", "Spiromesifen", "0.5 mL/L", "Spray for mites")
                        ),
                        precautions = listOf("Use clean tools", "Monitor for mites"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    "🥔 Potato - Early Blight" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Early Blight",
                        quickAdvice = listOf("Remove infected leaves", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "अर्ली ब्लाइट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Concentric leaf spots", "Yellowing"),
                        immediateAction = listOf("Destroy infected debris", "Improve air circulation"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Rotate crops", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new growth")
                    )
                    "🥔 Potato - Late Blight" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Late Blight",
                        quickAdvice = listOf("Remove infected plants", "Apply Mancozeb"),
                        diseaseNameLocalized = "लेट ब्लाइट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Water-soaked lesions", "White mold"),
                        immediateAction = listOf("Destroy infected plants", "Avoid wet foliage"),
                        recommendedTreatment = listOf(
                            Treatment("Dithane M-45", "Mancozeb", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Monitor weather", "Wear protective gear"),
                        naturalRemedies = listOf("Copper sulfate"),
                        weatherBasedAdvice = listOf("Apply before rain"),
                        expectedRecoveryTime = "14–21 days",
                        signsOfImprovement = listOf("No new lesions", "Healthy new growth")
                    )
                    "🍓 Strawberry - Leaf Scorch" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Scorch",
                        quickAdvice = listOf("Remove infected leaves", "Apply Captan"),
                        diseaseNameLocalized = "पत्ती झुलसन",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Dark purple spots", "Drying leaves"),
                        immediateAction = listOf("Improve air circulation", "Destroy infected debris"),
                        recommendedTreatment = listOf(
                            Treatment("Captan WP", "Captan", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid overhead watering", "Wear gloves"),
                        naturalRemedies = listOf("Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy leaf growth")
                    )
                    "🎃 Squash - Powdery Mildew" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Powdery Mildew",
                        quickAdvice = listOf("Improve air circulation", "Apply Sulfur"),
                        diseaseNameLocalized = "पाउडरी मिल्ड्यू",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("White powdery spots", "Yellowing leaves"),
                        immediateAction = listOf("Prune dense foliage", "Remove infected parts"),
                        recommendedTreatment = listOf(
                            Treatment("Microthiol Disperss", "Sulfur", "3 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying in high heat", "Wear protective gear"),
                        naturalRemedies = listOf("Milk spray", "Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new white spots", "Healthy leaf growth")
                    )
                    "🎃 Sweet Pumpkin - Downy Mildew Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Downy Mildew",
                        quickAdvice = listOf("Improve air circulation", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "डाउनी मिल्ड्यू",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Yellow spots", "Grayish mold on undersides"),
                        immediateAction = listOf("Remove infected leaves", "Avoid overhead watering"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid wet foliage", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy leaf growth")
                    )
                    "🎃 Sweet Pumpkin - Leaf Curl Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Curl Virus",
                        quickAdvice = listOf("Remove infected plants", "Control whiteflies"),
                        diseaseNameLocalized = "पत्ती कर्ल वायरस",
                        transmissionMethod = TransmissionMethod.VIRAL,
                        commonSymptoms = listOf("Curling leaves", "Stunted growth"),
                        immediateAction = listOf("Destroy infected plants", "Use yellow sticky traps"),
                        recommendedTreatment = listOf(
                            Treatment("Confidor", "Imidacloprid", "0.5 mL/L", "Spray for whiteflies")
                        ),
                        precautions = listOf("Monitor for whiteflies", "Use clean tools"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    "🎃 Sweet Pumpkin - Mosaic Disease" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Mosaic Virus",
                        quickAdvice = listOf("Remove infected plants", "Control aphids"),
                        diseaseNameLocalized = "मोज़ेक वायरस",
                        transmissionMethod = TransmissionMethod.VIRAL,
                        commonSymptoms = listOf("Mottled leaves", "Distorted fruit"),
                        immediateAction = listOf("Destroy infected plants", "Remove weeds"),
                        recommendedTreatment = listOf(
                            Treatment("Confidor", "Imidacloprid", "0.5 mL/L", "Spray for aphids")
                        ),
                        precautions = listOf("Use clean tools", "Monitor for aphids"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    "🎃 Sweet Pumpkin - Red Beetle" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Red Beetle Infestation",
                        quickAdvice = listOf("Hand-pick beetles", "Apply Spinosad"),
                        diseaseNameLocalized = "लाल भृंग",
                        transmissionMethod = TransmissionMethod.INSECT,
                        commonSymptoms = listOf("Chewed leaves", "Holes in fruit"),
                        immediateAction = listOf("Remove beetles manually", "Use sticky traps"),
                        recommendedTreatment = listOf(
                            Treatment("Entrust", "Spinosad", "1 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying near pollinators", "Wear gloves"),
                        naturalRemedies = listOf("Diatomaceous earth", "Neem oil"),
                        weatherBasedAdvice = listOf("Apply in early morning"),
                        expectedRecoveryTime = "5–10 days",
                        signsOfImprovement = listOf("Reduced beetle presence", "Healthy new growth")
                    )
                    "🍅 Tomato - Bacterial Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Bacterial Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Copper"),
                        diseaseNameLocalized = "बैक्टीरियल धब्बा",
                        transmissionMethod = TransmissionMethod.BACTERIAL,
                        commonSymptoms = listOf("Dark spots with yellow halos", "Leaf drop"),
                        immediateAction = listOf("Destroy infected debris", "Avoid overhead watering"),
                        recommendedTreatment = listOf(
                            Treatment("Kocide 3000", "Copper hydroxide", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Use clean tools", "Avoid wet foliage"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new growth")
                    )
                    "🍅 Tomato - Early Blight" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Early Blight",
                        quickAdvice = listOf("Remove infected leaves", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "अर्ली ब्लाइट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Concentric leaf spots", "Yellowing"),
                        immediateAction = listOf("Destroy infected debris", "Improve air circulation"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Rotate crops", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new growth")
                    )
                    "🍅 Tomato - Late Blight" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Late Blight",
                        quickAdvice = listOf("Remove infected plants", "Apply Mancozeb"),
                        diseaseNameLocalized = "लेट ब्लाइट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Water-soaked lesions", "White mold"),
                        immediateAction = listOf("Destroy infected plants", "Avoid wet foliage"),
                        recommendedTreatment = listOf(
                            Treatment("Dithane M-45", "Mancozeb", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Monitor weather", "Wear protective gear"),
                        naturalRemedies = listOf("Copper sulfate"),
                        weatherBasedAdvice = listOf("Apply before rain"),
                        expectedRecoveryTime = "14–21 days",
                        signsOfImprovement = listOf("No new lesions", "Healthy new growth")
                    )
                    "🍅 Tomato - Leaf Mold" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Leaf Mold",
                        quickAdvice = listOf("Improve ventilation", "Apply Chlorothalonil"),
                        diseaseNameLocalized = "पत्ती मोल्ड",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Yellow spots on upper leaves", "Gray mold on undersides"),
                        immediateAction = listOf("Remove infected leaves", "Reduce humidity"),
                        recommendedTreatment = listOf(
                            Treatment("Daconil", "Chlorothalonil", "2 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid overhead watering", "Wear gloves"),
                        naturalRemedies = listOf("Neem oil"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "7–14 days",
                        signsOfImprovement = listOf("No new mold", "Healthy leaf growth")
                    )
                    "🍅 Tomato - Septoria Leaf Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Septoria Leaf Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Mancozeb"),
                        diseaseNameLocalized = "सेप्टोरिया पत्ती धब्बा",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Grayish-white spots", "Yellowing leaves"),
                        immediateAction = listOf("Destroy infected debris", "Improve air circulation"),
                        recommendedTreatment = listOf(
                            Treatment("Dithane M-45", "Mancozeb", "2 g/L", "Spray foliage")
                        ),
                        precautions = listOf("Rotate crops", "Wear protective gear"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new growth")
                    )
                    "🍅 Tomato - Spider Mites (Two-Spotted)" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Spider Mite Infestation",
                        quickAdvice = listOf("Spray with water", "Apply Abamectin"),
                        diseaseNameLocalized = "स्पाइडर माइट",
                        transmissionMethod = TransmissionMethod.INSECT,
                        commonSymptoms = listOf("Speckled leaves", "Fine webbing"),
                        immediateAction = listOf("Increase humidity", "Remove heavily infested leaves"),
                        recommendedTreatment = listOf(
                            Treatment("Avid", "Abamectin", "0.5 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Avoid spraying in heat", "Wear protective gear"),
                        naturalRemedies = listOf("Neem oil", "Insecticidal soap"),
                        weatherBasedAdvice = listOf("Apply in early morning"),
                        expectedRecoveryTime = "5–10 days",
                        signsOfImprovement = listOf("Reduced mite presence", "Healthy new leaves")
                    )
                    "🍅 Tomato - Target Spot" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Target Spot",
                        quickAdvice = listOf("Remove infected leaves", "Apply Azoxystrobin"),
                        diseaseNameLocalized = "टारगेट स्पॉट",
                        transmissionMethod = TransmissionMethod.FUNGAL,
                        commonSymptoms = listOf("Concentric spots", "Leaf drop"),
                        immediateAction = listOf("Destroy infected debris", "Improve air circulation"),
                        recommendedTreatment = listOf(
                            Treatment("Quadris", "Azoxystrobin", "0.5 mL/L", "Spray foliage")
                        ),
                        precautions = listOf("Rotate fungicides", "Wear gloves"),
                        naturalRemedies = listOf("Copper spray"),
                        weatherBasedAdvice = listOf("Apply in dry weather"),
                        expectedRecoveryTime = "10–14 days",
                        signsOfImprovement = listOf("No new spots", "Healthy new growth")
                    )
                    "🍅 Tomato - Yellow Leaf Curl Virus" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Yellow Leaf Curl Virus",
                        quickAdvice = listOf("Remove infected plants", "Control whiteflies"),
                        diseaseNameLocalized = "पीला पत्ती कर्ल वायरस",
                        transmissionMethod = TransmissionMethod.VIRAL,
                        commonSymptoms = listOf("Yellowing, curling leaves", "Stunted growth"),
                        immediateAction = listOf("Destroy infected plants", "Use yellow sticky traps"),
                        recommendedTreatment = listOf(
                            Treatment("Confidor", "Imidacloprid", "0.5 mL/L", "Spray for whiteflies")
                        ),
                        precautions = listOf("Monitor for whiteflies", "Use clean tools"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    "🍅 Tomato - Mosaic Virus" -> CropDetail(
                        documentId = sanitize(label),
                        cropName = cropWithEmoji,
                        predictedDisease = "Mosaic Virus",
                        quickAdvice = listOf("Remove infected plants", "Sanitize tools"),
                        diseaseNameLocalized = "मोज़ेक वायरस",
                        transmissionMethod = TransmissionMethod.VIRAL,
                        commonSymptoms = listOf("Mottled leaves", "Distorted growth"),
                        immediateAction = listOf("Destroy infected plants", "Avoid tobacco products"),
                        recommendedTreatment = listOf(), // No chemical treatment for viruses
                        precautions = listOf("Wash hands", "Use clean tools"),
                        naturalRemedies = listOf("None effective"),
                        weatherBasedAdvice = listOf("Monitor in warm weather"),
                        expectedRecoveryTime = "Not curable; prevent spread",
                        signsOfImprovement = listOf("No new infections")
                    )
                    else -> {
                        // Fallback — should never hit with complete labels
                        CropDetail(
                            documentId = sanitize(label),
                            cropName = cropWithEmoji,
                            predictedDisease = diseaseOrHealthy,
                            quickAdvice = listOf("Contact local extension for guidance")
                        )
                    }
                }
            }
        }
    )

    // 2) Write them all to Firestore
    detailsMap.forEach { (docId, detail) ->
        col.document(docId)
            .set(detail, SetOptions.merge())
            .addOnSuccessListener { Log.d("Firestore", "Seeded $docId") }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error seeding $docId", e)
            }
    }
}

// --- Your labels ---
private fun getLabels() = listOf(
    "🍎 Apple - Apple Scab",
    "🍎 Apple - Black Rot",
    "🍎 Apple - Cedar Apple Rust",
    "🍎 Apple - Healthy",
    "🥒 Bitter Gourd - Downy Mildew",
    "🥒 Bitter Gourd - Healthy",
    "🥒 Bitter Gourd - Jassid",
    "🥒 Bitter Gourd - Leaf Spot",
    "🥒 Bitter Gourd - Nitrogen Deficiency",
    "🥒 Bitter Gourd - Nitrogen & Magnesium Deficiency",
    "🥒 Bitter Gourd - Nitrogen & Potassium Deficiency",
    "🥒 Bitter Gourd - Potassium Deficiency",
    "🥒 Bitter Gourd - Potassium & Magnesium Deficiency",
    "🫐 Blueberry - Healthy",
    "🍒 Cherry - Powdery Mildew",
    "🍒 Cherry - Healthy",
    "🌽 Corn - Cercospora Leaf Spot / Gray Leaf Spot",
    "🌽 Corn - Common Rust",
    "🌽 Corn - Northern Leaf Blight",
    "🌽 Corn - Healthy",
    "🍆 Eggplant - Aphids",
    "🍆 Eggplant - Cercospora Leaf Spot",
    "🍆 Eggplant - Flea Beetles",
    "🍆 Eggplant - Healthy",
    "🍆 Eggplant - Leaf Wilt",
    "🍆 Eggplant - Phytophthora Blight",
    "🍆 Eggplant - Powdery Mildew",
    "🍆 Eggplant - Tobacco Mosaic Virus",
    "🍇 Grape - Black Rot",
    "🍇 Grape - Esca (Black Measles)",
    "🍇 Grape - Leaf Blight (Isariopsis Leaf Spot)",
    "🍇 Grape - Healthy",
    "🥬 Lettuce - Bacterial Infection",
    "🥬 Lettuce - Fungal Infection",
    "🥬 Lettuce - Healthy",
    "🍊 Orange - Huanglongbing (Citrus Greening)",
    "🍈 Papaya - Anthracnose Disease",
    "🍈 Papaya - Black Spot Disease",
    "🍈 Papaya - Healthy",
    "🍈 Papaya - Powdery Mildew Disease",
    "🍈 Papaya - Ring Spot Disease",
    "🍈 Papaya - Phytophthora Disease",
    "🍑 Peach - Bacterial Spot",
    "🍑 Peach - Healthy",
    "🫑 Bell Pepper - Bacterial Spot",
    "🫑 Bell Pepper - Healthy",
    "🌿 Pigeon Pea - Healthy",
    "🌿 Pigeon Pea - Leaf Spot",
    "🌿 Pigeon Pea - Leaf Webber",
    "🌿 Pigeon Pea - Sterility Mosaic",
    "🥔 Potato - Early Blight",
    "🥔 Potato - Late Blight",
    "🥔 Potato - Healthy",
    "🍓 Strawberry - Leaf Scorch",
    "🍓 Strawberry - Healthy",
    "🫘 Soybean - Healthy",
    "🎃 Squash - Powdery Mildew",
    "🎃 Sweet Pumpkin - Downy Mildew Disease",
    "🎃 Sweet Pumpkin - Healthy",
    "🎃 Sweet Pumpkin - Leaf Curl Disease",
    "🎃 Sweet Pumpkin - Mosaic Disease",
    "🎃 Sweet Pumpkin - Red Beetle",
    "🍅 Tomato - Bacterial Spot",
    "🍅 Tomato - Early Blight",
    "🍅 Tomato - Late Blight",
    "🍅 Tomato - Leaf Mold",
    "🍅 Tomato - Septoria Leaf Spot",
    "🍅 Tomato - Spider Mites (Two-Spotted)",
    "🍅 Tomato - Target Spot",
    "🍅 Tomato - Yellow Leaf Curl Virus",
    "🍅 Tomato - Mosaic Virus",
    "🍅 Tomato - Healthy"
)