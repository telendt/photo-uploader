package com.example.uploader.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "upload", ignoreUnknownFields = false)
// @ConfigurationProperties currently does not work with data classes
// https://docs.spring.io/spring-boot/docs/2.0.x/reference/html/boot-features-kotlin.html#boot-features-kotlin-configuration-properties
class UploadProperties {
    lateinit var bucketName: String
    var keyPrefix: String = "code-challenge-${System.getenv("USER")}"
}