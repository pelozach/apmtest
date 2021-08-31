package com.datadog.apmtest.server

import datadog.trace.api.GlobalTracer
import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        if (!GlobalTracer.get().addTraceInterceptor(GrpcSpanInterceptor())) {
            println("WARNING: GrpcSpanInterceptor failed to register")
        }
        Micronaut.build()
            .packages("com.datadog.apmtest")
            .mainClass(Application.javaClass)
            .start()
    }
}
