// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.mysql.agent.sql.Fixtures;
import org.devlive.grantforge.mysql.agent.sql.Policies;
import org.devlive.grantforge.mysql.agent.sql.Refusal;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxySessionTest
{
    @Test
    void removesCapabilitiesWhoseProtocolTheProxyCannotInspect() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            int unsupported = (1 << 5) | (1 << 11) | (1 << 25) | (1 << 26) | (1 << 27);
            connection.greet(ProtocolFixtures.CAPABILITIES | unsupported);
            ProtocolFixtures.Packet greeting = connection.readClient();
            ByteBuf payload = Unpooled.wrappedBuffer(greeting.payload());
            try {
                int capabilities = payload.getUnsignedShortLE(20) | payload.getUnsignedShortLE(25) << 16;
                assertThat(capabilities).isEqualTo(ProtocolFixtures.CAPABILITIES);
            }
            finally {
                payload.release();
            }
            connection.login(ProtocolFixtures.CAPABILITIES | unsupported);
            ProtocolFixtures.Packet response = connection.readServer();
            ByteBuf login = Unpooled.wrappedBuffer(response.payload());
            try {
                assertThat(login.getIntLE(0)).isEqualTo(ProtocolFixtures.CAPABILITIES);
                assertThat(response.sequence()).isEqualTo(1);
                assertThat(response.text()).contains("alice", "sales");
            }
            finally {
                login.release();
            }
        }
    }

    @Test
    void deniedQueriesWaitForEarlierServerResponsesAndNeverReachMySql() throws Exception
    {
        List<AccessEvent> events = new CopyOnWriteArrayList<>();
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), events::add, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.sendClient(0, 14);
            assertThat(connection.readServer().first()).isEqualTo(14);
            connection.query("SELECT id FROM orders");
            connection.client.setSoTimeout(200);
            assertThatThrownBy(connection::readClient).isInstanceOf(SocketTimeoutException.class);
            connection.client.setSoTimeout(5000);
            connection.sendServer(1, 0, 0, 0, 2, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
            ProtocolFixtures.Packet denial = connection.readClient();
            assertThat(denial.sequence()).isEqualTo(1);
            assertThat(denial.first()).isEqualTo(Packets.ERR);
            assertThat(denial.errorCode()).isEqualTo(Refusal.COLUMN_DENIED);
            assertThat(denial.text()).contains("42000", "GrantForge", "id");
            assertThat(events).hasSize(1);
            connection.server.setSoTimeout(200);
            assertThatThrownBy(connection::readServer).isInstanceOf(SocketTimeoutException.class);
        }
    }

    @Test
    void relaysAllowedQueriesAndRejectsCommandsThatBypassSqlChecks() throws Exception
    {
        Policies policies = new Fixtures.Policy().allow("sales", "orders", "id", List.of("alice"), "select").policies();
        Gate gate = new Gate(Fixtures.catalog(), () -> policies, event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.query("SELECT id FROM orders");
            ProtocolFixtures.Packet query = connection.readServer();
            assertThat(query.first()).isEqualTo(3);
            assertThat(query.text()).isEqualTo("\3SELECT id FROM orders");
            connection.sendServer(1, 0, 0, 0, 2, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
            connection.sendClient(0, 18);
            ProtocolFixtures.Packet refusal = connection.readClient();
            assertThat(refusal.first()).isEqualTo(Packets.ERR);
            assertThat(refusal.errorCode()).isEqualTo(Refusal.NOT_ALLOWED);
            assertThat(refusal.text()).contains("COM_BINLOG_DUMP");
        }
    }

    @Test
    void malformedLoginReturnsTheMySqlHandshakeErrorAndClosesBothConnections() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.greet(ProtocolFixtures.CAPABILITIES);
            connection.readClient();
            connection.sendClient(1, 0, 0, 0, 0);
            ProtocolFixtures.Packet error = connection.readClient();
            assertThat(error.sequence()).isEqualTo(2);
            assertThat(error.errorCode()).isEqualTo(1043);
            assertThat(error.text()).contains("08S01", "protocol 4.1");
            assertThat(connection.client.getInputStream().read()).isEqualTo(-1);
            assertThat(connection.server.getInputStream().read()).isEqualTo(-1);
        }
    }

    @Test
    void packetsBeforeTheGreetingAreReleasedAndCloseTheSession()
    {
        EmbeddedChannel client = new EmbeddedChannel();
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        ProxySession session = new ProxySession(client, new Backend("127.0.0.1", 3306, null, null, false, 2000), gate, new PasswordBridge());
        client.pipeline().addLast(session.clientHandler());
        ByteBuf frame = Unpooled.buffer(5).writeMediumLE(1).writeByte(0).writeByte(14);
        try {
            client.writeInbound(frame);
            assertThat(frame.refCnt()).isZero();
            assertThat(client.isOpen()).isFalse();
            session.close();
            assertThat((Object) client.readOutbound()).isNull();
        }
        finally {
            client.finishAndReleaseAll();
        }
    }

    @Test
    void refusesAGreetingWithoutTheProtocolItNeeds() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.greet(8);
            ProtocolFixtures.Packet error = connection.readClient();
            assertThat(error.sequence()).isEqualTo(0);
            assertThat(error.errorCode()).isEqualTo(1043);
            assertThat(error.text()).contains("08S01", "protocol or TLS");
            assertThat(connection.client.getInputStream().read()).isEqualTo(-1);
            assertThat(connection.server.getInputStream().read()).isEqualTo(-1);
        }
    }

    @Test
    void passesOnTheServersOwnRefusalOfTheConnection() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.sendServer(0, 0xFF, 21, 4, 'n', 'o');
            ProtocolFixtures.Packet error = connection.readClient();
            assertThat(error.sequence()).isEqualTo(0);
            assertThat(error.errorCode()).isEqualTo(1045);
            assertThat(connection.client.getInputStream().read()).isEqualTo(-1);
        }
    }

    @Test
    void takesTheDatabaseFromInitDbIntoTheChecksOfLaterStatements() throws Exception
    {
        Policies policies = new Fixtures.Policy().allow("hr", "salaries", "employee", List.of("alice"), "select").policies();
        Gate gate = new Gate(Fixtures.catalog(), () -> policies, event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.query("SELECT employee FROM salaries");
            assertThat(connection.readClient().first()).isEqualTo(Packets.ERR);
            connection.server.setSoTimeout(200);
            assertThatThrownBy(connection::readServer).isInstanceOf(SocketTimeoutException.class);
            connection.server.setSoTimeout(5000);
            connection.sendClient(0, 2, 'h', 'r');
            assertThat(connection.readServer().text()).isEqualTo("\2hr");
            connection.sendServer(1, 0, 0, 0, 2, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
            connection.query("SELECT employee FROM salaries");
            assertThat(connection.readServer().text()).isEqualTo("\3SELECT employee FROM salaries");
            connection.sendServer(1, 0, 0, 0, 2, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
        }
    }

    @Test
    void listsATablesColumnsOnlyWhenTheTableIsAllowed() throws Exception
    {
        List<AccessEvent> events = new CopyOnWriteArrayList<>();
        AtomicReference<Policies> current = new AtomicReference<>(new Fixtures.Policy()
                .allow("sales", "orders", null, List.of("alice"), "select").policies());
        Gate gate = new Gate(Fixtures.catalog(), current::get, events::add, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.sendClient(0, 4, 'o', 'r', 'd', 'e', 'r', 's', 0);
            assertThat(connection.readServer().text()).isEqualTo("\4orders\0");
            connection.sendServer(1, 3, 'i', 'd');
            assertThat(connection.readClient().first()).isEqualTo(3);
            connection.sendServer(2, 0xFE, 0, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.EOF);
            assertThat(events).hasSize(1);
            current.set(new Fixtures.Policy().disabled().policies());
            connection.sendClient(0, 4, 'o', 'r', 'd', 'e', 'r', 's', 0);
            assertThat(connection.readServer().text()).isEqualTo("\4orders\0");
            connection.sendServer(1, 0xFE, 0, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.EOF);
            current.set(new Fixtures.Policy().allow("hr", "salaries", null, List.of("alice"), "select").policies());
            connection.sendClient(0, 4, 'o', 'r', 'd', 'e', 'r', 's', 0);
            ProtocolFixtures.Packet denial = connection.readClient();
            assertThat(denial.errorCode()).isEqualTo(Refusal.TABLE_DENIED);
            assertThat(denial.text()).contains("42000", "orders");
            assertThat(events).hasSize(2);
            connection.server.setSoTimeout(200);
            assertThatThrownBy(connection::readServer).isInstanceOf(SocketTimeoutException.class);
        }
    }

    @Test
    void signsInAgainAsAnotherUser() throws Exception
    {
        Policies policies = new Fixtures.Policy().allow("sales", "orders", "id", List.of("alice"), "select")
                .allow("hr", "salaries", "employee", List.of("bob"), "select").policies();
        Gate gate = new Gate(Fixtures.catalog(), () -> policies, event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.query("SELECT id FROM orders");
            assertThat(connection.readServer().text()).isEqualTo("\3SELECT id FROM orders");
            connection.sendServer(1, 0, 0, 0, 2, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
            connection.sendClient(0, 0x11, 'b', 'o', 'b', 0, 0, 'h', 'r', 0);
            assertThat(connection.readServer().text()).isEqualTo("\21bob\0\0hr\0");
            connection.sendServer(1, 0, 0, 0, 2, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
            connection.query("SELECT id FROM orders");
            ProtocolFixtures.Packet denial = connection.readClient();
            assertThat(denial.errorCode()).isEqualTo(Refusal.TABLE_DENIED);
            connection.query("SELECT employee FROM salaries");
            assertThat(connection.readServer().text()).isEqualTo("\3SELECT employee FROM salaries");
        }
    }

    @Test
    void checksAPreparedStatementAgainWhenThePoliciesChanged() throws Exception
    {
        AtomicReference<Policies> current = new AtomicReference<>(new Fixtures.Policy()
                .allow("sales", "orders", "id", List.of("alice"), "select").policies());
        Gate gate = new Gate(Fixtures.catalog(), current::get, event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.sendClient(0, 0x16, 'S', 'E', 'L', 'E', 'C', 'T', ' ', 'i', 'd', ' ', 'F', 'R', 'O', 'M', ' ', 'o', 'r',
                    'd', 'e', 'r', 's');
            assertThat(connection.readServer().text()).isEqualTo("\26SELECT id FROM orders");
            connection.sendServer(1, 0, 1, 0, 0, 0, 0, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
            current.set(new Fixtures.Policy().deny("sales", "orders", "id", List.of("alice"), "select").policies());
            connection.sendClient(0, 0x17, 1, 0, 0, 0);
            ProtocolFixtures.Packet again = connection.readClient();
            assertThat(again.errorCode()).isEqualTo(1615);
            assertThat(again.text()).contains("policies changed");
            connection.sendClient(0, 0x17, 9, 0, 0, 0);
            assertThat(connection.readServer().first()).isEqualTo(0x17);
        }
    }

    @Test
    void forwardsTheCommandsThatCarryNoStatement() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            for (int command : new int[] {0x0e, 0x09, 0x0c, 0x07, 0x0d, 0x1a, 0x1b, 0x1f, 0x0a}) {
                connection.sendClient(0, command);
                assertThat(connection.readServer().first()).isEqualTo(command);
                connection.sendServer(1, 0, 0, 0, 2, 0, 0, 0);
                assertThat(connection.readClient().first()).isEqualTo(Packets.OK);
            }
            connection.sendClient(0, 0x1c);
            assertThat(connection.readServer().first()).isEqualTo(0x1c);
            connection.sendServer(1, 0xFE, 0, 0, 0, 0);
            assertThat(connection.readClient().first()).isEqualTo(Packets.EOF);
            connection.sendClient(0, 0x18, 1, 0, 0, 0, 0, 0, 0, 0, 'x');
            assertThat(connection.readServer().first()).isEqualTo(0x18);
            connection.sendClient(0, 0x19, 1, 0, 0, 0);
            assertThat(connection.readServer().first()).isEqualTo(0x19);
        }
    }

    @Test
    void closesBothConnectionsWhenTheClientQuits() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.sendClient(0, 1);
            assertThat(connection.readServer().first()).isEqualTo(1);
            assertThat(connection.client.getInputStream().read()).isEqualTo(-1);
            assertThat(connection.server.getInputStream().read()).isEqualTo(-1);
        }
    }

    @Test
    void closesTheConnectionOnAPacketThatContinuesNoCommand() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.sendClient(5, 1, 2, 3);
            assertThat(connection.client.getInputStream().read()).isEqualTo(-1);
        }
    }

    @Test
    void closesTheConnectionOnAnEmptyCommand() throws Exception
    {
        Gate gate = new Gate(Fixtures.catalog(), () -> Policies.of(null), event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.sendClient(0);
            assertThat(connection.client.getInputStream().read()).isEqualTo(-1);
        }
    }

    @Test
    void sendsTheRewrittenStatementWhenAColumnIsMasked() throws Exception
    {
        Policies policies = new Fixtures.Policy().allow("sales", "orders", "card", List.of("alice"), "select")
                .mask("sales", "orders", "card", "alice", "mask_hash", null).policies();
        Gate gate = new Gate(Fixtures.catalog(), () -> policies, event -> { }, Runnable::run);
        try (ProtocolFixtures.Connection connection = new ProtocolFixtures.Connection(gate)) {
            connection.signIn();
            connection.query("SELECT card FROM orders");
            ProtocolFixtures.Packet query = connection.readServer();
            assertThat(query.first()).isEqualTo(3);
            assertThat(query.text()).contains("SHA2", "card").doesNotContain("SELECT card FROM orders");
        }
    }
}
