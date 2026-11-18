/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.workflow.NfcoreAtacseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Full original ATAC source, sample mapping, source branch counts and required published outputs.
 * Numerical model fitting, annotation and ataqv metrics remain upstream operations.
 */
final class NfcoreAtacseq121Audit {
    private NfcoreAtacseq121Audit() { }
    static Map<String, Object> read(NfcoreEngineBundle.Verified engine) throws IOException {
        var files = new NfcoreScientificFiles(engine); String workflow = files.alias("workflow");
        var expectedFiles = new HashSet<String>();
        for (var row : object(NfcoreAtacseq121Request.sourceLock().get("files")).entrySet()) {
            String name = member(text(row.getKey())); expectedFiles.add(workflow+"/"+name);
            if (!pin(files.inputMember(workflow+"/"+name), 1000000000L, () -> { }).equals(fingerprint(row.getValue(), 1000000000L))) throw new IOException("Captured original ATAC source differs: "+name);
        }
        expectedFiles.add(workflow+"/runtime/host.config");
        var capture = NextflowCapture.decode(json(engine.attempt().resolve("capture.json"), 67108864), new NextflowCapture.Limits(1000000, 1000000000000L, 1000000000000L));
        var actualFiles = new HashSet<String>();
        for (var root : capture.roots()) for (var row : root.items().entrySet()) {
            String name = root.key()+"/"+root.leaf()+(row.getKey().isEmpty() ? "" : "/"+row.getKey());
            if (!name.startsWith(workflow+"/") || row.getValue().kind().equals("directory")) continue;
            if (!row.getValue().kind().equals("file")) throw new IOException("Captured ATAC source contains a link or nonregular code member");
            actualFiles.add(name);
        }
        if (!actualFiles.equals(expectedFiles)) throw new IOException("Captured complete original ATAC source file census differs");
        Map<?, ?> parameters = files.parameters();
        String sourceDesign = utf8(bytes(files.inputMember(files.alias("design")), 67108864));
        var design = NfcoreAtacseq121Mappings.read(new StringReader(sourceDesign), NfcoreAtacseq121Mappings.Limits.defaults());
        for (var row : design.reads()) {
            files.inputPath(row.read1());
            if (!row.read2().isEmpty()) {
                files.inputPath(row.read2());
                if (files.sameInputFile(row.read1(), row.read2())) throw new IOException("Original paired FASTQ paths resolve to one physical captured input file: "+row.sampleId());
            }
        }
        if (!design.checkedCsv().equals(utf8(bytes(files.output("pipeline_info/design_reads.csv"), 67108864)))) throw new IOException("Complete original checked sample mapping differs from the independent Java reconstruction");
        if (design.paired() == Boolean.TRUE.equals(parameters.get("single_end"))) throw new IOException("Saved source mode differs from complete sample design");
        var expectedTasks = expectedTasks(design, parameters);
        var observed = trace(engine.attempt().resolve("trace.tsv"));
        if (!expectedTasks.equals(observed)) throw new IOException("Original ATAC task population differs from complete selected source branches: expected="+expectedTasks+", observed="+observed);
        String peak = Boolean.TRUE.equals(parameters.get("narrow_peak")) ? "narrowPeak" : "broadPeak";
        for (String library : design.libraries()) {
            required(files, "bwa/mergedLibrary/"+library+".mLb.clN.sorted.bam");
            files.output("bwa/mergedLibrary/macs/"+peak+"/"+library+".mLb.clN_peaks."+peak); // Zero called peaks is retained when the original full workflow completes.
            Path ataqv = files.output("bwa/mergedLibrary/ataqv/"+peak+"/"+library+".mLb.clN.ataqv.json");
            if (list(json(ataqv, 67108864)).isEmpty()) throw new IOException("Original per-library ataqv JSON contains no metric object");
        }
        if (design.replicatesExist()) for (String group : design.groups()) {
            required(files, "bwa/mergedReplicate/"+group+".mRp.clN.sorted.bam");
            files.output("bwa/mergedReplicate/macs/"+peak+"/"+group+".mRp.clN_peaks."+peak);
        }
        if (design.replicatesExist() || design.multipleGroups()) consensus(files, "mergedLibrary", "mLb", peak, design.replicatesExist() && design.multipleGroups());
        if (design.replicatesExist() && design.multipleGroups()) consensus(files, "mergedReplicate", "mRp", peak, true);
        required(files, "multiqc/"+peak+"/multiqc_report.html");
        return Map.ofEntries(Map.entry("pipeline_profile", NfcoreAtacseq121Request.PROFILE), Map.entry("source_commit", "1b3a832db5a53c92c2c89a4d6d79455e860461ad"),
            Map.entry("original_source_files", 86), Map.entry("complete_design", design.document()), Map.entry("source_task_counts", observed),
            Map.entry("original_engine_manifest", engine.manifest()), Map.entry("complete_published_files", engine.outputFiles()), Map.entry("complete_published_bytes", engine.outputBytes()),
            Map.entry("status", "complete-original-atac-execution-and-selected-output-contracts"), Map.entry("numerical_models_independently_reimplemented", false),
            Map.entry("whole_paper_reproduced", false), Map.entry("biological_accuracy_certified", false), Map.entry("unreported_author_engine_inferred", false));
    }
    private static void required(NfcoreScientificFiles files, String name) throws IOException {
        if (pin(files.output(name), 1000000000000L, () -> { }).bytes() == 0) throw new IOException("Required original ATAC output is empty: "+name);
    }
    private static void consensus(NfcoreScientificFiles files, String level, String suffix, String peak, boolean differential) throws IOException {
        String base = "bwa/"+level+"/macs/"+peak+"/consensus/", prefix = "consensus_peaks."+suffix+".clN";
        for (String extension : List.of(".bed", ".saf", ".boolean.txt", ".featureCounts.txt", ".featureCounts.txt.summary", ".annotatePeaks.txt", ".boolean.annotatePeaks.txt")) required(files, base+prefix+extension);
        // The source publishes MultiQC-wrapped tables; raw PCA/distance .txt intermediates remain in its task cache.
        if (differential) for (String extension : List.of(".dds.rld.RData", ".results.txt", ".pca.vals_mqc.tsv", ".sample.dists_mqc.tsv")) required(files, base+"deseq2/"+prefix+extension);
    }
    static Map<String, Integer> expectedTasks(NfcoreAtacseq121Mappings.Design design, Map<?, ?> parameters) {
        var result = new TreeMap<String, Integer>(); int technical = design.reads().size(), libraries = design.libraries().size(), groups = design.groups().size();
        for (String name : List.of("CHECK_DESIGN", "MAKE_GENOME_FILTER", "MERGED_LIB_MACS2_QC", "MERGED_LIB_ATAQV_MKARV", "IGV", "get_software_versions", "MULTIQC", "output_documentation")) result.put(name, 1);
        for (var row : Map.of("bwa_index", "BWA_INDEX", "gene_bed", "MAKE_GENE_BED", "tss_bed", "MAKE_TSS_BED").entrySet()) if (!parameters.containsKey(row.getKey())) result.put(row.getValue(), 1);
        for (String name : List.of("FASTQC", "TRIMGALORE", "BWA_MEM", "SORT_BAM")) result.put(name, technical);
        for (String name : List.of("MERGED_LIB_BAM", "MERGED_LIB_BAM_FILTER", "MERGED_LIB_PRESEQ", "MERGED_LIB_PICARD_METRICS", "MERGED_LIB_BIGWIG", "MERGED_LIB_PLOTPROFILE", "MERGED_LIB_PLOTFINGERPRINT", "MERGED_LIB_MACS2", "MERGED_LIB_MACS2_ANNOTATE", "MERGED_LIB_ATAQV")) result.put(name, libraries);
        if (design.paired()) result.put("MERGED_LIB_BAM_REMOVE_ORPHAN", libraries);
        if (design.replicatesExist() || design.multipleGroups()) for (String name : List.of("MERGED_LIB_CONSENSUS", "MERGED_LIB_CONSENSUS_ANNOTATE", "MERGED_LIB_CONSENSUS_COUNTS")) result.put(name, 1);
        if (design.replicatesExist() && design.multipleGroups()) result.put("MERGED_LIB_CONSENSUS_DESEQ2", 1);
        if (design.replicatesExist()) {
            for (String name : List.of("MERGED_REP_BAM", "MERGED_REP_BIGWIG", "MERGED_REP_MACS2", "MERGED_REP_MACS2_ANNOTATE")) result.put(name, groups);
            result.put("MERGED_REP_MACS2_QC", 1);
            if (design.multipleGroups()) for (String name : List.of("MERGED_REP_CONSENSUS", "MERGED_REP_CONSENSUS_ANNOTATE", "MERGED_REP_CONSENSUS_COUNTS", "MERGED_REP_CONSENSUS_DESEQ2")) result.put(name, 1);
        }
        return Map.copyOf(result);
    }
    private static Map<String, Integer> trace(Path file) throws IOException {
        String[] lines = utf8(bytes(file, 67108864)).split("\n"); String[] header = lines[0].split("\t", -1);
        int process = Arrays.asList(header).indexOf("process"), status = Arrays.asList(header).indexOf("status"), exit = Arrays.asList(header).indexOf("exit");
        if (process < 0 || status < 0 || exit < 0) throw new IOException("Original ATAC trace lacks required source process/status/exit columns");
        var result = new TreeMap<String, Integer>();
        for (int i = 1; i < lines.length; i++) {
            String[] cells = lines[i].split("\t", -1);
            if (cells.length != header.length || !List.of("COMPLETED", "CACHED").contains(cells[status]) || !cells[exit].equals("0")) throw new IOException("Original ATAC trace has incomplete, failed or unassessable tasks");
            result.merge(cells[process], 1, Math::addExact);
        }
        return result;
    }
}
