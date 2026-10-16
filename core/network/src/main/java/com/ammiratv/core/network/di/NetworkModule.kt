package com.ammiratv.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Big catalogues arrive slowly on a weak Wi-Fi chip: a stall is 60 s without bytes
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Many panels refuse the bare "okhttp" agent; also, request URLs carry the account
            // password, so nothing is ever logged (logcat is readable by other tools).
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(
                    if (request.header("User-Agent") == null) {
                        request.newBuilder().header("User-Agent", "AmmiraTv/1.0 (Android)").build()
                    } else request
                )
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://placeholder.local/") // Base URL set per-request for Xtream
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
