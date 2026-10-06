package com.adgeistkit.ads

import com.adgeistkit.constants.Messages

public enum class AdgeistEventType {
    AD_LOADED,
    AD_CLOSED,
    AD_CLICKED,
    AD_NO_FILL,
    AD_NETWORK_ERROR,
    AD_WARNING
}

public enum class AdgeistEventCode {
    AL1,
    AL2,
    AI1,
    AE1,
    AE2,
    AW1,
    AW2,
    AW3,
    AW4,
    AW5,
    AW6,
    AW7,
    AW8,
    AW9,
    AW10;

    public val type: AdgeistEventType
        get() = when (this) {
            AL1 -> AdgeistEventType.AD_LOADED
            AL2 -> AdgeistEventType.AD_CLOSED
            AI1 -> AdgeistEventType.AD_CLICKED
            AE1 -> AdgeistEventType.AD_NO_FILL
            AE2 -> AdgeistEventType.AD_NETWORK_ERROR
            AW1, AW2, AW3, AW4, AW5, AW6,
            AW7, AW8, AW9, AW10 -> AdgeistEventType.AD_WARNING
        }
}

public class AdgeistEventData internal constructor(
    public val reason: String
)

public class AdgeistEvent internal constructor(
    public val code: AdgeistEventCode,
    public val data: AdgeistEventData? = null
) {
    public val type: AdgeistEventType
        get() = code.type

    public val message: String
        get() = Messages.Listener.forCode(code)

    override fun toString(): String =
        if (data == null) "$code ($type): $message" else "$code ($type): $message - ${data.reason}"
}
