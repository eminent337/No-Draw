package com.example.data.remote

import com.example.data.model.FlexCalendarDateAdapter
import com.example.data.model.FlexScoreAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "https://site.api.espn.com/"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(FlexScoreAdapter())
            .add(FlexCalendarDateAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("User-Agent", "NoDrawStatsAndroid/1.0")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }

    val espnSoccerApi: EspnSoccerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(EspnSoccerApi::class.java)
    }
}
