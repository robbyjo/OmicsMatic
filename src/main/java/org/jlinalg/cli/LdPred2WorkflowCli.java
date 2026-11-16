/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import static org.jlinalg.cli.LdPred2WorkflowFiles.*;

/** Native inf/grid and original auto workflows with captured-input recomputation. */
public final class LdPred2WorkflowCli {
    static final String NOTICE="""
        # Warnings and required actions

        DEVELOPMENT SNAPSHOT: native inf/grid controls have been exercised.
        The original-R workflow adapter, including auto, still awaits actual
        end-to-end analysis and genuine replay qualification. Do not treat
        runtime adoption, R syntax checks or a completion marker as that
        qualification. No new literature-catalog credit is claimed.

        LDpred2 prepared-input workflow v1. Native Java implements inf and the
        complete serial grid with R-compatible integer seeds, stream jumps and
        source model ordering. Auto invokes the explicitly adopted original
        bigsnpr 1.12.2 or selected1.12.4 runtime in a Linux x86-64 JVM. Missing or
        forbidden prerequisites require SKIP3: skip dependent analysis, supply
        the named permitted setup and retry NEW output. No runtime is installed
        during analysis and no engine, method or edition is silently substituted.

        Inputs must already agree in variant order, effect alleles, genome build,
        population and effective sample-size definitions. Labels are caller
        declarations, not independently established ancestry/build. No matching,
        strand flipping, LD repair, thresholding or sample-size conversion occurs.
        Full CSC uses both triangles, sorted unique rows and unit diagonals.
        Numeric/input/work capacities are explicit in effective.properties.

        A completed grid/auto collection can contain REJECTED source-divergent
        candidates. Their raw diagnostics remain but no accepted effect file is
        provided. Never replace missing values or average rejected chains.
        Zero accepted candidates is FAIL2. Other analysis, verification or
        recording failures invalidate the WHOLE collection, including any
        earlier provisional files. Accept results only with this root's
        COMPLETED.properties and successful genuine --verify recomputation.
        WORKER_DONE.tsv alone is not an accepted workflow receipt.

        Native/worker JVM heap and original R address-space bounds are distinct.
        wall_ms and total work bytes/entries are monitored; work is not an OS
        disk quota. Cancel the recorded worker/parent normally so owned children
        can be stopped. Hard-killing a Windows parent has no process-job guarantee;
        stop its recorded worker if needed. There is no filesystem/network
        isolation or executable authenticity claim. Use permitted adequate
        capacities or skip: never thin variants or split LD dependencies to fit.

        Complete original auto R objects preserve source alpha NA values when
        use_mle=false. Raw sampled coefficients are standardized, not allele
        weights. No automatic chain averaging, phenotype tuning, best-score
        selection, downstream prediction or full paper reproduction is claimed.
        FAIL2: retain inputs, FAILURE.txt, runtime/worker consoles and original
        diagnostics. Correct the named cause and retry NEW output paths; do not
        refresh fingerprints to force verification. Use the original matching
        profile/artifact to verify historical bundles.
        """;
    private LdPred2WorkflowCli() {}
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err) {
        Path owned=null;
        try {
            if(args.length==1&&args[0].equals("--help")) {
                out.println("LDpred2 workflows: --request FILE --out NEW_DIRECTORY\nGenuine recomputation: --verify BUNDLE --out NEW_DIRECTORY\nRequired: "+new java.util.TreeSet<>(REQUIRED)+"\ninf: heritability. grid: models,seed. auto: engine=original,runtime,models,heritability,seed.\nSummary: variant effect_allele other_allele beta beta_se n_eff (tab-separated).\nLD variants: variant effect_allele other_allele. CSC files: little-endian int32 pointers/rows and float64 values.\nGrid models: model h2 p sparse; auto models: model p_init. See LDPRED2_WORKFLOWS.md.\n"+NOTICE);return 0;
            }
            Map<String,String> options=new TreeMap<>();
            for(int i=0;i<args.length;i+=2){if(i+1==args.length||!args[i].startsWith("--")||options.putIfAbsent(args[i].substring(2),args[i+1])!=null)throw new IllegalArgumentException("Use explicit unique option/value pairs; see --help");}
            boolean replay=options.containsKey("verify");if(!options.keySet().equals(replay?Set.of("verify","out"):Set.of("request","out")))throw new IllegalArgumentException("Use --request FILE --out NEW or --verify BUNDLE --out NEW");
            Path destination=Path.of(options.get("out")).toAbsolutePath().normalize();
            if(Files.exists(destination))throw new IOException("Output exists; preserve it and select a NEW directory");
            Path old=replay?Path.of(options.get("verify")).toRealPath():null;
            if(old!=null&&(destination.startsWith(old)||old.startsWith(destination)))throw new IOException("Verification output must be outside the original bundle");
            if(destination.getParent()==null)throw new IOException("Owned output parent required");Files.createDirectories(destination.getParent());
            destination=destination.getParent().toRealPath().resolve(destination.getFileName());
            if(old!=null&&(destination.startsWith(old)||old.startsWith(destination)))throw new IOException("Resolved output overlaps original bundle");
            Files.createDirectory(destination);owned=destination.toRealPath();write(owned.resolve("WARNINGS.md"),NOTICE);Files.createDirectory(owned.resolve("inputs"));
            Map<String,String> code=code();Config c;
            if(replay) {
                checkBundle(old);if(!Files.readString(old.resolve("code.tsv")).equals(render(code)))throw new IOException("Workflow implementation differs; use the original matching artifact/profile and preserve this receipt");
                c=config(load(old.resolve("effective.properties")));
                for(String key:INPUTS)copy(old.resolve(c.get(key)),owned.resolve("inputs/"+fileName(key)),c.number("max_input_bytes"));
                if(!c.get("method").equals("inf"))copy(old.resolve(c.get("models")),owned.resolve("inputs/models.tsv"),c.number("max_input_bytes"));
                copy(old.resolve("request.properties"),owned.resolve("request.properties"),1048576);
            } else {
                Path request=Path.of(options.get("request")).toRealPath();copy(request,owned.resolve("request.properties"),1048576);Config original=config(load(owned.resolve("request.properties")));
                Map<String,String> effective=new TreeMap<>(original.values());long captured=0;
                for(String key:INPUTS) {
                    Path source=resolve(request.getParent(),original.get(key));captured=Math.addExact(captured,Files.size(source));if(captured>original.number("max_input_bytes"))throw new IOException("Total captured input bytes exceed max_input_bytes");
                    copy(source,owned.resolve("inputs/"+fileName(key)),original.number("max_input_bytes"));effective.put(key,"inputs/"+fileName(key));
                }
                if(!original.get("method").equals("inf")){Path source=resolve(request.getParent(),original.get("models"));captured=Math.addExact(captured,Files.size(source));if(captured>original.number("max_input_bytes"))throw new IOException("Captured model/input bytes exceed max_input_bytes");copy(source,owned.resolve("inputs/models.tsv"),original.number("max_input_bytes"));effective.put("models","inputs/models.tsv");}
                if(original.original())effective.put("runtime",resolve(request.getParent(),original.get("runtime")).toString());c=config(toProperties(effective));
            }
            properties(owned.resolve("effective.properties"),c.values());write(owned.resolve("code.tsv"),render(code));
            Map<String,String> runtimeIdentity=null;
            if(c.original()) {
                LdPred2OriginalRuntime.check(c);runtimeIdentity=LdPred2OriginalRuntime.identity(c);
                if(replay&&!Files.readString(old.resolve("runtime-identity.tsv")).equals(render(runtimeIdentity)))throw new IOException("Original adopted runtime identity differs; use the matching original runtime/artifact, do not refresh saved fingerprints");
                write(owned.resolve("runtime-identity.tsv"),render(runtimeIdentity));Files.createDirectory(owned.resolve("runtime-records"));
                for(String name:runtimeIdentity.keySet())copy(Path.of(c.get("runtime")).resolve(name),owned.resolve("runtime-records").resolve(name),16777216);
            }
            LdPred2WorkflowProcess.capacity(owned,c);LdPred2WorkflowProcess.execute(owned,c);
            Map<String,String> summary=audit(owned,c);
            if(replay&&!inventory(old.resolve("results")).equals(inventory(owned.resolve("results"))))throw new IOException("Genuine recomputation produced different complete scientific results; retain both bundles and do not repair mismatches");
            if(!code.equals(code()))throw new IOException("Implementation changed during computation");
            if(c.original()){LdPred2OriginalRuntime.check(c);if(!runtimeIdentity.equals(LdPred2OriginalRuntime.identity(c)))throw new IOException("Original adopted runtime changed during computation");}
            write(owned.resolve("OUTCOME.txt"),"Accepted models: "+summary.get("accepted")+"\nRejected models: "+summary.get("rejected")+"\nInspect results/models.tsv. Rejected models have no accepted weights. All files remain provisional until the root COMPLETED.properties exists.\n");
            if(!"0".equals(summary.get("rejected")))err.println("WARNING: "+summary.get("rejected")+" original-divergent/nonfinite candidates rejected. Read OUTCOME.txt and results/models.tsv; do not use their diagnostics as weights.");
            LdPred2WorkflowProcess.capacity(owned,c);
            Map<String,String> inventory=inventory(owned);manifest(owned.resolve("FILES.tsv"),inventory);
            Map<String,String> marker=new TreeMap<>(summary);marker.put("profile",PROFILE);marker.put("kind",replay?"genuine-recomputation":"analysis");marker.put("manifest_sha256",sha(owned.resolve("FILES.tsv")));marker.put("parent_bundle",old==null?"":old.toString());
            properties(owned.resolve("COMPLETED.properties"),marker);
            LdPred2WorkflowProcess.capacity(owned,c);
            out.println("PASS "+PROFILE+" accepted="+summary.get("accepted")+" rejected="+summary.get("rejected")+" "+owned);out.flush();if(out.checkError())throw new IOException("Success recording to output stream failed; completion invalidated");return 0;
        } catch(Throwable error) {
            if(owned!=null) {
                try{if(Files.exists(owned.resolve("COMPLETED.properties")))Files.move(owned.resolve("COMPLETED.properties"),owned.resolve("INVALIDATED-"+UUID.randomUUID()+"-COMPLETED.properties"));write(owned.resolve("FAILURE.txt"),error.toString()+"\n"+NOTICE);}
                catch(Throwable recording){err.println("FAIL2_RECORDING_UNAVAILABLE "+recording);}
            }
            int code=error instanceof Prerequisite?3:2;err.println((code==3?"SKIP3":"FAIL2")+"_LDPRED2_UNADMITTED "+error+"\n"+NOTICE);return code;
        }
    }
    private static Properties toProperties(Map<String,String> values){Properties p=new Properties();p.putAll(values);return p;}
    private static String fileName(String key){return switch(key){case "summary","ld_variants"->key+".tsv";default->key+".bin";};}
    static String render(Map<String,String> code){StringBuilder text=new StringBuilder("member\tsha256\n");for(var e:new TreeMap<>(code).entrySet())text.append(e.getKey()).append('\t').append(e.getValue()).append('\n');return text.toString();}
    static Map<String,String> code()throws Exception {
        Map<String,String> result=new TreeMap<>();
        for(Class<?> type:List.of(LdPred2WorkflowCli.class,LdPred2WorkflowFiles.class,LdPred2WorkflowProcess.class,LdPred2WorkflowWorker.class,LdPred2OriginalRuntime.class,SimpleJson.class,org.jlinalg.genetics.LdCorrelation.class,org.jlinalg.genetics.LdPred2.class,org.jlinalg.genetics.LdPred2Random.class,org.jlinalg.genetics.LdPred2Seeds.class))classes(type,result);
        // Pin the existing numerical dependency as complete class bytes, even
        // when shaded in the executable JAR. No dependency implementation changes.
        var location=Path.of(jdistlib.Normal.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(Files.isRegularFile(location))try(var zip=new java.util.zip.ZipFile(location.toFile())) {
            var entries=zip.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(e.getName().startsWith("jdistlib/")&&e.getName().endsWith(".class")){try(var in=zip.getInputStream(e)){result.put(e.getName(),digest(in.readAllBytes()));}}}
        } else throw new IOException("Qualified numerical dependency JAR required");
        try(var in=LdPred2WorkflowCli.class.getResourceAsStream(LdPred2OriginalRuntime.RESOURCE)){if(in==null)throw new IOException("Packaged R worker unavailable; rebuild the full artifact");result.put("R/Worker.R",digest(in.readAllBytes()));}
        return result;
    }
    private static void classes(Class<?> type,Map<String,String> output)throws Exception{String name=type.getName().replace('.','/')+".class";try(var in=type.getClassLoader().getResourceAsStream(name)){if(in==null)throw new IOException("Class bytes unavailable: "+name);output.put(name,digest(in.readAllBytes()));}for(Class<?> child:type.getDeclaredClasses())classes(child,output);}
    private static String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void checkBundle(Path root)throws Exception {
        Properties marker=load(root.resolve("COMPLETED.properties"));if(!PROFILE.equals(marker.getProperty("profile"))||!sha(root.resolve("FILES.tsv")).equals(marker.getProperty("manifest_sha256")))throw new IOException("Qualified complete source bundle/manifest required");
        Map<String,String> expected=new TreeMap<>();try(BufferedReader in=Files.newBufferedReader(root.resolve("FILES.tsv"),StandardCharsets.UTF_8)){if(!"path\tbytes\tsha256".equals(in.readLine()))throw new IOException("Manifest header differs");for(String line;(line=in.readLine())!=null;){String[] x=fields(line,3);if(expected.putIfAbsent(x[0],x[1]+"\t"+x[2])!=null)throw new IOException("Duplicate manifest member");}}
        Map<String,String> actual=inventory(root);actual.remove("FILES.tsv");actual.remove("COMPLETED.properties");if(!actual.equals(expected))throw new IOException("Complete source bundle bytes/membership differ; no recomputation admitted");
    }
    private static Map<String,String> audit(Path root,Config c)throws Exception {
        Map<String,String> summary=new TreeMap<>();try(BufferedReader in=Files.newBufferedReader(root.resolve("WORKER_DONE.tsv"),StandardCharsets.UTF_8)) {
            if(!"key\tvalue".equals(in.readLine()))throw new IOException("Worker receipt header differs");for(String line;(line=in.readLine())!=null;){String[] x=fields(line,2);if(summary.putIfAbsent(x[0],x[1])!=null)throw new IOException("Duplicate worker receipt field");}
        }
        if(!summary.keySet().equals(Set.of("variants","models","accepted","rejected","engine","method"))||!c.get("engine").equals(summary.get("engine"))||!c.get("method").equals(summary.get("method")))throw new IOException("Worker receipt differs");
        int m=Integer.parseInt(summary.get("variants"));if(m<1||m>c.integer("max_variants"))throw new IOException("Worker variant count invalid");List<Model> models=models(root,c);int accepted=0,rejected=0;
        try(BufferedReader in=Files.newBufferedReader(root.resolve("results/models.tsv"),StandardCharsets.UTF_8)) {
            if(!"model\tstatus\treason".equals(in.readLine()))throw new IOException("Model outcome header differs");
            for(Model model:models){String line=in.readLine();if(line==null)throw new IOException("Missing model outcome");String[] x=fields(line,3);if(!model.id().equals(x[0]))throw new IOException("Model ordering/identity differs");Path folder=root.resolve("results").resolve(model.id());
                if(x[1].equals("accepted")){weightsAudit(root.resolve("inputs/summary.tsv"),folder.resolve("effects.tsv"),m);if(Files.exists(folder.resolve("sparse-effects.tsv")))weightsAudit(root.resolve("inputs/summary.tsv"),folder.resolve("sparse-effects.tsv"),m);accepted++;}
                else if(x[1].equals("rejected")){if(Files.exists(folder.resolve("effects.tsv"))||!Files.isRegularFile(folder.resolve("REFUSAL.txt")))throw new IOException("Rejected candidate has weights or lacks refusal");rejected++;}else throw new IOException("Unknown model status");
            }
            if(in.readLine()!=null)throw new IOException("Extra model outcome");
        }
        if(accepted<1||accepted!=Integer.parseInt(summary.get("accepted"))||rejected!=Integer.parseInt(summary.get("rejected"))||models.size()!=Integer.parseInt(summary.get("models")))throw new IOException("Accepted/rejected model accounting differs");return summary;
    }
    private static void weightsAudit(Path summary,Path weights,int m)throws Exception {
        try(BufferedReader s=Files.newBufferedReader(summary,StandardCharsets.UTF_8);BufferedReader w=Files.newBufferedReader(weights,StandardCharsets.UTF_8)) {
            s.readLine();if(!"variant\teffect_allele\tother_allele\teffect".equals(w.readLine()))throw new IOException("Weights header differs");
            for(int i=0;i<m;i++){String a=s.readLine(),b=w.readLine();if(a==null||b==null)throw new IOException("Effect/variant count differs");String[] x=fields(a,6),y=fields(b,4);for(int j=0;j<3;j++)if(!x[j].equals(y[j]))throw new IOException("Effect row identity/alleles differ");numeric(y[3]);}
            if(s.readLine()!=null||w.readLine()!=null)throw new IOException("Extra effect/variant rows");
        }
    }
}
