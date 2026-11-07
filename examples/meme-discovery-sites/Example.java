/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.MemeDiscoverySites;

/** Portable native component example with fixed, source-bound synthetic reference inputs. */
public final class Example {
    private Example() { }
    /** @param args one NEW output directory, relative to the repository root or absolute */
    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("Use Example NEW_OUTPUT_DIRECTORY from the repository root");
            Path out = Path.of(args[0]).toAbsolutePath().normalize();
            if (Files.exists(out)) throw new IllegalArgumentException("Use a NEW output directory; preserve earlier attempts");
            Path fixture = Path.of("src/test/resources/org/jlinalg/sequence/meme-discovery-sites/original-site-contracts.zip");
            if (Files.size(fixture) != 750720) throw new IllegalArgumentException("Original source-bound example fixture size differs");
            byte[] archive = Files.readAllBytes(fixture);
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)).equals("111d5adedd0dadadb08744dcf83175554864613341ec71cfdb6614d2cef0d816"))
                throw new IllegalArgumentException("Original source-bound example fixture hash differs");
            byte[] xml = null, fasta = null;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (entry.getName().equals("xml/sites-original-5.5.9-1/edge-both.xml")) xml = zip.readAllBytes();
                    if (entry.getName().equals("inputs/sites-original-5.5.9-1/captured/inputs/positive.fa")) fasta = zip.readAllBytes();
                }
            }
            if (xml == null || fasta == null) throw new IllegalArgumentException("Complete original example XML/FASTA is missing");
            // This example's checked fixture is unweighted, uppercase canonical DNA.
            // A production FASTA parser, source filtering or alias conversion is not supplied here.
            var inputs = new ArrayList<MemeDiscoverySites.Input>(); String name = null; var bases = new StringBuilder();
            for (String line : new String(fasta, StandardCharsets.US_ASCII).lines().toList()) {
                if (line.startsWith(">")) {
                    if (name != null) inputs.add(new MemeDiscoverySites.Input(name, bases.toString(), 1));
                    name = line.substring(1); bases = new StringBuilder();
                } else bases.append(line);
            }
            if (name != null) inputs.add(new MemeDiscoverySites.Input(name, bases.toString(), 1));
            // These choices are independently recorded by the reference command,
            // not inferred from its result XML or selected to improve its outcome.
            var expected = new MemeDiscoverySites.Expected("5.5.9", MemeDiscoverySites.Mode.ZOOPS, true,
                    2, 10, 10, 17, .01, new double[]{.25, .25, .25, .25}, inputs);
            Files.createDirectory(out); Path source = out.resolve("original-meme.xml"); Files.write(source, xml);
            Files.write(out.resolve("original-input.fa"), fasta);
            var audit = MemeDiscoverySites.read(source, expected, MemeDiscoverySites.Limits.defaults(), () -> { });
            long sites = audit.motifs().stream().mapToLong(m -> m.sites().size()).sum();
            long hits = audit.scans().stream().mapToLong(s -> s.independent().hits().size()).sum();
            emit(System.out, "NATIVE COMPONENT CHECKED: motifs=" + audit.motifs().size() + "; contributing-sites=" + sites
                    + "; sequence-diagrams=" + audit.scans().size() + "; selected-hits=" + hits + "; original-XML-sha256=" + audit.source().sha256());
            for (String warning : audit.warnings()) emit(System.err, "WARNING: " + warning);
            emit(System.out, "Retained exact synthetic reference inputs: " + out);
        } catch (MemeDiscoverySites.UnsupportedInput failure) {
            emit(System.err, "SKIP3 — COMPONENT AUDIT SKIPPED: " + failure.getMessage());
            emit(System.err, "Required action: retain the original inputs and use the matching edition/input/objective audit; do not normalize, replace an edition or infer missing records.");
            System.exit(3);
        } catch (Exception failure) {
            emit(System.err, "FAIL2 — NO COMPONENT RECEIPT: " + failure);
            emit(System.err, "Required action: inspect the complete fixture/input, expected settings and capacities. Preserve this attempt and retry in a NEW output. No discovery run is admitted by this example.");
            System.exit(2);
        }
    }
    private static void emit(PrintStream stream, String text) { stream.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
