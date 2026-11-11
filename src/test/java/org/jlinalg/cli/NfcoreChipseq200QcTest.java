/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.workflow.NfcoreChipseq200Qc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.cli.NextflowFiles.*;

class NfcoreChipseq200QcTest {
    @TempDir Path temporary;
    private static byte[] resource(String name) throws IOException { try (var stream = NfcoreChipseq200QcTest.class.getResourceAsStream("/org/jlinalg/workflow/nfcore-chipseq200/" + name)) { assertNotNull(stream); return stream.readAllBytes(); } }
    private Path reference(String name) throws Exception {
        Path root = Files.createDirectory(temporary.resolve(name)); byte[] raw = resource("qc-reference.zip"); Map<?,?> source = object(SimpleJson.parse(utf8(resource("QC_SOURCE.json")))); assertEquals(fingerprint(source.get("archive"),10000000),new Pin(raw.length,sha(raw))); Map<?,?> pins = object(source.get("files")); var names = new HashSet<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(raw))) { for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) { assertFalse(entry.isDirectory()); assertTrue(names.add(entry.getName())); byte[] value = zip.readAllBytes(); assertEquals(fingerprint(pins.get(entry.getName()),10000000),new Pin(value.length,sha(value))); Path target = root.resolve(entry.getName()).normalize(); assertTrue(target.startsWith(root)); Files.createDirectories(target.getParent()); Files.write(target,value); } }
        assertEquals(pins.keySet(),names); return root;
    }
    private static NfcoreChipseq200Qc.Result audit(NfcoreChipseq200QcFiles.Facts f) { return NfcoreChipseq200Qc.audit(f.counts(),f.factors(),f.normalized(),f.transformed(),f.scores(),f.sdev(),f.fractions(),f.distances(),NfcoreChipseq200Qc.Limits.defaults()); }
    @Test void everyOriginalCountTransformPcaAndDistanceCell() throws Exception {
        var f = NfcoreChipseq200QcFiles.facts(reference("complete"),"vst"); var result = audit(f); assertEquals(1472,result.genes()); assertEquals(4,result.samples()); assertEquals(4,result.components()); assertEquals(5888,result.normalizedCells()); assertEquals(16,result.completeDistanceCells());
        for (double[] row : f.scores()) for (int pc = 0; pc < row.length; pc++) row[pc] *= -1; assertDoesNotThrow(() -> audit(f));
    }
    @Test void lateCellsMissingValuesAndIncompleteAxesRefuseCompleteAudit() throws Exception {
        Path root = reference("adverse");
        for (String mode : List.of("factor","normalized","transform","distance","score","spectrum","fraction","nonfinite","negative-count","ragged","missing-axis")) {
            var f = NfcoreChipseq200QcFiles.facts(root,"vst"); int g = f.counts().length-1,s = f.factors().length-1,p = f.sdev().length-1;
            switch (mode) { case "factor" -> f.factors()[s] *= 1.01; case "normalized" -> f.normalized()[g][s] += 1; case "transform" -> f.transformed()[g][s] += 1; case "distance" -> f.distances()[s][s] += 1; case "score" -> f.scores()[s][p] += 1; case "spectrum" -> f.sdev()[p] += 1; case "fraction" -> f.fractions()[p] += .01; case "nonfinite" -> f.transformed()[g][s] = Double.NaN; case "negative-count" -> f.counts()[g][s] = -1; case "ragged" -> f.counts()[g] = new int[s]; case "missing-axis" -> f.scores()[s] = new double[p]; default -> fail(mode); }
            assertThrows(IllegalArgumentException.class,() -> audit(f),mode);
        }
    }
}
