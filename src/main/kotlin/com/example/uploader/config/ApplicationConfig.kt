package com.example.uploader.config

import com.example.uploader.UploadServiceImpl
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.nio.file.FileSystems
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal

@Configuration
class ApplicationConfig {
    // a workaround for custom datetime format (exif server)
    // https://github.com/FasterXML/jackson-modules-java8/issues/38#issuecomment-324637805
    private class CustomInstantDeserializer<T : Temporal>(instantDeserializer: InstantDeserializer<T>)
        : InstantDeserializer<T>(instantDeserializer, defaultFormatter) {
        companion object {
            private val defaultFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss['.'[SSS][SS][S]][XXX][XX][X]")
        }
    }

    @Bean
    fun customizeJacksonMapper() = Jackson2ObjectMapperBuilderCustomizer { c ->
        c.deserializerByType(OffsetDateTime::class.java,
                CustomInstantDeserializer(InstantDeserializer.OFFSET_DATE_TIME))
        c.deserializerByType(ZonedDateTime::class.java,
                CustomInstantDeserializer(InstantDeserializer.ZONED_DATE_TIME))
        c.deserializerByType(Instant::class.java,
                CustomInstantDeserializer(InstantDeserializer.INSTANT))
    }

    @Bean
    fun s3asyncClient(awsProperties: AwsProperties) = S3AsyncClient.builder()
            .credentialsProvider(awsProperties)
            .region(awsProperties.region)
            .advancedConfiguration(awsProperties.getS3AdvancedConfiguration())
            .build()!!

    @Bean
    fun exifClient(exifServiceProperties: ExifServiceProperties) = WebClient.builder()
            .baseUrl(exifServiceProperties.baseUrl.toString())
            .filter { request, next -> next.exchange(request).timeout(exifServiceProperties.timeout) }
            .build()

    @Bean
    fun uploadService(uploadProperties: UploadProperties, s3Client: S3AsyncClient) = UploadServiceImpl(
            uploadProperties.bucketName,
            uploadProperties.keyPrefix,
            s3Client,
            FileSystems.getDefault()
    )
}