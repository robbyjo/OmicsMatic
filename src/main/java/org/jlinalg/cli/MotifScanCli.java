/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.DnaMotifScanner;
import org.jlinalg.sequence.DnaPssm;
import org.jlinalg.sequence.MemeDnaMotifs;
import static org.jlinalg.cli.FollowupSupport.*;

/** Audited native DNA motif scanning with explicit background/strand/coordinate choices. */
final class MotifScanCli {
    private MotifScanCli() { }
    private static final Set<String> ALLOWED = Set.of("motifs","fasta","out","background","strands","coordinates",
            "threshold","pseudocount","normalize-probabilities","invalid-residues","maximum-hits","maximum-residues",
            "workspace-mib","maximum-convolution-operations","options-file");
    private static final List<String> PRODUCTS = List.of("results.tsv","model-metadata.tsv","sequence-metadata.tsv","scan-counts.tsv");
    private static final String RECOVERY = "Required action: correct the named input or resource limit and rerun to a new output directory. Native scanning requires no installation. Supply minimal MEME DNA probability matrices and FASTA; explicitly select background, strands and coordinates. Native results contain p-values, without q-values or position-specific priors. For a q-value analysis use a separately validated original FIMO runtime. Failed scientific streams are retained under partial/ and must not be used as successful results.";

    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (arguments.length == 1 && arguments[0].equals("--help")) { output.println(help()); return 0; }
        try {
            var o = parse(arguments); required(o,"out"); int[] code = {0}; boolean[] warned = {false};
            transaction(o,"motif-scan-native",(directory,manifest) -> {
                var session = new LinkedHashMap<String,Object>(); var warnings = new ArrayList<String>();
                session.put("engine","native-java"); session.put("reference_convention","MEME FIMO 5.5.9 DNA PSSM and p-value scanning subset");
                session.put("statistical_scope","Zero-order independent-letter lattice tails. No q-values, PSPs, max-strand tie sampling, stored-hit truncation, motif discovery, custom P007 empirical calibration, protein/custom alphabets or complete upstream file-format parity.");
                try {
                    Path motifs = source(o,"motifs"), fasta = source(o,"fasta");
                    var hashes = new LinkedHashMap<String,String>(); hashes.put("motifs",hash(motifs)); hashes.put("fasta",hash(fasta));
                    if (o.containsKey("options-file")) { Path recipe = source(o,"options-file"); hashes.put("options-file",hash(recipe)); Files.copy(recipe,directory.resolve("options-input.json")); }
                    manifest.put("input_sha256",hashes);
                    String strands = required(o,"strands"), coordinates = required(o,"coordinates"), invalid = o.getOrDefault("invalid-residues","reject");
                    if (!Set.of("both","forward").contains(strands)) throw new IllegalArgumentException("--strands must be both or forward");
                    if (!Set.of("genomic","sequence").contains(coordinates)) throw new IllegalArgumentException("--coordinates must be genomic or sequence");
                    if (!Set.of("reject","convert-n").contains(invalid)) throw new IllegalArgumentException("--invalid-residues must be reject or convert-n");
                    boolean both = strands.equals("both"), normalize = bool(o,"normalize-probabilities",false);
                    var document = MemeDnaMotifs.read(motifs,normalize); double[] supplied = background(required(o,"background"),document);
                    double backgroundSum = 0;
                    for (double p : supplied) { if (!Double.isFinite(p) || p <= 0 || p > 1) throw new IllegalArgumentException("Supplied background must contain four positive finite probabilities before strand averaging"); backgroundSum += p; }
                    if (Math.abs(backgroundSum - 1) > 1e-12) throw new IllegalArgumentException("Supplied background probabilities must sum to one before strand averaging");
                    double[] effective = DnaPssm.fimoScanBackground(supplied,both);
                    double pseudocount = number(o,"pseudocount",.1), threshold = number(o,"threshold",1e-4);
                    long maxHits = positiveLong(o,"maximum-hits",1_000_000), maxResidues = positiveLong(o,"maximum-residues",10_000_000_000L);
                    var scanOptions = new DnaMotifScanner.Options(both,coordinates.equals("genomic"),threshold,maxHits,maxResidues,invalid.equals("convert-n"));
                    var limits = new DnaPssm.Limits(Math.multiplyExact(1_048_576L,positiveLong(o,"workspace-mib",256)),positiveLong(o,"maximum-convolution-operations",1_000_000_000));
                    session.put("supplied_background_acgt",supplied);session.put("effective_background_acgt",effective);
                    session.put("complement_background_averaging",both);session.put("declared_motif_discovery_strands",document.declaredStrands());
                    session.put("scan_background_regularization_per_base",5e-7);session.put("background_resolution_order","Explicit input validation, optional complement averaging, add 5e-7 per base and normalize; separate from the motif pseudocount");
                    session.put("scanning_strands",strands);session.put("coordinate_policy",coordinates);session.put("threshold_inclusive",threshold);
                    session.put("pseudocount",pseudocount);session.put("maximum_hits_per_motif",maxHits);session.put("maximum_residues_per_pass",maxResidues);
                    session.put("maximum_workspace_bytes",limits.maximumWorkspaceBytes());session.put("maximum_convolution_operations",limits.maximumConvolutionOperations());
                    if (document.normalizedBackground()) warnings.add("The declared motif background was explicitly normalized. Required action: review the original file and resolved background in session.yaml before interpreting scores.");
                    try (var results = Files.newBufferedWriter(directory.resolve("results.tsv")); var metadata = Files.newBufferedWriter(directory.resolve("model-metadata.tsv"));
                         var identities = Files.newBufferedWriter(directory.resolve("sequence-metadata.tsv")); var counts = Files.newBufferedWriter(directory.resolve("scan-counts.tsv"))) {
                        results.write("motif_id\tmotif_alt_id\tsequence_name\tstart\tstop\tstrand\tscore\tp-value\tq-value\tmatched_sequence\tsequence_ordinal\tcontinuous_log_odds\tinteger_score\n");
                        metadata.write("motif_id\twidth\tnsites\tpseudocount\tscale\toffset\tnormalized_rows\n");
                        identities.write("motif_id\tordinal\toriginal_id_json\tsequence_name_json\theader_json\tcoordinate_start\tdeclared_end\tobserved_length\n");
                        counts.write("motif_id\tsequences\tresidues\twindows\tambiguous_windows\tscored_sites\thits\turacil_aliases\tambiguity_aliases\tconverted_invalid\tcoordinate_span_mismatches\n");
                        for (var motif : document.motifs()) {
                            var model = DnaPssm.build(motif.frequencies(),effective,motif.sites(),pseudocount,limits);
                            metadata.write(motif.id()+"\t"+model.width()+"\t"+motif.sites()+"\t"+pseudocount+"\t"+model.scale()+"\t"+model.offset()+"\t"+motif.normalizedRows()+"\n");
                            var summary = DnaMotifScanner.scan(fasta,model,scanOptions,hit -> {
                                var s = hit.score(); results.write(motif.id()+"\t"+motif.alternateId()+"\t"+hit.sequenceName()+"\t"+hit.start()+"\t"+hit.stop()+"\t"+hit.strand()+"\t"+s.logOdds()+"\t"+s.pValue()+"\t\t"+hit.word()+"\t"+hit.sequenceOrdinal()+"\t"+s.continuousLogOdds()+"\t"+s.integerScore()+"\n");
                            },seq -> identities.write(motif.id()+"\t"+seq.ordinal()+"\t"+RegenieRuntime.json(seq.originalId())+"\t"+RegenieRuntime.json(seq.name())+"\t"+RegenieRuntime.json(seq.header())+"\t"+seq.start()+"\t"+seq.declaredEnd()+"\t"+seq.residues()+"\n"));
                            counts.write(motif.id()+"\t"+summary.sequences()+"\t"+summary.residues()+"\t"+summary.windows()+"\t"+summary.ambiguousWindows()+"\t"+summary.scoredSites()+"\t"+summary.hits()+"\t"+summary.uracilAliases()+"\t"+summary.ambiguityAliases()+"\t"+summary.convertedInvalid()+"\t"+summary.coordinateSpanMismatches()+"\n");
                            if (motif.normalizedRows() != 0) warnings.add(motif.id()+": explicitly normalized "+motif.normalizedRows()+" probability rows. Required action: review model-metadata.tsv and input provenance; these resolved probabilities determine all scores.");
                            if (summary.uracilAliases()+summary.ambiguityAliases()+summary.convertedInvalid() != 0) warnings.add(motif.id()+": mapped U/u to T/t="+summary.uracilAliases()+", X/x/dot to N="+summary.ambiguityAliases()+", explicitly converted invalid residues to N="+summary.convertedInvalid()+". Ambiguous windows were excluded. Required action: review scan-counts.tsv and the original FASTA before downstream analysis.");
                            if (summary.coordinateSpanMismatches() != 0) warnings.add(motif.id()+": "+summary.coordinateSpanMismatches()+" FASTA genomic header spans differ from observed lengths. The parsed start anchors coordinates. Required action: inspect sequence-metadata.tsv and correct headers or choose sequence coordinates before interpreting genomic locations.");
                        }
                    }
                    if (!hashes.get("motifs").equals(hash(motifs)) || !hashes.get("fasta").equals(hash(fasta))) throw new IOException("Input changed during analysis; results cannot be published");
                    warned[0] = !warnings.isEmpty(); String state = warned[0] ? "completed-with-qc-warnings" : "completed";
                    manifest.put("analysis_status",state);status(directory,state);
                    if (warned[0]) Files.writeString(directory.resolve("WARNINGS.md"),"# MOTIF SCAN WARNINGS\n\n"+String.join("\n\n",warnings)+"\n");
                } catch (Exception failure) {
                    code[0] = 2; manifest.put("analysis_status","failed");manifest.put("error",failure.toString());session.put("error",failure.toString());
                    for (String name : PRODUCTS) if (Files.exists(directory.resolve(name))) { Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve(name),directory.resolve("partial").resolve(name)); }
                    status(directory,"failed");Files.writeString(directory.resolve("WARNINGS.md"),"# MOTIF ANALYSIS FAILED\n\n"+failure+"\n\n"+RECOVERY+"\n");
                }
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if (code[0] != 0) error.println("MOTIF ANALYSIS FAILED: see "+Path.of(o.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");
            else { output.println("Native motif scanning completed: "+o.get("out")); if (warned[0]) error.println("MOTIF SCAN WARNINGS: review "+Path.of(o.get("out")).resolve("WARNINGS.md")+" before downstream analysis."); }
            return code[0];
        } catch (Exception failure) { error.println("MOTIF ANALYSIS NOT STARTED: "+failure.getMessage()+". "+RECOVERY+" Review motif-scan --help.");return 2; }
    }
    private static Map<String,String> parse(String[] arguments) throws Exception {
        var direct = options(arguments,ALLOWED); if (!direct.containsKey("options-file")) return direct;
        Path recipe = source(direct,"options-file"); if (Files.size(recipe)>1_048_576) throw new IOException("Options recipe exceeds 1 MiB");
        Object parsed = SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(recipe))).toString());
        if (!(parsed instanceof Map<?,?> values)) throw new IllegalArgumentException("Require a UTF-8 JSON object of option:string values");
        var effective = new LinkedHashMap<String,String>();
        for (var entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!ALLOWED.contains(key) || key.equals("options-file") || !(entry.getValue() instanceof String text)) throw new IllegalArgumentException("Unknown, recursive or non-string recipe option: "+key);
            effective.put(key,text);
        }
        effective.putAll(direct);return effective;
    }
    private static Path source(Map<String,String> o,String key) throws IOException {
        Path path = Path.of(required(o,key)).toAbsolutePath().normalize();if (!Files.isRegularFile(path)) throw new IOException("Input file unavailable: "+path);return path;
    }
    private static long positiveLong(Map<String,String> o,String key,long fallback) {
        long value = Long.parseLong(o.getOrDefault(key,Long.toString(fallback)));if (value<1) throw new IllegalArgumentException("--"+key+" must be a positive signed 64-bit integer");return value;
    }
    private static double[] background(String choice,MemeDnaMotifs.Document document) {
        if (choice.equals("uniform")) return new double[]{.25,.25,.25,.25};
        if (choice.equals("nrdb")) return new double[]{.281774,.222020,.228876,.267330};
        if (choice.equals("motif")) { if (document.background()==null) throw new IllegalArgumentException("--background motif requires an explicit background in the motif file");return document.background(); }
        String[] fields=choice.split(",",-1);if (fields.length!=4) throw new IllegalArgumentException("--background must be uniform, nrdb, motif or explicit A,C,G,T probabilities");
        double[] values=new double[4];for (int i=0;i<4;i++) values[i]=Double.parseDouble(fields[i]);return values;
    }
    private static void status(Path directory,String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\""+state+"\"}\n"); }
    static String help() { return """
        motif-scan --motifs MOTIFS.meme --fasta SEQUENCES.fa[.gz] --out NEW_DIR
          --background uniform|nrdb|motif|A,C,G,T --strands forward|both
          --coordinates sequence|genomic [--threshold 1e-4] [--pseudocount 0.1]
          [--normalize-probabilities false] [--invalid-residues reject|convert-n]
          [--maximum-hits 1000000] [--maximum-residues 10000000000]
          [--workspace-mib 256] [--maximum-convolution-operations 1000000000]
          [--options-file UTF8_OPTIONS.json]
        Native Java DNA PSSM/p-value subset of FIMO 5.5.9; no installation required.
        Minimal MEME text only: explicit ALPHABET=ACGT, alength=4, w=width,
        probability rows and optional nsites (default 20), alternate ID and URL.
        At most 16 MiB/1000 motifs/512 positions per motif. Rounded probabilities
        require explicit normalization; counts/log-odds/HTML/custom alphabets fail.
        The FIMO scan convention averages A/T and C/G for both strands, then adds
        5e-7 per base and normalizes, separately from the motif pseudocount. The
        supplied and final backgrounds and this preprocessing are recorded.
        Discovery strand metadata does not override the requested scanning policy.
        Threshold is inclusive; --threshold 1 scores every canonical window, including
        allele windows that a more stringent scan would omit. No missing allele is zero.
        U/u scores as T/t while preserving forward matched text; X/x/dot aliases N.
        IUPAC ambiguity masks complete windows.
        Genomic mode parses name:start-end, anchors one-based coordinates at start,
        and warns on inconsistent lengths. Sequence mode preserves full first-token IDs.
        Duplicate names retain input ordinals and complete JSON-escaped header metadata.
        Outputs include lattice/continuous scores, blank q-values, all window counts,
        resolved background, hashes, limits and warnings. No silent hit truncation;
        failures quarantine partial scientific streams. No q-value/PSP/custom empirical
        calibration or complete upstream format parity. Exit 0 complete, 2 failed.
        JSON recipes avoid Windows launcher loss of Unicode file paths; CLI overrides.
        """; }
}
