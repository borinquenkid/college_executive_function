This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

## Google Cloud Console & API Setup

To enable Calendar sync and file import capabilities:

1. **Enable APIs**:
   Go to the Google Cloud Console and enable the following APIs for your project:
   - **Google Calendar API**: [Enable Calendar API](https://console.developers.google.com/apis/api/calendar-json.googleapis.com/overview)
   - **Google Drive API**: [Enable Drive API](https://console.developers.google.com/apis/api/drive.googleapis.com/overview)

2. **Configure OAuth Consent**:
   - Go to APIs & Services > OAuth Consent Screen.
   - Set the User Type to **External** and the Publishing Status to **Testing**.
   - Under **Test users**, add your Google Account email (e.g. `your-email@gmail.com`).
   - Under **Scopes**, ensure the following scopes are added:
     - `.../auth/calendar`
     - `.../auth/drive.readonly`

3. **Create Credentials**:
   - Go to APIs & Services > Credentials.
   - Click **Create Credentials** > **OAuth client ID**.
   - Select application type **Desktop app**.
   - Copy the generated Client ID and Client Secret.

3b. **Second client, for the self-hosted web deployment only** (skip this if you're only building
    the Android/iOS/Desktop apps): Google restricts a Desktop-app client's redirect URIs to
    loopback/OOB addresses, so it can't be reused for a browser redirect flow. Create a *second*
    OAuth client ID in the same project:
    - Application type **Web application**.
    - Under **Authorized redirect URIs**, add `<CEF_APP_BASE_URL>/api/auth/google/callback` (the
      same HTTPS origin the server is deployed at — see DEPLOYMENT.md).
    - Copy this Client ID/Secret separately — they go into `.env` as `CEF_GOOGLE_WEB_CLIENT_ID`/
      `CEF_GOOGLE_WEB_CLIENT_SECRET`, not the Desktop app's `GOOGLE_CLIENT_ID`/`SECRET`. See
      [docs/adr/0008-self-serve-google-oauth-web-flow.md](docs/adr/0008-self-serve-google-oauth-web-flow.md).

4. **Add to `.env`**:
   Create a `.env` file at the root of the project (copying from `.env_template`) and populate it:
   ```env
   GOOGLE_CLIENT_ID=your_client_id_here
   GOOGLE_CLIENT_SECRET=your_client_secret_here
   ```

5. **CI/CD Repository Secrets (for GitHub Releases)**:
   When GitHub Actions packages and builds the desktop installers (`.dmg`, `.msi`, `.deb`), it requires the OAuth credentials to be configured as Repository Secrets on GitHub.
   To configure these:
   - Go to your repository on GitHub.
   - Click **Settings** > **Secrets and variables** > **Actions** > **New repository secret**.
   - Add the following secrets:
     * `GOOGLE_CLIENT_ID`: (Your Google Client ID)
     * `GOOGLE_CLIENT_SECRET`: (Your Google Client Secret)
     * `WEB3FORMS_ACCESS_KEY`: (Optional. Web3Forms access key for anonymous bug reporting)


---

## Server & Web Client (Ktor + React)

Alongside the Android/iOS/Desktop apps, [/server](./server) is a Ktor backend and [/web](./web) is a React frontend for it — the pieces a college runs themselves to host CEF for their own students. See [DEPLOYMENT.md](DEPLOYMENT.md) for local dev setup, production deployment (Docker), and maintenance (backups, vacuuming). See [SPEC.md](SPEC.md) for the API/protocol design and [docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md](docs/adr/0002-multi-tenant-docker-path-partitioned-storage.md) for the multi-tenancy architecture.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…