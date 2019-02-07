package resource

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.*
import service.UserService


fun Route.user(userService: UserService) {
    route("/users") {
        get("/") {
            call.respond(mapOf("OK" to true))
        }
    }

    val mapper = jacksonObjectMapper().apply {
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }
}
