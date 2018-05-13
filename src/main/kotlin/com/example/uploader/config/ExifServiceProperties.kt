package com.example.uploader.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.net.URL
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "exif", ignoreUnknownFields = false)
// @ConfigurationProperties currently does not work with data classes
// https://docs.spring.io/spring-boot/docs/2.0.x/reference/html/boot-features-kotlin.html#boot-features-kotlin-configuration-properties
class ExifServiceProperties {
    lateinit var baseUrl: URL // TODO: validate
    var timeout: Duration = Duration.ofSeconds(1)
}