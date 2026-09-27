package com.polsl.bemyeyes.navigation.dataBase

import com.polsl.bemyeyes.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
object RetrofitClient {

    private const val BASE_URL ="https://yhtllubktutvmjngxmzj.supabase.co/rest/v1/"
    // KLUCZ ANON -token JWT
    private const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
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