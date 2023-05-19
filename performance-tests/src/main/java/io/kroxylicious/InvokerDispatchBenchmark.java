/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious;

import java.util.concurrent.TimeUnit;

import org.apache.kafka.common.protocol.ApiKeys;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import io.kroxylicious.proxy.filter.FilterInvoker;
import io.kroxylicious.proxy.filter.FilterInvokers;
import io.kroxylicious.proxy.filter.KrpcFilter;
import io.kroxylicious.proxy.filter.SpecificFilterSwitchArrayInvoker;

// try hard to make shouldHandleXYZ to observe different receivers concrete types, saving unrolling to bias a specific call-site to a specific concrete type
@Fork(value = 2, jvmArgsAppend = "-XX:LoopUnrollLimit=1")
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class InvokerDispatchBenchmark {

    public enum Invoker {
        array {
            @Override
            FilterInvoker invokerWith(KrpcFilter filter) {
                return FilterInvokers.arrayInvoker(filter);
            }
        },
        specific {
            @Override
            FilterInvoker invokerWith(KrpcFilter filter) {
                return new SpecificFilterInvoker(filter);
            }
        },
        switching {
            @Override
            FilterInvoker invokerWith(KrpcFilter filter) {
                return new SpecificFilterSwitchArrayInvoker(filter);
            }
        };

        abstract FilterInvoker invokerWith(KrpcFilter filter);
    }

    @org.openjdk.jmh.annotations.State(Scope.Benchmark)
    public static class BenchState {
        FilterInvoker[] invokers;

        ApiKeys[] keys;

        @Param({ "array", "specific", "switching" })
        String invoker;

        @Setup
        public void init() {
            Invoker invokerType = Invoker.valueOf(invoker);
            invokers = new FilterInvoker[]{
                    invokerType.invokerWith(new TwoInterfaceFilter0()),
                    invokerType.invokerWith(new TwoInterfaceFilter1()),
                    invokerType.invokerWith(new FourInterfaceFilter0()),
                    invokerType.invokerWith(new FourInterfaceFilter1())
            };
            keys = new ApiKeys[]{ ApiKeys.PRODUCE, ApiKeys.API_VERSIONS, ApiKeys.FETCH };
        }
    }

    @Benchmark
    public void testDispatch(BenchState state, Blackhole blackhole) {
        invoke(blackhole, state.invokers, state.keys);
    }

    @Benchmark
    @Threads(4)
    public void test4ThreadsDispatch(BenchState state, Blackhole blackhole) {
        invoke(blackhole, state.invokers, state.keys);
    }

    private static void invoke(Blackhole blackhole, FilterInvoker[] filters, ApiKeys[] key) {
        for (ApiKeys apiKeys : key) {
            final short apiVersion = apiKeys.latestVersion();
            for (FilterInvoker invoker : filters) {
                blackhole.consume(invoker.shouldHandleRequest(apiKeys, apiVersion));
                blackhole.consume(invoker.shouldHandleResponse(apiKeys, apiVersion));
            }
        }
    }

}
