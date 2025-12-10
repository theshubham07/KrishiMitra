package com.example.krishimitra.common

import android.net.Uri
import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.util.UUID

const val CROP_IMAGES_FOLDER_PATH = "Crop_images"
const val CROP_PATH = "Crop_path"

fun uploadImage(
    path: String,
    uri: Uri?,
    function: (isSuccessful: Boolean, fileUrl: String) -> Unit
) {

    if (uri != null) {
        Firebase.storage.reference.child("$path/${UUID.randomUUID()}.jpg").putFile(uri)
            .addOnCompleteListener {
                it.result.storage.downloadUrl.addOnSuccessListener {
                    function(true, it.toString())
                }

            }
            .addOnProgressListener {
                Log.d("uploadImage", " ${(it.bytesTransferred / it.totalByteCount) * 100} ")
            }
            .addOnFailureListener {

            }
    }



}