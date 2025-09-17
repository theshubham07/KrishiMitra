package com.example.krishimitra.Fragments

import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import coil.load
import com.example.krishimitra.CropDetailActivity
import com.example.krishimitra.MainActivity
import com.example.krishimitra.R
import com.example.krishimitra.User
import com.example.krishimitra.common.uploadImage
import com.example.krishimitra.databinding.FragmentProfileBinding
import com.example.krishimitra.fullscreen_image
import com.example.krishimitra.models.CropCaptureModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ProfileFragment : Fragment() {
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var binding: FragmentProfileBinding
    private lateinit var db: FirebaseFirestore
    private val PICK_IMAGE_REQUEST = 1
    private var userphoto: String? =
        "https://firebasestorage.googleapis.com/v0/b/nemo-app-c188b.appspot.com/o/Profile_photos%2Fprofile_pic.jpg?alt=media&token=b46f9062-ea6a-42c5-8718-97c08277eeb2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ai: ApplicationInfo = requireContext().packageManager
            .getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(ai.metaData["default_web_client_id"].toString())
            .requestEmail()
            .build()

        mGoogleSignInClient = context?.let { GoogleSignIn.getClient(it, gso) }!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(layoutInflater)
        db = FirebaseFirestore.getInstance()

        val user = auth.currentUser
        val userId = user?.uid

        userId?.let {
            db.collection("users").document(it).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val userData = document.toObject(User::class.java)
                        if (userData != null) {
                            binding.tvName.text = userData.name
                            binding.tvEmail.text = userData.email
                            userphoto = userData.prf_pic
                            binding.profileImage.load(userphoto)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Some Error Occurred", Toast.LENGTH_SHORT).show()
                }

            db.collection("Crop_path").whereEqualTo("capturedBy", userId).count()
                .get(AggregateSource.SERVER)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val snapshot = task.result.count.toString()
                        binding.tvCropNum.text = snapshot
                        Log.d(TAG, "Count: $snapshot")
                    } else {
                        Log.d(TAG, "Count failed: ", task.exception)
                    }
                }

            db.collection("Crop_path")
                .whereEqualTo("capturedBy", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        binding.noCaughtYet.visibility = View.GONE
                        binding.recentCaughtCard.visibility = View.VISIBLE
                        binding.logoutPng.visibility = View.VISIBLE

                        val document = querySnapshot.documents[0]
                        val cropData = document.toObject(CropCaptureModel::class.java)

                        if (cropData != null) {
                            binding.cropname.text = cropData.predictedDiseaseName
                            binding.location.text = cropData.captureLocation
                            binding.dateandtime.text = cropData.timestamp
                            binding.cropimage.load(cropData.cropImageUrl) {
                                crossfade(true)
                                crossfade(100)
                                placeholder(R.drawable.loader)
                            }

                            binding.recentCaughtCard.setOnClickListener {
                                val intent = Intent(context, CropDetailActivity::class.java)

                                val docId = cropData.predictedDiseaseName
                                    ?.substringAfter(" ") // Remove emoji and everything before first space
                                    ?.replace(Regex("[^A-Za-z0-9]+"), "_") // Replace non-alphanumerics with underscore
                                    ?.replace(Regex("_+"), "_") // Collapse multiple underscores into one
                                    ?.trim('_') // Remove leading/trailing underscores

                                intent.putExtra("docId", docId)
                                intent.putExtra("predictedDiseaseName", cropData.predictedDiseaseName)
                                intent.putExtra("location", cropData.captureLocation)
                                intent.putExtra("captured_on", cropData.timestamp)
                                intent.putExtra("cropimage", cropData.cropImageUrl)
                                startActivity(intent)
                            }
                        }
                    } else {
                        binding.recentCaughtCard.visibility = View.GONE
                        binding.noCaughtYet.visibility = View.VISIBLE
                        binding.logoutPng.visibility = View.VISIBLE
                        Log.d("Firestore", "No documents found")
                    }

                    binding.lineardetails.visibility = View.VISIBLE
                    binding.profileImage.visibility = View.VISIBLE
                    binding.prfChange.visibility = View.VISIBLE
                    binding.loader.visibility = View.GONE
                }
                .addOnFailureListener { exception ->
                    Log.e("Firestore", "Error getting documents: ", exception)
                    Toast.makeText(context, "There is some error in fetching", Toast.LENGTH_LONG).show()
                }
        }

        binding.logoutText.setOnClickListener {
            mGoogleSignInClient.signOut().addOnCompleteListener {
                val intent = Intent(context, MainActivity::class.java)
                Toast.makeText(context, "Logging Out", Toast.LENGTH_SHORT).show()
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.prfChange.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        binding.profileImage.setOnClickListener {
            val intent = Intent(context, fullscreen_image::class.java)
            intent.putExtra("imageResId", userphoto)
            startActivity(intent)
        }

        return binding.root
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.data != null) {
            val imageUri: Uri? = data.data
            binding.profileImage.setImageURI(imageUri)

            uploadImage("Profile_photos", imageUri) { it, imageUrl ->
                if (it) {
                    db = FirebaseFirestore.getInstance()
                    val user = auth.currentUser
                    val userId = user?.uid
                    userId?.let {
                        db.collection("users").document(it).update("prf_pic", imageUrl)
                            .addOnSuccessListener {
                                Log.d(TAG, "DocumentSnapshot successfully updated!")
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Error updating document", e)
                            }
                    }
                } else {
                    Toast.makeText(context, "Error uploading image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
