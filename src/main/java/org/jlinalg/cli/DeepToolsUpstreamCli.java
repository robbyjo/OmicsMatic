/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed local-file workflows through the pinned original deepTools runtime. */
final class DeepToolsUpstreamCli {
    private DeepToolsUpstreamCli() { }
    static final Set<String> METHODS=Set.of("bam-coverage","bigwig-compare","atac-shift");
    private static final Set<String> COMMON=Set.of("method","out","input","sizes","sizes-format","genome-build","runtime-home","timeout","max-bins","max-records");
    private static final Set<String> FILTERS=Set.of("min-mapq","sam-include","sam-exclude","ignore-duplicates","min-fragment-length","max-fragment-length");
    private static final Set<String> COVERAGE=Set.of("bin-size","normalization","exact-scaling","uncovered","effective-genome-size","extend-reads","scale-factor");
    private static final Set<String> COMPARE=Set.of("bin-size","input2","operation","scale1","scale2","pseudo1","pseudo2","missing","invalid","skip-zero-zero");
    private static final Set<String> SHIFT=Set.of("accept-sequence-tag-loss","cigar-policy");

    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            var allowed=new HashSet<>(COMMON);allowed.addAll(FILTERS);allowed.addAll(COVERAGE);allowed.addAll(COMPARE);allowed.addAll(SHIFT);
            var options=options(args,allowed);String method=required(options,"method");if(!METHODS.contains(method))throw new IllegalArgumentException("Unknown original deepTools method:"+method);
            var applicable=new HashSet<>(COMMON);if(method.equals("bigwig-compare"))applicable.addAll(COMPARE);else{applicable.addAll(FILTERS);applicable.addAll(method.equals("bam-coverage")?COVERAGE:SHIFT);}
            for(String key:options.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            for(String key:List.of("input","sizes","genome-build","out"))required(options,key);
            String sizesFormat=choice(options,"sizes-format","sizes","sizes","fai");int timeout=integer(options,"timeout",3600,1,86400);
            int maxRecords=integer(options,"max-records",10_000_000,1,1_000_000_000),maxBins=integer(options,"max-bins",1_000_000,1,100_000_000);
            int bin=method.equals("atac-shift")?0:integerRequired(options,"bin-size",1,1_000_000);
            if(method.equals("bam-coverage")){
                choiceRequired(options,"normalization","NONE","CPM","RPKM","BPM","RPGC");choiceRequired(options,"uncovered","zero","omit");required(options,"exact-scaling");bool(options,"exact-scaling",false);
                double scale=number(options,"scale-factor",1);if(scale<0)throw new IOException("scale-factor requires a nonnegative finite value");
                if(options.containsKey("extend-reads"))integerRequired(options,"extend-reads",1,2000);
                if(options.get("normalization").equals("RPGC"))integerRequired(options,"effective-genome-size",1,Integer.MAX_VALUE);
                else if(options.containsKey("effective-genome-size"))throw new IOException("effective-genome-size applies only to RPGC");
            }else if(method.equals("bigwig-compare")){
                required(options,"input2");choiceRequired(options,"operation","ratio","log2","reciprocal_ratio","subtract","add","first","second","mean");choiceRequired(options,"missing","zero","retain","error");choiceRequired(options,"invalid","error","retain");bool(options,"skip-zero-zero",false);
                for(String key:List.of("scale1","scale2","pseudo1","pseudo2"))number(options,key,key.startsWith("scale")?1:0);
                if(options.get("missing").equals("retain")&&!options.get("invalid").equals("retain"))throw new IOException("missing retain requires invalid retain because original fixed-step output retains missing bins as NaN");
            }else{
                if(!bool(options,"accept-sequence-tag-loss",false))throw new IOException("ATACshift drops SEQ,QUAL and all optional SAM tags and replaces CIGAR with M. Explicit --accept-sequence-tag-loss true is required; retain the original BAM");
                if(!"match-only".equals(required(options,"cigar-policy")))throw new IOException("This ATAC subset requires --cigar-policy match-only; complex CIGAR workflows need separate review");
            }
            if(!method.equals("bigwig-compare"))validateFilters(options);
            String runtime=options.getOrDefault("runtime-home",System.getenv("JLINALG_DEEPTOOLS_HOME"));int[] code={0};
            externalTransaction(options,"deep-tools-"+method,(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("method",method);session.put("engine","original-deepTools-3.5.6");session.put("genome_build_declared",options.get("genome-build"));session.put("scope","Typed local BAM coverage,BigWig comparison or simple-M-CIGAR ATAC shifts only; no complete-package,historical-version or full-paper parity.");
                try{
                    if(runtime==null||runtime.isBlank())throw new DeepToolsRuntime.Unavailable("Supply --runtime-home or JLINALG_DEEPTOOLS_HOME with a sealed deepTools3.5.6 environment",null);
                    var original=new DeepToolsRuntime(Path.of(runtime),directory,session);
                    var sources=new LinkedHashMap<Path,String>();Path input=source(options.get("input"),sources),sizes=source(options.get("sizes"),sources);var reference=GenomicSignalCli.reference(sizes,sizesFormat);
                    if(reference.isEmpty()||reference.values().stream().anyMatch(n->n>Integer.MAX_VALUE))throw new IOException("Original BAM/BigWig workflow requires a nonempty exact reference within signed-32-bit contig coordinates");
                    if(bin>0){long bins=0;for(long length:reference.values())bins=Math.addExact(bins,(length-1)/bin+1);if(bins>maxBins)throw new IOException("Explicit reference/bin grid exceeds max-bins; choose a suitable bin width or resource limit");session.put("nominal_bins",bins);}
                    if(method.equals("bigwig-compare")){
                        Path second=source(options.get("input2"),sources);for(Path path:List.of(input,second)){var audit=audit(original,"bigwig",path,reference,maxRecords);audit.put("missing",options.get("missing"));var result=original.audit(audit,path.equals(input)?"input-audit":"second-audit",timeout);session.put(path.equals(input)?"input_audit":"second_audit",result.get("audit"));}
                        var command=new ArrayList<>(List.of("-b1",original.path(input),"-b2",original.path(second),"-o",original.path(directory.resolve("original.bedgraph")),"--outFileFormat","bedgraph","--binSize",Integer.toString(bin),"--numberOfProcessors","1","--fixedStep","--operation",options.get("operation"),"--scaleFactors="+number(options,"scale1",1)+":"+number(options,"scale2",1),"--pseudocount",Double.toString(number(options,"pseudo1",0)),Double.toString(number(options,"pseudo2",0))));
                        if(options.get("missing").equals("retain"))command.add("--skipNonCoveredRegions");if(bool(options,"skip-zero-zero",false)){command.add("--skipZeroOverZero");warnings.add("ORIGINAL ZERO FILTER:deepTools3.5.6 tests the sum of the two unscaled bin means against zero. Signed nonzero values that cancel are also omitted, before scaling and pseudocounts. Native compare-native tests both means individually against zero.");}
                        original.run("bigwigCompare",command,"bigwig-compare",timeout);signal(directory,reference,options.get("invalid").equals("retain"),session,warnings);
                        warnings.add("BIGWIG INPUT PRECISION:binary BigWig signal is stored as float32; original fixed-step bedGraph uses six significant digits. Missing="+options.get("missing")+" and invalid="+options.get("invalid")+" are explicit. Pseudocounts follow scaling and affect ratio operations only. results.tsv records actual original output bins; filtered bins are absent and no zero is invented.");
                    }else{
                        indexes(input,sources);var request=audit(original,"bam",input,reference,maxRecords);if(method.equals("atac-shift"))request.put("match_only",true);var before=original.audit(request,"input-audit",timeout);session.put("input_audit",before.get("audit"));
                        var arguments=new ArrayList<>(List.of("-b",original.path(input),"--numberOfProcessors","1"));filters(options,arguments);
                        if(method.equals("bam-coverage")){
                            arguments.addAll(List.of("-o",original.path(directory.resolve("original.bedgraph")),"--outFileFormat","bedgraph","--binSize",Integer.toString(bin),"--normalizeUsing",options.get("normalization").equals("NONE")?"None":options.get("normalization"),"--scaleFactor",Double.toString(number(options,"scale-factor",1))));
                            if(bool(options,"exact-scaling",false))arguments.add("--exactScaling");if(options.get("uncovered").equals("omit"))arguments.add("--skipNonCoveredRegions");if(options.containsKey("effective-genome-size"))arguments.addAll(List.of("--effectiveGenomeSize",options.get("effective-genome-size")));if(options.containsKey("extend-reads"))arguments.addAll(List.of("--extendReads",options.get("extend-reads")));
                            original.run("bamCoverage",arguments,"bam-coverage",timeout);signal(directory,reference,false,session,warnings);
                            warnings.add("BAM COVERAGE MODEL:normalization="+options.get("normalization")+",exact-scaling="+options.get("exact-scaling")+",uncovered="+options.get("uncovered")+". Original read-overlap counting is used. "+(options.containsKey("extend-reads")?"Extension requested; original paired-end fragment handling also applies.":"No extension requested; aligned read blocks are counted.")+" Filtering changes denominators; exactScaling false can estimate retained depth. RPGC uses original median read length without extension or the declared extension and effective genome size. RPKM uses nominal bin width,including terminal bins; BPM uses kept-alignment scaling. Review the declared build and normalization before interpreting units.");
                        }else{
                            Path shifted=directory.resolve("shifted.bam");arguments.addAll(List.of("-o",original.path(shifted),"--ATACshift","--filterMetrics",original.path(directory.resolve("filter-metrics.tsv"))));original.run("alignmentSieve",arguments,"atac-shift",timeout);
                            var after=audit(original,"bam",shifted,reference,maxRecords);after.put("indexed",false);after.put("sorted",false);after.put("shifted",true);after.put("match_only",true);session.put("output_audit",original.audit(after,"shifted-audit",timeout).get("audit"));
                            warnings.add("DESTRUCTIVE REPRESENTATION CHANGE IN DERIVED BAM:original alignmentSieve ATACshift drops SEQ,QUAL and ALL optional SAM tags,including RG,NM,CB,RX, and replaces CIGAR with a single M operation. Keep the original BAM for sequence,UMI/barcode,read-group or variant analyses. Non-proper pairs are omitted; short shifts may be clamped by upstream behavior. Output is not asserted coordinate sorted or indexed: sort and index this derived BAM before indexed downstream use. filter-metrics.tsv is the original upstream metric,not a substitute for the explicit output-record audit.");
                        }
                    }
                    RegenieRuntime.verify(sources);original.verify();Files.copy(sizes,directory.resolve(sizesFormat.equals("fai")?"reference.fai":"reference.sizes"));session.put("source_sha256",sources.entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),Map.Entry::getValue)));
                    warnings.addAll(RnaToolRuntime.warnings(directory));manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                }catch(DeepToolsRuntime.Unavailable unavailable){code[0]=3;manifest.put("analysis_status","skipped-dependency");manifest.put("error",unavailable.getMessage());RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS SKIPPED - DEPENDENCY:"+unavailable.getMessage());}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(directory);warnings.add(0,"ANALYSIS FAILED:"+failure.getMessage());}
                session.put("warnings",warnings);Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
                if(!warnings.isEmpty())Files.writeString(directory.resolve("WARNINGS.md"),"# "+(code[0]==3?"ANALYSIS SKIPPED - DEPENDENCY REQUIRED":code[0]==2?"ANALYSIS FAILED":"DEEPTOOLS WARNINGS REQUIRE REVIEW")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action:"+(code[0]==3?setup():code[0]==2?"Inspect retained stage logs and execution markers under partial/. Correct the named input/reference,normalization,resource or runtime issue,then rerun to a NEW output directory. No successful result is published.":"Review these choices and any original warnings before downstream analysis. Retain original inputs and inspect source hashes,reference,stage logs and execution markers in this bundle.")+"\n");
            });
            if(Files.exists(Path.of(options.get("out"),"WARNINGS.md")))err.println((code[0]==3?"ANALYSIS SKIPPED - DEPENDENCY REQUIRED: ":code[0]==2?"ANALYSIS FAILED: ":"DEEPTOOLS WARNINGS REQUIRE REVIEW: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Original deepTools workflow completed:"+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg:"+failure.getMessage());return 2;}
    }
    private static String choice(Map<String,String> o,String key,String fallback,String... choices)throws IOException {String value=o.getOrDefault(key,fallback);if(!Set.of(choices).contains(value))throw new IOException(key+" requires "+String.join("/",choices));return value;}
    private static String choiceRequired(Map<String,String> o,String key,String... choices)throws IOException {required(o,key);return choice(o,key,"",choices);}
    private static int integerRequired(Map<String,String> o,String key,int min,int max){required(o,key);return integer(o,key,min,min,max);}
    private static Path source(String name,Map<Path,String> sources)throws IOException {Path path=RnaToolRuntime.file(name);String digest=hash(path),previous=sources.putIfAbsent(path,digest);if(previous!=null&&!previous.equals(digest))throw new IOException("Input changed during preparation:"+path);return path;}
    private static void indexes(Path input,Map<Path,String> sources)throws IOException {
        String text=input.toString();var candidates=new LinkedHashSet<Path>(List.of(Path.of(text+".bai"),Path.of(text+".csi")));if(text.endsWith(".bam")){candidates.add(Path.of(text.substring(0,text.length()-4)+".bai"));candidates.add(Path.of(text.substring(0,text.length()-4)+".csi"));}boolean found=false;for(Path p:candidates)if(Files.exists(p)){source(p.toString(),sources);found=true;}if(!found)throw new IOException("BAM requires an existing adjacent BAI/CSI index. Sort and index the original BAM first; this analysis never modifies input files");
    }
    private static Map<String,Object> audit(DeepToolsRuntime runtime,String kind,Path input,Map<String,Long> reference,int max)throws IOException {var request=new LinkedHashMap<String,Object>();request.put("schema","jlinalg-deeptools-audit-v1");request.put("kind",kind);request.put("input",runtime.path(input));request.put("reference",reference);request.put("max_records",max);return request;}
    private static void validateFilters(Map<String,String> o)throws IOException {integer(o,"min-mapq",0,0,255);integer(o,"sam-include",0,0,65535);integer(o,"sam-exclude",0,0,65535);bool(o,"ignore-duplicates",false);int min=integer(o,"min-fragment-length",0,0,Integer.MAX_VALUE),max=integer(o,"max-fragment-length",0,0,Integer.MAX_VALUE);if(max!=0&&max<min)throw new IOException("max-fragment-length must be zero(unbounded) or at least min-fragment-length");}
    private static void filters(Map<String,String> o,List<String> args){for(var e:Map.of("min-mapq","--minMappingQuality","sam-include","--samFlagInclude","sam-exclude","--samFlagExclude","min-fragment-length","--minFragmentLength","max-fragment-length","--maxFragmentLength").entrySet())if(o.containsKey(e.getKey()))args.addAll(List.of(e.getValue(),o.get(e.getKey())));if(bool(o,"ignore-duplicates",false))args.add("--ignoreDuplicates");}
    private static void signal(Path directory,Map<String,Long> reference,boolean retain,Map<String,Object> session,List<String> warnings)throws IOException {
        long rows=0,invalid=0;var endByChrom=new HashMap<String,Long>();var chromosomeOrder=new HashMap<String,Integer>();for(String chromosome:reference.keySet())chromosomeOrder.put(chromosome,chromosomeOrder.size());int previous=-1;
        Path original=directory.resolve("original.bedgraph");if(!Files.isRegularFile(original))throw new IOException("Original program did not produce bedGraph");
        try(var in=Files.newBufferedReader(original);var table=Files.newBufferedWriter(directory.resolve("results.tsv"));var bed=Files.newBufferedWriter(directory.resolve("signal.bedgraph"))){table.write("chromosome\tstart\tend\tvalue\tstatus\n");String line;while((line=in.readLine())!=null){if(line.isBlank())continue;String[] f=line.split("\t",-1);if(f.length!=4||!reference.containsKey(f[0]))throw new IOException("Invalid original bedGraph row/reference");long start=Long.parseLong(f[1]),end=Long.parseLong(f[2]);int order=chromosomeOrder.get(f[0]);if(order<previous||start<endByChrom.getOrDefault(f[0],0L)||end<=start||end>reference.get(f[0]))throw new IOException("Original bedGraph has invalid,unordered or overlapping coordinates");previous=order;endByChrom.put(f[0],end);double value=switch(f[3].toLowerCase(Locale.ROOT)){case"nan"->Double.NaN;case"inf","infinity"->Double.POSITIVE_INFINITY;case"-inf","-infinity"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(f[3]);};rows++;
                if(!Double.isFinite(value)){invalid++;if(!retain)throw new IOException("Original comparison/normalization produced a missing or nonfinite bin at"+f[0]+":"+start+"-"+end+"; inspect signal/pseudocount/domain or explicitly choose invalid retain");}
                else bed.write(line+"\n");table.write(f[0]+"\t"+start+"\t"+end+"\t"+(Double.isFinite(value)?f[3]:"NA")+"\t"+(Double.isFinite(value)?"FINITE":"MISSING_OR_NONFINITE")+"\n");}}
        session.put("output_bedgraph_rows",rows);session.put("output_missing_or_nonfinite_rows",invalid);if(invalid>0)warnings.add("MISSING/NONFINITE ORIGINAL OUTPUT:"+invalid+"bins retained as NA/status in results.tsv and verbatim in original.bedgraph; omitted from finite signal.bedgraph. No zero was invented.");if(rows==0)warnings.add("EMPTY SIGNAL OUTPUT:no original bins survived. Review filters/coverage and do not treat an empty track as measured zeros.");
    }
    static String setup(){return "Analysis never installs software. Where downloads are permitted,run python tools/literature/install_deeptools.py --environment NEW_RUNTIME_HOME --allow-download; add --allow-source-build only where native compilation is permitted. Native Windows is not supported by the stock pip route: supply a separately prepared private Cygwin profile using --prepared-cygwin SPEC (see DEEPTOOLS_WORKFLOWS.md). Existing sealed runtimes can be reused without download/build permission. If installation is forbidden,skip this analysis until an administrator supplies a matching managed deepTools3.5.6 profile. Pass --runtime-home HOME and rerun to a NEW output directory. Retain failed installation/probe logs; never bypass source/version/integrity checks.";}
    static String help(){return """
        Original deepTools3.5.6,local files,one processor; --runtime-home HOME or JLINALG_DEEPTOOLS_HOME.
          All: --input FILE --sizes EXACT.sizes --sizes-format sizes|fai --genome-build BUILD --out NEW_DIR
          --timeout 3600 --max-bins 1000000 --max-records 10000000. Exact entire reference dictionary required.
        bam-coverage: sorted,indexed BAM; --bin-size BASES --normalization NONE|CPM|RPKM|BPM|RPGC
          --exact-scaling true|false --uncovered zero|omit required; --scale-factor 1; --extend-reads 1..2000.
          RPGC requires --effective-genome-size BASES; without extension uses original median read length.
        bigwig-compare: --input2 FILE.bw --bin-size BASES --operation ratio|log2|reciprocal_ratio|subtract|add|first|second|mean
          --missing zero|retain|error --invalid error|retain required. Missing retain requires invalid retain.
          --scale1 1 --scale2 1 --pseudo1 0 --pseudo2 0 --skip-zero-zero false.
          Original sum==0 filter also omits cancelling signed values; fixed-step retains NaN explicitly.
          Original bedGraph precision is six significant digits; finite signal.bedgraph and audited results.tsv.
        atac-shift: sorted,indexed BAM; --accept-sequence-tag-loss true --cigar-policy match-only required.
          Original Tn5 shift4,-5,5,-4 drops SEQ/QUAL/ALL optional tags and replaces CIGAR. Retain original BAM.
          Derived shifted.bam is not asserted sorted/indexed. Complex CIGAR input fails in this subset.
        BAM methods: --min-mapq 0 --sam-include 0 --sam-exclude 0 --ignore-duplicates false
          --min-fragment-length 0 --max-fragment-length 0(unbounded). No blacklist/strand/MNase/smoothing subset.
        Missing/changed runtime skips3 with prominent WARNINGS.md and setup actions; analysis errors2 retain
          partials and no successful result. No implicit installation,remote I/O,build conversion or paper parity.
        """;}
}
