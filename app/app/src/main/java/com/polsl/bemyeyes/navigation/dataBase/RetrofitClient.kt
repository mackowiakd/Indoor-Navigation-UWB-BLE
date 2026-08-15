package com.polsl.bemyeyes.navigation.dataBase

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // todo - zrobic auto discovery adresu ip - nie hardcoded.. no chyba ze doclelowy serwer ma staly..
    private const val BASE_URL = "http://192.168.1.83:3000/"


    val apiService: TopologyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // Zamiana JSON na Kotlin (Gson)
            .build()
            .create(TopologyApiService::class.java)
    }
}