package com.timeplanning.app

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://api.pickntrix-themeparks.com/timeplanning/api/v1"

/** The server's Web OAuth client ID — not a secret, safe to embed (the Client Secret never leaves the server). */
const val GOOGLE_WEB_CLIENT_ID = "432704647120-g20tcjb2i4nirnshvf2io79fc2rnu126.apps.googleusercontent.com"

@Serializable
data class LoginUrlResponse(val authUrl: String)

@Serializable
data class TaskCountResponse(val count: Long)

@Serializable
data class DayCalendarStatus(
    val date: String,
    val hasEvents: Boolean,
    val fullyBlocked: Boolean,
)

@Serializable
data class NativeAuthRequestBody(val code: String)

@Serializable
data class SessionResponse(val sessionToken: String, val email: String, val displayName: String?)

/**
 * Talks directly to the TimePlanning backend. No token persistence yet —
 * this is the minimal client needed to smoke-test the backend from a real
 * app shell; a proper auth/session layer (secure storage, native Google
 * Sign-In instead of the browser-redirect flow) is follow-up work once
 * there's more to build a UI around.
 */
class ApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun fetchLoginUrl(webOrigin: String? = null): String =
        client.get("$BASE_URL/auth/google/login") {
            webOrigin?.let { parameter("webOrigin", it) }
        }.body<LoginUrlResponse>().authUrl

    /** Native sign-in front door — exchanges the server auth code from GoogleNativeSignIn for a session. */
    suspend fun exchangeNativeCode(code: String): SessionResponse =
        client.post("$BASE_URL/auth/google/token") {
            contentType(ContentType.Application.Json)
            setBody(NativeAuthRequestBody(code))
        }.body()

    suspend fun fetchTaskCount(sessionToken: String): Long =
        client.get("$BASE_URL/tasks/count") {
            header("Authorization", "Bearer $sessionToken")
        }.body<TaskCountResponse>().count

    suspend fun fetchCalendarWeek(sessionToken: String): List<DayCalendarStatus> =
        client.get("$BASE_URL/calendar/week") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun fetchTasks(sessionToken: String, status: TaskStatus? = null): List<Task> =
        client.get("$BASE_URL/tasks") {
            header("Authorization", "Bearer $sessionToken")
            status?.let { parameter("status", it.name) }
        }.body()

    suspend fun createTask(sessionToken: String, request: CreateTaskRequest): Task =
        client.post("$BASE_URL/tasks") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun completeTask(sessionToken: String, taskId: Long): Task =
        client.post("$BASE_URL/tasks/$taskId/complete") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun fetchBlockInstances(sessionToken: String, start: String, end: String): List<BlockInstance> =
        client.get("$BASE_URL/block-instances") {
            header("Authorization", "Bearer $sessionToken")
            parameter("start", start)
            parameter("end", end)
        }.body()

    suspend fun generateWeek(sessionToken: String, weekStart: String) {
        client.post("$BASE_URL/scheduling/weeks/$weekStart/generate") {
            header("Authorization", "Bearer $sessionToken")
        }
    }

    suspend fun fetchCalendarEvents(sessionToken: String, start: String, end: String): List<CalendarEventInfo> =
        client.get("$BASE_URL/calendar/events") {
            header("Authorization", "Bearer $sessionToken")
            parameter("start", start)
            parameter("end", end)
        }.body()

    suspend fun fetchCalendars(sessionToken: String): List<GoogleCalendarInfo> =
        client.get("$BASE_URL/calendar/calendars") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun updateSelectedCalendars(sessionToken: String, calendarIds: List<String>) {
        client.put("$BASE_URL/calendar/calendars/selected") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateSelectedCalendarsRequest(calendarIds))
        }
    }
}
