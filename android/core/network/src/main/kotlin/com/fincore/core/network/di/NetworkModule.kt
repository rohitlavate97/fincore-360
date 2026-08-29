package com.fincore.core.network.di

import com.fincore.core.network.BuildConfig
import com.fincore.core.network.authenticator.TokenAuthenticator
import com.fincore.core.network.interceptor.AuthInterceptor
import com.fincore.core.network.interceptor.CorrelationIdInterceptor
import com.fincore.core.security.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkMonitor(networkMonitor: com.fincore.core.network.monitor.ConnectivityManagerNetworkMonitor): com.fincore.core.network.monitor.NetworkMonitor = networkMonitor

    val BASE_URL: String = BuildConfig.BASE_URL

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): AuthInterceptor {
        return AuthInterceptor(tokenManager)
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(tokenManager: TokenManager, json: Json): TokenAuthenticator {
        return TokenAuthenticator(tokenManager, json, BASE_URL)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        // H-5: Disable plaintext HTTP body logging in release builds to prevent leaking tokens/PII
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.ENABLE_NETWORK_LOGS) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(CorrelationIdInterceptor())
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(loggingInterceptor)

        // H-6 / ADR-011: Enforce Certificate Pinning for production release builds
        if (!BuildConfig.ENABLE_NETWORK_LOGS) {
            val pinner = CertificatePinner.Builder()
                .add("api.fincore.com", "sha256/k2oTKiTGoQUfl+MYxW8KTJYupCWZZfZMWdwIpDXqJzs=")
                .add("api.fincore.com", "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18=")
                .build()
            builder.certificatePinner(pinner)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
