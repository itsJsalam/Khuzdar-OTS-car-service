# OTS Transport — Android Demo

Native Android demo for Khuzdar OTS Car Service, built with Kotlin and Jetpack Compose. This is an interactive demo: OTP, payments, GPS, and emergency actions are simulated; do not enter real CNIC details or use it for real trips/payments.

## Build APK

GitHub Actions builds a debug APK on every push and via **Actions → Build OTS Android APK → Run workflow**. Download the `OTS-Transport-Demo-APK` artifact from the workflow run.

## Demo features

- Passenger and driver modes
- Four route categories and ride search
- Interactive seat selection and sample fare calculation
- Demo checkout and digital ticket
- Driver ride publishing/profile preview
- Admin review dashboard simulation

The demo uses local in-memory sample data only. It is not production-ready and does not collect real personal information.