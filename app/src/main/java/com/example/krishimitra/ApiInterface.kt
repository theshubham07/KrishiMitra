package com.example.krishimitra

import com.example.krishimitra.data.WeatherApp
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query


interface ApiInterface {
    @GET("weather")
    fun getWeatherData(
        @Query("lat") lat: String,
        @Query("lon") lon: String,
        @Query("appid") appid: String,
        @Query("units") units: String
    ): Call<WeatherApp>
}