/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.singlecell.VelociraptorGrid120;

/** Complete binary interoperability probe; preserves original FP64 fixture bits. */
public final class VelociraptorGrid120Probe {
    private VelociraptorGrid120Probe() { }

    private static double[][] read(Path path) throws IOException {
        long bytes = Files.size(path);
        if (bytes == 0 || bytes % 16 != 0 || bytes > 16777216) throw new IOException("Complete bounded two-column binary input required");
        double[][] value = new double[(int) (bytes / 16)][2];
        try (DataInputStream stream = new DataInputStream(Files.newInputStream(path))) {
            for (double[] row : value) for (int axis = 0; axis < 2; axis++) row[axis] = Double.longBitsToDouble(Long.reverseBytes(stream.readLong()));
            if (stream.read() != -1) throw new IOException("Unconsumed original input bytes");
        }
        return value;
    }

    private interface Floating { double at(int row, int column); }
    private interface Integral { int at(int row, int column); }

    private static void floating(Path path, int rows, int columns, Floating value) throws IOException {
        try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(path))) {
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) stream.writeLong(Long.reverseBytes(Double.doubleToRawLongBits(value.at(row, column))));
        }
    }
    private static void integral(Path path, int rows, int columns, Integral value) throws IOException {
        try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(path))) {
            for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) stream.writeInt(Integer.reverseBytes(value.at(row, column)));
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException("input-directory NEW-output-directory resolution scale");
        Path input = Path.of(args[0]);
        Path out = Path.of(args[1]);
        if (!args[3].equals("true") && !args[3].equals("false")) throw new IllegalArgumentException("Explicit true/false scaling required");
        double[][] x = read(input.resolve("coordinates.bin"));
        double[][] vectors = read(input.resolve("embedded.bin"));
        VelociraptorGrid120.Result result = VelociraptorGrid120.grid(x, vectors, Integer.parseInt(args[2]), Boolean.parseBoolean(args[3]));
        Files.createDirectory(out);
        floating(out.resolve("start.bin"), result.bins(), 2, result::start);
        floating(out.resolve("end.bin"), result.bins(), 2, result::end);
        floating(out.resolve("vectors.bin"), result.bins(), 2, result::vector);
        floating(out.resolve("limits.bin"), 2, 2, result::limit);
        floating(out.resolve("delta.bin"), 2, 1, (row, column) -> result.delta(row));
        integral(out.resolve("categories.bin"), result.cells(), 2, result::category);
        integral(out.resolve("groups.bin"), result.cells(), 1, (row, column) -> result.groupOneBased(row));
        integral(out.resolve("group_labels.bin"), result.bins(), 1, (row, column) -> result.groupLabelOneBased(row));
        integral(out.resolve("populations.bin"), result.bins(), 1, (row, column) -> result.population(row));
        Files.writeString(out.resolve("RESULT.json"), "{\"status\":\"COMPLETE_NATIVE_GRID\",\"cells\":" + result.cells()
                + ",\"bins\":" + result.bins() + ",\"resolution\":" + result.resolution() + ",\"scaled\":" + result.scaled()
                + ",\"requires_nonfinite_review\":" + result.requiresNonFiniteReview() + "}\n");
    }
}
