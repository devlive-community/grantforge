// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.GrantForge;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static java.util.Objects.requireNonNull;

/**
 * Runs the performance benchmarks (M12-01) and checks them against the thresholds: exits with 1 when one is exceeded
 * and {@code perf.enforce} is on. Settings come from {@code perf.*} system properties (see {@link PerfSettings}).
 *
 * <p>Metrics, each over {@code perf.samples} runs after {@code perf.warmup} unmeasured ones, one at a time:
 * <ul>
 *   <li>{@code authz.snapshot.hit}: an account's console snapshot when it is cached, as every API call asks for it;</li>
 *   <li>{@code authz.snapshot.build}: the console snapshot of an account seen for the first time;</li>
 *   <li>{@code authz.snapshot.app}: the snapshot in the seeded application with the large resource tree;</li>
 *   <li>{@code api.*}: console API calls over HTTP, as the administrator and as an account with one page;</li>
 *   <li>{@code jmh.derivation.*}: the JMH averages of preparing and running a grant derivation.</li>
 * </ul>
 */
public final class PerfBenchmark
{
    private static final String SETUP_TOKEN = "perf-benchmark-setup-token-0123456789";
    private static final String PASSWORD = "a long enough password for benchmarks";
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final int PAGE_SIZE = 20;
    // Lists are read near their start, as people page through them; deep offsets are what search is for.
    private static final int DEEPEST_PAGE = 500;

    private final PerfSettings settings;
    private final PerfReport report;
    private final Random random = new Random(20_261_004);
    private final PrintStream out;

    private PerfBenchmark(PerfSettings settings, PrintStream out)
    {
        this.settings = requireNonNull(settings, "settings");
        this.report = new PerfReport(settings);
        this.out = requireNonNull(out, "out");
    }

    /**
     * Runs the benchmarks.
     *
     * @param args not used; settings are system properties
     * @throws Exception if the server, the database or a benchmark fails
     */
    public static void main(String[] args) throws Exception
    {
        PerfSettings settings = PerfSettings.from(System.getProperties());
        List<String> violations = new PerfBenchmark(settings, System.out).run();
        if (!violations.isEmpty() && settings.enforce()) {
            System.exit(1);
        }
    }

    private List<String> run() throws Exception
    {
        out.printf(Locale.ROOT, "Benchmarking on %s: %,d accounts, %,d roles, %,d resources%n", settings.database(), settings.users(),
                settings.roles(), settings.resources());
        if (settings.jmh()) {
            derivation();
        }
        try (TestDatabase database = TestDatabase.start(settings.database())) {
            Path home = Files.createTempDirectory("grantforge-perf");
            try (ConfigurableApplicationContext context = GrantForge.start("--server.port=0", "--spring.main.banner-mode=off",
                    "--logging.level.root=WARN", "--GRANTFORGE_HOME=" + home, "--spring.datasource.url=" + database.url(),
                    "--spring.datasource.username=" + database.username(), "--spring.datasource.password=" + database.password(),
                    "--grantforge.setup.token=" + SETUP_TOKEN)) {
                system(context);
            }
        }
        List<String> violations = Thresholds.read(settings.thresholds()).violations(report.results());
        Path file = settings.report();
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        Files.writeString(file, report.json(violations), StandardCharsets.UTF_8);
        out.print(report.table());
        violations.forEach(violation -> out.println("THRESHOLD EXCEEDED: " + violation));
        out.println("Report: " + file.toAbsolutePath());
        return violations;
    }

    /** Runs the JMH benchmark of grant derivation in a forked JVM and adds its averages. */
    // One average per JMH result is what the loop builds.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void derivation() throws RunnerException
    {
        Collection<RunResult> results = new Runner(new OptionsBuilder().include(GrantDerivationBenchmark.class.getName())
                .param("resources", Integer.toString(settings.resources())).build()).run();
        for (RunResult result : results) {
            String label = result.getParams().getBenchmark();
            String name = label.substring(label.lastIndexOf('.') + 1);
            Latencies average = new Latencies();
            // The score is already in milliseconds per operation (the benchmark's output unit).
            average.add(Math.round(result.getPrimaryResult().getScore() * NANOS_PER_MILLI));
            report.add("jmh.derivation." + name, average);
        }
    }

    private void system(ConfigurableApplicationContext context) throws IOException, InterruptedException
    {
        URI base = URI.create("http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port"));
        ConsoleClient admin = new ConsoleClient(base);
        admin.get("/api/v1/bootstrap");
        admin.post("/api/v1/setup", "{\"token\": " + ConsoleClient.json(SETUP_TOKEN) + ", \"username\": \"admin\", \"password\": "
                + ConsoleClient.json(PASSWORD) + "}");
        admin.signIn("admin", PASSWORD);
        long tenant = context.getBean(TenantRepository.class).findAll().stream().filter(Tenant::isPlatform).findFirst()
                .map(Tenant::requireId).orElseThrow(() -> new IllegalStateException("setup created no platform tenant"));
        Seeder seeder = new Seeder(settings, context, tenant);

