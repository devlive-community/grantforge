// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.apache.shardingsphere.database.protocol.mysql.constant.MySQLCapabilityFlag;
import org.apache.shardingsphere.database.protocol.mysql.packet.command.MySQLCommandPacketType;
import org.apache.shardingsphere.database.protocol.mysql.packet.handshake.MySQLAuthMoreDataPacket;
import org.apache.shardingsphere.database.protocol.mysql.packet.handshake.MySQLAuthSwitchRequestPacket;
import org.apache.shardingsphere.database.protocol.mysql.packet.handshake.MySQLHandshakePacket;
import org.apache.shardingsphere.database.protocol.mysql.packet.handshake.MySQLHandshakeResponse41Packet;
import org.apache.shardingsphere.database.protocol.mysql.payload.MySQLPacketPayload;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.mysql.agent.sql.Policies;
import org.devlive.grantforge.mysql.agent.sql.QueryGuard;
import org.devlive.grantforge.mysql.agent.sql.Refusal;
import org.devlive.grantforge.mysql.agent.sql.SqlAnalyzer;
import org.devlive.grantforge.mysql.agent.sql.StatementPlan;
import org.devlive.grantforge.mysql.agent.sql.Verdict;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * One client's connection through the proxy and the proxy's connection to the server for it.
 *
 * <p>The sign-in passes through: the server's greeting goes to the client and the client's answers to the server, so
 * the server checks the password and the proxy learns the user it accepted. On the way the proxy takes out what it
 * cannot follow (compression, query attributes, optional metadata) and puts in its own TLS on either side. Once the
 * server accepts the user, every statement (COM_QUERY, COM_STMT_PREPARE) is checked and maybe rewritten before it goes
 * on; a refused one is answered by the proxy in its turn. Everything the server sends reaches the client unchanged.
 * All of it runs on the client channel's event loop; checks run on the gate's executor.
 */
// What a session does not have yet or any more (a database, a packet being gathered) is null by design. A write that
// fails leaves a broken channel, whose closing ends the session: the futures of writes need no checking.
@SuppressWarnings({"PMD.NullAssignment", "FutureReturnValueIgnored"})
final class ProxySession
{
    static final Logger LOG = Logger.getLogger(ProxySession.class.getName());

    private static final int CONNECT_WITH_DB = MySQLCapabilityFlag.CLIENT_CONNECT_WITH_DB.getValue();
    private static final int COMPRESS = MySQLCapabilityFlag.CLIENT_COMPRESS.getValue();
    private static final int PROTOCOL_41 = MySQLCapabilityFlag.CLIENT_PROTOCOL_41.getValue();
    private static final int SSL = MySQLCapabilityFlag.CLIENT_SSL.getValue();
    private static final int SECURE_CONNECTION = MySQLCapabilityFlag.CLIENT_SECURE_CONNECTION.getValue();
    private static final int DEPRECATE_EOF = MySQLCapabilityFlag.CLIENT_DEPRECATE_EOF.getValue();
    /** CLIENT_OPTIONAL_RESULTSET_METADATA, CLIENT_ZSTD_COMPRESSION_ALGORITHM and CLIENT_QUERY_ATTRIBUTES of MySQL 8. */
    private static final int OPTIONAL_METADATA = 1 << 25;
    private static final int ZSTD = 1 << 26;
    private static final int QUERY_ATTRIBUTES = 1 << 27;
    /** What the proxy cannot follow: packets it could not read or statements it would not see whole. */
    private static final int UNFOLLOWED = COMPRESS | ZSTD | QUERY_ATTRIBUTES | OPTIONAL_METADATA;

    /** ER_NEED_REPREPARE: a prepared statement that must be prepared again. */
    private static final int NEED_REPREPARE = 1615;
    /** ER_SECURE_TRANSPORT_REQUIRED. */
    private static final int SECURE_TRANSPORT_REQUIRED = 3159;
    /** ER_HANDSHAKE_ERROR. */
    private static final int HANDSHAKE_ERROR = 1043;
    /** ER_ACCESS_DENIED_ERROR. */
    private static final int ACCESS_DENIED = 1045;
    /** caching_sha2_password's packets: more data, the request for full authentication and the request for the key. */
    private static final int AUTH_MORE_DATA = 0x01;
    private static final int FULL_AUTH = 0x04;
    private static final byte KEY_REQUEST = 0x02;

    private enum Phase
    {
        GREETING, LOGIN, SIGN_IN, COMMAND, CLOSED
    }

