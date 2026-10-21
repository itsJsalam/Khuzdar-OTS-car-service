package com.ots.transport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        setContent { OTSApp() }
    }
}

private data class Ride(val city: String, val route: String, val time: String, val car: String, val driver: String, val price: Int, val seats: Int, val rating: String, val tag: String)
private val sampleRides = listOf(
    Ride("Karachi", "Khuzdar → Karachi", "Tomorrow • 06:30 AM", "Toyota Corolla • White", "Naveed Baloch", 3000, 3, "4.9", "POPULAR"),
    Ride("Quetta", "Khuzdar → Quetta", "Tomorrow • 08:00 AM", "Honda City • Silver", "Rahim Shah", 2500, 2, "4.8", "ON TIME"),
    Ride("Larkana", "Khuzdar → Larkana", "Fri • 07:00 AM", "Toyota Yaris • Grey", "Aslam Mengal", 2800, 2, "4.7", "NEW"),
    Ride("Gwadar", "Khuzdar → Gwadar", "Fri • 09:15 AM", "Suzuki Ciaz • Black", "Bilal Ahmed", 2200, 3, "4.9", "COASTAL")
)

@Composable
private fun OTSApp() {
    var role by remember { mutableStateOf("Passenger") }
    var tab by remember { mutableStateOf("Home") }
    var selectedRoute by remember { mutableStateOf("Karachi") }
    var selectedRide by remember { mutableStateOf<Ride?>(null) }
    var selectedSeat by remember { mutableStateOf("Rear seat") }
    var selectedDrop by remember { mutableStateOf("Karachi") }
    var luggage by remember { mutableStateOf("Small bag") }
    var booked by remember { mutableStateOf(false) }
    var showOtp by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var authEmail by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authName by remember { mutableStateOf("") }
    var authPhone by remember { mutableStateOf("") }
    var authIsSignup by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<SupabaseApi.Session?>(null) }
    var driverStatus by remember { mutableStateOf("not_submitted") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session?.userId, role) {
        val active = session
        if (active != null && role == "Driver") {
            driverStatus = try { SupabaseApi.getDriverStatus(active) } catch (_: Exception) { "unknown" }
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
                    showTicket -> TicketScreen(ride = selectedRide ?: sampleRides[0], seat = selectedSeat, fare = fareFor(selectedRide ?: sampleRides[0], selectedSeat), onBack = { showTicket = false; tab = "Bookings" })
                    tab == "Rides" -> RidesScreen(route = selectedRoute, onRoute = { selectedRoute = it }, onSelect = { selectedRide = it; selectedDrop = it.city; tab = "Seat" })
                    tab == "Seat" -> SeatScreen(ride = selectedRide ?: sampleRides[0], seat = selectedSeat, onSeat = { selectedSeat = it }, drop = selectedDrop, onDrop = { selectedDrop = it }, luggage = luggage, onLuggage = { luggage = it }, onBack = { tab = "Rides" }, onBook = { booked = true; showTicket = true; notice = "This ticket is a local preview. Real booking and seat locking will be enabled after the booking workflow is connected." })
                    tab == "Bookings" -> BookingsScreen(booked = booked, ride = selectedRide ?: sampleRides[0], seat = selectedSeat, onBrowse = { tab = "Home" }, onTicket = { showTicket = true })
                    tab == "Profile" -> ProfileScreen(role = role, onRole = { role = it }, onLogin = { authMessage = ""; showOtp = true }, signedIn = session != null, email = session?.email.orEmpty(), onSignOut = { session = null; driverStatus = "not_submitted"; notice = "You have signed out." })
                    role == "Driver" && tab == "Home" -> DriverScreen(
                        session = session,
                        status = driverStatus,
                        onLogin = { authMessage = "Sign in or create an account before submitting a driver application."; showOtp = true },
                        onSubmit = { name, phone, year, make, model, plate, seats, color, luggageCapacity ->
                            val active = session
                            if (active == null) {
                                authMessage = "Please sign in first."
                                showOtp = true
                            } else {
                                scope.launch {
                                    driverStatus = "submitting"
                                    try {
                                        SupabaseApi.submitDriverApplication(active, name, phone, make, model, year, plate, color, seats, "Car", luggageCapacity)
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
                    Box(Modifier.size(94.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DirectionsCarFilled, null, tint = Gold, modifier = Modifier.size(62.dp))
                    }
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
                StatCard("24/7", "Trip support*", Icons.Default.SupportAgent, Modifier.weight(1f))
                StatCard("PKR", "Clear fares", Icons.Default.Payments, Modifier.weight(1f))
            }
            Text("*Demo concept only. Live support is not connected.", color = Muted, fontSize = 10.sp)
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
private fun RidesScreen(route: String, onRoute: (String) -> Unit, onSelect: (Ride) -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Find your ride", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Sample departures • Tomorrow and upcoming days", color = Muted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Karachi", "Quetta", "Larkana", "Gwadar").forEach { city ->
                FilterChip(selected = route == city, onClick = { onRoute(city) }, label = { Text(city) }, leadingIcon = if (route == city) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null)
            }
        }
        sampleRides.filter { it.city == route || route == "Sindh" || route == "Makran" }.forEach { ride ->
            RideCard(ride, onClick = { onSelect(ride) })
        }
        if (sampleRides.none { it.city == route }) sampleRides.take(2).forEach { RideCard(it, onClick = { onSelect(it) }) }
        Text("Demo data only. Times, drivers and prices are illustrative.", color = Muted, fontSize = 11.sp)
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
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { SeatButton("Front seat", seat == "Front seat", onSeat, true) }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = .15f))
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            SeatButton("Rear left", seat == "Rear left", onSeat, true)
                            SeatButton("Rear middle", seat == "Rear middle", onSeat, false)
                            SeatButton("Rear right", seat == "Rear right", onSeat, true)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Tap an available seat to select it", color = Color(0xFFB8C9D7), fontSize = 10.sp)
                    }
                }
            }
        }
        Text("Drop-off destination", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(ride.city, "Larkana", "Ratto Dero", "Gwadar", "Turbat").distinct().forEach { city ->
                FilterChip(selected = drop == city, onClick = { onDrop(city) }, label = { Text(city) })
            }
        }
        Text("Luggage", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Small bag", "Medium bag", "Large box").forEach { item -> FilterChip(selected = luggage == item, onClick = { onLuggage(item) }, label = { Text(item, fontSize = 11.sp) }) } }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("TOTAL DEMO FARE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("PKR ${fareFor(ride, seat)}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("Includes selected seat • no payment taken", color = Muted, fontSize = 10.sp) }
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

private fun fareFor(ride: Ride, seat: String): Int = if (seat == "Front seat") maxOf(ride.price + 1000, 4000) else ride.price

@Composable
private fun BookingsScreen(booked: Boolean, ride: Ride, seat: String, onBrowse: () -> Unit, onTicket: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your bookings", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        if (booked) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Mint); Spacer(Modifier.width(8.dp)); Text("BOOKING CONFIRMED (DEMO)", color = Color(0xFF16876E), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                    Text(ride.route, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${ride.time} • ${ride.car}", color = Muted, fontSize = 12.sp)
                    Text("$seat • PKR ${fareFor(ride, seat)}", color = Ink, fontWeight = FontWeight.Bold)
                    Button(onClick = onTicket, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Icon(Icons.Default.ConfirmationNumber, null); Spacer(Modifier.width(8.dp)); Text("Open digital ticket") }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Luggage, null, tint = Navy2, modifier = Modifier.size(54.dp))
                    Text("Your next journey starts here", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Your confirmed demo bookings will appear here.", color = Muted, fontSize = 12.sp)
                    Button(onClick = onBrowse, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Find a ride") }
                }
            }
        }
        Text("Payments and ticket confirmations are simulated in this demo.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TicketScreen(ride: Ride, seat: String, fare: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text("Digital travel ticket", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Gold), contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = Ink) }
                    Spacer(Modifier.width(10.dp)); Column { Text("OTS TRANSPORT", color = Ink, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text("DEMO BOARDING PASS", color = Muted, fontSize = 9.sp) }
                    Spacer(Modifier.weight(1f)); Icon(Icons.Default.Verified, null, tint = Color(0xFF159C7B))
                }
                HorizontalDivider(color = Color(0xFFE2E8EE))
                Text(ride.route, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(ride.time, color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { TicketDetail("SEAT", seat); TicketDetail("DRIVER", ride.driver); TicketDetail("FARE", "PKR $fare") }
                HorizontalDivider(color = Color(0xFFE2E8EE))
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Color(0xFF9B681F)); Spacer(Modifier.width(7.dp)); Text("DEMO ONLY • NOT VALID FOR REAL TRAVEL", color = Color(0xFF9B681F), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                Text("Booking reference: OTS-DEMO-2048", color = Muted, fontSize = 11.sp)
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
    onLogin: () -> Unit,
    onSubmit: (String, String, Int, String, String, String, Int, String, String) -> Unit
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
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsCar, null, tint = Navy, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column { Text("Driver centre", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text("One registered car • multiple routes", color = Muted, fontSize = 12.sp) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("APPLICATION STATUS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(when (status) {
                    "approved" -> "Approved — you can prepare trip listings."
                    "pending", "submitting" -> "Pending review — publishing is locked."
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
        } else if (status == "not_submitted" || status == "unknown") {
            Text("Driver and vehicle application", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Register exactly one car. Use test details while trying the app. Secure driver-photo and CNIC-document upload is still being connected; do not type your CNIC number here.", color = Muted, fontSize = 12.sp)
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
            Button(
                onClick = { onSubmit(name, phone, year.toIntOrNull() ?: 2020, make, model, plate, seats.toIntOrNull() ?: 3, color, luggage) },
                enabled = status != "submitting" && name.isNotBlank() && phone.isNotBlank() && make.isNotBlank() && model.isNotBlank() && plate.isNotBlank() && (year.toIntOrNull() ?: 0) in 1980..2100 && (seats.toIntOrNull() ?: 0) in 1..20,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)
            ) { Text(if (status == "submitting") "Submitting…" else "Submit for approval") }
        } else {
            Text("Registered vehicle", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("Your account has one registered car. Vehicle edits and document uploads will be enabled in the next connected workflow.", color = Muted, fontSize = 12.sp)
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF9AA7B4))) {
                Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Trip publishing requires full approval workflow")
            }
        }
    }
}

@Composable
private fun ProfileScreen(role: String, onRole: (String) -> Unit, onLogin: () -> Unit, signedIn: Boolean, email: String, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your profile", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Ink, modifier = Modifier.size(32.dp)) }
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
