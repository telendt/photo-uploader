package com.example.uploader

import org.assertj.core.api.Assertions
import org.reactivestreams.Publisher
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Signal
import reactor.test.test
import java.io.File
import java.io.IOException

/**
 * Verifies that the values produces by actual source publisher are equal to the ones produced by the second one.
 *
 * Please not that if both publishers infinite and publish equal values in equal order this function will block forever.
 * Use {@code predicate} to decide when to stop subscription in such case.
 * <p>
 * Example:
 * <pre><code class='kotlin'> // assertions will pass
 * assertEqualValuesPublished(Flux.just(1, 2, 3), Flux.just(1, 2, 3));
 * assertEqualValuesPublished(Flux.just(1), Mono.just(1))
 *
 * // assertions will fail
 * assertEqualValuesPublished(Flux.just(1, 2, 3), Flux.just(1, 2, 2));
 * assertEqualValuesPublished(Flux.just(1, 2, 3), Flux.just(1, 2));
 * assertEqualValuesPublished(Flux.just(1, 2, 3), Flux.just(1, 2, 3).concatWith(Mono.error(RuntimeException("fail"))));
 * </code></pre>
 *
 * @param actualSource    the actual publisher source
 * @param expectedSource  the given publisher source to compare the actual publisher source to.
 * @param predicate       the condition to continue consuming onNext
 *
 * @throws AssertionError if any value published by actual source is not equal to value published by expected source
 * at the same step.
 */
fun <T> assertEqualValuesPublished(actualSource: Publisher<T>, expectedSource: Publisher<T>,
                                   predicate: (Long, Signal<T>, Signal<T>) -> Boolean = { _, _, _ -> true }) {
    Flux.zip(
            Flux.from(actualSource).materialize(),
            Flux.from(expectedSource).materialize()
    ).index().test().thenConsumeWhile({
        predicate(it.t1, it.t2.t1, it.t2.t2)
    }, {
        val i = it.t1
        val (actual, expected) = it.t2.toArray()
        Assertions.assertThat(actual)
                .describedAs("check #%d value", i + 1)
                .isEqualTo(expected)
    }).thenCancel().verify()
}

/**
 * Subclass of WebClientResponseException that contain given HTTP status.
 */
class ResponseException(status: HttpStatus) :
        WebClientResponseException(
                "ClientResponse has erroneous status code: ${status.value()} ${status.reasonPhrase}",
                status.value(),
                status.reasonPhrase,
                null,
                null,
                null)


class FakePhotoFilePart(
        private val mediaType: MediaType = MediaType.IMAGE_JPEG,
        private val copySuccess: Boolean = true) : FilePart {

    override fun content() = Flux.just(DefaultDataBufferFactory().wrap("ferfe".toByteArray(Charsets.UTF_8)))

    override fun headers(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = mediaType
        return HttpHeaders.readOnlyHttpHeaders(headers)
    }

    override fun filename() = "filename.jpg"

    override fun name() = "photo"

    override fun transferTo(dest: File): Mono<Void> = if (copySuccess) Mono.empty() else Mono.error(IOException())
}

