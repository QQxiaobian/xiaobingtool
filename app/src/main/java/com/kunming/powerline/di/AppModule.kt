package com.kunming.powerline.di

import android.content.Context
import androidx.room.Room
import com.kunming.powerline.BuildConfig
import com.kunming.powerline.data.local.AppDatabase
import com.kunming.powerline.data.local.dao.BatchDao
import com.kunming.powerline.data.local.dao.CableDao
import com.kunming.powerline.data.local.dao.LineDao
import com.kunming.powerline.data.local.dao.MiscDao
import com.kunming.powerline.data.local.dao.OverlayDao
import com.kunming.powerline.data.local.dao.PhotoDao
import com.kunming.powerline.data.local.dao.PointDao
import com.kunming.powerline.data.local.dao.TowerDao
import com.kunming.powerline.data.local.dao.TransactionDao
import com.kunming.powerline.data.remote.PmsApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun lineDao(db: AppDatabase): LineDao = db.lineDao()
    @Provides fun towerDao(db: AppDatabase): TowerDao = db.towerDao()
    @Provides fun cableDao(db: AppDatabase): CableDao = db.cableDao()
    @Provides fun pointDao(db: AppDatabase): PointDao = db.pointDao()
    @Provides fun photoDao(db: AppDatabase): PhotoDao = db.photoDao()
    @Provides fun overlayDao(db: AppDatabase): OverlayDao = db.overlayDao()
    @Provides fun batchDao(db: AppDatabase): BatchDao = db.batchDao()
    @Provides fun miscDao(db: AppDatabase): MiscDao = db.miscDao()
    @Provides fun transactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG_MODE) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun providePmsApi(client: OkHttpClient, moshi: Moshi): PmsApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.PMS_BASE_URL.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PmsApi::class.java)

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}
