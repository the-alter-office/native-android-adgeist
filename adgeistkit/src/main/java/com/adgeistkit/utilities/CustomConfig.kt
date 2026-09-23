package com.adgeistkit.utilities

public class CustomConfig @JvmOverloads constructor(
    public val backendDomain: String? = null,
    public val packageOrBundleId: String? = null,
    public val adgeistAppId: String? = null,
    public val versioning: String? = null,
) {

    public class Builder {
        private var backendDomain: String? = null
        private var packageOrBundleId: String? = null
        private var adgeistAppId: String? = null
        private var versioning: String? = null

        public fun backendDomain(value: String?): Builder = apply { backendDomain = value }

        public fun packageOrBundleId(value: String?): Builder = apply { packageOrBundleId = value }

        public fun adgeistAppId(value: String?): Builder = apply { adgeistAppId = value }

        public fun versioning(value: String?): Builder = apply { versioning = value }

        public fun build(): CustomConfig =
            CustomConfig(backendDomain, packageOrBundleId, adgeistAppId, versioning)
    }
}