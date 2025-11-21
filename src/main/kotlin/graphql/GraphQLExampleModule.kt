package graphql

import RepairJob
import RepairJobCreateRequest
import RepairJobUpdateRequest
import RepairService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// NOTE:
// This file is a self-contained EXAMPLE GraphQL module showing how to expose your existing
// RepairService as GraphQL, WITHOUT changing any of your existing files.
// You can compare REST vs GraphQL side-by-side.
//
// To try it, manually call `GraphQLExampleModule.installGraphQL(exampleApp)`
// from your Application.module, but that is OPTIONAL and not done automatically here.

/**
 * GraphQL entry for your domain.
 *
 * We reuse existing DTOs from Model.kt:
 *  - RepairJob
 *  - RepairJobCreateRequest
 *  - RepairJobUpdateRequest
 * and existing service:
 *  - RepairService
 *
 * For clarity, this example implements a very small, manual GraphQL handler that supports:
 *  - query { repairs { ... } }
 *  - query { repair(id: X) { ... } }
 *  - mutation { createRepair(input: { ... }) { ... } }
 *  - mutation { updateRepair(id: X, input: { ... }) { ... } }
 *  - mutation { deleteRepair(id: X) }
 *
 * This is not a full GraphQL implementation; it's intentionally minimal so you can see:
 *  - What classes are involved
 *  - How it wires to your existing service
 *  - How client requests differ from REST.
 */

// ---------------------
// Public entry point
// ---------------------

object GraphQLExampleModule {

    /**
     * Call this from Application.module() if you want to enable the /graphql endpoint:
     *
     *     fun Application.module() {
     *         // your existing REST + config...
     *
     *         val repo = FileRepo("src/main/resources/repairs.json")
     *         val service = RepairService(repo)
     *
     *         GraphQLExampleModule.installGraphQL(this, service)
     *     }
     *
     * This method does NOT modify your existing REST routes.
     */
    fun installGraphQL(application: Application, service: RepairService) {
        val handler = SimpleGraphQLHandler(RepairQuery(service), RepairMutation(service))

        application.routing {
            post("/graphql") {
                // Expect: { "query": "...", "variables": { ... } }
                val bodyText = call.receiveText()
                val result = handler.handleRequest(bodyText)
                call.respond(result)
            }
        }
    }
}

// ---------------------
// Query & Mutation API
// ---------------------

/**
 * GraphQL "Query" type.
 *
 * Maps to:
 *  - GET /repairs
 *  - GET /repairs/{id}
 */
class RepairQuery(
    private val service: RepairService
) {
    suspend fun repairs(): List<RepairJob> = service.getAll()

    suspend fun repair(id: Int): RepairJob? = service.getById(id)
}

/**
 * GraphQL "Mutation" type.
 *
 * Maps to:
 *  - POST /repairs
 *  - PUT /repairs/{id}
 *  - DELETE /repairs/{id}
 */
class RepairMutation(
    private val service: RepairService
) {
    suspend fun createRepair(input: RepairJobCreateRequest): RepairJob =
        service.create(input)

    suspend fun updateRepair(id: Int, input: RepairJobUpdateRequest): RepairJob? =
        service.update(id, input)

    suspend fun deleteRepair(id: Int): Boolean =
        service.delete(id)
}

// ---------------------
// Very small GraphQL-style handler (for demo)
// ---------------------

/**
 * A minimal handler that:
 *  - Parses JSON body
 *  - Reads "query" and "variables"
 *  - Supports a few hard-coded operations for demonstration.
 *
 * This is NOT a production-ready GraphQL engine.
 * It exists so you can see end-to-end flow without touching existing code.
 */
