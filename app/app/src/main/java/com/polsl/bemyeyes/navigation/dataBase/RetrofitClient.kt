package com.polsl.bemyeyes.navigation.dataBase

import com.polsl.bemyeyes.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
object RetrofitClient {

    private const val BASE_URL = BuildConfig.SUPABASE_ANON_KEY
    // KLUCZ ANON -token JWT
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlodGxsdWJrdHV0dm1qbmd4bXpqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAxMDU3NzEsImV4cCI6MjEwNTY4MTc3MX0.THw6bdT2GslmYr_kHmKj7AnD3bWZqqtgVkDEqOxgXdU"

    // Konfiguracja klienta HTTP, który automatycznie "dokleja" klucz do każdego zapytania
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .build()
            chain.proceed(request)
        }
        .build()


    val apiService: TopologyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) //client with key
            .addConverterFactory(GsonConverterFactory.create()) // Zamiana JSON na Kotlin (Gson)
            .build()
            .create(TopologyApiService::class.java)
    }
}