    /** Where carrying a password across sides of different TLS stands; see {@link PasswordBridge}. */
    private enum Bridging
    {
        /** Nothing to carry. */
        NONE,
        /** A plain client is about to ask for the server's key, which the proxy answers with its own. */
        KEY_REQUEST,
        /** The plain client's password, encrypted with the proxy's key, comes next. */
        ENCRYPTED,
        /** The TLS client's password comes next, in clear. */
        CLEAR,
        /** The plain server's key comes next, to encrypt the password with. */
        SERVER_KEY
    }

    /** A statement the client prepared, as checked. */
    private record Prepared(String sql, @Nullable String sent, @Nullable String database, long version)
    {
    }

    /** The result of checking a text off the event loop. */
    private record Outcome(@Nullable List<StatementPlan> plans, @Nullable Verdict verdict, @Nullable Refusal refusal, long version)
    {
    }

    private final Backend settings;
    private final Gate gate;
    private final PasswordBridge bridge;
    private final SqlAnalyzer analyzer;
    private final QueryGuard guard;
    private final Channel client;
    private final @Nullable String clientIp;
    private final Deque<ByteBuf> waiting = new ArrayDeque<>();
    private final Map<Long, Prepared> prepared = new HashMap<>();
    private @Nullable Channel backend;
    private Phase phase = Phase.GREETING;
    private int clientShift;
    private int backendShift;
    private boolean clientTls;
    private int serverCapabilities;
    private int clientCapabilities;
    private String user = "";
    private @Nullable String database;
    private Charset charset = StandardCharsets.UTF_8;
    private Charset signInCharset = StandardCharsets.UTF_8;
    private Responses responses = new Responses(false);
    private boolean busy;
    private @Nullable CompositeByteBuf gathering;
    private int gatheredSequence;
    private long lastPrepared = -1;
    private boolean closed;
    private byte[] scramble = new byte[0];
    private Bridging bridging = Bridging.NONE;
    private byte[] password = new byte[0];

    ProxySession(Channel client, Backend settings, Gate gate, PasswordBridge bridge)
    {
        this.client = client;
        this.settings = settings;
        this.gate = gate;
        this.bridge = bridge;
        this.analyzer = gate.analyzer();
        this.guard = gate.guard();
        this.clientIp = client.remoteAddress() instanceof InetSocketAddress address && address.getAddress() != null
                ? address.getAddress().getHostAddress() : null;
    }

    /** The handler of the client's channel. */
    ChannelInboundHandlerAdapter clientHandler()
    {
        return new ChannelInboundHandlerAdapter()
        {
            @Override
            public void channelActive(ChannelHandlerContext ctx)
            {
                connect();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object message)
            {
                fromClient((ByteBuf) message);
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx)
            {
                Channel server = backend;
                if (server != null) {
                    server.config().setAutoRead(client.isWritable());
                }
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx)
            {
                close();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            {
                // A connection that drops is ordinary; anything else is the proxy's to explain.
                LOG.log(cause instanceof IOException ? Level.FINE : Level.WARNING, "The client connection of " + user + " failed", cause);
                close();
            }
        };
    }

    private void connect()
    {
        client.config().setAutoRead(false);
        new Bootstrap().group(client.eventLoop()).channel(client.getClass())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, settings.connectTimeoutMillis())
                .handler(new ChannelInitializer<>()
                {
                    @Override
                    protected void initChannel(Channel channel)
                    {
                        channel.pipeline().addLast("framer", Packets.framer()).addLast("handler", backendHandler());
                    }
                })
                .connect(settings.host(), settings.port()).addListener((ChannelFutureListener) connected -> {
                    if (!connected.isSuccess()) {
                        LOG.log(Level.WARNING, "The proxy cannot reach MySQL at " + settings.host() + ":" + settings.port(), connected.cause());
                        fail(0, 2003, "HY000", "GrantForge cannot reach the MySQL server behind the proxy");
                        return;
                    }
                    backend = connected.channel();
                    if (closed) {
                        connected.channel().close();
                    }
                });
    }

    private ChannelInboundHandlerAdapter backendHandler()
    {
        return new ChannelInboundHandlerAdapter()
        {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object message)
            {
                fromBackend((ByteBuf) message);
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx)
            {
                client.config().setAutoRead(!busy && ctx.channel().isWritable() && phase != Phase.GREETING);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx)
            {
                // What the server sent before it went has been passed on; the client follows it out.
                client.writeAndFlush(client.alloc().buffer(0)).addListener(ChannelFutureListener.CLOSE);
                shutdown();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            {
                LOG.log(cause instanceof IOException ? Level.FINE : Level.WARNING, "The server connection for " + user + " failed", cause);
                close();
            }
        };
    }

