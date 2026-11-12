/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

/** Original 0.5.2 API only: complete FASTA intake and the actual DNA5 lookup table. */
public final class Malt052FastaSourceProbe {
    private Malt052FastaSourceProbe() { }
    private static String hex(byte[] bytes) {
        var result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Complete ordered FASTA path list and NEW output required");
        List<String> inputs = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        if (inputs.isEmpty() || inputs.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Complete nonempty path list required");
        Path out = Path.of(args[1]);
        Files.createDirectory(out);
        var alphabet = malt.data.DNA5.getInstance();
        var table = new StringBuilder("ascii\tnormalized_ascii\n");
        for (int i = 0; i < 127; i++) table.append(i).append('\t').append((int) alphabet.getNormalized((byte) i)).append('\n');
        Files.writeString(out.resolve("dna5.tsv"), table, StandardCharsets.UTF_8);
        var references = new malt.data.ReferencesDBBuilder();
        references.loadFastAFiles(inputs, alphabet);
        var rows = new StringBuilder("ordinal\theader_base64\tbases\tsequence_sha256\n");
        for (int i = 0; i < references.getNumberOfSequences(); i++) {
            byte[] header = references.getHeader(i), sequence = references.getSequence(i);
            rows.append(i).append('\t').append(Base64.getEncoder().encodeToString(header)).append('\t')
                .append(sequence.length).append('\t').append(hex(MessageDigest.getInstance("SHA-256").digest(sequence))).append('\n');
            Files.write(out.resolve("sequence-" + i + ".bin"), sequence);
            Files.write(out.resolve("header-" + i + ".bin"), header);
        }
        Files.writeString(out.resolve("references.tsv"), rows, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("counts.tsv"), "references\tbases\n" + references.getNumberOfSequences() + "\t" + references.getNumberOfLetters() + "\n", StandardCharsets.UTF_8);
    }
}
