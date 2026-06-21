package com.borinquenterrier.cef

class SyncGate(private val localRepo: StudentCalendarRepository) {
    fun isLive(): Boolean = isLiveSyncEnabled() && isGoogleLinked()

    private fun isLiveSyncEnabled(): Boolean =
        (localRepo.getSettings()?.getString("run_profile", "local") ?: "local") != "test"

    private fun isGoogleLinked(): Boolean {
        val settings = localRepo.getSettings() ?: return false
        return settings.getString("GOOGLE_ACCESS_TOKEN", "").isNotBlank()
    }
}