        long started = System.nanoTime();
        Seeder.Seeded seeded = seeder.seed(seeder.accountId("admin"), PASSWORD);
        report.note("seed.seconds", (System.nanoTime() - started) / NANOS_PER_MILLI / 1000);
        out.printf(Locale.ROOT, "Seeded in %.1f s%n", (System.nanoTime() - started) / NANOS_PER_MILLI / 1000);

        snapshots(context.getBean(AuthorizationEvaluator.class), tenant, seeded);
        apis(admin, base);
    }

    /** Snapshots: cached ones of a few accounts, then new ones of accounts not asked about before. */
    private void snapshots(AuthorizationEvaluator evaluator, long tenant, Seeder.Seeded seeded)
    {
        long[] accounts = seeded.accounts();
        shuffle(accounts);
        int warm = Math.min(100, accounts.length);
        // Every account the hit runs ask about is cached first, however short the warmup.
        for (int index = 0; index < warm; index++) {
            long account = accounts[index];
            TenantContext.callInTenant(tenant, () -> evaluator.snapshot(account));
        }
        report.add("authz.snapshot.hit", measure(index -> TenantContext.callInTenant(tenant, () -> evaluator.snapshot(accounts[index % warm]))));
        // Every run below asks about an account no run asked about before, so nothing comes from the cache.
        AtomicInteger next = new AtomicInteger(warm);
        int cold = settings.warmup() + settings.samples();
        if (warm + 2 * cold > accounts.length) {
            throw new IllegalStateException("seed more accounts than " + (warm + 2 * cold) + " for the cold snapshots");
        }
        report.add("authz.snapshot.build", measure(index -> TenantContext.callInTenant(tenant, () -> evaluator.snapshot(accounts[next.getAndIncrement()]))));
        report.add("authz.snapshot.app", measure(index -> TenantContext.callInTenant(tenant,
                () -> evaluator.snapshot(accounts[next.getAndIncrement()], seeded.applicationId()))));
    }

    /** Console API calls over HTTP. */
    private void apis(ConsoleClient admin, URI base) throws IOException, InterruptedException
    {
        int userPages = Math.min(DEEPEST_PAGE, settings.users() / PAGE_SIZE);
        report.add("api.users.page", measureCalls(index -> admin.get("/api/v1/users?page=" + (1 + random.nextInt(userPages)) + "&size=" + PAGE_SIZE)));
        report.add("api.users.search", measureCalls(index -> admin.get(String.format(Locale.ROOT, "/api/v1/users?q=perf-user-%07d&size=%d",
                random.nextInt(settings.users()), PAGE_SIZE))));
        int groupPages = Math.max(1, Math.max(10, settings.users() / 1000) / PAGE_SIZE);
        report.add("api.groups.page", measureCalls(index -> admin.get("/api/v1/groups?page=" + (1 + random.nextInt(groupPages)) + "&size=" + PAGE_SIZE)));
        report.add("api.roles.list", measureCalls(index -> admin.get("/api/v1/roles")));
        ConsoleClient member = new ConsoleClient(base);
        member.signIn(Seeder.MEMBER, PASSWORD);
        report.add("api.me.authorization", measureCalls(index -> member.get("/api/v1/me/authorization")));
        report.add("api.member.groups", measureCalls(index -> member.get("/api/v1/groups?page=1&size=" + PAGE_SIZE)));
    }

    private Latencies measure(IntConsumer run)
    {
        for (int index = 0; index < settings.warmup(); index++) {
            run.accept(index);
        }
        Latencies latencies = new Latencies();
        for (int index = 0; index < settings.samples(); index++) {
            long start = System.nanoTime();
            run.accept(index);
            latencies.add(System.nanoTime() - start);
        }
        return latencies;
    }

    private Latencies measureCalls(Call call) throws IOException, InterruptedException
    {
        for (int index = 0; index < settings.warmup(); index++) {
            call.run(index);
        }
        Latencies latencies = new Latencies();
        for (int index = 0; index < settings.samples(); index++) {
            long start = System.nanoTime();
            call.run(index);
            latencies.add(System.nanoTime() - start);
        }
        return latencies;
    }

    private void shuffle(long... values)
    {
        for (int index = values.length - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            long swapped = values[index];
            values[index] = values[other];
            values[other] = swapped;
        }
    }

    /** One API call. */
    @FunctionalInterface
    private interface Call
    {
        /**
         * Makes the call.
         *
         * @param index which run it is
         * @throws IOException if the server cannot be reached or answers with an error
         * @throws InterruptedException if interrupted
         */
        void run(int index) throws IOException, InterruptedException;
    }
}
