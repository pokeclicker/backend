package main

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.joda.JodaModule
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.jackson.jackson
import io.ktor.routing.Routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import resource.info
import resource.pokemon
import resource.store
import resource.user
import service.pokemon.PokemonService
import service.store.StoreService
import service.user.UserService
import utility.DatabaseFactory
import utility.ErrorLogger

fun Application.module() {
    install(DefaultHeaders)

    install(CallLogging)

    install(WebSockets)

    install(CORS) {
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        header(HttpHeaders.AccessControlAllowOrigin)
        header(HttpHeaders.Authorization)
        anyHost()
    }

    install(ContentNegotiation) {
        jackson {
            configure(SerializationFeature.INDENT_OUTPUT, true)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            registerModule(JodaModule())
        }
    }

    ErrorLogger.setupSentry()

    DatabaseFactory.init()

    install(Routing) {
        info()
        user(UserService())
        store(StoreService())
        pokemon(PokemonService())
    }
}

fun main() {
    embeddedServer(
        factory = Netty,
        port = System.getenv("backend_port").toInt(),
        watchPaths = listOf("MainKt"),
        module = Application::module
    ).start()
}
