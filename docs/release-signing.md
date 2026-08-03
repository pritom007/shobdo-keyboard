# Android release signing

Shohojakkhor release APKs use one permanent signing identity. Android accepts an
in-place update only when the application ID is unchanged, the new
`versionCode` is higher, and both APKs are signed by the same certificate.

## Certificate identity

- Alias: `shohojakkhor-release`
- Store type: PKCS#12
- Algorithm: RSA 4096 / SHA-256 with RSA
- Valid through: 2053-12-19
- SHA-256 fingerprint:
  `5A:0A:48:DA:BC:97:28:16:77:11:69:D3:4E:ED:33:A3:2E:AA:D4:77:DC:C7:DE:B1:18:99:56:E1:D7:AC:7A:BD`

The keystore and its password are never committed. The repository Actions
secrets are:

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_STORE_PASSWORD`

## Versioning and releases

Push an exact semantic-version tag such as `v0.0.2`. The release workflow
derives both values automatically:

- `versionName`: `0.0.2`
- `versionCode`: `2`

The formula is `major * 1,000,000 + minor * 1,000 + patch`, so future minor
and major releases remain greater than earlier builds.

The workflow builds two signed APKs and one signed Android App Bundle, verifies
the APK certificate fingerprint, and publishes all three files to the GitHub
Release.

## Recovery and rotation

Keep an offline backup of the PKCS#12 file and its password. GitHub Actions
secrets cannot be read back after creation. Losing the key means sideloaded
installations signed by it cannot receive updates from a replacement key.

When publishing through Google Play, enroll in Play App Signing and treat this
keystore as the upload key. Follow the Play Console key-upgrade process rather
than replacing signing files directly.
