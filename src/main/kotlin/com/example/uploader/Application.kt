package com.example.uploader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.PathMatchConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

@EnableWebFlux
@SpringBootApplication
class Application : WebFluxConfigurer {
    override fun configurePathMatching(configurer: PathMatchConfigurer) {
        configurer.setUseTrailingSlashMatch(true)
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
