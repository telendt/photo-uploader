package com.example.uploader

import com.example.uploader.config.AwsProperties
import com.example.uploader.photo.model.Photo
import com.example.uploader.photo.model.RequestPhotoParams
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import software.amazon.awssdk.core.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.config.SdkAdvancedClientOption
import software.amazon.awssdk.core.internal.auth.NoOpSignerProvider
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.net.URL

@AutoConfigureWebTestClient(timeout = "3000")
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {
    @TestConfiguration
    class TestConfig {
        @Bean(initMethod = "start", destroyMethod = "shutdown")
        fun mockWebServer() = MockWebServer()

        @Bean
        fun exifClient(server: MockWebServer) = WebClient.create(server.url("/").toString())

        @Bean
        fun s3asyncClient(awsProperties: AwsProperties, server: MockWebServer): S3AsyncClient {
            // default signer causes NPE for HTTP requests
            // https://github.com/aws/aws-sdk-java-v2/issues/437
            val overrideConfiguration = ClientOverrideConfiguration.builder()
                    .advancedOption(SdkAdvancedClientOption.SIGNER_PROVIDER, NoOpSignerProvider())
                    .build()
            // setup s3 client and point it to out mock server
            return S3AsyncClient.builder()
                    .endpointOverride(server.url("/").uri())
                    .overrideConfiguration(overrideConfiguration)
                    .credentialsProvider(awsProperties)
                    .region(awsProperties.region)
                    .advancedConfiguration(awsProperties.getS3AdvancedConfiguration())
                    .build()
        }
    }

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var photoRepository: ReactiveRepository<Photo>

    @Autowired
    private lateinit var mockServer: MockWebServer

    private fun prepareResponse(consumer: (resp: MockResponse) -> Unit) {
        val response = MockResponse()
        consumer(response)
        mockServer.enqueue(response)
    }

    private fun expectRequest(consumer: (resp: RecordedRequest) -> Unit) {
        consumer(mockServer.takeRequest())
    }

    @Test
    fun getBadValue() {
        webClient.get().uri("/photo/-1").exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.status").isEqualTo(HttpStatus.BAD_REQUEST.value())
                .jsonPath("$.error").isEqualTo("get.id: must be greater than or equal to 1")
    }

    @Test
    fun getMissing() {
        given(photoRepository.get(1)).willReturn(Mono.empty())
        webClient.get().uri("/photo/1").exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.status").isEqualTo(HttpStatus.NOT_FOUND.value())
                .jsonPath("$.error").isEqualTo("photo not found")
    }

    @Test
    fun getNoExif() {
        val photo = Photo(id = 1, user = 2, description = "XYZ", url = URL("http://example.com"))
        given(photoRepository.get(1)).willReturn(Mono.just(photo))

        prepareResponse { it.setResponseCode(404) }

        webClient.get().uri("/photo/1").exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.user").isEqualTo(2)
                .jsonPath("$.description").isEqualTo("XYZ")
                .jsonPath("$.url").isEqualTo("http://example.com")
                .jsonPath("$.exif").doesNotExist()

        expectRequest { request ->
            assertThat(request.path).isEqualTo("/exif/1")
        }
    }

    @Test
    fun getExifOk() {
        val photo = Photo(id = 1, user = 2, description = "XYZ", url = URL("http://example.com"))
        given(photoRepository.get(1)).willReturn(Mono.just(photo))

        prepareResponse {
            it.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
                    .setResponseCode(200)
                    .setBody("""{
                        "dateTime": "2009-07-30T08:31:44.683387+00:00",
                        "exposureTime": 500,
                        "fNumber": 7.9,
                        "orientation": 0
                    }""".trimIndent())
        }

        webClient.get().uri("/photo/1").exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.user").isEqualTo(2)
                .jsonPath("$.description").isEqualTo("XYZ")
                .jsonPath("$.url").isEqualTo("http://example.com")
                .jsonPath("$.exif").exists()

        expectRequest { request ->
            assertThat(request.path).isEqualTo("/exif/1")
        }
    }

    @Disabled("disabled until learned how to mock s3 PUT object response")
    @Test
    fun addOk() {
        prepareResponse {
            it.setSocketPolicy(SocketPolicy.EXPECT_CONTINUE)
                    .setResponseCode(200)
                    .setHeader("x-amz-id-2", "LriYPLdmOdAiIfgSm/F1YsViT1LW94/xUQxMsF7xiEb1a0wiIOIxl+zbwZ163pt7")
                    .setHeader("x-amz-request-id", "0A49CE4060975EAC")
                    .setHeader("x-amz-version-id", "43jfkodU8493jnFJD9fjj3HHNVfdsQUIFDNsidf038jfdsjGFDSIRp")
                    .setHeader("Date", "Wed, 12 Oct 2009 17:50:00 GMT")
                    .setHeader("ETag", "fbacf535f27731c9771645a39863328")
                    .setHeader("Content-Length", "0")
                    .setHeader("Connection", "close")
                    .setHeader("Server", "AmazonS3")

        }

        val bodyInserter = BodyInserters.fromMultipartData(
                "json", RequestPhotoParams(9, "description")
        ).with("photo", object : ByteArrayResource("PHOTO_CONTENT".toByteArray()) {
            override fun getFilename(): String {
                return "test.jpg"
            }
        })

        webClient.post().uri("/photo").body(bodyInserter).exchange().expectStatus().isOk
    }
}
