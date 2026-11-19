/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichr;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Native lossless interpretation of current Enrichr service responses; no statistical refit. */
public final class EnrichrResponse {
    private EnrichrResponse() { }

    /** Exact service token; use decimal() rather than narrowing small P values to double. */
    public record Numeric(String token) {
        public Numeric {
            if (token == null || token.length() > 128) throw new IllegalArgumentException("numeric token required");
            if (!List.of("Infinity", "-Infinity", "NaN").contains(token)) {
                if (!token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))
                    throw new IllegalArgumentException("invalid numeric token");
                BigDecimal n = new BigDecimal(token);
                if (Math.abs((long)n.scale()) > 10000) throw new IllegalArgumentException("numeric exponent capacity exceeded");
            }
        }
        public boolean isFinite() { return !List.of("Infinity", "-Infinity", "NaN").contains(token); }
        public BigDecimal decimal() {
            if (!isFinite()) throw new ArithmeticException("nonfinite service token: " + token);
            return new BigDecimal(token);
        }
    }
    public record Row(int rank, String term, Numeric pValue, Numeric oddsRatio,
                      Numeric combinedScore, List<String> overlappingGenes,
                      Numeric adjustedPValue, Numeric oldPValue, Numeric oldAdjustedPValue) {
        public Row { overlappingGenes = List.copyOf(overlappingGenes); }
    }
    public record Results(String library, List<Row> rows, List<String> warnings) {
        public Results { rows = List.copyOf(rows); warnings = List.copyOf(warnings); }
    }
    public record Term(String name, String description, List<String> genes) {
        public Term { genes = List.copyOf(genes); }
    }
    public record Library(String name, List<Term> terms, List<String> warnings) {
        public Library { terms = List.copyOf(terms); warnings = List.copyOf(warnings); }
    }

    public static Results results(byte[] response, String library) throws IOException {
        Map<?,?> root = object(EnrichrJson.read(response));
        require(root.size() == 1 && root.containsKey(library), "Require the exact requested library response; service error/extra keys are not results");
        List<?> raw = array(root.get(library));
        List<Row> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        HashSet<Integer> ranks = new HashSet<>();
        HashSet<String> terms = new HashSet<>();
        for (Object value : raw) {
            List<?> r = array(value);
            require(r.size() == 9, "Current Enrichr result requires exactly nine fields");
            int rank;
            try { rank = numeric(r.get(0)).decimal().intValueExact(); }
            catch (ArithmeticException | IllegalArgumentException e) { throw new IOException("Invalid service rank", e); }
            String term = text(r.get(1));
            require(rank > 0 && ranks.add(rank) && terms.add(term), "Duplicate/nonpositive rank or duplicate term");
            Numeric p = probability(r.get(2)), odds = score(r.get(3)), combined = score(r.get(4));
            Numeric adjusted = probability(r.get(6)), old = probability(r.get(7)), oldAdjusted = probability(r.get(8));
            List<String> hits = new ArrayList<>();
            for (Object hit : array(r.get(5))) hits.add(text(hit));
            if (!odds.isFinite() || !combined.isFinite()) warnings.add("Rank " + rank + ": positive infinite source score retained; no cap or replacement.");
            if (p.decimal().signum() == 0) warnings.add("Rank " + rank + ": service reported P=0; exact nonzero tail is unavailable, no floor or recalculation applied.");
            rows.add(new Row(rank, term, p, odds, combined, hits, adjusted, old, oldAdjusted));
        }
        if (rows.isEmpty()) warnings.add("The service returned an empty term family; this is not evidence of pathway absence.");
        warnings.add("Current API field four is Odds ratio. Older clients label this slot zscore; no conversion, reranking, BH adjustment or combined-score recomputation was performed. Raw server order and literal identifiers are retained.");
        return new Results(library, rows, warnings);
    }

    /** GMT is retained literally, including duplicate genes and terminal empty fields. */
    public static Library library(byte[] response, String library) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response)).toString();
        require(!text.isEmpty(), "Empty library download is not a gene-set library");
        String[] lines = text.split("\\r?\\n", -1);
        List<Term> terms = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        HashSet<String> names = new HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            if (i == lines.length - 1 && lines[i].isEmpty()) continue;
            String[] fields = lines[i].split("\\t", -1);
            require(fields.length >= 3 && !fields[0].isEmpty() && names.add(fields[0]), "Invalid/duplicate GMT term at line " + (i + 1));
            List<String> genes = List.of(fields).subList(2, fields.length);
            for (String gene : genes) require(gene.indexOf('\r') < 0 && gene.indexOf('\0') < 0, "Invalid GMT gene control character");
            if (genes.stream().anyMatch(String::isEmpty)) warnings.add("Line " + (i + 1) + ": empty GMT gene field retained literally; select and record a preparation policy before statistical use.");
            if (new HashSet<>(genes).size() != genes.size()) warnings.add("Line " + (i + 1) + ": duplicate GMT genes retained literally.");
            terms.add(new Term(fields[0], fields[1], genes));
        }
        require(!terms.isEmpty(), "No library terms");
        warnings.add("This is the downloaded current library, not a recovered historical author snapshot. No gene identity, case, version or membership was repaired.");
        return new Library(library, terms, warnings);
    }

    static Map<?,?> object(Object value) throws IOException {
        require(value instanceof Map<?,?>, "JSON object required"); return (Map<?,?>)value;
    }
    static List<?> array(Object value) throws IOException {
        require(value instanceof List<?>, "JSON array required"); return (List<?>)value;
    }
    static String text(Object value) throws IOException {
        require(value instanceof String && !((String)value).isEmpty(), "Nonempty literal string required");
        return (String)value;
    }
    private static Numeric numeric(Object value) throws IOException {
        require(value instanceof EnrichrJson.Numeric, "Numeric service field required");
        try { return new Numeric(((EnrichrJson.Numeric)value).token()); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid service numeric field", e); }
    }
    private static Numeric probability(Object value) throws IOException {
        Numeric n = numeric(value);
        require(n.isFinite() && n.decimal().signum() >= 0 && n.decimal().compareTo(BigDecimal.ONE) <= 0,
                "Invalid/nonfinite service probability; no result admitted");
        return n;
    }
    private static Numeric score(Object value) throws IOException {
        Numeric n = numeric(value);
        require(n.token().equals("Infinity") || (n.isFinite() && n.decimal().signum() >= 0),
                "Invalid source score; no result admitted");
        return n;
    }
    static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
