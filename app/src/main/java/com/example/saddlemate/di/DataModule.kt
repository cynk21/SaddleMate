package com.example.saddlemate.di

import android.content.Context
import androidx.room.Room
import com.example.saddlemate.data.local.FeedbackNoteDao
import com.example.saddlemate.data.local.HorseDao
import com.example.saddlemate.data.local.OwnRouteDao
import com.example.saddlemate.data.local.PendingVoicePinDao
import com.example.saddlemate.data.local.RideDao
import com.example.saddlemate.data.local.SaddleMateDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SaddleMateDatabase =
        Room.databaseBuilder(context, SaddleMateDatabase::class.java, "saddlemate.db")
            .addMigrations(
                SaddleMateDatabase.MIGRATION_1_2,
                SaddleMateDatabase.MIGRATION_2_3,
                SaddleMateDatabase.MIGRATION_3_4,
                SaddleMateDatabase.MIGRATION_4_5,
                SaddleMateDatabase.MIGRATION_5_6,
                SaddleMateDatabase.MIGRATION_6_7,
                SaddleMateDatabase.MIGRATION_7_8,
                SaddleMateDatabase.MIGRATION_8_9,
                SaddleMateDatabase.MIGRATION_9_10,
            )
            .build()

    @Provides
    fun provideHorseDao(db: SaddleMateDatabase): HorseDao = db.horseDao()

    @Provides
    fun provideRideDao(db: SaddleMateDatabase): RideDao = db.rideDao()

    @Provides
    fun provideOwnRouteDao(db: SaddleMateDatabase): OwnRouteDao = db.ownRouteDao()

    @Provides
    fun providePendingVoicePinDao(db: SaddleMateDatabase): PendingVoicePinDao = db.pendingVoicePinDao()

    @Provides
    fun provideFeedbackNoteDao(db: SaddleMateDatabase): FeedbackNoteDao = db.feedbackNoteDao()
}
