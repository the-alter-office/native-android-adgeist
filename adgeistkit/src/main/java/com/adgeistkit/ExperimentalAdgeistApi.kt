package com.adgeistkit

/**
 * Marks a public API that ships but is not finished yet, and is reserved for a future release.
 */
@RequiresOptIn(
    message = "Not implemented yet. Planned for a future release; calling it today has no effect.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
public annotation class ExperimentalAdgeistApi
