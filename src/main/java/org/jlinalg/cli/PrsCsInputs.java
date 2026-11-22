/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.PrsCsMath;

/** Streaming original-table intersection, explicit exclusions and native marginal effects. */
final class PrsCsInputs {
    private PrsCsInputs() { }
    record Limits(int variants,long rows,long inputBytes,long retainedBytes,int lineBytes,
            long ldCells,int blockSnps,int blocks,long reportBytes,long posteriorCells) {
        Limits {
            if(variants<1||rows<1||inputBytes<1||retainedBytes<16L*lineBytes||lineBytes<256
                    ||ldCells<1||blockSnps<1||blocks<1||reportBytes<1||posteriorCells<1)
                throw new IllegalArgumentException("Require positive PRS-CS resource ceilings and retained storage >=16 line buffers");
        }
    }
    record Request(Path summary,Path bim,Path reference,String kind,List<Integer> chromosomes,String format,
            long sampleSize,String unmatched,String palindromic,String strand,String positions,PrsCsMath.PValuePolicy pFloor) {
        Request {
            chromosomes=List.copyOf(chromosomes);
            if(chromosomes.isEmpty()||new HashSet<>(chromosomes).size()!=chromosomes.size()
                    ||chromosomes.stream().anyMatch(c->c<1||c>22)||sampleSize<1||sampleSize>9_007_199_254_740_991L
                    ||!Set.of("1kg","ukbb").contains(kind)||!Set.of("beta-se","or-se","beta-p","or-p").contains(format)
                    ||!Set.of("error","exclude-with-warning").contains(unmatched)
                    ||!Set.of("error","exclude-with-warning","allow-upstream-with-warning").contains(palindromic)
                    ||!Set.of("exact","allow-complement-with-warning").contains(strand)
                    ||!Set.of("require-match","reference-with-warning").contains(positions)||pFloor==null)
                throw new IllegalArgumentException("Invalid explicit PRS-CS input/model/exclusion policy");
        }
    }
    record Variant(int chromosome,String id,int position,char effectAllele,char otherAllele,double frequency,
            double marginal,int referenceSign,int summarySign,boolean palindrome,boolean requiredComplement,long targetLine) { }
    record Prepared(Request request,Limits limits,Map<Integer,List<Variant>> model,Map<String,Variant> byId,
            Map<String,Path> sources,Map<String,String> sourceHashes,Map<String,String> stagedHashes,
            Map<String,Long> counts,long estimatedRetained,long ledgerBytes,List<String> warnings) { }
    private record Target(int chromosome,int position,String a1,String a2,long line) { }
    private static final class Reference {
        final int chromosome,position;
        final String id,a1,a2;
        final double frequency;
        Target target;
        boolean targetSeen,summarySeen;
        String decision="TARGET_WITHOUT_SUMMARY";
        Variant result;
        Reference(int c,String id,int p,String a1,String a2,double f){chromosome=c;this.id=id;position=p;this.a1=a1;this.a2=a2;frequency=f;}
    }
    private static final class Budget {
        final Limits limits;
        long rows,inputBytes,retained,report;
        Budget(Limits limits,long hdfBytes){this.limits=limits;inputBytes=hdfBytes;retained=16L*limits.lineBytes();}
        void row()throws IOException{if(++rows>limits.rows())throw new IOException("PRS-CS combined input-row ceiling exceeded");}
        void retain(long bytes)throws IOException{retained=Math.addExact(retained,bytes);if(retained>limits.retainedBytes())throw new IOException("PRS-CS estimated retained Java input storage exceeds --maximum-retained-mib;choose a smaller explicit chromosome family or review the ceiling");}
        void write(BufferedWriter writer,String line)throws IOException{
            report=Math.addExact(report,line.getBytes(StandardCharsets.UTF_8).length);
            if(report>limits.reportBytes())throw new IOException("PRS-CS ledger/output byte ceiling exceeded");
            writer.write(line);
        }
    }

