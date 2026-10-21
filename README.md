# OTS Transport — Android app

Native Android passenger and driver app for OTS Transport in Khuzdar, built with Kotlin and Jetpack Compose. The public app is separate from the private OTS Admin app.

## Current app features

- Passenger and driver flows in one public app, with separate sign-in/sign-up entry points in Settings.
- English/Urdu language choice saved on the device and a Settings option to change it.
- Navigation tabs, back controls and Android Back handling.
- Passenger trip discovery, seat selection, fares, bookings and cancellation requests through the shared Supabase backend.
- Driver application with one registered car per driver, private driver/CNIC/car-document uploads, car-detail correction and trip publishing after administrator approval.
- Improved text-field contrast and a Google sign-in entry point using PKCE.

## Google sign-in setup (required before use)

Google sign-in is free to use without a per-login fee, but the OAuth provider must be configured by the project owner:

1. In Google Cloud Console, create/select a project and configure the OAuth consent screen.
2. Create an OAuth client of type **Web application**. Add this authorized redirect URI: `https://konrdhiwgluwmaakskdr.supabase.co/auth/v1/callback`.
3. In Supabase Dashboard → Authentication → Sign In / Providers → Google, enable Google and enter the Google client ID and client secret.
4. In Supabase Dashboard → Authentication → URL Configuration, add `ots-transport://auth-callback` to the allowed redirect URLs.
5. Build/install the latest APK and test Google sign-in on a device.

Do not commit Google client secrets, Supabase service-role keys, passwords or real CNIC documents. Only the public Supabase publishable key belongs in the Android client.

## Build APK

GitHub Actions builds a debug APK on pushes to the main branch. Open **Actions → Build OTS Android APK**, select a successful run and download the `OTS-Transport-APK` artifact.

## Before real-world launch

The UI and connected workflows still need full device and end-to-end QA, including Google OAuth after setup, driver approval, private document previews, bookings and cancellation handling. Paid payments, SMS OTP, live GPS and SOS integrations are not included. Use fictional identity documents for testing.
