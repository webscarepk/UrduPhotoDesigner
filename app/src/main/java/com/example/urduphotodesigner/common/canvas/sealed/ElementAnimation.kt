package com.example.urduphotodesigner.common.canvas.sealed

sealed class ElementAnimation(val name: String) {
    object Rise : ElementAnimation("Rise")
    object Pan : ElementAnimation("Pan")
    object Fade : ElementAnimation("Fade")
    object Pop : ElementAnimation("Pop")
    object Wipe : ElementAnimation("Wipe")
    object Blur : ElementAnimation("Blur")
    object Succession : ElementAnimation("Succession")
    object Breathe : ElementAnimation("Breathe")
    object Baseline : ElementAnimation("Baseline")
    object Drift : ElementAnimation("Drift")
    object Tectonic : ElementAnimation("Tectonic")
    object Tumble : ElementAnimation("Tumble")
    object Neon : ElementAnimation("Neon")
    object Scrapbook : ElementAnimation("Scrapbook")
    object Stomp : ElementAnimation("Stomp")

    // Add-on effects
    object Rotate : ElementAnimation("Rotate")
    object Flicker : ElementAnimation("Flicker")
    object Pulse : ElementAnimation("Pulse")
    object Wiggle : ElementAnimation("Wiggle")
}
