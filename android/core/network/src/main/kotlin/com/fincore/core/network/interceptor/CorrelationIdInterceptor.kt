package com.fincore.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

class CorrelationIdInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-Correlation-ID", UUID.randomUUID().toString())
            .build()
        return chain.proceed(request)
    }
}
