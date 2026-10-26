/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.DiploidScores;
import org.jlinalg.genetics.PlinkBedDataset;

/** Native, supplied-weight scoring of resolved diploid autosomal BED genotypes. */
final class GenotypeScoreCli {
    private GenotypeScoreCli() { }
    private static final Set<String> ALLOWED = Set.of("bfile","weights","missing","genome-build","out","options-file",
        "maximum-samples","maximum-variants","maximum-scored-variants","maximum-scored-cells","maximum-metadata-mib",
        "maximum-retained-mib","maximum-line-bytes","maximum-weights-mib","maximum-bed-mib");
    private static final List<String> PRODUCTS = List.of("results.tsv","variant-metadata.tsv","sample-metadata.tsv");
    private static final String RECOVERY = "Required action: correct the named input, allele pair, declared missing policy or explicit resource limit and rerun to a new writable output directory. Preserve the original files. Native scoring needs only Java and never installs software. Supply a variant-major BED/BIM/FAM set with exact unique FID/IID pairs and one resolved biallelic record for every model variant. Only human diploid autosomal hard calls are supported by this command. Mean imputation requires supplied effect-allele frequencies; target-cohort frequencies are not estimated implicitly. Failed scientific streams are quarantined under partial/.";

    private record Weight(String id, String effect, String other, double value, double frequency, int ordinal) { }

