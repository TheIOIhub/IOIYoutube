package com.ioi.youtube.di

import android.content.Context
import androidx.room.Room
import com.ioi.youtube.data.extractor.OkHttpDownloader
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.CachedVideoDetailsDao
import com.ioi.youtube.data.local.IOIDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlin.random.Random

private const val DATABASE_NAME = "ioitube.db"

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDownloader(client: OkHttpClient): Downloader = OkHttpDownloader(client)

    /** Джерело випадковості для пулiв запитiв стрiчки; тести пiдставляють Random(seed). */
    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IOIDatabase =
        Room.databaseBuilder(context, IOIDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCachedVideoDao(database: IOIDatabase): CachedVideoDao =
        database.cachedVideoDao()

    @Provides
    fun provideCachedVideoDetailsDao(database: IOIDatabase): CachedVideoDetailsDao =
        database.cachedVideoDetailsDao()
}
