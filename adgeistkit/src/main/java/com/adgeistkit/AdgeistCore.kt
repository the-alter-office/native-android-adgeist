package com.adgeistkit

import android.content.Context

class AdgeistCore private constructor(
    private val context: Context,
    private val domain: String
) {
    companion object {
        @Volatile private var instance: AdgeistCore? = null

        private const val DEFAULT_DOMAIN = "bg-services-qa-api.adgeist.ai"

        fun initialize(context: Context, customDomain: String? = null): AdgeistCore {
            return instance ?: synchronized(this) {
                instance ?: AdgeistCore(
                    context.applicationContext,
                    customDomain ?: DEFAULT_DOMAIN
                ).also { instance = it }
            }
        }

        fun getInstance(): AdgeistCore {
            return instance ?: throw IllegalStateException("AdgeistCore is not initialized")
        }
    }

    private val deviceIdentifier = DeviceIdentifier(context)
    private val networkUtils = NetworkUtils(context)

    fun getCreative(): FetchCreative {
        return FetchCreative(context, deviceIdentifier, networkUtils, domain)
    }

    fun postCreativeAnalytics(): CreativeAnalytics {
        return CreativeAnalytics(context, deviceIdentifier, networkUtils, domain)
    }
}
