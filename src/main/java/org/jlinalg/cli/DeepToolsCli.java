/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.BedGraphSignals;
import org.jlinalg.genetics.DeepToolsSignals;
import static org.jlinalg.cli.FollowupSupport.*;

/** Explicit native supplied-track transformations; original-package workflows are separate. */
final class DeepToolsCli {
    private DeepToolsCli() { }
    private static final Set<String> COMMON=Set.of("method","out","input","sizes","sizes-format","genome-build","bin-size");
    private static final Set<String> COMPARE=Set.of("input2","operation","scale1","scale2","pseudo1","pseudo2","missing","invalid","skip-zero-zero","max-bins");
    private static final Set<String> SCALE=Set.of("normalization","mapped-alignments","effective-genome-size","represented-length","scale-factor","count-kind");
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        for(int i=0;i+1<args.length;i++)if(args[i].equals("--method")&&DeepToolsUpstreamCli.METHODS.contains(args[i+1]))return DeepToolsUpstreamCli.run(args,out,err);
        for(int i=0;i+1<args.length;i++)if(args[i].equals("--method")&&DeepToolsMatrixCli.METHODS.contains(args[i+1]))return DeepToolsMatrixCli.run(args,out,err);
        try {
            var allowed=new HashSet<>(COMMON);allowed.addAll(COMPARE);allowed.addAll(SCALE);var o=options(args,allowed);String method=required(o,"method");boolean compare=method.equals("compare-native");
            if(!compare&&!method.equals("scale-native"))throw new IllegalArgumentException("Unknown deep-tools method; supported native methods:compare-native,scale-native");
            for(String key:(compare?SCALE:COMPARE))if(o.containsKey(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            for(String key:List.of("input","sizes","genome-build","bin-size"))required(o,key);long bin=positive(required(o,"bin-size"),"bin-size");String sizesFormat=o.getOrDefault("sizes-format","sizes");if(!Set.of("sizes","fai").contains(sizesFormat))throw new IllegalArgumentException("sizes-format must be sizes or fai");
            if(compare){for(String k:List.of("input2","operation","missing","invalid"))required(o,k);if(!Set.of("zero","covered","error").contains(o.get("missing"))||!Set.of("error","retain").contains(o.get("invalid")))throw new IllegalArgumentException("missing requires zero/covered/error; invalid requires error/retain");}
            else{required(o,"normalization");required(o,"count-kind");if(!Set.of("raw","estimate").contains(o.get("count-kind")))throw new IllegalArgumentException("count-kind must explicitly be raw or estimate");}
            int[] code={0};transaction(o,"deep-tools-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method",method);session.put("engine","native-java");session.put("genome_build_declared",o.get("genome-build"));session.put("scope","Supplied bedGraph and exact reference only. No BAM counting/filtering, depth/fragment estimation, BigWig I/O, feature matrices, plotting, historical or full deepTools parity.");
                try{
                    var sources=new LinkedHashMap<Path,String>();Path first=source(o.get("input"),sources),sizes=source(o.get("sizes"),sources);var ref=GenomicSignalCli.reference(sizes,sizesFormat);var segments=GenomicSignalCli.signals(first);var track=new BedGraphSignals.Track(ref,segments);session.put("source_hashes",sources.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),Map.Entry::getValue)));session.put("nominal_bin_size",bin);
                    if(compare){Path second=source(o.get("input2"),sources);var other=new BedGraphSignals.Track(ref,GenomicSignalCli.signals(second));compare(dir,o,ref,track,other,bin,session,warnings);}
                    else scale(dir,o,ref,segments,bin,session,warnings);
                    for(var e:sources.entrySet())if(!hash(e.getKey()).equals(e.getValue()))throw new IOException("Source changed during analysis:"+e.getKey());session.put("source_hashes",sources.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),Map.Entry::getValue)));Files.copy(sizes,dir.resolve(sizesFormat.equals("fai")?"reference.fai":"reference.sizes"));manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                }catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(dir);warnings.add(0,"ANALYSIS FAILED:"+failure.getMessage());}
                if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==0?"SIGNAL TRANSFORM WARNINGS REQUIRE REVIEW":"SIGNAL TRANSFORM FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action:"+(code[0]==0?"Review missing/nonfinite/omitted bins,input units and declared normalization denominator/length before downstream analysis. Original inputs remain unchanged.":"Correct the named source/reference/bin-grid, count/denominator or comparison contract. Select explicit missing/nonfinite handling; never invent counts or a denominator. Rerun to a new output directory. Failed partials remain under partial/; no successful results.tsv is published.")+"\n");session.put("warnings",warnings);Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
            });if(code[0]!=0||Files.exists(Path.of(o.get("out"),"WARNINGS.md")))err.println((code[0]==0?"ANALYSIS WARNINGS:":"ANALYSIS FAILED:")+"see "+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Native signal transform completed:"+o.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg:"+failure.getMessage());return 2;}
    }
    private static Path source(String name,Map<Path,String> hashes)throws IOException {Path p=RnaToolRuntime.file(name);String value=hash(p),previous=hashes.putIfAbsent(p,value);if(previous!=null&&!previous.equals(value))throw new IOException("Source changed during preparation:"+p);return p;}
    private static long positive(String text,String name){long n=Long.parseLong(text);if(n<1)throw new IllegalArgumentException(name+" must be positive");return n;}
    private static boolean flag(Map<String,String> o,String key){String v=o.getOrDefault(key,"false");if(!Set.of("true","false").contains(v))throw new IllegalArgumentException(key+" requires true/false");return Boolean.parseBoolean(v);}
    private static String format(double n){return Double.isFinite(n)?Double.toString(n):"NA";}
    private static double number(Map<String,String> o,String key,double fallback){double n=Double.parseDouble(o.getOrDefault(key,Double.toString(fallback)));if(!Double.isFinite(n))throw new IllegalArgumentException(key+" must be finite");return n;}
    private static double mean(BedGraphSignals.Summary s,String missing)throws IOException {if(missing.equals("error")&&s.coveredBases()!=s.bases())throw new IOException("Unrepresented signal bases; provide complete signal or choose an explicit zero/covered policy");return missing.equals("zero")?s.meanIncludingZeros():s.meanOverCovered();}
    private static void compare(Path dir,Map<String,String> o,Map<String,Long> ref,BedGraphSignals.Track first,BedGraphSignals.Track second,long bin,Map<String,Object> session,List<String> warnings)throws Exception {
        var operation=DeepToolsSignals.Operation.valueOf(o.get("operation").toUpperCase(Locale.ROOT));var parameters=new DeepToolsSignals.Comparison(operation,number(o,"scale1",1),number(o,"scale2",1),number(o,"pseudo1",0),number(o,"pseudo2",0));long limit=positive(o.getOrDefault("max-bins","1000000"),"max-bins"),count=0;
        for(long length:ref.values()){long n=(length-1)/bin+1;if(n>limit-count)throw new IOException("Reference/bin grid exceeds max-bins; explicitly choose a suitable bin width or resource limit");count+=n;}
        boolean skip=flag(o,"skip-zero-zero");long omitted=0,invalid=0,gaps=0,finite=0;
        try(var table=Files.newBufferedWriter(dir.resolve("results.tsv"));var signal=Files.newBufferedWriter(dir.resolve("signal.bedgraph"))){table.write("chromosome\tstart\tend\tfirst_covered_bases\tsecond_covered_bases\tfirst_mean\tsecond_mean\tvalue\tstatus\n");
            for(var chr:ref.entrySet())for(long start=0;start<chr.getValue();){long end=start+Math.min(bin,chr.getValue()-start);var a=first.summarize(chr.getKey(),start,end);var b=second.summarize(chr.getKey(),start,end);if(a.coveredBases()!=a.bases()||b.coveredBases()!=b.bases())gaps++;double x=mean(a,o.get("missing")),y=mean(b,o.get("missing"));String state;double value=Double.NaN;
                if(skip&&x==0&&y==0){state="omitted-zero-over-zero";omitted++;}else{var result=parameters.apply(x,y);state=result.status().name();value=result.value();if(result.status()!=DeepToolsSignals.Status.FINITE){invalid++;if(o.get("invalid").equals("error"))throw new IOException("Missing/nonfinite comparison at"+chr.getKey()+":"+start+"-"+end+"; inspect signal/domain,pseudocount or select --invalid retain explicitly");}else{finite++;bedgraph(signal,chr.getKey(),start,end,value);}}
                table.write(chr.getKey()+"\t"+start+"\t"+end+"\t"+a.coveredBases()+"\t"+b.coveredBases()+"\t"+format(x)+"\t"+format(y)+"\t"+(Double.isFinite(value)?Double.toString(value):"NA")+"\t"+state+"\n");start=end;}
        }
        session.put("comparison",parameters.toString());session.put("bins",count);session.put("finite_bins",finite);session.put("omitted_zero_bins",omitted);session.put("missing_or_nonfinite_bins",invalid);session.put("bins_with_unrepresented_bases",gaps);session.put("missing_policy",o.get("missing"));session.put("invalid_policy",o.get("invalid"));
        if(gaps>0)warnings.add("UNREPRESENTED BASES:"+gaps+"bins used explicit missing="+o.get("missing")+". Zero filling and covered-base means are different estimands.");if(invalid>0)warnings.add("NONFINITE/MISSING BINS:"+invalid+"retained as NA with status in results.tsv and omitted from signal.bedgraph; no zero was invented.");if(omitted>0)warnings.add("ZERO-OVER-ZERO FILTER:"+omitted+"bins omitted before scale/pseudocount arithmetic, retained with status in results.tsv.");if(finite==0)warnings.add("NO FINITE COMPARISON BINS:signal.bedgraph is empty; review input coverage,missing policy,domain and filters.");
    }
    private static void scale(Path dir,Map<String,String> o,Map<String,Long> ref,List<BedGraphSignals.Segment> input,long bin,Map<String,Object> session,List<String> warnings)throws Exception {
        var norm=DeepToolsSignals.Normalization.valueOf(o.get("normalization").toUpperCase(Locale.ROOT));if(norm!=DeepToolsSignals.Normalization.NONE)required(o,"mapped-alignments");else if(o.containsKey("mapped-alignments"))throw new IOException("mapped-alignments does not apply to normalization NONE");
        if(norm==DeepToolsSignals.Normalization.RPGC){required(o,"effective-genome-size");required(o,"represented-length");}else if(o.containsKey("effective-genome-size")||o.containsKey("represented-length"))throw new IOException("effective-genome-size/represented-length apply only to RPGC");
        double n=number(o,"mapped-alignments",0),length=number(o,"represented-length",0),multiplier=number(o,"scale-factor",1);long effective=o.containsKey("effective-genome-size")?positive(o.get("effective-genome-size"),"effective-genome-size"):0;double factor=DeepToolsSignals.coverageScale(norm,n,bin,effective,length,multiplier);if(input.isEmpty())throw new IOException("No supplied bin-count segments; no counts or denominator are inferred");
        var order=new HashMap<String,Integer>();for(String chr:ref.keySet())order.put(chr,order.size());input.sort(Comparator.comparingInt((BedGraphSignals.Segment s)->order.get(s.chromosome())).thenComparingLong(BedGraphSignals.Segment::start));boolean raw=o.get("count-kind").equals("raw");
        try(var table=Files.newBufferedWriter(dir.resolve("results.tsv"));var signal=Files.newBufferedWriter(dir.resolve("signal.bedgraph"))){table.write("chromosome\tstart\tend\tinput_bin_count\tscale_factor\tvalue\n");for(var s:input){if(s.start()%bin!=0||s.end()%bin!=0&&s.end()!=ref.get(s.chromosome()))throw new IOException("Bin-count segments must align to the declared nominal bin grid,except the terminal reference boundary");if(s.value()<0||raw&&s.value()!=Math.rint(s.value()))throw new IOException("Require nonnegative counts; raw mode requires integers. Declare count-kind estimate only for justified weighted/processed counts");double value=s.value()*factor;if(!Double.isFinite(value))throw new IOException("Scaled signal exceeds finite range");table.write(s.chromosome()+"\t"+s.start()+"\t"+s.end()+"\t"+s.value()+"\t"+factor+"\t"+value+"\n");bedgraph(signal,s.chromosome(),s.start(),s.end(),value);}}
        session.put("normalization",norm.name());session.put("mapped_alignments_declared",n);session.put("represented_length_declared",length);session.put("effective_genome_bases_declared",effective);session.put("coverage_multiplier",factor);session.put("count_kind",o.get("count-kind"));warnings.add("SUPPLIED NORMALIZATION CONTRACT:bin counts,kept alignment depth,nominal bin width and RPGC represented length/effective genome are declared inputs. No BAM filtering,blacklist adjustment,fragment estimation or read-versus-fragment inference was performed.");if(!raw)warnings.add("PROCESSED/WEIGHTED COUNT ESTIMATES:input is explicitly not asserted to be raw integer alignment counts.");if(norm==DeepToolsSignals.Normalization.BPM)warnings.add("BPM follows deepTools3.5.6 bamCoverage's kept-alignment scaling function; it is not an inferred sum-of-all-bin-counts TPM normalization.");if(multiplier==0)warnings.add("ZERO EXPLICIT MULTIPLIER:all output values are zero by the supplied choice.");
    }
    private static void bedgraph(BufferedWriter out,String chr,long start,long end,double value)throws IOException {out.write(chr+"\t"+start+"\t"+end+"\t"+value+"\n");}
    static String help(){return """
        deep-tools --method compare-native|scale-native --input TRACK.bedgraph --sizes EXACT.sizes
          --sizes-format sizes|fai --genome-build DECLARED_BUILD --bin-size BASES --out NEW_DIR
        Native Java,local plain tab-delimited bedGraph only. No BAM/BigWig import or external runtime.
        compare-native: --input2 TRACK --operation ratio|log2|reciprocal_ratio|subtract|add|first|second|mean
          --missing zero|covered|error --invalid error|retain are required.
          --scale1 1 --scale2 1 --pseudo1 0 --pseudo2 0 --skip-zero-zero false --max-bins 1000000.
          Whole explicit reference grid; final bins can be shorter. Scale,then pseudocounts for ratios.
          Any missing input propagates,even for first/second. Invalid retain keeps NA/status in
          results.tsv and omits that bin from signal.bedgraph; no zero is invented. Zero-over-zero
          filtering precedes scaling/pseudocounts. Missing zero and covered-base means differ.
        scale-native: supplied per-bin alignment-count bedGraph; --normalization NONE|CPM|RPKM|BPM|RPGC
          --count-kind raw|estimate required. Nonnegative raw counts must be integers.
          --mapped-alignments KEPT_ALIGNMENT_DEPTH required except NONE; --scale-factor 1.
          RPGC additionally --effective-genome-size BASES --represented-length BASES.
          No depth/fragment estimation or counts-from-BAM. Input/merged segments must align to
          the nominal bin grid except at reference ends; RPKM uses nominal width even there.
          NONE uses only the explicit multiplier. BPM follows original kept-alignment scaling.
        Both methods preserve original hashes,bounds,build declaration and source units. Results
          and finite float64 bedGraph are distinct from float32 binary BigWig export. Missing,
          omitted/nonfinite and declared normalization choices have prominent warnings/actions.
          Failure2 retains partials and no successful results.tsv. No automatic downstream analysis.
        """+DeepToolsUpstreamCli.help()+DeepToolsMatrixCli.help();}
}