    static int run(String[] arguments, PrintStream output, PrintStream error) {
        if (arguments.length == 1 && arguments[0].equals("--help")) { output.println(help()); return 0; }
        try {
            var options = parse(arguments); required(options,"out"); int[] code = {0}; boolean[] warned = {false};
            transaction(options,"genotype-score-native",(directory,manifest) -> {
                var session = new LinkedHashMap<String,Object>(); var warnings = new ArrayList<String>();
                session.put("engine","native-java"); session.put("scope","Supplied-weight, diploid autosomal hard-call BED scoring. Exact case-sensitive allele pairs, no strand complements, dosage import, weight training, covariate fitting or absolute-risk calibration. Genome build is a caller declaration, not a reference-genome validation.");
                try {
                    Path prefix = Path.of(required(options,"bfile")).toAbsolutePath().normalize();
                    Path weightsPath = file(options,"weights");
                    var policy = switch (required(options,"missing")) {
                        case "error" -> DiploidScores.MissingPolicy.ERROR;
                        case "mean-impute" -> DiploidScores.MissingPolicy.MEAN_IMPUTE;
                        case "omit" -> DiploidScores.MissingPolicy.OMIT;
                        default -> throw new IllegalArgumentException("--missing must be error, mean-impute or omit");
                    };
                    String build = required(options,"genome-build");
                    if (!Set.of("GRCh37","GRCh38","unspecified").contains(build)) throw new IllegalArgumentException("--genome-build must be GRCh37, GRCh38 or unspecified");
                    int maxSamples = positiveInt(options,"maximum-samples",1_000_000), maxScored = positiveInt(options,"maximum-scored-variants",1_000_000);
                    long maxVariants = positiveLong(options,"maximum-variants",50_000_000), maxCells = positiveLong(options,"maximum-scored-cells",1_000_000_000);
                    var readerLimits = new PlinkBedDataset.Limits(maxSamples,maxVariants,maxScored,mib(options,"maximum-metadata-mib",8192),mib(options,"maximum-retained-mib",256),positiveInt(options,"maximum-line-bytes",16384));
                    long maxWeights = mib(options,"maximum-weights-mib",128), maxBed = mib(options,"maximum-bed-mib",65536);
                    if (Files.size(weightsPath) > maxWeights) throw new IOException("Weights input exceeds --maximum-weights-mib");
                    Path bed = Path.of(prefix + ".bed"), bim = Path.of(prefix + ".bim"), fam = Path.of(prefix + ".fam");
                    for (Path p : List.of(bed,bim,fam)) if (!Files.isRegularFile(p)) throw new IOException("Missing BED-set input: " + p);
                    if (Files.size(bed) > maxBed) throw new IOException("BED input exceeds --maximum-bed-mib");
                    var inputs = new LinkedHashMap<String,Path>(); inputs.put("weights",weightsPath); inputs.put("bed",bed); inputs.put("bim",bim); inputs.put("fam",fam);
                    if (options.containsKey("options-file")) { Path recipe = file(options,"options-file"); inputs.put("options-file",recipe); Files.copy(recipe,directory.resolve("options-input.json")); }
                    var hashes = new LinkedHashMap<String,String>();
                    for (var entry : inputs.entrySet()) hashes.put(entry.getKey(),hash(entry.getValue()));
                    manifest.put("input_sha256",hashes);
                    var weights = weights(weightsPath,policy,maxScored,readerLimits.maximumLineBytes(),readerLimits.maximumEstimatedRetainedBytes()); long zeroWeights = weights.values().stream().filter(w -> w.value() == 0).count();
                    session.put("missing_policy",policy.toString()); session.put("genome_build_declared",build); session.put("frequency_source","Explicit supplied effect-allele frequencies only; no target-cohort inference");
                    session.put("maximum_scored_cells",maxCells); session.put("reader_limits",Map.of("samples",maxSamples,"variants",maxVariants,"selected",maxScored,"metadata_bytes",readerLimits.maximumMetadataBytes(),"estimated_retained_bytes",readerLimits.maximumEstimatedRetainedBytes(),"line_bytes",readerLimits.maximumLineBytes()));
                    session.put("maximum_weights_bytes",maxWeights); session.put("maximum_bed_bytes",maxBed); session.put("zero_weight_variants",zeroWeights);
                    if (build.equals("unspecified")) warnings.add("Genome build is explicitly unspecified. Required action: verify model/genotype variant identity and assembly provenance before genomic interpretation. Exact ID/allele matching does not validate coordinates or perform liftOver.");
                    if (zeroWeights == weights.size()) warnings.add("Every supplied weight is zero. Required action: verify the intended model. A zero linear score is not evidence of absence of biological effects.");
                    try (var data = PlinkBedDataset.open(prefix,weights.keySet(),readerLimits)) {
                        long cells = Math.multiplyExact((long)data.samples().size(),weights.size());
                        if (cells > maxCells) throw new IOException("Selected sample-by-variant cells exceed --maximum-scored-cells");
                        var score = DiploidScores.accumulator(data.samples().size(),policy,new DiploidScores.Limits(maxSamples,maxScored,maxCells));
                        long zeroPositions = 0;
                        try (var variants = Files.newBufferedWriter(directory.resolve("variant-metadata.tsv"),StandardCharsets.UTF_8)) {
                            variants.write("variant\tbim_ordinal\tweight_ordinal\tchromosome\tposition\tbim_a1\tbim_a2\tea\toa\tweight\tea_frequency\torientation\n");
                            for (var variant : data.selectedVariants()) {
                                if (!autosome(variant.chromosome())) throw new IOException("Scored variant is outside the diploid human autosomal subset: " + variant.id() + " (" + variant.chromosome() + ")");
                                Weight weight = weights.get(variant.id()); boolean swapped;
                                if (weight.effect().equals(variant.allele1()) && weight.other().equals(variant.allele2())) swapped = false;
                                else if (weight.effect().equals(variant.allele2()) && weight.other().equals(variant.allele1())) swapped = true;
                                else throw new IOException("Exact model/BIM allele-pair mismatch at " + variant.id() + "; no case conversion or strand complement is inferred");
                                double[] dosages = data.readAllele1Dosages(variant);
                                if (swapped) for (int i = 0; i < dosages.length; i++) dosages[i] = DiploidScores.otherAlleleDosage(dosages[i]);
                                score.add(dosages,weight.value(),weight.frequency());
                                if (variant.position() == 0) zeroPositions++;
                                variants.write(variant.id()+"\t"+(variant.ordinal()+1)+"\t"+weight.ordinal()+"\t"+variant.chromosome()+"\t"+variant.position()+"\t"+variant.allele1()+"\t"+variant.allele2()+"\t"+weight.effect()+"\t"+weight.other()+"\t"+weight.value()+"\t"+numeric(weight.frequency())+"\t"+(swapped?"BIM_A2":"BIM_A1")+"\n");
                            }
                        }
                        long imputed = 0, omitted = 0, unreportable = 0, fullyImputed = 0;
                        try (var results = Files.newBufferedWriter(directory.resolve("results.tsv"),StandardCharsets.UTF_8);
                             var samples = Files.newBufferedWriter(directory.resolve("sample-metadata.tsv"),StandardCharsets.UTF_8)) {
                            results.write("fid\tiid\tscore_sum\tscore_average\tinput_variants\tobserved_variants\timputed_variants\tobserved_alleles\tdenominator_alleles\tstatus\n");
                            samples.write("ordinal\tfid\tiid\tFAM_founder\tFAM_sex\n");
                            for (int i = 0; i < data.samples().size(); i++) {
                                var identity = data.samples().get(i); var value = score.score(i);
                                imputed += value.imputedVariants();
                                if (policy == DiploidScores.MissingPolicy.OMIT) omitted += value.inputVariants() - value.observedVariants();
                                if (value.status() == DiploidScores.Status.NO_OBSERVED_DOSAGES) unreportable++;
                                if (value.status() == DiploidScores.Status.FULLY_IMPUTED) fullyImputed++;
                                results.write(identity.familyId()+"\t"+identity.individualId()+"\t"+numeric(value.sum())+"\t"+numeric(value.average())+"\t"+value.inputVariants()+"\t"+value.observedVariants()+"\t"+value.imputedVariants()+"\t"+value.observedAlleles()+"\t"+value.denominatorAlleles()+"\t"+value.status()+"\n");
                                samples.write((i+1)+"\t"+identity.familyId()+"\t"+identity.individualId()+"\t"+identity.founder()+"\t"+identity.sex()+"\n");
                            }
                        }
                        session.put("samples",data.samples().size()); session.put("BIM_variants",data.variantCount()); session.put("scored_variants",score.variantCount()); session.put("scored_cells",cells);
                        session.put("imputed_cells",imputed); session.put("omitted_cells",omitted); session.put("unreportable_samples",unreportable); session.put("fully_imputed_samples",fullyImputed);
                        if (imputed > 0) warnings.add("Explicit mean imputation replaced "+imputed+" missing dosages using supplied effect-allele frequencies. Required action: verify that frequency panel and model eligibility. Observed alleles differ from the average denominator, which includes imputed variants.");
                        if (omitted > 0) warnings.add("Explicit omission excluded "+omitted+" missing variant contributions and their denominator alleles. Required action: review sample-specific denominators; these averages differ from mean-imputed averages and sums are not zero-imputed complete models.");
                        if (unreportable > 0) warnings.add(unreportable+" samples have no observed scored dosages. Sum/average fields are blank, not zero. Required action: provide valid genotypes or a scientifically justified explicit mean-imputation model before using those samples.");
                        if (fullyImputed > 0) warnings.add(fullyImputed+" samples are fully imputed from reference frequencies. Required action: treat these as conditional reference expectations, not measured individual genotype differences.");
                        if (zeroPositions > 0) warnings.add(zeroPositions+" scored BIM variants have bp position zero. Required action: resolve coordinate provenance before genomic mapping; this calculation matched exact variant IDs and allele pairs only.");
                        if (unreportable == data.samples().size()) throw new IOException("No sample has a reportable score after omission");
                    }
                    for (var entry : inputs.entrySet()) if (!hashes.get(entry.getKey()).equals(hash(entry.getValue()))) throw new IOException("Input changed during genotype scoring: " + entry.getKey());
                    warned[0] = !warnings.isEmpty(); String state = warned[0] ? "completed-with-qc-warnings" : "completed";
                    manifest.put("analysis_status",state); status(directory,state);
                    if (warned[0]) Files.writeString(directory.resolve("WARNINGS.md"),"# GENOTYPE SCORE WARNINGS\n\n"+String.join("\n\n",warnings)+"\n");
                } catch (Exception failure) {
                    code[0] = 2; manifest.put("analysis_status","failed"); manifest.put("error",failure.toString()); session.put("error",failure.toString());
                    for (String name : PRODUCTS) if (Files.exists(directory.resolve(name))) { Files.createDirectories(directory.resolve("partial")); Files.move(directory.resolve(name),directory.resolve("partial").resolve(name)); }
                    status(directory,"failed"); Files.writeString(directory.resolve("WARNINGS.md"),"# GENOTYPE SCORING FAILED\n\n"+failure+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                }
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if (code[0] != 0) error.println("GENOTYPE SCORING FAILED: see "+Path.of(options.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");
            else { output.println("Native genotype scoring completed: "+options.get("out")); if (warned[0]) error.println("GENOTYPE SCORE WARNINGS: review "+Path.of(options.get("out")).resolve("WARNINGS.md")+" before downstream use."); }
            return code[0];
        } catch (Exception failure) { error.println("GENOTYPE SCORING NOT STARTED: "+failure.getMessage()+". "+RECOVERY+" Review genotype-score --help."); return 2; }
    }

    private static Map<String,Weight> weights(Path path, DiploidScores.MissingPolicy policy, int maximum, int maximumLine, long maximumRetained) throws IOException {
        Set<String> columns = Set.of("variant","ea","oa","weight","ea_frequency");
        var result = new LinkedHashMap<String,Weight>(); int ordinal = 0; long retained = 0;
        char delimiter = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv") ? ',' : '\t';
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            String first = line(input,maximumLine); if (first == null) throw new IOException("Weights table is empty");
            var header = DelimitedData.parse(first,delimiter,1,path); var index = new LinkedHashMap<String,Integer>();
            for (int i = 0; i < header.size(); i++) { String name = header.get(i).trim(); if (!columns.contains(name) || index.putIfAbsent(name,i) != null) throw new IOException("Unknown or duplicate native score weight column: " + name); }
            for (String name : List.of("variant","ea","oa","weight")) if (!index.containsKey(name)) throw new IOException("Missing score weight column: " + name);
            boolean frequencies = index.containsKey("ea_frequency");
            if (policy == DiploidScores.MissingPolicy.MEAN_IMPUTE && !frequencies) throw new IOException("Mean imputation requires the supplied ea_frequency column");
            long lineNumber = 1;
            for (String text; (text = line(input,maximumLine)) != null;) {
                lineNumber++; if (text.isBlank()) continue;
                if (ordinal >= maximum) throw new IOException("Weights exceed --maximum-scored-variants");
                var row = DelimitedData.parse(text,delimiter,lineNumber,path); if (row.size() != header.size()) throw new IOException("Weights row has wrong column count at line " + lineNumber);
                retained = Math.addExact(retained,240 + 2L * text.getBytes(StandardCharsets.UTF_8).length);
                if (retained > maximumRetained) throw new IOException("Estimated retained weights metadata limit exceeded");
            String id = token(row.get(index.get("variant"))), effect = token(row.get(index.get("ea"))), other = token(row.get(index.get("oa")));
            if (effect.equalsIgnoreCase(other) || Set.of("0",".").contains(effect) || Set.of("0",".").contains(other)) throw new IOException("Weights require distinct resolved ea/oa at " + id);
            double weight = Double.parseDouble(row.get(index.get("weight")));
            double frequency = frequencies ? missingNumber(row.get(index.get("ea_frequency"))) : Double.NaN;
            if (!Double.isFinite(weight)) throw new IOException("Nonfinite weight at " + id);
            if (Double.isNaN(frequency) ? policy == DiploidScores.MissingPolicy.MEAN_IMPUTE : !Double.isFinite(frequency) || frequency < 0 || frequency > 1) throw new IOException("Invalid or missing effect-allele frequency at " + id);
            if (result.putIfAbsent(id,new Weight(id,effect,other,weight,frequency,++ordinal)) != null) throw new IOException("Duplicate weight variant ID: " + id);
            }
        }
        if (result.isEmpty()) throw new IOException("Weights table contains no model variants");
        return result;
    }
    private static String line(InputStream input, int maximum) throws IOException {
        var bytes = new ByteArrayOutputStream(); int value;
        while ((value = input.read()) != -1 && value != '\n') { if (bytes.size() == maximum) throw new IOException("Weights line-byte limit exceeded"); bytes.write(value); }
        if (value == -1 && bytes.size() == 0) return null;
        byte[] raw = bytes.toByteArray(); int length = raw.length; if (length > 0 && raw[length-1] == '\r') length--;
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,length)).toString();
    }
    private static double missingNumber(String text) { return Set.of("","NA","NaN",".").contains(text) ? Double.NaN : Double.parseDouble(text); }
    private static String token(String value) throws IOException {
        if (value.isEmpty()) throw new IOException("Empty score identifier/allele");
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i)) || Character.isWhitespace(value.charAt(i)) || Character.isSpaceChar(value.charAt(i))) throw new IOException("Whitespace/control in score identifier/allele");
        return value;
    }
    private static boolean autosome(String value) {
        String chromosome = value.regionMatches(true,0,"chr",0,3) ? value.substring(3) : value;
        if (!chromosome.matches("[0-9]+")) return false;
        try { int number = Integer.parseInt(chromosome); return number >= 1 && number <= 22; } catch (NumberFormatException invalid) { return false; }
    }
    private static String numeric(double value) { return Double.isNaN(value) ? "" : Double.toString(value); }
    private static void status(Path directory, String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\""+state+"\"}\n"); }
    private static Path file(Map<String,String> options, String key) throws IOException { Path path = Path.of(required(options,key)).toAbsolutePath().normalize(); if (!Files.isRegularFile(path)) throw new IOException("Missing input --"+key+": "+path); return path; }
    private static long positiveLong(Map<String,String> options, String key, long fallback) { long n = Long.parseLong(options.getOrDefault(key,Long.toString(fallback))); if (n <= 0) throw new IllegalArgumentException("--"+key+" must be positive"); return n; }
    private static int positiveInt(Map<String,String> options, String key, int fallback) { return Math.toIntExact(positiveLong(options,key,fallback)); }
    private static long mib(Map<String,String> options, String key, long fallback) { return Math.multiplyExact(positiveLong(options,key,fallback),1_048_576); }
    private static Map<String,String> parse(String[] arguments) throws Exception {
        var direct = options(arguments,ALLOWED); if (!direct.containsKey("options-file")) return direct;
        Path recipe = file(direct,"options-file"); if (Files.size(recipe) > 1_048_576) throw new IOException("Options recipe exceeds 1 MiB");
        Object parsed = SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(recipe))).toString());
        if (!(parsed instanceof Map<?,?> values)) throw new IllegalArgumentException("Require a UTF-8 JSON object of option:string values");
        var effective = new LinkedHashMap<String,String>();
        for (var entry : values.entrySet()) { String key = String.valueOf(entry.getKey()); if (!ALLOWED.contains(key) || key.equals("options-file") || !(entry.getValue() instanceof String text)) throw new IllegalArgumentException("Unknown,recursive or non-string recipe option: "+key); effective.put(key,text); }
        effective.putAll(direct); return effective;
    }
    static String help() { return """
        genotype-score --bfile BED_PREFIX --weights MODEL.tsv --missing error|mean-impute|omit
          --genome-build GRCh37|GRCh38|unspecified --out NEW_DIRECTORY
          [--options-file UTF8_OPTIONS.json]
          [--maximum-samples 1000000] [--maximum-variants 50000000]
          [--maximum-scored-variants 1000000] [--maximum-scored-cells 1000000000]
          [--maximum-metadata-mib 8192] [--maximum-retained-mib 256]
          [--maximum-line-bytes 16384] [--maximum-weights-mib 128] [--maximum-bed-mib 65536]
        Native Java supplied-weight scoring of resolved biallelic, diploid human autosomal
        hard-call BED/BIM/FAM inputs. No installation. Not a PLINK QC/PCA/GWAS adapter.
        TSV or .csv weights: variant ea oa weight, plus ea_frequency for mean imputation.
        Each declared variant must have one exact BIM ID/allele pair; swap uses 2-A1 dosage.
        No strand complements, silent ID/allele repair or implicit target-frequency estimation.
        Mean imputation uses twice the supplied effect-allele frequency. Its average uses
        all model alleles; observed alleles remain separately reported. Omission uses only
        observed alleles. An omitted all-missing sample has blank, unreportable scores.
        Genome build is only a caller declaration, not reference/coordinate validation.
        Weights are applied, not trained; sums are not calibrated absolute risks.
        Streams selected variants with bounded metadata/work; full inputs are SHA-256
        hashed before and after scoring. This can add substantial I/O for large BED sets.
        Outputs: sums/averages/counts/status, exact sample/variant/allele ledgers, provenance
        and prominent warnings/actions. Failures return2 and quarantine partial science;
        all-unreportable omitted cohorts fail. Existing outputs are never overwritten.
        A UTF-8 JSON recipe avoids Windows launcher loss of Unicode paths; CLI overrides.
        """; }
}
