package com.example.uploader.photo.model

import java.net.URL

data class Photo(
        val id: Long,
        val user: Long,
        val description: String,
        val url: URL,
        val exif: ExifMetadata? = null
)