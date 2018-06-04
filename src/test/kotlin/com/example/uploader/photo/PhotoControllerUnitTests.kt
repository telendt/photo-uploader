package com.example.uploader.photo

import com.example.uploader.*
import com.example.uploader.photo.model.ExifMetadata
import com.example.uploader.photo.model.Photo
import com.example.uploader.photo.model.RequestPhotoParams
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.BDDMockito.*
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.io.IOException
import java.net.URL
import java.time.OffsetDateTime
import java.util.concurrent.TimeoutException
import java.util.stream.Stream


@ExtendWith(SpringExtension::class)
class PhotoControllerUnitTests {
    private companion object {
        private val TEST_PHOTO = Photo(id = 1, user = 2, description = "XYZ", url = URL("http://example.com"))

        @JvmStatic
        fun throwableProvider(): Stream<Arguments> {
            return Stream.of(
                    Arguments.of(RuntimeException("unexpected"), HttpStatus.INTERNAL_SERVER_ERROR, "unexpected"),
                    Arguments.of(RuntimeException(), HttpStatus.INTERNAL_SERVER_ERROR, null),
                    Arguments.of(TimeoutException("timed out"), HttpStatus.GATEWAY_TIMEOUT, "timed out"),
                    Arguments.of(TimeoutException(), HttpStatus.GATEWAY_TIMEOUT, null),
                    Arguments.of(ResponseStatusException(HttpStatus.NOT_FOUND, "photo not found"), HttpStatus.NOT_FOUND, "photo not found"),
                    Arguments.of(ResponseStatusException(HttpStatus.I_AM_A_TEAPOT), HttpStatus.I_AM_A_TEAPOT, null),
                    Arguments.of(IllegalArgumentException("illegal"), HttpStatus.BAD_REQUEST, "illegal"),
                    Arguments.of(IllegalArgumentException(), HttpStatus.BAD_REQUEST, null)
            )
        }

        @JvmStatic
        fun exifThrowableProvider(): Stream<Throwable> {
            return Stream.of(
                    ResponseException(HttpStatus.NOT_FOUND),
                    ResponseException(HttpStatus.BAD_REQUEST),
                    ResponseException(HttpStatus.INTERNAL_SERVER_ERROR),
                    RuntimeException("runtime")
            )
        }
    }

    @Mock(stubOnly = true)
    private lateinit var uploadService: UploadService

    @Mock(stubOnly = true)
    private lateinit var exifClient: WebClient

    private lateinit var photoRepository: InMemoryRepositoryStub<Photo>

    private lateinit var photoController: PhotoController

    @BeforeEach
    fun setUp() {
        photoRepository = InMemoryRepositoryStub()
        photoController = PhotoController(uploadService, exifClient, photoRepository)
    }

    @ParameterizedTest(name = "#{index} \"{0}\": expected status={1}, expected msg={2}")
    @MethodSource("throwableProvider")
    fun testHandleThrowable(throwable: Throwable, expectedHttpStatus: HttpStatus, expectedErrorMsg: String?) {
        val responseEntity = photoController.handleThrowable(throwable)
        assertThat(responseEntity.statusCode)
                .describedAs("check status code")
                .isEqualTo(expectedHttpStatus)
        assertThat(responseEntity.body!!.status)
                .describedAs("check status (body)")
                .isEqualTo(expectedHttpStatus.value())
        assertThat(responseEntity.body!!.error)
                .describedAs("check error message (body)")
                .isEqualTo(expectedErrorMsg)
    }


    @Test
    fun getMissing() {
        assertEqualValuesPublished(photoController.get(1), PhotoController.PHOTO_NOT_FOUND_MONO)
    }

    @ParameterizedTest(name = "#{index} {0}")
    @MethodSource("exifThrowableProvider")
    fun getExifErr(throwable: Throwable) {
        photoRepository.storageStub(24L to TEST_PHOTO)

        val requestHeadersUriSpec = mock(RequestHeadersUriSpec::class.java, RETURNS_SELF)
        val responseSpec = mock(ResponseSpec::class.java, RETURNS_SELF)

        given(exifClient.get())
                .willReturn(requestHeadersUriSpec)
        given(requestHeadersUriSpec.retrieve())
                .willReturn(responseSpec)
        given(responseSpec.bodyToMono(ExifMetadata::class.java))
                .willReturn(Mono.error(throwable))

        assertEqualValuesPublished(photoController.get(24), Mono.just(TEST_PHOTO))
    }

    @Test
    fun getExifOk() {
        val exifMetadata = ExifMetadata(OffsetDateTime.now(), 500.0, 7.9, 0)
        photoRepository.storageStub(24L to TEST_PHOTO)

        val requestHeadersUriSpec = mock(RequestHeadersUriSpec::class.java, RETURNS_SELF)
        val responseSpec = mock(ResponseSpec::class.java, RETURNS_SELF)

        given(exifClient.get())
                .willReturn(requestHeadersUriSpec)
        given(requestHeadersUriSpec.retrieve())
                .willReturn(responseSpec)
        given(responseSpec.bodyToMono(ExifMetadata::class.java))
                .willReturn(Mono.just(exifMetadata))

        assertEqualValuesPublished(photoController.get(24), Mono.just(TEST_PHOTO.copy(exif = exifMetadata)))
    }

    @Test
    fun addFailure() {
        val filePart = FakePhotoFilePart()
        val err = IOException("upload failed")

        given(uploadService.upload(anyString(), safeSame(filePart))).willReturn(Mono.error<URL>(err))

        val actual = photoController.add(RequestPhotoParams(123, "description"), filePart)
        assertEqualValuesPublished(actual, Mono.error<Photo>(err))
    }

    @Test
    fun addSuccess() {
        val filePart = FakePhotoFilePart()
        val url = URL("http://example.com")

        given(uploadService.upload(anyString(), safeSame(filePart))).willReturn(Mono.just(url))

        val actual = photoController.add(RequestPhotoParams(123, "description"), filePart)
        assertEqualValuesPublished(actual, Mono.just(Photo(
                id = 1,
                user = 123,
                description = "description",
                url = url,
                exif = null
        )))
    }
}