package com.example.saddlemate

import android.app.Application
import com.example.saddlemate.sensors.AlertFeedback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class SaddleMateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Warm up TTS at app start so the first radar alert is not delayed.
        EntryPointAccessors.fromApplication(this, FeedbackEntryPoint::class.java)
            .alertFeedback()
            .init()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FeedbackEntryPoint {
        fun alertFeedback(): AlertFeedback
    }
}
