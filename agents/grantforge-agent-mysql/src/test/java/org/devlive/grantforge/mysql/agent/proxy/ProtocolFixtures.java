// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jspecify.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A real TCP peer that speaks the MySQL handshake and command protocol, without a database process. */
final class ProtocolFixtures
{
    static final int CAPABILITIES = (1 << 9) | (1 << 15) | (1 << 19) | 8;

    private ProtocolFixtures()
    {
    }

    record Packet(int sequence, byte[] payload)
    {
        int first()
        {
            return payload.length == 0 ? -1 : payload[0] & 0xFF;
        }

        int errorCode()
        {
            return (payload[1] & 0xFF) | (payload[2] & 0xFF) << 8;
        }

        String text()
        {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    static final class Connection
            implements AutoCloseable
    {
        final ProxyServer proxy;
        final Socket client;
        final Socket server;
        private final ServerSocket listener;
        private boolean proxyStopped;

        Connection(Gate gate) throws IOException, InterruptedException
        {
            ServerSocket openedListener = new ServerSocket();
            Socket openedClient = null;
            Socket acceptedServer = null;
            ProxyServer startedProxy = null;
            try {
                openedClient = new Socket();
                openedListener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
                openedListener.setSoTimeout(5000);
                startedProxy = ProxyServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                        new Backend(InetAddress.getLoopbackAddress().getHostAddress(), openedListener.getLocalPort(), null, null, false, 2000), gate);
                openedClient.connect(startedProxy.address(), 5000);
                openedClient.setSoTimeout(5000);
                acceptedServer = openedListener.accept();
                acceptedServer.setSoTimeout(5000);
                listener = openedListener;
                client = openedClient;
                server = acceptedServer;
                proxy = startedProxy;
            }
            catch (IOException | InterruptedException | RuntimeException failed) {
                closeAfterFailure(acceptedServer, failed);
                closeAfterFailure(openedClient, failed);
                closeAfterFailure(startedProxy, failed);
                closeAfterFailure(openedListener, failed);
                throw failed;
            }
        }

        void greet(int capabilities) throws IOException
        {
            ByteBuf payload = Unpooled.buffer();
            try {
                payload.writeByte(10).writeBytes("8.4.0\0".getBytes(StandardCharsets.US_ASCII)).writeIntLE(1)
                        .writeBytes("12345678".getBytes(StandardCharsets.US_ASCII)).writeByte(0).writeShortLE(capabilities & 0xFFFF)
                        .writeByte(45).writeShortLE(2).writeShortLE(capabilities >>> 16).writeByte(21).writeZero(10)
                        .writeBytes("abcdefghijkl\0".getBytes(StandardCharsets.US_ASCII))
                        .writeBytes("mysql_native_password\0".getBytes(StandardCharsets.US_ASCII));
                write(server, 0, payload);
            }
            finally {
                payload.release();
            }
        }

        void login(int capabilities) throws IOException
        {
            ByteBuf payload = Unpooled.buffer();
            try {
                payload.writeIntLE(capabilities).writeIntLE(16 * 1024 * 1024).writeByte(45).writeZero(23)
                        .writeBytes("alice\0".getBytes(StandardCharsets.US_ASCII)).writeByte(0)
                        .writeBytes("sales\0mysql_native_password\0".getBytes(StandardCharsets.US_ASCII));
                write(client, 1, payload);
            }
            finally {
                payload.release();
            }
        }

        void signIn() throws IOException
        {
            greet(CAPABILITIES);
            readClient();
            login(CAPABILITIES);
            readServer();
            sendServer(2, 0, 0, 0, 2, 0, 0, 0);
            readClient();
        }

        void query(String sql) throws IOException
        {
            ByteBuf payload = Unpooled.buffer();
            try {
                payload.writeByte(3).writeBytes(sql.getBytes(StandardCharsets.UTF_8));
                write(client, 0, payload);
            }
            finally {
                payload.release();
            }
        }

        void sendClient(int sequence, int... bytes) throws IOException
        {
            send(client, sequence, bytes);
        }

        void sendServer(int sequence, int... bytes) throws IOException
        {
            send(server, sequence, bytes);
        }

        Packet readClient() throws IOException
        {
            return read(client);
        }

        Packet readServer() throws IOException
        {
            return read(server);
        }

        void stopProxy()
        {
            if (!proxyStopped) {
                proxyStopped = true;
                proxy.close();
            }
        }

        @Override
        public void close() throws IOException
        {
            try {
                client.close();
            }
            finally {
                try {
                    server.close();
                }
                finally {
                    try {
                        stopProxy();
                    }
                    finally {
                        listener.close();
                    }
                }
            }
        }
    }

    private static void closeAfterFailure(@Nullable AutoCloseable resource, Exception failed)
    {
        if (resource != null) {
            try {
                resource.close();
            }
            catch (Exception cleanup) {
                failed.addSuppressed(cleanup);
            }
        }
    }

    private static void send(Socket socket, int sequence, int... bytes) throws IOException
    {
        ByteBuf payload = Unpooled.buffer(bytes.length);
        try {
            for (int value : bytes) {
                payload.writeByte(value);
            }
            write(socket, sequence, payload);
        }
        finally {
            payload.release();
        }
    }

    private static void write(Socket socket, int sequence, ByteBuf payload) throws IOException
    {
        int length = payload.readableBytes();
        socket.getOutputStream().write(new byte[] {(byte) length, (byte) (length >>> 8), (byte) (length >>> 16), (byte) sequence});
        byte[] bytes = new byte[length];
        payload.getBytes(payload.readerIndex(), bytes);
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    private static Packet read(Socket socket) throws IOException
    {
        InputStream input = socket.getInputStream();
        byte[] header = input.readNBytes(Packets.HEADER);
        if (header.length != Packets.HEADER) {
            throw new EOFException("the MySQL connection closed before its next packet header");
        }
        int length = (header[0] & 0xFF) | (header[1] & 0xFF) << 8 | (header[2] & 0xFF) << 16;
        byte[] payload = input.readNBytes(length);
        if (payload.length != length) {
            throw new EOFException("the MySQL connection closed in a packet payload");
        }
        return new Packet(header[3] & 0xFF, payload);
    }
}
