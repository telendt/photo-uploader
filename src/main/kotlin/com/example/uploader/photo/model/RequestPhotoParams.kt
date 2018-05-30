package com.example.uploader.photo.model

import javax.validation.constraints.Positive
import javax.validation.constraints.Size

data class RequestPhotoParams(
        @field:Positive
        val user: Long,

        @field:Size(max = 1024)
        val description: String
)