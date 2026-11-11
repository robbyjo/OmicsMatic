/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Original v14 reverse tables, including its table-10 defect; see bundled MIT notice. */
final class Pal2NalCodes {
    private Pal2NalCodes() { }
    private static final Set<Integer> IDS = Set.of(1,2,3,4,5,6,9,10,11,12,13,14,15,16,21,22,23);
    private static final Map<Integer,Map<Character,Pattern>> TABLES = load();
    private static final Pattern FALLBACK_START = Pattern.compile("((A|C|G|R)TG)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPPERCASE_STOP = Pattern.compile("(((U|T)A(A|G|R))|((T|U)GA))");
    static Set<Integer> tableIds() { return IDS; }
    static void requireTable(int id) {
        if (!IDS.contains(id)) throw new IllegalArgumentException("PAL2NAL v14 has no declared genetic-code table " + id);
    }
    static boolean matches(int table, char residue, String triplet) {
        requireTable(table);
        Pattern pattern = TABLES.get(table).get(residue);
        if (pattern == null) throw new IllegalArgumentException("No original v14 pattern for residue " + residue);
        return triplet.length() == 3 && pattern.matcher(triplet).matches();
    }
    static boolean matchesFallbackStart(String triplet) { return FALLBACK_START.matcher(triplet).matches(); }
    static boolean matchesUniversalUppercaseStop(String triplet) { return UPPERCASE_STOP.matcher(triplet).matches(); }
    private static Map<Integer,Map<Character,Pattern>> load() {
        try (var stream = Pal2NalCodes.class.getResourceAsStream("/org/jlinalg/omics/pal2nal-v14-codes.tsv")) {
            if (stream == null) throw new IOException("Missing original genetic-code patterns");
            byte[] raw = stream.readNBytes(65537);
            if (raw.length > 65536 || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)).equals("b536bb1df3c2d358254a508449728d25fe5afac43487f651a4e25870deccde9a"))
                throw new IOException("Original genetic-code pattern integrity check failed");
            var tables = new HashMap<Integer,Map<Character,Pattern>>();
            for (String line : new String(raw, StandardCharsets.US_ASCII).split("\n")) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 3 || fields[1].length() != 1 || fields[2].length() > 1024)
                    throw new IOException("Malformed original genetic-code pattern record");
                int id = Integer.parseInt(fields[0]); requireTable(id);
                var row = tables.computeIfAbsent(id, ignored -> new HashMap<>());
                if (row.putIfAbsent(fields[1].charAt(0), Pattern.compile(fields[2], Pattern.CASE_INSENSITIVE)) != null)
                    throw new IOException("Duplicate original genetic-code pattern");
            }
            if (!tables.keySet().equals(IDS)) throw new IOException("Incomplete original genetic-code tables");
            var expected = new HashSet<Character>();
            for (char c : "BLRSAGPTVI_*CDEFHKNQYMWX".toCharArray()) expected.add(c);
            for (var entry : tables.entrySet()) {
                if (!entry.getValue().keySet().equals(expected)) throw new IOException("Incomplete original residue patterns");
                entry.setValue(Map.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(tables);
        } catch (IOException | NoSuchAlgorithmException e) { throw new ExceptionInInitializerError(e); }
    }
}
