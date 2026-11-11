/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.NextflowFiles.*;
/** Actual original-R failure and prerequisite controls; preserve every derived faulty input. */
public final class NfcoreChipseq200QcRuntimeControl {
    private NfcoreChipseq200QcRuntimeControl() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Immutable engine result, original image declaration, private home, NEW root");
        Path root = Path.of(args[3]); Files.createDirectory(root); Path images = Path.of(args[1]), home = Path.of(args[2]); var data = new NfcoreChipseq200Files(NfcoreEngineBundle.verify(Path.of(args[0]), () -> { })); data.verifySource(); var peaks = new NfcoreChipseq200Peaks(data, NfcoreChipseq200Peaks.mappings(data)); var selected = peaks.groups.stream().filter(g -> g.multipleGroups() && g.replicates()).toList(); if (selected.size() != 1) throw new IOException("Require the complete original single-antibody fixture"); var group = selected.get(0); String prefix = group.prefix().substring(group.prefix().lastIndexOf('/') + 1), base = group.prefix().substring(0, group.prefix().lastIndexOf('/') + 1) + "deseq2/";
        Path dds = data.files.output(base + prefix + ".dds.RData"), rds = data.files.output(base + prefix + ".rds"), counts = data.files.output(group.prefix() + ".featureCounts.txt"), factors = data.files.output(base + "size_factors/" + prefix + ".size_factors.RData"); var limits = new NextflowProcess.Limits(1800000,68719476736L,1800,1000000000L,4096,17179869184L,100000,128,4096,17179869184L,100); var runtime = new NfcoreChipseq200QcRuntime(home, images, Files.createDirectory(root.resolve("original-runtime")), limits); runtime.health(); var checks = new ArrayList<Map<String,Object>>();
        for (String mode : List.of("last-count-cell", "truncated-RDS", "wrong-size-factor-object")) {
            Path caseRoot = Files.createDirectory(root.resolve("original-runtime").resolve(mode)); Path chosenCounts = counts, chosenRds = rds, chosenFactors = factors;
            if (mode.equals("last-count-cell")) { String raw = utf8(bytes(counts,67108864)); int end = raw.endsWith("\n") ? raw.length()-1 : raw.length(), tab = raw.lastIndexOf('\t',end-1); long value = Long.parseLong(raw.substring(tab+1,end)); chosenCounts = caseRoot.resolve("deliberately-changed-final-count.txt"); Files.writeString(chosenCounts,raw.substring(0,tab+1)+(value+1)+raw.substring(end)); }
            else if (mode.equals("truncated-RDS")) { chosenRds = caseRoot.resolve("deliberately-truncated-original.rds"); byte[] raw = bytes(rds,1000000000); Files.write(chosenRds,java.util.Arrays.copyOf(raw,Math.min(128,raw.length/2))); }
            else chosenFactors = dds;
            boolean refused = false;
            try { runtime.inspect(dds,chosenRds,chosenCounts,chosenFactors,"vst",caseRoot); }
            catch (IOException expected) {
                Map<?,?> process = object(json(caseRoot.resolve("process/process.json"),67108864)); String stderr = utf8(bytes(caseRoot.resolve("process/process.stderr"),1048576));
                if (integer(process.get("exit_code"),0,255) == 1 && list(process.get("owned_live_after_cleanup")).isEmpty() && !process.containsKey("cleanup_state") && (!process.containsKey("suppressed") || list(process.get("suppressed")).isEmpty()) && !Files.exists(caseRoot.resolve("worker/WORKER_COMPLETED.txt"))) {
                    refused = mode.equals("last-count-cell") ? stderr.contains("Original featureCounts/sample/rounded count facts disagree") : mode.equals("wrong-size-factor-object") ? stderr.contains("Original complete size-factor RData and DDS factors disagree") : stderr.contains("Error");
                }
            }
            if (!refused) throw new IllegalStateException("Original R failure was not cleanly retained as no result: "+mode); checks.add(Map.of("case",mode,"expected_exit_code",2,"status","PASS_ORIGINAL_R_REFUSAL_NO_RESULT"));
        }
        Path missing = Files.createDirectory(root.resolve("missing-runtime")); boolean skipped = false;
        try { new NfcoreChipseq200QcRuntime(root.resolve("NOT_INSTALLED"),images,missing,limits); } catch (NextflowProcess.Unavailable expected) { skipped = expected.getMessage().contains("missing"); }
        if (!skipped) throw new IllegalStateException("Missing private runtime was not skipped"); checks.add(Map.of("case","missing-runtime","expected_exit_code",3,"status","PASS_EXPECTED_SKIP"));
        runtime.verify(); writeJson(root.resolve("COMPLETED.json"),Map.of("status","PASS_ORIGINAL_QC_FAILURE_AND_PREREQUISITE_CONTROLS_ONLY","checks",checks,"new_tool_credit",0,"scientific_result_admitted",false)); System.out.println("PASS complete original R refusals and missing-runtime skip");
    }
}
