package com.example.saddlemate.di

import com.example.saddlemate.domain.FallDetector
import com.example.saddlemate.domain.HazardRadarEngine
import com.example.saddlemate.domain.TelemetryEngine
import com.example.saddlemate.domain.WelfareEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideTelemetryEngine(): TelemetryEngine = TelemetryEngine()

    @Provides
    fun provideWelfareEngine(): WelfareEngine = WelfareEngine()

    @Provides
    fun provideHazardRadarEngine(telemetry: TelemetryEngine): HazardRadarEngine =
        HazardRadarEngine(telemetry = telemetry)

    @Provides
    fun provideFallDetector(): FallDetector = FallDetector()
}
