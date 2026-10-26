/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Read-only REF checks against a supplied FASTA, with optional existing FAI index. */
final class StudyVariantReference implements AutoCloseable {
    private record Index(long length, long offset, long bases, long bytes) { }
    private final Map<String, Index> indexes = new LinkedHashMap<>();
    private final Map<String, String> sequences = new LinkedHashMap<>();
    private final RandomAccessFile file;
    final String sha256;
    StudyVariantReference(Path path) throws IOException {
        sha256 = FollowupSupport.hash(path);
        Path fai = path.resolveSibling(path.getFileName() + ".fai");
        if (Files.isRegularFile(fai)) {
            for (String line : Files.readAllLines(fai)) {
                String[] row = line.split("\t");
                if (row.length < 5) throw new IOException("Invalid reference FASTA index");
                try {
                    Index index = new Index(Long.parseLong(row[1]), Long.parseLong(row[2]), Long.parseLong(row[3]), Long.parseLong(row[4]));
                    if (index.length < 1 || index.offset < 0 || index.bases < 1 || index.bytes < index.bases || indexes.putIfAbsent(row[0], index) != null)
                        throw new IOException("Invalid/duplicate FASTA index entry");
                } catch (NumberFormatException invalid) { throw new IOException("Invalid numeric FASTA index", invalid); }
            }
            file = new RandomAccessFile(path.toFile(), "r");
        } else {
            file = null;
            if (Files.size(path) > 16 * 1024 * 1024) throw new IOException("A reference FASTA larger than 16 MiB requires its existing .fai index; raw reference files are never modified");
            String name = null; StringBuilder sequence = new StringBuilder();
            for (String line : Files.readAllLines(path)) {
                if (line.startsWith(">")) {
                    if (name != null && sequences.putIfAbsent(name, sequence.toString()) != null) throw new IOException("Duplicate FASTA contig");
                    name = line.substring(1).split("\\s+", 2)[0]; sequence = new StringBuilder();
                } else { if (name == null && !line.isBlank()) throw new IOException("FASTA header missing"); sequence.append(line.strip().toUpperCase(Locale.ROOT)); }
            }
            if (name != null && sequences.putIfAbsent(name, sequence.toString()) != null) throw new IOException("Duplicate FASTA contig");
        }
    }
    String check(StudyVariantUniverse.Key key) throws IOException {
        String contig = indexes.containsKey(key.chromosome()) || sequences.containsKey(key.chromosome()) ? key.chromosome() : "chr" + key.chromosome();
        String observed;
        if (file == null) {
            String sequence = sequences.get(contig); if (sequence == null) return "reference_contig_missing";
            if (key.position() > sequence.length() || key.reference().length() > sequence.length() - (key.position() - 1)) return "reference_position_unavailable";
            observed = sequence.substring((int) key.position() - 1, (int) key.position() - 1 + key.reference().length());
        } else {
            Index index = indexes.get(contig); if (index == null) return "reference_contig_missing";
            if (key.position() > index.length || key.reference().length() > index.length - (key.position() - 1)) return "reference_position_unavailable";
            StringBuilder bases = new StringBuilder();
            for (int i = 0; i < key.reference().length(); i++) {
                long coordinate = key.position() - 1 + i;
                file.seek(index.offset + coordinate / index.bases * index.bytes + coordinate % index.bases);
                int base = file.read(); if (base < 0) throw new IOException("FASTA/index length disagreement"); bases.append(Character.toUpperCase((char) base));
            }
            observed = bases.toString();
        }
        if (!observed.matches("[ACGT]+")) return "reference_sequence_ambiguous";
        return observed.equals(key.reference()) ? "reference_matched" : "reference_mismatch";
    }
    @Override public void close() throws IOException { if (file != null) file.close(); }
}
