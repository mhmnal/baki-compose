package app.baki

/**
 * Hands back the fixes buffered by the platform tracker and clears the buffer.
 * Desktop has no tracker, so it returns empty.
 */
expect fun drainRouteBuffer(): List<RoutePoint>