    static Prepared prepare(Request requested,Limits limits,Path directory)throws Exception {
        Path reference=requested.reference().toAbsolutePath().normalize();
        if(!Files.isDirectory(reference))throw new IOException("Missing original PRS-CS reference directory");
        String basename=reference.getFileName().toString();String actual=basename.contains("1kg")?"1kg":basename.contains("ukbb")?"ukbb":"";
        if(!requested.kind().equals(actual))throw new IOException("Original PRS-CS derives panel kind from its directory name;use a directory with the declared lowercase 1kg/ukbb naming and matching original files");
        var sources=new LinkedHashMap<String,Path>();
        sources.put("summary",requested.summary().toAbsolutePath().normalize());sources.put("target_bim",requested.bim().toAbsolutePath().normalize());
        sources.put("reference_info",reference.resolve("snpinfo_"+requested.kind()+"_hm3"));
        long sourceBytes=0,hdfBytes=0;
        for(int c:requested.chromosomes())sources.put("reference_chr_"+c,reference.resolve("ldblk_"+requested.kind()+"_chr"+c+".hdf5"));
        var hashes=new LinkedHashMap<String,String>();
        for(var entry:sources.entrySet()) {
            Path path=entry.getValue();if(!Files.isRegularFile(path))throw new IOException("Missing original PRS-CS source: "+path);
            long bytes=Files.size(path);sourceBytes=Math.addExact(sourceBytes,bytes);if(sourceBytes>limits.inputBytes())throw new IOException("PRS-CS combined source-file byte ceiling exceeded");
            if(entry.getKey().startsWith("reference_chr_"))hdfBytes=Math.addExact(hdfBytes,bytes);
            hashes.put(entry.getKey(),hash(path));
        }
        var budget=new Budget(limits,hdfBytes);var counts=new LinkedHashMap<String,Long>();
        var refs=new LinkedHashMap<String,Reference>();var chromosomes=new HashSet<>(requested.chromosomes());
        try(var input=open(sources.get("reference_info"),budget);var ledger=Files.newBufferedWriter(directory.resolve("reference-input-ledger.tsv"))) {
            budget.write(ledger,"source_line\tchromosome\tvariant\tposition\ta1\ta2\tfrequency_raw\tdecision\n");
            String[] header=fields(line(input,limits));
            if(header.length<6||!header[0].replace("#","").equalsIgnoreCase("CHR")||!header[1].equalsIgnoreCase("SNP")
                    ||!header[2].equalsIgnoreCase("BP")||!header[3].equalsIgnoreCase("A1")||!header[4].equalsIgnoreCase("A2")
                    ||!Set.of("MAF","AF").contains(header[5].toUpperCase(Locale.ROOT)))throw new IOException("Reference SNP info requires CHR SNP BP A1 A2 MAF/AF in its first six columns");
            long ordinal=1;
            for(String text;(text=line(input,limits))!=null;) {
                ordinal++;budget.row();String[] row=fields(text);if(row.length<6)throw new IOException("Reference SNP-info row has fewer than six fields at line "+ordinal);
                int c=positiveInt(row[0],"reference chromosome");token(row[1],"reference SNP ID");
                if(chromosomes.contains(c)) {
                    int position=positiveInt(row[2],"reference position");alleles(row[3],row[4]);double frequency=pythonNumber(row[5]);
                    if(refs.size()>=limits.variants())throw new IOException("Selected reference SNP family exceeds --maximum-model-variants");
                    budget.retain(1024L+4L*row[1].length());
                    if(refs.putIfAbsent(row[1],new Reference(c,row[1],position,row[3],row[4],frequency))!=null)throw new IOException("Duplicate selected reference SNP ID: "+row[1]);
                }
                budget.write(ledger,ordinal+"\t"+String.join("\t",java.util.Arrays.copyOf(row,6))+"\t"+(chromosomes.contains(c)?"REQUESTED_CHROMOSOME":"OUTSIDE_REQUESTED_CHROMOSOMES")+"\n");
                count(counts,"reference_rows",1);
            }
        }
        if(refs.isEmpty())throw new IOException("No reference SNP-info rows for the explicitly requested chromosome family");
        var targetOnly=new long[]{0};
        try(var input=open(sources.get("target_bim"),budget);var ledger=Files.newBufferedWriter(directory.resolve("target-input-ledger.tsv"));
                var decisions=Files.newBufferedWriter(directory.resolve("variant-ledger.tsv"))) {
            budget.write(ledger,"source_line\tchromosome\tvariant\tposition\ta1\ta2\tinitial_decision\n");
            budget.write(decisions,"target_source_line\tchromosome\tvariant\ttarget_position\treference_position\teffect_allele\tother_allele\toutput_expected\tdecision\n");
            long ordinal=0;
            for(String text;(text=line(input,limits))!=null;) {
                ordinal++;budget.row();String[] row=fields(text);if(row.length!=6)throw new IOException("Target BIM must have exactly six fields at line "+ordinal);
                int c=positiveInt(row[0],"target chromosome"),position=positiveInt(row[3],"target position");token(row[1],"target SNP ID");token(row[4],"target A1");token(row[5],"target A2");
                String initial="OUTSIDE_REQUESTED_CHROMOSOMES";
                if(chromosomes.contains(c)) {
                    count(counts,"requested_target_rows",1);Reference ref=refs.get(row[1]);
                    if(ref==null) {
                        initial="TARGET_NOT_IN_REFERENCE";targetOnly[0]++;
                        budget.write(decisions,ordinal+"\t"+c+"\t"+row[1]+"\t"+position+"\t\t"+row[4]+"\t"+row[5]+"\tfalse\t"+initial+"\n");
                        if(requested.unmatched().equals("error"))throw new IOException("Target SNP absent from requested reference family: "+row[1]+";review inputs or explicitly choose exclude-with-warning");
                    } else {
                        if(ref.targetSeen)throw new IOException("Duplicate target BIM SNP ID in the selected reference universe: "+row[1]);
                        ref.targetSeen=true;ref.target=new Target(c,position,row[4],row[5],ordinal);initial="REFERENCE_TARGET_CANDIDATE";
                    }
                }
                budget.write(ledger,ordinal+"\t"+c+"\t"+row[1]+"\t"+position+"\t"+row[4]+"\t"+row[5]+"\t"+initial+"\n");
                count(counts,"target_rows",1);
            }
        }
        try(var input=open(sources.get("summary"),budget);var ledger=Files.newBufferedWriter(directory.resolve("summary-input-ledger.tsv"));
                var stage=Files.newBufferedWriter(directory.resolve("summary.txt"))) {
            budget.write(ledger,"source_line\tvariant\ta1\ta2\tdecision\n");
            String[] header=fields(line(input,limits));String effect=requested.format().startsWith("or")?"OR":"BETA",uncertainty=requested.format().endsWith("-se")?"SE":"P";
            if(header.length<5||!header[0].equalsIgnoreCase("SNP")||!header[1].equalsIgnoreCase("A1")||!header[2].equalsIgnoreCase("A2")
                    ||!header[3].equalsIgnoreCase(effect)||!header[4].equalsIgnoreCase(uncertainty))throw new IOException("Summary first five columns must match declared SNP A1 A2 "+effect+" "+uncertainty+" format;extra annotations are not model inputs");
            budget.write(stage,"SNP A1 A2 "+effect+" "+uncertainty+"\n");long ordinal=1;
            for(String text;(text=line(input,limits))!=null;) {
                ordinal++;budget.row();String[] row=fields(text);if(row.length<5)throw new IOException("Summary row has fewer than five fields at line "+ordinal);
                token(row[0],"summary SNP ID");token(row[1],"summary A1");token(row[2],"summary A2");Reference ref=refs.get(row[0]);String decision="OUTSIDE_REQUESTED_TARGET_REFERENCE";
                if(ref!=null&&ref.target!=null&&ref.target.chromosome()==ref.chromosome) {
                    if(ref.summarySeen)throw new IOException("Duplicate summary SNP ID in the selected target/reference universe: "+row[0]);
                    ref.summarySeen=true;decision=match(ref,row,requested,counts);
                    if(ref.result!=null)budget.write(stage,String.join(" ",java.util.Arrays.copyOf(row,5))+"\n");
                    ref.decision=decision;
                }
                budget.write(ledger,ordinal+"\t"+row[0]+"\t"+row[1]+"\t"+row[2]+"\t"+decision+"\n");
                count(counts,"summary_rows",1);
            }
        }
        var byId=new LinkedHashMap<String,Variant>();var model=new LinkedHashMap<Integer,List<Variant>>();
        for(int c:requested.chromosomes())model.put(c,new ArrayList<>());
        long unmatched=targetOnly[0],palExcluded=0;
        try(var target=Files.newBufferedWriter(directory.resolve("target.bim"));var marginal=Files.newBufferedWriter(directory.resolve("native-marginals.tsv"));
                var ledger=Files.newBufferedWriter(directory.resolve("variant-ledger.tsv"),java.nio.file.StandardOpenOption.APPEND)) {
            budget.write(marginal,"chromosome\tvariant\tposition\teffect_allele\tother_allele\treference_effect_frequency\tstandardized_marginal\treference_sign\tsummary_sign\n");
            for(Reference ref:refs.values()) {
                if(ref.target==null)continue;
                if(ref.result==null) {
                    if(ref.target.chromosome()!=ref.chromosome)ref.decision="TARGET_REFERENCE_CHROMOSOME_MISMATCH";
                    if(ref.decision.equals("EXPLICIT_PALINDROMIC_EXCLUSION"))palExcluded++;else {
                        unmatched++;
                        if(requested.unmatched().equals("error"))throw new IOException("Unmatched requested target SNP "+ref.id+": "+ref.decision+";review model inputs or explicitly choose exclude-with-warning");
                    }
                } else {
                    Variant v=ref.result;model.get(v.chromosome()).add(v);byId.put(v.id(),v);budget.retain(256L+4L*v.id().length());
                    budget.write(target,v.chromosome()+" "+v.id()+" 0 "+ref.target.position()+" "+v.effectAllele()+" "+v.otherAllele()+"\n");
                    budget.write(marginal,v.chromosome()+"\t"+v.id()+"\t"+v.position()+"\t"+v.effectAllele()+"\t"+v.otherAllele()+"\t"+v.frequency()+"\t"+v.marginal()+"\t"+v.referenceSign()+"\t"+v.summarySign()+"\n");
                }
                budget.write(ledger,ref.target.line()+"\t"+ref.target.chromosome()+"\t"+ref.id+"\t"+ref.target.position()+"\t"+ref.position+"\t"+ref.target.a1()+"\t"+ref.target.a2()+"\t"+(ref.result!=null)+"\t"+ref.decision+"\n");
            }
        }
        for(var entry:model.entrySet()) {
            if(entry.getValue().isEmpty())throw new IOException("No eligible model SNPs on requested chromosome "+entry.getKey()+";no empty chromosome output is credited as fitted");
            entry.setValue(List.copyOf(entry.getValue()));
        }
        count(counts,"model_variants",byId.size());count(counts,"unmatched_target_exclusions",unmatched);count(counts,"explicit_palindromic_exclusions",palExcluded);
        var warnings=new ArrayList<String>();
        if(unmatched>0)warnings.add(unmatched+" requested target rows were excluded under explicit exclude-with-warning. Required action: review variant-ledger.tsv and both raw-input ledgers; the output is the declared target/reference/summary intersection.");
        if(palExcluded>0)warnings.add(palExcluded+" palindromic target SNPs were explicitly excluded. Required action: review the selected family and variant-ledger.tsv.");
        if(counts.getOrDefault("palindromic_admitted",0L)>0)warnings.add("Palindromic SNPs were explicitly admitted using original upstream branch order. This does not resolve biological strand; summary sign can differ from naive allele reversal. Required action: review original alleles and panel provenance before scoring.");
        if(counts.getOrDefault("complement_required",0L)>0)warnings.add("Some retained SNPs require strand complements under the explicit allow-complement-with-warning policy. Required action: review native-marginals.tsv and exact source allele/build provenance.");
        if(counts.getOrDefault("position_mismatches_admitted",0L)>0)warnings.add("Original reference positions were explicitly retained despite target-BIM position mismatches. Required action: reconcile coordinate/build provenance before applying these weights; no liftover or allele repair was performed.");
        if(counts.getOrDefault("pvalues_floored_upstream",0L)>0)warnings.add(counts.get("pvalues_floored_upstream")+" retained P values use original PRS-CS's 1e-323 floor under explicit permission. Raw tokens are preserved upstream. Required action: use BETA/OR plus SE with a compatible source profile when full extreme-signal information is needed; no substitute P-value inference was made.");
        var staged=new LinkedHashMap<String,String>();
        for(String name:List.of("target.bim","summary.txt","native-marginals.tsv","reference-input-ledger.tsv","target-input-ledger.tsv","summary-input-ledger.tsv","variant-ledger.tsv"))staged.put(name,hash(directory.resolve(name)));
        var result=new Prepared(requested,limits,Collections.unmodifiableMap(model),Collections.unmodifiableMap(byId),Map.copyOf(sources),Map.copyOf(hashes),Map.copyOf(staged),Map.copyOf(counts),budget.retained,budget.report,List.copyOf(warnings));
        verify(result,directory);return result;
    }

