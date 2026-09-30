package com.timeplanning.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

/**
 * Compose Multiplatform's web renderer (Skiko) draws everything through a
 * WebGL2 canvas — there's no software-canvas fallback. With WebGL2
 * unavailable (hardware acceleration disabled in the browser, a locked-down
 * machine, certain VMs), the page would otherwise just be a silent black
 * rectangle with no indication why. Checking first and showing a plain HTML
 * message turns an inexplicable failure into an actionable one.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun hasWebGL2(): Boolean = try {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.getContext("webgl2") != null
} catch (e: Throwable) {
    false
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    if (!hasWebGL2()) {
        document.getElementById("composeApplication")?.innerHTML = """
            <div style="font-family: sans-serif; max-width: 480px; margin: 48px auto; padding: 0 16px; line-height: 1.5;">
                <h2>Can't display TimePlanning</h2>
                <p>This app needs hardware-accelerated graphics (WebGL2), which your browser currently has turned off or unavailable.</p>
                <p><strong>In Chrome:</strong> go to <code>chrome://settings/system</code>, turn on "Use graphics acceleration when available", then fully quit and reopen Chrome (use the Relaunch button, or Cmd+Q / close every window) — just reloading this page isn't enough.</p>
                <p>Already on? Quit and reopen Chrome anyway: after a graphics crash it can switch graphics off for the rest of the session. <code>chrome://gpu</code> shows whether WebGL2 is available and why not.</p>
                <p>If that doesn't help (a work/managed device, or a browser that doesn't support it), try a different browser such as Safari or Firefox.</p>
            </div>
        """.trimIndent()
        return
    }
    ComposeViewport("composeApplication") {
        App()
    }
}
