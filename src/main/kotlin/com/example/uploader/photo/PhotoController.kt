package com.example.uploader.photo

import com.example.uploader.ReactiveRepository
import com.example.uploader.UploadService
import com.example.uploader.photo.model.ErrorResponse
import com.example.uploader.photo.model.ExifMetadata
import com.example.uploader.photo.model.Photo
import com.example.uploader.photo.model.RequestPhotoParams
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.*
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.TimeoutException
import javax.validation.Valid
import javax.validation.ValidationException
import javax.validation.constraints.Min

@RestController
@Validated
@RequestMapping("/photo", produces = [APPLICATION_JSON_UTF8_VALUE])
class PhotoController(private val uploadService: UploadService,
                      private val exifClient: WebClient,
                      private val photoRepository: ReactiveRepository<Photo>) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PhotoController::class.java)

        val PHOTO_NOT_FOUND_MONO = Mono.error<Photo>(ResponseStatusException(HttpStatus.NOT_FOUND, "photo not found"))
    }

    /**
     * Handler for photo upload endpoint. Uploads given photo file to s3, inserts all necessary data to photoRepository
     * and returns {@code Mono} of Photo object or exception.
     *
     * @param metadata
     * @param filePart
     * @return a {@code Mono} of {@code Photo} or exception
     */
    @PostMapping(consumes = [MULTIPART_FORM_DATA_VALUE])
    fun add(@Valid @RequestPart("json") metadata: RequestPhotoParams,
            @RequestPart("photo") filePart: FilePart): Mono<Photo> {

        val uuid = UUID.randomUUID().toString()
        // TODO: map mime-type to file extension with Apache Tika?
        // TODO: only allow certain (image) content-types?
        val filename = uuid + when (filePart.headers().contentType) {
            IMAGE_JPEG -> ".jpg"
            IMAGE_GIF -> ".gif"
            IMAGE_PNG -> ".png"
            else -> ""
        }

        return uploadService.upload(filename, filePart).flatMap { url ->
            photoRepository.add { id ->
                Photo(
                        id = id,
                        user = metadata.user,
                        description = metadata.description,
                        url = url
                )
            }
        }
    }

    /**
     * Handler for photo get endpoint. Returns {@code Mono} of Photo object with optional exif data or
     * ResponseStatusException with 404 status code.
     *
     * @param id
     * @return a {@code Mono} of {@code Photo}
     */
    @GetMapping("/{id}")
    fun get(@PathVariable @Min(1) id: Long): Mono<Photo> {
        return photoRepository.get(id)
                .flatMap { photo ->
                    exifClient.get()
                            .uri("exif/{id}", id)
                            .accept(APPLICATION_JSON)
                            .retrieve()
                            .bodyToMono(ExifMetadata::class.java)
                            .map { photo.copy(exif = it) }
                            .doOnError { t ->
                                when {
                                    t is WebClientResponseException && t.statusCode == HttpStatus.NOT_FOUND ->
                                        logger.info("metadata for photo {} not found", id)
                                    t is WebClientResponseException && t.statusCode.is5xxServerError ->
                                        logger.warn("metadata server error {}", t.message, t)
                                    else -> logger.error("metadata fetch error {}", t.message, t) // bad request, parsing error, etc...
                                }
                            }
                            .onErrorReturn(photo)
                }
                .switchIfEmpty(PHOTO_NOT_FOUND_MONO)
    }

    @ExceptionHandler
    fun handleThrowable(e: Throwable): ResponseEntity<ErrorResponse> {
        val (status, error) = when (e) {
            is ResponseStatusException -> Pair(e.status, e.reason)
            is TimeoutException -> Pair(HttpStatus.GATEWAY_TIMEOUT, e.message)
            is IllegalArgumentException, is ValidationException -> Pair(HttpStatus.BAD_REQUEST, e.message)
            else -> Pair(HttpStatus.INTERNAL_SERVER_ERROR, e.message)
        }
        when {
            status.is4xxClientError -> logger.warn(error)
            status.is5xxServerError -> logger.error(error, e)
        }
        return ResponseEntity(ErrorResponse(status.value(), error), status)
    }
}