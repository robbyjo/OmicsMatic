/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Arrays;

/** Executable, bounded-memory statistical association command line. */
public final class JLinAlgCli {
    private JLinAlgCli() { }

    public static void main(String[] arguments) {
        int status = run(arguments);
        if (status != 0) System.exit(status);
    }

    static int run(String[] arguments) {
        return run(arguments, System.out, System.err);
    }

    static int run(String[] arguments, PrintStream output,
            PrintStream errorOutput) {
        try {
            if (arguments.length > 0 && arguments[0].equals("study"))
                return StudyCli.run(Arrays.copyOfRange(
                    arguments, 1, arguments.length), output, errorOutput);
            ProjectConfiguration.Resolved config = ProjectConfiguration.resolve(arguments,
                java.nio.file.Path.of("").toAbsolutePath(), java.nio.file.Path.of(System.getProperty("user.home")));
            arguments = config.arguments();
            if (arguments.length == 1 && arguments[0].equals("config")) {
                output.print(new org.yaml.snakeyaml.Yaml().dump(config.provenance()));
                return 0;
            }
            java.nio.file.Path record = null;
            boolean auditConfig = !((java.util.List<?>)config.provenance().get("sources")).isEmpty()
                || arguments.length>0 && java.util.Set.of("single-cell","spatial","network","variant-db","variant-annotate","variant-consequence","variant-score").contains(arguments[0]);
            if (auditConfig && !Arrays.asList(arguments).contains("--help")) {
                for (int i=0;i+1<arguments.length;i++) if(arguments[i].equals("--out"))
                    record=java.nio.file.Path.of(arguments[i+1]+".config.yaml");
            }
            // Existing commands retain their overwrite semantics; configuration never overwrites inputs.
            if(record!=null && Files.exists(record) && !Arrays.asList(arguments).contains("--overwrite"))
                throw new IOException("Configuration output already exists: "+record);
            if(record!=null && Files.exists(record)) for(Object source:(java.util.List<?>)config.provenance().get("sources")) {
                String path=String.valueOf(((java.util.Map<?,?>)source).get("path"));
                if(Files.isSameFile(record,java.nio.file.Path.of(path)))
                    throw new IOException("Configuration output aliases a source configuration");
            }
            if(record!=null) for(String value:arguments) {
                if(value.startsWith("--"))continue;
                try { if(Files.exists(record) && Files.exists(java.nio.file.Path.of(value))
                        && Files.isSameFile(record,java.nio.file.Path.of(value)))
                    throw new IOException("Configuration output aliases an input"); }
                catch(java.nio.file.InvalidPathException ignored) { }
            }
            int status = runConfigured(arguments,output,errorOutput);
            if(status==0 && record!=null) {
                Files.createDirectories(record.toAbsolutePath().getParent());
                Files.writeString(record,new org.yaml.snakeyaml.Yaml().dump(config.provenance()));
            }
            return status;
        } catch (IOException | RuntimeException exception) {
            errorOutput.println("jlinalg: "+exception.getMessage()); return 2;
        }
    }

    private static int runConfigured(String[] arguments, PrintStream output, PrintStream errorOutput) {
        if (arguments.length > 0 && CliRunLogging.accepts(arguments[0]))
            return CliRunLogging.run(arguments, errorOutput,
                forwarded -> dispatch(forwarded, output, errorOutput));
        return dispatch(arguments, output, errorOutput);
    }

