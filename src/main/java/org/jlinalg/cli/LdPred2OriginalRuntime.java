/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import static org.jlinalg.cli.LdPred2WorkflowFiles.*;

/** Original public bigsnpr methods, using an explicitly adopted immutable Linux runtime. */
final class LdPred2OriginalRuntime {
    static final String RESOURCE="/org/jlinalg/omics/ldpred2-workflow-v2/Worker.R";
    private LdPred2OriginalRuntime() {}
    static Properties check(Config c)throws Exception {return check(c,true);}
    private static Properties check(Config c,boolean full)throws Exception {
        if(!System.getProperty("os.name").equals("Linux")||!java.util.Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Prerequisite("Original bigsnpr profile requires a permitted Linux x86-64 JVM and adopted runtime. Run Java inside Linux/WSL or skip this analysis; no native/edition substitution.");
        for(String helper:List.of("/usr/bin/prlimit","/usr/bin/setpriv"))if(!Files.isExecutable(Path.of(helper)))throw new Prerequisite("Required Linux helper unavailable: "+helper+". Install permitted util-linux helpers or skip dependent analysis.");
        Path requested=Path.of(c.get("runtime"));if(!Files.isDirectory(requested)||!Files.isRegularFile(requested.resolve("runtime.properties")))throw new Prerequisite("Adopted original runtime unavailable: "+requested+". Supply permitted setup or skip dependent analysis.");
        Path root=requested.toRealPath();Properties p=load(root.resolve("runtime.properties"));
        if(!"original-bigsnpr-ldpred2-runtime-v1".equals(p.getProperty("profile"))||!java.util.Set.of("1.12.2","1.12.4").contains(p.getProperty("edition")))throw new IOException("Unsupported original runtime profile/edition");
        if(!Files.isRegularFile(root.resolve("READY.json")))throw new Prerequisite("Runtime adoption is incomplete; retain its failures and supply a separately completed permitted runtime");
        if(!(SimpleJson.parse(Files.readString(root.resolve("READY.json"))) instanceof Map<?,?> ready)||!"READY_ADOPTED_ORIGINAL_BIGSNPR_LDPRED2_RUNTIME_NOT_ANALYSIS".equals(ready.get("status"))||!p.getProperty("edition").equals(ready.get("edition")))throw new IOException("Completed runtime adoption record differs");
        String sourceExpected=p.getProperty("edition").equals("1.12.2")?"849d6cc30ca8b121c4492e6d7248ae51956dc6fa6c940a8f89ebcfc33afb931f":"7b69135fa732e11c63a259236a360a15cb72ae1eb94df7958369c71fea5f87a3";
        String archiveExpected=p.getProperty("edition").equals("1.12.2")?"443a5529ae2596eb3cf8f19760893b96beba4f12a4decbf6bea645db4d2187f6":"17d0b436315de24ccc638d0f817508e6ef14cefb066b4c2e60a2fcb0465f38d3";
        if(!sourceExpected.equals(p.getProperty("original_r_sha256"))||!archiveExpected.equals(p.getProperty("original_archive_sha256")))throw new IOException("Exact selected original source/edition required");
        if(!Files.isExecutable(Path.of(p.getProperty("rscript"))))throw new Prerequisite("Adopted original R executable is missing; supply the permitted runtime or skip dependent analysis");
        if(c.get("method").equals("auto")&&p.getProperty("edition").equals("1.12.2")&&c.values().containsKey("p_min"))throw new IllegalArgumentException("bigsnpr 1.12.2 has fixed original p bounds; do not pass the later p_bounds option");
        Path source=root.resolve("original-LDpred2.R");if(!sha(source).equals(p.getProperty("original_r_sha256")))throw new IOException("Adopted original R source record differs");
        Path manifest=root.resolve("runtime-files.tsv");if(!sha(manifest).equals(p.getProperty("manifest_sha256")))throw new IOException("Runtime file manifest differs; retain this runtime record and install/adopt a separately qualified replacement");
        if(!full)return p;
        List<String[]> entries=new ArrayList<>();
        try(BufferedReader in=Files.newBufferedReader(manifest,StandardCharsets.UTF_8)) {
            if(!"kind\tpath\ttarget\tsha256".equals(in.readLine()))throw new IOException("Runtime manifest header differs");
            for(String line;(line=in.readLine())!=null;) {
                entries.add(fields(line,4));
            }
        }
        if(entries.size()!=((Number)ready.get("files")).intValue())throw new IOException("Complete adopted runtime file count differs");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<Void>> checks=new ArrayList<>();
            for(String[] row:entries)checks.add(pool.submit(()->{
                Path file=Path.of(row[1]);
                if(row[0].equals("link")) {
                    if(!Files.isSymbolicLink(file)||!Files.readSymbolicLink(file).toString().equals(row[2]))throw new IOException("Runtime symbolic link differs: "+file);
                    if(!row[3].equals("-")&&(!Files.isRegularFile(file)||!sha(file).equals(row[3])))throw new IOException("Linked runtime bytes differ: "+file);
                } else if(row[0].equals("file")) {
                    if(!Files.isRegularFile(file))throw new Prerequisite("Adopted runtime file is missing: "+file+"; restore permitted prerequisites or skip analysis");
                    if(Files.isSymbolicLink(file)||!sha(file).equals(row[3]))throw new IOException("Adopted runtime bytes differ: "+file);
                } else throw new IOException("Unknown runtime entry kind");
                return null;
            }));
            for(var future:checks)try{future.get();}catch(java.util.concurrent.ExecutionException failure){if(failure.getCause() instanceof Exception cause)throw cause;throw new IOException("Runtime fingerprinting failed",failure.getCause());}
        } finally {pool.shutdownNow();}
        return p;
    }
    static void execute(Path root,Config c)throws Exception {
        Properties runtime=check(c,false);
        Path script=root.resolve("original-worker.R");try(var input=LdPred2OriginalRuntime.class.getResourceAsStream(RESOURCE)) {
            if(input==null)throw new Prerequisite("Packaged original R worker unavailable; build the matching current source or skip analysis");Files.copy(input,script);
        }
        StringBuilder control=new StringBuilder("key\tvalue\n");for(var e:new java.util.TreeMap<>(c.values()).entrySet())control.append(e.getKey()).append('\t').append(e.getValue()).append('\n');write(root.resolve("control.tsv"),control.toString());
        long bytes=Math.multiplyExact(c.number("r_address_space_mb"),1048576L);
        List<String> command=new ArrayList<>(List.of("/usr/bin/prlimit","--as="+bytes,"--","/usr/bin/setpriv","--pdeathsig","KILL","--",runtime.getProperty("rscript"),"--vanilla",script.toString(),root.toString(),c.get("runtime")));
        write(root.resolve("runtime-command.txt"),String.join("\n",command)+"\n");
        ProcessBuilder builder=new ProcessBuilder(command).directory(root.toFile()).redirectOutput(root.resolve("runtime.stdout").toFile()).redirectError(root.resolve("runtime.stderr").toFile());
        builder.environment().put("OPENBLAS_NUM_THREADS","1");builder.environment().put("OMP_NUM_THREADS","1");builder.environment().put("MKL_NUM_THREADS","1");
        builder.environment().put("R_LIBS_USER",root.resolve("unused-user-library").toString());
        Files.createDirectory(root.resolve("tmp"));builder.environment().put("TMPDIR",root.resolve("tmp").toString());
        Process process=builder.start();int result;
        try{result=process.waitFor();}catch(InterruptedException error){process.destroyForcibly();Thread.currentThread().interrupt();throw error;}
        write(root.resolve("runtime-exit.txt"),Integer.toString(result)+"\n");
        if(result!=0)throw new IOException("Original bigsnpr failed with exit "+result+"; preserve runtime.stderr and raw R outputs; no complete workflow result");
    }
    static Map<String,String> identity(Config c)throws IOException {
        Path root=Path.of(c.get("runtime"));Map<String,String> result=new java.util.TreeMap<>();
        for(String name:List.of("runtime.properties","runtime-files.tsv","original-LDpred2.R","READY.json"))result.put(name,sha(root.resolve(name)));return result;
    }
}
