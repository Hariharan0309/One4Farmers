//package com.example.farmers.di
//
//import com.example.farmers.service.FirebaseApiService
//import com.example.farmers.service.WeatherApiService
//import com.google.gson.GsonBuilder
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.InstallIn
//import com.squareup.moshi.Moshi
//import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
//import dagger.hilt.components.SingletonComponent
//import okhttp3.OkHttpClient
//import okhttp3.logging.HttpLoggingInterceptor
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.converter.moshi.MoshiConverterFactory
//import java.util.concurrent.TimeUnit
//import javax.inject.Named
//import javax.inject.Singleton
//
//@Module
//@InstallIn(SingletonComponent::class)
//object NetworkModule {
//
//    @Provides
//    @Singleton
//    fun provideGsonConverterFactory(): GsonConverterFactory {
//        return GsonConverterFactory.create(
//            GsonBuilder()
//                .serializeNulls()
//                .create()
//        )
//    }
//
//    @Provides
//    @Singleton
//    fun provideMoshi(): Moshi {
//        return Moshi.Builder()
//            .add(KotlinJsonAdapterFactory())
//            .build()
//    }
//
//    @Provides
//    @Singleton
//    fun provideOkHttpClient(): OkHttpClient {
//        val loggingInterceptor = HttpLoggingInterceptor().apply {
//            level = HttpLoggingInterceptor.Level.BODY
//        }
//
//        return OkHttpClient.Builder()
//            // Add the logging interceptor to the OkHttpClient
//            .addInterceptor(loggingInterceptor)
//            .readTimeout(2400, TimeUnit.SECONDS)
//            .connectTimeout(2400, TimeUnit.SECONDS)
//            .build()
//    }
//
//    @Provides
//    @Singleton
//    fun provideRetrofit(
//        gsonConverterFactory: GsonConverterFactory,
//        okHttpClient: OkHttpClient
//    ): Retrofit {
//        return Retrofit.Builder()
//            .baseUrl("https://us-central1-valued-mediator-461216-k7.cloudfunctions.net/")
//            .addConverterFactory(gsonConverterFactory)
//            .client(okHttpClient)
//            .build()
//    }
//
//    @Provides
//    @Singleton
//    @Named("WeatherRetrofit")
//    fun provideWeatherRetrofit(okHttpClient: OkHttpClient): Retrofit {
//        return Retrofit.Builder()
//            .baseUrl("https://api.open-meteo.com/") // Weather API base URL
//            .client(okHttpClient)
//            .addConverterFactory(MoshiConverterFactory.create())
//            .build()
//    }
//
//    @Provides
//    @Singleton
//    fun provideFirebaseApiService(
//        retrofit: Retrofit
//    ): FirebaseApiService {
//        return retrofit.create(FirebaseApiService::class.java)
//    }
//
//    @Provides
//    @Singleton
//    fun provideWeatherApiService(@Named("WeatherRetrofit") retrofit: Retrofit): WeatherApiService {
//        return retrofit.create(WeatherApiService::class.java)
//    }
//}



package com.example.farmers.di

import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.WeatherApiService
import com.google.gson.GsonBuilder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // --- Common Client ---
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .readTimeout(2400, TimeUnit.SECONDS)
            .connectTimeout(2400, TimeUnit.SECONDS)
            .build()
    }


    // --- Firebase Setup (Using GSON) - UNCHANGED ---
    @Provides
    @Singleton
    fun provideGsonConverterFactory(): GsonConverterFactory {
        return GsonConverterFactory.create(
            GsonBuilder()
                .serializeNulls()
                .create()
        )
    }

    @Provides
    @Singleton
    @Named("FirebaseRetrofit") // Named to avoid conflicts
    fun provideFirebaseRetrofit(
        gsonConverterFactory: GsonConverterFactory,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://us-central1-valued-mediator-461216-k7.cloudfunctions.net/")
            .addConverterFactory(gsonConverterFactory)
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideFirebaseApiService(
        @Named("FirebaseRetrofit") retrofit: Retrofit // Use the named instance
    ): FirebaseApiService {
        return retrofit.create(FirebaseApiService::class.java)
    }


    // --- Weather API Setup (Using Moshi) - CORRECTED ---
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory()) // This adapter is crucial for Kotlin data classes
            .build()
    }

    @Provides
    @Singleton
    @Named("WeatherRetrofit")
    fun provideWeatherRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit { // Inject the Moshi instance
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi)) // Use the Moshi instance here
            .build()
    }

    @Provides
    @Singleton
    fun provideWeatherApiService(@Named("WeatherRetrofit") retrofit: Retrofit): WeatherApiService {
        return retrofit.create(WeatherApiService::class.java)
    }
}