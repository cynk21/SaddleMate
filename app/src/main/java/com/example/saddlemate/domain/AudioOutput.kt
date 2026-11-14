package com.example.saddlemate.domain

data class AlertOutput(val speak: Boolean, val haptic: Boolean, val tone: Boolean)

/** Maps the selected audio mode to the channels a radar alert may use. */
fun AudioMode.outputForAlert(): AlertOutput = when (this) {
    AudioMode.FULL_COPILOT -> AlertOutput(speak = true, haptic = true, tone = false)
    AudioMode.RADAR_ONLY -> AlertOutput(speak = false, haptic = true, tone = true)
    AudioMode.GROUP_HAPTIC -> AlertOutput(speak = false, haptic = true, tone = false)
    AudioMode.SILENT -> AlertOutput(speak = false, haptic = false, tone = false)
}
