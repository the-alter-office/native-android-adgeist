package com.adgeistkit.utilities

@RequiresOptIn(
    message = "Not implemented yet. Planned for a future release; calling it today has no effect.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class ExperimentalAdgeistApi

@RequiresOptIn(
    message = "Not part of the publisher API. Intended for development, debugging and framework " +
        "wrappers such as React Native; may change without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class AdgeistInternalApi

@RequiresOptIn(
    message = "For frameworks built on top of this SDK, such as React Native, that own the " +
        "layout, view hierarchy or lifecycle the AdView otherwise manages itself. Publishers " +
        "integrating the SDK directly never need it.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class AdgeistEmbedderApi
