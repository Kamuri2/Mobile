package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.local.UserDao
import com.example.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class UserRepository(
    private val context: Context,
    private val userDao: UserDao
) {
    fun getUserProfile(): Flow<UserEntity?> = userDao.getUserProfile()
    
    suspend fun getUserProfileSync(): UserEntity? = userDao.getUserProfileSync()

    suspend fun saveUserProfile(name: String, bio: String, imageUri: Uri?) = withContext(Dispatchers.IO) {
        val existing = userDao.getUserProfileSync()
        
        var finalImagePath = existing?.profileImageUri
        
        if (imageUri != null) {
            // Check if we need to copy a new file (if it's not already our local file)
            if (imageUri.toString() != existing?.profileImageUri) {
                // Delete old if exists
                existing?.profileImageUri?.let { oldUri ->
                    try {
                        val oldFile = File(Uri.parse(oldUri).path!!)
                        if (oldFile.exists()) oldFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Copy new file
                val file = File(context.filesDir, "profile_pic_${System.currentTimeMillis()}.jpg")
                try {
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    finalImagePath = Uri.fromFile(file).toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val newUser = UserEntity(
            name = name,
            bio = bio,
            profileImageUri = finalImagePath
        )
        userDao.saveUserProfile(newUser)
    }
    companion object {
        @Volatile
        private var INSTANCE: UserRepository? = null

        fun getInstance(context: Context): UserRepository {
            return INSTANCE ?: synchronized(this) {
                val db = com.example.data.local.AppDatabase.getDatabase(context)
                val instance = UserRepository(context.applicationContext, db.userDao())
                INSTANCE = instance
                instance
            }
        }
    }
}