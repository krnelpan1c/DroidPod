# Releasing DroidPod

Signed APKs are built by GitHub Actions ([release.yml](.github/workflows/release.yml)) when a `v*` tag is pushed. The signing key never goes in the repo: it lives in repository secrets, and a backup copy lives somewhere safe offline.

> **Keep the keystore and its password backed up.** Android only installs an update if it's signed with the same key. If the key is lost, users have to uninstall and reinstall, losing their settings.

## 1. Create the signing key (once)

Use the `keytool` bundled with Android Studio. In PowerShell:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "$HOME\droidpod-release.jks" -alias droidpod `
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a keystore password and for name/organization details. The details are only embedded in the certificate; anything sensible is fine. The keystore is PKCS12, so **the key password is the same as the keystore password**.

Keep the `.jks` file outside the repository (`*.jks` is already in `.gitignore`), and back it up together with the password.

## 2. Add the repository secrets (once)

The workflow needs four secrets (*Settings → Secrets and variables → Actions → New repository secret*):

| Secret | Value |
|---|---|
| `DROIDPOD_KEYSTORE_BASE64` | The keystore, base64-encoded (see below) |
| `DROIDPOD_KEYSTORE_PASSWORD` | Keystore password |
| `DROIDPOD_KEY_ALIAS` | `droidpod` |
| `DROIDPOD_KEY_PASSWORD` | Same as the keystore password |

Copy the base64 keystore to the clipboard:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\droidpod-release.jks")) | Set-Clipboard
```

Or, with the GitHub CLI from the repo folder:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\droidpod-release.jks")) | gh secret set DROIDPOD_KEYSTORE_BASE64
gh secret set DROIDPOD_KEYSTORE_PASSWORD
gh secret set DROIDPOD_KEY_ALIAS --body droidpod
gh secret set DROIDPOD_KEY_PASSWORD
```

## 3. Test a signed build locally (recommended before the first release)

Release builds are minified with R8, so try one on a phone before tagging:

```powershell
$env:DROIDPOD_KEYSTORE = "$HOME\droidpod-release.jks"
$env:DROIDPOD_KEYSTORE_PASSWORD = Read-Host "Keystore password"
$env:DROIDPOD_KEY_ALIAS = "droidpod"
$env:DROIDPOD_KEY_PASSWORD = $env:DROIDPOD_KEYSTORE_PASSWORD
.\gradlew.bat assembleRelease

# Confirm it's signed with your key
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

Install `app/build/outputs/apk/release/app-release.apk` and go through setup, Now Playing, Players and both system styles.

## 4. Publish a release

1. Bump `versionCode` (always increase it) and `versionName` in [app/build.gradle.kts](app/build.gradle.kts), and commit.
2. Tag and push:

   ```powershell
   git tag v1.0.0
   git push origin main v1.0.0
   ```

3. The *Release* workflow builds `DroidPod-v1.0.0.apk`, signs it and attaches it to a new GitHub Release with generated notes. Edit the notes on GitHub if needed.
