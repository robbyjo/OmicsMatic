/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Complete RFC1952 member decoding; every trailer and the physical EOF are checked. */
final class RepeatGzip extends InputStream {
    private final PushbackInputStream input;
    private final Inflater inflater = new Inflater(true);
    private final CRC32 crc = new CRC32();
    private final byte[] compressed = new byte[8192];
    private final Runnable check;
    private long size;
    private int supplied;
    private boolean member, finished, closed;

    RepeatGzip(InputStream input, Runnable check) {
        this.input = new PushbackInputStream(input, compressed.length);
        this.check = check;
    }
    @Override public int read() throws IOException {
        byte[] value = new byte[1];
        return read(value, 0, 1) < 0 ? -1 : value[0] & 255;
    }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
        if (closed) throw new IOException("Repeat gzip stream is closed");
        if (length == 0) return 0;
        while (!finished) {
            check.run();
            if (!member) {
                int first = input.read();
                if (first < 0) {
                    if (!finishedMember) throw new EOFException("Missing gzip member");
                    finished = true; return -1;
                }
                header(first); member = true;
            }
            if (!inflater.finished() && inflater.needsInput()) {
                supplied = input.read(compressed);
                if (supplied < 0) throw new EOFException("Truncated gzip deflate data");
                inflater.setInput(compressed, 0, supplied);
            }
            int count;
            try { count = inflater.inflate(bytes, offset, length); }
            catch (DataFormatException invalid) { throw new IOException("Invalid gzip deflate data", invalid); }
            if (count > 0) { crc.update(bytes, offset, count); size = (size + count) & 0xffffffffL; return count; }
            if (inflater.finished()) {
                int remaining = inflater.getRemaining();
                if (remaining > 0) input.unread(compressed, supplied - remaining, remaining);
                long expectedCrc = little(4), expectedSize = little(4);
                if (expectedCrc != crc.getValue() || expectedSize != size)
                    throw new IOException("Gzip CRC32 or uncompressed-size trailer differs");
                inflater.reset(); member = false; finishedMember = true;
            } else if (inflater.needsDictionary()) throw new IOException("Gzip deflate dictionary is unavailable");
            else if (!inflater.needsInput()) throw new IOException("Gzip inflater made no progress");
        }
        return -1;
    }
    private boolean finishedMember;
    private void header(int first) throws IOException {
        CRC32 header = new CRC32();
        header.update(first);
        if (first != 31 || octet(header) != 139 || octet(header) != 8)
            throw new IOException("Require a gzip member header; trailing bytes are not discarded");
        int flags = octet(header);
        if ((flags & 224) != 0) throw new IOException("Reserved gzip header flags");
        for (int i = 0; i < 6; i++) octet(header);
        if ((flags & 4) != 0) {
            int length = octet(header) | octet(header) << 8;
            for (int i = 0; i < length; i++) octet(header);
        }
        if ((flags & 8) != 0) while (octet(header) != 0) check.run();
        if ((flags & 16) != 0) while (octet(header) != 0) check.run();
        if ((flags & 2) != 0 && little(2) != (header.getValue() & 65535))
            throw new IOException("Gzip header CRC16 differs");
        crc.reset(); size = 0;
    }
    private int octet(CRC32 header) throws IOException {
        int value = input.read(); if (value < 0) throw new EOFException("Truncated gzip header");
        header.update(value); return value;
    }
    private long little(int count) throws IOException {
        long value = 0;
        for (int i = 0; i < count; i++) {
            int next = input.read(); if (next < 0) throw new EOFException("Truncated gzip trailer/header checksum");
            value |= (long)next << (8 * i);
        }
        return value;
    }
    @Override public void close() throws IOException {
        if (!closed) { closed = true; inflater.end(); input.close(); }
    }
}
