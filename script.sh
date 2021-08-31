#!/bin/sh

foo() {
    grpcurl -proto proto/apmtest.proto -plaintext localhost:50051 datadog.apmtest.v1.ApmTest/CreateStream &
    child="$!"
    sleep 0.25
    kill -KILL "$child"
}


while true; do
  foo
done
