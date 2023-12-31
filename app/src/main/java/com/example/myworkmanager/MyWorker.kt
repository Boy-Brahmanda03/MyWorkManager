package com.example.myworkmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.myworkmanager.response.Response
import com.loopj.android.http.AsyncHttpResponseHandler
import com.loopj.android.http.SyncHttpClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import cz.msebera.android.httpclient.Header
import java.text.DecimalFormat

class MyWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    private var resultStatus: Result? = null

    override fun doWork(): Result {
        val dataCity = inputData.getString(EXTRA_CITY)
        return getCurrentWeather(dataCity)
    }

    private fun getCurrentWeather(city: String?): Result {
        Log.d(TAG, "getCurrentWeather: Mulai.....")
        Looper.prepare()
        val client = SyncHttpClient()
        val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=${BuildConfig.APP_ID}"
        Log.d(TAG, "getCurrentWeather: $url")
        client.post(url, object : AsyncHttpResponseHandler() {
            override fun onSuccess(
                statusCode: Int,
                headers: Array<out Header>?,
                responseBody: ByteArray
            ) {
                val result = String(responseBody)
                Log.d(TAG, result)
                try {
                    val moshi = Moshi.Builder()
                        .addLast(KotlinJsonAdapterFactory())
                        .build()
                    val jsonAdapter = moshi.adapter(Response::class.java)
                    val response = jsonAdapter.fromJson(result)
                    response?.let {
                        val currentWeather = it.weatherList[0].main
                        val description = it.weatherList[0].description
                        val tempInKelvin = it.main.temperature

                        val tempInCelcius = tempInKelvin - 273
                        val temperature = DecimalFormat("##.##").format(tempInCelcius)
                        val title = "Current Weather in $city"
                        val message = "$currentWeather, $description with $temperature celcius"
                        showNotification(title, message)
                    }
                    Log.d(TAG, "onSuccess: Selesai....")
                    resultStatus = Result.success()
                } catch (e: Exception) {
                    showNotification("Get Current Weather Not Succes", e.message.toString())
                    Log.d(TAG, "onSuccess: Gagal....")
                    resultStatus = Result.failure()

                }
            }

            override fun onFailure(
                statusCode: Int,
                headers: Array<out Header>?,
                responseBody: ByteArray?,
                error: Throwable?
            ) {
                Log.d(TAG, "onFailure: Gagal...")
                //ketika gagal, maka job finished diset dengean parameter true, artinya per reschedule
                showNotification("Get Current Weather Failed", error?.message.toString())
                resultStatus = Result.failure()
            }
        })
        return resultStatus as Result
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_notifications_active_24)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            notification.setChannelId(CHANNEL_ID)
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }

    companion object {
        private val TAG = MyWorker::class.java.simpleName
        const val EXTRA_CITY = "city"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "channel_01"
        const val CHANNEL_NAME = "dicoding channel"
    }
}