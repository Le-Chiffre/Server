package com.rimmer.yttrium.router

import com.rimmer.yttrium.Context
import com.rimmer.yttrium.Task
import com.rimmer.yttrium.serialize.Reader
import com.rimmer.yttrium.serialize.Writer
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.EventLoop

/** The supported http call methods. */
enum class HttpMethod {GET, POST, DELETE, PUT}

/**
 * Contains information about a callable route in this server.
 * @param name The route name as it will be displayed in logs, metrics, etc.
 * @param method The HTTP calling method for this route.
 * @param version The route version - this allows using equivalent routes with different parameters.
 * @param segments The route path as it will be called through HTTP.
 * @param typedSegments The path segments that will be bound the a parameter.
 * @param queries Any parameters that aren't provided through the path.
 * @param handler Takes request information for the route and executes it.
 */
class Route(
    val name: String,
    val method: HttpMethod,
    val version: Int,
    val segments: Array<PathSegment>,
    val typedSegments: Array<PathSegment>,
    val queries: Array<RouteQuery>,
    val writer: Writer<Any>?,
    val bodyQuery: Int?,
    var handler: (RouteContext, RouteListener) -> Unit = {c, r ->}
)

/** Interface that can be used by plugins to modify the signature of a route. */
interface RouteModifier {
    /** The parameter types that need to be provided to the route handler. */
    val parameterReaders: Array<Reader?>

    /**
     * Indicates that this plugin will provide this parameter to the route handler.
     * It will be ignored by the query parameter builder.
     */
    fun provideParameter(index: Int)

    /**
     * Adds to the calling url of this route.
     * @param segments A list of segments to add - this can contain both string and parameter segments.
     * @return The id of the created parameter for the first typed segment. This can be used to retrieve it in modifyCall.
     */
    fun addPath(segments: List<PathSegment>): Int

    /**
     * Adds an additional query parameter.
     * @return The id of the created query. This can be used to retrieve it in modifyCall.
     */
    fun addArg(name: String, reader: Reader, description: String = ""): Int

    /**
     * Adds an additional optional query parameter.
     * @return The id of the created query. This can be used to retrieve it in modifyCall.
     */
    fun addOptional(name: String, reader: Reader, default: Any? = null, description: String = ""): Int

    /** Returns the index of the first argument of the provided type, or null. */
    fun hasParameter(type: Class<*>): Int? {
        val i = parameterReaders.indexOfFirst { it?.target === type }
        return if(i == -1) null else i
    }

    /** If a parameter of the requested type exists, provide it and return its index. */
    fun provide(type: Class<*>): Int {
        val i = hasParameter(type) ?: throw IllegalArgumentException("No parameter of type $type exists.")
        provideParameter(i)
        return i
    }
}

/**
 * Defines a generic property applied on a route.
 * Properties can be used to send additional data to plugins.
 */
class RouteProperty(val name: String, val value: Any)

/**
 * Represents a query parameter within a route.
 * @param name The parameter name as it appears in the url.
 * @param hash The hash of the parameter name.
 * @param reader A reader for the query target type.
 * @param default If set, the parameter is optional and should be set to this if not provided.
 * @param description The provided description of this parameter.
 */
class RouteQuery(
    val name: String,
    val hash: Int,
    val reader: Reader,
    val optional: Boolean,
    val default: Any?,
    val description: String
)

/**
 * When sent to a route handler, this will be called with progress information about the route.
 * Can be used to collect metrics or display debug information.
 */
interface RouteListener {
    /**
     * This is called whenever a route call starts.
     * @return A listener id that will be sent to any followup calls for this route.
     */
    fun onStart(eventLoop: EventLoop, route: Route): Long

    /**
     * This is called whenever a route call succeeds.
     * @param route The context for this route, including the call id returned by onStart.
     * @param result The returned result of the route.
     */
    fun onSucceed(route: RouteContext, result: Any?)

    /**
     * This is called whenever a route call fails.
     * @param route The context for this route, including the call id returned by onStart.
     * @param reason The reason this call failed, if any.
     */
    fun onFail(route: RouteContext, reason: Throwable?)
}

/**
 * Context information that is sent to route handlers.
 * @param channel The channel this route is being executed for.
 * @param sourceIp The source ip this request originated from. This may be different from the channel ip.
 * @param eventLoop The current event loop.
 * @param route The route being executed.
 * @param pathParameters The path parameters that were sent to the route.
 * @param queryParameters The query parameters that were sent to the route.
 * @param id The metrics/call id for the current route.
 */
class RouteContext(
    val channel: ChannelHandlerContext,
    val sourceIp: String,
    eventLoop: EventLoop,
    val route: Route,
    val pathParameters: Array<Any?>,
    val queryParameters: Array<Any?>,
    id: Long
): Context(eventLoop, id) {
    fun <T> finish(v: T) = Task<T>().finish(v)
    fun finish() = Task<Unit>().finish(Unit)

    fun <T> fail(cause: Throwable) = Task<T>().fail(cause)
}
