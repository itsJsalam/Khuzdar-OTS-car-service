package com.ots.transport

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.location.LocationManager
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val Ink = Color(0xFF071525)
private val Navy = Color(0xFF0D2238)
private val Navy2 = Color(0xFF173650)
private val Gold = Color(0xFFFFC66D)
private val Mint = Color(0xFF42D6B2)
private val Paper = Color(0xFFF4F7FB)
private val Muted = Color(0xFF7E8EA1)

class MainActivity : ComponentActivity() {
    private val oauthCallbackState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthCallbackState.value = intent?.data
        setContent {
            OTSApp(
                initialSession = SessionStore.load(this@MainActivity),
                oauthCallback = oauthCallbackState.value,
                onOAuthCallbackHandled = { oauthCallbackState.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oauthCallbackState.value = intent.data
    }
}

private data class Ride(val city: String, val route: String, val time: String, val car: String, val driver: String, val price: Int, val seats: Int, val rating: String, val tag: String, val tripId: String? = null, val totalSeats: Int = 4, val pickupPoint: String = "Khuzdar", val dropoffPoint: String? = null, val vehicleType: String = "Car")
@Composable
private fun OtsLogo(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(id = R.drawable.ots_brand),
        contentDescription = "OTS Transport logo",
        modifier = Modifier.size(size).clip(RoundedCornerShape(13.dp))
    )
}

private fun vehicleTypeFor(passengerSeats: Int, vehicleName: String = ""): String {
    val name = vehicleName.lowercase()
    return when {
        name.contains("bus") -> "Bus"
        name.contains("coaster") -> "Coaster"
        name.contains("van") -> "Van"
        passengerSeats > 25 -> "Bus"
        passengerSeats > 10 -> "Coaster"
        passengerSeats > 4 -> "Van"
        else -> "Car"
    }
}

private val sampleRides = listOf(
    Ride("Karachi", "Khuzdar → Karachi", "Tomorrow • 06:30 AM", "Toyota Corolla • White", "Naveed Baloch", 3000, 3, "4.9", "POPULAR"),
    Ride("Quetta", "Khuzdar → Quetta", "Tomorrow • 08:00 AM", "Honda City • Silver", "Rahim Shah", 2500, 2, "4.8", "ON TIME"),
    Ride("Larkana", "Khuzdar → Larkana", "Fri • 07:00 AM", "Toyota Yaris • Grey", "Aslam Mengal", 2800, 2, "4.7", "NEW"),
    Ride("Gwadar", "Khuzdar → Gwadar", "Fri • 09:15 AM", "Suzuki Ciaz • Black", "Bilal Ahmed", 2200, 3, "4.9", "COASTAL")
)

@Composable
private fun OTSApp(
    initialSession: SupabaseApi.Session?,
    oauthCallback: Uri?,
    onOAuthCallbackHandled: () -> Unit
) {
    val appContext = LocalContext.current
    var role by remember { mutableStateOf("Passenger") }
    var tab by remember { mutableStateOf("Home") }
    var language by remember { mutableStateOf(appContext.getSharedPreferences("ots_settings", android.content.Context.MODE_PRIVATE).getString("language", "").orEmpty()) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var selectedRoute by remember { mutableStateOf("Karachi") }
    var selectedRide by remember { mutableStateOf<Ride?>(null) }
    var selectedSeat by remember { mutableStateOf("Seat 1") }
    var selectedDrop by remember { mutableStateOf("Karachi") }
    var luggage by remember { mutableStateOf("Small bag") }
    var booked by remember { mutableStateOf(false) }
    var showOtp by remember { mutableStateOf(false) }
    var showSessionExpired by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var pendingBookingId by remember { mutableStateOf("") }
    var pendingPaymentAmount by remember { mutableStateOf(0) }
    var paymentMethod by remember { mutableStateOf("easypaisa") }
    var paymentSubmitting by remember { mutableStateOf(false) }
    var paymentSubmitted by remember { mutableStateOf(false) }
    var paymentMessage by remember { mutableStateOf("") }
    var ticketReference by remember { mutableStateOf("") }
    var ticketFare by remember { mutableStateOf(0) }
    var liveBookings by remember { mutableStateOf<List<SupabaseApi.Booking>>(emptyList()) }
    var bookingsLoading by remember { mutableStateOf(false) }
    var bookingsError by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var authEmail by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authName by remember { mutableStateOf("") }
    var authPhone by remember { mutableStateOf("") }
    var authIsSignup by remember { mutableStateOf(false) }
    var phoneOtpMode by remember { mutableStateOf(false) }
    var phoneOtpSent by remember { mutableStateOf(false) }
    var phoneOtpCode by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<SupabaseApi.Session?>(initialSession) }
    var driverStatus by remember { mutableStateOf("not_submitted") }
    var registeredVehicle by remember { mutableStateOf<SupabaseApi.Vehicle?>(null) }
    var driverTrips by remember { mutableStateOf<List<SupabaseApi.Trip>>(emptyList()) }
    var liveTrips by remember { mutableStateOf<List<SupabaseApi.Trip>>(emptyList()) }
    var tripsLoading by remember { mutableStateOf(false) }
    var tripsError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val locationShareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val location = try {
            if (permissions.values.any { it }) {
                val manager = appContext.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null
        } catch (_: SecurityException) { null } catch (_: Exception) { null }
        val rideToShare = selectedRide ?: sampleRides[0]
        val mapLink = if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "https://www.google.com/maps/dir/?api=1&origin=Khuzdar&destination=" + java.net.URLEncoder.encode(rideToShare.city, "UTF-8")
        }
        val text = "OTS Transport trip: ${rideToShare.route}\nBooking reference: ${ticketReference.ifBlank { "not booked yet" }}\nMy location / route: $mapLink"
        val shareIntent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        appContext.startActivity(Intent.createChooser(shareIntent, "Share OTS trip location"))
    }

    LaunchedEffect(oauthCallback) {
        val callback = oauthCallback ?: return@LaunchedEffect
        authBusy = true
        authMessage = ""
        try {
            val signedIn = SupabaseApi.completeGoogleOAuth(appContext, callback)
            val profile = SupabaseApi.getProfile(signedIn)
            if (profile == null) {
                SupabaseApi.saveProfile(signedIn, signedIn.email.substringBefore("@").ifBlank { "OTS passenger" }, "", role)
            } else {
                role = if (profile.optString("account_type") == "driver") "Driver" else "Passenger"
            }
            SessionStore.save(appContext, signedIn)
            session = signedIn
            showOtp = false
            notice = if (language == "ur") "Google کے ذریعے OTS میں سائن اِن ہو گیا۔" else "You are signed in to OTS with Google."
        } catch (e: Exception) {
            authMessage = "Google sign-in: " + (e.message ?: "Unable to complete sign-in. Check OAuth setup.")
            showOtp = true
        } finally {
            authBusy = false
            onOAuthCallbackHandled()
        }
    }