class SimpleGraphQLHandler(
    private val queryApi: RepairQuery,
    private val mutationApi: RepairMutation
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handleRequest(body: String): Map<String, Any?> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return error("Invalid JSON: ${e.message}")
        }

        val queryText = root["query"]?.jsonPrimitive?.content ?: return error("Missing 'query'")
        val variables = root["variables"]?.jsonObject ?: JsonObject(emptyMap())

        return when {
            // query { repairs { ... } }
            queryText.contains("repairs") && queryText.trim().startsWith("query") -> {
                val list = queryApi.repairs()
                data(mapOf("repairs" to list))
            }

            // query with variable: query ($id: Int!) { repair(id: $id) { ... } }
            queryText.contains("repair(") && queryText.contains("query") -> {
                val id = extractIdFromVariablesOrInline(queryText, variables)
                    ?: return error("Missing 'id' for repair query")
                val item = queryApi.repair(id)
                data(mapOf("repair" to item))
            }

            // mutation: createRepair
            queryText.contains("mutation") && queryText.contains("createRepair") -> {
                val input = extractInput<RepairJobCreateRequest>(variables)
                    ?: return error("Missing 'input' for createRepair")
                val created = mutationApi.createRepair(input)
                data(mapOf("createRepair" to created))
            }

            // mutation: updateRepair
            queryText.contains("mutation") && queryText.contains("updateRepair") -> {
                val id = extractIdFromVariablesOrInline(queryText, variables)
                    ?: return error("Missing 'id' for updateRepair")
                val input = extractInput<RepairJobUpdateRequest>(variables)
                    ?: return error("Missing 'input' for updateRepair")
                val updated = mutationApi.updateRepair(id, input)
                data(mapOf("updateRepair" to updated))
            }

            // mutation: deleteRepair
            queryText.contains("mutation") && queryText.contains("deleteRepair") -> {
                val id = extractIdFromVariablesOrInline(queryText, variables)
                    ?: return error("Missing 'id' for deleteRepair")
                val ok = mutationApi.deleteRepair(id)
                data(mapOf("deleteRepair" to ok))
            }

            else -> {
                error("Unsupported or unrecognized GraphQL-like query for this demo")
            }
        }
    }

    private fun data(payload: Map<String, Any?>): Map<String, Any?> =
        mapOf("data" to payload)

    private fun error(message: String): Map<String, Any?> =
        mapOf("errors" to listOf(mapOf("message" to message)))

    private fun extractIdFromVariablesOrInline(queryText: String, variables: JsonObject): Int? {
        // Try variable: { "variables": { "id": 1 } }
        variables["id"]?.jsonPrimitive?.intOrNull?.let { return it }

        // Very naive inline parse: repair(id: 1)
        val regex = Regex("""repair\s*\(\s*id\s*:\s*(\d+)\s*\)""")
        val match = regex.find(queryText)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private inline fun <reified T> extractInput(variables: JsonObject): T? {
        val inputNode = variables["input"] ?: return null
        return try {
            json.decodeFromJsonElement<T>(inputNode)
        } catch (e: Exception) {
            null
        }
    }
}

// ---------------------
// Client-side usage (for comparison)
// ---------------------

/*
REST (existing):
----------------
GET  /repairs
GET  /repairs/{id}
POST /repairs
PUT  /repairs/{id}
DELETE /repairs/{id}

Example:
  GET http://localhost:8080/repairs

GraphQL (this module, if enabled):
----------------------------------
Single endpoint:
  POST http://localhost:8080/graphql
Body: { "query": "...", "variables": { ... } }

Examples:

1) Get all repairs
------------------
POST /graphql
{
  "query": "query { repairs { id customerName phoneModel status } }"
}

2) Get one repair
-----------------
POST /graphql
{
  "query": "query ($id: Int!) { repair(id: $id) { id customerName issue status } }",
  "variables": { "id": 1 }
}

3) Create repair
----------------
POST /graphql
{
  "query": "mutation ($input: RepairJobCreateRequest!) { createRepair(input: $input) { id customerName status } }",
  "variables": {
    "input": {
      "customerName": "John",
      "phoneModel": "iPhone 15",
      "issue": "Screen broken",
      "status": "PENDING",
      "priceEstimate": 200.0
    }
  }
}

4) Update repair
----------------
POST /graphql
{
  "query": "mutation ($id: Int!, $input: RepairJobUpdateRequest!) { updateRepair(id: $id, input: $input) { id status } }",
  "variables": {
    "id": 1,
    "input": {
      "status": "COMPLETED"
    }
  }
}

5) Delete repair
----------------
POST /graphql
{
  "query": "mutation ($id: Int!) { deleteRepair(id: $id) }",
  "variables": { "id": 1 }
}

This file allows you to see all required wiring in one place, while keeping your original REST implementation untouched.
*/