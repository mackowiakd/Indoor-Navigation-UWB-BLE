package com.polsl.bemyeyes.navigation.dataBase

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // ⚠️ TUTAJ WPISZ ADRES IP SWOJEGO LAPTOPA ⚠️
    // Pamiętaj o ukośniku na samym końcu!
    private const val BASE_URL = "http://192.168.1.X:3000/"

    val apiService: TopologyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // Zamiana JSON na Kotlin (Gson)
            .build()
            .create(TopologyApiService::class.java)
    }
}