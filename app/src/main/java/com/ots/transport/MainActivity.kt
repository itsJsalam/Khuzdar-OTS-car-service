package com.ots.transport

import android.os.Bundle
import android.net.Uri
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OTSApp(initialSession = SessionStore.load(this@MainActivity)) }
    }
}

private data class Ride(val city: String, val route: String, val time: String, val car: String, val driver: String, val price: Int, val seats: Int, val rating: String, val tag: String, val tripId: String? = null, val totalSeats: Int = 4, val pickupPoint: String = "Khuzdar", val dropoffPoint: String? = null)
@Composable
private fun OtsLogo(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(id = R.drawable.ots_logo),
        contentDescription = "OTS Transport logo",
        modifier = Modifier.size(size).clip(RoundedCornerShape(13.dp))
    )
}

private val sampleRides = listOf(
    Ride("Karachi", "Khuzdar → Karachi", "Tomorrow • 06:30 AM", "Toyota Corolla • White", "Naveed Baloch", 3000, 3, "4.9", "POPULAR"),
    Ride("Quetta", "Khuzdar → Quetta", "Tomorrow • 08:00 AM", "Honda City • Silver", "Rahim Shah", 2500, 2, "4.8", "ON TIME"),
    Ride("Larkana", "Khuzdar → Larkana", "Fri • 07:00 AM", "Toyota Yaris • Grey", "Aslam Mengal", 2800, 2, "4.7", "NEW"),
    Ride("Gwadar", "Khuzdar → Gwadar", "Fri • 09:15 AM", "Suzuki Ciaz • Black", "Bilal Ahmed", 2200, 3, "4.9", "COASTAL")
)

