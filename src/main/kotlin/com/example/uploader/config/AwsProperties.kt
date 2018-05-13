package com.example.uploader.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AdvancedConfiguration

@Component
@ConfigurationProperties(prefix = "aws", ignoreUnknownFields = false)
// @ConfigurationProperties currently does not work with data classes
// https://docs.spring.io/spring-boot/docs/2.0.x/reference/html/boot-features-kotlin.html#boot-features-kotlin-configuration-properties
class AwsProperties : AwsCredentialsProvider {
    lateinit var accessKeyId: String
    lateinit var secretAccessKey: String
    var region: Region? = null
    var s3: S3Config? = null

    class S3Config(
        var dualstackEnabled: Boolean = false,
        var accelerateModeEnabled: Boolean = false,
        var pathStyleAccessEnabled: Boolean = false
    )

    override fun getCredentials() = AwsCredentials.create(accessKeyId, secretAccessKey)!!

    fun getS3AdvancedConfiguration(): S3AdvancedConfiguration? {
        val s3conf = s3
        return if (s3conf == null) {
            null
        } else {
            S3AdvancedConfiguration.builder()
                    .dualstackEnabled(s3conf.dualstackEnabled)
                    .accelerateModeEnabled(s3conf.accelerateModeEnabled)
                    .pathStyleAccessEnabled(s3conf.pathStyleAccessEnabled)
                    .build()
        }
    }
}