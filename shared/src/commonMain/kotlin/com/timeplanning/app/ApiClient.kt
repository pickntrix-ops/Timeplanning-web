package com.timeplanning.app

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        // Without this, a non-2xx response (e.g. the server's 400 when a delete is
        // blocked) doesn't throw — the call just "succeeds" with an unread error
        // body, so every runCatching { ... }.onFailure { } in this app was silently
        // treating real server errors as success.
        expectSuccess = true
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

    /** `date` (YYYY-MM-DD) logs the completion on an earlier day — catching up on a missed task. */
    suspend fun completeTask(sessionToken: String, taskId: Long, date: String? = null): Task =
        client.post("$BASE_URL/tasks/$taskId/complete") {
            header("Authorization", "Bearer $sessionToken")
            date?.let { parameter("date", it) }
        }.body()

    suspend fun updateTask(sessionToken: String, taskId: Long, request: UpdateTaskRequest): Task =
        client.patch("$BASE_URL/tasks/$taskId") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteTask(sessionToken: String, taskId: Long) {
        client.delete("$BASE_URL/tasks/$taskId") {
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

    suspend fun fetchTaskCategories(sessionToken: String): List<TaskCategory> =
        client.get("$BASE_URL/task-categories") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun createTaskCategory(sessionToken: String, request: CreateTaskCategoryRequest): TaskCategory =
        client.post("$BASE_URL/task-categories") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateTaskCategory(sessionToken: String, categoryId: Long, request: UpdateTaskCategoryRequest): TaskCategory =
        client.patch("$BASE_URL/task-categories/$categoryId") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** A prefilled, always-overridable guess — never applied without the person seeing/confirming it client-side. Null if suggestions aren't set up on the server, or the model's answer was unusable. */
    suspend fun suggestCategoryEnergyLevel(sessionToken: String, name: String, description: String?): EnergyLevel? =
        client.post("$BASE_URL/task-categories/suggest-energy-level") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(SuggestEnergyLevelRequest(name, description))
        }.body<SuggestEnergyLevelResponse>().energyLevel

    suspend fun deleteTaskCategory(sessionToken: String, categoryId: Long) {
        client.delete("$BASE_URL/task-categories/$categoryId") {
            header("Authorization", "Bearer $sessionToken")
        }
    }

    suspend fun createTaskSubcategory(sessionToken: String, categoryId: Long, request: CreateTaskSubcategoryRequest): TaskSubcategory =
        client.post("$BASE_URL/task-categories/$categoryId/subcategories") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateTaskSubcategory(sessionToken: String, categoryId: Long, subcategoryId: Long, request: UpdateTaskSubcategoryRequest): TaskSubcategory =
        client.patch("$BASE_URL/task-categories/$categoryId/subcategories/$subcategoryId") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteTaskSubcategory(sessionToken: String, categoryId: Long, subcategoryId: Long) {
        client.delete("$BASE_URL/task-categories/$categoryId/subcategories/$subcategoryId") {
            header("Authorization", "Bearer $sessionToken")
        }
    }

    suspend fun fetchDayProfiles(sessionToken: String): List<DayProfile> =
        client.get("$BASE_URL/day-profiles") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun updateDayProfile(sessionToken: String, dayType: DayType, request: UpdateDayProfileRequest): DayProfile =
        client.put("$BASE_URL/day-profiles/${dayType.name}") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun fetchFreeDays(sessionToken: String): Set<Weekday> =
        client.get("$BASE_URL/free-days") {
            header("Authorization", "Bearer $sessionToken")
        }.body<FreeDaysResponse>().days

    suspend fun updateFreeDays(sessionToken: String, days: Set<Weekday>): Set<Weekday> =
        client.put("$BASE_URL/free-days") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(UpdateFreeDaysRequest(days))
        }.body<FreeDaysResponse>().days

    suspend fun fetchPlanPreferences(sessionToken: String): PlanPreferencesResponse =
        client.get("$BASE_URL/plan-preferences") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    /** Only the fields passed update — see UpdatePlanPreferencesRequest. */
    suspend fun updatePlanPreferences(sessionToken: String, freeTimePercent: Int? = null, preferWeekdays: Boolean? = null): PlanPreferencesResponse =
        client.put("$BASE_URL/plan-preferences") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(UpdatePlanPreferencesRequest(freeTimePercent, preferWeekdays))
        }.body()

    /** Runs the scheduling engine for the given week (server defaults to next Monday if omitted) and persists the result. */
    suspend fun generatePlan(sessionToken: String, weekStart: String? = null): GeneratePlanResponse =
        client.post("$BASE_URL/plan/generate") {
            header("Authorization", "Bearer $sessionToken")
            weekStart?.let { parameter("weekStart", it) }
        }.body()

    /** Re-fetches an already-generated week's task placements plus that week's fixed-commitment occurrences, without recomputing anything. */
    suspend fun fetchPlanBlocks(sessionToken: String, weekStart: String? = null): PlanBlocksResponse =
        client.get("$BASE_URL/plan/blocks") {
            header("Authorization", "Bearer $sessionToken")
            weekStart?.let { parameter("weekStart", it) }
        }.body()

    /** Re-plans just one date — every other date's stored blocks are left exactly as they were. See PlanGenerationService.regenerateDay; the response is already scoped to `date`. */
    suspend fun regenerateDay(sessionToken: String, date: String): GeneratePlanResponse =
        client.post("$BASE_URL/plan/regenerate-day") {
            header("Authorization", "Bearer $sessionToken")
            parameter("date", date)
        }.body()

    /** Calendar events added since this or next week's plan was made that land on something planned — see PlanGenerationService.clashes. */
    suspend fun fetchClashes(sessionToken: String): List<CalendarClash> =
        client.get("$BASE_URL/plan/clashes") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun resolveClash(sessionToken: String, clash: CalendarClash, action: ClashAction) {
        client.post("$BASE_URL/plan/clashes/resolve") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(ResolveClashRequest(clash.eventKey, clash.date, action.name))
        }
    }

    /** All-day events from today to the end of next week that haven't been answered yet. */
    suspend fun fetchAllDayPrompts(sessionToken: String): List<AllDayPrompt> =
        client.get("$BASE_URL/plan/all-day-events") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    /** Saves the answer (for every occurrence of a repeating event) and re-plans any affected day. */
    suspend fun resolveAllDay(sessionToken: String, prompt: AllDayPrompt, choice: AllDayChoice) {
        client.post("$BASE_URL/plan/all-day-events/choice") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(ResolveAllDayRequest(prompt.seriesKey, choice.name))
        }
    }

    suspend fun fetchMe(sessionToken: String): Me =
        client.get("$BASE_URL/me") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun fetchDayReview(sessionToken: String, date: String): List<ReviewItem> =
        client.get("$BASE_URL/plan/day-review") {
            header("Authorization", "Bearer $sessionToken")
            parameter("date", date)
        }.body()

    suspend fun fetchWeekTasks(sessionToken: String): List<WeekTask> =
        client.get("$BASE_URL/plan/week-tasks") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun fetchHistory(sessionToken: String, weeks: Int = 4): HistorySummary =
        client.get("$BASE_URL/history") {
            header("Authorization", "Bearer $sessionToken")
            parameter("weeks", weeks)
        }.body()

    /** Drops a single task's current placement (whichever date it's on), leaving the task itself untouched — free to be picked up again next time that date (or the week) is regenerated. */
    suspend fun unscheduleTask(sessionToken: String, taskId: Long) {
        client.delete("$BASE_URL/plan/blocks/$taskId") {
            header("Authorization", "Bearer $sessionToken")
        }
    }

    suspend fun fetchPeople(sessionToken: String): List<Person> =
        client.get("$BASE_URL/people") {
            header("Authorization", "Bearer $sessionToken")
        }.body()

    suspend fun createPerson(sessionToken: String, name: String): Person =
        client.post("$BASE_URL/people") {
            header("Authorization", "Bearer $sessionToken")
            contentType(ContentType.Application.Json)
            setBody(CreatePersonRequest(name))
        }.body()
}

/**
 * The server's {"error": "..."} body reads much better than Ktor's own verbose
 * exception text (which wraps the whole response) — falls back to that when
 * there's no such body (a network failure, an unexpected response shape, ...).
 */
suspend fun Throwable.serverMessage(): String {
    if (this is ResponseException) {
        val fromBody = runCatching {
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        if (fromBody != null) return fromBody
    }
    return message ?: "Something went wrong"
}
