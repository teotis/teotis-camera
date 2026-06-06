package com.opencamera.app

import com.opencamera.core.session.SessionIntent

internal class MainActivityLifecycleDispatcher {
    private var detachedForPause = false

    fun onStart(dispatch: (SessionIntent) -> Unit) {
        detachedForPause = false
        dispatch(SessionIntent.Boot)
        dispatch(SessionIntent.PreviewHostAttached)
    }

    fun onResume(dispatch: (SessionIntent) -> Unit) {
        if (detachedForPause) {
            detachedForPause = false
            dispatch(SessionIntent.PreviewHostAttached)
        }
    }

    fun onPause(dispatch: (SessionIntent) -> Unit) {
        detachedForPause = true
        dispatch(SessionIntent.PreviewHostDetached("Activity paused"))
    }

    fun onStop(dispatch: (SessionIntent) -> Unit) {
        if (!detachedForPause) {
            dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
        }
    }

    fun onDestroy(dispatch: (SessionIntent) -> Unit) {
        dispatch(SessionIntent.Shutdown)
    }
}
