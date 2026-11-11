/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.jlinalg.sequence.MemeDiscoverySites;
import org.jlinalg.sequence.MemeXml;

/** Source-controlled empty-result extension; the validated nonempty component stays frozen. */
final class MemeDiscoveryEmpty {
    private MemeDiscoveryEmpty() { }
    private static final String EMPTY_COMPONENT = "Empty original MEME discovery populations need separately captured complete source controls";

    static MemeDiscoverySites.Audit read(Path xml, MemeDiscoveryRequest.Request request,
                                        MemeDiscoveryInputs.Captured inputs, Runnable check) throws IOException {
        var before = NextflowFiles.pin(xml, request.limit("xml_bytes"), check);
        try {
            return MemeDiscoverySites.read(xml, inputs.expected(), request.outputLimits(), check);
        } catch (MemeDiscoverySites.UnsupportedInput unsupported) {
            // This exact terminal branch follows ALL of the frozen reader's input,
            // settings, training-population, alphabet and background checks.
            // Other unsupported domains must never be admitted through this path.
            if (!EMPTY_COMPONENT.equals(unsupported.getMessage())) throw unsupported;
        }
        var document = MemeXml.read(xml, request.outputLimits().xml(), check);
        require(before.bytes() == document.bytes() && before.sha256().equals(document.sha256()), "Original empty MEME XML changed during validation");
        var root = document.root();
        require(root.children().stream().map(MemeXml.Node::name).toList().equals(List.of("training_set", "model", "motifs"))
            && root.one("motifs").children().isEmpty() && root.one("motifs").text().isBlank(),
            "Empty MEME output must have the complete source empty namespace; no sequence diagrams were produced");
        require(request.maximumEvalue() != null, "This empty MEME result requires the separately source-controlled E-value stopping path; retain other empty results for their own validation");
        String reason = root.one("model").one("reason_for_stopping").text().strip();
        String prefix = request.legacy() ? "Stopped because motif E-value > " : "Stopped because the next motif E-value > ";
        require(reason.startsWith(prefix) && reason.endsWith("."), "Empty MEME stopping reason differs from the validated source path");
        String token = reason.substring(prefix.length(), reason.length() - 1);
        require(token.matches("[0-9]\\.[0-9]{2}e[+-][0-9]{2,3}"), "Invalid source empty MEME stopping threshold");
        BigDecimal printed = new BigDecimal(token), expected = request.maximumEvalue();
        BigDecimal quantum = BigDecimal.ONE.scaleByPowerOfTen(expected.precision() - expected.scale() - 3);
        require(printed.subtract(expected).abs().compareTo(quantum.divide(BigDecimal.valueOf(2)).add(expected.abs().multiply(new BigDecimal("1e-10")))) <= 0,
            "Empty MEME stopping threshold differs from the explicit request");
        require(before.equals(NextflowFiles.pin(xml, request.limit("xml_bytes"), check)), "Original empty MEME XML changed during validation");
        long operations = Math.addExact(inputs.population().bases(), inputs.population().records().size());
        return new MemeDiscoverySites.Audit(document, inputs.expected(), List.of(), List.of(), operations,
            List.of("The complete original program accepted zero motifs at the explicit E-value cutoff. It produced no motif matrices, contributing sites or sequence diagrams. This is not evidence that binding sites or biological signals are absent."));
    }

    static void text(String text, MemeDiscoverySites.Audit audit) throws IOException {
        String reason = audit.source().root().one("model").one("reason_for_stopping").text().strip();
        require(text.contains("MEME version " + audit.expected().version()) && text.lines().filter(reason::equals).count() == 1
            && text.stripTrailing().endsWith("*".repeat(80)) && Pattern.compile("(?m)^CPU: .+$").matcher(text).find(),
            "Original empty MEME text carrier is incomplete or its stopping reason differs from XML");
        require(!Pattern.compile("(?m)^\\s*(?:MOTIF\\s|letter-probability matrix:|log-odds matrix:)").matcher(text).find(),
            "Original empty MEME text contains a motif or matrix absent from XML");
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
