# Android release signing

Shobdo release APKs use one permanent signing identity. Android accepts an
in-place update only when the application ID is unchanged, the new
`versionCode` is higher, and both APKs are signed by the same certificate.

## Certificate identity

- Alias: `shobdo-release`
- Store type: PKCS#12
- Algorithm: RSA 4096 / SHA-256 with RSA
- Valid through: 2053-12-19
- SHA-256 fingerprint:
  `74:81:36:D3:8F:05:59:36:2A:89:EA:DC:3D:2E:2D:EC:11:1D:8C:F7:4C:6F:A4:90:28:8A:18:EF:A6:49:A3:04`

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
