package com.adgeistkit.constants

internal object General {
    object Timing {
        const val STALLED_HANDSHAKE_GRACE_MS = 300L
        const val WEBVIEW_DESTROY_GRACE_MS = 600L
        const val MIN_VIEW_TIME_MS = 1000L
        const val CLICK_DEBOUNCE_MS = 1000L
        const val CONNECTION_WARM_MIN_INTERVAL_MS = 60_000L
        const val RETRY_BASE_BACKOFF_MS = 60_000L
        const val RETRY_MAX_BACKOFF_MS = 900_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }

    object Network {
        const val AD_PATH = "/v2/dsp/ad"
        const val IMPRESSION_PATH = "/v2/ssp/impression"

        const val UTC_TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    }

    object Storage {
        const val VIEW_MODEL_STORE_KEY = "com.adgeistkit.ads.AdViewModel"
    }

    object Bridge {
        const val SHELL_READY = "SHELL_READY"
        const val RENDER_STATUS = "RENDER_STATUS"
        const val RENDER_SUCCESS = "Success"

        const val VIDEO_PLAY = "PLAY"
        const val VIDEO_PAUSE = "PAUSE"
        const val VIDEO_ENDED = "ENDED"
    }

    object Analytics {
        const val ZEROED_AD_ID = "00000000-0000-0000-0000-000000000000"
        const val EVENT_TYPE_VIEW = "VIEW"
        const val EVENT_TYPE_CLICK = "CLICK"
    }
}
