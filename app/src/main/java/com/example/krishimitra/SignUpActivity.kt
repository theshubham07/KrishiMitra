package com.example.krishimitra

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.krishimitra.common.uploadImage
import com.example.krishimitra.databinding.ActivitySignUpBinding
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
import com.google.firebase.ktx.Firebase
import com.mikhaellopez.circularimageview.CircularImageView

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val PICK_IMAGE_REQUEST = 1
    private lateinit var profileImageView: CircularImageView
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private val Req_Code: Int = 123
    private val usermodel = User()

    override fun onStart() {
        super.onStart()
        if (GoogleSignIn.getLastSignedInAccount(this) != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.theme)

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()
        profileImageView = findViewById(R.id.prf_pic)

        val ai: ApplicationInfo = applicationContext.packageManager
            .getApplicationInfo(applicationContext.packageName, PackageManager.GET_META_DATA)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(ai.metaData["default_web_client_id"].toString())
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.prfPic.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        binding.signupButton.setOnClickListener { handleSignUp() }

        binding.loginBtn1.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        binding.loginWithGoogleBtn.setOnClickListener {
            val signInIntent = mGoogleSignInClient.signInIntent
            startActivityForResult(signInIntent, Req_Code)
        }

        val passwordEt = binding.confirmPasswordEt

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

    private fun handleSignUp() {
        binding.loader.visibility = View.VISIBLE
        val firstName = binding.firstNameEt.text.toString()
        val lastName = binding.lastNameEt.text.toString()
        val email = binding.emailEt.text.toString()
        val password = binding.passwordEt.text.toString()
        val confirmPassword = binding.confirmPasswordEt.text.toString()

        if (firstName.isEmpty() || !firstName.matches(Regex("[a-zA-Z ]+"))) {
            binding.loader.visibility = View.GONE
            binding.firstNameEt.error = "Invalid First Name"
            return
        }

        if (lastName.isEmpty() || !lastName.matches(Regex("[a-zA-Z ]+"))) {
            binding.loader.visibility = View.GONE
            binding.lastNameEt.error = "Invalid Last Name"
            return
        }

        if (email.isEmpty() || !isValidEmail(email)) {
            binding.loader.visibility = View.GONE
            binding.emailEt.error = "Invalid Email"
            return
        }

        if (password.isEmpty() || !password.matches(Regex("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,20}$"))) {
            binding.loader.visibility = View.GONE
            binding.passwordEt.error = "Invalid Password"
            return
        }

        if (confirmPassword.isEmpty() || confirmPassword != password) {
            binding.loader.visibility = View.GONE
            binding.confirmPasswordEt.error = "Passwords Don't Match"
            return
        }

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val userId = user?.uid
                usermodel.name = "$firstName $lastName"
                usermodel.email = email

                userId?.let {
                    db.collection("users").document(it).set(usermodel)
                        .addOnSuccessListener {
                            binding.loader.visibility = View.GONE
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            binding.loader.visibility = View.GONE
                            Toast.makeText(this, "Error Occurred", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                binding.loader.visibility = View.GONE
                Toast.makeText(this, task.exception?.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            val imageUri: Uri? = data.data
            profileImageView.setImageURI(imageUri)

            uploadImage("Profile_photos", imageUri) { success, imageUrl ->
                if (success) {
                    usermodel.prf_pic = imageUrl
                } else {
                    Toast.makeText(this, "Error uploading image", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (requestCode == Req_Code) {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    handleGoogleSignInSuccess(account)
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In Failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun handleGoogleSignInSuccess(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                val userId = user?.uid
                usermodel.name = account.displayName ?: ""
                usermodel.email = account.email ?: ""
                usermodel.prf_pic = account.photoUrl.toString()

                userId?.let {
                    db.collection("users").document(it).get().addOnSuccessListener { document ->
                        if (!document.exists()) {
                            db.collection("users").document(it).set(usermodel)
                        }
                    }
                }

                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, task.exception?.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
