package com.ots.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Small REST client for Supabase Auth and PostgREST.
 * Only the public publishable key is embedded in this Android client.
 * Never put a service-role/secret key in a mobile app.
 */
internal object SupabaseApi {
    private const val BASE_URL = "https://konrdhiwgluwmaakskdr.supabase.co"
    private const val PUBLIC_KEY = "sb_publishable_nECd6u9jkvSxn42_EJGI_A_ejyp8WVG"

    data class Session(
        val userId: String,
        val email: String,
        val accessToken: String,
        val refreshToken: String
    )

    data class Trip(
        val id: String,
        val origin: String,
        val destination: String,
        val pickupPoint: String,
        val dropoffPoint: String,
        val departureAt: String,
        val farePkr: Int,
        val availableSeats: Int
    )

    suspend fun signUp(email: String, password: String): Pair<Session?, String> = withContext(Dispatchers.IO) {
        val response = request(
            "/auth/v1/signup",
            "POST",
            body = JSONObject().put("email", email.trim()).put("password", password)
        )
        val json = JSONObject(response)
        val user = json.optJSONObject("user") ?: json
        val id = user.optString("id")
        val access = json.optString("access_token")
        if (id.isBlank()) throw IllegalStateException("The account service returned no user ID.")
        if (access.isBlank()) {
            null to "Account created. Check your email for the verification link, then return and sign in."
        } else {
            Session(id, email.trim(), access, json.optString("refresh_token")) to "Account created."
        }
    }

    suspend fun signIn(email: String, password: String): Session = withContext(Dispatchers.IO) {
        val response = request(
            "/auth/v1/token?grant_type=password",
            "POST",
            body = JSONObject().put("email", email.trim()).put("password", password)
        )
        val json = JSONObject(response)
        val user = json.optJSONObject("user") ?: throw IllegalStateException("Sign-in did not return a user.")
        Session(
            userId = user.getString("id"),
            email = user.optString("email", email.trim()),
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token")
        )
    }

    suspend fun saveProfile(session: Session, name: String, phone: String, accountType: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("id", session.userId)
            .put("account_type", if (accountType == "Driver") "driver" else "passenger")
            .put("full_name", name.trim())
            .put("phone", phone.trim())
        request(
            "/rest/v1/profiles?on_conflict=id",
            "POST",
            token = session.accessToken,
            body = payload,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
        Unit
    }

    suspend fun getProfile(session: Session): JSONObject? = withContext(Dispatchers.IO) {
        val response = request(
            "/rest/v1/profiles?id=eq.${session.userId}&select=id,account_type,full_name,phone",
            "GET",
            token = session.accessToken
        )
        val rows = JSONArray(response)
        if (rows.length() == 0) null else rows.getJSONObject(0)
    }

    suspend fun submitDriverApplication(
        session: Session,
        name: String,
        phone: String,
        make: String,
        model: String,
        year: Int,
        registration: String,
        color: String,
        seats: Int,
        vehicleType: String,
        luggageCapacity: String
    ) = withContext(Dispatchers.IO) {
        saveProfile(session, name, phone, "Driver")
        val appPayload = JSONObject()
            .put("user_id", session.userId)
            .put("status", "pending")
        request(
            "/rest/v1/driver_applications?on_conflict=user_id",
            "POST",
            token = session.accessToken,
            body = appPayload,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
        val vehiclePayload = JSONObject()
            .put("driver_id", session.userId)
            .put("make", make.trim())
            .put("model", model.trim())
            .put("model_year", year)
            .put("registration_number", registration.trim().uppercase())
            .put("color", color.trim())
            .put("vehicle_type", vehicleType)
            .put("passenger_seats", seats)
            .put("luggage_capacity", luggageCapacity)
            .put("status", "pending")
        request(
            "/rest/v1/vehicles?on_conflict=driver_id",
            "POST",
            token = session.accessToken,
            body = vehiclePayload,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
        Unit
    }

    suspend fun getDriverStatus(session: Session): String = withContext(Dispatchers.IO) {
        val response = request(
            "/rest/v1/driver_applications?user_id=eq.${session.userId}&select=status",
            "GET",
            token = session.accessToken
        )
        val rows = JSONArray(response)
        if (rows.length() == 0) "not_submitted" else rows.getJSONObject(0).optString("status", "pending")
    }

    suspend fun getApprovedTrips(session: Session): List<Trip> = withContext(Dispatchers.IO) {
        val response = request(
            "/rest/v1/trips?status=eq.published&departure_at=gt.${java.time.Instant.now()}&select=id,origin,destination,pickup_point,dropoff_point,departure_at,fare_pkr,available_seats&order=departure_at.asc",
            "GET",
            token = session.accessToken
        )
        val rows = JSONArray(response)
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            Trip(
                id = row.getString("id"),
                origin = row.getString("origin"),
                destination = row.getString("destination"),
                pickupPoint = row.getString("pickup_point"),
                dropoffPoint = row.getString("dropoff_point"),
                departureAt = row.getString("departure_at"),
                farePkr = row.getInt("fare_pkr"),
                availableSeats = row.getInt("available_seats")
            )
        }
    }

    private fun request(
        path: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null,
        prefer: String? = null
    ): String {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", PUBLIC_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = try {
                    JSONObject(response).optString("msg").ifBlank {
                        JSONObject(response).optString("message").ifBlank {
                            JSONObject(response).optString("error_description").ifBlank { response }
                        }
                    }
                } catch (_: Exception) { response }
                throw IllegalStateException(message.ifBlank { "Request failed (HTTP $code)." })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
