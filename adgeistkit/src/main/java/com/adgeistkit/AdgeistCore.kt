package com.adgeistkit
import android.content.Context

class AdgeistCore private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: AdgeistCore? = null

        fun initialize(context: Context): AdgeistCore {
            return instance ?: synchronized(this) {
                instance ?: AdgeistCore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val deviceIdentifier = DeviceIdentifier(context)
    private val networkUtils = NetworkUtils(context)

    fun getCreative(): FetchCreative {
        return FetchCreative(context, deviceIdentifier, networkUtils)
    }

    fun postCreativeAnalytics(): CreativeAnalytics {
        return CreativeAnalytics(context, deviceIdentifier, networkUtils)
    }
}