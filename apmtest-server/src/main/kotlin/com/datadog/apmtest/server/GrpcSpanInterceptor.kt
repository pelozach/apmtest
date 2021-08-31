package com.datadog.apmtest.server

import datadog.trace.api.interceptor.MutableSpan
import datadog.trace.api.interceptor.TraceInterceptor
import io.grpc.Status.CANCELLED

/**
* GrpcSpanInterceptor is a Datadog trace interceptor that adjusts traces
* that would be otherwise flagged as errors due to having a non-OK
* gRPC code.
*/
class GrpcSpanInterceptor : TraceInterceptor {
    // gRPC calls with a status of CANCELLED indicate benign client cancellation.
    private val cancelled = CANCELLED.getCode().name

    override fun onTraceComplete(trace: Collection<MutableSpan>): Collection<MutableSpan> {
        for (span in trace) {
            span.setTag(datadog.trace.api.DDTags.MANUAL_KEEP, true)
            println("GOT A SPAN")
            println(span)
            if (span.isError() && span.tags.get("status.code") == cancelled
            ) {
                println("FLIPPING SPAN TO NON ERROR")
                span.setError(false)
                println(span)
            }
        }

        return trace
    }

    override fun priority(): Int {
        // some high unique number so this interceptor is last
        return 10000
    }
}
