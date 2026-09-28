package com.adgeistkit.utilities

public class CustomConfig @JvmOverloads constructor(
    backendDomain: String? = null,
    packageOrBundleId: String? = null,
    adgeistAppId: String? = null,
    versioning: String? = null,
) {
    public val backendDomain: String? = backendDomain?.takeIf { it.isNotBlank() }
    public val packageOrBundleId: String? = packageOrBundleId?.takeIf { it.isNotBlank() }
    public val adgeistAppId: String? = adgeistAppId?.takeIf { it.isNotBlank() }
    public val versioning: String? = versioning?.takeIf { it.isNotBlank() }

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