// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * How long a decision takes against many HDFS path policies, with the prefix index and with a full scan. Not part of
 * the test run; start it with {@code main} from the test classpath, for example:
 * {@code java -cp "target/test-classes:target/classes:$(cat target/cp.txt)" org.devlive.grantforge.policy.engine.PolicyEngineBenchmark}
 * after {@code mvn -pl core/grantforge-policy-engine test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PolicyEngineBenchmark
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Param({"100", "10000"})
    private int policies;

    // Replaced in setUp(), which JMH calls before measuring.
    private PolicyEngine indexed = PolicyEngine.create(Models.HDFS, List.of(), Map.of());
    private PolicyEngine scanning = indexed;
    private AccessRequest[] requests = new AccessRequest[0];
    private int next;

    /** Builds policies for distinct project directories and requests for files in them. */
    @Setup(Level.Trial)
    public void setUp()
    {
        Random random = new Random(42);
        List<Policy> all = new ArrayList<>();
        for (int index = 0; index < policies; index++) {
            all.add(Policy.builder(index + 1).resource("path", ResourceSpec.of(List.of("/projects/p" + index), false, true))
                    .allow(PolicyItem.builder().users("user" + index % 50).groups("team" + index % 20).accessTypes("read").build())
                    .build());
        }
        indexed = PolicyEngine.create(Models.HDFS, all, Map.of());
        scanning = PolicyEngine.withoutIndex(Models.HDFS, all, Map.of());
        requests = new AccessRequest[1024];
        for (int index = 0; index < requests.length; index++) {
            int project = random.nextInt(policies);
            requests[index] = AccessRequest.builder("user" + random.nextInt(50), "read")
                    .resource("path", "/projects/p" + project + "/data/part-" + index + ".parquet").groups("team" + random.nextInt(20))
                    .time(NOW).build();
        }
    }

    private AccessRequest nextRequest()
    {
        next = (next + 1) & (requests.length - 1);
        return requests[next];
    }

    /**
     * Decides with the prefix index.
     *
     * @return the decision
     */
    @Benchmark
    public Decision indexed()
    {
        return indexed.evaluate(nextRequest());
    }

    /**
     * Decides by trying every policy.
     *
     * @return the decision
     */
    @Benchmark
    public Decision scanning()
    {
        return scanning.evaluate(nextRequest());
    }

    /**
     * Runs the benchmarks.
     *
     * @param args unused
     * @throws RunnerException if JMH fails
     */
    public static void main(String[] args)
            throws RunnerException
    {
        new Runner(new OptionsBuilder().include(PolicyEngineBenchmark.class.getSimpleName()).build()).run();
    }
}
