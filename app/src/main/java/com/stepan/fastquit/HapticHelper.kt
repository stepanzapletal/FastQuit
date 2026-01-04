package com.stepan.fastquit

import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {
    fun click(view: View, prefs: UserPreferences) {
        if (prefs.hapticsGlobal && prefs.hapticsUI)
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun tick(view: View, prefs: UserPreferences) {
        if (prefs.hapticsGlobal && prefs.hapticsTimer)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun success(view: View, prefs: UserPreferences) {
        if (prefs.hapticsGlobal && prefs.hapticsEvents)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun warning(view: View, prefs: UserPreferences) {
        if (prefs.hapticsGlobal && prefs.hapticsWarnings)
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}