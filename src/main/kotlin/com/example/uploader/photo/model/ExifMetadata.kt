package com.example.uploader.photo.model

import java.time.OffsetDateTime

data class ExifMetadata(
        val dateTime: OffsetDateTime,
        val exposureTime: Double,
        val fNumber: Double,
        val orientation: Int
)