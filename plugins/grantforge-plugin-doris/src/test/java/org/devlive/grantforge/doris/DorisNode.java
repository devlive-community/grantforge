// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

import com.github.dockerjava.api.model.Network.Ipam;
import org.jspecify.annotations.Nullable;
import org.mariadb.jdbc.Driver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * A Doris cluster of one release in containers: one frontend and one backend on a network of their own, at the fixed
 * addresses the official images are told of. Statements run as {@code root} through MariaDB Connector/J, or as a user
 * with its password.
 */
final class DorisNode
        implements AutoCloseable
{
    /** The frontends' MySQL protocol port. */
    static final int QUERY_PORT = 9030;

    private final Network network;
    private final GenericContainer<?> frontend;
    private final GenericContainer<?> backend;

    /**
     * Starts a cluster and waits until its backend is alive.
     *
     * @param version the release, such as {@code 4.1.5}
     * @throws SQLException when the frontend cannot be reached
     * @throws InterruptedException when interrupted while waiting
     */
    // The containers are closed with the node, or when it fails to start.
    @SuppressWarnings({"resource", "PMD.CloseResource"})
    DorisNode(String version) throws SQLException, InterruptedException
    {
        String subnet = "172.31." + (64 + new SecureRandom().nextInt(180));
        network = Network.builder().createNetworkCmdModifier(cmd -> cmd.withIpam(new Ipam().withConfig(new Ipam.Config().withSubnet(subnet
                + ".0/24")))).build();
        String frontendAddress = subnet + ".2";
        String backendAddress = subnet + ".3";
        String servers = "fe1:" + frontendAddress + ":9010";
        frontend = new GenericContainer<>(DockerImageName.parse("apache/doris:fe-" + version)).withNetwork(network)
                .withCreateContainerCmdModifier(cmd -> cmd.withIpv4Address(frontendAddress)).withEnv("FE_SERVERS", servers).withEnv("FE_ID", "1")
                .withExposedPorts(QUERY_PORT).waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofMinutes(5));
        backend = new GenericContainer<>(DockerImageName.parse("apache/doris:be-" + version)).withNetwork(network)
                .withCreateContainerCmdModifier(cmd -> cmd.withIpv4Address(backendAddress)).withEnv("FE_SERVERS", servers)
                .withEnv("BE_ADDR", backendAddress + ":9050").withEnv("SKIP_CHECK_ULIMIT", "true");
        try {
            frontend.start();
            backend.start();
            awaitBackend();
        }
        catch (RuntimeException | SQLException | InterruptedException failed) {
            close();
            throw failed;
        }
    }

    /** Waits until the frontend sees the backend alive, which it takes to answer any query. */
    private void awaitBackend() throws SQLException, InterruptedException
    {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        while (true) {
            try {
                for (List<String> row : rows("SHOW BACKENDS")) {
                    if (row.contains("true")) {
                        rows("SELECT 1");
                        return;
                    }
                }
            }
            catch (SQLException starting) {
                if (Instant.now().isAfter(deadline)) {
                    throw starting;
                }
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("the Doris backend did not come up:\n" + backend.getLogs());
            }
            Thread.sleep(1000);
        }
    }

    /**
     * Returns the frontend's MySQL protocol address as seen from the tests.
     *
     * @return such as {@code localhost:55012}
     */
    String host()
    {
        return frontend.getHost() + ":" + frontend.getMappedPort(QUERY_PORT);
    }

    /**
     * Opens a connection.
     *
     * @param user the user
     * @param password its password, or {@code null}
     * @return the connection
     * @throws SQLException when the sign-in is refused
     */
    Connection connect(String user, @Nullable String password) throws SQLException
    {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        if (password != null) {
            properties.setProperty("password", password);
        }
        properties.setProperty("connectTimeout", "10000");
        return requireNonNull(new Driver().connect("jdbc:mariadb://" + host() + "/", properties), "connection");
    }

    /**
     * Runs statements as {@code root}.
     *
     * @param statements the statements
     * @throws SQLException when one fails
     */
    void run(String... statements) throws SQLException
    {
        try (Connection connection = connect("root", null); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    /**
     * Runs a query as {@code root}.
     *
     * @param sql the query
     * @return the rows, each value as text
     * @throws SQLException when it fails
     */
    List<List<String>> rows(String sql) throws SQLException
    {
        try (Connection connection = connect("root", null)) {
            return rows(connection, sql);
        }
    }

    /**
     * Runs a query as {@code root} and returns its first value.
     *
     * @param sql the query
     * @return the value, empty without rows
     * @throws SQLException when it fails
     */
    String value(String sql) throws SQLException
    {
        List<List<String>> rows = rows(sql);
        return rows.isEmpty() || rows.get(0).isEmpty() ? "" : rows.get(0).get(0);
    }

    /**
     * What a statement run as a user did.
     *
     * @param ok whether it succeeded
     * @param rows its rows, each value as text
     * @param error the failure's message, empty when it succeeded
     */
    record Result(boolean ok, List<List<String>> rows, String error)
    {
        /** Whether Doris refused it for a missing privilege. */
        boolean denied()
        {
            return !ok && (error.contains("denied") || error.contains("Access denied") || error.contains("Permission denied"));
        }

        /** The values of the rows, rows separated by new lines and values by tabs. */
        String output()
        {
            List<String> lines = new ArrayList<>();
            rows.forEach(row -> lines.add(String.join("\t", row)));
            return String.join("\n", lines);
        }
    }

    /**
     * Runs a statement as a user.
     *
     * @param user the user
     * @param password its password
     * @param sql the statement
     * @return what it did
     */
    Result as(String user, String password, String sql)
    {
        try (Connection connection = connect(user, password)) {
            return new Result(true, rows(connection, sql), "");
        }
        catch (SQLException failed) {
            return new Result(false, List.of(), String.valueOf(failed.getMessage()));
        }
    }

    /**
     * Runs statements one after the other in one session of a user, as a session variable needs.
     *
     * @param user the user
     * @param password its password
     * @param statements the statements
     * @return what the last one did, or the first that failed
     */
    Result asSession(String user, String password, String... statements)
    {
        try (Connection connection = connect(user, password)) {
            List<List<String>> rows = List.of();
            for (String sql : statements) {
                rows = rows(connection, sql);
            }
            return new Result(true, rows, "");
        }
        catch (SQLException failed) {
            return new Result(false, List.of(), String.valueOf(failed.getMessage()));
        }
    }

    // A list per row and a value per column read.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static List<List<String>> rows(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement()) {
            if (!statement.execute(sql)) {
                return List.of();
            }
            List<List<String>> rows = new ArrayList<>();
            try (ResultSet result = statement.getResultSet()) {
                int columns = result.getMetaData().getColumnCount();
                while (result.next()) {
                    List<String> row = new ArrayList<>(columns);
                    for (int column = 1; column <= columns; column++) {
                        row.add(String.valueOf(result.getString(column)));
                    }
                    rows.add(row);
                }
            }
            return rows;
        }
    }

    @Override
    public void close()
    {
        backend.stop();
        frontend.stop();
        network.close();
    }
}
