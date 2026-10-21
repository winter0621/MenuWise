package com.example.careium.core.database.realtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.GridView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.careium.core.adapters.GridViewAdapter
import com.example.careium.core.database.authentication.LocalAccountStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.component1
import java.time.LocalDateTime
import java.io.File
import java.io.FileOutputStream
import kotlin.collections.ArrayList


class DishImage {
    companion object {

        private var auth: FirebaseAuth = FirebaseAuth.getInstance()
        private lateinit var userid: String

        @RequiresApi(Build.VERSION_CODES.O)
        @JvmStatic
        fun uploadImage(image: Bitmap, uri: Uri) {

            val today =
                "${LocalDateTime.now().dayOfMonth} ${LocalDateTime.now().monthValue}"
            userid = auth.currentUser!!.uid
            val ref =
                FirebaseStorage.getInstance().getReference("foodimages/$userid/$today/$image.jpg")
            ref.putFile(uri)

        }

        @JvmStatic
        fun saveLocalImage(context: Context, image: Bitmap) {
            val day = "${LocalDateTime.now().dayOfMonth} ${LocalDateTime.now().monthValue}"
            val account = LocalAccountStore.currentAccountId(context)
            val folder = File(context.filesDir, "local_food_images/$account/$day").apply { mkdirs() }
            FileOutputStream(File(folder, "${System.currentTimeMillis()}.jpg")).use {
                image.compress(Bitmap.CompressFormat.JPEG, 88, it)
            }
        }

        @SuppressLint("SimpleDateFormat")
        @JvmStatic
        fun fetchImages(
            context: Context,
            gridView: GridView,
            waitContainer: View,
            day: String
        ) {

            if (context.getSharedPreferences("menuwise_local", Context.MODE_PRIVATE).getBoolean("offline_mode", false)) {
                val account = LocalAccountStore.currentAccountId(context)
                val folder = File(context.filesDir, "local_food_images/$account/$day")
                val images = folder.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
                    .mapNotNull { BitmapFactory.decodeFile(it.absolutePath) }
                gridView.adapter = GridViewAdapter(context, ArrayList(images))
                waitContainer.visibility = View.GONE
                if (images.isEmpty()) Toast.makeText(context, "这一天还没有本地照片", Toast.LENGTH_SHORT).show()
                return
            }

            val userDishesImages = ArrayList<Bitmap>()
            val mega = (1024 * 1024 * 5).toLong()

            FirebaseStorage.getInstance().reference.child("foodimages/${auth.currentUser!!.uid}/$day")
                .listAll()
                .addOnSuccessListener { (items) ->
                    if (items.isEmpty()) {
                        waitContainer.visibility = View.GONE
                        Toast.makeText(
                            context,
                            "这一天还没有照片",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    items.forEach { item ->
                        FirebaseStorage.getInstance().reference
                            .child("foodimages/${auth.currentUser!!.uid}/$day/${item.name}")
                            .getBytes(mega).addOnSuccessListener { bytes ->
                                val bmp =
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                userDishesImages.add(bmp)
                                if (userDishesImages.size == 1) {
                                    waitContainer.visibility = View.GONE
                                }
                                val mainAdapter =
                                    GridViewAdapter(context, userDishesImages)
                                gridView.adapter = mainAdapter

                            }.addOnCanceledListener {
                                Toast.makeText(
                                    context,
                                    "照片加载失败，请稍后重试",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
                .addOnCanceledListener {
                    Toast.makeText(
                        context,
                        "照片加载失败，请稍后重试",
                        Toast.LENGTH_LONG
                    ).show()
                }

        }

    }
}
