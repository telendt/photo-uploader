package com.example.uploader

import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Dummy interface for reactive key-value repository with auto-generated long keys
 */
interface ReactiveRepository<T> {
    fun <S : T> add(valueGen: (key: Long) -> S): Mono<S>
    fun get(key: Long): Mono<T>
}

// TODO: switch to (reactive) spring-data?
@Repository
class InMemoryRepository<T> : ReactiveRepository<T> {
    private val inc = AtomicLong(1)
    protected val storage = ConcurrentHashMap<Long, T>()

    override fun <S : T> add(valueGen: (key: Long) -> S): Mono<S> {
        val key = inc.getAndIncrement()
        val value = valueGen(key)
        storage[key] = value
        return Mono.just(value)
    }

    override fun get(key: Long): Mono<T> {
        val value = storage[key]
        return if (value == null) Mono.empty() else Mono.just(value)
    }
}