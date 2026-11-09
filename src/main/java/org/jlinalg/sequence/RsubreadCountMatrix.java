/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Literal named count matrices returned by Rsubread and explicit column binding.
 * Duplicate and missing names are observations, not identifiers to repair.
 * This class does not count alignments, normalize data, join genes by name, or
 * reproduce an Rsubread fit. Numeric values are the represented R values.
 */
public final class RsubreadCountMatrix {
    /** Storage of the original R matrix, retained through integer/double promotion. */
    public enum Storage { INTEGER, DOUBLE }
    /** Caller must choose whether positional binding can retain different names. */
    public enum RowNamesPolicy { REQUIRE_IDENTICAL, RETAIN_FIRST_POSITIONAL }

    /** R character NA is distinct from the literal string "NA" and an empty name. */
    public record Name(boolean missing, String value) {
        public Name {
            if (missing != (value == null)) throw new IllegalArgumentException("Name missing/value mismatch");
            if (value != null) {
                if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("R names cannot contain NUL");
                try { StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value)); }
                catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid Unicode name", e); }
            }
        }
        public static Name literal(String value) { return new Name(false, Objects.requireNonNull(value)); }
        public static Name na() { return new Name(true, null); }
    }

    /** Explicit read capacities; the supplied Java heap must also be sufficient. */
    public record Limits(int rows, int columns, int cells, int nameUtf8Bytes, long totalNameUtf8Bytes) {
        public Limits {
            if (rows < 0 || columns < 1 || cells < 0 || nameUtf8Bytes < 1 || nameUtf8Bytes > 16777216
                    || totalNameUtf8Bytes < 0 || totalNameUtf8Bytes > 1000000000000L)
                throw new IllegalArgumentException("Invalid matrix-wire capacities");
        }
        public static Limits defaults() { return new Limits(1000000, 10000, 25000000, 1048576, 67108864); }
    }

    /** Positional provenance is retained even when the caller permits different names. */
    public record Bound(RsubreadCountMatrix matrix, RowNamesPolicy policy, List<Integer> differingInputs) {
        public Bound { Objects.requireNonNull(matrix); Objects.requireNonNull(policy); differingInputs = List.copyOf(differingInputs); }
        public boolean rowNamesDiffer() { return !differingInputs.isEmpty(); }
    }

    private final Storage storage;
    private final List<Name> rowNames;
    private final List<Name> columnNames;
    private final double[] values;

    /** Values are column-major, exactly as an R matrix; all counts must be finite and nonnegative. */
    public RsubreadCountMatrix(Storage storage, List<Name> rowNames, List<Name> columnNames, double[] columnMajor) {
        this.storage = Objects.requireNonNull(storage);
        this.rowNames = List.copyOf(rowNames); this.columnNames = List.copyOf(columnNames);
        if (columnNames.isEmpty() || (long) rowNames.size() * columnNames.size() != columnMajor.length)
            throw new IllegalArgumentException("Count matrix dimensions differ or no libraries supplied");
        this.values = columnMajor.clone();
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Nonfinite/negative original count; no matrix admitted");
            if (storage == Storage.INTEGER && (value != Math.rint(value) || value > Integer.MAX_VALUE
                    || Double.doubleToRawLongBits(value) == Long.MIN_VALUE))
                throw new IllegalArgumentException("Value does not have original nonnegative R integer storage");
        }
    }

    public Storage storage() { return storage; }
    public int rows() { return rowNames.size(); }
    public int columns() { return columnNames.size(); }
    public List<Name> rowNames() { return rowNames; }
    public List<Name> columnNames() { return columnNames; }
    public double value(int row, int column) { return values[Objects.checkIndex(column, columns()) * rows() + Objects.checkIndex(row, rows())]; }
    public double[] columnMajor() { return values.clone(); }

    /**
     * Bind whole matrices by position, as base R cbind does for named matrices.
     * REQUIRE_IDENTICAL adds an explicit safety check; RETAIN_FIRST_POSITIONAL
     * preserves R's first-matrix row names and reports every differing input.
     * Column names, including duplicates/NA, are never made unique or reordered.
     */
    public static Bound columnBind(List<RsubreadCountMatrix> inputs, RowNamesPolicy policy) {
        inputs = List.copyOf(inputs); Objects.requireNonNull(policy);
        if (inputs.isEmpty()) throw new IllegalArgumentException("No matrices supplied");
        RsubreadCountMatrix first = inputs.get(0); long columns = 0; Storage storage = Storage.INTEGER;
        var differences = new ArrayList<Integer>(); var names = new ArrayList<Name>();
        for (int i = 0; i < inputs.size(); i++) {
            RsubreadCountMatrix matrix = inputs.get(i);
            if (matrix.rows() != first.rows()) throw new IllegalArgumentException("Positional cbind requires equal row counts");
            if (!matrix.rowNames.equals(first.rowNames)) differences.add(i);
            columns += matrix.columns();
            if (columns > Integer.MAX_VALUE || columns * first.rows() > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Combined matrix exceeds Java array capacity");
            names.addAll(matrix.columnNames); if (matrix.storage == Storage.DOUBLE) storage = Storage.DOUBLE;
        }
        if (policy == RowNamesPolicy.REQUIRE_IDENTICAL && !differences.isEmpty())
            throw new IllegalArgumentException("Row names differ; explicit positional retention is required, never an automatic gene join");
        double[] values = new double[(int) columns * first.rows()]; int offset = 0;
        for (RsubreadCountMatrix matrix : inputs) { System.arraycopy(matrix.values, 0, values, offset, matrix.values.length); offset += matrix.values.length; }
        return new Bound(new RsubreadCountMatrix(storage, first.rowNames, names, values), policy, differences);
    }

    /** Lossless ASCII bridge: UTF8-hex names, explicit NA, and raw binary64 bits. Caller owns the writer. */
    public void writeWire(Writer output) throws IOException {
        output.write("RSUBREAD_MATRIX_V1\t" + storage.name() + "\t" + rows() + "\t" + columns() + "\n");
        for (Name name : rowNames) writeName(output, "R", name);
        for (Name name : columnNames) writeName(output, "C", name);
        for (double value : values) output.write("D\t" + HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value)) + "\n");
    }

    private static void writeName(Writer output, String prefix, Name name) throws IOException {
        output.write(prefix + "\t" + (name.missing ? "NA" : "S" + HexFormat.of().formatHex(name.value.getBytes(StandardCharsets.UTF_8))) + "\n");
    }

    /** Read the complete canonical wire; unknown fields, trailing content and capacity overruns fail. */
    public static RsubreadCountMatrix readWire(Reader input, Limits limits) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(limits);
        try {
            String[] header = line(input, 96).split("\t", -1);
            if (header.length != 4 || !header[0].equals("RSUBREAD_MATRIX_V1") || !header[2].matches("0|[1-9][0-9]*") || !header[3].matches("[1-9][0-9]*")) throw new IOException("Invalid matrix wire header");
            Storage storage = Storage.valueOf(header[1]); int rows = Integer.parseInt(header[2]), columns = Integer.parseInt(header[3]); long cells = (long) rows * columns;
            if (rows > limits.rows || columns > limits.columns || cells > limits.cells) throw new IOException("Matrix wire capacity exceeded; increase explicit limits and Java heap or skip");
            var rowNames = new ArrayList<Name>(rows); var columnNames = new ArrayList<Name>(columns);
            long[] remainingNameBytes = {limits.totalNameUtf8Bytes};
            for (int i = 0; i < rows; i++) rowNames.add(readName(input, "R", limits.nameUtf8Bytes, remainingNameBytes));
            for (int i = 0; i < columns; i++) columnNames.add(readName(input, "C", limits.nameUtf8Bytes, remainingNameBytes));
            double[] values = new double[(int) cells];
            for (int i = 0; i < values.length; i++) {
                String row = line(input, 18);
                if (!row.matches("D\\t[0-9a-f]{16}")) throw new IOException("Invalid raw numeric matrix value");
                values[i] = Double.longBitsToDouble(Long.parseUnsignedLong(row.substring(2), 16));
            }
            if (input.read() != -1) throw new IOException("Trailing matrix wire content");
            return new RsubreadCountMatrix(storage, rowNames, columnNames, values);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid original count matrix", e); }
    }

    private static Name readName(Reader input, String prefix, int capacity, long[] remainingBytes) throws IOException {
        String raw = line(input, 2 * capacity + 3);
        if (!raw.startsWith(prefix + "\t")) throw new IOException("Matrix name population/order differs");
        String token = raw.substring(2); if (token.equals("NA")) return Name.na();
        if (!token.matches("S[0-9a-f]*") || (token.length() - 1) % 2 != 0) throw new IOException("Invalid UTF8-hex name");
        byte[] bytes = HexFormat.of().parseHex(token.substring(1));
        if (bytes.length > capacity || bytes.length > remainingBytes[0]) throw new IOException("Matrix name capacity exceeded");
        remainingBytes[0] -= bytes.length;
        String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return Name.literal(value);
    }

    private static String line(Reader input, int capacity) throws IOException {
        var text = new StringBuilder(Math.min(64, capacity));
        for (int c; (c = input.read()) != -1;) {
            if (c == '\n') return text.toString();
            if (c > 127 || c == '\r' || text.length() >= capacity) throw new IOException("Noncanonical or over-capacity matrix wire line");
            text.append((char) c);
        }
        throw new IOException("Incomplete matrix wire population");
    }
}
