// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.apache.shardingsphere.database.protocol.mysql.packet.generic.MySQLEofPacket;
import org.apache.shardingsphere.database.protocol.mysql.packet.generic.MySQLErrPacket;
import org.apache.shardingsphere.database.protocol.mysql.payload.MySQLPacketPayload;
import org.jspecify.annotations.Nullable;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * MySQL packets as the proxy relays them: whole frames, a four-byte header (three bytes of payload length, little
 * endian, and the sequence id) and the payload. Netty frames them; ShardingSphere's MySQL protocol classes read and
 * write the packets the proxy looks into.
 */
final class Packets
{
    /** The largest payload of one packet; a longer one continues in the next. */
    static final int MAX_PAYLOAD = 0xFF_FFFF;

    /** The header's length. */
    static final int HEADER = 4;

    /** The first byte of an OK packet. */
    static final int OK = 0x00;

    /** The first byte of an ERR packet. */
    static final int ERR = 0xFF;

    /** The first byte of an EOF packet, of an OK packet that ends a result set, and of an auth switch request. */
    static final int EOF = 0xFE;

    /** The first byte of the server's request for a LOCAL INFILE file. */
    static final int LOCAL_INFILE = 0xFB;

    private Packets()
    {
    }

    /**
     * Returns a decoder that cuts the bytes of a connection into whole packets, headers included.
     *
     * @return the decoder
     */
    static Framer framer()
    {
        return new Framer();
    }

    /** Netty's length-field framing of MySQL packets, which hands over the bytes after the last packet when TLS begins. */
    static final class Framer
            extends LengthFieldBasedFrameDecoder
    {
        Framer()
        {
            super(ByteOrder.LITTLE_ENDIAN, MAX_PAYLOAD + HEADER, 0, 3, 1, 0, true);
        }

        /**
         * Takes the bytes received after the packets decoded so far.
         *
         * @return them, or {@code null} when there are none
         */
        @Nullable ByteBuf takeRest()
        {
            ByteBuf rest = internalBuffer();
            return rest.isReadable() ? rest.readRetainedSlice(rest.readableBytes()) : null;
        }
    }

    /**
     * Returns the length of a frame's payload.
     *
     * @param frame the frame
     * @return the length
     */
    static int length(ByteBuf frame)
    {
        return frame.getUnsignedMediumLE(frame.readerIndex());
    }

    /**
     * Returns a frame's sequence id.
     *
     * @param frame the frame
     * @return the id
     */
    static int sequence(ByteBuf frame)
    {
        return frame.getUnsignedByte(frame.readerIndex() + 3);
    }

    /**
     * Changes a frame's sequence id in place.
     *
     * @param frame the frame
     * @param sequence the id, taken modulo 256 as MySQL does
     */
    static void sequence(ByteBuf frame, int sequence)
    {
        frame.setByte(frame.readerIndex() + 3, sequence & 0xFF);
    }

    /**
     * Returns a frame's payload, sharing its bytes.
     *
     * @param frame the frame
     * @return the payload
     */
    static ByteBuf payload(ByteBuf frame)
    {
        return frame.slice(frame.readerIndex() + HEADER, length(frame));
    }

    /**
     * Returns the first byte of a frame's payload.
     *
     * @param frame the frame
     * @return the byte, or -1 for an empty payload
     */
    static int first(ByteBuf frame)
    {
        return length(frame) == 0 ? -1 : frame.getUnsignedByte(frame.readerIndex() + HEADER);
    }

    /**
     * Writes a payload as packets: one, or more when it is longer than a packet holds.
     *
     * @param allocator the allocator
     * @param payload the payload
     * @param sequence the first packet's sequence id
     * @return the packets
     */
    static ByteBuf frames(ByteBufAllocator allocator, ByteBuf payload, int sequence)
    {
        ByteBuf out = allocator.buffer(payload.readableBytes() + HEADER * (payload.readableBytes() / MAX_PAYLOAD + 1));
        int next = sequence;
        while (true) {
            int length = Math.min(payload.readableBytes(), MAX_PAYLOAD);
            out.writeMediumLE(length).writeByte(next & 0xFF).writeBytes(payload, length);
            next++;
            // A payload of exactly the maximum ends with an empty packet, so the reader knows it is complete.
            if (length < MAX_PAYLOAD) {
                return out;
            }
        }
    }

    /**
     * Writes an ERR packet.
     *
     * @param allocator the allocator
     * @param sequence its sequence id
     * @param code the MySQL error code
     * @param state the SQL state
     * @param message the message
     * @return the packet
     */
    static ByteBuf error(ByteBufAllocator allocator, int sequence, int code, String state, String message)
    {
        ByteBuf payload = allocator.buffer();
        try {
            new MySQLErrPacket(new SQLException(message, state, code)).write(new MySQLPacketPayload(payload, StandardCharsets.UTF_8));
            return frames(allocator, payload, sequence);
        }
        finally {
            payload.release();
        }
    }

    /**
     * Reads the status flags of a packet that ends a result or a result set: OK (headed 0x00 or 0xFE) or EOF.
     *
     * @param frame the frame
     * @return the flags
     */
    static int status(ByteBuf frame)
    {
        ByteBuf payload = payload(frame);
        MySQLPacketPayload read = new MySQLPacketPayload(payload, StandardCharsets.UTF_8);
        if (payload.getUnsignedByte(payload.readerIndex()) == EOF && payload.readableBytes() < 9) {
            return new MySQLEofPacket(read).getStatusFlags();
        }
        read.readInt1();
        read.readIntLenenc();
        read.readIntLenenc();
        return read.readInt2();
    }

    /**
     * Reads the length-encoded integer that starts a payload, such as a result set's column count.
     *
     * @param frame the frame
     * @return the integer
     */
    static long leadingInteger(ByteBuf frame)
    {
        return new MySQLPacketPayload(payload(frame), StandardCharsets.UTF_8).readIntLenenc();
    }
}
