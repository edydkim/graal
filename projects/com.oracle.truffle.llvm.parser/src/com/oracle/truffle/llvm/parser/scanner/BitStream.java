/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.scanner;

import java.nio.ByteBuffer;

public final class BitStream {

    private static final long BYTE_MASK = 0xffL;
    private final ByteBuffer bitstream;

    private BitStream(ByteBuffer bitstream) {
        this.bitstream = bitstream;
    }

    public static BitStream create(ByteBuffer bytes) {

        return new BitStream(bytes);
    }

    public static BitStream createFromBlob(long[] args, int blobStartIndex) {
        final byte[] blob = new byte[(args.length - blobStartIndex) * Long.BYTES];
        int to = 0;
        for (int from = blobStartIndex; from < args.length; from++) {
            final long l = args[from];
            for (int i = 0; i < Long.BYTES; i++) {
                blob[to++] = (byte) ((l >> (Byte.SIZE * i)) & BYTE_MASK);
            }
        }
        return new BitStream(ByteBuffer.wrap(blob));
    }

    public static long widthVBR(long value, long width) {
        long total = 0;
        long v = value;
        do {
            total += width;
            v >>>= (width - 1);
        } while (v != 0);
        return total;
    }

    public long read(long offset, long bits) {
        final long l = read(offset);
        if (bits < Long.SIZE) {
            // shifting 1L << 64 would cause an overflow
            return l & ((1L << bits) - 1L);
        } else {
            return l;
        }
    }

    public long readVBR(long offset, long width) {
        long value = 0;
        long shift = 0;
        long datum;
        long o = offset;
        long dmask = 1 << (width - 1);
        do {
            datum = read(o, width);
            o += width;
            value += (datum & (dmask - 1)) << shift;
            shift += width - 1;
        } while ((datum & dmask) != 0);
        return value;
    }

    public long size() {
        return bitstream.limit() * Byte.SIZE;
    }

    private long read(long offset) {
        long div = offset / Byte.SIZE;
        long value = 0;
        for (int i = 0; i < Byte.SIZE; i++) {
            value += readAlignedByte(div + i) << (i * Byte.SIZE);
        }
        long mod = offset & (Byte.SIZE - 1L);
        if (mod != 0) {
            value >>>= mod;
            value += readAlignedByte(div + Byte.SIZE) << (Long.SIZE - mod);
        }
        return value;
    }

    private long readAlignedByte(long i) {
        return i < bitstream.capacity() ? bitstream.get((int) i) & BYTE_MASK : 0;
    }

    public ByteBuffer getBitstream() {
        return bitstream;
    }
}
