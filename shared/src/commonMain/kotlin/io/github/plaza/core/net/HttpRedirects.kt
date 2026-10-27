package io.github.plaza.core.net

/** Match browser redirect methods; never send a password/body or authorization to another origin. */
internal fun redirectRequest(request: HttpRequest, target: String, status: Int): HttpRequest? {
    val source = WebUrl.parse(request.url) ?: return null
    val destination = WebUrl.parse(target) ?: return null
    if (destination.scheme != "https" || destination.host == null || destination.userInfo != null) return null
    val sameOrigin = source.host.equals(destination.host, true) && (source.port.takeIf { it >= 0 } ?: 443) == (destination.port.takeIf { it >= 0 } ?: 443) && source.scheme == destination.scheme
    val becomesGet = (status == 303 && request.method != "HEAD") || (status in setOf(301, 302) && request.method == "POST")
    if (!sameOrigin && !becomesGet && request.body != null) return null
    val stripped = buildSet {
        add("cookie")
        if (!sameOrigin) addAll(listOf("authorization", "proxy-authorization", "referer", "origin"))
        if (becomesGet) addAll(listOf("content-type", "content-length", "transfer-encoding"))
    }
    return request.copy(
        url = target,
        method = if (becomesGet) "GET" else request.method,
        headers = request.headers.filterKeys { it.lowercase() !in stripped },
        body = if (becomesGet) null else request.body,
    )
}
