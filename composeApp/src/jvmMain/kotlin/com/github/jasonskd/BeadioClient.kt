package com.github.jasonskd

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse(
    val type: String? = null,
    val message: String? = null,
    val exceptionType: String? = null,
    val data: JsonElement? = null
)

@Serializable
private data class CreatePlanRequest(val sites: List<String>)

@Serializable
private data class FetchVideosRequest(val selection: Map<String, List<Int>>)

@Serializable
private data class PlanNameRequest(val planName: String)

@Serializable
private data class UpdateConfigRequest(val browserChannel: String)

object BeadioClient {
    val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }

    private const val BASE = "http://localhost:8080"

    // Sessions
    suspend fun createSession(siteName: String): ApiResponse =
        http.post("$BASE/sessions/$siteName").body()

    suspend fun getSession(siteName: String): ApiResponse =
        http.get("$BASE/sessions/$siteName").body()

    suspend fun retrySession(siteName: String): ApiResponse =
        http.post("$BASE/sessions/$siteName/retry").body()

    suspend fun deleteSessions() { http.delete("$BASE/sessions") }

    // Plan creation
    suspend fun createPlanManager(sites: List<String>): ApiResponse =
        http.post("$BASE/plans/new") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(CreatePlanRequest(sites))
        }.body()

    suspend fun startCourseCollection(): ApiResponse =
        http.post("$BASE/plans/new/courses").body()

    suspend fun getPlanCreationState(): ApiResponse =
        http.get("$BASE/plans/new").body()

    suspend fun fetchVideos(selection: Map<String, List<Int>>): ApiResponse =
        http.post("$BASE/plans/new/videos") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(FetchVideosRequest(selection))
        }.body()

    suspend fun savePlan(planName: String): ApiResponse =
        http.put("$BASE/plans/new") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(PlanNameRequest(planName))
        }.body()

    // Plans CRUD
    suspend fun getPlans(): List<String> =
        http.get("$BASE/plans").body()

    suspend fun deletePlan(planName: String) {
        http.delete("$BASE/plans") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(PlanNameRequest(planName))
        }
    }

    // Investigation
    suspend fun createInvestigation(planName: String): ApiResponse =
        http.post("$BASE/investigation") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(PlanNameRequest(planName))
        }.body()

    suspend fun getInvestigation(): ApiResponse =
        http.get("$BASE/investigation").body()

    suspend fun deleteInvestigation() { http.delete("$BASE/investigation") }

    // Execution
    suspend fun createExecution(planName: String): ApiResponse =
        http.post("$BASE/execution") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(PlanNameRequest(planName))
        }.body()

    suspend fun getExecution(): ApiResponse =
        http.get("$BASE/execution").body()

    suspend fun deleteExecution() { http.delete("$BASE/execution") }

    // Config
    suspend fun configExists(): Boolean = http.get("$BASE/config/exists").body()
    suspend fun getAvailableBrowsers(): List<String> = http.get("$BASE/config/browsers").body()
    suspend fun getConfig(): ApiResponse = http.get("$BASE/config").body()
    suspend fun updateConfig(browserChannel: String): ApiResponse =
        http.put("$BASE/config") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(UpdateConfigRequest(browserChannel))
        }.body()

    // Lifecycle
    suspend fun breakBackend(): ApiResponse = http.post("$BASE/break").body()
}
