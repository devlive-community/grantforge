// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PacketsTest
{
    @Test
    void framesFragmentedAndCoalescedTcpReads()
    {
        EmbeddedChannel channel = new EmbeddedChannel(Packets.framer());
        ByteBuf first = frame(255, new byte[] {3, 'S', 'Q', 'L'});
        ByteBuf second = frame(0, new byte[0]);
        ByteBuf input = Unpooled.wrappedBuffer(first, second);
        try {
            assertThat(channel.writeInbound(input.readRetainedSlice(2))).isFalse();
            assertThat(channel.writeInbound(input.readRetainedSlice(input.readableBytes()))).isTrue();
            ByteBuf decoded = channel.readInbound();
            try {
                assertThat(Packets.length(decoded)).isEqualTo(4);
                assertThat(Packets.sequence(decoded)).isEqualTo(255);
                assertThat(Packets.first(decoded)).isEqualTo(3);
                assertThat(Packets.payload(decoded).toString(StandardCharsets.US_ASCII)).isEqualTo("\3SQL");
                Packets.sequence(decoded, 256);
                assertThat(Packets.sequence(decoded)).isZero();
            }
            finally {
                decoded.release();
            }
            ByteBuf empty = channel.readInbound();
            try {
                assertThat(Packets.length(empty)).isZero();
                assertThat(Packets.first(empty)).isEqualTo(-1);
            }
            finally {
                empty.release();
            }
            assertThat((Object) channel.readInbound()).isNull();
        }
        finally {
            input.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void handsUnreadTlsBytesBackWhenThePacketDecoderIsRemoved()
    {
        Packets.Framer framer = Packets.framer();
        EmbeddedChannel channel = new EmbeddedChannel(framer);
        try {
            assertThat(framer.takeRest()).isNull();
            assertThat(channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {22, 3, 3}))).isFalse();
            ByteBuf tls = framer.takeRest();
            assertThat(tls).isNotNull();
            try {
                assertThat(tls.readUnsignedByte()).isEqualTo((short) 22);
                assertThat(tls.readUnsignedShort()).isEqualTo(0x0303);
            }
            finally {
                tls.release();
            }
            assertThat(framer.takeRest()).isNull();
        }
        finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void maximumPayloadEndsWithAnEmptyContinuationAndWrapsSequenceIds()
    {
        ByteBuf payload = Unpooled.buffer(Packets.MAX_PAYLOAD).writeZero(Packets.MAX_PAYLOAD);
        ByteBuf wire = null;
        try {
            wire = Packets.frames(UnpooledByteBufAllocator.DEFAULT, payload, 255);
            assertThat(payload.isReadable()).isFalse();
            assertThat(Packets.length(wire)).isEqualTo(Packets.MAX_PAYLOAD);
            assertThat(Packets.sequence(wire)).isEqualTo(255);
            wire.skipBytes(Packets.HEADER + Packets.MAX_PAYLOAD);
            assertThat(Packets.length(wire)).isZero();
            assertThat(Packets.sequence(wire)).isZero();
            assertThat(wire.readableBytes()).isEqualTo(Packets.HEADER);
        }
        finally {
            payload.release();
            if (wire != null) {
                wire.release();
            }
        }
    }

    @Test
    void serializesMySqlErrorCodeStateAndMessage()
    {
        ByteBuf error = Packets.error(UnpooledByteBufAllocator.DEFAULT, 7, 1142, "42000", "table access denied");
        try {
            assertThat(Packets.sequence(error)).isEqualTo(7);
            ByteBuf payload = Packets.payload(error);
            assertThat(payload.readUnsignedByte()).isEqualTo((short) Packets.ERR);
            assertThat(payload.readUnsignedShortLE()).isEqualTo(1142);
            assertThat(payload.readByte()).isEqualTo((byte) '#');
            assertThat(payload.readCharSequence(5, StandardCharsets.US_ASCII).toString()).isEqualTo("42000");
            assertThat(payload.toString(StandardCharsets.UTF_8)).isEqualTo("table access denied");
        }
        finally {
            error.release();
        }
    }

    @Test
    void readsOkAndEofStatusWithoutConsumingTheRelayedFrame()
    {
        ByteBuf ok = frame(1, new byte[] {0, 0, 0, 8, 0, 0, 0});
        ByteBuf eof = frame(2, new byte[] {(byte) 0xFE, 0, 0, 64, 0});
        ByteBuf count = frame(3, new byte[] {(byte) 0xFC, 44, 1});
        try {
            assertThat(Packets.status(ok)).isEqualTo(8);
            assertThat(Packets.status(eof)).isEqualTo(64);
            assertThat(Packets.leadingInteger(count)).isEqualTo(300);
            assertThat(ok.readerIndex()).isZero();
            assertThat(eof.readerIndex()).isZero();
            assertThat(count.readerIndex()).isZero();
        }
        finally {
            ok.release();
            eof.release();
            count.release();
        }
    }

    private static ByteBuf frame(int sequence, byte[] payload)
    {
        return Unpooled.buffer(payload.length + Packets.HEADER).writeMediumLE(payload.length).writeByte(sequence).writeBytes(payload);
    }
}
