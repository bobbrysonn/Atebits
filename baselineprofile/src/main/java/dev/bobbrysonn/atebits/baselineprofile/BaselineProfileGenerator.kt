package dev.bobbrysonn.atebits.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Generates the baseline profile committed at
 * app/src/release/generated/baselineProfiles/ by walking the hot path:
 * startup, then timeline flings (PostItem composition, Coil image loads,
 * video poster path).
 *
 * Run with: ./gradlew :app:generateReleaseBaselineProfile
 *
 * Requires a logged-in session on the connected device: install a
 * debug-signed build and log in first. The nonMinifiedRelease APK the
 * generator installs is also debug-signed, so app data (and the session)
 * survives the reinstall and the journey lands on the timeline. If the
 * run collects from the login screen instead, the profile silently loses
 * the timeline path — check the device before generating.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "dev.bobbrysonn.atebits",
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // Wait for the timeline (a scrollable list) to render
            device.wait(Until.hasObject(By.scrollable(true)), 10_000)
            val width = device.displayWidth
            val height = device.displayHeight

            repeat(3) {
                device.swipe(width / 2, (height * 0.8).toInt(), width / 2, (height * 0.2).toInt(), 10)
                device.waitForIdle()
            }
            device.swipe(width / 2, (height * 0.2).toInt(), width / 2, (height * 0.8).toInt(), 10)
            device.waitForIdle()
        }
    }
}