@Composable
private fun OTSApp(initialSession: SupabaseApi.Session?) {
    var role by remember { mutableStateOf("Passenger") }
    var tab by remember { mutableStateOf("Home") }
    var selectedRoute by remember { mutableStateOf("Karachi") }
    var selectedRide by remember { mutableStateOf<Ride?>(null) }
    var selectedSeat by remember { mutableStateOf("Seat 1") }
    var selectedDrop by remember { mutableStateOf("Karachi") }
    var luggage by remember { mutableStateOf("Small bag") }
    var booked by remember { mutableStateOf(false) }
    var showOtp by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }
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
    val appContext = LocalContext.current

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
    androidx.activity.compose.BackHandler(enabled = tab == "Seat" && !showTicket) { tab = "Rides" }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = Gold, secondary = Mint, background = Ink, surface = Navy, onPrimary = Ink, onBackground = Color.White, onSurface = Color.White)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize()) {
                when {
                    showTicket -> TicketScreen(ride = selectedRide ?: sampleRides[0], seat = selectedSeat, fare = if (ticketFare > 0) ticketFare else fareFor(selectedRide ?: sampleRides[0], selectedSeat), reference = ticketReference, onBack = { showTicket = false; tab = "Bookings" })
                    tab == "Rides" -> RidesScreen(route = selectedRoute, trips = liveTrips, loading = tripsLoading, error = tripsError, onRoute = { selectedRoute = it }, onSelect = { selectedRide = it; selectedDrop = it.city; selectedSeat = "Seat 1"; tab = "Seat" })
                    tab == "Seat" -> SeatScreen(ride = selectedRide ?: sampleRides[0], seat = selectedSeat, onSeat = { selectedSeat = it }, drop = selectedDrop, onDrop = { selectedDrop = it }, luggage = luggage, onLuggage = { luggage = it }, onBack = { tab = "Rides" }, onBook = {
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
                                    val ticketId = SupabaseApi.createBooking(active, trip.tripId, selectedSeat, fareFor(trip, selectedSeat), trip.pickupPoint, trip.dropoffPoint ?: trip.city, luggage)
                                    booked = true
                                    ticketReference = ticketId
                                    ticketFare = fareFor(trip, selectedSeat)
                                    liveBookings = SupabaseApi.getBookings(active)
                                    showTicket = true
                                    notice = "Your booking is confirmed. Ticket reference: " + ticketId.take(8).uppercase()
                                } catch (e: Exception) {
                                    notice = e.message ?: "The booking could not be completed. Please try again."
                                }
                            }
                        }
                    })
                    tab == "Bookings" -> BookingsScreen(signedIn = session != null, bookings = liveBookings, loading = bookingsLoading, error = bookingsError, onBrowse = { tab = "Rides" }, onLogin = { authMessage = "Sign in to view your bookings."; showOtp = true }, onTicket = { booking ->
                        selectedRide = Ride(booking.destination, "${booking.origin} → ${booking.destination}", booking.departureAt.replace("T", " ").take(16), "OTS vehicle", "OTS driver", booking.fareTotalPkr, 0, "—", booking.status.uppercase(), booking.id)
                        selectedSeat = booking.seatLabels.ifBlank { "Seat assigned by driver" }
                        ticketFare = booking.fareTotalPkr
                        ticketReference = booking.id
                        showTicket = true
                    }, onCancel = { booking ->
                        val active = session
                        if (active != null) scope.launch {
                            try {
                                SupabaseApi.requestBookingCancellation(active, booking.id)
                                liveBookings = SupabaseApi.getBookings(active)
                                notice = "Cancellation request sent. The trip is not cancelled until OTS reviews the request."
                            } catch (e: Exception) { notice = e.message ?: "Could not request cancellation." }
                        }
                    })
                    tab == "Profile" -> ProfileScreen(role = role, onRole = { role = it }, onLogin = { authMessage = ""; showOtp = true }, signedIn = session != null, email = session?.email.orEmpty(), onSignOut = { SessionStore.clear(appContext); session = null; driverStatus = "not_submitted"; notice = "You have signed out." })
                    role == "Driver" && tab == "Home" -> DriverScreen(
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
                        onEditTrip = { trip, destination, pickup, dropoff, date, time, fare, seats ->
                            val active = session
                            val departure = toDepartureIso(date, time)
                            if (active == null) {
                                authMessage = "Please sign in first."
                                showOtp = true
                            } else if (departure == null) {
                                notice = "Enter a valid future departure date and time."
                            } else {
                                scope.launch {
                                    try {
                                        SupabaseApi.updateTrip(active, trip.id, destination, pickup, dropoff, departure, fare, seats)
                                        driverTrips = SupabaseApi.getDriverTrips(active)
                                        notice = "Trip updated."
                                    } catch (e: Exception) {
                                        notice = e.message ?: "Trip changes were blocked. Trips with active bookings need OTS review."
                                    }
                                }
                            }
                        },
                        onPublishTrip = { destination, pickup, dropoff, date, time, fare, seats ->
                            val active = session
                            val departure = toDepartureIso(date, time)
                            if (active == null) {
                                authMessage = "Please sign in first."
                                showOtp = true
                            } else if (departure == null) {
                                notice = "Enter a valid future departure date as YYYY-MM-DD and time as HH:MM (24-hour)."
                            } else {
                                scope.launch {
                                    try {
                                        val vehicle = SupabaseApi.getApprovedVehicle(active) ?: throw IllegalStateException("Your car has not been approved yet.")
                                        if (seats > vehicle.passengerSeats) throw IllegalStateException("This car is approved for only ${vehicle.passengerSeats} passenger seats.")
                                        val tripId = SupabaseApi.publishTrip(active, vehicle.id, destination, pickup, dropoff, departure, fare, seats)
                                        driverTrips = SupabaseApi.getDriverTrips(active)
                                        notice = "Trip published successfully. Reference: " + tripId.take(8).uppercase()
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
                                        notice = "Your driver and one-car application has been submitted. You cannot publish trips until the administrator approves it."
                                    } catch (e: Exception) {
                                        driverStatus = "not_submitted"
                                        notice = e.message ?: "The application could not be submitted. Please check your connection and try again."
                                    }
                                }
                            }
                        }
                    )
                    else -> HomeScreen(role = role, onRole = { role = it }, onRoute = { selectedRoute = it; tab = "Rides" }, onSearch = { tab = "Rides" }, onDriver = { role = "Driver"; tab = "Home" })
                }
                if (!showTicket && tab != "Seat") {
                    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                        listOf(Triple("Home", Icons.Default.Home, "Home"), Triple("Rides", Icons.Default.Route, "Find rides"), Triple("Bookings", Icons.Default.ConfirmationNumber, "Tickets"), Triple("Profile", Icons.Default.Person, "Profile")).forEach { (key, icon, label) ->
                            NavigationBarItem(selected = tab == key, onClick = { tab = key; showTicket = false }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Navy, indicatorColor = Color(0xFFFFE6BD), selectedTextColor = Navy))
                        }
                    }
                }
            }
            if (showOtp) AlertDialog(
                onDismissRequest = { if (!authBusy) showOtp = false },
                title = { Text(if (authIsSignup) "Create your OTS account" else "Sign in to OTS") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (role == "Driver") "Driver access • approval is required before trip publishing." else "Passenger access • use an email and password. No paid SMS service is needed.")
                        if (authIsSignup) {
                            OutlinedTextField(value = authName, onValueChange = { authName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = authPhone, onValueChange = { authPhone = it }, label = { Text("Phone number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        OutlinedTextField(value = authEmail, onValueChange = { authEmail = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = authPassword, onValueChange = { authPassword = it }, label = { Text("Password (at least 6 characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        if (authMessage.isNotBlank()) Text(authMessage, color = if (authMessage.startsWith("Account created")) Color(0xFF14785E) else Color(0xFF9A4D20), fontSize = 12.sp)
                        if (authBusy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Connecting securely…", fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    Button(enabled = !authBusy, onClick = {
                        if (authEmail.isBlank() || authPassword.length < 6 || (authIsSignup && authName.isBlank())) {
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
                    }) { Text(if (authIsSignup) "Create account" else "Sign in") }
                },
                dismissButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !authBusy, onClick = { authIsSignup = !authIsSignup; authMessage = "" }) { Text(if (authIsSignup) "I have an account" else "Create account") }
                        TextButton(enabled = !authBusy, onClick = { showOtp = false }) { Text("Back") }
                    }
                }
            )
            if (notice.isNotBlank()) AlertDialog(onDismissRequest = { notice = "" }, title = { Text("OTS Transport") }, text = { Text(notice) }, confirmButton = { TextButton(onClick = { notice = "" }) { Text("OK") } })
        }
    }
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
private fun HomeScreen(role: String, onRole: (String) -> Unit, onRoute: (String) -> Unit, onSearch: () -> Unit, onDriver: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Paper)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Ink, Navy2, Color(0xFF23536A)))).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Gold, Color(0xFFFFE7B6)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = Ink, modifier = Modifier.size(27.dp)) }
                    Spacer(Modifier.width(11.dp))
                    Column { Text("OTS TRANSPORT", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, fontSize = 17.sp); Text("Khuzdar • Intercity journeys", color = Color(0xFFB9CBD9), fontSize = 11.sp) }
                    Spacer(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("YOUR NEXT JOURNEY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("The road is\nyours.", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 37.sp)
                        Spacer(Modifier.height(7.dp))
                        Text("Reserve your seat. Travel with confidence.", color = Color(0xFFD0DCE6), fontSize = 12.sp)
                    }
                    OtsLogo(78.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleChip("Passenger", role == "Passenger") { onRole("Passenger") }
                    RoleChip("Driver", role == "Driver") { onRole("Driver"); onDriver() }
                }
            }
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Where to?", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("Choose your route from Khuzdar", color = Muted, fontSize = 12.sp) }; Text("4 ROUTES", color = Navy2, fontWeight = FontWeight.Bold, fontSize = 10.sp) }
            val routes = listOf(Triple("Karachi", "Coastal metropolis", Icons.Default.LocationCity), Triple("Quetta", "Mountain gateway", Icons.Default.Terrain), Triple("Sindh", "Larkana • Ratto Dero", Icons.Default.Route), Triple("Makran", "Turbat • Gwadar", Icons.Default.Waves))
            routes.forEachIndexed { index, route ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { onRoute(if (route.first == "Sindh") "Larkana" else if (route.first == "Makran") "Gwadar" else route.first) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(listOf(Color(0xFFFFF0D7), Color(0xFFE1F4F1), Color(0xFFE8ECFF), Color(0xFFE7F0F8))[index]), contentAlignment = Alignment.Center) { Icon(route.third, null, tint = Navy2, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) { Text("Khuzdar → ${route.first}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(route.second, color = Muted, fontSize = 11.sp) }
                    Icon(Icons.Default.ArrowForward, null, tint = Navy2)
                }
            }
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Find available rides", fontWeight = FontWeight.Bold) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("4", "Routes", Icons.Default.Route, Modifier.weight(1f))
                StatCard("OTS", "Driver review", Icons.Default.Verified, Modifier.weight(1f))
                StatCard("PKR", "Clear fares", Icons.Default.Payments, Modifier.weight(1f))
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
    route: String,
    trips: List<SupabaseApi.Trip>,
    loading: Boolean,
    error: String,
    onRoute: (String) -> Unit,
    onSelect: (Ride) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Find your ride", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Live trips published by approved OTS drivers", color = Muted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Karachi", "Quetta", "Larkana", "Gwadar").forEach { city ->
                FilterChip(selected = route == city, onClick = { onRoute(city) }, label = { Text(city) }, leadingIcon = if (route == city) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null)
            }
        }
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Loading available trips…", color = Muted, fontSize = 12.sp) }
        } else {
            val filtered = trips.filter { route == "All" || it.destination.equals(route, ignoreCase = true) }
            filtered.forEach { trip ->
                RideCard(
                    Ride(
                        city = trip.destination,
                        route = "${trip.origin} → ${trip.destination}",
                        time = trip.departureAt.replace("T", " ").take(16),
                        car = "Verified vehicle",
                        driver = "Approved OTS driver",
                        price = trip.farePkr,
                        seats = trip.availableSeats,
                        rating = "—",
                        tag = "AVAILABLE",
                        tripId = trip.id,
                        totalSeats = trip.totalSeats,
                        pickupPoint = trip.pickupPoint,
                        dropoffPoint = trip.dropoffPoint
                    ),
                    onClick = {
                        onSelect(Ride(trip.destination, "${trip.origin} → ${trip.destination}", trip.departureAt.replace("T", " ").take(16), "Verified vehicle", "Approved OTS driver", trip.farePkr, trip.availableSeats, "—", "AVAILABLE", trip.id, trip.totalSeats, trip.pickupPoint, trip.dropoffPoint))
                    }
                )
            }
            if (filtered.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Default.Route, null, tint = Navy2, modifier = Modifier.size(34.dp))
                        Text(if (error.isBlank()) "No trips available yet" else "Trips could not be loaded", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(if (error.isBlank()) "There are no published trips for this route right now. Please check again later." else error, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Text("Trips, prices and available seats are read from the shared OTS database.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RideCard(ride: Ride, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clip(RoundedCornerShape(14.dp)).background(Navy), contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = Gold, modifier = Modifier.size(27.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(ride.route, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text(ride.time, color = Muted, fontSize = 11.sp) }
            Surface(color = Color(0xFFFFF0D7), shape = RoundedCornerShape(7.dp)) { Text(ride.tag, Modifier.padding(horizontal = 7.dp, vertical = 5.dp), color = Color(0xFF8B5A18), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
        }
        HorizontalDivider(color = Color(0xFFE9EEF3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(ride.car, color = Navy2, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("Driver: ${ride.driver}  •  ★ ${ride.rating}", color = Muted, fontSize = 10.sp) }
            Column(horizontalAlignment = Alignment.End) { Text("PKR ${ride.price}", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("${ride.seats} seats left", color = Color(0xFF16876E), fontSize = 10.sp) }
        }
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Choose seats"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun SeatScreen(ride: Ride, seat: String, onSeat: (String) -> Unit, drop: String, onDrop: (String) -> Unit, luggage: String, onLuggage: (String) -> Unit, onBack: () -> Unit, onBook: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Ink) }; Column { Text("Choose your seat", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold); Text(ride.route, color = Muted, fontSize = 12.sp) } }
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VEHICLE SEATING PLAN", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(260.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF203D54)).padding(14.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("FRONT", color = Color(0xFFB8C9D7), fontSize = 9.sp); Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFFB8C9D7), modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            (1..ride.totalSeats.coerceIn(1, 20)).forEach { number ->
                                val label = "Seat " + number
                                SeatButton(label, seat == label, onSeat, true)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Seat count matches the approved vehicle capacity. The database prevents double-booking a seat.", color = Color(0xFFB8C9D7), fontSize = 10.sp)
                    }
                }
            }
        }
        Text("Pickup and drop-off", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("PICKUP", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(ride.pickupPoint, color = Ink, fontWeight = FontWeight.SemiBold) }
                Icon(Icons.Default.ArrowForward, null, tint = Gold)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Text("DROP-OFF", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(ride.dropoffPoint ?: ride.city, color = Ink, fontWeight = FontWeight.SemiBold) }
            }
        }
        Text("Luggage", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Small bag", "Medium bag", "Large box").forEach { item -> FilterChip(selected = luggage == item, onClick = { onLuggage(item) }, label = { Text(item, fontSize = 11.sp) }) } }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("FARE PER SEAT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("PKR ${fareFor(ride, seat)}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("No online payment is collected in this version.", color = Muted, fontSize = 10.sp) }
                Button(onClick = onBook, shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Book now") }
            }
        }
        Text("Before boarding, compare the driver and car details with your ticket.", color = Color(0xFF8B5A18), fontSize = 11.sp)
    }
}

@Composable
private fun SeatButton(label: String, selected: Boolean, onSeat: (String) -> Unit, available: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Gold else if (available) Color(0xFF2B6C68) else Color(0xFF4A5E6F)).clickable(enabled = available) { onSeat(label) }.padding(vertical = 11.dp, horizontal = 4.dp)) {
        Icon(Icons.Default.EventSeat, null, tint = if (selected) Ink else Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(5.dp)); Text(label.removePrefix("Rear "), color = if (selected) Ink else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(if (selected) "SELECTED" else if (available) "AVAILABLE" else "FULL", color = if (selected) Ink else Color(0xFFD9E7ED), fontSize = 6.sp)
    }
}

