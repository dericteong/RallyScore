# RallyScore Release Checklist

This checklist is a low-risk release-readiness guide for publishing RallyScore to Google Play.

## Android App Bundle Release Process

- Build release artifacts as Android App Bundles (`.aab`) for Play submission.
- Use the phone/tablet app bundle for the handheld listing.
- Use the Wear OS app bundle for the watch listing if distributed separately.
- Keep local debug APK assembly for manual device testing, but do not treat debug APKs as release artifacts.

## Play App Signing / Upload Key

- Use Google Play App Signing for production releases.
- Store the upload key securely outside the repository.
- Document who owns the upload key and where the signing process runs.
- Do not store production signing keys in the repository.

## Versioning

- `versionCode` must increase monotonically for every Play upload.
- `versionName` should follow semantic versioning, for example:
  - `0.1.0`
  - `0.1.1`
  - `0.2.0`
- Confirm both phone and wear modules are updated consistently before each release.

## Release History

| Version | versionCode | Status | Notes |
| --- | --- | --- | --- |
| 0.1.0 | 1 | Internal | Initial development build |

## Target SDK Status

- Phone/tablet app currently targets API 35.
- Wear app currently targets API 35.
- Re-check Google Play target API requirements before every production submission.

## Device Behavior Notes

- Phone/tablet experience is landscape-first by product design.
- Wear app supports standalone scoring and connected scoring.
- Phone backup is intentionally disabled to avoid restoring stale live match/player state across devices.

## Permissions and Local Network Explanation

- `INTERNET` is used for phone-tablet and watch-tablet local network sync paths.
- `ACCESS_WIFI_STATE` is used to inspect current network state for local connectivity.
- `CHANGE_WIFI_MULTICAST_STATE` is used to support local network discovery on the same Wi-Fi or hotspot network.
- `VIBRATE` on Wear is used for watch haptic feedback during scoring.
- RallyScore does not request location, contacts, camera, microphone, SMS, or notification runtime permission.

## Pre-Release Checklist

- All unit tests pass.
- Debug builds compile.
- Release bundle builds successfully.
- `versionCode` incremented.
- `versionName` updated.
- `CHANGELOG` updated.
- Git working tree clean.
- Commit pushed to GitHub.
- Release tag created.

## Play Store Assets

- App icon.
- Feature graphic.
- Phone screenshots.
- Tablet screenshots.
- Wear screenshots.
- Short description.
- Full description.
- Privacy Policy URL.
- Data Safety completed.

## Monetization

- Free version features confirmed.
- Premium features confirmed.
- Google Play Billing configured, if applicable.
- Ads configured, if applicable.

## Manual Device Testing Checklist

- Phone Only:
  - Start a match from setup.
  - Score both sides.
  - Undo.
  - End match.
  - Reopen app and confirm expected restore behavior.
- Watch Only:
  - Start standalone match on watch.
  - Score both sides.
  - Undo.
  - End match.
- Tablet Only:
  - Start standalone match on tablet.
  - Score both sides.
  - Undo.
  - Open correction flow.
  - End match.
- Watch + Phone:
  - Start on phone or from connected watch flow.
  - Confirm watch commands update phone score.
  - Confirm watch receives confirmed score state.
- Phone + Tablet:
  - Confirm local network discovery.
  - Confirm tablet receives phone state.
  - Confirm tablet scoring commands update phone and echo back confirmed state.
- Watch + Phone + Tablet:
  - Confirm watch scoring updates phone and tablet.
  - Confirm Undo and End propagate correctly.
- Edge cases:
  - Test under battery saver mode.
  - Test using phone hotspot instead of external Wi-Fi.
  - Confirm watch behavior if the watch disconnects during scoring.
  - Confirm tablet reconnects after network loss.
  - Kill the app during an active match and confirm expected restore behavior.
  - Test low-battery conditions on the watch.
- UI and device checks:
  - Confirm landscape readability on phone and tablet.
  - Confirm round watch layout has no clipping.
  - Confirm app icons and labels appear correctly on launcher surfaces.
  - Confirm no crash logs after setup-field editing and device reconnection scenarios.

## Future Improvements

- Modernize deprecated Android APIs.
- Adopt the Android 12+ SplashScreen API.
- Add a monochrome adaptive icon.
- Upgrade Gradle / AGP.
- Run an accessibility review.
