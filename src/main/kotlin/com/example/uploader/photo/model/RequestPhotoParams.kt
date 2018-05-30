package com.example.uploader.photo.model

import javax.validation.constraints.Min
import javax.validation.constraints.Size

data class RequestPhotoParams(
        @field:Min(1, message = "user ID should be greater or equal to 1")
        val user: Long,

        @field:Size(max = 1024, message = "description should be no longer than 1024 characters")
        val description: String
)