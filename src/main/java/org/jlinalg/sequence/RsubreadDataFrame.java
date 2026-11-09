/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;

/**
 * Immutable scalar columns observed in Rsubread annotation, summary and junction
 * data frames. Retains storage, order, missing strings and raw numeric bits.
 * This is a value bridge, not an R object serializer or an alignment counter.
 */
public final class RsubreadDataFrame {
    public enum Type { CHARACTER, INTEGER, DOUBLE, LOGICAL }
    public enum RowNames { AUTOMATIC, EXPLICIT }

    /** Bounded complete reads. Text budgets include names and character cells. */
    public record Limits(int rows, int columns, int cells, int textUtf8Bytes, long totalTextUtf8Bytes) {
        public Limits {
            if (rows < 0 || columns < 0 || cells < 0 || textUtf8Bytes < 1
                    || textUtf8Bytes > 16777216 || totalTextUtf8Bytes < 0
                    || totalTextUtf8Bytes > 1000000000000L)
                throw new IllegalArgumentException("Invalid data-frame capacities");
        }
        public static Limits defaults() { return new Limits(1000000, 10000, 25000000, 1048576, 67108864); }
    }

    /** R integer/logical NA is Integer.MIN_VALUE; DOUBLE words are raw binary64 bits. */
    public static final class Column {
        private final Name name;
        private final Type type;
        private final List<Name> strings;
        private final long[] words;

        private Column(Name name, Type type, List<Name> strings, long[] words) {
            this.name = Objects.requireNonNull(name); this.type = Objects.requireNonNull(type);
            this.strings = List.copyOf(strings); this.words = words.clone();
            if (type == Type.CHARACTER) {
                if (words.length != 0) throw new IllegalArgumentException("Character column has numeric storage");
            } else {
                if (!strings.isEmpty()) throw new IllegalArgumentException("Numeric column has character storage");
                for (long value : words) {
                    if (type == Type.INTEGER && value != (int) value)
                        throw new IllegalArgumentException("R integer exceeds int32");
                    if (type == Type.LOGICAL && value != 0 && value != 1 && value != Integer.MIN_VALUE)
                        throw new IllegalArgumentException("Invalid R logical value");
                }
            }
        }
        public static Column characters(Name name, List<Name> values) {
            return new Column(name, Type.CHARACTER, values, new long[0]);
        }
        public static Column integers(Name name, int[] values) { return integral(name, Type.INTEGER, values); }
        public static Column logicals(Name name, int[] values) { return integral(name, Type.LOGICAL, values); }
        private static Column integral(Name name, Type type, int[] values) {
            long[] words = new long[values.length]; for (int i = 0; i < values.length; i++) words[i] = values[i];
            return new Column(name, type, List.of(), words);
        }
        public static Column doubles(Name name, long[] rawBinary64) {
            return new Column(name, Type.DOUBLE, List.of(), rawBinary64);
        }
        public Name name() { return name; }
        public Type type() { return type; }
        public int size() { return type == Type.CHARACTER ? strings.size() : words.length; }
        public List<Name> characters() {
            if (type != Type.CHARACTER) throw new IllegalStateException("Column is not character storage");
            return strings;
        }
        public long rawWord(int row) {
            if (type == Type.CHARACTER) throw new IllegalStateException("Column is not numeric storage");
            return words[Objects.checkIndex(row, words.length)];
        }
        public long[] rawWords() {
            if (type == Type.CHARACTER) throw new IllegalStateException("Column is not numeric storage");
            return words.clone();
        }
        /** Missing integral/logical values yield NaN. Raw DOUBLE payloads remain accessible. */
        public double numeric(int row) {
            long word = rawWord(row);
            return type == Type.DOUBLE ? Double.longBitsToDouble(word) : word == Integer.MIN_VALUE ? Double.NaN : word;
        }
    }

    private final RowNames rowNamesKind;
    private final List<Name> rowNames;
    private final List<Column> columns;

    public RsubreadDataFrame(RowNames kind, List<Name> rowNames, List<Column> columns) {
        this.rowNamesKind = Objects.requireNonNull(kind); this.rowNames = List.copyOf(rowNames);
        this.columns = List.copyOf(columns); var unique = new HashSet<Name>();
        for (int i = 0; i < rowNames.size(); i++) {
            Name name = rowNames.get(i);
            if (name.missing() || !unique.add(name)) throw new IllegalArgumentException("Data-frame row names must be nonmissing and unique");
            if (kind == RowNames.AUTOMATIC && !name.equals(Name.literal(Integer.toString(i + 1))))
                throw new IllegalArgumentException("Automatic data-frame row names differ from 1..n");
        }
        for (Column column : columns) if (column.size() != rowNames.size())
            throw new IllegalArgumentException("Data-frame column population differs");
    }
    public int rows() { return rowNames.size(); }
    public int columns() { return columns.size(); }
    public RowNames rowNamesKind() { return rowNamesKind; }
    public List<Name> rowNames() { return rowNames; }
    public List<Column> columnList() { return columns; }
    public Column column(int index) { return columns.get(index); }