    private static String match(Reference ref,String[] row,Request request,Map<String,Long> counts)throws IOException {
        PrsCsMath.Alleles target,summary,reference;
        try {target=alleles(ref.target.a1(),ref.target.a2());summary=alleles(row[1],row[2]);reference=alleles(ref.a1,ref.a2);}
        catch(IOException failure){return "UNRESOLVED_OR_NON_SNP_TARGET_SUMMARY_ALLELES";}
        var orientation=PrsCsMath.align(reference,target,summary);
        if(!orientation.matched())return "ALLELE_MISMATCH";
        if(orientation.palindromic()) {
            if(request.palindromic().equals("error"))throw new IOException("Palindromic model SNP requires an explicit exclusion/upstream-ambiguity choice: "+ref.id);
            if(request.palindromic().equals("exclude-with-warning"))return "EXPLICIT_PALINDROMIC_EXCLUSION";
        }
        boolean complement=!sameOrReverse(reference,target)||!sameOrReverse(summary,target);
        if(complement&&request.strand().equals("exact"))return "STRAND_COMPLEMENT_NOT_ADMITTED";
        if(ref.target.position()!=ref.position&&request.positions().equals("require-match"))throw new IOException("Target/reference position mismatch at "+ref.id+";reconcile build or explicitly choose reference-with-warning");
        double effect=finiteNumber(row[3],"effect at "+ref.id),uncertainty=finiteNumber(row[4],"uncertainty at "+ref.id);
        try {
            double frequency=PrsCsMath.effectAlleleFrequency(ref.frequency,orientation);
            double marginal=switch(request.format()) {
                case "beta-se" -> PrsCsMath.fromBetaSe(effect,uncertainty,request.sampleSize(),orientation.summarySign());
                case "or-se" -> PrsCsMath.fromOddsRatioSe(effect,uncertainty,request.sampleSize(),orientation.summarySign());
                case "beta-p" -> PrsCsMath.fromBetaP(effect,uncertainty,request.sampleSize(),orientation.summarySign(),request.pFloor());
                case "or-p" -> PrsCsMath.fromOddsRatioP(effect,uncertainty,request.sampleSize(),orientation.summarySign(),request.pFloor());
                default -> throw new IllegalArgumentException("Unknown PRS-CS summary format");
            };
            ref.result=new Variant(ref.chromosome,ref.id,ref.position,target.first(),target.second(),frequency,marginal,orientation.referenceSign(),orientation.summarySign(),orientation.palindromic(),complement,ref.target.line());
        } catch(IllegalArgumentException failure){throw new IOException("Invalid retained SNP "+ref.id+": "+failure.getMessage(),failure);}
        if(orientation.palindromic())count(counts,"palindromic_admitted",1);
        if(complement)count(counts,"complement_required",1);
        if(ref.target.position()!=ref.position)count(counts,"position_mismatches_admitted",1);
        if(request.format().endsWith("-p")&&uncertainty<PrsCsMath.UPSTREAM_MINIMUM_P)count(counts,"pvalues_floored_upstream",1);
        return "MODEL_RETAINED";
    }
    private static boolean sameOrReverse(PrsCsMath.Alleles a,PrsCsMath.Alleles b){return a.first()==b.first()&&a.second()==b.second()||a.first()==b.second()&&a.second()==b.first();}
    private static PrsCsMath.Alleles alleles(String a1,String a2)throws IOException {
        if(a1.length()!=1||a2.length()!=1)throw new IOException("Require distinct uppercase A/C/G/T SNP alleles");
        try{return new PrsCsMath.Alleles(a1.charAt(0),a2.charAt(0));}catch(IllegalArgumentException failure){throw new IOException(failure.getMessage(),failure);}
    }
    private static void count(Map<String,Long> counts,String key,long n){counts.merge(key,n,Math::addExact);}
    private static int positiveInt(String value,String label)throws IOException {try{int n=Integer.parseInt(value);if(n>0)return n;}catch(NumberFormatException failure){throw new IOException("Invalid "+label,failure);}throw new IOException("Require positive "+label);}
    private static String[] fields(String text)throws IOException {if(text==null||text.isBlank())throw new IOException("Missing/blank PRS-CS table header or row");return text.strip().replaceFirst("^\uFEFF","").split("\\s+");}
    private static String line(InputStream input,Limits limits)throws IOException{return PlinkGlmInputs.line(input,limits.lineBytes());}
    private static void token(String value,String label)throws IOException {if(value.isEmpty()||value.length()>8192||value.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IOException("Invalid/oversized "+label);}
    private static double finiteNumber(String value,String label)throws IOException {double result=pythonNumber(value);if(!Double.isFinite(result))throw new IOException("Require finite "+label);return result;}
    private static double pythonNumber(String value)throws IOException {
        if(value.length()>256)throw new IOException("Oversized numeric token");
        return switch(value.toLowerCase(Locale.ROOT)) {
            case "nan","+nan","-nan" -> Double.NaN;
            case "inf","+inf","infinity","+infinity" -> Double.POSITIVE_INFINITY;
            case "-inf","-infinity" -> Double.NEGATIVE_INFINITY;
            default -> {
                if(!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require a decimal/scientific numeric token: "+value);
                try{yield Double.parseDouble(value);}catch(NumberFormatException failure){throw new IOException("Invalid numeric token",failure);}
            }
        };
    }
    private static InputStream open(Path path,Budget budget)throws IOException {
        var raw=new BufferedInputStream(Files.newInputStream(path));raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();InputStream decoded;
        try{decoded=a==31&&b==139?new GZIPInputStream(raw):raw;}catch(IOException failure){raw.close();throw failure;}
        var bounded=new FilterInputStream(decoded) {
            private void count(int bytes)throws IOException{if(bytes>0){budget.inputBytes=Math.addExact(budget.inputBytes,bytes);if(budget.inputBytes>budget.limits.inputBytes())throw new IOException("PRS-CS combined decompressed table/HDF5 source-byte ceiling exceeded");}}
            @Override public int read()throws IOException{int n=in.read();count(n<0?0:1);return n;}
            @Override public int read(byte[] buffer,int offset,int length)throws IOException{int n=in.read(buffer,offset,length);count(n);return n;}
        };
        return new BufferedInputStream(bounded,65536);
    }
    static void verify(Prepared input,Path directory)throws IOException {
        for(var entry:input.sources().entrySet())if(!hash(entry.getValue()).equals(input.sourceHashes().get(entry.getKey())))throw new IOException("PRS-CS original input changed: "+entry.getKey());
        for(var entry:input.stagedHashes().entrySet())if(!hash(directory.resolve(entry.getKey())).equals(entry.getValue()))throw new IOException("PRS-CS staged input/ledger changed: "+entry.getKey());
    }
}
