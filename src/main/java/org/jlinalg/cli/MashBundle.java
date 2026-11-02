/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Native full-population replay before completion and on every portable verify. */
final class MashBundle {
    private MashBundle() { }
    static final String TOOL="mash-native",SCOPE="explicit-population-native-fit-and-full-posterior",ALGORITHM="gaussian-qr-plain-em-residual-v1";
    private static final int JSON_BYTES=8_388_608;
    private static final double ABSOLUTE=1e-10,RELATIVE=1e-9;
    record Replay(long numericValues,double maximumAbsoluteError,double maximumScaledError) { }
    record Verified(MashFiles.Pin manifest,Replay replay) { }
    static String warnings(MashNativeExecution.Result result){return """
        # Native mash scoped analysis

        Scope: explicit representative populations, supplied/canonical covariance bases,
        native plain-EM weights, fixed/simple/ad-hoc residual estimation and full target
        posteriors. Only the top-level COMPLETED.json plus successful mashr-tools --verify
        admits this scoped result. execution/result.tsv is a retained development-stage
        record; its public_bundle_admitted=false describes that stage alone.

        Read execution/WARNINGS.md in full for every numerical, prior-pruning and residual
        warning. Read execution/fit for every weight history and accepted/rejected residual
        proposal. RESIDUAL_ROLLBACK returns the preceding accepted model under the ad-hoc
        likelihood rule; it does not prove a global residual optimum.

        PCA/extreme-deconvolution learning, automatic RDS conversion, effect/P-value/SE
        derivation, inferred missingness, sharing/composite hypotheses and whole-paper
        reproduction are outside this native contract. Imported models must already be
        explicitly fully expanded. Strong rows are recorded but do not learn covariances.

        Verification replays every captured input and output, including fit histories,
        model, posterior covariance and warnings. Numeric outputs use fixed absolute
        1e-10 plus relative 1e-9 tolerance; identities, discrete states and choices are exact.
        Each complete production or replay obeys limits.execution; creation performs two
        complete executions and each verify performs one. Byte/census limits apply per
        evidence tree. Limits are admission checks, not an operating-system sandbox.

        Native execution needs Java only. SKIP3 means a requested unavailable capability:
        skip dependent analysis and use a supported, separately validated workflow. No
        fallback, population truncation, silent jitter or parameter guessing is performed.
        FAIL2 means invalid input, integrity, numerical, convergence, capacity, cancellation
        or finalization failure; no result is admitted. Required action: retain all inputs,
        partial outputs and diagnostics, correct the named cause or increase only permitted
        limits/heap, then retry into NEW output paths. Failed portable replay names retained
        scratch and leaves the source bundle unchanged. SHA-256 establishes integrity,
        not authenticity. Biological accuracy is not certified.
        """+"\nFit status: "+result.fitStatus()+"\n";}

