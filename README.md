# ApmTest

This project is intended to replicate an issue when using an interceptor to
adjust spans before being ingested into Datadog's APM.

This is a Kotlin project which spins up a Micronaut web server exposing
a GRPC API. 

* The RPC definition is in proto/apmtest.proto.
* The entrypoint is apmtest-server/src/main/kotlin/com/datadog/apmtest/server/Application.kt
* The endpoint and server logic is in 
apmtest-server/src/main/kotlin/com/datadog/apmtest/server/Endpoint.kt
* The interceptor is apmtest-server/src/main/kotlin/com/datadog/apmtest/server/GrpcSpanInterceptor.kt

## Problem Overview

This service exposes a streaming API via an RPC called `CreateStream`. The implementation of
this endpoint on the server (in Endpoint.kt) just sleeps. When the client cancels this stream,
a java.util.CancellationException is eventually raised and intercepted by Datadog's middleware,
here: https://github.com/DataDog/dd-trace-java/blob/master/dd-java-agent/instrumentation/grpc-1.5/src/main/java/datadog/trace/instrumentation/grpc/server/TracingServerInterceptor.java#L140

The problem is that this cancellation is benign and a status of CANCELLED should not be logged as an error in APM.

Originally, before dd-java-agent version 0.85.0, this exception was logged at this point to the trace as an error. This led us
down the path of creating GrpcSpanInterceptor to modify the traces before submission to the agent.

## Problem 1

The interceptor works nearly all of the time, but a small proportion of traces still end up flagged as errors.
I am able to reproduce this behavior locally running this application, after leaving the application
running with traffic flowing for some time (less than day, but it is a low error rate. The problem is more apparent
at high traffic volumes, as we see in production of 1-2%). 

This problem is reliably reproducible with this setup, albeit tedious. It is unclear to me why these traces are being flagged as errors and I have not yet
gotten any greater visibility into what is going wrong at the application or tracer level.

## Problem 2

dd-trace-java version 0.85.0 includes a fix to the TracingServerInterceptor that should remove the original need for our custom interceptor.
This fix manually ignores CancellationExceptions and prevents them from being logged to the trace- this is a great fix and we really appreciate the
thought there.

However, oddly enough- this does not prevent these exceptions from being raised to the trace! If we bump the agent to 0.85 in the dockerfile,
and comment out our interceptor in Application.kt, we will see all of these spans flagged as errors in STDOUT and in APM. The stack trace does show the correct
line number of TracingServerInterceptor, so it does appear to be using the new code, but this trace is still being flagged as an error.

## Running this service

Docker version 20.10.7, build f0df350

I have been starting the agent like this:
`docker run -e DD_HOSTNAME=zachlocal -e DD_APM_ENABLED=true -e DD_API_KEY=<API_KEY> -p 8126:8126/tcp gcr.io/datadoghq/agent:latest`

Build the image with `docker build -t apmtest .`

and start the service like:
`docker run -p 50051:50051 -e DD_TRACE_DEBUG=true -e DD_AGENT_HOST=host.docker.internal -e DD_TRACE_ANALYTICS_ENABLED=true -e DD_PROFILING_ENABLED=true -e DD_ENTITY_ID=zachtest -e DD_ENV=local -e DD_SERVICE=zachtest -e DD_TRACE_SAMPLE_RATE=1 apmtest`

I then run script.sh in a couple sessions and script2.sh, both of which create stream requests and terminate, script2 streams last a bit longer- perhaps irrelevant,
but all cases of this false error that I've seen have a minimum connection time of 5s.

These scripts use grpcurl: https://github.com/fullstorydev/grpcurl to make GRPC requests to the service.

## Final notes

This is an annoying and tedious problem, and we would really appreciate any additional thoughts or visibility into what may be going wrong here. 

The current state of this service is that the Dockerfile is using agent version 0.85.0, with the custom interceptor enabled.
This demonstrates both problems 1 and 2:
* These spans should not even have errors on them when they reach the interceptor, because of the fix in 0.85.0 to TracingServerInterceptor. You can
comment out the interceptor in Application.kt and observe that all spans have errored in APM
* The interceptor correctly flags most spans as non-errors, but a small volume of spans will appear as errors in APM.

If problem 2 were resolved, and we no longer needed to modify spans at all to handle this cancellation error, this would solve 95% of our problems.

It is still possible that we would like to modify other status codes in certaion situations (e.g. NOT_FOUND), in which case we would run into
problem 1 again. 

Thank you for the time assisting with this issue and reading these notes.

Stack from 0.85.0 tracer on a span that is still flagged as an error:
error.stack=java.util.concurrent.CancellationException: Cancellation received from client
	at kotlinx.coroutines.ExceptionsKt.CancellationException(Exceptions.kt:22)
	at kotlinx.coroutines.CoroutineScopeKt.cancel(CoroutineScope.kt:225)
	at kotlinx.coroutines.CoroutineScopeKt.cancel$default(CoroutineScope.kt:225)
	at io.grpc.kotlin.ServerCalls$serverCallListener$2.onCancel(ServerCalls.kt:263)
	at datadog.trace.instrumentation.grpc.server.TracingServerInterceptor$TracingServerCallListener.onCancel(TracingServerInterceptor.java:135)
	at io.grpc.internal.ServerCallImpl$ServerStreamListenerImpl.closedInternal(ServerCallImpl.java:353)
	at io.grpc.internal.ServerCallImpl$ServerStreamListenerImpl.closed(ServerCallImpl.java:341)
	at io.grpc.internal.ServerImpl$JumpToApplicationThreadServerStreamListener$1Closed.runInContext(ServerImpl.java:861)
	at io.grpc.internal.ContextRunnable.run(ContextRunnable.java:37)
	at io.grpc.internal.SerializingExecutor.run(SerializingExecutor.java:123)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1130)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:630)
	at java.base/java.lang.Thread.run(Thread.java:832)