    /** Canonical ASCII/LF bridge. Caller owns the writer. */
    public void writeWire(Writer out) throws IOException {
        out.write("RSUBREAD_FRAME_V1\t" + rows() + "\t" + columns() + "\t" + rowNamesKind + "\n");
        for (Name name : rowNames) out.write("R\t" + token(name) + "\n");
        for (Column column : columns) {
            out.write("C\t" + column.type + "\t" + token(column.name) + "\n");
            for (int row = 0; row < rows(); row++) {
                String value = column.type == Type.CHARACTER ? token(column.strings.get(row))
                    : column.type == Type.DOUBLE ? HexFormat.of().toHexDigits(column.words[row])
                    : HexFormat.of().toHexDigits((int) column.words[row]);
                out.write("V\t" + value + "\n");
            }
        }
    }

    /** Complete populations and EOF are required; unknown fields and exceeded capacities fail. */
    public static RsubreadDataFrame readWire(Reader in, Limits limits) throws IOException {
        Objects.requireNonNull(in); Objects.requireNonNull(limits);
        try {
            String[] h = line(in, 96).split("\t", -1);
            if (h.length != 4 || !h[0].equals("RSUBREAD_FRAME_V1")
                    || !h[1].matches("0|[1-9][0-9]*") || !h[2].matches("0|[1-9][0-9]*"))
                throw new IOException("Invalid data-frame header");
            int rows = Integer.parseInt(h[1]), columns = Integer.parseInt(h[2]);
            RowNames kind = RowNames.valueOf(h[3]);
            if (rows > limits.rows || columns > limits.columns || (long) rows * columns > limits.cells)
                throw new IOException("Data-frame capacity exceeded; increase explicit limits and heap or skip");
            long[] remaining = {limits.totalTextUtf8Bytes}; var names = new ArrayList<Name>(rows);
            for (int i = 0; i < rows; i++) {
                String text = line(in, 2 * limits.textUtf8Bytes + 3);
                if (!text.startsWith("R\t")) throw new IOException("Missing/extra data-frame row name");
                names.add(name(text.substring(2), limits, remaining));
            }
            var values = new ArrayList<Column>(columns);
            for (int c = 0; c < columns; c++) {
                String[] text = line(in, 2 * limits.textUtf8Bytes + 16).split("\t", -1);
                if (text.length != 3 || !text[0].equals("C")) throw new IOException("Missing/extra data-frame column");
                Type type = Type.valueOf(text[1]); Name columnName = name(text[2], limits, remaining);
                var strings = new ArrayList<Name>(type == Type.CHARACTER ? rows : 0);
                long[] words = new long[type == Type.CHARACTER ? 0 : rows];
                for (int r = 0; r < rows; r++) {
                    String cell = line(in, type == Type.CHARACTER ? 2 * limits.textUtf8Bytes + 3 : 18);
                    if (!cell.startsWith("V\t")) throw new IOException("Missing/extra data-frame cell");
                    String token = cell.substring(2);
                    if (type == Type.CHARACTER) strings.add(name(token, limits, remaining));
                    else if (type == Type.DOUBLE) {
                        if (!token.matches("[0-9a-f]{16}")) throw new IOException("Invalid raw binary64 cell");
                        words[r] = Long.parseUnsignedLong(token, 16);
                    } else {
                        if (!token.matches("[0-9a-f]{8}")) throw new IOException("Invalid raw int32 cell");
                        words[r] = (int) Long.parseLong(token, 16);
                    }
                }
                values.add(new Column(columnName, type, strings, words));
            }
            if (in.read() != -1) throw new IOException("Trailing data-frame content");
            return new RsubreadDataFrame(kind, names, values);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid original data-frame population", e); }
    }

    private static String token(Name name) {
        return name.missing() ? "NA" : "S" + HexFormat.of().formatHex(name.value().getBytes(StandardCharsets.UTF_8));
    }
    private static Name name(String token, Limits limits, long[] remaining) throws IOException {
        if (token.equals("NA")) return Name.na();
        if (!token.matches("S[0-9a-f]*") || (token.length() - 1) % 2 != 0) throw new IOException("Invalid character cell/name");
        byte[] raw = HexFormat.of().parseHex(token.substring(1));
        if (raw.length > limits.textUtf8Bytes || raw.length > remaining[0]) throw new IOException("Data-frame text capacity exceeded");
        remaining[0] -= raw.length;
        return Name.literal(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString());
    }
    private static String line(Reader in, int cap) throws IOException {
        var text = new StringBuilder(Math.min(64, cap));
        for (int c; (c = in.read()) != -1;) {
            if (c == '\n') return text.toString();
            if (c > 127 || c == '\r' || text.length() >= cap) throw new IOException("Noncanonical/over-capacity data-frame line");
            text.append((char) c);
        }
        throw new IOException("Incomplete data-frame population");
    }
}
