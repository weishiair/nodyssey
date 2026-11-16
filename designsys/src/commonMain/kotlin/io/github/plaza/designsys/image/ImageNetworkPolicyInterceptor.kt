package io.github.plaza.designsys.image

import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Applies the image data-usage preference at the last boundary before Coil can use the network.
 *
 * [imagesOnWifiOnly] is a flow rather than a boolean because the setting is observable and this is
 * consulted per request; where that flow comes from — which DataStore key, under which name — it is
 * deliberately not told, which is what lets its test hand it a `flowOf` and nothing else.
 *
 * Both app shells install this policy with their own settings and network status. Sharing it keeps
 * cached images and the explicit per-image opt-out working the same way on every platform.
 */
class ImageNetworkPolicyInterceptor(
    private val imagesOnWifiOnly: Flow<Boolean>,
    private val hasUnmeteredNetwork: suspend () -> Boolean,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val deferred =
            shouldDeferImage(
                request = chain.request,
                imagesOnWifiOnly = imagesOnWifiOnly.first(),
                hasUnmeteredNetwork = if (chain.request.isRemoteImage()) hasUnmeteredNetwork() else true,
            )
        val request = if (deferred) chain.request.withoutNetwork() else chain.request
        val result = chain.withRequest(request).proceed()
        /*
         * Re-label the failure as ours.
         *
         * With the network disabled, a cache miss surfaces as an ordinary transport error, and the
         * viewer says "图片加载失败" — which sends the user to debug a network that is working fine.
         * It cost a real debugging session (2026-07-28: a freshly posted image "failed" on a phone
         * whose only problem was this switch plus a VPN whose tunnel does not report NOT_METERED).
         * The app declined on purpose; it should say so — and offer to fetch it anyway.
         */
        return if (deferred && result is ErrorResult) {
            ErrorResult(
                image = result.image,
                request = result.request,
                throwable = ImagesDeferredException(),
            )
        } else {
            result
        }
    }
}

/** True when this request must not touch the network, i.e. the user has not asked for it by hand. */
internal fun shouldDeferImage(
    request: ImageRequest,
    imagesOnWifiOnly: Boolean,
    hasUnmeteredNetwork: Boolean,
): Boolean =
    imagesOnWifiOnly && !hasUnmeteredNetwork && !request.getExtra(AllowMeteredImage)

/**
 * Disables only network reads. Coil can still satisfy the request from memory or its disk cache, so
 * enabling the preference does not blank images the user has already loaded.
 */
internal fun ImageRequest.withoutNetwork(): ImageRequest =
    newBuilder().networkCachePolicy(CachePolicy.DISABLED).build()
