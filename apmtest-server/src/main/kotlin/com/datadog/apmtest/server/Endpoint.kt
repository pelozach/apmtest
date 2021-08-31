package com.datadog.apmtest.server

import com.datadog.apmtest.v1.CreateStreamRequest
import com.datadog.apmtest.v1.Message
import com.datadog.apmtest.v1.ApmTestGrpcKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Singleton
class ApmTest() : ApmTestGrpcKt.ApmTestCoroutineImplBase() {
    @ExperimentalCoroutinesApi
    override fun createStream(request: CreateStreamRequest): Flow<Message> {
        return channelFlow {
            /*launch(Dispatchers.IO) {
                while (true) {
                    delay(2000L)
                }
            }*/
            delay(2000L)
        }
    }
}
