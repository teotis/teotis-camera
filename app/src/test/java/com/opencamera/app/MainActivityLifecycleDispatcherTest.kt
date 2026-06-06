package com.opencamera.app

import com.opencamera.core.session.SessionIntent
import kotlin.test.Test
import kotlin.test.assertEquals

class MainActivityLifecycleDispatcherTest {
    @Test
    fun `pause detaches preview and resume reattaches once`() {
        val dispatcher = MainActivityLifecycleDispatcher()
        val intents = mutableListOf<SessionIntent>()

        dispatcher.onPause(intents::add)
        dispatcher.onResume(intents::add)
        dispatcher.onResume(intents::add)

        assertEquals(
            listOf<SessionIntent>(
                SessionIntent.PreviewHostDetached("Activity paused"),
                SessionIntent.PreviewHostAttached
            ),
            intents
        )
    }

    @Test
    fun `stop after pause does not detach twice`() {
        val dispatcher = MainActivityLifecycleDispatcher()
        val intents = mutableListOf<SessionIntent>()

        dispatcher.onPause(intents::add)
        dispatcher.onStop(intents::add)

        assertEquals(
            listOf<SessionIntent>(SessionIntent.PreviewHostDetached("Activity paused")),
            intents
        )
    }

    @Test
    fun `destroy dispatches shutdown`() {
        val dispatcher = MainActivityLifecycleDispatcher()
        val intents = mutableListOf<SessionIntent>()

        dispatcher.onDestroy(intents::add)

        assertEquals(listOf<SessionIntent>(SessionIntent.Shutdown), intents)
    }
}
