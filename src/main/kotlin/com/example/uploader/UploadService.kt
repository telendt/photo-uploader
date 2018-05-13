package com.example.uploader

import org.reactivestreams.Subscriber
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import sun.security.action.GetPropertyAction
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.FileSystem
import java.security.AccessController

class UploadService(
        private val bucketName: String,
        private val keyPrefix: String,
        private val s3client: S3AsyncClient,
        private val fileSystem: FileSystem) {

    fun upload(fileName: String, filePart: FilePart): Mono<URL> {
        return filePartToBodyProvider(fileName, filePart)
                .flatMap { reqBody ->
                    val key = URI("$keyPrefix/").resolve(fileName)
                    val req = PutObjectRequest.builder()
                            .bucket(bucketName)
                            .contentType(filePart.headers().contentType.toString())
                            .key(key.toString())
                            .build()
                    s3client.putObject(req, reqBody).toMono()
                            .thenReturn(URL("http", "$bucketName.s3.amazonaws.com", "/$key"))
                }
    }

    private fun filePartToBodyProvider(fileName: String, filePart: FilePart): Mono<AsyncRequestBody> {
        val fileContentLength = filePart.headers().contentLength
        return when (fileContentLength) {
            -1L -> { // no content length header, need to copy part file to temp file
                val mono = Mono.fromSupplier {
                    // create temp file lazily (only if there's a subscriber)
                    createTempFile(
                            prefix = "upload",
                            suffix = fileName,
                            directory = fileSystem.getPath(
                                    AccessController.doPrivileged(GetPropertyAction("java.io.tmpdir"))).toFile())
                }
                mono.flatMap { tempFile ->
                    filePart.transferTo(tempFile)
                            .doOnSuccess {
                                // after successful transfer register delete finalizer on top-level mono
                                mono.doFinally { tempFile.delete() }
                            }
                            .thenReturn(AsyncRequestBody.fromFile(tempFile.toPath()))
                }
            }

            else -> Mono.just(object : AsyncRequestBody {
                override fun contentLength() = fileContentLength

                override fun subscribe(s: Subscriber<in ByteBuffer>) =
                        filePart.content().map(DataBuffer::asByteBuffer).subscribe(s)
            })
        }
    }
}