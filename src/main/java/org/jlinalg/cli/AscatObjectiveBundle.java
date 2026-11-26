/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.AscatObjective;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Whole-input capture, complete numerical output, and exact saved replay. */
final class AscatObjectiveBundle {
    static final String PROFILE="native-ascat-objective-v1";
    static final String ACTION="WARNINGS AND REQUIRED ACTION: this is a numerical component for already aligned, segmented autosomal loci. It does not estimate purity, ploidy or tumor copy number. Read WARNINGS.md and OUTPUT-AUDIT.json; require COMPLETED.json and a successful ascat-objective --verify with the exact producing JAR. Missing input/producer produces SKIP3 and skips dependent calculation. Invalid input, changed bytes, overflow, cancellation or capacity exhaustion produces FAIL2 with no complete objective. Preserve failed attempts, correct the named cause and retry NEW paths. Full ASCAT analysis requires separately validated upstream preprocessing, model fitting and QC; skip dependent analysis when required software, references, installation permission or licences are unavailable.";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String text){super(text);}}
    record Spec(double[] logR,double[] baf,double gamma,double[] psi,double[] rho,long cells,long operations,long bytes,long wall) { }
    private AscatObjectiveBundle() { }

    static Spec parse(byte[] raw)throws IOException {
        var doc=object(SimpleJson.parse(utf8(raw).replaceFirst("^\\uFEFF","")));
        keys(doc,"schema_version","profile","input_domain","preparation","loci","gamma","psi","rho","capacity");integer(doc.get("schema_version"),1,1);
        require(PROFILE.equals(doc.get("profile"))&&"aligned-segmented-autosomal-logR-BAF".equals(doc.get("input_domain")),"Require the explicit aligned, segmented autosomal component contract");
        String preparation=text(doc.get("preparation"));require(!preparation.isBlank()&&preparation.length()<=8192&&preparation.chars().noneMatch(c->c<32||c==127),"Supply bounded actual preparation provenance");
        var cap=object(doc.get("capacity"));keys(cap,"rows","cells","segment_cell_operations","output_bytes","wall_millis");
        long rows=integer(cap.get("rows"),1,2000000),cells=integer(cap.get("cells"),1,1000000),operations=integer(cap.get("segment_cell_operations"),1,1000000000),bytes=integer(cap.get("output_bytes"),1,536870912),wall=integer(cap.get("wall_millis"),1,604800000);
        var loci=list(doc.get("loci"));require(!loci.isEmpty()&&loci.size()<=rows,"Complete locus population exceeds row capacity or is empty");
        double[] r=new double[loci.size()],b=new double[loci.size()];
        for(int i=0;i<r.length;i++){var pair=list(loci.get(i));require(pair.size()==2,"Each locus requires exactly [logR,BAF]");r[i]=pair.get(0)==null?Double.NaN:number(pair.get(0));b[i]=pair.get(1)==null?Double.NaN:number(pair.get(1));}
        double[] psi=axis(doc.get("psi")),rho=axis(doc.get("rho"));require((long)psi.length*rho.length<=cells,"Complete explicit objective grid exceeds cell capacity");
        return new Spec(r,b,number(doc.get("gamma")),psi,rho,cells,operations,bytes,wall);
    }
    private static double number(Object raw)throws IOException {require(raw instanceof Number,"Require a JSON number (or null for a missing locus value)");double value=((Number)raw).doubleValue();require(Double.isFinite(value),"Nonfinite numeric input");return value;}
    private static double[] axis(Object raw)throws IOException {var rows=list(raw);require(!rows.isEmpty()&&rows.size()<=1000000,"Require a nonempty bounded explicit grid axis");double[] values=new double[rows.size()];for(int i=0;i<values.length;i++){values[i]=number(rows.get(i));require(values[i]>0&&(i==0||values[i]>values[i-1]),"Grid axes must be positive and strictly increasing");}return values;}
    private static Runnable bounded(Spec s,Runnable cancellation) {long start=System.nanoTime();return()->{cancellation.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>s.wall())throw new IllegalStateException("ASCAT objective wall limit/cancellation; no complete result");};}
    private static Pin producer()throws IOException {
        try {Path path=Path.of(AscatObjectiveBundle.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!path.getFileName().toString().endsWith(".jar"))throw new Unavailable("Build and retain the source-built executable JAR; class directories do not identify an exact producer");return pin(path,1000000000,()->{});}
        catch(java.net.URISyntaxException e){throw new Unavailable("Exact producing JAR unavailable");}
    }
    static Path capture(Path request,Path supplied,Runnable cancellation)throws IOException {
        if(!Files.isReadable(request))throw new Unavailable("Complete input request unavailable: "+request);
        byte[] input=bytes(request,67108864);Spec s=parse(input);Runnable check=bounded(s,cancellation);Pin source=pin(request,67108864,check),producer=producer();
        require(source.equals(new Pin(input.length,sha(input))),"Request changed while reading");
        Path root=supplied.toAbsolutePath().normalize();require(root.getParent()!=null&&supplied.toAbsolutePath().equals(root),"Require a canonical NEW capture path");directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Capture path already exists; use a NEW path");Files.createDirectory(root);
        try {
            writeJson(root.resolve("INCOMPLETE.json"),map("objective_admitted",false,"tumor_model_admitted",false));
            Files.write(root.resolve("request.json"),input,StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve("WARNINGS.md"),warnings(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            writeJson(root.resolve("OUTPUT-AUDIT.json"),compute(root,s,false,check));
            require(source.equals(pin(request,67108864,check))&&producer.equals(producer()),"Original request or producing JAR changed");
            var files=files(root,s,check);files.remove("INCOMPLETE.json");
            writeJson(root.resolve("manifest.json"),map("schema_version",1,"profile",PROFILE,"producer",producer,"files",files,"objective_admitted",true,"tumor_model_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_NATIVE_ASCAT_OBJECTIVE_COMPONENTS","manifest",pin(root.resolve("manifest.json"),1048576,check),"objective_admitted",true,"tumor_model_admitted",false));
            verify(root,true,check);require(source.equals(pin(request,67108864,check))&&producer.equals(producer()),"Source request or JAR changed before admission");Files.delete(root.resolve("INCOMPLETE.json"));return root;
        }catch(IOException|RuntimeException|Error error){failure(root,error);throw error;}
    }
    static void verify(Path supplied,boolean precommit,Runnable cancellation)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS)&&Files.exists(root.resolve("INCOMPLETE.json"),LinkOption.NOFOLLOW_LINKS)==precommit,"Failed/incomplete objective attempt is not admitted");
        var done=object(json(root.resolve("COMPLETED.json"),1048576));keys(done,"status","manifest","objective_admitted","tumor_model_admitted");
        require("COMPLETE_NATIVE_ASCAT_OBJECTIVE_COMPONENTS".equals(done.get("status"))&&Boolean.TRUE.equals(done.get("objective_admitted"))&&Boolean.FALSE.equals(done.get("tumor_model_admitted")),"Missing or expanded component admission");
        require(pin(root.resolve("manifest.json"),1048576,cancellation).equals(fingerprint(done.get("manifest"),1048576)),"Saved objective manifest changed");
        var manifest=object(json(root.resolve("manifest.json"),1048576));keys(manifest,"schema_version","profile","producer","files","objective_admitted","tumor_model_admitted");integer(manifest.get("schema_version"),1,1);
        require(PROFILE.equals(manifest.get("profile"))&&Boolean.TRUE.equals(manifest.get("objective_admitted"))&&Boolean.FALSE.equals(manifest.get("tumor_model_admitted")),"Different component contract");
        require(producer().equals(fingerprint(manifest.get("producer"),1000000000)),"Restore the exact producing JAR; do not reseal another executable");
        Spec s=parse(bytes(root.resolve("request.json"),67108864));Runnable check=bounded(s,cancellation);var actual=files(root,s,check);
        var expected=new HashSet<>(Set.of("request.json","WARNINGS.md","segments.tsv","distance.tsv","OUTPUT-AUDIT.json","manifest.json","COMPLETED.json"));if(precommit)expected.add("INCOMPLETE.json");
        require(actual.keySet().equals(expected),"Missing or extra file in complete objective population");actual.remove("manifest.json");actual.remove("COMPLETED.json");actual.remove("INCOMPLETE.json");
        require(NextflowJson.json(actual).equals(NextflowJson.json(manifest.get("files"))),"Complete objective byte population changed");
        require(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings()),"Saved objective warnings differ");
        var audit=compute(root,s,true,check);require(NextflowJson.json(audit).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"),1048576))),"Complete numerical audit differs");
        check.run();
    }
    private static Map<String,Object> compute(Path root,Spec s,boolean replay,Runnable check)throws IOException {
        var segments=AscatObjective.segments(s.logR(),s.baf(),check);long cells=(long)s.psi().length*s.rho().length;
        require(Math.multiplyExact(Math.multiplyExact(cells,segments.size()),2)<=s.operations(),"Complete segment/cell population exceeds declared operation capacity");
        long[] output={0};
        try(var writer=new Lines(root.resolve("segments.tsv"),replay,s.bytes(),output)) {
            writer.line("logR\tBAF\tlength");for(var segment:segments){check.run();writer.line(Double.toString(segment.logR())+"\t"+Double.toString(segment.baf())+"\t"+segment.length());}
        }
        try(var writer=new Lines(root.resolve("distance.tsv"),replay,s.bytes(),output)) {
            writer.line("psi\trho\tdistance");for(double psi:s.psi())for(double rho:s.rho()) {check.run();double distance=AscatObjective.distance(segments,s.gamma(),psi,rho,check);writer.line(Double.toString(psi)+"\t"+Double.toString(rho)+"\t"+Double.toString(distance));}
        }
        long retained=0;for(var segment:segments)retained+=segment.length();
        return map("profile",PROFILE,"input_rows",s.logR().length,"retained_rows",retained,"omitted_missing_pairs",s.logR().length-retained,"segments",segments.size(),"distance_cells",cells,"output_table_bytes",output[0],"gamma",s.gamma(),"objective_admitted",true,"tumor_model_admitted",false,"original_runtime_executed",false,"native_family_credit",0,"upstream_family_credit",0);
    }
    private static TreeMap<String,Object> files(Path root,Spec s,Runnable check)throws IOException {
        var files=new TreeMap<String,Object>();long total=0;
        try(var paths=Files.newDirectoryStream(root)) {for(Path path:paths){check.run();require(files.size()<16,"Extra objective files exceed complete population capacity");Pin p=pin(path,Math.max(s.bytes(),67108864),check);total=Math.addExact(total,p.bytes());require(total<=s.bytes()+71303168,"Complete objective capture exceeds byte capacity");files.put(path.getFileName().toString(),p);}}
        return files;
    }
    private static final class Lines implements AutoCloseable {
        final BufferedWriter writer;final BufferedReader reader;final long maximum;final long[] bytes;
        Lines(Path path,boolean replay,long maximum,long[] bytes)throws IOException {this.maximum=maximum;this.bytes=bytes;writer=replay?null:Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);reader=replay?Files.newBufferedReader(path,StandardCharsets.UTF_8):null;}
        void line(String line)throws IOException {bytes[0]=Math.addExact(bytes[0],line.length()+1L);require(bytes[0]<=maximum,"Complete objective table population exceeds output byte capacity");if(writer!=null){writer.write(line);writer.write('\n');}else require(line.equals(reader.readLine()),"Fresh complete numerical replay differs from saved table");}
        @Override public void close()throws IOException {if(writer!=null)writer.close();else try{require(reader.readLine()==null,"Extra row in saved objective output");}finally{reader.close();}}
    }
    private static void failure(Path root,Throwable error) {try {Files.deleteIfExists(root.resolve("COMPLETED.json"));if(!Files.exists(root.resolve("FAILURE.json")))writeJson(root.resolve("FAILURE.json"),map("status","FAIL2_NO_COMPLETE_ASCAT_OBJECTIVE","error",error.toString(),"required_action",ACTION,"objective_admitted",false,"tumor_model_admitted",false));}catch(IOException suppressed){error.addSuppressed(suppressed);}}
    private static String warnings() {return "ASCAT OBJECTIVE COMPONENTS: WARNINGS AND REQUIRED ACTION\n\nInput loci are ordered [segmented logR, segmented BAF] pairs for autosomes after ID alignment and the experiment's actual segmentation. The component cannot check genomic identity, genome build, segmentation correctness or autosome selection from these pairs; record those steps in preparation. No sorting, chromosome partition, smoothing, genotype inference or coordinate repair occurs. Null means a missing value; pairs with either value missing are removed before consecutive equal pairs form runs, matching original make_segments. Signed zero compares equal and the first run's original values are retained. Empty/all-missing populations fail. Finite logR and BAF in [0,1] are supported; infinities and mismatched/recycled vectors are outside this component's admitted domain.\n\nGamma and every positive, increasing psi/rho coordinate are explicit. Rho may extend to 1.05, as in the original grid. Global unweighted allele sums select one allele vector; run lengths then weight squared distance to nonnegative ties-to-even rounded copy values. Exactly BAF=0.5 receives weight0.05. No best solution is selected, and the smallest objective is not itself a valid ASCAT fit. Source validity filters, purity/ploidy optimization, sex chromosomes, integer copy calls, GC/replication correction, allele counting, normal panels, segmentation and clinical interpretation require separate analysis. No full ASCAT family or paper is qualified by this component.\n\nNative arithmetic uses StrictMath.pow and compensated finite accumulation. Overflow fails rather than replacing a value or dropping a row. References from complete unmodified ASCAT2.5.2 and3.2.0 namespace functions on synthetic cases are retained with source hashes. Their floating-point agreement is numerical, not bitwise equivalence to R's math library/long-double sum. Saved replay checks every segment and distance cell exactly with the producing JAR.\n\nAll original request bytes are retained. The request is limited to64MiB; declared row, cell, segment/cell operation, output-table byte and cooperative time/cancellation limits bound complete populations, not JVM memory. A failure preserves its partial tables and warning/error records and admits no complete objective. No software is downloaded or installed by this command.\n\n"+ACTION+"\n";}
}
