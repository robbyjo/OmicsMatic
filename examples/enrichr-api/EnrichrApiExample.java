/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.nio.file.StandardOpenOption;
import org.jlinalg.enrichr.EnrichrClient;

/** Explicitly uploads only the eight synthetic example symbols; not a general file workflow. */
public final class EnrichrApiExample {
    private static void properties(Path path, Properties values) throws Exception {
        try (var writer = Files.newBufferedWriter(path)) { values.store(writer, "Enrichr component evidence; no author reproduction"); }
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static void capture(EnrichrClient.Session session, Path out) throws Exception {
        int index = 0;
        for (var exchange : session.exchanges()) {
            String prefix = String.format(java.util.Locale.ROOT, "%03d", ++index);
            Path metadata = out.resolve(prefix + ".properties");
            if (Files.exists(metadata)) continue;
            byte[] request = exchange.request().body(), response = exchange.reply().body();
            Files.write(out.resolve(prefix + ".request"), request);
            Files.write(out.resolve(prefix + ".response"), response);
            Properties p = new Properties();
            p.setProperty("method", exchange.request().method());
            p.setProperty("uri", exchange.request().uri().toString());
            p.setProperty("request_content_type", String.valueOf(exchange.request().contentType()));
            p.setProperty("request_sha256", hash(request)); p.setProperty("response_sha256", hash(response));
            p.setProperty("status", Integer.toString(exchange.reply().status()));
            p.setProperty("response_complete", Boolean.toString(exchange.reply().complete()));
            p.setProperty("failure", String.valueOf(exchange.reply().failure()));
            int h = 0;
            for (var header : exchange.reply().headers().entrySet())
                for (String value : header.getValue()) {
                    p.setProperty("header." + h + ".name", header.getKey());
                    p.setProperty("header." + h++ + ".value", value);
                }
            properties(metadata, p);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].equals("--allow-synthetic-upload")) {
            System.err.println("FAIL2: Usage: EnrichrApiExample NEW_OUTPUT_DIR --allow-synthetic-upload");
            System.exit(2); return;
        }
        Path out = Path.of(args[0]);
        try {Files.createDirectory(out);}
        catch (Exception e) {
            System.err.println("FAIL2: Cannot create NEW output directory: " + e + ". Preserve existing files and retry a new destination.");
            System.exit(2); return;
        }
        var session = new EnrichrClient().openSession();
        List<String> query = List.of("TP53", "BRCA1", "BRCA2", "ATM", "ATR", "CHEK1", "CHEK2", "RAD51");
        Files.write(out.resolve("query.txt"), query);
        Files.writeString(out.resolve("WARNINGS.md"), "Explicit synthetic gene-symbol upload to maayanlab.cloud. Current service/library only; no historical paper reproduction. This example tests API components, not an admitted RUN/VERIFY/REPLAY workflow. See captures; never treat an error or partial response as a complete result.\n");
        int exit = 0;
        try {
            String name = "MSigDB_Hallmark_2020";
            var names = session.libraries(); capture(session, out);
            if (!names.contains(name)) throw new MissingLibrary("Required exact example library is unavailable; no alias substitution");
            var library = session.library(name); capture(session, out);
            TreeSet<String> background = new TreeSet<>(query);
            int selectedTerms = 0;
            for (var term : library.terms()) {
                if (term.name().equals("DNA Repair") || term.name().equals("p53 Pathway")) {
                    selectedTerms++;
                    for (String gene : term.genes()) if (!gene.isEmpty()) background.add(gene);
                }
            }
            if (selectedTerms != 2) throw new IllegalStateException("Exact example library terms missing; no search/alias fallback");
            Files.write(out.resolve("background.txt"), background);
            Files.writeString(out.resolve("BACKGROUND-PREPARATION.txt"), "Synthetic background: union of the exact DNA Repair and p53 Pathway term fields, explicitly excluding empty fields and deduplicating/sorting, plus the eight literal query IDs. No paper background is inferred.\n");
            var list = session.submit(EnrichrClient.Channel.STANDARD, query, "OmicsMatic synthetic Java API component test"); capture(session, out);
            var standard = session.enrich(list, name); capture(session, out);
            var serverGenes = session.view(list); capture(session, out);
            Files.write(out.resolve("server-prepared-genes.txt"), serverGenes);
            var customList = session.submit(EnrichrClient.Channel.CUSTOM_BACKGROUND, query, "OmicsMatic synthetic Java API component test"); capture(session, out);
            var bg = session.background(List.copyOf(background)); capture(session, out);
            var custom = session.enrich(customList, bg, name); capture(session, out);
            Properties summary = new Properties();
            summary.setProperty("library", name); summary.setProperty("library_terms", Integer.toString(library.terms().size()));
            summary.setProperty("standard_rows", Integer.toString(standard.rows().size()));
            summary.setProperty("background_rows", Integer.toString(custom.rows().size()));
            summary.setProperty("background_genes", Integer.toString(background.size()));
            summary.setProperty("query_genes", Integer.toString(query.size()));
            summary.setProperty("workflow_qualified", "false"); summary.setProperty("historical_author_execution", "false");
            properties(out.resolve("COMPONENT-PROBE-COMPLETED.properties"), summary);
            System.out.println("Current Enrichr component probe completed; standard=" + standard.rows().size()
                + ", background=" + custom.rows().size() + ", library terms=" + library.terms().size() + ". Inspect exact captures and warnings.");
        } catch (Exception failure) {
            exit = failure instanceof EnrichrClient.Failure f ? f.suggestedExitCode()
                : failure instanceof MissingLibrary ? 3 : 2;
            String action = exit == 3 ? "SKIP3: Analysis skipped because a required exact service/library prerequisite is unavailable. Restore permitted HTTPS access and the exact named library, or retain this skip."
                : "FAIL2: No complete result. Preserve the captured inputs, partial replies and errors; correct invalid data/protocol/capacity or interruption and retry NEW output. No identifier, probability or algorithm repair is performed.";
            String notice = action + "\nCause: " + failure + "\nNo completed workflow or author-paper result is admitted.\n";
            Files.writeString(out.resolve("WARNINGS.md"), notice, StandardOpenOption.APPEND);
            Files.writeString(out.resolve("FAILURE.txt"), notice);
            System.err.print(notice);
        } finally { capture(session, out); }
        if (exit != 0) System.exit(exit);
    }
    private static final class MissingLibrary extends Exception {
        MissingLibrary(String message) { super(message); }
    }
}