    private static int dispatch(String[] arguments, PrintStream output,
            PrintStream errorOutput) {
        if (arguments.length > 0 && arguments[0].equals("signature-score"))
            return SignatureScoreCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("intervals"))
            return GenomicIntervalCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("cell-embed"))
            return CellEmbeddingCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("sequence-qc"))
            return SequenceToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("sequence-tools"))
            return SequenceUtilsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("alignment-tools"))
            return AlignmentToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("hts-tools"))
            return HtsToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("read-align"))
            return ReadAlignmentCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("gatk-tools"))
            return GatkToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("star-tools"))
            return StarToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("genomic-signal"))
            return GenomicSignalCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("track-peaks"))
            return TrackPeakCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("regenie-tools"))
            return RegenieToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("coordinate-lift"))
            return CoordinateLiftCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("feature-counts"))
            return FeatureCountsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("minimap-tools"))
            return MinimapToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("hisat-tools"))
            return HisatToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("trimmomatic-tools"))
            return TrimmomaticToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("enrichment-tools"))
            return EnrichmentToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("trim-galore-reads"))
            return TrimGaloreReadsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("flash-reads"))
            return FlashReadsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("motif-scan"))
            return MotifScanCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("fimo-tools"))
            return FimoToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("genotype-score"))
            return GenotypeScoreCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("beagle-tools"))
            return BeagleToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("prs-cs-tools"))
            return PrsCsToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("molecular-geometry"))
            return MolecularGeometryCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("electrostatic-grid"))
            return ElectrostaticGridCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("apbs-tools"))
            return ApbsToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("igv-tools"))
            return IgvToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("unrelated-select"))
            return UnrelatedSelectCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("pdb2pqr-tools"))
            return Pdb2pqrToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("potential-surface"))
            return PymolPotentialCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("pymol-tools"))
            return PymolToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("plink-tools"))
            return PlinkToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("trim-galore-tools"))
            return TrimGaloreToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("kallisto-tools"))
            return KallistoToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("transcript-import"))
            return TranscriptImportCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("quantile-normalize"))
            return QuantileNormalizeCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("deep-tools"))
            return DeepToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("magma-tools"))
            return MagmaToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("single-cell-qc"))
            return SingleCellQcCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("plot"))
            try {
                return PlotCli.run(Arrays.copyOfRange(
                    arguments, 1, arguments.length), output, errorOutput);
            } catch (IOException exception) {
                throw new IllegalArgumentException(exception.getMessage(), exception);
            }
        if(arguments.length>0 && arguments[0].equals("single-cell"))
            return SingleCellCli.run(Arrays.copyOfRange(arguments,1,arguments.length),output,errorOutput);
        if(arguments.length>0 && arguments[0].equals("spatial"))
            return SpatialCli.run(Arrays.copyOfRange(arguments,1,arguments.length),output,errorOutput);
        if(arguments.length>0 && arguments[0].equals("variant-tools"))
            return VariantToolsCli.run(Arrays.copyOfRange(arguments,1,arguments.length),output,errorOutput);
        if(arguments.length>0 && java.util.Set.of("variant-db","variant-annotate","variant-consequence","variant-score").contains(arguments[0]))
            return VariantFollowupCli.run(arguments[0],Arrays.copyOfRange(arguments,1,arguments.length),output,errorOutput);
        if(arguments.length>0 && arguments[0].equals("network"))
            return NetworkCli.run(Arrays.copyOfRange(arguments,1,arguments.length),output,errorOutput);
        if (arguments.length > 0 && java.util.Set.of("censored-regression", "ordinal-regression",
                "rare-events-logit", "survey-regression").contains(arguments[0]))
            return InferenceCli.run(arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (Arrays.asList(arguments).contains("--enrichment"))
            return EnrichmentCli.run(arguments, output, errorOutput);
        if (arguments.length > 0 && java.util.Set.of("ldsc", "twas", "pwas", "genomic-factor").contains(arguments[0]))
            return SummaryXwasCli.run(arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && java.util.Set.of("score-train", "score-apply").contains(arguments[0]))
            return ScoreCli.run(arguments[0], Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("grm"))
            return GrmCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("rare-score"))
            return RareScoreCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("rare-meta"))
            return RareMetaCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("conditional-score"))
            return ConditionalScoreCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && (arguments[0].equals("confounders")
                || arguments[0].equals("batch-adjust")))
            return ConfounderCli.run(arguments[0], Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && java.util.Set.of("differential",
                "ewas-regions", "multiple-test", "multiple-impute", "mi-pool")
                .contains(arguments[0]))
            return XwasInferenceCli.run(arguments[0], Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("glm-predict"))
            return PredictionCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("iv-regression"))
            return InstrumentalVariableCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("arima-regression"))
            return ArimaRegressionCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && (arguments[0].equals("meta-analysis")
                || arguments[0].equals("meta-regression")))
            return MetaCli.run(arguments[0], Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("ld-db"))
            return LdDatabaseCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("mr-instruments"))
            return MrInstrumentCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("clump"))
            return LdClumpCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("mr-xwas"))
            return MrXwasCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("mr-estimate"))
            return MrEstimatorCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("mr-mvmr"))
            return MultivariableMrCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("mr-multivariate"))
            return MultivariateMrCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && (arguments[0].equals("susie")
                || arguments[0].equals("coloc")))
            return FineMappingCli.run(arguments[0], Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && arguments[0].equals("mediation"))
            return MediationCli.run(Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        if (arguments.length > 0 && (arguments[0].equals("beta-regression")
                || arguments[0].equals("penalized-regression")))
            return RegressionCli.run(arguments[0], Arrays.copyOfRange(
                arguments, 1, arguments.length), output, errorOutput);
        CliOptions options;
        try {
            options = CliOptions.parse(arguments);
            if (options.help) {
                output.println(help());
                return 0;
            }
            if (options.version) {
                output.println(version());
                return 0;
            }
            options.validateForRun();
        } catch (RuntimeException exception) {
            errorOutput.println("jlinalg: " + exception.getMessage());
            errorOutput.println("Use --help for usage.");
            return 2;
        }

        RunLog log = null;
        try {
            if (!options.noLog)
                log = RunLog.open(options.logPath(), options.resume);
            info(log, "version=" + version());
            info(log, "command=" + String.join(" ", arguments));
            info(log, "java=" + System.getProperty("java.version"));
            info(log, "max_heap_bytes=" + Runtime.getRuntime().maxMemory());
            FormulaPlan plan = FormulaPlan.parse(options.formula);
            if (plan.hasOmics() && options.omics == null)
                throw new IllegalArgumentException(
                    "formula contains <omics> but --omics is absent");
            if (!plan.hasOmics() && options.omics != null)
                throw new IllegalArgumentException(
                    "--omics requires a <omics> formula term");
            AnalysisRunner runner = new AnalysisRunner(
                options, plan, log, output);
            int result = runner.execute();
            complete(log, "complete");
            return result;
        } catch (Exception exception) {
            try {
                error(log, exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
                complete(log, "failed");
            } catch (IOException ignored) {
                // Preserve the original failure.
            }
            errorOutput.println("jlinalg: " + exception.getMessage());
            if (log == null) exception.printStackTrace(errorOutput);
            return 1;
        } finally {
            if (log != null) {
                try {
                    log.close();
                } catch (IOException exception) {
                    errorOutput.println(
                        "jlinalg: failed to close log: " + exception.getMessage());
                }
            }
        }
    }

    static String version() {
        String value = JLinAlgCli.class.getPackage()
            .getImplementationVersion();
        return value == null ? "development" : value;
    }

    private static void info(RunLog log, String message) throws IOException {
        if (log != null) log.info(message);
    }
    private static void error(RunLog log, String message) throws IOException {
        if (log != null) log.error(message);
    }
    private static void complete(RunLog log, String status) throws IOException {
        if (log != null) log.complete(status);
    }

    private static String help() {
        return """
            Usage:
              java -jar omicsmatic-<version>.jar plot --help
              java -jar omicsmatic-<version>.jar study --help
              java -jar omicsmatic-<version>.jar signature-score --help
              java -jar omicsmatic-<version>.jar intervals --help
              java -jar omicsmatic-<version>.jar cell-embed --help
              java -jar omicsmatic-<version>.jar sequence-qc --help
              java -jar omicsmatic-<version>.jar sequence-tools --help
              java -jar omicsmatic-<version>.jar alignment-tools --help
              java -jar omicsmatic-<version>.jar hts-tools --help
              java -jar omicsmatic-<version>.jar read-align --help
              java -jar omicsmatic-<version>.jar gatk-tools --help
              java -jar omicsmatic-<version>.jar star-tools --help
              java -jar omicsmatic-<version>.jar genomic-signal --help
              java -jar omicsmatic-<version>.jar track-peaks --help
              java -jar omicsmatic-<version>.jar regenie-tools --help
              java -jar omicsmatic-<version>.jar coordinate-lift --help
              java -jar omicsmatic-<version>.jar feature-counts --help
              java -jar omicsmatic-<version>.jar minimap-tools --help
              java -jar omicsmatic-<version>.jar hisat-tools --help
              java -jar omicsmatic-<version>.jar single-cell-qc --help
              java -jar omicsmatic-<version>.jar magma-tools --help
              java -jar omicsmatic-<version>.jar trimmomatic-tools --help
              java -jar omicsmatic-<version>.jar deep-tools --help
              java -jar omicsmatic-<version>.jar enrichment-tools --help
              java -jar omicsmatic-<version>.jar trim-galore-reads --help
              java -jar omicsmatic-<version>.jar flash-reads --help
              java -jar omicsmatic-<version>.jar motif-scan --help
              java -jar omicsmatic-<version>.jar fimo-tools --help
              java -jar omicsmatic-<version>.jar genotype-score --help
              java -jar omicsmatic-<version>.jar beagle-tools --help
              java -jar omicsmatic-<version>.jar prs-cs-tools --help
              java -jar omicsmatic-<version>.jar molecular-geometry --help
              java -jar omicsmatic-<version>.jar electrostatic-grid --help
              java -jar omicsmatic-<version>.jar apbs-tools --help
              java -jar omicsmatic-<version>.jar igv-tools --help
              java -jar omicsmatic-<version>.jar unrelated-select --help
              java -jar omicsmatic-<version>.jar pdb2pqr-tools --help
              java -jar omicsmatic-<version>.jar potential-surface --help
              java -jar omicsmatic-<version>.jar pymol-tools --help
              java -jar omicsmatic-<version>.jar plink-tools --help
              java -jar omicsmatic-<version>.jar trim-galore-tools --help
              java -jar omicsmatic-<version>.jar kallisto-tools --help
              java -jar omicsmatic-<version>.jar transcript-import --help
              java -jar omicsmatic-<version>.jar quantile-normalize --help
              java -jar omicsmatic-<version>.jar config --command COMMAND
              java -jar omicsmatic-<version>.jar variant-db --help
              java -jar omicsmatic-<version>.jar variant-annotate --help
              java -jar omicsmatic-<version>.jar variant-consequence --help
              java -jar omicsmatic-<version>.jar variant-tools install alphagenome
              java -jar omicsmatic-<version>.jar variant-score --help
              java -jar omicsmatic-<version>.jar network --help
              java -jar omicsmatic-<version>.jar single-cell --help
              java -jar omicsmatic-<version>.jar spatial --help
              All commands: [--local-config FILE] [--config FILE | --no-config]
              Precedence: built-in defaults < ~/.jlinalg/config.yaml < ./jlinalg.yaml < CLI
              java -jar omicsmatic-<version>.jar censored-regression --help
              java -jar omicsmatic-<version>.jar ordinal-regression --help
              java -jar omicsmatic-<version>.jar rare-events-logit --help
              java -jar omicsmatic-<version>.jar survey-regression --help
              java -jar omicsmatic-<version>.jar --enrichment GO:BP --enrichment-db DIRECTORY
                --input results.csv --input-id probe_id --selection "FDR < 0.05" --out enrichment.tsv
              java -jar omicsmatic-<version>.jar --enrichment GO --download NEW_DIRECTORY
              java -jar omicsmatic-<version>.jar --enrichment GO --help
              java -jar omicsmatic-<version>.jar grm --genotypes cohort.vcf.gz
                --out cohort-grm.tsv [--maf 0.01] [--call-rate 0.95]
              java -jar omicsmatic-<version>.jar rare-score --vcf cohort.vcf.gz
                --pheno phenotype.tsv --id sample --response trait
                --genome-build GRCh38 --out PREFIX
              java -jar omicsmatic-<version>.jar rare-meta --cohorts manifest.tsv
                --genome-build GRCh38 --test single|burden|skat|skat-o|acat-v|acat-o --out PREFIX
              java -jar omicsmatic-<version>.jar conditional-score --cohorts manifest.tsv
                --targets CHR:POS:REF:ALT --condition-on CHR:POS:REF:ALT --out FILE.tsv
              java -jar omicsmatic-<version>.jar confounders --method pca|sva|autosva|peer
                --omics MATRIX [--pheno TABLE --id COLUMN] --factors N|auto --out PREFIX
              java -jar omicsmatic-<version>.jar batch-adjust --method combat --omics MATRIX
                --pheno TABLE --id COLUMN --batch COLUMN --out PREFIX
              java -jar omicsmatic-<version>.jar differential --method limma|voom|negative-binomial
                --omics MATRIX --pheno TABLE --id COLUMN --group COLUMN --out FILE.tsv
              java -jar omicsmatic-<version>.jar ewas-regions --input probes.tsv
                --genome-build GRCh38 --out regions.tsv
              java -jar omicsmatic-<version>.jar multiple-test --method ihw|hierarchy
                --input tests.tsv --out adjusted.tsv
              java -jar omicsmatic-<version>.jar multiple-impute --input TABLE
                --types age:continuous,case:binary --out PREFIX
              java -jar omicsmatic-<version>.jar mi-pool --input estimates.tsv --out pooled.tsv
              java -jar omicsmatic-<version>.jar glm-predict --input TABLE --response Y
                --predictors x1,x2 --family probit --estimand expected --out FILE
              java -jar omicsmatic-<version>.jar iv-regression --input TABLE --response Y
                --endogenous X --instruments Z1,Z2 --out PREFIX
              java -jar omicsmatic-<version>.jar arima-regression --input TABLE --response Y
                --order p,d,q --out PREFIX [--smooth]
              java -jar omicsmatic-<version>.jar meta-analysis
                --cohort NAME=FILE [--cohort NAME=FILE ...] --model fixed|random --out FILE
              java -jar omicsmatic-<version>.jar meta-regression
                --cohort NAME=FILE [--cohort NAME=FILE ...] --moderator-file FILE
                --moderators COLUMN[,COLUMN...] --out FILE
              java -jar omicsmatic-<version>.jar --pheno FILE --id COLUMN
                --formula "y ~ covariates + <omics>" [--omics FILE] --out FILE
              java -jar omicsmatic-<version>.jar ld-db list
              java -jar omicsmatic-<version>.jar ld-db download
                --database NAME [--location DIRECTORY]
              java -jar omicsmatic-<version>.jar mr-instruments search --trait TEXT
              java -jar omicsmatic-<version>.jar mr-instruments download
                --study GCST... --out FILE [--p-threshold 5e-8]
              java -jar omicsmatic-<version>.jar mr-instruments format
                --input FILE --out FILE [--map TARGET=SOURCE,...]
              java -jar omicsmatic-<version>.jar clump --database DIRECTORY
                --instrument FILE --ld-threshold 0.001 --output FILE
              java -jar omicsmatic-<version>.jar mr-xwas --exposure FILE
                --outcome FILE --output FILE --p-threshold X
              java -jar omicsmatic-<version>.jar mr-estimate --input FILE
                [--method ivw-fixed|ivw-random|egger|weighted-median|
                 ivw-generalized-fixed|ivw-generalized-random|egger-generalized|
                 overlap-aware|conditional|all] [--ld MATRIX]
                [--sampling-covariance FILE] [--output FILE] [--plot FILE]
              java -jar omicsmatic-<version>.jar mr-mvmr --input FILE
                --exposures NAME1,NAME2 [--outcome NAME] [--method ivw|egger]
              java -jar omicsmatic-<version>.jar mr-multivariate --input FILE
                --exposures NAME[,NAME...] --outcomes NAME1,NAME2
                --outcome-correlation MATRIX [--method ivw-fixed|ivw-random|presso]
              java -jar omicsmatic-<version>.jar susie --summary FILE --ld FILE
                --sample-size N --out FILE
              java -jar omicsmatic-<version>.jar coloc --trait1 FILE
                --trait2 FILE --out FILE
              java -jar omicsmatic-<version>.jar mediation --input FILE
                --outcome Y --treatment X --mediator M --out FILE
              java -jar omicsmatic-<version>.jar beta-regression --input FILE
                --response COLUMN [--mean COLUMNS] [--precision COLUMNS]
              java -jar omicsmatic-<version>.jar penalized-regression --input FILE
                --response COLUMN --predictors COLUMNS
                --model ridge|lasso|elastic-net

            Core options:
              New source-build workflows (use COMMAND --help):
                ldsc                       Heritability and genetic correlation
                twas / pwas                Genetically predicted molecular tests
                genomic-factor             Genetic factor and conditional SNP tests
                score-train / score-apply   Train, apply, and evaluate prediction scores

              --omics FILE                 CSV/TSV, VCF, BCF, or BGEN matrix
              --pheno FILE                 Observation-by-variable CSV/TSV
              --id COLUMN                  Phenotype sample-ID column; omics
                                           scans use the ID intersection
              --formula FORMULA            R-style fixed/random formula
              --model auto|ols|lmm|glm|glmm|cox
              --family gaussian|binomial|probit|binomial-probit|poisson|gamma|
                       inverse-gaussian|quasi-binomial|quasi-poisson
              --link LINK                 Restate the family's canonical link;
                                          noncanonical overrides are unavailable
              --grm FILE|PREFIX           Labeled dense matrix or GCTA prefix
              --individual-id COLUMN      Phenotype-to-GRM/pedigree ID
                                           (defaults to --id)
              --pedigree FILE             Pedigree table for a matching
                                           (1|individual-id) formula term
              --pedigree-id COLUMN        Pedigree individual ID column
              --sire-id COLUMN            Pedigree sire/parent-1 ID column
              --dam-id COLUMN             Pedigree dam/parent-2 ID column
              --pedigree-family-id COLUMN Disambiguates duplicate member IDs;
                                           unique raw IDs and exact family:id
                                           values match, ambiguous raw IDs fail
                                           Absent IDs become unrelated
                                           singletons; zero file matches fail
              --variance-components auto|refit|null-model
                                           auto uses per-feature refits for
                                           numeric mixed scans and a null model
                                           only for genotype LMM scans
              --ties efron|breslow         Cox tied-event method
              --conditional-gwas-summary  Add efficient-score columns, covariance blocks and null metadata
              --score-genome-build NAME   Required reference build for score export
              --score-block-size N        Complete covariance per block (default 64, maximum 512)
              --condition-on IDS          Comma-separated variant IDs or CHR:POS:REF:ALT; refit null locally
              --df auto|satterth|kr
              --out FILE                   .csv writes CSV; other suffixes
                                           write TSV. Log defaults to FILE.log

            Streaming and reproducibility:
              --block-size auto|N           Default uses JVM heap headroom
                                           and available scan threads
              --threads N
              --backend POLICY              preferred (default), cholmod, gpu,
                                            cuda, opencl, vulkan, onemkl,
                                            openblas, auto, or cpu
              --resume                    Existing/partial results cannot yet be verified; use a new output
              --checkpoint-every N
              --log FILE
              --no-log
                                         Logs include UTC start/finish and
                                         readable elapsed runtime. Other
                                         subcommands use OUT.log or logs/.
              --dry-run                  Print the resolved general analysis
                                         plan and stop before model fitting
              --explain                  Print that plan, then continue fitting
                                         (singular; --explains is invalid)
              --overwrite

            Filtering and processing:
              --min-maf X --max-maf X --min-mac X --max-mac X  (minimum MAC defaults to 20)
              --max-marker-missing X --min-info X
              --genotype-kind auto|hard_calls|imputed|sequenced
              --min-hwe-p X --hwe-filter-scope all|controls|cases
              --binary-test efficient-score|firth-lrt|exact-conditional (genotype binomial GLM)
              --firth-max-iterations N --firth-tolerance X
              --exact-strata-column NAME   Declared categorical stratum term in formula
              --exact-max-states N --exact-max-work N
              --exact-effect-max-parameters N (0 retains exact P values without effects)
                                           Exact autosomal biallelic hard-call HWE;
                                           unavailable requested QC fails that variant
              --hwe-sample-file FILE        Optional independent QC panel intersected with analyzed samples
              --min-missingness-p X         Optional case/control missingness Fisher filter
              --missingness-max-states N    Exact conditional state bound (default 100000)
              --autosomes-only             Keep known human chromosomes 1-22
              --human-ploidy-region autosomes|par|x_nonpar_xx|x_nonpar_xy|y_nonpar_xy
              --human-ploidy-build GRCh37|GRCh38 --ploidy-samples FILE
                                           Local id/chromosome_complement table (XX/XY)
              --haploid-source-scale observed|haploid|diploid (default observed)
              --invalid-ploidy-calls error|missing --unknown-ploidy-samples error|exclude
                                           One independent OLS/GLM contrast; physical allele copies
                                           and local audits. PAR uses X coordinates. Haploid HWE undefined.
              --transform SPEC             Row-wise <omics> stages, left to right
                Built-ins: identity(), winsor(...), winsor_mad(k=4),
                log1p(), log(offset=...), zscore(), int(), mvalue(epsilon=...)
                Guide: https://robbyjo.github.io/OmicsMatic/vignettes/omics-transforms.html
              --transform-plugin JAR       Trusted Java transform provider
              --omics-type auto|gwas|ewas|expression|proteomics|generic
              --annot FILE --annot-id COLUMN --annot-cols c1,c2,...
              --case-value VALUE --control-value VALUE
              Phenotype rows missing any model variable are omitted.

            LD reference databases:
              ld-db list                  Show freely available choices
              ld-db download              Download and install a choice
              --database NAME             Required database identifier
              --location DIRECTORY        Install directory; default is .

            MR instrument preparation:
              mr-instruments search       Find downloadable GWAS by trait
              mr-instruments download     Stream significant MR candidates
              mr-instruments format       Normalize user GWAS/QTL columns

            LD clumping:
              clump --database DIRECTORY --instrument FILE --output FILE
              --population PANEL          AFR, AMR, EAS, EUR (default), or SAS
              --clump-kb N                Window in kilobases; default 10000
              --ld-threshold X            LD r-squared cutoff; default 0.001
              --p-threshold X             Index p-value cutoff; default 1
              --overwrite                 Replace an existing output file

            Parallel xWAS MR:
              mr-xwas --exposure FILE --outcome FILE --output FILE
              --p-threshold X             Retain screening p <= X
              --threads N                 Bounded exposure-outcome workers
              --pair-block-size N         Maximum resident pair evaluations
              Run mr-xwas --help for log-scale thresholds and table columns.

            Joint-model MR terminology:
              mr-mvmr                    Multiple exposures, exactly one outcome
              mr-multivariate            One or more exposures, multiple correlated outcomes
              mr-xwas                    Parallel independent exposure-outcome pairs; not a joint model
            """;
    }
}
