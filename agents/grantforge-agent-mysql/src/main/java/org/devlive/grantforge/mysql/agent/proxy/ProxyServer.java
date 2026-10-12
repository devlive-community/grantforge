// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/** The proxy's listening socket: each client that connects gets a {@link ProxySession} to the server. */
// A standalone server process: it runs Netty's event loops for itself.
@SuppressWarnings("PMD.DoNotUseThreads")
public final class ProxyServer
        implements AutoCloseable
{
    private final EventLoopGroup acceptor;
    private final EventLoopGroup workers;
    private final Channel channel;

    private ProxyServer(EventLoopGroup acceptor, EventLoopGroup workers, Channel channel)
    {
        this.acceptor = acceptor;
        this.workers = workers;
        this.channel = channel;
    }

    /**
     * Listens for clients.
     *
     * @param address where to listen; port 0 picks a free one
     * @param backend the server and the TLS of both sides
     * @param gate what statements are checked with
     * @return the running proxy
     * @throws InterruptedException when interrupted while binding
     */
    public static ProxyServer start(InetSocketAddress address, Backend backend, Gate gate) throws InterruptedException
    {
        requireNonNull(backend, "backend");
        requireNonNull(gate, "gate");
        PasswordBridge bridge = new PasswordBridge();
        EventLoopGroup acceptor = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workers = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            Channel channel = new ServerBootstrap().group(acceptor, workers).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>()
                    {
                        @Override
                        protected void initChannel(SocketChannel client)
                        {
                            ProxySession session = new ProxySession(client, backend, gate, bridge);
                            client.pipeline().addLast("framer", Packets.framer()).addLast("handler", session.clientHandler());
                        }
                    })
                    .bind(address).sync().channel();
            return new ProxyServer(acceptor, workers, channel);
        }
        catch (InterruptedException | RuntimeException failed) {
            acceptor.shutdownGracefully(0, 0, TimeUnit.SECONDS).awaitUninterruptibly();
            workers.shutdownGracefully(0, 0, TimeUnit.SECONDS).awaitUninterruptibly();
            throw failed;
        }
    }

    /**
     * Returns where the proxy listens.
     *
     * @return the address
     */
    public InetSocketAddress address()
    {
        return (InetSocketAddress) channel.localAddress();
    }

    /** Stops listening and closes every connection. */
    @Override
    public void close()
    {
        channel.close().syncUninterruptibly();
        acceptor.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        workers.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
    }
}
