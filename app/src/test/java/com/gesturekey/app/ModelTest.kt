package com.gesturekey.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStabilizerTest {

    private val love = Gesture.LOVE.label
    private val thumb = Gesture.THUMB_UP.label

    @Test
    fun firesOnceAfterHoldingForNFrames() {
        val s = GestureStabilizer(setOf(love), framesNeeded = 3)
        assertNull(s.offer(love, 0.9f))
        assertNull(s.offer(love, 0.9f))
        assertEquals(love, s.offer(love, 0.9f))
        assertNull(s.offer(love, 0.9f)) // keeps holding: no second fire
    }

    @Test
    fun switchingGestureRestartsTheCount() {
        val s = GestureStabilizer(setOf(love, thumb), framesNeeded = 3)
        s.offer(love, 0.9f)
        s.offer(love, 0.9f)
        assertNull(s.offer(thumb, 0.9f))
        assertEquals(1, s.count)
        assertNull(s.offer(thumb, 0.9f))
        assertEquals(thumb, s.offer(thumb, 0.9f))
    }

    @Test
    fun lowScoreOrNoneBreaksTheHold() {
        val s = GestureStabilizer(setOf(love), framesNeeded = 3, minScore = 0.6f)
        s.offer(love, 0.9f)
        s.offer(love, 0.9f)
        assertNull(s.offer(love, 0.4f))
        assertEquals(0, s.count)
        s.offer(love, 0.9f)
        assertNull(s.offer(Gesture.NONE, 0.99f))
        assertEquals(0f, s.progress)
    }

    @Test
    fun disabledGesturesNeverFire() {
        val s = GestureStabilizer(setOf(love), framesNeeded = 1)
        assertNull(s.offer(Gesture.FIST.label, 0.99f))
        assertEquals(love, s.offer(love, 0.99f))
    }
}

class SettingsModelTest {

    @Test
    fun defaultsEnableOnlyTheExpectedGestures() {
        val labels = AppSettings.defaults().enabledLabels
        assertTrue(Gesture.LOVE.label in labels)
        assertFalse(Gesture.OPEN_PALM.label in labels) // too easy to trigger by accident
        assertFalse(Gesture.FIST.label in labels)
    }

    @Test
    fun blankMessageDisablesAGesture() {
        val d = AppSettings.defaults()
        val s = d.copy(gestures = d.gestures.map { if (it.gesture == Gesture.LOVE) it.copy(message = " ") else it })
        assertFalse(Gesture.LOVE.label in s.enabledLabels)
    }

    @Test
    fun overrideTargetWinsOverDefault() {
        val d = AppSettings.defaults()
        val love = d.forLabel(Gesture.LOVE.label)!!
        assertEquals(d.defaultTarget, d.targetFor(love))
        val custom = Target(TargetApp.TELEGRAM)
        assertEquals(custom, d.targetFor(love.copy(override = custom)))
    }

    @Test
    fun everyCannedLabelMapsToAGesture() {
        listOf("ILoveYou", "Thumb_Up", "Victory", "Pointing_Up", "Thumb_Down", "Closed_Fist", "Open_Palm")
            .forEach { assertEquals(it, Gesture.fromLabel(it)?.label) }
        assertNull(Gesture.fromLabel(Gesture.NONE))
    }
}

class MessageLinksTest {

    @Test
    fun normalizesInternationalNumbers() {
        assertEquals("923001234567", MessageLinks.normalizePhone("+92 300-123 4567"))
        assertEquals("923001234567", MessageLinks.normalizePhone("0092 300 1234567"))
        assertEquals("447700900123", MessageLinks.normalizePhone("(44) 7700 900123"))
    }

    @Test
    fun whatsAppLinkEncodesEmojiAndSpaces() {
        val url = MessageLinks.whatsAppUrl("+92 300 1234567", "❤️ I love you")
        assertTrue(url.startsWith("https://api.whatsapp.com/send?phone=923001234567&text="))
        assertFalse(url.contains(" "))
        assertFalse(url.contains("+"))
        assertTrue(url.endsWith("I%20love%20you"))
    }

    @Test
    fun smsUriKeepsPlus() {
        assertEquals("smsto:+923001234567", MessageLinks.smsUri("+92 300 123 4567"))
    }
}