    static MashNativeExecution.Result produce(Path root,Path input,byte[] limitRequest,Runnable check)throws IOException {
        String raw=new String(limitRequest,StandardCharsets.UTF_8);MashLimits limits=MashLimits.parse(SimpleJson.parse(raw));
        Files.write(root.resolve("limits-request.json"),limitRequest,StandardOpenOption.CREATE_NEW);MashFiles.write(root.resolve("limits.json"),limits);
        MashFiles.write(root.resolve("request.json"),Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"engine","native","input_directory",input.toString()));
        MashNativeExecution.Result result=MashNativeExecution.execute(input,root.resolve("execution"),limits.execution(),check);
        MashFiles.write(root.resolve("session.json"),session(result));Files.writeString(root.resolve("WARNINGS.md"),warnings(result),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        complete(root,limits,check);return result;
    }
    private static Map<String,Object> session(MashNativeExecution.Result result){return Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"algorithm",ALGORITHM,"status","audited-provisional","scoped_result_admitted",false,"original_tool_executed",false,"result",result);}
    private static void complete(Path root,MashLimits limits,Runnable check)throws IOException {
        MashFiles.Inventory before=MashFiles.inventory(root,limits,check);
        MashFiles.write(root.resolve("manifest.json"),Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"algorithm",ALGORITHM,"limits",limits,"files",new TreeMap<>(before.files()),"directories",new java.util.TreeSet<>(before.directories())));
        MashFiles.Pin manifest=MashFiles.pin(root.resolve("manifest.json"),JSON_BYTES,check);
        MashFiles.write(root.resolve("COMPLETION-PROVISIONAL.json"),Map.of("schema_version",1,"tool",TOOL,"scope",SCOPE,"algorithm",ALGORITHM,"manifest",manifest,"scoped_result_admitted",true,"original_tool_executed",false,"biological_accuracy_certified",false,"whole_paper_reproduced",false));
        MashFiles.Pin marker=MashFiles.pin(root.resolve("COMPLETION-PROVISIONAL.json"),262144,check);
        boolean moved=false;
        try {
            verify(root,"COMPLETION-PROVISIONAL.json",check);Files.move(root.resolve("COMPLETION-PROVISIONAL.json"),root.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);moved=true;
            var finalCensus=MashFiles.inventory(root,limits,check);var finalData=new TreeMap<>(finalCensus.files());finalData.remove("COMPLETED.json");finalData.remove("manifest.json");
            if(!finalData.equals(before.files())||!finalCensus.directories().equals(before.directories())||!manifest.equals(finalCensus.files().get("manifest.json"))||!marker.equals(finalCensus.files().get("COMPLETED.json")))throw new IOException("Native mash evidence changed during finalization; no completion marker is admitted");
        }
        catch(IOException|RuntimeException failure){if(moved)try{Files.delete(root.resolve("COMPLETED.json"));}catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;}
    }
    static Verified verify(Path supplied,String completion,Runnable check)throws IOException {
        if(!Set.of("COMPLETED.json","COMPLETION-PROVISIONAL.json").contains(completion))throw new IOException("Unknown native mash completion marker");
        Path root=supplied.toAbsolutePath().normalize();MashFiles.canonical(root);
        var markerPin=MashFiles.pin(root.resolve(completion),262144,check);
        Map<?,?> marker=MashLimits.keys(MashFiles.json(root.resolve(completion),262144),Set.of("schema_version","tool","scope","algorithm","manifest","scoped_result_admitted","original_tool_executed","biological_accuracy_certified","whole_paper_reproduced"));
        Map<?,?> manifest=MashLimits.keys(MashFiles.json(root.resolve("manifest.json"),JSON_BYTES),Set.of("schema_version","tool","scope","algorithm","limits","files","directories"));
        for(Map<?,?> record:List.of(marker,manifest))if(MashLimits.n(record,"schema_version")!=1||!TOOL.equals(record.get("tool"))||!SCOPE.equals(record.get("scope"))||!ALGORITHM.equals(record.get("algorithm")))throw new IOException("Native mash schema/tool/scope/algorithm differs");
        if(!Boolean.TRUE.equals(marker.get("scoped_result_admitted"))||!Boolean.FALSE.equals(marker.get("original_tool_executed"))||!Boolean.FALSE.equals(marker.get("biological_accuracy_certified"))||!Boolean.FALSE.equals(marker.get("whole_paper_reproduced")))throw new IOException("No admitted native-only scoped mash result");
        MashFiles.Pin manifestPin=MashFiles.pin(root.resolve("manifest.json"),JSON_BYTES,check);if(!manifestPin.equals(pin(marker.get("manifest"))))throw new IOException("Native mash completion/manifest integrity mismatch");
        MashLimits limits=MashLimits.parse(manifest.get("limits"));MashFiles.Inventory before=MashFiles.inventory(root,limits,check);
        var actual=new TreeMap<>(before.files());actual.remove(completion);actual.remove("manifest.json");
        if(!(manifest.get("files") instanceof Map<?,?> declared)||!actual.keySet().equals(declared.keySet()))throw new IOException("Missing/extra native mash evidence files");
        for(var entry:actual.entrySet())if(!entry.getValue().equals(pin(declared.get(entry.getKey()))))throw new IOException("Changed native mash evidence member: "+entry.getKey());
        if(!(manifest.get("directories") instanceof List<?> dirs)||dirs.size()!=before.directories().size()||!new java.util.HashSet<>(dirs).equals(before.directories())||!MashFiles.directories(before.files().keySet()).equals(before.directories()))throw new IOException("Changed/unexplained native mash evidence directory graph");
        Set<String> top=Set.of("request.json","limits-request.json","limits.json","session.json","WARNINGS.md");var seen=new java.util.HashSet<String>();
        for(String name:actual.keySet())if(!name.startsWith("execution/"))seen.add(name);if(!seen.equals(top))throw new IOException("Unexpected native mash top-level semantic graph");
        Object normalized=SimpleJson.parse(MashJson.json(limits));
        if(!normalized.equals(MashFiles.json(root.resolve("limits.json"),262144))||!limits.equals(MashLimits.parse(MashFiles.json(root.resolve("limits-request.json"),262144))))throw new IOException("Native mash requested and retained capacities differ");
        Map<?,?> request=MashLimits.keys(MashFiles.json(root.resolve("request.json"),262144),Set.of("schema_version","tool","scope","engine","input_directory"));
        if(MashLimits.n(request,"schema_version")!=1||!TOOL.equals(request.get("tool"))||!SCOPE.equals(request.get("scope"))||!"native".equals(request.get("engine"))||!(request.get("input_directory") instanceof String text)||text.isBlank()||text.length()>65536)throw new IOException("Invalid native mash producer request record");
        Path scratch=Files.createTempDirectory("omicsmatic-mash-verify-").toRealPath();
        if(scratch.startsWith(root)||root.startsWith(scratch))throw new IOException("Native mash replay scratch must be disjoint from source evidence");
        try {
            MashNativeExecution.Result replay=MashNativeExecution.execute(root.resolve("execution/inputs"),scratch.resolve("execution"),limits.execution(),check);
            if(!SimpleJson.parse(MashJson.json(session(replay))).equals(MashFiles.json(root.resolve("session.json"),262144)))throw new IOException("Native mash session/result claims differ from full replay");
            if(!warnings(replay).equals(MashFiles.text(root.resolve("WARNINGS.md"),65536)))throw new IOException("Required native mash warnings/recovery text changed");
            Replay compared=compare(root.resolve("execution"),scratch.resolve("execution"),limits,check);
            if(!before.equals(MashFiles.inventory(root,limits,check))||!markerPin.equals(MashFiles.pin(root.resolve(completion),262144,check))||!manifestPin.equals(MashFiles.pin(root.resolve("manifest.json"),JSON_BYTES,check)))throw new IOException("Native mash source bundle changed during complete replay");
            MashFiles.removeSuccessfulScratch(scratch);return new Verified(manifestPin,compared);
        }catch(IOException|RuntimeException failure){throw new IOException("Native mash portable replay failed; source bundle unchanged. Retain scratch "+scratch+". Required action: inspect the named discrepancy and retry verification after correction. "+failure,failure);}
    }
    private static MashFiles.Pin pin(Object value)throws IOException {Map<?,?> object=MashLimits.keys(value,Set.of("bytes","sha256"));Object bytes=object.get("bytes"),hash=object.get("sha256");if(!(bytes instanceof Number n)||n.longValue()<0||n.doubleValue()!=n.longValue()||!(hash instanceof String text)||!text.matches("[0-9a-f]{64}"))throw new IOException("Invalid complete native mash byte fingerprint");return new MashFiles.Pin(n.longValue(),text);}
    private static Replay compare(Path recorded,Path replay,MashLimits limits,Runnable check)throws IOException {
        var actual=MashFiles.inventory(recorded,limits,check);var expected=MashFiles.inventory(replay,limits,check);
        if(!actual.files().keySet().equals(expected.files().keySet())||!actual.directories().equals(expected.directories()))throw new IOException("Native mash replay has a different complete file/directory graph");
        manifest(recorded,"SHA256SUMS",limits,check);manifest(recorded.resolve("model"),"SHA256SUMS",limits,check);
        Statistics statistics=new Statistics();
        for(String name:new java.util.TreeSet<>(actual.files().keySet())){
            check.run();if(name.equals("SHA256SUMS")||name.equals("model/SHA256SUMS"))continue;
            Path a=recorded.resolve(name),b=replay.resolve(name);
            if(numeric(name))matrix(a,b,statistics,check);
            else if(name.matches("fit/weights-[0-9]+/history\\.tsv"))history(a,b,statistics,check);
            else if(name.equals("fit/final.tsv")||name.matches("fit/step-[0-9]+\\.tsv")||name.matches("fit/weights-[0-9]+/diagnostics\\.tsv"))keyed(a,b,statistics,check);
            else if(!actual.files().get(name).equals(expected.files().get(name)))throw new IOException("Native mash replay changed an exact identity, choice, warning or input: "+name);
        }
        return new Replay(statistics.count,statistics.absolute,statistics.scaled);
    }
    private static boolean numeric(String name){return name.startsWith("posterior/")||name.matches("model/(component-[0-9]+|residual|proportions)\\.tsv")||name.matches("fit/(expanded-component-[0-9]+|dirichlet|null-estimated-residual|final-training-component-loglik)\\.tsv")||name.matches("fit/(weights|proposal)-[0-9]+/(residual|proportions)\\.tsv");}
    private static void matrix(Path a,Path b,Statistics statistics,Runnable check)throws IOException {
        try(var x=Files.newBufferedReader(a,StandardCharsets.UTF_8);var y=Files.newBufferedReader(b,StandardCharsets.UTF_8)){
            for(;;){check.run();String p=x.readLine(),q=y.readLine();if(p==null||q==null){if(p!=q)throw new IOException("Truncated native mash complete numeric table");break;}String[] left=row(p),right=row(q);if(left.length!=right.length)throw new IOException("Native mash numeric width differs");for(int i=0;i<left.length;i++)statistics.near(left[i],right[i]);}
        }
    }
    private static void history(Path a,Path b,Statistics statistics,Runnable check)throws IOException {
        try(var x=Files.newBufferedReader(a,StandardCharsets.UTF_8);var y=Files.newBufferedReader(b,StandardCharsets.UTF_8)){
            String expected="iteration\trelative_objective\trelative_dual_gap\tmaximum_weight_change";if(!expected.equals(x.readLine())||!expected.equals(y.readLine()))throw new IOException("Native mash history header differs");
            for(;;){check.run();String p=x.readLine(),q=y.readLine();if(p==null||q==null){if(p!=q)throw new IOException("Truncated native mash complete optimizer history");break;}String[] left=row(p),right=row(q);if(left.length!=4||right.length!=4||!left[0].equals(right[0]))throw new IOException("Native mash exact optimizer iteration differs");for(int i=1;i<4;i++)statistics.near(left[i],right[i]);}
        }
    }
    private static void keyed(Path a,Path b,Statistics statistics,Runnable check)throws IOException {
        Set<String> floating=Set.of("log_likelihood","delta_log_likelihood","relative_objective","relative_dual_gap");
        try(var x=Files.newBufferedReader(a,StandardCharsets.UTF_8);var y=Files.newBufferedReader(b,StandardCharsets.UTF_8)){
            if(!"key\tvalue".equals(x.readLine())||!"key\tvalue".equals(y.readLine()))throw new IOException("Native mash state table header differs");
            for(;;){check.run();String p=x.readLine(),q=y.readLine();if(p==null||q==null){if(p!=q)throw new IOException("Truncated native mash complete state table");break;}String[] left=row(p),right=row(q);if(left.length!=2||right.length!=2||!left[0].equals(right[0]))throw new IOException("Native mash state key differs");if(floating.contains(left[0]))statistics.near(left[1],right[1]);else if(!left[1].equals(right[1]))throw new IOException("Native mash exact discrete state differs: "+left[0]);}
        }
    }
    private static String[] row(String line)throws IOException {if(line.length()>1_048_576)throw new IOException("Native mash replay line exceeds capacity");return line.split("\t",-1);}
    private static void manifest(Path root,String name,MashLimits limits,Runnable check)throws IOException {
        var files=new TreeMap<>(MashFiles.inventory(root,limits,check).files());files.remove(name);var expected=new TreeMap<String,String>();
        try(BufferedReader reader=Files.newBufferedReader(root.resolve(name),StandardCharsets.UTF_8)){for(String line;(line=reader.readLine())!=null;){check.run();if(line.length()<67||line.length()>65536||!line.substring(0,64).matches("[0-9a-f]{64}")||!line.substring(64,66).equals("  ")||expected.putIfAbsent(line.substring(66),line.substring(0,64))!=null)throw new IOException("Invalid/duplicate native mash stage hash manifest");}}
        if(!files.keySet().equals(expected.keySet()))throw new IOException("Native mash stage hash manifest does not cover every file");for(var item:files.entrySet())if(!item.getValue().sha256().equals(expected.get(item.getKey())))throw new IOException("Native mash stage hash differs: "+item.getKey());
    }
    private static final class Statistics {
        private long count;private double absolute,scaled;
        void near(String left,String right)throws IOException {try{double a=Double.parseDouble(left),b=Double.parseDouble(right);double difference=Math.abs(a-b);if(!Double.isFinite(a)||!Double.isFinite(b)||difference>ABSOLUTE+RELATIVE*Math.abs(b))throw new IOException("Native mash complete numeric replay mismatch: recorded="+a+", replay="+b);count++;absolute=Math.max(absolute,difference);scaled=Math.max(scaled,difference/Math.max(1,Math.abs(b)));}catch(NumberFormatException error){throw new IOException("Invalid native mash numeric replay table",error);}}
    }
}