    LaunchedEffect(Unit) {
        val stored = initialSession
        if (stored != null) {
            try {
                val refreshed = SupabaseApi.refreshSession(stored)
                SessionStore.save(appContext, refreshed)
                session = refreshed
            } catch (_: Exception) {
                SessionStore.clear(appContext)
                session = null
                showSessionExpired = true
            }
        }
    }
    LaunchedEffect(session?.userId, role, tab) {
        val active = session
        if (active != null && role == "Driver" && tab == "Home") {
            driverStatus = try { SupabaseApi.getDriverStatus(active) } catch (_: Exception) { "unknown" }
            registeredVehicle = try { SupabaseApi.getRegisteredVehicle(active) } catch (_: Exception) { null }
            driverTrips = if (driverStatus == "approved") try { SupabaseApi.getDriverTrips(active) } catch (_: Exception) { emptyList() } else emptyList()
        }
    }
    LaunchedEffect(tab, session?.userId) {
        if (tab == "Rides") {
            tripsLoading = true
            tripsError = ""
            try {
                liveTrips = SupabaseApi.getApprovedTrips(session)
            } catch (e: Exception) {
                liveTrips = emptyList()
                tripsError = e.message ?: "Unable to load trips. Check your connection."
            } finally {
                tripsLoading = false
            }
        }
        if (tab == "Bookings") {
            val active = session
            if (active == null) {
                liveBookings = emptyList()
            } else {
                bookingsLoading = true
                bookingsError = ""
                try {
                    liveBookings = SupabaseApi.getBookings(active)
                } catch (e: Exception) {
                    bookingsError = e.message ?: "Unable to load your bookings."
                } finally {
                    bookingsLoading = false
                }
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = showTicket) { showTicket = false; tab = "Bookings" }
    androidx.activity.compose.BackHandler(enabled = showPayment && !paymentSubmitting) { showPayment = false; tab = "Bookings" }
    androidx.activity.compose.BackHandler(enabled = showOtp && !authBusy) { showOtp = false }
    androidx.activity.compose.BackHandler(enabled = tab == "Seat" && !showTicket) { tab = "Rides" }
    androidx.activity.compose.BackHandler(enabled = !showTicket && !showOtp && tab != "Seat" && (tab != "Home" || role == "Driver")) {
        if (tab != "Home") tab = "Home" else role = "Passenger"
    }

    MaterialTheme(
        // Dark entered text and readable grey placeholders on white surfaces.
        colorScheme = lightColorScheme(
            primary = Ink, secondary = Mint, background = Paper, surface = Color.White,
            onPrimary = Color.White, onBackground = Ink, onSurface = Ink,
            onSurfaceVariant = Color(0xFF4B5563), outline = Color(0xFF667085)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize()) {
                if (!showTicket && tab != "Seat" && (tab != "Home" || role == "Driver")) {
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (tab != "Home") tab = "Home" else role = "Passenger" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = if (language == "ur") "واپس" else "Back", tint = Ink)
                        }
                        Text(when (tab) {
                            "Rides" -> if (language == "ur") "دستیاب سفر" else "Find rides"
                            "Bookings" -> if (language == "ur") "میرے ٹکٹ" else "My tickets"
                            "Profile" -> if (language == "ur") "پروفائل" else "Profile"
                            "Settings" -> if (language == "ur") "ترتیبات" else "Settings"
                            else -> if (language == "ur") "ڈرائیور مرکز" else "Driver centre"
                        }, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    showTicket -> TicketScreen(
                        isUrdu = language == "ur",
                        ride = selectedRide ?: sampleRides[0],
                        seat = selectedSeat,
                        fare = if (ticketFare > 0) ticketFare else fareFor(selectedRide ?: sampleRides[0], selectedSeat),
                        reference = ticketReference,
                        onBack = { showTicket = false; tab = "Bookings" },
                        onShareLocation = { locationShareLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        onSos = { appContext.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122"))) }
                    )
                    showPayment -> PaymentScreen(
                        isUrdu = language == "ur",
                        amount = pendingPaymentAmount,
                        bookingId = pendingBookingId,
                        method = paymentMethod,
                        onMethod = { paymentMethod = it },
                        submitting = paymentSubmitting,
                        submitted = paymentSubmitted,
                        message = paymentMessage,
                        onBack = { showPayment = false; tab = "Bookings" },
                        onSubmit = { method, reference ->
                            val active = session
                            if (active == null) {
                                paymentMessage = "Your session expired. Sign in again before submitting the payment reference."
                            } else {
                                scope.launch {
                                    paymentSubmitting = true
                                    paymentMessage = ""
                                    try {
                                        SupabaseApi.submitPaymentReference(active, pendingBookingId, method, reference, pendingPaymentAmount)
                                        paymentSubmitted = true
                                        paymentMessage = "Your transaction reference was submitted. OTS will verify it manually before confirming your ticket."
                                        try { liveBookings = SupabaseApi.getBookings(active) } catch (_: Exception) {}
                                    } catch (e: Exception) {
                                        paymentMessage = e.message ?: "The payment reference could not be submitted. Please check it and retry."
                                    } finally {
                                        paymentSubmitting = false
                                    }
                                }
                            }
                        }
                    )
                    tab == "Rides" -> RidesScreen(isUrdu = language == "ur", route = selectedRoute, trips = liveTrips, loading = tripsLoading, error = tripsError, onRoute = { selectedRoute = it }, onSelect = { selectedRide = it; selectedDrop = it.city; selectedSeat = "Seat 1"; tab = "Seat" })
                    tab == "Seat" -> SeatScreen(isUrdu = language == "ur", ride = selectedRide ?: sampleRides[0], seat = selectedSeat, onSeat = { selectedSeat = it }, drop = selectedDrop, onDrop = { selectedDrop = it }, luggage = luggage, onLuggage = { luggage = it }, onBack = { tab = "Rides" }, onMap = {
                        val destination = java.net.URLEncoder.encode((selectedRide?.city ?: "Karachi"), "UTF-8")
                        appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&origin=Khuzdar&destination=$destination&travelmode=driving")))
                    }, onBook = {
                        val trip = selectedRide
                        val active = session
                        if (trip?.tripId == null) {
                            notice = "Only trips published through OTS are bookable. Please choose a currently listed trip."
                        } else if (active == null) {
                            authMessage = "Sign in before confirming a booking."
                            showOtp = true
                        } else {
                            scope.launch {
                                try {
                                    val bookingId = SupabaseApi.createBooking(active, trip.tripId, selectedSeat, fareFor(trip, selectedSeat), trip.pickupPoint, trip.dropoffPoint ?: trip.city, luggage)
                                    pendingBookingId = bookingId
                                    pendingPaymentAmount = fareFor(trip, selectedSeat)
                                    paymentMethod = "easypaisa"
                                    paymentSubmitted = false
                                    paymentMessage = ""
                                    showPayment = true
                                    try { liveBookings = SupabaseApi.getBookings(active) } catch (_: Exception) {}
                                    notice = "Seat held for 15 minutes. Complete the payment steps to submit your transaction reference."
                                } catch (e: Exception) {
                                    notice = e.message ?: "The booking could not be completed. Please try again."
                                }
                            }
                        }
                    })
                    tab == "Bookings" -> BookingsScreen(isUrdu = language == "ur", signedIn = session != null, bookings = liveBookings, loading = bookingsLoading, error = bookingsError, onBrowse = { tab = "Rides" }, onLogin = { authMessage = "Sign in to view your bookings."; showOtp = true }, onTicket = { booking ->
                        selectedRide = Ride(booking.destination, "${booking.origin} → ${booking.destination}", booking.departureAt.replace("T", " ").take(16), "OTS vehicle", "OTS driver", booking.fareTotalPkr, 0, "—", booking.status.uppercase(), booking.id)
                        selectedSeat = booking.seatLabels.ifBlank { "Seat assigned by driver" }
                        ticketFare = booking.fareTotalPkr
                        ticketReference = booking.id
                        showTicket = true
                    }, onPay = { booking ->
                        pendingBookingId = booking.id
                        pendingPaymentAmount = booking.fareTotalPkr
                        paymentMethod = booking.paymentMethod.ifBlank { "easypaisa" }
                        paymentSubmitted = false
                        paymentMessage = ""
                        showPayment = true
                    }, onCancel = { booking ->
                        val active = session
                        if (active != null) scope.launch {
                            try {
                                if (booking.status == "pending_payment") {
                                    SupabaseApi.cancelPendingPaymentBooking(active, booking.id)
                                    notice = "Unpaid seat hold cancelled."
                                } else {
                                    SupabaseApi.requestBookingCancellation(active, booking.id)
                                    notice = "Cancellation request sent. A confirmed trip is cancelled only after OTS reviews it."
                                }
                                liveBookings = SupabaseApi.getBookings(active)
                            } catch (e: Exception) { notice = e.message ?: "Could not cancel this booking." }
                        }
                    })
                    tab == "Profile" -> ProfileScreen(isUrdu = language == "ur", role = role, onRole = { role = it }, onLogin = { authMessage = ""; showOtp = true }, signedIn = session != null, email = session?.email.orEmpty(), onSignOut = { SessionStore.clear(appContext); session = null; driverStatus = "not_submitted"; notice = "You have signed out." })
                    tab == "Settings" -> SettingsScreen(
                        isUrdu = language == "ur",
                        signedIn = session != null,
                        email = session?.email.orEmpty(),
                        onChooseLanguage = { showLanguagePicker = true },
                        onAuth = { chosenRole, signup -> role = chosenRole; authIsSignup = signup; authMessage = ""; showOtp = true },
                        onSignOut = { SessionStore.clear(appContext); session = null; driverStatus = "not_submitted"; notice = if (language == "ur") "آپ سائن آؤٹ ہو گئے ہیں۔" else "You have signed out." }
                    )
                    role == "Driver" && tab == "Home" -> DriverScreen(
                        isUrdu = language == "ur",
                        session = session,
                        status = driverStatus,
                        trips = driverTrips,
                        vehicle = registeredVehicle,
                        onLogin = { authMessage = "Sign in or create an account before submitting a driver application."; showOtp = true },
                        onUpdateVehicle = { vehicle, make, model, year, plate, color, seats, luggageCapacity, photos ->
                            val active = session
                            if (active != null) scope.launch {
                                try {
                                    SupabaseApi.updateRegisteredVehicle(active, vehicle.id, make, model, year, plate, color, seats, luggageCapacity, appContext, photos)
                                    driverStatus = "pending"
                                    registeredVehicle = SupabaseApi.getRegisteredVehicle(active)
                                    driverTrips = emptyList()
                                    notice = "Vehicle details updated and sent for reapproval. Publishing is locked until the administrator approves the changes."
                                } catch (e: Exception) {
                                    notice = e.message ?: "Vehicle details could not be updated."
                                }
                            }
                        },
                        onCancelTrip = { trip ->
                            val active = session
                            if (active != null) scope.launch {
                                try {
                                    SupabaseApi.cancelTrip(active, trip.id)
                                    driverTrips = SupabaseApi.getDriverTrips(active)
                                    notice = "Trip cancelled."
                                } catch (e: Exception) {
                                    notice = e.message ?: "This trip could not be cancelled. Trips with active bookings require OTS review."
                                }
                            }
                        },
                        onEditTrip = { trip, destination, pickup, dropoff, date, time, arrivalText, fare, seats ->
                            val active = session
                            val departure = toDepartureIso(date, time)
                            val arrival = if (arrivalText.isBlank()) null else {
                                val parts = arrivalText.split(" ")
                                if (parts.size == 2) toDepartureIso(parts[0], parts[1]) else null
                            }
                            if (active == null) {
                                authMessage = "Please sign in first."
                                showOtp = true
                            } else if (departure == null) {
                                notice = "Enter a valid future departure date and time."
                            } else if (arrivalText.isNotBlank() && (arrival == null || arrival <= departure)) {
                                notice = "Estimated arrival must be a valid date/time after departure."
                            } else {
                                scope.launch {
                                    try {
                                        SupabaseApi.updateTrip(active, trip.id, destination, pickup, dropoff, departure, arrival, fare, seats)
                                        driverTrips = SupabaseApi.getDriverTrips(active)
                                        notice = "Trip updated."
                                    } catch (e: Exception) {
                                        notice = e.message ?: "Trip changes were blocked. Trips with active bookings need OTS review."
                                    }
                                }
                            }
                        },
                        onPublishTrip = { destination, pickup, dropoff, date, time, arrivalText, fare, seats ->
                            val active = session
                            val departure = toDepartureIso(date, time)
                            val arrival = if (arrivalText.isBlank()) null else {
                                val parts = arrivalText.split(" ")
                                if (parts.size == 2) toDepartureIso(parts[0], parts[1]) else null
                            }
                            if (active == null) {
                                authMessage = "Please sign in first."
                                showOtp = true
                            } else if (departure == null) {
                                notice = "Enter a valid future departure date as YYYY-MM-DD and time as HH:MM (24-hour)."
                            } else if (arrivalText.isNotBlank() && (arrival == null || arrival <= departure)) {
                                notice = "Estimated arrival must be a valid date/time after departure, using YYYY-MM-DD HH:MM."
                            } else {
                                scope.launch {
                                    try {
                                        val vehicle = SupabaseApi.getApprovedVehicle(active) ?: throw IllegalStateException("Your registered vehicle has not been approved yet.")
                                        if (seats > vehicle.passengerSeats) throw IllegalStateException("This vehicle is approved for only ${vehicle.passengerSeats} passenger seats.")
                                        val tripId = SupabaseApi.publishTrip(active, vehicle.id, destination, pickup, dropoff, departure, arrival, fare, seats)
                                        notice = "Trip published successfully. Reference: " + tripId.take(8).uppercase()
                                        try { driverTrips = SupabaseApi.getDriverTrips(active) } catch (_: Exception) { }
                                    } catch (e: Exception) {
                                        notice = e.message ?: "The trip could not be published."
                                    }
                                }
                            }
                        },
                        onSubmit = { name, phone, year, make, model, plate, seats, color, luggageCapacity, driverPhoto, cnicDocument, vehiclePhotos ->
                            val active = session
                            if (active == null) {
                                authMessage = "Please sign in first."
                                showOtp = true
                            } else {
                                scope.launch {
                                    driverStatus = "submitting"
                                    try {
                                        SupabaseApi.submitDriverApplication(active, name, phone, make, model, year, plate, color, seats, "Car", luggageCapacity, appContext, driverPhoto, cnicDocument, vehiclePhotos)
                                        driverStatus = "pending"
                                        notice = "Your driver and single-vehicle application has been submitted. You cannot publish trips until the administrator approves it."
                                    } catch (e: Exception) {
                                        driverStatus = "not_submitted"
                                        notice = e.message ?: "The application could not be submitted. Please check your connection and try again."
                                    }
                                }
                            }
                        }
                    )
                    else -> HomeScreen(isUrdu = language == "ur", role = role, onRole = { role = it }, onRoute = { selectedRoute = it; tab = "Rides" }, onSearch = { tab = "Rides" }, onDriver = { role = "Driver"; tab = "Home" }, onAccount = { chosenRole, signup -> role = chosenRole; authIsSignup = signup; authMessage = ""; showOtp = true })
                }
                }
                if (!showTicket && tab != "Seat") {
                    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                        listOf(
                            Triple("Home", Icons.Default.Home, if (language == "ur") "ہوم" else "Home"),
                            Triple("Rides", Icons.Default.Route, if (language == "ur") "سفر" else "Rides"),
                            Triple("Bookings", Icons.Default.ConfirmationNumber, if (language == "ur") "ٹکٹ" else "Tickets"),
                            Triple("Profile", Icons.Default.Person, if (language == "ur") "پروفائل" else "Profile"),
                            Triple("Settings", Icons.Default.Settings, if (language == "ur") "ترتیبات" else "Settings")
                        ).forEach { (key, icon, label) ->
                            NavigationBarItem(selected = tab == key, onClick = { tab = key; showTicket = false }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Navy, indicatorColor = Color(0xFFFFE6BD), selectedTextColor = Navy))
                        }
                    }
                }
            }
            if (showSessionExpired) AlertDialog(
                onDismissRequest = { showSessionExpired = false },
                title = { Text(if (language == "ur") "سیشن ختم ہو گیا" else "Session expired") },
                text = { Text(if (language == "ur") "سیکیورٹی کے لیے دوبارہ سائن اِن کریں۔ آپ کی محفوظ کردہ بکنگز حذف نہیں ہوئیں۔" else "For your security, please sign in again. Your saved bookings have not been deleted.") },
                confirmButton = {
                    Button(onClick = {
                        showSessionExpired = false
                        authMessage = if (language == "ur") "دوبارہ سائن اِن کریں۔" else "Please sign in again."
                        authIsSignup = false
                        showOtp = true
                    }) { Text(if (language == "ur") "سائن اِن" else "Sign in") }
                },
                dismissButton = {
                    TextButton(onClick = { showSessionExpired = false }) { Text(if (language == "ur") "بعد میں" else "Later") }
                }
            )
            if (showOtp) AlertDialog(
                onDismissRequest = { if (!authBusy) showOtp = false },
                title = { Text(if (language == "ur") { if (authIsSignup) "OTS اکاؤنٹ بنائیں" else "OTS میں سائن اِن کریں" } else { if (authIsSignup) "Create your OTS account" else "Sign in to OTS" }) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (phoneOtpMode) {
                            "Phone verification uses Supabase SMS. An SMS provider must be enabled in the Supabase Auth settings before codes can be delivered."
                        } else if (role == "Driver") {
                            "Driver access • administrator approval is required before trip publishing."
                        } else {
                            "Passenger access • sign in with email and password, or choose phone verification."
                        })
                        if (authIsSignup || phoneOtpMode) {
                            OutlinedTextField(value = authName, onValueChange = { authName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        if (authIsSignup || phoneOtpMode) {
                            OutlinedTextField(value = authPhone, onValueChange = { authPhone = it }, label = { Text(if (phoneOtpMode) "Pakistani mobile number" else "Phone number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        if (!phoneOtpMode) {
                            OutlinedTextField(value = authEmail, onValueChange = { authEmail = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = authPassword, onValueChange = { authPassword = it }, label = { Text("Password (at least 6 characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        } else if (phoneOtpSent) {
                            OutlinedTextField(value = phoneOtpCode, onValueChange = { phoneOtpCode = it.filter(Char::isDigit).take(10) }, label = { Text("SMS verification code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        if (authMessage.isNotBlank()) Text(authMessage, color = if (authMessage.startsWith("Account created")) Color(0xFF14785E) else Color(0xFF9A4D20), fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = !authBusy, onClick = { authIsSignup = !authIsSignup; authMessage = "" }) {
                                Text(if (language == "ur") { if (authIsSignup) "میرا اکاؤنٹ موجود ہے؟ سائن اِن" else "نیا اکاؤنٹ بنائیں" } else { if (authIsSignup) "Already have an account? Sign in" else "New here? Create an account" })
                            }
                        }
                        TextButton(enabled = !authBusy, onClick = {
                            phoneOtpMode = !phoneOtpMode
                            phoneOtpSent = false
                            phoneOtpCode = ""
                            authMessage = ""
                        }) {
                            Text(if (phoneOtpMode) "Use email and password instead" else "Use phone verification (SMS)")
                        }
                        if (authBusy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Connecting securely…", fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !authBusy, onClick = {
                        if (phoneOtpMode) {
                            if (authPhone.isBlank()) {
                                authMessage = "Enter your Pakistani mobile number."
                            } else if (!phoneOtpSent) {
                                scope.launch {
                                    authBusy = true
                                    authMessage = ""
                                    try {
                                        SupabaseApi.sendPhoneOtp(authPhone)
                                        phoneOtpSent = true
                                        authMessage = "SMS code requested. Check your messages; delivery requires an active Supabase SMS provider."
                                    } catch (e: Exception) {
                                        authMessage = e.message ?: "SMS code could not be sent. Check the Supabase SMS provider configuration."
                                    } finally { authBusy = false }
                                }
                            } else if (phoneOtpCode.isBlank()) {
                                authMessage = "Enter the SMS verification code."
                            } else {
                                scope.launch {
                                    authBusy = true
                                    authMessage = ""
                                    try {
                                        val signedIn = SupabaseApi.verifyPhoneOtp(authPhone, phoneOtpCode)
                                        val profile = SupabaseApi.getProfile(signedIn)
                                        if (profile == null) {
                                            SupabaseApi.saveProfile(signedIn, authName.ifBlank { authPhone }, authPhone, role)
                                        } else {
                                            role = if (profile.optString("account_type") == "driver") "Driver" else "Passenger"
                                        }
                                        SessionStore.save(appContext, signedIn)
                                        session = signedIn
                                        showOtp = false
                                        notice = "Your phone number is verified and you are signed in to OTS Transport."
                                    } catch (e: Exception) {
                                        authMessage = e.message ?: "Phone verification failed. Check the code and try again."
                                    } finally { authBusy = false }
                                }
                            }
                        } else if (authEmail.isBlank() || authPassword.length < 6 || (authIsSignup && authName.isBlank())) {
                            authMessage = if (authIsSignup) "Enter your name, a valid email, and a password of at least 6 characters." else "Enter your email and a password of at least 6 characters."
                        } else {
                            scope.launch {
                                authBusy = true
                                authMessage = ""
                                try {
                                    if (authIsSignup) {
                                        val result = SupabaseApi.signUp(authEmail, authPassword)
                                        val created = result.first
                                        if (created == null) {
                                            authMessage = result.second
                                        } else {
                                            SupabaseApi.saveProfile(created, authName, authPhone, role)
                                            SessionStore.save(appContext, created)
                                            session = created
                                            showOtp = false
                                            notice = "Your OTS account is ready."
                                        }
                                    } else {
                                        val signedIn = SupabaseApi.signIn(authEmail, authPassword)
                                        val profile = SupabaseApi.getProfile(signedIn)
                                        if (profile == null) {
                                            val name = authName.ifBlank { authEmail.substringBefore("@") }
                                            SupabaseApi.saveProfile(signedIn, name, authPhone, role)
                                        } else {
                                            authName = profile.optString("full_name")
                                            authPhone = profile.optString("phone")
                                            role = if (profile.optString("account_type") == "driver") "Driver" else "Passenger"
                                        }
                                        SessionStore.save(appContext, signedIn)
                                        session = signedIn
                                        showOtp = false
                                        notice = "You are signed in to OTS Transport."
                                    }
                                } catch (e: Exception) {
                                    authMessage = e.message ?: "Unable to connect. Please try again."
                                } finally {
                                    authBusy = false
                                }
                            }
                        }
                    }) { Text(if (phoneOtpMode) { if (phoneOtpSent) "Verify SMS code" else "Send SMS code" } else if (language == "ur") { if (authIsSignup) "اکاؤنٹ بنائیں" else "سائن اِن" } else { if (authIsSignup) "Create account" else "Sign in" }) }
                    OutlinedButton(
                        enabled = !authBusy,
                        onClick = {
                            try {
                                val url = SupabaseApi.beginGoogleOAuth(appContext)
                                appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (e: Exception) {
                                authMessage = e.message ?: "Google sign-in could not be started."
                            }
                        }
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (language == "ur") "Google کے ساتھ جاری رکھیں" else "Continue with Google")
                    }
                    }
                },
                dismissButton = {
                    TextButton(enabled = !authBusy, onClick = { showOtp = false }) { Text(if (language == "ur") "واپس" else "Back") }
                }
            )
            if (notice.isNotBlank()) AlertDialog(onDismissRequest = { notice = "" }, title = { Text("OTS Transport") }, text = { Text(notice) }, confirmButton = { TextButton(onClick = { notice = "" }) { Text(if (language == "ur") "ٹھیک ہے" else "OK") } })
            if (language.isBlank() || showLanguagePicker) {
                AlertDialog(
                    onDismissRequest = { if (language.isNotBlank()) showLanguagePicker = false },
                    title = { Text(if (language.isBlank()) "Choose your language / اپنی زبان منتخب کریں" else if (language == "ur") "زبان تبدیل کریں" else "Change language", color = Ink) },
                    text = { Text(if (language.isBlank()) "Choose once to get started. You can change this later in Settings.\nشروع کرنے کے لیے زبان منتخب کریں۔ بعد میں ترتیبات سے تبدیل کر سکتے ہیں۔" else "English / اردو", color = Ink) },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                language = "en"
                                appContext.getSharedPreferences("ots_settings", android.content.Context.MODE_PRIVATE).edit().putString("language", "en").apply()
                                showLanguagePicker = false
                            }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("English") }
                            Button(onClick = {
                                language = "ur"
                                appContext.getSharedPreferences("ots_settings", android.content.Context.MODE_PRIVATE).edit().putString("language", "ur").apply()
                                showLanguagePicker = false
                            }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("اردو") }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    isUrdu: Boolean,
    signedIn: Boolean,
    email: String,
    onChooseLanguage: () -> Unit,
    onAuth: (String, Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (isUrdu) "ترتیبات" else "Settings", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isUrdu) "زبان" else "Language", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (isUrdu) "موجودہ زبان: اردو" else "Current language: English", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = onChooseLanguage, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Language, null); Spacer(Modifier.width(8.dp)); Text(if (isUrdu) "زبان تبدیل کریں" else "Change language")
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isUrdu) "اکاؤنٹ" else "Account", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (signedIn) {
                    Text(email, color = Ink, fontSize = 13.sp)
                    Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                        Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text(if (isUrdu) "سائن آؤٹ" else "Sign out")
                    }
                } else {
                    Text(if (isUrdu) "مسافر یا ڈرائیور کے طور پر سائن اِن کریں یا نیا اکاؤنٹ بنائیں۔" else "Choose whether you are a passenger or a driver, then sign in or create an account.", color = Muted, fontSize = 12.sp)
                    Button(onClick = { onAuth("Passenger", false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text(if (isUrdu) "مسافر لاگ اِن" else "Passenger Login") }
                    OutlinedButton(onClick = { onAuth("Passenger", true) }, modifier = Modifier.fillMaxWidth()) { Text(if (isUrdu) "مسافر سائن اَپ" else "Passenger Sign Up") }
                    Button(onClick = { onAuth("Driver", false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.DirectionsCar, null); Spacer(Modifier.width(8.dp)); Text(if (isUrdu) "ڈرائیور لاگ اِن" else "Driver Login") }
                    OutlinedButton(onClick = { onAuth("Driver", true) }, modifier = Modifier.fillMaxWidth()) { Text(if (isUrdu) "ڈرائیور سائن اَپ" else "Driver Sign Up") }
                }
            }
        }
        Text(if (isUrdu) "OTS Transport • محفوظ اور آسان انٹر سٹی سفر" else "OTS Transport • Your intercity journey, thoughtfully connected", color = Muted, fontSize = 11.sp)
    }
}

private fun formatTripTime(departureAt: String, arrivalAt: String?): String {
    val departure = departureAt.replace("T", " ").take(16)
    val arrival = arrivalAt?.replace("T", " ")?.take(16)
    return if (arrival.isNullOrBlank()) departure else "$departure • Arrives $arrival"
}

private fun toDepartureIso(date: String, time: String): String? {
    return try {
        val zone = TimeZone.getTimeZone("Asia/Karachi")
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            isLenient = false
            timeZone = zone
        }
        val parsed = parser.parse("$date $time") ?: return null
        if (parsed.time <= System.currentTimeMillis()) return null
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = zone }.format(parsed)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun HomeScreen(isUrdu: Boolean, role: String, onRole: (String) -> Unit, onRoute: (String) -> Unit, onSearch: () -> Unit, onDriver: () -> Unit, onAccount: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FC)).verticalScroll(rememberScrollState())) {
        // Redesigned to follow the blue-and-white reference screen supplied by the user.
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 42.dp, bottomEnd = 42.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1554D1), Color(0xFF0C3EAA), Color(0xFF092C7A))))
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 64.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OtsLogo(46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OTS TRANSPORT", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(if (isUrdu) "خضدار • انٹر سٹی سفر" else "KHUZDAR • INTERCITY TRAVEL", color = Color(0xFFD8E5FF), fontSize = 11.sp)
                    }
                    IconButton(onClick = { onAccount(role, false) }) {
                        Icon(Icons.Default.Person, contentDescription = "Account", tint = Color.White, modifier = Modifier.size(25.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (isUrdu) "خوش آمدید" else "WELCOME TO OTS", color = Color(0xFFFFD17A), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(if (isUrdu) "آج کہاں جانا ہے؟" else "Ready for your next journey?", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (isUrdu) "آرام سے سفر کریں۔ اپنی نشست پہلے محفوظ کریں۔" else "Book your seat ahead. Travel with confidence.", color = Color(0xFFD8E5FF), fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleChip(if (isUrdu) "مسافر" else "Passenger", role == "Passenger") { onRole("Passenger") }
                    RoleChip(if (isUrdu) "ڈرائیور" else "Driver", role == "Driver") { onRole("Driver"); onDriver() }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onAccount("Passenger", true) }) { Text(if (isUrdu) "سائن اَپ" else "Sign up", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        // Floating route search overlaps the blue header, like the supplied reference.
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).offset(y = (-28).dp),
            shape = RoundedCornerShape(50.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth().clickable { onSearch() }.padding(horizontal = 20.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF1554D1), modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(13.dp))
                Text(if (isUrdu) "کہاں جانا ہے؟" else "Where would you like to go?", color = Color(0xFF7B879B), fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Schedule, null, tint = Color(0xFF1554D1), modifier = Modifier.size(23.dp))
            }
        }
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 0.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Card(
                Modifier.fillMaxWidth().height(152.dp).clickable { onSearch() },
                shape = RoundedCornerShape(27.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111A32))
            ) {
                Row(Modifier.fillMaxSize().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = Color(0xFF30394D), shape = RoundedCornerShape(50.dp)) {
                            Text(if (isUrdu) "روزانہ سفر" else "DAILY RIDES", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Text(if (isUrdu) "OTS انٹر سٹی" else "Intercity Travel", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (isUrdu) "منظور شدہ ڈرائیور • واضح کرایہ" else "Cars • Vans • Coasters • Buses", color = Color(0xFFCFD6E3), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(70.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF29334C)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.DirectionsCar, null, tint = Color.White, modifier = Modifier.size(39.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf(Icons.Default.DirectionsCar, Icons.Default.AirportShuttle, Icons.Default.DirectionsBus).forEach { vehicleIcon ->
                                Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF35435D)), contentAlignment = Alignment.Center) {
                                    Icon(vehicleIcon, null, tint = Color(0xFFFFD17A), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(
                    Modifier.weight(1f).height(190.dp).clickable { onSearch() },
                    shape = RoundedCornerShape(25.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1554D1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QrCode2, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Text(if (isUrdu) "سفر تلاش کریں" else "Find Your\nRide", color = Color.White, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (isUrdu) "جلدی سے نشست بک کریں" else "Compare cars, vans, coasters and buses", color = Color(0xFFDDE8FF), fontSize = 11.sp)
                    }
                }
                Card(
                    Modifier.weight(1f).height(190.dp).clickable { onSearch() },
                    shape = RoundedCornerShape(25.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFFEAF0FF)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF1554D1), modifier = Modifier.size(31.dp))
                        }
                        Text(if (isUrdu) "سفر بک کریں" else "Book a Journey", color = Color(0xFF121A2B), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (isUrdu) "اپنی نشست منتخب کریں" else "Choose your vehicle and seats", color = Color(0xFF7A8496), fontSize = 11.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isUrdu) "پسندیدہ راستے" else "Popular routes", color = Color(0xFF121A2B), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onSearch) { Text(if (isUrdu) "سب دیکھیں" else "See all", color = Color(0xFF1554D1), fontWeight = FontWeight.Bold) }
            }
            val routes = if (isUrdu) listOf(Triple("Karachi", "ساحلی شہر", Icons.Default.LocationCity), Triple("Quetta", "پہاڑی راستہ", Icons.Default.Terrain), Triple("Sindh", "لاڑکانہ • رتو ڈیرو", Icons.Default.Route), Triple("Makran", "تربت • گوادر", Icons.Default.Waves)) else listOf(Triple("Karachi", "Coastal metropolis", Icons.Default.LocationCity), Triple("Quetta", "Mountain gateway", Icons.Default.Terrain), Triple("Sindh", "Larkana • Ratto Dero", Icons.Default.Route), Triple("Makran", "Turbat • Gwadar", Icons.Default.Waves))
            routes.forEachIndexed { index, route ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { onRoute(if (route.first == "Sindh") "Larkana" else if (route.first == "Makran") "Gwadar" else route.first) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).background(listOf(Color(0xFFE9F0FF), Color(0xFFE5F4FF), Color(0xFFECEBFF), Color(0xFFE1F7F3))[index]), contentAlignment = Alignment.Center) {
                        Icon(route.third, null, tint = Color(0xFF1554D1), modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (isUrdu) "خضدار → " else "Khuzdar → ") + route.first, color = Color(0xFF121A2B), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(route.second, color = Color(0xFF7B879B), fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = Color(0xFF1554D1))
                }
            }
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1554D1))) {
                Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(if (isUrdu) "دستیاب سفر تلاش کریں" else "Find available rides", fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("4", if (isUrdu) "راستے" else "Routes", Icons.Default.Route, Modifier.weight(1f))
                StatCard("OTS", if (isUrdu) "ڈرائیور کی جانچ" else "Driver review", Icons.Default.Verified, Modifier.weight(1f))
                StatCard("PKR", if (isUrdu) "واضح کرایہ" else "Clear fares", Icons.Default.Payments, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Gold else Color.White.copy(alpha = .12f), contentColor = if (selected) Ink else Color.White) {
        Text(label, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatCard(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(Color.White).padding(12.dp)) {
        Icon(icon, null, tint = Color(0xFFB17A2B), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp)); Text(value, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Text(label, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RidesScreen(
    isUrdu: Boolean,
    route: String,
    trips: List<SupabaseApi.Trip>,
    loading: Boolean,
    error: String,
    onRoute: (String) -> Unit,
    onSelect: (Ride) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (isUrdu) "اپنا سفر تلاش کریں" else "Find your journey", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (isUrdu) "منظور شدہ OTS ڈرائیوروں کے دستیاب سفر" else "Live trips by approved OTS drivers and vehicle operators", color = Muted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Karachi", "Quetta", "Larkana", "Gwadar").forEach { city ->
                FilterChip(selected = route == city, onClick = { onRoute(city) }, label = { Text(if (isUrdu && city == "All") "تمام" else city) }, leadingIcon = if (route == city) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null)
            }
        }
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(if (isUrdu) "دستیاب سفر لوڈ ہو رہے ہیں…" else "Loading available trips…", color = Muted, fontSize = 12.sp) }
        } else {
            val filtered = trips.filter { route == "All" || it.destination.equals(route, ignoreCase = true) }
            filtered.forEach { trip ->
                RideCard(
                    isUrdu = isUrdu,
                    ride = Ride(
                        city = trip.destination,
                        route = "${trip.origin} → ${trip.destination}",
                        time = formatTripTime(trip.departureAt, trip.arrivalAt),
                        car = "Verified vehicle",
                        driver = "Approved OTS driver",
                        price = trip.farePkr,
                        seats = trip.availableSeats,
                        rating = "—",
                        tag = "AVAILABLE",
                        tripId = trip.id,
                        totalSeats = trip.totalSeats,
                        pickupPoint = trip.pickupPoint,
                        vehicleType = vehicleTypeFor(trip.totalSeats),
                        dropoffPoint = trip.dropoffPoint
                    ),
                    onClick = {
                        onSelect(Ride(trip.destination, "${trip.origin} → ${trip.destination}", formatTripTime(trip.departureAt, trip.arrivalAt), "Verified vehicle", "Approved OTS driver", trip.farePkr, trip.availableSeats, "—", "AVAILABLE", trip.id, trip.totalSeats, trip.pickupPoint, trip.dropoffPoint, vehicleTypeFor(trip.totalSeats)))
                    }
                )
            }
            if (filtered.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Default.Route, null, tint = Navy2, modifier = Modifier.size(34.dp))
                        Text(if (error.isBlank()) { if (isUrdu) "ابھی کوئی سفر دستیاب نہیں" else "No journeys available yet" } else { if (isUrdu) "سفر لوڈ نہیں ہو سکے" else "We couldn’t load journeys" }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(if (error.isBlank()) { if (isUrdu) "اس راستے کے لیے ابھی سفر شائع نہیں ہوا۔ بعد میں دوبارہ دیکھیں۔" else "No trips are published for this route right now. Try another destination or check again later." } else error, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(if (isUrdu) "کرایہ اور نشستوں کی دستیابی سفر کی معلومات کے مطابق دکھائی جاتی ہے۔" else "Fares and seat availability are shown from each trip’s published details.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RideCard(isUrdu: Boolean, ride: Ride, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Navy), contentAlignment = Alignment.Center) { Icon(when (ride.vehicleType.lowercase()) { "bus" -> Icons.Default.DirectionsBus; "coaster", "van" -> Icons.Default.AirportShuttle; else -> Icons.Default.DirectionsCar }, contentDescription = ride.vehicleType, tint = Gold, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(ride.route, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(ride.time, color = Muted, fontSize = 11.sp) }
                Surface(color = Color(0xFFE4F7F0), shape = RoundedCornerShape(8.dp)) { Text(ride.tag, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = Color(0xFF11765E), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventSeat, null, tint = Navy2, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(if (isUrdu) "دستیاب نشستیں" else "Seats available", color = Muted, fontSize = 10.sp); Text("${ride.seats}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(if (isUrdu) "فی نشست" else "PER SEAT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text("PKR ${ride.price}", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Black) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF16876E), modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(ride.driver, color = Navy2, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("${ride.vehicleType} • ${ride.car}", color = Muted, fontSize = 10.sp) }
                Text("★ ${ride.rating}", color = Color(0xFF8B5A18), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text(if (isUrdu) "نشستیں منتخب کریں" else "View trip & choose seat"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun PaymentScreen(
    isUrdu: Boolean,
    amount: Int,
    bookingId: String,
    method: String,
    onMethod: (String) -> Unit,
    submitting: Boolean,
    submitted: Boolean,
    message: String,
    onBack: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var transactionReference by remember(bookingId) { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !submitting) { Icon(Icons.Default.ArrowBack, null, tint = Ink) }
            Column {
                Text(if (isUrdu) "ادائیگی کا طریقہ" else "Payment method", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("Booking ${bookingId.take(8).uppercase()}", color = Muted, fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TOTAL TO TRANSFER", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("PKR $amount", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Black)
                Text("Recipient account number", color = Color(0xFFB8C9D7), fontSize = 12.sp)
                Text("03312717330", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("Use this number in the selected Easypaisa or JazzCash app. Confirm the recipient details before sending money.", color = Color(0xFFD5E0E8), fontSize = 12.sp)
            }
        }
        Text("Choose transfer method", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = method == "easypaisa", onClick = { onMethod("easypaisa") }, label = { Text("Easypaisa") }, leadingIcon = if (method == "easypaisa") ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null)
            FilterChip(selected = method == "jazzcash", onClick = { onMethod("jazzcash") }, label = { Text("JazzCash") }, leadingIcon = if (method == "jazzcash") ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(if (submitted) "Payment reference submitted" else "After you transfer the money", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                if (submitted) {
                    Text("Your reference is waiting for an OTS administrator to check the actual transfer. Your ticket is not confirmed until that review is complete.", color = Muted, fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("1. Open ${if (method == "easypaisa") "Easypaisa" else "JazzCash"} on your phone.", color = Muted, fontSize = 13.sp)
                        Text("2. Transfer exactly PKR $amount to 03312717330.", color = Muted, fontSize = 13.sp)
                        Text("3. Enter the transaction reference shown by your payment app below.", color = Muted, fontSize = 13.sp)
                    }
                    OutlinedTextField(
                        value = transactionReference,
                        onValueChange = { transactionReference = it.take(120) },
                        label = { Text("Transaction reference / TID") },
                        placeholder = { Text("Enter the reference from your receipt") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !submitting
                    )
                    Text("This is a manual transfer flow, not an automatic wallet gateway. Do not submit until you have actually sent the money.", color = Color(0xFF8B5A18), fontSize = 11.sp)
                }
                if (message.isNotBlank()) Text(message, color = if (submitted) Color(0xFF16876E) else Color(0xFFB42318), fontSize = 12.sp)
                Button(
                    onClick = { if (submitted) onBack() else onSubmit(method, transactionReference.trim()) },
                    enabled = !submitting && (submitted || transactionReference.trim().length >= 4),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    if (submitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(if (submitted) "Back to my bookings" else "Submit payment reference", fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Seat holds expire after 15 minutes if no valid payment submission is made. OTS staff must verify transfers manually before a confirmed ticket is issued.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SeatCarPhoto(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.ots_seat_car)?.let { source ->
            // Crop the supplied reference down to the realistic top-down vehicle cabin.
            val left = 78.coerceIn(0, source.width - 1)
            val top = 42.coerceIn(0, source.height - 1)
            val width = 370.coerceAtMost(source.width - left)
            val height = 175.coerceAtMost(source.height - top)
            Bitmap.createBitmap(source, left, top, width, height).also { cropped ->
                if (cropped !== source) source.recycle()
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Realistic top-down car cabin with front and rear passenger seats",
            contentScale = ContentScale.FillBounds,
            modifier = modifier.clip(RoundedCornerShape(18.dp))
        )
    } else {
        Box(modifier.background(Color(0xFF24445A), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsCar, null, tint = Gold, modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
private fun SeatScreen(isUrdu: Boolean, ride: Ride, seat: String, onSeat: (String) -> Unit, drop: String, onDrop: (String) -> Unit, luggage: String, onLuggage: (String) -> Unit, onBack: () -> Unit, onMap: () -> Unit, onBook: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Ink) }
            Column {
                Text(if (isUrdu) "اپنی نشست منتخب کریں" else "Choose your seat", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(ride.route, color = Muted, fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = onMap, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Icon(Icons.Default.Map, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isUrdu) "نقشے پر راستہ دیکھیں" else "View route in Google Maps")
        }
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (isUrdu) "3D گاڑی اور نشستیں" else "3D RIDE & SEAT MAP", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                Text(if (isUrdu) "اپنی دستیاب نشست منتخب کریں" else "Choose any available passenger seat", color = Color(0xFFB8C9D7), fontSize = 10.sp)
                RideVehicle3D(Modifier.fillMaxWidth().height(154.dp), ride.vehicleType, ride.car)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ride.vehicleType.uppercase(), color = Gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text(ride.car, color = Color(0xFFD7E2E9), fontSize = 10.sp)
                    }
                    Text("${ride.totalSeats} passenger seats", color = Color(0xFFD7E2E9), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(if (isUrdu) "ڈرائیور کی نشست بک نہیں ہو سکتی" else "DRIVER SEAT • NOT BOOKABLE", color = Color(0xFFB8C9D7), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                val seatColumns = when {
                    ride.totalSeats >= 25 -> 4
                    ride.totalSeats >= 10 -> 3
                    else -> 2
                }
                (1..ride.totalSeats.coerceAtLeast(0)).toList().chunked(seatColumns).forEachIndexed { rowIndex, rowSeats ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        rowSeats.forEach { number ->
                            val label = "Seat $number"
                            SeatButton(label, seat == label, onSeat, true, Modifier.weight(1f))
                        }
                        repeat(seatColumns - rowSeats.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${ride.totalSeats} PASSENGER SEATS", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (isUrdu) "ڈرائیور کی نشست شامل نہیں" else "Driver seat excluded", color = Color(0xFFD7E2E9), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(Gold)); Spacer(Modifier.width(5.dp)); Text(if (isUrdu) "منتخب" else "Selected", color = Color.White, fontSize = 9.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF2B8B7F))); Spacer(Modifier.width(5.dp)); Text(if (isUrdu) "دستیاب" else "Available", color = Color.White, fontSize = 9.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF536273))); Spacer(Modifier.width(5.dp)); Text(if (isUrdu) "بک" else "Full", color = Color.White, fontSize = 9.sp) }
                }
                Text(if (isUrdu) "ڈرائیور کی نشست بک نہیں ہو سکتی۔ دستیابی بکنگ کے وقت تصدیق ہوتی ہے۔" else "Only passenger seats can be selected. Seat availability must be confirmed when you book.", color = Color(0xFFB8C9D7), fontSize = 10.sp)
            }
        }
        Text(if (isUrdu) "پک اَپ اور منزل" else "Pickup and drop-off", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(if (isUrdu) "پک اَپ" else "PICKUP", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(ride.pickupPoint, color = Ink, fontWeight = FontWeight.SemiBold) }
                Icon(Icons.Default.ArrowForward, null, tint = Gold)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Text(if (isUrdu) "منزل" else "DROP-OFF", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(ride.dropoffPoint ?: ride.city, color = Ink, fontWeight = FontWeight.SemiBold) }
            }
        }
        Text(if (isUrdu) "سامان" else "Luggage", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Small bag", "Medium bag", "Large box").forEach { item -> FilterChip(selected = luggage == item, onClick = { onLuggage(item) }, label = { Text(item, fontSize = 11.sp) }) } }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(if (isUrdu) "فی نشست کرایہ" else "FARE PER SEAT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("PKR ${fareFor(ride, seat)}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(if (isUrdu) "اس ورژن میں آن لائن ادائیگی نہیں لی جاتی۔" else "No online payment is collected in this version.", color = Muted, fontSize = 10.sp) }
                Button(onClick = onBook, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text(if (isUrdu) "ابھی بک کریں" else "Book now") }
            }
        }
        Text(if (isUrdu) "سفر سے پہلے ڈرائیور اور گاڑی کی تفصیلات اپنے ٹکٹ سے ملائیں۔" else "Before boarding, match the driver and vehicle details with your ticket.", color = Color(0xFF8B5A18), fontSize = 11.sp)
    }
}

@Composable
private fun RideVehicle3D(modifier: Modifier = Modifier, vehicleType: String, vehicleName: String) {
    val type = vehicleType.lowercase()
    val isBus = type.contains("bus") || type.contains("coaster")
    val isVan = type.contains("van")
    val bodyColor = when {
        isBus -> Color(0xFFD9E6F0)
        isVan -> Color(0xFFE4E9F1)
        else -> Color(0xFFE7EDF4)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Layered perspective body gives each vehicle category its own dimensional silhouette.
        Box(Modifier.fillMaxWidth(if (isBus) .94f else .78f).fillMaxHeight(.68f).offset(y = 8.dp)
            .clip(RoundedCornerShape(if (isBus) 18.dp else 30.dp)).background(Color(0xFF081321)))
        Row(Modifier.fillMaxWidth(if (isBus) .91f else .75f).fillMaxHeight(.62f)
            .clip(RoundedCornerShape(if (isBus) 17.dp else 28.dp)).background(bodyColor).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (!isBus) {
                Box(Modifier.width(if (isVan) 28.dp else 34.dp).fillMaxHeight(.82f)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFF8CB3CE)))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(if (isBus) 5 else if (isVan) 3 else 2) {
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF3E6683)))
                    }
                }
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))
                    .background(Color(0xFFB7C9D8)))
            }
            Box(Modifier.width(if (isBus) 15.dp else 23.dp).fillMaxHeight(.72f)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF49677D)))
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(if (isBus) .83f else .64f)
            .offset(y = (-5).dp), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(2) {
                Box(Modifier.size(if (isBus) 23.dp else 27.dp).clip(CircleShape)
                    .background(Color(0xFF101A26)).padding(5.dp)
                    .clip(CircleShape).background(Color(0xFF9BAFBE)))
            }
            if (isBus || isVan) {
                repeat(2) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF101A26))
                        .padding(4.dp).clip(CircleShape).background(Color(0xFF9BAFBE)))
                }
            }
        }
        Text(vehicleName, Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
            .clip(RoundedCornerShape(7.dp)).background(Color(0xFF142B40)).padding(horizontal = 9.dp, vertical = 4.dp),
            color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DriverSeatVisual(modifier: Modifier = Modifier, isUrdu: Boolean) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(56.dp).background(Brush.verticalGradient(listOf(Color(0xFF647789), Color(0xFF334B60))), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsCar, null, tint = Gold, modifier = Modifier.size(25.dp))
        }
        Text(if (isUrdu) "ڈرائیور" else "DRIVER", color = Color(0xFFD7E2E9), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(if (isUrdu) "بک نہیں" else "NOT BOOKABLE", color = Color(0xFF9FB1C0), fontSize = 6.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SeatButton(label: String, selected: Boolean, onSeat: (String) -> Unit, available: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(
                if (selected) listOf(Color(0xFFFFE0A1), Gold, Color(0xFFE6A84D))
                else if (available) listOf(Color(0xFF45A69A), Color(0xFF216E69), Color(0xFF164E50))
                else listOf(Color(0xFF697B8C), Color(0xFF4A5B6B), Color(0xFF354555))
            ))
            .clickable(enabled = available) { onSeat(label) }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Two raised cushions give each seat a more physical, car-seat appearance.
        Box(Modifier.fillMaxWidth().height(35.dp).padding(horizontal = 3.dp)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) .75f else .28f), Color.Black.copy(alpha = .15f))), RoundedCornerShape(11.dp))
            .padding(4.dp)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                if (selected) Color(0xFFFFF0CB) else Color(0xFFB7C6D1).copy(alpha = .9f),
                if (selected) Color(0xFFE7B15C) else Color(0xFF64798B)
            )), RoundedCornerShape(8.dp)))
        }
        Text(label, color = if (selected) Ink else Color.White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(if (selected) "SELECTED" else if (available) "AVAILABLE" else "FULL", color = if (selected) Ink else Color(0xFFD9E7ED), fontSize = 5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun fareFor(ride: Ride, seat: String): Int = ride.price



@Composable
private fun BookingsScreen(
    isUrdu: Boolean,
    signedIn: Boolean,
    bookings: List<SupabaseApi.Booking>,
    loading: Boolean,
    error: String,
    onBrowse: () -> Unit,
    onLogin: () -> Unit,
    onTicket: (SupabaseApi.Booking) -> Unit,
    onPay: (SupabaseApi.Booking) -> Unit,
    onCancel: (SupabaseApi.Booking) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (isUrdu) "آپ کی بکنگز" else "Your bookings", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (isUrdu) "آپ کے OTS اکاؤنٹ کی بکنگز اور منسوخی کی درخواستیں" else "Bookings and cancellation requests from your OTS account", color = Muted, fontSize = 12.sp)
        if (!signedIn) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Person, null, tint = Navy2, modifier = Modifier.size(40.dp))
                    Text("Sign in to see your tickets", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = onLogin, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Sign in") }
                }
            }
        } else if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(9.dp)); Text("Loading your bookings…", color = Muted, fontSize = 12.sp) }
        } else if (bookings.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Luggage, null, tint = Navy2, modifier = Modifier.size(48.dp))
                    Text(if (error.isBlank()) "No bookings yet" else "Unable to load bookings", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (error.isBlank()) "Your confirmed OTS tickets will appear here after you book a published trip." else error, color = Muted, fontSize = 12.sp)
                    Button(onClick = onBrowse, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Find a ride") }
                }
            }
        } else {
            bookings.forEach { booking ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(booking.status.replace("_", " ").uppercase(), color = if (booking.status == "confirmed") Color(0xFF16876E) else Color(0xFF9B681F), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            Text("PKR ${booking.fareTotalPkr}", color = Ink, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("${booking.origin} → ${booking.destination}", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text(formatTripTime(booking.departureAt, booking.arrivalAt), color = Muted, fontSize = 12.sp)
                        Text("Seat: ${booking.seatLabels.ifBlank { "Not assigned" }}", color = Navy2, fontSize = 12.sp)
                        if (booking.status == "confirmed") {
                            Button(onClick = { onTicket(booking) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.ConfirmationNumber, null); Spacer(Modifier.width(8.dp)); Text("Open confirmed ticket") }
                            OutlinedButton(onClick = { onCancel(booking) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Cancel, null); Spacer(Modifier.width(8.dp)); Text("Request cancellation") }
                        } else if (booking.status == "pending_payment" && booking.paymentStatus == "pending") {
                            Text("Payment reference submitted. Your ticket will appear after OTS verifies the transfer.", color = Color(0xFF9B681F), fontSize = 11.sp)
                        } else if (booking.status == "pending_payment") {
                            Text("Payment has not been submitted yet. The seat hold expires after 15 minutes.", color = Color(0xFF9B681F), fontSize = 11.sp)
                            OutlinedButton(onClick = { onPay(booking) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Payments, null); Spacer(Modifier.width(8.dp)); Text("Continue payment") }
                            TextButton(onClick = { onCancel(booking) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Cancel, null); Spacer(Modifier.width(8.dp)); Text("Cancel unpaid seat hold") }
                        } else if (booking.status == "cancel_requested") Text("Your cancellation request is waiting for OTS review.", color = Muted, fontSize = 11.sp)
                        else if (booking.status == "cancelled") Text("This booking was cancelled and the seat is no longer held.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketScreen(isUrdu: Boolean, ride: Ride, seat: String, fare: Int, reference: String, onBack: () -> Unit, onShareLocation: () -> Unit, onSos: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text(if (isUrdu) "ڈیجیٹل سفری ٹکٹ" else "Digital travel ticket", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OtsLogo(42.dp)
                    Spacer(Modifier.width(10.dp)); Column { Text("OTS TRANSPORT", color = Ink, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text("DIGITAL BOOKING TICKET", color = Muted, fontSize = 9.sp) }
                    Spacer(Modifier.weight(1f)); Icon(Icons.Default.Verified, null, tint = Color(0xFF159C7B))
                }
                HorizontalDivider(color = Color(0xFFE2E8EE))
                Text("BOOKING REF  " + reference.take(12).uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                BookingQrCode(reference = reference)
                Text("Booking reference QR • OTS staff must verify your ticket", color = Muted, fontSize = 10.sp)
                Text(ride.route, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(ride.time, color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { TicketDetail("SEAT", seat); TicketDetail("DRIVER", ride.driver); TicketDetail("FARE", "PKR $fare") }
                HorizontalDivider(color = Color(0xFFE2E8EE))
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Color(0xFF16876E)); Spacer(Modifier.width(7.dp)); Text("Keep this booking reference for your journey.", color = Color(0xFF16876E), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Text("Booking reference: " + reference, color = Muted, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onShareLocation, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share location", fontSize = 11.sp)
            }
            Button(onClick = onSos, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))) {
                Icon(Icons.Default.Call, null); Spacer(Modifier.width(5.dp)); Text("SOS • 1122", fontSize = 11.sp)
            }
        }
        Text("Location sharing uses your last known location if permission is granted. SOS opens the phone dialer; it does not call automatically.", color = Color(0xFFB8C9D7), fontSize = 10.sp)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}


@Composable
private fun BookingQrCode(reference: String) {
    val qrBitmap = remember(reference) {
        try {
            val matrix = QRCodeWriter().encode(
                "OTS-BOOKING:$reference",
                BarcodeFormat.QR_CODE,
                320,
                320
            )
            Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).also { bitmap ->
                for (x in 0 until 320) {
                    for (y in 0 until 320) {
                        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "QR code for booking reference $reference",
            modifier = Modifier.size(144.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Text("QR code unavailable", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TicketDetail(label: String, value: String) {
    Column(Modifier.widthIn(max = 125.dp)) { Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
}

@Composable
private fun DriverScreen(
    isUrdu: Boolean,
    session: SupabaseApi.Session?,
    status: String,
    trips: List<SupabaseApi.Trip>,
    vehicle: SupabaseApi.Vehicle?,
    onLogin: () -> Unit,
    onUpdateVehicle: (SupabaseApi.Vehicle, String, String, Int, String, String, Int, String, List<Uri>) -> Unit,
    onCancelTrip: (SupabaseApi.Trip) -> Unit,
    onEditTrip: (SupabaseApi.Trip, String, String, String, String, String, String, Int, Int) -> Unit,
    onPublishTrip: (String, String, String, String, String, String, Int, Int) -> Unit,
    onSubmit: (String, String, Int, String, String, String, Int, String, String, Uri?, Uri?, List<Uri>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("Toyota") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2020") }
    var plate by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var seats by remember { mutableStateOf("4") }
    var luggage by remember { mutableStateOf("Standard") }
    var destination by remember { mutableStateOf("Karachi") }
    var pickupPoint by remember { mutableStateOf("Khuzdar") }
    var dropoffPoint by remember { mutableStateOf("Karachi") }
    var departureDate by remember { mutableStateOf("") }
    var departureTime by remember { mutableStateOf("06:30") }
    var estimatedArrival by remember { mutableStateOf("") }
    var tripFare by remember { mutableStateOf("") }
    var tripSeats by remember { mutableStateOf("4") }
    var cancelCandidate by remember { mutableStateOf<SupabaseApi.Trip?>(null) }
    var showPublishReview by remember { mutableStateOf(false) }
    var editVehicle by remember { mutableStateOf<SupabaseApi.Vehicle?>(null) }
    var replacementVehiclePhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val editVehiclePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { replacementVehiclePhotos = it.take(5) }
    LaunchedEffect(vehicle?.id, status) {
        if (vehicle != null && (status == "pending" || status == "rejected")) {
            make = vehicle.make; model = vehicle.model; year = vehicle.year.toString()
            plate = vehicle.registration; color = vehicle.color; seats = vehicle.seats.toString(); luggage = vehicle.luggageCapacity
        }
    }
    var editCandidate by remember { mutableStateOf<SupabaseApi.Trip?>(null) }
    var editDestination by remember { mutableStateOf("") }
    var editPickup by remember { mutableStateOf("") }
    var editDropoff by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editTime by remember { mutableStateOf("") }
    var editArrival by remember { mutableStateOf("") }
    var editFare by remember { mutableStateOf("") }
    var editSeats by remember { mutableStateOf("") }
    var driverPhoto by remember { mutableStateOf<Uri?>(null) }
    var cnicDocument by remember { mutableStateOf<Uri?>(null) }
    var vehiclePhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val driverPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { driverPhoto = it }
    val vehiclePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { vehiclePhotos = it.take(5) }
    val cnicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { cnicDocument = it }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OtsLogo(38.dp)
            Spacer(Modifier.width(10.dp))
            Column { Text(if (isUrdu) "ڈرائیور مرکز" else "Driver centre", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text(if (isUrdu) "ایک رجسٹرڈ گاڑی • متعدد راستے" else "One registered vehicle • multiple routes", color = Muted, fontSize = 12.sp) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (isUrdu) "درخواست کی صورتحال" else "APPLICATION STATUS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(when (status) {
                    "approved" -> "Approved — you can prepare trip listings."
                    "pending", "submitting" -> "Pending review — publishing is locked."
                    "vehicle_pending" -> "Vehicle changes pending reapproval — publishing is locked."
                    "rejected" -> "Not approved — contact support for next steps."
                    "suspended" -> "Account suspended — publishing is disabled."
                    "unknown" -> "Unable to check status. Try again when connected."
                    else -> "Not registered as a driver yet."
                }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (isUrdu) "ایڈمن کی منظوری تک سفر شائع نہیں کیے جا سکتے۔" else "Trips stay locked until an administrator approves your driver application and registered vehicle.", color = Color(0xFFB9CBD9), fontSize = 11.sp)
            }
        }
        if (session == null) {
            Text("Sign in or create an account before submitting driver details.", color = Muted, fontSize = 13.sp)
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Sign in / Create account") }
        } else if (status == "not_submitted" || status == "unknown" || status == "pending" || status == "rejected") {
            Text(if (status == "rejected") "Correct and resubmit application" else if (status == "pending") "Update pending application" else "Driver and vehicle application", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Register exactly one vehicle—car, van, coaster or bus. Add your driver photo, CNIC document and clear vehicle photos for administrator review.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (isUrdu) "پورا قانونی نام" else "Full legal name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(if (isUrdu) "فون نمبر" else "Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text(if (isUrdu) "گاڑی بنانے والی کمپنی" else "Vehicle make") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(if (isUrdu) "ماڈل" else "Model") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = year, onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) }, label = { Text(if (isUrdu) "سال" else "Year") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(value = plate, onValueChange = { plate = it.uppercase() }, label = { Text(if (isUrdu) "رجسٹریشن نمبر" else "Registration number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = seats, onValueChange = { seats = it.filter { c -> c.isDigit() }.take(2) }, label = { Text(if (isUrdu) "مسافروں کی نشستیں" else "Passenger seats (vehicle capacity)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = luggage, onValueChange = { luggage = it }, label = { Text(if (isUrdu) "سامان کی گنجائش" else "Luggage capacity") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedButton(onClick = { driverPhotoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text(if (driverPhoto == null) { if (isUrdu) "ڈرائیور کی تصویر منتخب کریں" else "Choose driver photo" } else if (isUrdu) "ڈرائیور کی تصویر منتخب ہو گئی" else "Driver photo selected")
            }
            OutlinedButton(onClick = { cnicPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Badge, null); Spacer(Modifier.width(8.dp)); Text(if (cnicDocument == null) { if (isUrdu) "شناختی کارڈ کی تصویر / PDF منتخب کریں" else "Choose CNIC image / PDF" } else if (isUrdu) "شناختی دستاویز منتخب ہو گئی" else "CNIC document selected")
            }
            OutlinedButton(onClick = { vehiclePhotoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DirectionsCar, null); Spacer(Modifier.width(8.dp)); Text(if (vehiclePhotos.isEmpty()) { if (isUrdu) "گاڑی کی تصاویر منتخب کریں (زیادہ سے زیادہ ۵)" else "Choose vehicle photos (up to 5)" } else if (isUrdu) "${vehiclePhotos.size} تصاویر منتخب ہوئیں" else "${vehiclePhotos.size} vehicle photos selected")
            }
            Button(
                onClick = { onSubmit(name, phone, year.toIntOrNull() ?: 2020, make, model, plate, seats.toIntOrNull() ?: 4, color, luggage, driverPhoto, cnicDocument, vehiclePhotos) },
                enabled = status != "submitting" && name.isNotBlank() && phone.isNotBlank() && make.isNotBlank() && model.isNotBlank() && plate.isNotBlank() && driverPhoto != null && cnicDocument != null && vehiclePhotos.isNotEmpty() && (year.toIntOrNull() ?: 0) in 1980..2100 && (seats.toIntOrNull() ?: 0) in 1..60,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text(if (isUrdu) { if (status == "submitting") "جمع ہو رہا ہے…" else "منظوری کے لیے جمع کریں" } else { if (status == "submitting") "Submitting…" else "Submit for approval" }) }
        } else if (status == "approved") {
            if (vehicle != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("YOUR REGISTERED VEHICLE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("${vehicle.make} ${vehicle.model} • ${vehicle.year}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Registration: ${vehicle.registration} • ${vehicle.seats} passenger seats", color = Muted, fontSize = 11.sp)
                        OutlinedButton(onClick = {
                            editVehicle = vehicle
                            make = vehicle.make; model = vehicle.model; year = vehicle.year.toString()
                            plate = vehicle.registration; color = vehicle.color; seats = vehicle.seats.toString(); luggage = vehicle.luggageCapacity
                            replacementVehiclePhotos = emptyList()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Correct vehicle details") }
                    }
                }
            }
            Text(if (isUrdu) "سفر شائع کریں" else "Publish a trip", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Your single approved vehicle is used for every route. Set each trip’s passenger-seat count within its approved capacity.", color = Muted, fontSize = 12.sp)
            Text(if (isUrdu) "منزل" else "Destination", color = Ink, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Karachi", "Quetta", "Larkana", "Gwadar", "Turbat", "Ratto Dero").forEach { city ->
                    FilterChip(selected = destination == city, onClick = { destination = city; dropoffPoint = city }, label = { Text(city) })
                }
            }
            OutlinedTextField(value = pickupPoint, onValueChange = { pickupPoint = it }, label = { Text(if (isUrdu) "پک اَپ کی جگہ" else "Pickup point") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = dropoffPoint, onValueChange = { dropoffPoint = it }, label = { Text(if (isUrdu) "اترنے کی جگہ" else "Drop-off point") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = departureDate, onValueChange = { departureDate = it.take(10) }, label = { Text(if (isUrdu) "روانگی کی تاریخ (YYYY-MM-DD)" else "Departure date (YYYY-MM-DD)") }, modifier = Modifier.weight(1.3f), singleLine = true)
                OutlinedTextField(value = departureTime, onValueChange = { departureTime = it.take(5) }, label = { Text(if (isUrdu) "وقت (HH:MM)" else "Time (HH:MM)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(
                value = estimatedArrival,
                onValueChange = { estimatedArrival = it.take(16) },
                label = { Text(if (isUrdu) "متوقع آمد (YYYY-MM-DD HH:MM، اختیاری)" else "Estimated arrival (YYYY-MM-DD HH:MM, optional)") },
                placeholder = { Text("2026-10-23 14:30") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = tripFare, onValueChange = { tripFare = it.filter { c -> c.isDigit() }.take(7) }, label = { Text(if (isUrdu) "فی نشست کرایہ (PKR)" else "Fare per seat (PKR)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = tripSeats, onValueChange = { tripSeats = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Passenger seats (max ${vehicle?.seats ?: 60})") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Button(
                onClick = { showPublishReview = true },
                enabled = pickupPoint.isNotBlank() && dropoffPoint.isNotBlank() && departureDate.length == 10 && departureTime.length == 5 && (estimatedArrival.isBlank() || estimatedArrival.length == 16) && (tripFare.toIntOrNull() ?: 0) > 0 && (tripSeats.toIntOrNull() ?: 0) in 1..(vehicle?.seats ?: 60),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Icon(Icons.Default.Publish, null); Spacer(Modifier.width(8.dp)); Text("Publish trip") }
            Spacer(Modifier.height(10.dp))
            Text("Your trips", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (trips.isEmpty()) Text("You have not published any trips yet.", color = Muted, fontSize = 12.sp)
            trips.forEach { trip ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${trip.origin} → ${trip.destination}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(formatTripTime(trip.departureAt, trip.arrivalAt), color = Muted, fontSize = 11.sp)
                        Text("PKR ${trip.farePkr} • ${trip.availableSeats}/${trip.totalSeats} seats available • ${trip.status.uppercase()}", color = Navy2, fontSize = 11.sp)
                        if (trip.status == "published") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { cancelCandidate = trip }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                            Button(onClick = {
                                editCandidate = trip
                                editDestination = trip.destination
                                editPickup = trip.pickupPoint
                                editDropoff = trip.dropoffPoint
                                editDate = trip.departureAt.take(10)
                                editTime = trip.departureAt.substringAfter("T").take(5)
                                editArrival = trip.arrivalAt?.replace("T", " ")?.take(16).orEmpty()
                                editFare = trip.farePkr.toString()
                                editSeats = trip.totalSeats.toString()
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Edit trip") }
                        }
                    }
                }
            }
        } else {
            Text("Registered vehicle", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Your application is under review or requires action. Trip publishing remains locked until driver and car approval are both complete.", color = Muted, fontSize = 12.sp)
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF9AA7B4))) {
                Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Publishing locked")
            }
        }
    }
    if (showPublishReview) {
        AlertDialog(
            onDismissRequest = { showPublishReview = false },
            title = { Text(if (isUrdu) "سفر شائع کرنے سے پہلے جائزہ" else "Review trip before publishing") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(if (isUrdu) "گاڑی" else "APPROVED VEHICLE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(vehicle?.let { "${it.make} ${it.model} • ${it.registration}" } ?: "Approved vehicle", color = Ink, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    Text(if (isUrdu) "راستہ" else "ROUTE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("$pickupPoint → $dropoffPoint", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Destination: $destination", color = Muted, fontSize = 12.sp)
                    Text(if (isUrdu) "روانگی" else "DEPARTURE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("$departureDate • $departureTime", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Estimated arrival: ${estimatedArrival.ifBlank { "Not provided" }}", color = Muted, fontSize = 12.sp)
                    HorizontalDivider()
                    Text("Fare per passenger: PKR ${tripFare.toIntOrNull() ?: 0}", color = Ink, fontWeight = FontWeight.Bold)
                    Text("Vehicle type: ${vehicleTypeFor(tripSeats.toIntOrNull() ?: 0)} • Passenger seats: ${tripSeats.toIntOrNull() ?: 0} (driver seat excluded)", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Publishing is still subject to server-side driver approval, document validity and operational freeze checks.", color = Color(0xFF8B5A18), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPublishReview = false
                        onPublishTrip(destination, pickupPoint, dropoffPoint, departureDate, departureTime, estimatedArrival, tripFare.toIntOrNull() ?: 0, tripSeats.toIntOrNull() ?: 0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text(if (isUrdu) "تصدیق کریں اور شائع کریں" else "Confirm & publish") }
            },
            dismissButton = {
                TextButton(onClick = { showPublishReview = false }) { Text(if (isUrdu) "واپس ترمیم کریں" else "Back to edit") }
            }
        )
    }
    if (editVehicle != null) {
        AlertDialog(
            onDismissRequest = { editVehicle = null },
            title = { Text("Correct registered car") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only one car can be registered to this driver. Any change sends the vehicle back for administrator approval.", color = Muted, fontSize = 11.sp)
                    OutlinedTextField(make, { make = it }, label = { Text("Make") }, singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                    OutlinedTextField(year, { year = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Year") }, singleLine = true)
                    OutlinedTextField(plate, { plate = it.uppercase() }, label = { Text("Registration number") }, singleLine = true)
                    OutlinedTextField(color, { color = it }, label = { Text("Color") }, singleLine = true)
                    OutlinedTextField(seats, { seats = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Passenger seats") }, singleLine = true)
                    OutlinedTextField(luggage, { luggage = it }, label = { Text("Luggage capacity") }, singleLine = true)
                    OutlinedButton(onClick = { editVehiclePhotoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (replacementVehiclePhotos.isEmpty()) "Replace car photos (optional, up to 5)" else "${replacementVehiclePhotos.size} replacement photos selected")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = make.isNotBlank() && model.isNotBlank() && plate.isNotBlank() && (year.toIntOrNull() ?: 0) in 1980..2100 && (seats.toIntOrNull() ?: 0) in 1..4,
                    onClick = { editVehicle?.let { onUpdateVehicle(it, make, model, year.toIntOrNull() ?: 2020, plate, color, seats.toIntOrNull() ?: 4, luggage, replacementVehiclePhotos) }; editVehicle = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text("Submit for reapproval") }
            },
            dismissButton = { TextButton(onClick = { editVehicle = null }) { Text("Back") } }
        )
    }
    if (editCandidate != null) {
        AlertDialog(
            onDismissRequest = { editCandidate = null },
            title = { Text("Edit trip details") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editDestination, { editDestination = it }, label = { Text("Destination") }, singleLine = true)
                    OutlinedTextField(editPickup, { editPickup = it }, label = { Text("Pickup point") }, singleLine = true)
                    OutlinedTextField(editDropoff, { editDropoff = it }, label = { Text("Drop-off point") }, singleLine = true)
                    OutlinedTextField(editDate, { editDate = it.take(10) }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(editTime, { editTime = it.take(5) }, label = { Text("Time (HH:MM)") }, singleLine = true)
                    OutlinedTextField(editArrival, { editArrival = it.take(16) }, label = { Text("Estimated arrival (YYYY-MM-DD HH:MM, optional)") }, singleLine = true)
                    OutlinedTextField(editFare, { editFare = it.filter { c -> c.isDigit() }.take(7) }, label = { Text("Fare per seat (PKR)") }, singleLine = true)
                    OutlinedTextField(editSeats, { editSeats = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Passenger seats") }, singleLine = true)
                    Text("Trips with active bookings cannot change their route, time, fare or seats. OTS administration must handle those cases.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = editDestination.isNotBlank() && editPickup.isNotBlank() && editDropoff.isNotBlank() && (editArrival.isBlank() || editArrival.length == 16) && (editFare.toIntOrNull() ?: 0) > 0 && (editSeats.toIntOrNull() ?: 0) in 1..4,
                    onClick = { editCandidate?.let { onEditTrip(it, editDestination, editPickup, editDropoff, editDate, editTime, editArrival, editFare.toIntOrNull() ?: 0, editSeats.toIntOrNull() ?: 0) }; editCandidate = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)
                ) { Text("Save changes") }
            },
            dismissButton = { TextButton(onClick = { editCandidate = null }) { Text("Back") } }
        )
    }
    if (cancelCandidate != null) {
        AlertDialog(
            onDismissRequest = { cancelCandidate = null },
            title = { Text("Cancel this trip?") },
            text = { Text("OTS will block cancellation if this trip has active passenger bookings, so their journeys are not silently disrupted.") },
            confirmButton = { Button(onClick = { cancelCandidate?.let(onCancelTrip); cancelCandidate = null }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Confirm cancellation") } },
            dismissButton = { TextButton(onClick = { cancelCandidate = null }) { Text("Keep trip") } }
        )
    }
}

@Composable
private fun ProfileScreen(isUrdu: Boolean, role: String, onRole: (String) -> Unit, onLogin: () -> Unit, signedIn: Boolean, email: String, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (isUrdu) "آپ کا پروفائل" else "Your profile", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                OtsLogo(58.dp)
                Spacer(Modifier.width(12.dp)); Column { Text("Your OTS account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(if (signedIn) email else "Sign in to manage your journeys", color = Color(0xFFB9CBD9), fontSize = 11.sp) }
            }
        }
        Text("Current mode", color = Ink, fontWeight = FontWeight.Bold)
        listOf("Passenger", "Driver").forEach { FilterChip(selected = role == it, onClick = { onRole(it) }, label = { Text(it) }) }
        if (signedIn) { Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out") } } else { Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.PhoneAndroid, null); Spacer(Modifier.width(8.dp)); Text("Sign in / Create account") } }
        Text("Identity documents and payment details are not collected on this screen. Secure document upload and paid payment services will be added only when configured.", color = Muted, fontSize = 11.sp)
        Text("Version 1.0 • OTS Transport", color = Muted, fontSize = 11.sp)
    }
}
