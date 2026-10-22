# OTS Transport — Deployment and owner setup

## Implemented in the current source

- Separate public passenger/driver and private administrator Android apps.
- Five-seat vehicle model: one driver seat that cannot be booked, one front passenger seat and three rear passenger seats. Only approved passenger capacity (maximum four) can be published.
- Database constraints and seat-selection trigger validation for passenger seat labels, trip capacity, published/future trips, fare consistency and seat collisions.
- Pending-payment booking holds, which expire after 15 minutes if no payment reference is submitted. Once a reference is submitted, the hold remains for administrator review.
- Manual Easypaisa/JazzCash transfer reference submission and an administrator review screen. An administrator must verify the transfer in the actual wallet account before confirming the booking.
- Free Google Maps directions opened externally from the seat-selection screen.
- User-initiated sharing of the last known phone location (if permission is granted) and an SOS button that opens the dialer with Rescue 1122; it does not place a call automatically.
- Supabase phone OTP send/verify client flow, ready for SMS provider configuration.
- Hosted account-verification landing page: https://ots-auth-portal.vercel.app/ (source: `auth-website/index.html`).
- Generated booking-reference QR codes. The QR is an identifier only; it is not independent proof of boarding.

## Required Supabase Auth configuration

The connected Supabase tools can manage the database but do not expose the Auth URL configuration or provider credentials. Complete these in the Supabase Dashboard for project `konrdhiwgluwmaakskdr`:

1. Under **Authentication → URL Configuration**, set the Site URL to `https://ots-auth-portal.vercel.app/`.
2. Add `https://ots-auth-portal.vercel.app/**` to the allowed redirect URLs.
3. Keep `ots-transport://auth-callback` in the allowed redirect URLs for Android deep links.
4. Under **Authentication → Providers → Google**, enable Google and enter the OAuth client ID and client secret from your Google Cloud OAuth configuration. Use the Supabase callback URL `https://konrdhiwgluwmaakskdr.supabase.co/auth/v1/callback` in Google Cloud.
5. Under **Authentication → Providers → Phone**, enable phone auth and configure an SMS provider. The app calls Supabase's SMS OTP endpoints, but messages cannot be delivered until a provider is configured. Provider usage may cost money; do not enable a paid service without approval.
6. Enable leaked-password protection in Auth security settings when available on the project's plan.

## Payment limitations

The wallet flow is a **manual transfer and review workflow**, not a direct Easypaisa/JazzCash merchant API integration. It does not automatically query the wallet provider. Never verify a payment just because a passenger entered a reference; check the real account transfer first. A merchant API integration requires approved merchant access and provider credentials.

## Production checks still required

- Complete the Supabase Auth URL/provider setup above and test real email confirmation, Google OAuth and SMS delivery.
- Test passenger registration, driver application review, trip publication, seat collisions, payment expiry, manual payment review and cancellation on physical Android devices.
- Configure the owner's email templates later if a custom sender/domain is desired.
- Continuous live GPS tracking, automatic SOS escalation and push notifications are not implemented yet; the current location action shares a last-known location once and SOS only opens the dialer.
- The app's current QR is a booking reference only; server-side boarding verification has not been implemented.

## Builds

APK files are uploaded as GitHub Actions artifacts from each repository's Actions workflow. Use the latest successful run on `main`; do not install an artifact from a failed or older run.
