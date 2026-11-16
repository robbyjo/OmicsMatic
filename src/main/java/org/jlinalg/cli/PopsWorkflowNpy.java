/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded non-pickle NumPy array reading, including C/Fortran order and byte order. */
final class PopsWorkflowNpy {
    private final Path file;
    private final int[] shape;
    private final char kind;
    private final int bytes;
    private final ByteOrder order;
    private final boolean fortran;
    private final long offset, fileBytes, elements;

    private PopsWorkflowNpy(Path file, int[] shape, char kind, int bytes, ByteOrder order, boolean fortran, long offset, long fileBytes, long elements) {
        this.file = file; this.shape = shape; this.kind = kind; this.bytes = bytes; this.order = order;
        this.fortran = fortran; this.offset = offset; this.fileBytes = fileBytes; this.elements = elements;
    }

    static PopsWorkflowNpy inspect(Path file, int maximumHeaderBytes, long maximumFileBytes) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) throw new IOException("Regular captured NPY file required: " + file);
        long size = Files.size(file); if (size > maximumFileBytes) throw new IOException("NPY exceeds declared input capacity");
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (!Arrays.equals(in.readNBytes(6), new byte[] {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y'})) throw new IOException("NPY magic differs");
            int major = in.readUnsignedByte(), minor = in.readUnsignedByte();
            if (minor != 0 || major < 1 || major > 3) throw new IOException("NPY format 1.0, 2.0 or 3.0 required");
            long length = major == 1 ? Short.toUnsignedInt(Short.reverseBytes(in.readShort())) : Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            if (length < 1 || length > maximumHeaderBytes) throw new IOException("NPY header exceeds declared capacity");
            byte[] raw = in.readNBytes(Math.toIntExact(length)); if (raw.length != length) throw new EOFException("Truncated NPY header");
            String text = new String(raw, major == 3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
            Header header = new Header(text); header.parse();
            String dtype = header.dtype;
            if (dtype.length() < 3 || "<>=|".indexOf(dtype.charAt(0)) < 0) throw new IOException("Explicit scalar NPY dtype required");
            char kind = dtype.charAt(1); int units;
            try { units = Integer.parseInt(dtype.substring(2)); } catch (NumberFormatException e) { throw new IOException("Invalid NPY dtype", e); }
            if (units < 1) throw new IOException("Positive NPY scalar size required");
            boolean numeric = (kind == 'f' && (units == 4 || units == 8)) || ((kind == 'i' || kind == 'u') && Set.of(1, 2, 4, 8).contains(units)) || (kind == 'b' && units == 1);
            if (!numeric && !((kind == 'S' || kind == 'U') && units <= 16)) throw new IOException("Unsupported scalar dtype; numeric primitive arrays or a short NPZ-format string required. Pickles and structured/object arrays are not read.");
            int bytes = kind == 'U' ? Math.multiplyExact(4, units) : units;
            long elements = 1;
            try { for (int dimension : header.shape) elements = Math.multiplyExact(elements, dimension); }
            catch (ArithmeticException e) { throw new IOException("NPY shape overflows declared byte capacity", e); }
            long offset = (major == 1 ? 10 : 12) + length;
            long expected;
            try { expected = Math.addExact(offset, Math.multiplyExact(elements, bytes)); }
            catch (ArithmeticException e) { throw new IOException("NPY payload byte count overflows", e); }
            if (expected != size) throw new IOException("NPY shape/dtype byte count differs from the complete file; truncated or trailing bytes");
            ByteOrder order = dtype.charAt(0) == '>' ? ByteOrder.BIG_ENDIAN : dtype.charAt(0) == '<' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.nativeOrder();
            return new PopsWorkflowNpy(file, header.shape, kind, bytes, order, header.fortran, offset, size, elements);
        }
    }

    int dimensions() { return shape.length; }
    int dimension(int index) { return shape[index]; }
    long elements() { return elements; }

    double[] vector() throws IOException {
        numeric(); if (shape.length > 1 || elements > Integer.MAX_VALUE) throw new IOException("Bounded NPY vector required");
        double[] values = new double[(int) elements];
        try (var channel = open()) { read(channel, offset, values, 0, values.length, ByteBuffer.allocate(65536).order(order)); }
        return values;
    }

    String textScalar() throws IOException {
        if ((kind != 'S' && kind != 'U') || elements != 1) throw new IOException("Scalar NPZ format string required");
        byte[] data = new byte[bytes];
        try (var channel = open()) { ByteBuffer b = ByteBuffer.wrap(data); channel.position(offset); fill(channel, b); }
        if (kind == 'S') {
            int count = 0; while (count < data.length && data[count] != 0) count++;
            return new String(data, 0, count, StandardCharsets.US_ASCII);
        }
        ByteBuffer b = ByteBuffer.wrap(data).order(order); StringBuilder text = new StringBuilder();
        while (b.hasRemaining()) { int code = b.getInt(); if (code == 0) break; if (!Character.isValidCodePoint(code)) throw new IOException("Invalid NPZ format string"); text.appendCodePoint(code); }
        return text.toString();
    }

    double[][] columns(int first, int count) throws IOException {
        numeric(); if (shape.length != 2 || first < 0 || count < 1 || (long) first + count > shape[1]) throw new IOException("Valid two-dimensional NPY column interval required");
        Math.multiplyExact(shape[0], count); double[][] result = new double[shape[0]][count]; ByteBuffer buffer = ByteBuffer.allocate(65536).order(order);
        try (var channel = open()) {
            if (!fortran) {
                for (int i = 0; i < shape[0]; i++) read(channel, offset + ((long) i * shape[1] + first) * bytes, result[i], 0, count, buffer);
            } else {
                double[] block = new double[Math.min(shape[0], 8192)];
                for (int j = 0; j < count; j++) for (int firstRow = 0; firstRow < shape[0]; firstRow += block.length) {
                    int length = Math.min(block.length, shape[0] - firstRow);
                    read(channel, offset + ((long) (first + j) * shape[0] + firstRow) * bytes, block, 0, length, buffer);
                    for (int i = 0; i < length; i++) result[firstRow + i][j] = block[i];
                }
            }
        }
        return result;
    }

    double[][] square(int[] indices) throws IOException {
        numeric(); if (shape.length != 2 || shape[0] != shape[1] || indices.length == 0) throw new IOException("Square NPY covariance and nonempty index set required");
        int low = Integer.MAX_VALUE, high = -1;
        for (int index : indices) { if (index < 0 || index >= shape[0]) throw new IOException("Covariance index out of range"); low = Math.min(low, index); high = Math.max(high, index); }
        double[][] result = new double[indices.length][indices.length]; double[] span = new double[high - low + 1]; ByteBuffer buffer = ByteBuffer.allocate(65536).order(order);
        try (var channel = open()) {
            for (int k = 0; k < indices.length; k++) {
                read(channel, offset + ((long) indices[k] * shape[0] + low) * bytes, span, 0, span.length, buffer);
                for (int j = 0; j < indices.length; j++) {
                    if (fortran) result[j][k] = span[indices[j] - low]; else result[k][j] = span[indices[j] - low];
                }
            }
        }
        return result;
    }

    private FileChannel open() throws IOException {
        if (Files.size(file) != fileBytes || Files.isSymbolicLink(file)) throw new IOException("Captured NPY file changed: " + file);
        return FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }
    private void numeric() throws IOException { if (kind == 'S' || kind == 'U') throw new IOException("Numeric primitive NPY array required"); }
    private void read(FileChannel channel, long start, double[] dest, int first, int count, ByteBuffer buffer) throws IOException {
        channel.position(start); int done = 0;
        while (done < count) {
            int length = Math.min(count - done, buffer.capacity() / bytes); buffer.clear(); buffer.limit(length * bytes); fill(channel, buffer); buffer.flip();
            for (int i = 0; i < length; i++) dest[first + done + i] = number(buffer);
            done += length;
        }
    }
    private double number(ByteBuffer b) {
        if (kind == 'f') return bytes == 8 ? b.getDouble() : b.getFloat();
        if (kind == 'b') return b.get() == 0 ? 0 : 1;
        if (kind == 'i') return switch (bytes) { case 1 -> b.get(); case 2 -> b.getShort(); case 4 -> b.getInt(); default -> b.getLong(); };
        return switch (bytes) {
            case 1 -> Byte.toUnsignedInt(b.get()); case 2 -> Short.toUnsignedInt(b.getShort()); case 4 -> Integer.toUnsignedLong(b.getInt());
            default -> { long value = b.getLong(); yield value >= 0 ? (double) value : BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63).doubleValue(); }
        };
    }
    private static void fill(FileChannel channel, ByteBuffer buffer) throws IOException { while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new EOFException("Truncated NPY payload"); }

    private static final class Header {
        final String text; int position; String dtype; int[] shape; boolean fortran;
        Header(String text) { this.text = text; }
        void parse() throws IOException {
            take('{'); Set<String> keys = new HashSet<>();
            while (peek() != '}') {
                String key = string(); if (!keys.add(key)) throw new IOException("Duplicate NPY header key"); take(':');
                switch (key) {
                    case "descr" -> dtype = string();
                    case "fortran_order" -> { if (literal("True")) fortran = true; else if (literal("False")) fortran = false; else throw new IOException("NPY order must be a boolean"); }
                    case "shape" -> {
                        take('('); List<Integer> dimensions = new ArrayList<>(); boolean comma = false;
                        while (peek() != ')') {
                            int start = position; while (position < text.length() && Character.isDigit(text.charAt(position))) position++;
                            try { if (start == position) throw new NumberFormatException(); dimensions.add(Integer.parseInt(text.substring(start, position))); }
                            catch (NumberFormatException e) { throw new IOException("Bounded nonnegative NPY dimensions required", e); }
                            if (dimensions.size() > 2) throw new IOException("Scalar, vector or two-dimensional NPY required");
                            if (peek() != ',') break; take(','); comma = true;
                        }
                        take(')'); if (dimensions.size() == 1 && !comma) throw new IOException("One-dimensional NPY shape must be a tuple with a comma");
                        shape = dimensions.stream().mapToInt(Integer::intValue).toArray();
                    }
                    default -> throw new IOException("Unexpected NPY header key: " + key);
                }
                if (peek() != ',') break; take(',');
            }
            take('}'); skip(); if (position != text.length() || !keys.equals(Set.of("descr", "fortran_order", "shape"))) throw new IOException("Incomplete or trailing NPY header expression");
        }
        boolean literal(String value) { skip(); if (!text.startsWith(value, position)) return false; position += value.length(); return true; }
        String string() throws IOException {
            char quote = peek(); if (quote != '\'' && quote != '"') throw new IOException("Literal NPY header string required"); position++; StringBuilder value = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++); if (c == quote) return value.toString();
                if (c == '\\') { if (position == text.length()) break; c = text.charAt(position++); if (c != quote && c != '\\') throw new IOException("Unsupported NPY string escape"); }
                value.append(c);
            }
            throw new IOException("Unterminated NPY header string");
        }
        void skip() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
        char peek() throws IOException { skip(); if (position == text.length()) throw new IOException("Truncated NPY header expression"); return text.charAt(position); }
        void take(char expected) throws IOException { if (peek() != expected) throw new IOException("Unexpected NPY header expression"); position++; }
    }
}
