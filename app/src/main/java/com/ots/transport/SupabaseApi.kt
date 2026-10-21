package com.ots.transport

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        val availableSeats: Int,
        val totalSeats: Int = 0,
        val status: String = "published"
    )

    data class Booking(
        val id: String,
        val origin: String,
        val destination: String,
        val departureAt: String,
        val seatLabels: String,
        val fareTotalPkr: Int,
        val status: String
    )

    data class ApprovedVehicle(
        val id: String,
        val make: String,
        val model: String,
        val registration: String,
        val passengerSeats: Int
    )

    data class Vehicle(
        val id: String, val make: String, val model: String, val year: Int,
        val registration: String, val color: String, val vehicleType: String,
        val seats: Int, val luggageCapacity: String, val status: String,
        val photoPaths: List<String>
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

    suspend fun refreshSession(session: Session): Session = withContext(Dispatchers.IO) {
        if (session.refreshToken.isBlank()) throw IllegalStateException("Your session expired. Please sign in again.")
        val json = JSONObject(request(
            "/auth/v1/token?grant_type=refresh_token",
            "POST",
            body = JSONObject().put("refresh_token", session.refreshToken)
        ))
        val user = json.optJSONObject("user")
        Session(
            userId = user?.optString("id").orEmpty().ifBlank { session.userId },
            email = user?.optString("email").orEmpty().ifBlank { session.email },
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").ifBlank { session.refreshToken }
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
        luggageCapacity: String,
        context: Context,
        driverPhoto: Uri?,
        cnicDocument: Uri?,
        vehiclePhoto: Uri?
    ) = withContext(Dispatchers.IO) {
        if (driverPhoto == null || cnicDocument == null || vehiclePhoto == null) {
            throw IllegalStateException("Choose a driver photo, a CNIC image/PDF, and at least one car photo before submitting.")
        }
        saveProfile(session, name, phone, "Driver")
        val driverPhotoPath = uploadPrivateFile(context, session, "driver-documents", driverPhoto, "driver-photo")
        val cnicDocumentPath = uploadPrivateFile(context, session, "driver-documents", cnicDocument, "cnic-document")
        val vehiclePhotoPath = uploadPrivateFile(context, session, "vehicle-photos", vehiclePhoto, "vehicle-photo")
        val appPayload = JSONObject()
            .put("user_id", session.userId)
            .put("driver_photo_path", driverPhotoPath)
            .put("cnic_document_path", cnicDocumentPath)
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
            .put("exterior_photo_paths", JSONArray().put(vehiclePhotoPath))
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
        val profileRows = JSONArray(request(
            "/rest/v1/profiles?id=eq.${session.userId}&select=account_status",
            "GET",
            token = session.accessToken
        ))
        if (profileRows.length() > 0 && profileRows.getJSONObject(0).optString("account_status") == "suspended") return@withContext "suspended"
        val response = request(
            "/rest/v1/driver_applications?user_id=eq.${session.userId}&select=status,vehicles!vehicles_driver_id_fkey(status)",
            "GET",
            token = session.accessToken
        )
        val rows = JSONArray(response)
        if (rows.length() == 0) return@withContext "not_submitted"
        val row = rows.getJSONObject(0)
        val applicationStatus = row.optString("status", "pending")
        if (applicationStatus != "approved") return@withContext applicationStatus
        val vehicleValue = row.opt("vehicles")
        val vehicle = when (vehicleValue) {
            is JSONObject -> vehicleValue
            is JSONArray -> if (vehicleValue.length() > 0) vehicleValue.getJSONObject(0) else JSONObject()
            else -> JSONObject()
        }
        val vehicleStatus = vehicle.optString("status", "pending")
        if (vehicleStatus == "pending") "vehicle_pending" else vehicleStatus
    }

    suspend fun getApprovedTrips(session: Session? = null): List<Trip> = withContext(Dispatchers.IO) {
        val now = URLEncoder.encode(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("Asia/Karachi") }
                .format(Date()),
            "UTF-8"
        )
        val response = request(
            "/rest/v1/trips?status=eq.published&departure_at=gt.$now&available_seats=gt.0&select=id,origin,destination,pickup_point,dropoff_point,departure_at,fare_pkr,available_seats,total_seats,status&order=departure_at.asc",
            "GET",
            token = session?.accessToken
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
                availableSeats = row.getInt("available_seats"),
                totalSeats = row.optInt("total_seats"),
                status = row.optString("status", "published")
            )
        }
    }

    suspend fun getApprovedVehicle(session: Session): ApprovedVehicle? = withContext(Dispatchers.IO) {
        val response = request(
            "/rest/v1/vehicles?driver_id=eq.${session.userId}&status=eq.approved&select=id,make,model,registration_number,passenger_seats",
            "GET",
            token = session.accessToken
        )
        val rows = JSONArray(response)
        if (rows.length() == 0) null else {
            val row = rows.getJSONObject(0)
            ApprovedVehicle(
                id = row.getString("id"),
                make = row.getString("make"),
                model = row.getString("model"),
                registration = row.getString("registration_number"),
                passengerSeats = row.getInt("passenger_seats")
            )
        }
    }

    suspend fun getRegisteredVehicle(session: Session): Vehicle? = withContext(Dispatchers.IO) {
        val rows = JSONArray(request(
            "/rest/v1/vehicles?driver_id=eq.${session.userId}&select=id,make,model,model_year,registration_number,color,vehicle_type,passenger_seats,luggage_capacity,status,exterior_photo_paths",
            "GET",
            token = session.accessToken
        ))
        if (rows.length() == 0) null else {
            val row = rows.getJSONObject(0)
            val photos = row.optJSONArray("exterior_photo_paths") ?: JSONArray()
            Vehicle(
                id = row.getString("id"), make = row.optString("make"), model = row.optString("model"),
                year = row.optInt("model_year"), registration = row.optString("registration_number"),
                color = row.optString("color"), vehicleType = row.optString("vehicle_type", "Car"),
                seats = row.optInt("passenger_seats"), luggageCapacity = row.optString("luggage_capacity", "Standard"),
                status = row.optString("status", "pending"),
                photoPaths = (0 until photos.length()).map { photos.optString(it) }
            )
        }
    }

    suspend fun updateRegisteredVehicle(
        session: Session,
        vehicleId: String,
        make: String,
        model: String,
        year: Int,
        registration: String,
        color: String,
        seats: Int,
        luggageCapacity: String,
        context: Context,
        newPhoto: Uri?
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("make", make.trim())
            .put("model", model.trim())
            .put("model_year", year)
            .put("registration_number", registration.trim().uppercase())
            .put("color", color.trim())
            .put("passenger_seats", seats)
            .put("luggage_capacity", luggageCapacity.trim())
            .put("status", "pending")
        if (newPhoto != null) {
            val path = uploadPrivateFile(context, session, "vehicle-photos", newPhoto, "vehicle-photo")
            payload.put("exterior_photo_paths", JSONArray().put(path))
        }
        request(
            "/rest/v1/vehicles?id=eq.$vehicleId",
            "PATCH",
            token = session.accessToken,
            body = payload,
            prefer = "return=minimal"
        )
        Unit
    }

    suspend fun publishTrip(
        session: Session,
        vehicleId: String,
        destination: String,
        pickup: String,
        dropoff: String,
        departureAt: String,
        farePkr: Int,
        seats: Int
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("driver_id", session.userId)
            .put("vehicle_id", vehicleId)
            .put("origin", "Khuzdar")
            .put("destination", destination)
            .put("pickup_point", pickup.trim())
            .put("dropoff_point", dropoff.trim())
            .put("departure_at", departureAt)
            .put("fare_pkr", farePkr)
            .put("total_seats", seats)
            .put("available_seats", seats)
            .put("status", "published")
        val response = request(
            "/rest/v1/trips",
            "POST",
            token = session.accessToken,
            body = payload,
            prefer = "return=representation"
        )
        val rows = JSONArray(response)
        if (rows.length() == 0) throw IllegalStateException("Trip publishing did not return a trip ID.")
        rows.getJSONObject(0).getString("id")
    }

    suspend fun getBookings(session: Session): List<Booking> = withContext(Dispatchers.IO) {
        val response = request(
            "/rest/v1/bookings?select=id,seat_labels,fare_total_pkr,status,trips(origin,destination,departure_at)&order=created_at.desc",
            "GET",
            token = session.accessToken
        )
        val rows = JSONArray(response)
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val tripValue = row.opt("trips")
            val trip = when (tripValue) {
                is JSONObject -> tripValue
                is JSONArray -> if (tripValue.length() > 0) tripValue.getJSONObject(0) else JSONObject()
                else -> JSONObject()
            }
            val seats = row.optJSONArray("seat_labels") ?: JSONArray()
            Booking(
                id = row.getString("id"),
                origin = trip.optString("origin", "Unknown origin"),
                destination = trip.optString("destination", "Unknown destination"),
                departureAt = trip.optString("departure_at", ""),
                seatLabels = (0 until seats.length()).joinToString(", ") { seats.optString(it) },
                fareTotalPkr = row.optInt("fare_total_pkr"),
                status = row.optString("status", "confirmed")
            )
        }
    }

    suspend fun requestBookingCancellation(session: Session, bookingId: String) = withContext(Dispatchers.IO) {
        request(
            "/rest/v1/bookings?id=eq.$bookingId",
            "PATCH",
            token = session.accessToken,
            body = JSONObject().put("status", "cancel_requested"),
            prefer = "return=minimal"
        )
        Unit
    }

    suspend fun getDriverTrips(session: Session): List<Trip> = withContext(Dispatchers.IO) {
        val response = request(
            "/rest/v1/trips?driver_id=eq.${session.userId}&select=id,origin,destination,pickup_point,dropoff_point,departure_at,fare_pkr,available_seats,total_seats,status&order=departure_at.asc",
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
                availableSeats = row.getInt("available_seats"),
                totalSeats = row.optInt("total_seats"),
                status = row.optString("status", "published")
            )
        }
    }

    suspend fun updateTrip(
        session: Session,
        tripId: String,
        destination: String,
        pickup: String,
        dropoff: String,
        departureAt: String,
        farePkr: Int,
        seats: Int
    ) = withContext(Dispatchers.IO) {
        request(
            "/rest/v1/trips?id=eq.$tripId",
            "PATCH",
            token = session.accessToken,
            body = JSONObject()
                .put("destination", destination)
                .put("pickup_point", pickup.trim())
                .put("dropoff_point", dropoff.trim())
                .put("departure_at", departureAt)
                .put("fare_pkr", farePkr)
                .put("total_seats", seats)
                .put("available_seats", seats),
            prefer = "return=minimal"
        )
        Unit
    }

    suspend fun cancelTrip(session: Session, tripId: String) = withContext(Dispatchers.IO) {
        request(
            "/rest/v1/trips?id=eq.$tripId",
            "PATCH",
            token = session.accessToken,
            body = JSONObject().put("status", "cancelled"),
            prefer = "return=minimal"
        )
        Unit
    }

    suspend fun createBooking(session: Session, tripId: String, seatLabel: String, farePkr: Int, pickupPoint: String, dropoffPoint: String, luggageType: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("passenger_id", session.userId)
            .put("trip_id", tripId)
            .put("seat_labels", JSONArray().put(seatLabel))
            .put("passenger_count", 1)
            .put("fare_total_pkr", farePkr)
            .put("pickup_point", pickupPoint)
            .put("dropoff_point", dropoffPoint)
            .put("luggage_type", luggageType)
            .put("status", "confirmed")
        val response = request(
            "/rest/v1/bookings",
            "POST",
            token = session.accessToken,
            body = payload,
            prefer = "return=representation"
        )
        val rows = JSONArray(response)
        if (rows.length() == 0) throw IllegalStateException("The booking service did not return a ticket.")
        rows.getJSONObject(0).getString("id")
    }

    private fun uploadPrivateFile(
        context: Context,
        session: Session,
        bucket: String,
        uri: Uri,
        label: String
    ): String {
        val mimeType = context.contentResolver.getType(uri)
            ?: throw IllegalStateException("The selected file type could not be identified.")
        val allowed = when (bucket) {
            "driver-documents" -> mimeType in setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
            "vehicle-photos" -> mimeType in setOf("image/jpeg", "image/png", "image/webp")
            else -> false
        }
        if (!allowed) throw IllegalStateException("Please choose a supported image or PDF file.")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("The selected file could not be opened.")
        if (bytes.size > 10 * 1024 * 1024) throw IllegalStateException("Each upload must be smaller than 10 MB.")
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "application/pdf" -> "pdf"
            else -> "bin"
        }
        val objectPath = session.userId + "/" + label + "-" + System.currentTimeMillis() + "." + extension
        val connection = (URL(BASE_URL + "/storage/v1/object/" + bucket + "/" + objectPath).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("apikey", PUBLIC_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + session.accessToken)
            connection.setRequestProperty("Content-Type", mimeType)
            connection.setRequestProperty("x-upsert", "true")
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = try { JSONObject(response).optString("message").ifBlank { response } } catch (_: Exception) { response }
                throw IllegalStateException(message.ifBlank { "File upload failed (HTTP " + code + ")." })
            }
            return objectPath
        } finally {
            connection.disconnect()
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
