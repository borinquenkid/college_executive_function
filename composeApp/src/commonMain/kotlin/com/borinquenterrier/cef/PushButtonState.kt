package com.borinquenterrier.cef

enum class PushButtonVariant { CONFLICT, GOOGLE, LOCAL }

object PushButtonState {

    fun variant(hasConflicts: Boolean, isConnected: Boolean): PushButtonVariant = when {
        hasConflicts -> PushButtonVariant.CONFLICT
        isConnected  -> PushButtonVariant.GOOGLE
        else         -> PushButtonVariant.LOCAL
    }

    fun label(variant: PushButtonVariant): String = when (variant) {
        PushButtonVariant.CONFLICT -> "Force Sync Remaining"
        PushButtonVariant.GOOGLE   -> "Push to Google Calendar"
        PushButtonVariant.LOCAL    -> "Save to Local Calendar"
    }
}
