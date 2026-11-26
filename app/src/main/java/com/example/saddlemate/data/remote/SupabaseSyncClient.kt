package com.example.saddlemate.data.remote

import com.example.saddlemate.data.local.FeedbackNoteEntity
import com.example.saddlemate.data.local.RideEntity
import com.example.saddlemate.domain.FeedbackStoryFormatter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncSummary(
    val ok: Boolean,
    val message: String,
    val ticketsSynced: Int = 0,
    val ridesSynced: Int = 0,
)

/**
 * Lightweight Supabase / PostGIS REST client using standard HttpURLConnection.
 * When SUPABASE_URL or SUPABASE_ANON_KEY are empty, all data remains cleanly local.
 */
@Singleton
class SupabaseSyncClient @Inject constructor() {

    fun isConfigured(url: String, anonKey: String): Boolean =
        url.trim().startsWith("https://") && anonKey.trim().length > 10

    suspend fun syncAll(
        baseUrl: String,
        anonKey: String,
        tickets: List<FeedbackNoteEntity>,
        rides: List<RideEntity>,
    ): SyncSummary = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        if (!isConfigured(cleanUrl, cleanKey)) {
            return@withContext SyncSummary(
                ok = false,
                message = "Supabase nicht konfiguriert – alle Tickets und Ritte bleiben lokal gespeichert.",
            )
        }
        return@withContext runCatching {
            var ticketCount = 0
            if (tickets.isNotEmpty()) {
                val payload = buildTicketsJson(tickets)
                postJson("$cleanUrl/rest/v1/product_backlog_tickets", cleanKey, payload)
                ticketCount = tickets.size
            }
            var rideCount = 0
            if (rides.isNotEmpty()) {
                val payload = buildRidesJson(rides)
                postJson("$cleanUrl/rest/v1/ride_sessions", cleanKey, payload)
                rideCount = rides.size
            }
            SyncSummary(
                ok = true,
                message = "Sync erfolgreich: $ticketCount Tickets & $rideCount Ritte hochgeladen.",
                ticketsSynced = ticketCount,
                ridesSynced = rideCount,
            )
        }.getOrElse { err ->
            SyncSummary(
                ok = false,
                message = "Sync fehlgeschlagen (lokale Daten unverändert): ${err.message ?: "Netzwerkfehler"}",
            )
        }
    }

    private fun postJson(endpoint: String, anonKey: String, jsonBody: String) {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $anonKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "resolution=merge-duplicates")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code")
        }
    }

    companion object {
        fun buildTicketsJson(tickets: List<FeedbackNoteEntity>): String {
            val entries = tickets.map { note ->
                val story = note.userStory.ifBlank { FeedbackStoryFormatter.buildUserStory(note.text) }
                val priorityScore = when (note.priority) {
                    "HIGH" -> 9
                    "MEDIUM" -> 5
                    else -> 2
                }
                val pipelineStatus = when (note.status) {
                    "BACKLOG" -> "backlog_ranked"
                    "IN_PROGRESS" -> "in_automated_dev"
                    "DONE" -> "shipped_ota"
                    else -> "new_triage"
                }
                """{"title":"${escapeJson(note.text.take(80))}","technical_spec":"${escapeJson(story)}","category":"ux_improvement","impact_score":$priorityScore,"complexity_score":3,"pipeline_status":"$pipelineStatus"}"""
            }
            return "[${entries.joinToString(",")}]"
        }

        fun buildRidesJson(rides: List<RideEntity>): String {
            val entries = rides.map { r ->
                val durSec = (r.durationMs / 1000L).toInt().coerceAtLeast(0)
                val started = Instant.ofEpochMilli(r.startedAtMillis).toString()
                val dist = String.format(Locale.US, "%.2f", r.distanceMeters)
                val avg = String.format(Locale.US, "%.2f", r.averageSpeedKmh)
                val max = String.format(Locale.US, "%.2f", r.maxSpeedKmh)
                val asc = String.format(Locale.US, "%.1f", r.ascentM)
                val desc = String.format(Locale.US, "%.1f", r.descentM)
                val mcal = String.format(Locale.US, "%.3f", r.horseCalories / 1000.0)
                """{"started_at":"$started","total_duration_sec":$durSec,"moving_duration_sec":$durSec,"distance_meters":$dist,"avg_speed_kmh":$avg,"max_speed_kmh":$max,"elevation_gain_m":$asc,"elevation_loss_m":$desc,"rider_calories":${r.riderCalories},"horse_energy_mcal":$mcal}"""
            }
            return "[${entries.joinToString(",")}]"
        }

        private fun escapeJson(s: String): String = buildString(s.length) {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
    }
}