private fun fareFor(ride: Ride, seat: String): Int = ride.price

@Composable
private fun BookingsScreen(
    signedIn: Boolean,
    bookings: List<SupabaseApi.Booking>,
    loading: Boolean,
    error: String,
    onBrowse: () -> Unit,
    onLogin: () -> Unit,
    onTicket: (SupabaseApi.Booking) -> Unit,
    onCancel: (SupabaseApi.Booking) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your bookings", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Bookings and cancellation requests from your OTS account", color = Muted, fontSize = 12.sp)
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
                        Text(booking.departureAt.replace("T", " ").take(16), color = Muted, fontSize = 12.sp)
                        Text("Seat: ${booking.seatLabels.ifBlank { "Not assigned" }}", color = Navy2, fontSize = 12.sp)
                        Button(onClick = { onTicket(booking) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.ConfirmationNumber, null); Spacer(Modifier.width(8.dp)); Text("Open ticket") }
                        if (booking.status == "confirmed") OutlinedButton(onClick = { onCancel(booking) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Cancel, null); Spacer(Modifier.width(8.dp)); Text("Request cancellation") }
                        if (booking.status == "cancel_requested") Text("Your cancellation request is waiting for OTS review.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketScreen(ride: Ride, seat: String, fare: Int, reference: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text("Digital travel ticket", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OtsLogo(42.dp)
                    Spacer(Modifier.width(10.dp)); Column { Text("OTS TRANSPORT", color = Ink, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text("DIGITAL BOARDING PASS", color = Muted, fontSize = 9.sp) }
                    Spacer(Modifier.weight(1f)); Icon(Icons.Default.Verified, null, tint = Color(0xFF159C7B))
                }
                HorizontalDivider(color = Color(0xFFE2E8EE))
                Text("BOOKING REF  " + reference.take(12).uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(ride.route, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(ride.time, color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { TicketDetail("SEAT", seat); TicketDetail("DRIVER", ride.driver); TicketDetail("FARE", "PKR $fare") }
                HorizontalDivider(color = Color(0xFFE2E8EE))
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Color(0xFF16876E)); Spacer(Modifier.width(7.dp)); Text("Keep this booking reference for your journey.", color = Color(0xFF16876E), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Text("Booking reference: " + reference, color = Muted, fontSize = 11.sp)
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun TicketDetail(label: String, value: String) {
    Column(Modifier.widthIn(max = 125.dp)) { Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
}

@Composable
private fun DriverScreen(
    session: SupabaseApi.Session?,
    status: String,
    trips: List<SupabaseApi.Trip>,
    vehicle: SupabaseApi.Vehicle?,
    onLogin: () -> Unit,
    onUpdateVehicle: (SupabaseApi.Vehicle, String, String, Int, String, String, Int, String, List<Uri>) -> Unit,
    onCancelTrip: (SupabaseApi.Trip) -> Unit,
    onEditTrip: (SupabaseApi.Trip, String, String, String, String, String, Int, Int) -> Unit,
    onPublishTrip: (String, String, String, String, String, Int, Int) -> Unit,
    onSubmit: (String, String, Int, String, String, String, Int, String, String, Uri?, Uri?, List<Uri>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("Toyota") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2020") }
    var plate by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var seats by remember { mutableStateOf("3") }
    var luggage by remember { mutableStateOf("Standard") }
    var destination by remember { mutableStateOf("Karachi") }
    var pickupPoint by remember { mutableStateOf("Khuzdar") }
    var dropoffPoint by remember { mutableStateOf("Karachi") }
    var departureDate by remember { mutableStateOf("") }
    var departureTime by remember { mutableStateOf("06:30") }
    var tripFare by remember { mutableStateOf("") }
    var tripSeats by remember { mutableStateOf("3") }
    var cancelCandidate by remember { mutableStateOf<SupabaseApi.Trip?>(null) }
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
            Column { Text("Driver centre", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("One registered car • multiple routes", color = Muted, fontSize = 12.sp) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("APPLICATION STATUS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(when (status) {
                    "approved" -> "Approved — you can prepare trip listings."
                    "pending", "submitting" -> "Pending review — publishing is locked."
                    "vehicle_pending" -> "Vehicle changes pending reapproval — publishing is locked."
                    "rejected" -> "Not approved — contact support for next steps."
                    "suspended" -> "Account suspended — publishing is disabled."
                    "unknown" -> "Unable to check status. Try again when connected."
                    else -> "Not registered as a driver yet."
                }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Trips stay locked until an administrator approves your driver application and car.", color = Color(0xFFB9CBD9), fontSize = 11.sp)
            }
        }
        if (session == null) {
            Text("Sign in or create an account before submitting driver details.", color = Muted, fontSize = 13.sp)
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Sign in / Create account") }
        } else if (status == "not_submitted" || status == "unknown" || status == "pending" || status == "rejected") {
            Text(if (status == "rejected") "Correct and resubmit application" else if (status == "pending") "Update pending application" else "Driver and vehicle application", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Register exactly one car. Your driver photo, CNIC document and car photo are uploaded to private storage for administrator review. Use fictional documents while testing.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full legal name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Car make") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = year, onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Year") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(value = plate, onValueChange = { plate = it.uppercase() }, label = { Text("Registration number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = seats, onValueChange = { seats = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Passenger seats") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = luggage, onValueChange = { luggage = it }, label = { Text("Luggage capacity") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedButton(onClick = { driverPhotoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text(if (driverPhoto == null) "Choose driver photo" else "Driver photo selected")
            }
            OutlinedButton(onClick = { cnicPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Badge, null); Spacer(Modifier.width(8.dp)); Text(if (cnicDocument == null) "Choose CNIC image / PDF" else "CNIC document selected")
            }
            OutlinedButton(onClick = { vehiclePhotoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DirectionsCar, null); Spacer(Modifier.width(8.dp)); Text(if (vehiclePhotos.isEmpty()) "Choose car photos (up to 5)" else "${vehiclePhotos.size} car photos selected")
            }
            Button(
                onClick = { onSubmit(name, phone, year.toIntOrNull() ?: 2020, make, model, plate, seats.toIntOrNull() ?: 3, color, luggage, driverPhoto, cnicDocument, vehiclePhotos) },
                enabled = status != "submitting" && name.isNotBlank() && phone.isNotBlank() && make.isNotBlank() && model.isNotBlank() && plate.isNotBlank() && driverPhoto != null && cnicDocument != null && vehiclePhotos.isNotEmpty() && (year.toIntOrNull() ?: 0) in 1980..2100 && (seats.toIntOrNull() ?: 0) in 1..20,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text(if (status == "submitting") "Submitting…" else "Submit for approval") }
        } else if (status == "approved") {
            if (vehicle != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("YOUR REGISTERED CAR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
            Text("Publish a trip", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Your approved car will be used for every route. You can publish multiple trips with different schedules.", color = Muted, fontSize = 12.sp)
            Text("Destination", color = Ink, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Karachi", "Quetta", "Larkana", "Gwadar", "Turbat", "Ratto Dero").forEach { city ->
                    FilterChip(selected = destination == city, onClick = { destination = city; dropoffPoint = city }, label = { Text(city) })
                }
            }
            OutlinedTextField(value = pickupPoint, onValueChange = { pickupPoint = it }, label = { Text("Pickup point") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = dropoffPoint, onValueChange = { dropoffPoint = it }, label = { Text("Drop-off point") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = departureDate, onValueChange = { departureDate = it.take(10) }, label = { Text("Departure date (YYYY-MM-DD)") }, modifier = Modifier.weight(1.3f), singleLine = true)
                OutlinedTextField(value = departureTime, onValueChange = { departureTime = it.take(5) }, label = { Text("Time (HH:MM)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = tripFare, onValueChange = { tripFare = it.filter { c -> c.isDigit() }.take(7) }, label = { Text("Fare per seat (PKR)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = tripSeats, onValueChange = { tripSeats = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Passenger seats") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Button(
                onClick = { onPublishTrip(destination, pickupPoint, dropoffPoint, departureDate, departureTime, tripFare.toIntOrNull() ?: 0, tripSeats.toIntOrNull() ?: 0) },
                enabled = pickupPoint.isNotBlank() && dropoffPoint.isNotBlank() && departureDate.length == 10 && departureTime.length == 5 && (tripFare.toIntOrNull() ?: 0) > 0 && (tripSeats.toIntOrNull() ?: 0) in 1..20,
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
                        Text(trip.departureAt.replace("T", " ").take(16), color = Muted, fontSize = 11.sp)
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
                    enabled = make.isNotBlank() && model.isNotBlank() && plate.isNotBlank() && (year.toIntOrNull() ?: 0) in 1980..2100 && (seats.toIntOrNull() ?: 0) in 1..20,
                    onClick = { editVehicle?.let { onUpdateVehicle(it, make, model, year.toIntOrNull() ?: 2020, plate, color, seats.toIntOrNull() ?: 3, luggage, replacementVehiclePhotos) }; editVehicle = null },
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
                    OutlinedTextField(editFare, { editFare = it.filter { c -> c.isDigit() }.take(7) }, label = { Text("Fare per seat (PKR)") }, singleLine = true)
                    OutlinedTextField(editSeats, { editSeats = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Passenger seats") }, singleLine = true)
                    Text("Trips with active bookings cannot change their route, time, fare or seats. OTS administration must handle those cases.", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = editDestination.isNotBlank() && editPickup.isNotBlank() && editDropoff.isNotBlank() && (editFare.toIntOrNull() ?: 0) > 0 && (editSeats.toIntOrNull() ?: 0) in 1..20,
                    onClick = { editCandidate?.let { onEditTrip(it, editDestination, editPickup, editDropoff, editDate, editTime, editFare.toIntOrNull() ?: 0, editSeats.toIntOrNull() ?: 0) }; editCandidate = null },
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
private fun ProfileScreen(role: String, onRole: (String) -> Unit, onLogin: () -> Unit, signedIn: Boolean, email: String, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your profile", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
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
