package app.baki

/** The tracker owns the buffer; the repo drains it when a shift ends. */
actual fun drainRouteBuffer(): List<RoutePoint> = DistanceTracker.drainBuffer()
