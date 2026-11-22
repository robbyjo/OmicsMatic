/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.ExpressionNormalization;
import org.jlinalg.sequence.GeneFeatures;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed featureCounts 2.1.1 counting plus native annotation/normalization helpers. */
final class FeatureCountsCli {
    private FeatureCountsCli() { }
    private static final Set<String> BASE = Set.of("method","out");
    private static final Set<String> ANNOTATION = Set.of("annotation","format","feature-type","group-attribute");
    private static final Set<String> COUNT = Set.of("home","inputs","library","unit","nh-policy","multimapping","overlap","fraction","primary-only","ignore-duplicates","mapq","min-overlap","overlap-fraction","feature-overlap-fraction","split","junctions","reference","both-ends","exclude-chimeric","check-distance","min-fragment","max-fragment","long-reads","threads","timeout");
    private record Sample(String id, Path alignment, int strand) { }
    static int run(String[] args, PrintStream output, PrintStream error) {
        if (Arrays.asList(args).contains("--help")) { output.println(help()); return 0; }
        try {
            var allowed = new HashSet<>(BASE); allowed.addAll(ANNOTATION); allowed.addAll(COUNT); allowed.addAll(Set.of("minimum-intron","counts","normalization","library-units"));
            var o = options(args,allowed); String method = required(o,"method");
            if (!Set.of("count","annotation","splice-sites","normalize").contains(method)) throw new IllegalArgumentException("Unknown feature-counts method");
            var applicable = new HashSet<>(BASE);
            if (method.equals("normalize")) applicable.addAll(Set.of("counts","normalization","library-units"));
            else { applicable.addAll(ANNOTATION); applicable.add("reference"); if (method.equals("count")) applicable.addAll(COUNT); if (method.equals("splice-sites")) applicable.add("minimum-intron"); }
            for (String key : o.keySet()) if (!applicable.contains(key)) throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            if (method.equals("count")) validateOptions(o);
            else if (method.equals("normalize")) { required(o,"counts"); required(o,"normalization"); }
            else { required(o,"annotation"); required(o,"reference"); }
            int[] code = {0};
            externalTransaction(o,"feature-counts-"+method,(dir,manifest)->{
                var session = new LinkedHashMap<String,Object>(); var warnings = new ArrayList<String>();
                session.put("scope","Raw meta-feature counts or explicitly requested native helpers. Gene union lengths are genomic, not inferred transcript effective lengths; no UMI deduplication, differential expression, automatic strand inference or Rsubread API parity.");
                try {
                    if (method.equals("count")) count(o,dir,manifest,session,warnings);
                    else if (method.equals("normalize")) normalize(o,dir,manifest);
                    else annotation(o,method,dir,manifest);
                    warnings.addAll(RnaToolRuntime.warnings(dir)); manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    if (!warnings.isEmpty()) Files.writeString(dir.resolve("WARNINGS.md"),"# COUNTING / ANNOTATION WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect the named assumptions, annotation, library units, strand and raw assignment totals before interpreting expression or peaks.\n");
                } catch (Exception failure) {
                    code[0] = failure instanceof RnaToolRuntime.Unavailable?3:2; manifest.put("analysis_status",code[0]==3?"skipped-dependency-unavailable":"failed"); manifest.put("error",String.valueOf(failure.getMessage()));
                    RnaToolRuntime.quarantine(dir);
                    Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==3?"ANALYSIS SKIPPED - FEATURECOUNTS UNAVAILABLE":"COUNTING / ANNOTATION FAILED")+"\n\n"+failure.getMessage()+"\n\nRequired action: "+(code[0]==3?RnaToolRuntime.setup("featurecounts"):"Correct the named input/option cause and inspect retained native logs. Rerun to a new output directory.")+" Partial artifacts are retained under partial/; no successful counts/results table is published.\n");
                }
                session.put("warnings",warnings); Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
                Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
            });
            if (code[0]!=0 || Files.exists(Path.of(o.get("out"),"WARNINGS.md"))) error.println((code[0]==3?"SKIPPED ANALYSIS":code[0]==2?"ANALYSIS FAILED":"ANALYSIS WARNINGS")+": see "+Path.of(o.get("out"),"WARNINGS.md"));
            if (code[0]==0) output.println("Feature/count operation completed: "+o.get("out")); return code[0];
        } catch (Exception failure) { error.println("jlinalg: "+failure.getMessage()); return 2; }
    }
    private static void validateOptions(Map<String,String> o) {
        for (String key : List.of("home","inputs","annotation","library","unit")) required(o,key);
        String library=required(o,"library"),unit=required(o,"unit");
        if (!Set.of("single","paired").contains(library) || !Set.of("reads","fragments").contains(unit) || library.equals("single")&&unit.equals("fragments")) throw new IllegalArgumentException("Require library single|paired and unit reads|fragments; fragments require paired data");
        if (!Set.of("require","assume-unique").contains(o.getOrDefault("nh-policy","require"))) throw new IllegalArgumentException("nh-policy must be require or assume-unique");
        if (!Set.of("exclude","include").contains(o.getOrDefault("multimapping","exclude")) || !Set.of("unique","all","largest").contains(o.getOrDefault("overlap","unique")) || !Set.of("all","split","non-split").contains(o.getOrDefault("split","all"))) throw new IllegalArgumentException("Invalid multimapping, overlap or split policy");
        for (String key : List.of("fraction","primary-only","ignore-duplicates","junctions","both-ends","exclude-chimeric","check-distance","long-reads")) bool(o,key,false);
        if (bool(o,"fraction",false) && !o.getOrDefault("multimapping","exclude").equals("include") && !o.getOrDefault("overlap","unique").equals("all")) throw new IllegalArgumentException("fraction requires multimapping include or overlap all");
        if (library.equals("single") && o.keySet().stream().anyMatch(Set.of("both-ends","exclude-chimeric","check-distance","min-fragment","max-fragment")::contains)) throw new IllegalArgumentException("Paired filtering options require paired data");
        if (!bool(o,"check-distance",false) && (o.containsKey("min-fragment")||o.containsKey("max-fragment"))) throw new IllegalArgumentException("Fragment thresholds require --check-distance true");
        if (integer(o,"max-fragment",600,0,10_000_000)<integer(o,"min-fragment",50,0,10_000_000)) throw new IllegalArgumentException("max-fragment must be >= min-fragment");
        int threads=integer(o,"threads",1,1,64); integer(o,"timeout",3600,1,86400); integer(o,"mapq",0,0,255); integer(o,"min-overlap",1,1,10_000_000);
        for (String key : List.of("overlap-fraction","feature-overlap-fraction")) { double x=number(o,key,0); if (x<0||x>1) throw new IllegalArgumentException(key+" must be in [0,1]"); }
        if (bool(o,"long-reads",false) && (threads!=1||!library.equals("single"))) throw new IllegalArgumentException("Long-read counting requires one thread, library single and unit reads");
        if (o.containsKey("reference") && !bool(o,"junctions",false)) throw new IllegalArgumentException("Count --reference is only used with --junctions true");
    }
    private static GeneFeatures.Annotation load(Map<String,String> o, Map<String,Long> dictionary) throws IOException {
        String format=o.getOrDefault("format","GTF");
        if (format.equals("SAF") && (o.containsKey("feature-type")||o.containsKey("group-attribute"))) throw new IllegalArgumentException("SAF already supplies GeneID; GTF selection options do not apply");
        var types=new HashSet<>(List.of(o.getOrDefault("feature-type","exon").split(",",-1)));
        if (types.stream().anyMatch(x->!x.matches("[A-Za-z_][A-Za-z0-9_.-]*"))) throw new IllegalArgumentException("Invalid feature-type selection");
        return GeneFeatures.read(RnaToolRuntime.file(required(o,"annotation")),format,types,o.getOrDefault("group-attribute","gene_id"),dictionary);
    }
    private static void annotation(Map<String,String> o,String method,Path dir,Map<String,Object> manifest) throws Exception {
        Path reference=RnaToolRuntime.file(required(o,"reference")),file=RnaToolRuntime.file(required(o,"annotation"));
        var hashes=Map.of(reference,hash(reference),file,hash(file));
        var annotation=load(o,RnaToolRuntime.dictionary(reference));
        if (method.equals("splice-sites")) {
            if (!o.getOrDefault("format","GTF").equals("GTF") || !o.getOrDefault("feature-type","exon").equals("exon") || !o.getOrDefault("group-attribute","gene_id").equals("gene_id")) throw new IllegalArgumentException("Splice extraction requires GTF exon features grouped by gene_id");
            int minimum=integer(o,"minimum-intron",5,1,Integer.MAX_VALUE);
            var sites=GeneFeatures.spliceSites(annotation,minimum); GeneFeatures.writeSpliceSites(sites,dir.resolve("splice-sites.tsv"));
            manifest.put("splice_boundary_convention","zero-based exonic flanks; minimum intron bases="+minimum);
            table(dir.resolve("results.tsv"),List.of("metric","value"),List.<String[]>of(new String[]{"splice_sites",Integer.toString(sites.size())}));
        } else {
            GeneFeatures.writeSaf(annotation,dir.resolve("features.saf"));
            var rows=new ArrayList<String[]>(); annotation.unionLengths().forEach((id,length)->rows.add(new String[]{id,Long.toString(length)}));
            table(dir.resolve("results.tsv"),List.of("feature_id","union_length"),rows);
        }
        RegenieRuntime.verify(hashes); manifest.put("source_sha256",stringHashes(hashes));
    }
    private static List<Sample> samples(Path input) throws IOException {
        var samples=new ArrayList<Sample>(); var seen=new HashSet<String>(); var paths=new HashSet<Path>();
        try (var reader=Files.newBufferedReader(input)) {
            if (!"sample\talignment\tstrand".equals(reader.readLine())) throw new IOException("Inputs TSV requires exact sample, alignment, strand header");
            String line; while ((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1);
                if (f.length!=3||!f[0].matches("[A-Za-z0-9_.-]{1,128}")||!seen.add(f[0])||!Set.of("0","1","2").contains(f[2])) throw new IOException("Invalid/duplicate sample or strand in inputs TSV");
                Path alignment=RnaToolRuntime.file(input.getParent().resolve(f[1]).normalize().toString());
                if (!paths.add(alignment.toRealPath())) throw new IOException("An alignment file was supplied under multiple samples");
                samples.add(new Sample(f[0],alignment,Integer.parseInt(f[2])));
                if (samples.size()>10000) throw new IOException("Inputs TSV exceeds 10000 samples");
            }
        }
        if (samples.isEmpty()) throw new IOException("Inputs TSV contains no samples"); return samples;
    }
    private static Map<String,Long> inspect(Sample sample,boolean paired,boolean requireNh,Map<String,Object> session,List<String> warnings) throws IOException {
        var dict=new LinkedHashMap<String,Long>(); long records=0,missing=0,mapped=0;
        try (var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(sample.alignment())) {
            if (reader.type()==htsjdk.samtools.SamReader.Type.CRAM_TYPE) throw new IOException("featureCounts input must be SAM or BAM, not CRAM");
            for (var seq:reader.getFileHeader().getSequenceDictionary().getSequences()) { if (seq.getSequenceLength()<1) throw new IOException("Input SAM dictionary lacks positive sequence lengths"); dict.put(seq.getSequenceName(),(long)seq.getSequenceLength()); }
            if (dict.isEmpty()) throw new IOException("Alignment omits a reference dictionary");
            try (var it=reader.iterator()) { while (it.hasNext()) {
                var r=it.next(); records++;
                if (r.getReadPairedFlag()!=paired) throw new IOException("Alignment library flags differ from explicit single/paired declaration: "+sample.id());
                if (paired && r.getFirstOfPairFlag()==r.getSecondOfPairFlag()) throw new IOException("Paired alignment has invalid first/second mate flags");
                if (!r.getReadUnmappedFlag()) {
                    mapped++; Object nh=r.getAttribute("NH");
                    if (nh==null) missing++; else if (!(nh instanceof Number n)||n.longValue()<1||n.doubleValue()!=n.longValue()) throw new IOException("Mapped alignment has invalid NH multiplicity tag");
                }
            } }
        }
        if (records==0) throw new IOException("Alignment is empty: "+sample.id());
        if (missing>0&&requireNh) throw new IOException("Mapped records lack NH tags in "+sample.id()+". Supply aligner multiplicity tags, or explicitly --nh-policy assume-unique after reviewing the upstream alignment policy");
        if (missing>0) warnings.add("NH ASSUMPTION: "+sample.id()+" has "+missing+" mapped records without NH; upstream featureCounts treats untagged records as unique. This does not establish unique mapping.");
        session.put("input_"+sample.id(),Map.of("records",records,"mapped_records",mapped,"mapped_without_nh",missing,"strand",sample.strand())); return dict;
    }
    private static void count(Map<String,String> o,Path dir,Map<String,Object> manifest,Map<String,Object> session,List<String> warnings) throws Exception {
        var runtime=new RnaToolRuntime(Path.of(required(o,"home")),"featurecounts",dir,session);
        Path input=RnaToolRuntime.file(required(o,"inputs")),source=RnaToolRuntime.file(required(o,"annotation"));
        var hashes=new LinkedHashMap<Path,String>(); hashes.put(input,hash(input)); hashes.put(source,hash(source));
        var samples=samples(input); Map<String,Long> dictionary=null;
        for (var sample:samples) {
            hashes.put(sample.alignment(),hash(sample.alignment()));
            var d=inspect(sample,o.get("library").equals("paired"),o.getOrDefault("nh-policy","require").equals("require"),session,warnings);
            if (dictionary==null) dictionary=d; else if (!dictionary.equals(d)) throw new IOException("Input alignments have different exact reference names/lengths");
        }
        var annotation=load(o,dictionary); GeneFeatures.writeSaf(annotation,dir.resolve("features.saf"));
        var command=new ArrayList<>(List.of(runtime.binary("featureCounts"),"-F",o.getOrDefault("format","GTF"),"-a",source.toString(),"-o",dir.resolve("raw-counts.tsv").toString(),"-T",Integer.toString(integer(o,"threads",1,1,64)),"-s",String.join(",",samples.stream().map(s->Integer.toString(s.strand())).toList()),"-Q",Integer.toString(integer(o,"mapq",0,0,255)),"--minOverlap",Integer.toString(integer(o,"min-overlap",1,1,10_000_000)),"--fracOverlap",Double.toString(number(o,"overlap-fraction",0)),"--fracOverlapFeature",Double.toString(number(o,"feature-overlap-fraction",0)),"--tmpDir",dir.toString()));
        // Keep the original GTF for upstream junction/transcript annotation; SAF is an audit export.
        if (o.getOrDefault("format","GTF").equals("GTF")) command.addAll(List.of("-t",o.getOrDefault("feature-type","exon"),"-g",o.getOrDefault("group-attribute","gene_id")));
        // -p alone has not counted fragments since Subread 2.0.2.
        if (o.get("library").equals("paired")) command.add("-p"); if (o.get("unit").equals("fragments")) command.add("--countReadPairs");
        if (o.getOrDefault("multimapping","exclude").equals("include")) command.add("-M");
        if (bool(o,"primary-only",false)&&bool(o,"fraction",false)&&o.getOrDefault("multimapping","exclude").equals("include")) warnings.add("PRIMARY/FRACTION POLICY: upstream Subread 2.1.1 disables NH-based fractional weighting with --primary; retained primary alignments receive whole mapping weight. Fractional allocation across overlapping meta-features still applies with -O.");
        if (o.getOrDefault("overlap","unique").equals("all")) command.add("-O"); if (o.getOrDefault("overlap","unique").equals("largest")) command.add("--largestOverlap");
        for (var entry:Map.of("fraction","--fraction","primary-only","--primary","ignore-duplicates","--ignoreDup","junctions","-J","both-ends","-B","exclude-chimeric","-C","long-reads","-L").entrySet()) if (bool(o,entry.getKey(),false)) command.add(entry.getValue());
        if (bool(o,"check-distance",false)) command.addAll(List.of("-P","-d",o.getOrDefault("min-fragment","50"),"-D",o.getOrDefault("max-fragment","600")));
        String split=o.getOrDefault("split","all"); if (!split.equals("all")) command.add(split.equals("split")?"--splitOnly":"--nonSplitOnly");
        if (o.containsKey("reference")) {
            Path reference=RnaToolRuntime.file(o.get("reference")); hashes.put(reference,hash(reference));
            if (!RnaToolRuntime.dictionary(reference).equals(dictionary)) throw new IOException("Junction FASTA dictionary differs from the alignments"); command.addAll(List.of("-G",reference.toString()));
        }
        for (var sample:samples) command.add(sample.alignment().toString());
        runtime.execute(command,dir,"count",null,integer(o,"timeout",3600,1,86400));
        parseCounts(dir.resolve("raw-counts.tsv"),dir.resolve("counts.tsv"),annotation,samples);
        parseSummary(dir.resolve("raw-counts.tsv.summary"),dir.resolve("assignment-summary.tsv"),samples,warnings);
        if (bool(o,"junctions",false)) {
            Path junctions=dir.resolve("raw-counts.tsv.jcounts");
            if (!Files.isRegularFile(junctions)) throw new IOException("Upstream omitted requested junction counts");
            parseJunctions(junctions,dir.resolve("junction-counts.tsv"),samples,dictionary);
            session.put("junction_table","raw-counts.tsv.jcounts; upstream coordinates/strand and sample columns retained");
        }
        manifest.put("source_sha256",stringHashes(hashes)); session.put("library",o.get("library")); session.put("count_unit",o.get("unit")); session.put("meta_features",annotation.unionLengths().size());
        session.put("paired_contract","Input flags and SAM/BAM structure checked; upstream pairs/sorts coordinate-sorted inputs. Completeness of the original sequencing library is not inferred.");
        RegenieRuntime.verify(hashes); runtime.verify();
        table(dir.resolve("results.tsv"),List.of("metric","value"),List.<String[]>of(new String[]{"samples",Integer.toString(samples.size())},new String[]{"meta_features",Integer.toString(annotation.unionLengths().size())},new String[]{"count_unit",o.get("unit")}));
    }
    private static void parseCounts(Path input,Path output,GeneFeatures.Annotation annotation,List<Sample> samples) throws IOException {
        var seen=new HashSet<String>(); var rows=new ArrayList<String[]>();
        try (var reader=Files.newBufferedReader(input)) {
            String line; do { line=reader.readLine(); } while (line!=null&&line.startsWith("#"));
            if (line==null) throw new IOException("Missing upstream count header"); String[] header=line.split("\t",-1);
            if (header.length!=6+samples.size()||!String.join("\t",Arrays.copyOf(header,6)).equals("Geneid\tChr\tStart\tEnd\tStrand\tLength")) throw new IOException("Unexpected upstream count header");
            verifySampleColumns(header,6,samples);
            while ((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1); if (f.length!=header.length||!seen.add(f[0])||!annotation.unionLengths().containsKey(f[0])) throw new IOException("Upstream count rows changed feature identities/columns");
                if (Long.parseLong(f[5])!=annotation.unionLengths().get(f[0])) throw new IOException("Upstream gene length differs from native genomic union length: "+f[0]);
                var row=new ArrayList<>(List.of(f[0],f[5])); for (int i=6;i<f.length;i++) { value(f[i]); row.add(f[i]); } rows.add(row.toArray(String[]::new));
            }
        }
        if (!seen.equals(annotation.unionLengths().keySet())) throw new IOException("Upstream omitted annotated meta-features");
        var header=new ArrayList<>(List.of("feature_id","length")); samples.forEach(s->header.add(s.id())); table(output,header,rows);
    }
    private static void verifySampleColumns(String[] header,int offset,List<Sample> samples) throws IOException {
        for (int i=0;i<samples.size();i++) if (!Path.of(header[i+offset]).toAbsolutePath().normalize().equals(samples.get(i).alignment())) throw new IOException("Upstream changed sample file columns/order");
    }
    private static void parseSummary(Path input,Path output,List<Sample> samples,List<String> warnings) throws IOException {
        var rows=new ArrayList<String[]>(); var seen=new HashSet<String>();
        try (var reader=Files.newBufferedReader(input)) {
            String line=reader.readLine(); if (line==null) throw new IOException("Missing count assignment summary"); String[] header=line.split("\t",-1);
            if (header.length!=samples.size()+1||!header[0].equals("Status")) throw new IOException("Unexpected assignment summary header"); verifySampleColumns(header,1,samples);
            while ((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1); if (f.length!=header.length||!seen.add(f[0])) throw new IOException("Invalid assignment summary row");
                for (int i=1;i<f.length;i++) { double n=value(f[i]); if (n!=Math.rint(n)) throw new IOException("Assignment totals must be whole units"); if (f[0].equals("Assigned")&&n==0) warnings.add("ZERO ASSIGNED UNITS: "+samples.get(i-1).id()+"; inspect assembly, strand, overlap and filtering before downstream analysis."); }
                rows.add(f);
            }
        }
        if (!seen.contains("Assigned")) throw new IOException("Summary omits Assigned units"); var header=new ArrayList<>(List.of("status")); samples.forEach(s->header.add(s.id())); table(output,header,rows);
    }
    private static void parseJunctions(Path input,Path output,List<Sample> samples,Map<String,Long> dictionary) throws IOException {
        var rows=new ArrayList<String[]>(); String[] header;
        try (var reader=Files.newBufferedReader(input)) {
            String line=reader.readLine(); if (line==null) throw new IOException("Missing junction header"); header=line.split("\t",-1);
            String prefix="Gene_SP1\tGene_SP2\tTranscript\tStatus\tDonor\tAcceptor\tChr_SP1\tLocation_SP1\tStrand_SP1\tChr_SP2\tLocation_SP2\tStrand_SP2\tNearestExonBoundary_SP1\tNearestExonBoundary_SP2";
            if (header.length!=14+samples.size()||!String.join("\t",Arrays.copyOf(header,14)).equals(prefix)) throw new IOException("Unexpected Subread 2.1.1 junction header");
            verifySampleColumns(header,14,samples);
            while ((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1); if (f.length!=header.length) throw new IOException("Invalid junction columns");
                for (int col:List.of(6,9)) {
                    Long length=dictionary.get(f[col]); long p=Long.parseLong(f[col+1]);
                    if (length==null||p<1||p>length||!Set.of("+","-","NA",".").contains(f[col+2])) throw new IOException("Junction differs from the exact reference/strand contract");
                }
                for (int i=14;i<f.length;i++) value(f[i]); rows.add(f);
            }
        }
        for (int i=0;i<samples.size();i++) header[14+i]=samples.get(i).id(); table(output,List.of(header),rows);
    }
    private static double value(String text) throws IOException {
        try { double x=Double.parseDouble(text); if (!Double.isFinite(x)||x<0) throw new NumberFormatException(); return x; }
        catch (NumberFormatException failure) { throw new IOException("Count values must be finite and nonnegative"); }
    }
    private static void normalize(Map<String,String> o,Path dir,Map<String,Object> manifest) throws Exception {
        Path input=RnaToolRuntime.file(required(o,"counts")); String before=hash(input),method=required(o,"normalization");
        if (!Set.of("tpm","rpkm","fpkm").contains(method)) throw new IllegalArgumentException("normalization must be tpm, rpkm or fpkm");
        if (method.equals("tpm")&&o.containsKey("library-units")) throw new IllegalArgumentException("TPM denominator is the supplied feature universe; library-units does not apply");
        var rows=new ArrayList<String[]>(); String[] header;
        try (var reader=Files.newBufferedReader(input)) {
            String line=reader.readLine(); if (line==null) throw new IOException("Empty counts TSV"); header=line.split("\t",-1);
            if (header.length<3||!header[0].equals("feature_id")||!header[1].equals("length")||new HashSet<>(List.of(header)).size()!=header.length) throw new IOException("Counts require feature_id, length and unique sample columns");
            var seen=new HashSet<String>();
            while ((line=reader.readLine())!=null) { String[] f=line.split("\t",-1); if (f.length!=header.length||f[0].isBlank()||!seen.add(f[0])) throw new IOException("Invalid/duplicate feature count row"); rows.add(f); }
        }
        long[] lengths=new long[rows.size()]; for (int i=0;i<lengths.length;i++) lengths[i]=Long.parseLong(rows.get(i)[1]);
        String[] units=method.equals("tpm")?null:required(o,"library-units").split(",",-1);
        if (units!=null&&units.length!=header.length-2) throw new IllegalArgumentException("library-units requires one explicit read/fragment denominator per sample column");
        for (int j=2;j<header.length;j++) {
            double[] counts=new double[rows.size()]; for (int i=0;i<counts.length;i++) counts[i]=value(rows.get(i)[j]);
            double[] normalized=method.equals("tpm")?ExpressionNormalization.tpm(counts,lengths):ExpressionNormalization.perKilobaseMillion(counts,lengths,Double.parseDouble(units[j-2]));
            for (int i=0;i<rows.size();i++) rows.get(i)[j]=Double.toString(normalized[i]);
        }
        table(dir.resolve("results.tsv"),List.of(header),rows); if (!before.equals(hash(input))) throw new IOException("Counts changed during normalization");
        manifest.put("counts_sha256",before); manifest.put("normalization",method); manifest.put("denominator",method.equals("tpm")?"Supplied feature universe, counts divided by genomic union lengths":required(o,"library-units"));
    }
    private static Map<String,String> stringHashes(Map<Path,String> hashes) { var result=new LinkedHashMap<String,String>(); hashes.forEach((p,h)->result.put(p.toString(),h)); return result; }
    static String help() { return """
        feature-counts --method count|annotation|splice-sites|normalize --out NEW_DIR
        count: --home HOME (bin/featureCounts 2.1.1) --inputs samples.tsv --annotation FILE
          --library single|paired --unit reads|fragments. Inputs exact TSV header:
          sample<TAB>alignment<TAB>strand (0 unstranded, 1 same, 2 reverse); relative SAM/BAM
          paths resolve beside the TSV. Explicit library units prevent the post-2.0.2 -p ambiguity.
          --format GTF|SAF [GTF: --feature-type exon --group-attribute gene_id]. Native validation
          uses exact reference names/lengths, no aliases; exon transcript_id is optional. The original
          annotation is supplied upstream; features.saf is an audit export, preserving GTF metadata.
          --nh-policy require|assume-unique (default require); --multimapping exclude|include;
          --overlap unique|all|largest --fraction false --primary-only false --ignore-duplicates false
          --mapq 0 --min-overlap 1 --overlap-fraction 0 --feature-overlap-fraction 0
          --split all|split|non-split --junctions false [--reference ref.fa only with junctions]
          paired: --both-ends false --exclude-chimeric false --check-distance false
          [--min-fragment 50 --max-fragment 600 when checking distance].
          --long-reads false (true requires single/reads/one thread) --threads 1 --timeout 3600.
          Counts are per meta-feature (gene/peak), retaining all raw upstream count/summary/junction
          tables and canonical counts.tsv / assignment-summary.tsv. NH exclusion depends on NH tags;
          --primary excludes SAM bit 0x100, and does not itself exclude supplementary bit 0x800.
          With --primary, upstream disables NH fractional weighting even if --fraction is selected;
          fractional allocation across overlapping features with -O remains active (prominent warning).
        annotation: --reference ref.fa --annotation FILE [same GTF/SAF selection], native features.saf
          and genomic union lengths (overlapping exons/isoforms counted once per gene/contig).
        splice-sites: --reference ref.fa --annotation exons.gtf [--minimum-intron 5]. Native unique
          transcript exon junctions; +/- strand required. Output is HISAT2 zero-based EXONIC flanks.
          Nested exons merge by maximum end; single-base exons are retained; malformed rows fail.
        normalize: --counts counts.tsv --normalization tpm|rpkm|fpkm
          [--library-units comma-separated explicit denominators, required for rpkm/fpkm].
          Counts header feature_id<TAB>length<TAB>sample...; TPM uses this complete feature universe.
          FPKM requires fragment counts AND fragment denominators; units are the caller's contract.
          These genomic-length values are separate from transcript abundance/effective-length models.
        No UMI deduplication, DESeq2/edgeR testing, EDASeq GC correction, inferred strand or Rsubread
          API implementation. Missing/wrong upstream runtime skips with exit 3; other failure exits 2.
          WARNINGS.md names the cause and required action; failures retain partial artifacts.
        """; }
}
