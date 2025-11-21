package org.example

import FileRepo
import RepairJobCreateRequest
import RepairJobUpdateRequest
import RepairService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * embeddedServer - create a embedded ktor server using netty engine
 */
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {

    // ContentNegotiation - It's automatically convert bodies to JSON (we can use other formats also like xml,yaml ect...)
    install(ContentNegotiation) { json() }
    // Controls the domains to make browse requests to the API
    install(CORS) {
        anyHost() // local testing
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
    }

    val repo = FileRepo("src/main/resources/repairs.json")
    val service = RepairService(repo)

    routing {
        route("/repairs") {
            get {
                // serialize List<RepairJob> to JSON
                call.respond(service.getAll())
            }
            post {
                val body = call.receive<RepairJobCreateRequest>()
                call.respond(service.create(body))
            }
            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                val item = id?.let { service.getById(it) }
                val error = RepairResult.NotFound("Invalid Id")
                if (item == null) call.respondText(error.message, status = HttpStatusCode.NotFound)
                else call.respond(item)
            }
            put("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                val body = call.receive<RepairJobUpdateRequest>()
                val updated = id?.let { service.update(it, body) }
                if (updated == null) call.respondText("Not found", status = HttpStatusCode.NotFound)
                else call.respond(updated)
            }
            delete("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                val ok = id?.let { service.delete(it) } ?: false
                if (!ok) call.respondText("Not found", status = HttpStatusCode.NotFound)
                else call.respondText("Deleted")
            }
        }
    }
}