    // ---- what the server sends ----------------------------------------------------------------------------------

    private void fromBackend(ByteBuf frame)
    {
        switch (phase) {
            case GREETING -> greeting(frame);
            case SIGN_IN -> signedIn(frame);
            case COMMAND -> relay(frame);
            default -> {
                frame.release();
                close();
            }
        }
    }

    /** Passes the server's greeting on with only the capabilities the proxy can follow, and TLS if the proxy offers it. */
    private void greeting(ByteBuf frame)
    {
        if (Packets.first(frame) == Packets.ERR) {
            client.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        ByteBuf payload = Packets.payload(frame);
        int lower;
        try {
            MySQLHandshakePacket handshake = new MySQLHandshakePacket(new MySQLPacketPayload(payload.duplicate(), StandardCharsets.UTF_8));
            // Protocol version, server version, connection id, 8 bytes of scramble and a filler come first.
            lower = 1 + handshake.getServerVersion().getBytes(StandardCharsets.UTF_8).length + 1 + 4 + 8 + 1;
            serverCapabilities = payload.getUnsignedShortLE(lower) | payload.getUnsignedShortLE(lower + 5) << 16;
            scramble = handshake.getAuthPluginData().getAuthenticationPluginData();
            if ((serverCapabilities & 0xFFFF) != handshake.getCapabilityFlagsLower()) {
                throw new IllegalStateException("the greeting's capabilities are not where they belong");
            }
        }
        catch (RuntimeException unreadable) {
            frame.release();
            LOG.log(Level.WARNING, "The MySQL server's greeting cannot be read", unreadable);
            fail(0, HANDSHAKE_ERROR, "08S01", "GrantForge cannot read the MySQL server's greeting");
            return;
        }
        if ((serverCapabilities & PROTOCOL_41) == 0 || (settings.backendTls() != null && (serverCapabilities & SSL) == 0)) {
            frame.release();
            fail(0, HANDSHAKE_ERROR, "08S01", "the MySQL server behind GrantForge does not offer the protocol or TLS the proxy needs");
            return;
        }
        int offered = (serverCapabilities & ~UNFOLLOWED & ~SSL) | (settings.clientTls() != null ? SSL : 0);
        payload.setShortLE(lower, offered & 0xFFFF);
        payload.setShortLE(lower + 5, offered >>> 16);
        phase = Phase.LOGIN;
        client.writeAndFlush(frame);
        client.config().setAutoRead(true);
    }

    /** Relays the sign-in until the server accepts or refuses it. */
    private void signedIn(ByteBuf frame)
    {
        int first = Packets.first(frame);
        ByteBuf payload = Packets.payload(frame);
        if (bridging == Bridging.SERVER_KEY && first == AUTH_MORE_DATA) {
            int sequence = Packets.sequence(frame);
            byte[] key = new MySQLAuthMoreDataPacket(new MySQLPacketPayload(payload.duplicate(), StandardCharsets.US_ASCII)).getPluginData();
            frame.release();
            sendPassword(key, sequence + 1);
            return;
        }
        if (first == Packets.EOF) {
            // An auth switch brings a new scramble.
            try {
                scramble = new MySQLAuthSwitchRequestPacket(new MySQLPacketPayload(payload.duplicate(), StandardCharsets.UTF_8))
                        .getAuthPluginData().getAuthenticationPluginData();
            }
            catch (RuntimeException unreadable) {
                LOG.log(Level.FINE, "An auth switch request cannot be read", unreadable);
            }
        }
        if (first == AUTH_MORE_DATA && payload.readableBytes() == 2 && payload.getUnsignedByte(payload.readerIndex() + 1) == FULL_AUTH
                && clientTls != (settings.backendTls() != null)) {
            bridging = clientTls ? Bridging.CLEAR : Bridging.KEY_REQUEST;
        }
        Packets.sequence(frame, Packets.sequence(frame) - backendShift + clientShift);
        if (first == Packets.OK) {
            phase = Phase.COMMAND;
            client.writeAndFlush(frame);
            drain();
        }
        else if (first == Packets.ERR) {
            client.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
        }
        else {
            client.writeAndFlush(frame);
        }
    }

    private void relay(ByteBuf frame)
    {
        List<ByteBuf> answers = responses.observe(frame);
        client.write(frame);
        answers.forEach(client::write);
        client.flush();
    }

    // ---- what the client sends ----------------------------------------------------------------------------------

    private void fromClient(ByteBuf frame)
    {
        switch (phase) {
            case LOGIN -> login(frame);
            case SIGN_IN -> signingIn(frame);
            case COMMAND -> {
                waiting.addLast(frame);
                drain();
            }
            default -> {
                frame.release();
                close();
            }
        }
    }

    /** Reads who signs in from the client's handshake response and passes it on, through TLS when asked. */
    private void login(ByteBuf frame)
    {
        ByteBuf payload = Packets.payload(frame);
        int capabilities = payload.readableBytes() >= 4 ? payload.getIntLE(payload.readerIndex()) : 0;
        if ((capabilities & SSL) != 0 && Packets.length(frame) == 32) {
            frame.release();
            SslContext tls = settings.clientTls();
            if (tls == null || clientTls) {
                close();
                return;
            }
            startClientTls(tls);
            return;
        }
        int sequence = Packets.sequence(frame);
        if (settings.clientTlsRequired() && !clientTls) {
            frame.release();
            fail(sequence + 1, SECURE_TRANSPORT_REQUIRED, "HY000", "Connections to the GrantForge proxy must use TLS");
            return;
        }
        if ((capabilities & PROTOCOL_41) == 0) {
            frame.release();
            fail(sequence + 1, HANDSHAKE_ERROR, "08S01", "GrantForge needs a client of MySQL protocol 4.1 or later");
            return;
        }
        try {
            MySQLHandshakeResponse41Packet response = new MySQLHandshakeResponse41Packet(new MySQLPacketPayload(payload.duplicate(),
                    StandardCharsets.UTF_8));
            user = response.getUsername();
            String chosen = (capabilities & CONNECT_WITH_DB) != 0 ? response.getDatabase() : null;
            database = chosen == null || chosen.isEmpty() ? null : fold(chosen);
            signInCharset = Charsets.byCollation(response.getCharacterSet());
            charset = signInCharset;
        }
        catch (RuntimeException unreadable) {
            frame.release();
            fail(sequence + 1, HANDSHAKE_ERROR, "08S01", "GrantForge cannot read the client's handshake response");
            return;
        }
        clientCapabilities = (capabilities & ~UNFOLLOWED & ~SSL) | (settings.backendTls() != null ? SSL : 0);
        payload.setIntLE(payload.readerIndex(), clientCapabilities);
        responses = new Responses((clientCapabilities & serverCapabilities & DEPRECATE_EOF) != 0);
        phase = Phase.SIGN_IN;
        SslContext tls = settings.backendTls();
        Channel server = backend;
        if (server == null) {
            frame.release();
            close();
            return;
        }
        if (tls == null) {
            toBackend(frame, sequence - clientShift);
            return;
        }
        // The first 32 bytes of the response are the SSL request; after TLS, the response follows with the next id.
        ByteBuf request = server.alloc().buffer(Packets.HEADER + 32).writeMediumLE(32).writeByte(1)
                .writeBytes(payload, payload.readerIndex(), 32);
        server.writeAndFlush(request);
        backendShift = 1;
        SslHandler handler = tls.newHandler(server.alloc(), settings.host(), settings.port());
        server.pipeline().addFirst("tls", handler);
        handler.handshakeFuture().addListener(done -> {
            if (done.isSuccess()) {
                toBackend(frame, sequence - clientShift + backendShift);
            }
            else {
                frame.release();
                LOG.log(Level.WARNING, "TLS to the MySQL server failed", done.cause());
                fail(sequence + 1, HANDSHAKE_ERROR, "08S01", "GrantForge cannot connect to the MySQL server with TLS");
            }
        });
    }

    /** Relays the client's steps of the sign-in, carrying its password across when the two sides differ in TLS. */
    private void signingIn(ByteBuf frame)
    {
        int sequence = Packets.sequence(frame);
        ByteBuf payload = Packets.payload(frame);
        switch (bridging) {
            case KEY_REQUEST -> {
                if (payload.readableBytes() == 1 && Packets.first(frame) == KEY_REQUEST) {
                    frame.release();
                    ByteBuf key = client.alloc().buffer();
                    try {
                        // ShardingSphere reads MySQLAuthMoreDataPacket but does not write it: its layout is the header and the data.
                        MySQLPacketPayload write = new MySQLPacketPayload(key, StandardCharsets.US_ASCII);
                        write.writeInt1(AUTH_MORE_DATA);
                        write.writeBytes(bridge.publicKey());
                        client.writeAndFlush(Packets.frames(client.alloc(), key, sequence + 1));
                    }
                    finally {
                        key.release();
                    }
                    // Two packets the server never sees.
                    clientShift += 2;
                    bridging = Bridging.ENCRYPTED;
                    return;
                }
                bridging = Bridging.NONE;
            }
            case ENCRYPTED -> {
                bridging = Bridging.NONE;
                byte[] clear;
                try {
                    byte[] encrypted = new byte[payload.readableBytes()];
                    payload.getBytes(payload.readerIndex(), encrypted);
                    clear = bridge.decrypt(encrypted, scramble);
                }
                catch (GeneralSecurityException unreadable) {
                    frame.release();
                    LOG.log(Level.FINE, "A client's encrypted password cannot be read", unreadable);
                    fail(sequence + 1, ACCESS_DENIED, "28000", "Access denied for user '" + user + "' (the proxy cannot read the password)");
                    return;
                }
                frame.release();
                ByteBuf sent = Packets.frames(client.alloc(), Unpooled.wrappedBuffer(clear), 0);
                Arrays.fill(clear, (byte) 0);
                toBackend(sent, sequence - clientShift + backendShift);
                return;
            }
            case CLEAR -> {
                bridging = Bridging.SERVER_KEY;
                password = new byte[payload.readableBytes()];
                payload.getBytes(payload.readerIndex(), password);
                frame.release();
                // Ask the plain server for its key, to send the password encrypted.
                toBackend(Packets.frames(client.alloc(), Unpooled.wrappedBuffer(new byte[] {KEY_REQUEST}), 0),
                        sequence - clientShift + backendShift);
                return;
            }
            default -> {
                // Nothing to carry.
            }
        }
        toBackend(frame, sequence - clientShift + backendShift);
    }

    /** Sends the TLS client's password to the plain server, encrypted with the server's key. */
    private void sendPassword(byte[] key, int sequence)
    {
        bridging = Bridging.NONE;
        byte[] encrypted;
        try {
            encrypted = PasswordBridge.encrypt(password, scramble, key);
        }
        catch (GeneralSecurityException | IllegalArgumentException unusable) {
            LOG.log(Level.WARNING, "The MySQL server's RSA key cannot be used", unusable);
            fail(sequence - backendShift + clientShift, HANDSHAKE_ERROR, "08S01", "GrantForge cannot use the MySQL server's RSA key");
            return;
        }
        finally {
            Arrays.fill(password, (byte) 0);
        }
        // Two packets the client never sees.
        backendShift += 2;
        toBackend(Packets.frames(client.alloc(), Unpooled.wrappedBuffer(encrypted), 0), sequence);
    }

    /** Switches the client's connection to TLS after its SSL request; bytes it sent after the request belong to TLS. */
    private void startClientTls(SslContext tls)
    {
        ChannelPipeline pipeline = client.pipeline();
        ByteBuf early = ((Packets.Framer) pipeline.get("framer")).takeRest();
        pipeline.remove("framer");
        pipeline.addFirst("tls", tls.newHandler(client.alloc()));
        pipeline.addAfter("tls", "framer", Packets.framer());
        clientTls = true;
        clientShift = 1;
        if (early != null) {
            pipeline.fireChannelRead(early);
        }
    }

    private void toBackend(ByteBuf frame, int sequence)
    {
        Channel server = backend;
        if (server == null) {
            frame.release();
            close();
            return;
        }
        Packets.sequence(frame, sequence);
        server.writeAndFlush(frame);
    }

    /** Works through the client's packets in order; a check in progress holds the rest back. */
    private void drain()
    {
        while (!busy && phase == Phase.COMMAND && !waiting.isEmpty()) {
            process(waiting.removeFirst());
        }
        Channel server = backend;
        client.config().setAutoRead(!busy && phase == Phase.COMMAND && (server == null || server.isWritable()));
    }

    private void process(ByteBuf frame)
    {
        int sequence = Packets.sequence(frame);
        CompositeByteBuf gathered = gathering;
        if (gathered != null) {
            if (sequence != (gatheredSequence + 1 & 0xFF)) {
                frame.release();
                violation("a command's packets arrived out of order");
                return;
            }
            gatheredSequence = sequence;
            gathered.addComponent(true, Packets.payload(frame).retain());
            boolean last = Packets.length(frame) < Packets.MAX_PAYLOAD;
            frame.release();
            if (last) {
                gathering = null;
                command(gathered);
            }
            return;
        }
        if (sequence != 0) {
            // Only a LOCAL INFILE file or the steps of COM_CHANGE_USER's sign-in continue a command.
            if (responses.awaitsClient()) {
                toBackend(frame, sequence);
            }
            else {
                frame.release();
                violation("a packet arrived that continues no command");
            }
            return;
        }
        if (Packets.length(frame) == Packets.MAX_PAYLOAD) {
            CompositeByteBuf started = client.alloc().compositeBuffer(Integer.MAX_VALUE);
            started.addComponent(true, Packets.payload(frame).retain());
            frame.release();
            gathering = started;
            gatheredSequence = 0;
            return;
        }
        ByteBuf payload = Packets.payload(frame).retain();
        frame.release();
        if (payload.isReadable()) {
            command(payload);
        }
        else {
            payload.release();
            violation("an empty command arrived");
        }
    }

    private void command(ByteBuf payload)
    {
        MySQLCommandPacketType type;
        try {
            type = MySQLCommandPacketType.valueOf(payload.getUnsignedByte(payload.readerIndex()));
        }
        catch (RuntimeException unknown) {
            refuse(payload, "GrantForge does not allow the command " + payload.getUnsignedByte(payload.readerIndex()));
            return;
        }
        switch (type) {
            case COM_QUERY -> check(payload, false);
            case COM_STMT_PREPARE -> check(payload, true);
            case COM_STMT_EXECUTE -> execute(payload);
            case COM_INIT_DB -> initDb(payload);
            case COM_FIELD_LIST -> fieldList(payload);
            case COM_CHANGE_USER -> changeUser(payload);
            case COM_RESET_CONNECTION -> send(payload, Responses.Kind.SINGLE, ok -> {
                if (ok) {
                    prepared.clear();
                    lastPrepared = -1;
                    charset = signInCharset;
                }
            }, null);
            case COM_STMT_CLOSE -> {
                prepared.remove(payload.getUnsignedIntLE(payload.readerIndex() + 1));
                forward(payload);
            }
            case COM_STMT_SEND_LONG_DATA -> forward(payload);
            case COM_QUIT -> {
                forward(payload);
                close();
            }
            case COM_STMT_FETCH -> send(payload, Responses.Kind.FETCH, null, null);
            case COM_PROCESS_INFO -> send(payload, Responses.Kind.RESULT, null, null);
            case COM_PING, COM_STATISTICS, COM_SET_OPTION, COM_STMT_RESET, COM_PROCESS_KILL, COM_REFRESH, COM_DEBUG ->
                    send(payload, Responses.Kind.SINGLE, null, null);
            // Replication streams, the old database commands and the rest bypass statements altogether.
            default -> refuse(payload, "GrantForge does not allow the command " + type.name());
        }
    }

    /** Checks COM_QUERY's or COM_STMT_PREPARE's text off the event loop, then sends it, rewritten maybe, or refuses it. */
    private void check(ByteBuf payload, boolean prepare)
    {
        String sql;
        try {
            sql = decode(payload, 1);
        }
        catch (CharacterCodingException unreadable) {
            refuse(payload, "GrantForge cannot read the statement in the connection's character set " + charset.name());
            return;
        }
        String name = user;
        String current = database;
        evaluate(sql, current, name, prepare, outcome -> decided(payload, sql, current, prepare, outcome));
    }

    private void evaluate(String sql, @Nullable String current, String name, boolean prepare, Consumer<Outcome> then)
    {
        busy = true;
        client.config().setAutoRead(false);
        Policies policies = gate.policies().get();
        CompletableFuture.supplyAsync(() -> {
            try {
                List<StatementPlan> plans = analyzer.analyze(sql, current);
                return new Outcome(plans, guard.check(sql, plans, name, clientIp, policies, prepare), null, policies.version());
            }
            catch (Refusal refusal) {
                return new Outcome(null, null, refusal, policies.version());
            }
        }, gate.executor()).whenComplete((outcome, failure) -> client.eventLoop().execute(() -> {
            busy = false;
            if (phase == Phase.CLOSED) {
                return;
            }
            if (failure != null) {
                LOG.log(Level.WARNING, "GrantForge could not check a statement", failure);
                then.accept(new Outcome(null, null, new Refusal(Refusal.NOT_ALLOWED,
                        "GrantForge cannot check the statement now: the MySQL schema cannot be read"), policies.version()));
            }
            else {
                then.accept(outcome);
            }
            drain();
        }));
    }

    private void decided(ByteBuf payload, String sql, @Nullable String current, boolean prepare, Outcome outcome)
    {
        Verdict verdict = outcome.verdict();
        if (verdict != null) {
            verdict.events().forEach(gate.events());
        }
        Refusal refusal = verdict != null ? verdict.refusal() : outcome.refusal();
        if (refusal != null || verdict == null || outcome.plans() == null) {
            payload.release();
            Refusal shown = refusal != null ? refusal : new Refusal(Refusal.NOT_ALLOWED, "GrantForge refused the statement");
            answer(Packets.error(client.alloc(), 1, shown.code(), shown.state(), String.valueOf(shown.getMessage())));
            return;
        }
        ByteBuf sent = payload;
        String rewritten = verdict.sql();
        if (rewritten != null) {
            try {
                sent = encode(payload.getUnsignedByte(payload.readerIndex()), rewritten);
            }
            catch (CharacterCodingException unwritable) {
                payload.release();
                answer(Packets.error(client.alloc(), 1, Refusal.NOT_ALLOWED, "42000",
                        "GrantForge cannot write the rewritten statement in the connection's character set " + charset.name()));
                return;
            }
            payload.release();
        }
        List<StatementPlan> plans = requireNonNull(outcome.plans(), "plans");
        Consumer<Boolean> done = ok -> {
            if (!ok) {
                return;
            }
            for (StatementPlan plan : plans) {
                String used = plan.database();
                if (used != null) {
                    database = used;
                }
                String named = plan.charset();
                if (named != null) {
                    Charset chosen = Charsets.byName(named);
                    if (chosen == null) {
                        // The server reads statements in a character set the proxy cannot: it may not check any more.
                        violation("the client chose the character set " + named + ", which GrantForge cannot read");
                        return;
                    }
                    charset = chosen;
                }
                if (plan.action().startsWith("CREATE ") || plan.action().startsWith("ALTER ") || plan.action().startsWith("DROP ")
                        || plan.action().startsWith("RENAME ") || "TRUNCATE".equals(plan.action())) {
                    gate.catalog().changed();
                }
            }
        };
        long version = outcome.version();
        send(sent, prepare ? Responses.Kind.PREPARE : Responses.Kind.RESULT, done, prepare ? id -> {
            prepared.put(id, new Prepared(sql, rewritten, current, version));
            lastPrepared = id;
        } : null);
    }

    /** Executes a prepared statement, checking it again if the policies changed since it was prepared. */
    private void execute(ByteBuf payload)
    {
        long id = payload.getUnsignedIntLE(payload.readerIndex() + 1);
        Prepared statement = prepared.get(id == 0xFFFF_FFFFL ? lastPrepared : id);
        if (statement == null || statement.version() == gate.policies().get().version()) {
            send(payload, Responses.Kind.RESULT, null, null);
            return;
        }
        long key = id == 0xFFFF_FFFFL ? lastPrepared : id;
        evaluate(statement.sql(), statement.database(), user, true, outcome -> {
            Verdict verdict = outcome.verdict();
            if (verdict != null) {
                verdict.events().forEach(gate.events());
            }
            if (verdict == null || !verdict.allowed() || !Objects.equals(verdict.sql(), statement.sent())) {
                payload.release();
                answer(Packets.error(client.alloc(), 1, NEED_REPREPARE, "HY000",
                        "GrantForge policies changed since this statement was prepared; prepare it again"));
                return;
            }
            prepared.put(key, new Prepared(statement.sql(), statement.sent(), statement.database(), outcome.version()));
            send(payload, Responses.Kind.RESULT, null, null);
        });
    }

    private void initDb(ByteBuf payload)
    {
        String chosen;
        try {
            chosen = decode(payload, 1);
        }
        catch (CharacterCodingException unreadable) {
            refuse(payload, "GrantForge cannot read the database name");
            return;
        }
        String folded = fold(chosen);
        send(payload, Responses.Kind.SINGLE, ok -> {
            if (ok) {
                database = folded;
            }
        }, null);
    }

    /** COM_FIELD_LIST lists a table's columns, so it needs the table as a whole. */
    private void fieldList(ByteBuf payload)
    {
        int end = payload.indexOf(payload.readerIndex() + 1, payload.writerIndex(), (byte) 0);
        String table = payload.toString(payload.readerIndex() + 1, (end < 0 ? payload.writerIndex() : end) - payload.readerIndex() - 1, charset);
        String current = database;
        Policies policies = gate.policies().get();
        if (current == null || !policies.enforced()) {
            send(payload, Responses.Kind.FIELDS, null, null);
            return;
        }
        String name = fold(table);
        AccessRequest.Builder request = AccessRequest.builder(user, "select").resource("database", current).resource("table", name);
        if (clientIp != null) {
            request.context(Collections.singletonMap("clientAddress", clientIp));
        }
        AgentDecision decision = policies.decide(request.build());
        gate.events().accept(AccessEvent.builder(user, current + "." + name, "select", decision.allowed()).decidedBy(decision)
                .enforcedByGrantForge().clientIp(clientIp).resourceType("table").action("FIELD LIST").build());
        if (decision.allowed()) {
            send(payload, Responses.Kind.FIELDS, null, null);
        }
        else {
            payload.release();
            answer(Packets.error(client.alloc(), 1, Refusal.TABLE_DENIED, "42000", "SELECT command denied to user '" + user + "' for table '"
                    + name + "' by GrantForge"));
        }
    }

    /** COM_CHANGE_USER signs in again as another user; the proxy follows once the server accepts. */
    private void changeUser(ByteBuf payload)
    {
        if (clientTls != (settings.backendTls() != null)) {
            // The password could not be carried across sides of different TLS in the middle of a session.
            refuse(payload, "GrantForge does not allow COM_CHANGE_USER when the client's and the server's connections differ in TLS;"
                    + " connect again as the other user");
            return;
        }
        String next;
        String chosen;
        Charset nextCharset = charset;
        try {
            MySQLPacketPayload read = new MySQLPacketPayload(payload.duplicate(), StandardCharsets.UTF_8);
            read.readInt1();
            next = read.readStringNul();
            if ((clientCapabilities & SECURE_CONNECTION) != 0) {
                read.skipReserved(read.readInt1());
            }
            else {
                read.readStringNul();
            }
            chosen = read.readStringNul();
            if (read.getByteBuf().readableBytes() >= 2) {
                nextCharset = Charsets.byCollation(read.readInt2());
            }
        }
        catch (RuntimeException unreadable) {
            refuse(payload, "GrantForge cannot read COM_CHANGE_USER");
            return;
        }
        String folded = chosen.isEmpty() ? null : fold(chosen);
        Charset changed = nextCharset;
        send(payload, Responses.Kind.SIGN_IN, ok -> {
            if (ok) {
                user = next;
                database = folded;
                charset = changed;
                signInCharset = changed;
                prepared.clear();
                lastPrepared = -1;
            }
        }, null);
    }

    private void send(ByteBuf payload, Responses.Kind kind, @Nullable Consumer<Boolean> done, @Nullable LongConsumer statement)
    {
        responses.expect(kind, done, statement);
        forward(payload);
    }

    private void forward(ByteBuf payload)
    {
        Channel server = backend;
        if (server == null) {
            payload.release();
            close();
            return;
        }
        try {
            server.writeAndFlush(Packets.frames(server.alloc(), payload, 0));
        }
        finally {
            payload.release();
        }
    }

    private void refuse(ByteBuf payload, String message)
    {
        payload.release();
        answer(Packets.error(client.alloc(), 1, Refusal.NOT_ALLOWED, "42000", message));
    }

    private void answer(ByteBuf packet)
    {
        ByteBuf now = responses.answer(packet);
        if (now != null) {
            client.writeAndFlush(now);
        }
    }

    private void violation(String what)
    {
        LOG.warning("Closing the connection of " + user + " from " + clientIp + ": " + what);
        close();
    }

    private void fail(int sequence, int code, String state, String message)
    {
        client.writeAndFlush(Packets.error(client.alloc(), sequence, code, state, message)).addListener(ChannelFutureListener.CLOSE);
        shutdown();
    }

    /** Closes both connections. */
    void close()
    {
        shutdown();
        client.close();
    }

    /** Closes the server's connection and lets go of what waits; the client's closes on its own terms. */
    private void shutdown()
    {
        if (closed) {
            return;
        }
        closed = true;
        phase = Phase.CLOSED;
        waiting.forEach(ByteBuf::release);
        waiting.clear();
        CompositeByteBuf gathered = gathering;
        if (gathered != null) {
            gathered.release();
            gathering = null;
        }
        responses.release();
        Channel server = backend;
        if (server != null) {
            server.close();
        }
    }

    private String fold(String name)
    {
        return gate.catalog().foldsNames() ? name.toLowerCase(Locale.ROOT) : name;
    }

    /** Reads text strictly: a byte the character set does not have stops the statement rather than change it. */
    private String decode(ByteBuf payload, int skip) throws CharacterCodingException
    {
        ByteBuffer bytes = payload.nioBuffer(payload.readerIndex() + skip, payload.readableBytes() - skip);
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(bytes).toString();
    }

    private ByteBuf encode(int command, String text) throws CharacterCodingException
    {
        ByteBuffer bytes = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text));
        return client.alloc().buffer(bytes.remaining() + 1).writeByte(command).writeBytes(bytes);
    }
}
