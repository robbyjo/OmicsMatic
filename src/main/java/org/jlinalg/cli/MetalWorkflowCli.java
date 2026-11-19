/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.jlinalg.cli.MetalWorkflowFiles.*;
import org.jlinalg.cli.MetalWorkflowRequest.Config;

/** Captured original METAL 2020-05-05 workflow, with explicit result admission. */
public final class MetalWorkflowCli {
    static final String ACTION="""
        SKIP3: dependent analysis skipped. Supply the named permitted original
        2020-05-05 Linux x86_64 runtime, notice and system prerequisites, or leave
        analysis skipped. No installation or historical-edition substitution is
        implicit. FAIL2: no complete result. Read FAILURE.txt, native/WARNINGS.tsv
        and both original consoles; preserve original inputs and partials, correct
        the named cause, then use a NEW output directory. Never drop failed rows,
        infer directions, floor P=0 or repair NaN/Inf to force admission.
        """;
    static final String NOTICE="""
        # Original METAL captured workflow: warnings and required actions

        SOURCE-BUILD PROFILE. This executes a caller-supplied checksum-declared
        original 2020-05-05 Linux x86_64 executable. Qualification is specific to
        the saved source-build runtime and controls; a release label alone is
        not a checksum or producer authentication. System loader/libraries and
        the Java runtime are external prerequisites, with host metadata retained.
        No software is downloaded or installed during analysis.

        PRIVATE RUNTIME. Original source headers restrict redistribution. The
        local bundle contains the captured executable and supplied notice. Keep
        it private unless separate permission covers redistribution. Public
        qualification must exclude original source, archives, binaries and bundled
        author examples. Independently written adapter code is separate.

        The literal nine-column prepared summaries and all requested settings
        are explicit. Caller must establish the same hypothesis, trait units,
        effective N, genome build and allele/strand conventions. No author command,
        effective N, phenotype transformation or genome coordinate is inferred.
        Paired mode covers single-base DNA alleles; non-allelic mode explicitly
        declares common-direction gene/other units and needs recorded acceptance
        of the original missing-allele-column warning. Palindromic SNPs require a
        trustworthy shared strand declaration; original allele matching does not
        establish that biological fact. Original warnings remain in raw consoles.

        Every declared row and marker/study contribution is checked; missingness
        between studies is explicit, not invalid-row omission. Incompatible
        alleles, duplicate IDs, invalid numbers and nonfinite source results fail.
        warning_policy=allow-recorded never permits dropped input contributions.
        Limits refuse the complete population. The metadata estimate is not a
        JVM heap guarantee; stat-only process monitoring can overshoot a byte
        budget between polls and is not an operating-system disk quota.

        Original log probabilities are BASE 10. Ordinary output preserves literal
        positive scientific notation even if conversion to double underflows;
        a literal zero probability is refused, not floored. Use log10 output in
        a NEW request where supported. Effect/SE output uses original 12-decimal
        print settings; an unrepresentable/nonpositive printed SE fails instead
        of silently changing precision. Native weighted-Z natural-log inputs
        belong to a different Java API and are not original METAL log input.

        Optional post-meta GC reloads the original printed primary table in a
        second original process, and thus retains that printed handoff precision.
        Primary heterogeneity and pooled-frequency dispersion remain in native
        summary columns and raw primary results; a second single aggregate does
        not invent multi-cohort heterogeneity. Original overlap Weight, adjusted N
        and caller-declared total N are distinct retained quantities. No true
        biological overlap estimate is independently established by this adapter.

        DeclaredN is the original custom-variable sum printed with SIX significant
        digits, checked against the captured input sum at that precision. It is
        not an exact unrounded participant count. With overlap and frequencies
        enabled, original Freq1 can lie outside original MinFreq/MaxFreq: the
        source uses its adjusted Weight in this calculation. A constant input
        frequency of0.3 yields original Freq1=0.2573 in the overlap control.
        FreqSE is retained as an original source field, not independently certified
        frequency uncertainty. No range projection or silent mean repair is made.

        VERIFY checks exact directory membership, method fingerprints, runtime
        identity, process records and regenerated admission/summary/warning rows.
        It never launches METAL. REPLAY actually reruns the captured typed workflow
        in NEW output and requires original scientific tables and native summaries
        to agree byte for byte. Integrity/replay are not producer authentication,
        whole-paper replication or biological validation. Keep matching old readers
        for historical bundles rather than refreshing their fingerprints.

        """+ACTION;
    private MetalWorkflowCli(){ }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error){
        Path owned=null;
        try{
            if(args.length==0||(args.length==1&&Set.of("help","--help").contains(args[0]))){help(output);return 0;}
            if(args.length==3&&args[0].equals("verify")&&args[1].equals("--bundle")){var report=validate(Path.of(args[2]));output.println("VERIFIED captured original METAL result: "+report.markers()+" markers; no runtime re-executed");return 0;}
            require(args.length==5&&Set.of("run","replay").contains(args[0])&&args[3].equals("--out")&&args[1].equals(args[0].equals("run")?"--request":"--bundle"),"Use help, run --request REQUEST --out NEW, verify --bundle BUNDLE, or replay --bundle BUNDLE --out NEW");
            Path request=Path.of(args[2]).toAbsolutePath().normalize();Path parent=null;
            if(args[0].equals("replay")){parent=request;validate(parent);request=parent.resolve("REQUEST.tsv");}
            Config c=MetalWorkflowRequest.read(request);Path destination=destination(Path.of(args[4]),List.of(request.toRealPath()));Files.createDirectories(destination.getParent());Files.createDirectory(destination);owned=destination;
            Files.writeString(owned.resolve("WARNINGS.md"),NOTICE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            MetalWorkflowRuntime.prerequisite(c);execute(request,c,owned);
            if(parent!=null){
                for(String name:List.of("native/summary.tsv","native/WARNINGS.tsv","stages/primary/result1.tbl","stages/primary/result1.tbl.info"))require(pin(parent.resolve(name)).equals(pin(owned.resolve(name))),"Replay scientific output differs: "+name);
                if(c.post())for(String suffix:List.of("result1.tbl","result1.tbl.info"))require(pin(parent.resolve("stages/post-gc/"+suffix)).equals(pin(owned.resolve("stages/post-gc/"+suffix))),"Replay post-GC output differs: "+suffix);
            }
            var report=validate(owned);output.println("COMPLETED admitted original METAL result: "+report.markers()+" markers; "+report.inputRows()+" captured rows; "+report.warnings()+" recorded original warnings");output.println(owned);return 0;
        }catch(Exception|LinkageError e){
            boolean skipped=e instanceof Prerequisite;String status=skipped?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            if(owned!=null)try{
                if(Files.exists(owned.resolve("COMPLETED.tsv")))Files.move(owned.resolve("COMPLETED.tsv"),owned.resolve("INCOMPLETE-COMPLETION.tsv"));
                var text=new StringWriter();e.printStackTrace(new PrintWriter(text));Files.writeString(owned.resolve("FAILURE.txt"),status+"\n"+text+"\n"+ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }catch(IOException failure){error.println("Could not retain failure receipt: "+failure.getMessage());}
            error.println(status+": "+e.getMessage());if(e instanceof LinkageError)error.println("Incomplete or incompatible runtime classes; supply the complete matching source-build JAR and retry NEW output.");error.println(ACTION);return skipped?3:2;
        }
    }
    private static void execute(Path request,Config c,Path root)throws IOException,InterruptedException {
        Config captured=MetalWorkflowInputs.capture(request,c,root);map(root.resolve("IMPLEMENTATION.tsv"),implementation());
        map(root.resolve("HOST.tsv"),Map.of("java_runtime",System.getProperty("java.runtime.version"),"os_name",System.getProperty("os.name"),"os_arch",System.getProperty("os.arch"),"os_version",System.getProperty("os.version"),"system_libraries","external-prerequisites-not-bundled"));
        var frozen=new TreeMap<String,String>();for(var e:inventory(root,c.number("max_bytes"),c.number("max_files"),true).entrySet())if(!e.getKey().endsWith("/"))frozen.put(e.getKey(),e.getValue().bytes()+":"+e.getValue().sha256());map(root.resolve("CAPTURE.tsv"),frozen);
        var p=MetalWorkflowInputs.inspect(root.resolve("REQUEST.tsv"),captured);MetalWorkflowInputs.originalDeclarations(root,captured,p);
        MetalWorkflowRuntime.execute(root,captured,p,false);MetalWorkflowRuntime.validateProcess(root,captured,p,false);
        if(captured.post()){
            try(var warnings=new WriterSink(root.resolve("native/PRIMARY-WARNINGS.tsv"),c.number("max_bytes"))){warnings.line("stage\tstream\tline\toriginal_warning");long n=MetalWorkflowAudit.diagnostics(root,c,false,p.markers().size(),warnings);require(n==0||c.get("warning_policy").equals("allow-recorded"),"Original primary warnings forbidden; inspect native/PRIMARY-WARNINGS.tsv");}
            MetalWorkflowAudit.table(root,c,p,false,line->{});MetalWorkflowRuntime.execute(root,captured,p,true);MetalWorkflowRuntime.validateProcess(root,captured,p,true);
        }
        MetalWorkflowAudit.Report report;
        try(var summary=new WriterSink(root.resolve("native/summary.tsv"),c.number("max_bytes"));var warnings=new WriterSink(root.resolve("native/WARNINGS.tsv"),c.number("max_bytes"))){report=MetalWorkflowAudit.audit(root,captured,p,summary,warnings);}
        checkCapture(root);manifest(root,c);
        map(root.resolve("COMPLETED.tsv"),Map.of("profile",PROFILE,"result_admitted","true","manifest_sha256",pin(root.resolve("MANIFEST.tsv")).sha256(),"markers",Long.toString(report.markers()),"input_rows",Long.toString(report.inputRows()),"warnings",Long.toString(report.warnings()),"runtime_distribution","restricted-private-local","metadata_estimated_bytes",Long.toString(p.estimatedBytes())));
    }
    static MetalWorkflowAudit.Report validate(Path requested)throws IOException {
        require(!Files.isSymbolicLink(requested)&&Files.isDirectory(requested,LinkOption.NOFOLLOW_LINKS),"Require a regular captured bundle directory");Path root=requested.toRealPath();Config c=MetalWorkflowRequest.read(root.resolve("REQUEST.tsv"));
        require(c.get("executable").equals("runtime/metal")&&c.get("notice").equals("runtime/NOTICE.txt")&&c.get("studies").equals("inputs/STUDIES.tsv")&&c.get("release").equals("2020-05-05")&&c.get("execution").equals("allow"),"Captured paths/release/execution are not canonical");
        var completion=map(root.resolve("COMPLETED.tsv"));require(PROFILE.equals(completion.get("profile"))&&"true".equals(completion.get("result_admitted")),"No admitted completion for this profile");verifyManifest(root,c);
        require(map(root.resolve("IMPLEMENTATION.tsv")).equals(implementation()),"Method bytecode differs; use the matching frozen reader or qualify changed code separately");require(pin(root.resolve("runtime/metal")).sha256().equals(c.get("executable_sha256")),"Captured runtime differs from declared checksum");checkCapture(root);
        var p=MetalWorkflowInputs.inspect(root.resolve("REQUEST.tsv"),c);MetalWorkflowInputs.originalDeclarations(root,c,p);for(int i=0;i<p.studies().size();i++)require(p.studies().get(i).file().equals(root.resolve("inputs/"+String.format(Locale.ROOT,"%04d.tsv",i))),"Noncanonical captured study path");
        MetalWorkflowRuntime.validateProcess(root,c,p,false);if(c.post())MetalWorkflowRuntime.validateProcess(root,c,p,true);
        if(c.post())try(var primaryWarnings=new CompareSink(root.resolve("native/PRIMARY-WARNINGS.tsv"),c.integer("max_line_bytes"))){primaryWarnings.line("stage\tstream\tline\toriginal_warning");long n=MetalWorkflowAudit.diagnostics(root,c,false,p.markers().size(),primaryWarnings);require(n==0||c.get("warning_policy").equals("allow-recorded"),"Forbidden primary warnings in saved post-GC workflow");}
        MetalWorkflowAudit.Report report;try(var summary=new CompareSink(root.resolve("native/summary.tsv"),c.integer("max_line_bytes"));var warnings=new CompareSink(root.resolve("native/WARNINGS.tsv"),c.integer("max_line_bytes"))){report=MetalWorkflowAudit.audit(root,c,p,summary,warnings);}
        require(completion.get("markers").equals(Long.toString(report.markers()))&&completion.get("input_rows").equals(Long.toString(report.inputRows()))&&completion.get("warnings").equals(Long.toString(report.warnings()))&&completion.get("metadata_estimated_bytes").equals(Long.toString(p.estimatedBytes())),"Completion population differs from regenerated audit");return report;
    }
    private static void checkCapture(Path root)throws IOException {
        for(var e:map(root.resolve("CAPTURE.tsv")).entrySet()){
            require(!e.getKey().startsWith("/")&&!e.getKey().contains("..")&&!e.getKey().contains("\\"),"Unsafe capture member");Pin p=pin(root.resolve(e.getKey()));require(e.getValue().equals(p.bytes()+":"+p.sha256()),"Captured request/input/runtime changed: "+e.getKey());
        }
    }
    static Map<String,String> implementation()throws IOException {
        var types=new TreeMap<String,Class<?>>();for(Class<?> type:List.of(MetalWorkflowCli.class,MetalWorkflowFiles.class,MetalWorkflowRequest.class,MetalWorkflowInputs.class,MetalWorkflowRuntime.class,MetalWorkflowAudit.class))collect(type,types);var result=new TreeMap<String,String>();
        for(var e:types.entrySet())try(var in=e.getValue().getResourceAsStream('/'+e.getKey().replace('.','/')+".class")){
            require(in!=null,"Complete source-build runtime classes are required");try{result.put(e.getKey(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        return result;
    }
    private static void collect(Class<?> type,Map<String,Class<?>> into){into.put(type.getName(),type);for(Class<?> child:type.getDeclaredClasses())collect(child,into);}
    private static final class WriterSink implements MetalWorkflowAudit.Sink,AutoCloseable {
        private final BufferedWriter out;private final long maximum;private long bytes;
        WriterSink(Path path,long maximum)throws IOException {out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);this.maximum=maximum;}
        public void line(String line)throws IOException {bytes=Math.addExact(bytes,line.getBytes(StandardCharsets.UTF_8).length+1L);require(bytes<=maximum,"Generated audit output exceeds byte capacity");out.write(line);out.write('\n');}
        public void close()throws IOException {out.close();}
    }
    private static final class CompareSink implements MetalWorkflowAudit.Sink,AutoCloseable {
        private final Lines in;CompareSink(Path path,int maximum)throws IOException {in=new Lines(path,maximum);}
        public void line(String line)throws IOException {require(line.equals(in.next()),"Saved native audit differs from complete regeneration");}
        public void close()throws IOException {try{require(in.next()==null,"Trailing saved native audit rows");}finally{in.close();}}
    }
    private static void help(PrintStream out){
        out.println("Original METAL 2020-05-05 captured source-build workflow\nrun --request REQUEST.tsv --out NEW\nverify --bundle BUNDLE\nreplay --bundle BUNDLE --out NEW");out.println("Request: literal UTF-8 key/value TSV, header key<TAB>value; exactly these keys:\n"+new TreeSet<>(MetalWorkflowRequest.KEYS));
        out.println("scheme=STDERR|SAMPLESIZE; allele_mode=paired|none; strand/frequency/overlap/heterogeneity=ON|OFF; execution=allow|forbid; output_probability=ordinary|log10; warning_policy=forbid|allow-recorded; z_cutoff='-' without overlap, positive otherwise; post_genomic_control=OFF|ON|positive lambda.");
        out.println("Studies TSV: study<TAB>file<TAB>genomic_control. Unique study IDs and source paths; per-study GC=OFF|ON|positive lambda. Prepared summaries require exact header:\n"+MetalWorkflowInputs.HEADER+"\nUse '-' for unused PVALUE/STDERR/FREQ or non-allelic ALLELE1/ALLELE2. N is always explicit and positive. Limits and hypothesis are mandatory. Paired mode uses two distinct uppercase A/C/G/T alleles; OFF strand still requires literal '+' input. Original native runtime is Linux x86_64; verification works with Java17+ on other hosts.");out.println(NOTICE);
    }
}
