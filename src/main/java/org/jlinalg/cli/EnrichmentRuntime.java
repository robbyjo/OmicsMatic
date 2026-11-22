/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Explicit original R engine boundary; an analysis command never installs software. */
final class EnrichmentRuntime {
    static final Map<String,String> PINS=Map.of("DOSE","4.6.0","clusterProfiler","4.20.3","fgsea","1.38.0","enrichit","0.2.6","jsonlite","2.0.0");
    private final Path rscript,library,doseLibrary;
    private final Map<Path,String> hashes=new LinkedHashMap<>();
    private final Map<String,Object> session;
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    EnrichmentRuntime(Map<String,String> options,Path directory,Map<String,Object> session)throws Exception {
        this.session=session;
        try {
            if(!RegenieRuntime.windows())throw new IOException("The sealed enrichment profile requires Windows x86_64 R4.6.1");
            rscript=path(options,"rscript","JLINALG_ENRICHMENT_RSCRIPT");
            if(!Files.isRegularFile(rscript))throw new IOException("Rscript is absent: "+rscript);
            hashes.put(rscript,hash(rscript));library=path(options,"r-library","JLINALG_ENRICHMENT_R_LIBRARY");
            Path marker=library.resolve(".jlinalg-enrichment-runtime.json");var metadata=RegenieRuntime.object(marker);
            if(!Objects.equals(metadata.get("pins"),PINS))throw new IOException("Main enrichment package pins differ");
            seal(library,marker,metadata,"jlinalg-enrichment-runtime-v1");
            Files.copy(marker,directory.resolve("enrichment-runtime.json"));
            session.put("runtime_marker_sha256",hash(marker));session.put("runtime_library",library.toString());
            session.put("installed_package_versions",metadata.get("package_versions"));
            if(options.get("engine").equals("dose")) {
                doseLibrary=path(options,"dose-library","JLINALG_DOSE_LEGACY_R_LIBRARY");Path old=doseLibrary.resolve(".jlinalg-dose-legacy-runtime.json");var legacy=RegenieRuntime.object(old);
                if(!Objects.equals(legacy.get("version"),"4.5.0")||!Objects.equals(legacy.get("dependency_marker_sha256"),hash(marker)))throw new IOException("Legacy DOSE version or dependency profile changed");
                seal(doseLibrary,old,legacy,"jlinalg-dose-legacy-v1");Files.copy(old,directory.resolve("dose-legacy-runtime.json"));
                session.put("dose_legacy_marker_sha256",hash(old));session.put("dose_legacy_library",doseLibrary.toString());
            } else doseLibrary=null;
            session.put("rscript",rscript.toString());session.put("rscript_sha256",hash(rscript));
            execute(List.of(rscript.toString(),"--version"),directory,"r-version",30);
            if(!Files.readString(directory.resolve("r-version.log")).contains("version 4.6.1"))throw new IOException("Rscript version differs from R4.6.1");
        }catch(IOException|IllegalArgumentException failure){throw new Unavailable("Enrichment dependency unavailable or changed: "+failure.getMessage()+". "+setup(),failure);}
    }
    private static Path path(Map<String,String> options,String key,String environment)throws IOException {
        String value=options.get(key);if(value==null)value=System.getenv(environment);
        if(value==null||value.isBlank())throw new IOException("Supply --"+key+" or "+environment);
        return Path.of(value).toAbsolutePath().normalize();
    }
    private void seal(Path home,Path marker,Map<?,?> metadata,String schema)throws IOException {
        if(!Objects.equals(metadata.get("schema"),schema)||!(metadata.get("files") instanceof Map<?,?> files)||files.isEmpty())throw new IOException("Managed enrichment marker lacks exact file identities");
        var expected=new HashSet<Path>();
        for(var entry:files.entrySet()) {
            Path file=RegenieRuntime.contained(home,String.valueOf(entry.getKey()));String digest=String.valueOf(entry.getValue());
            if(!digest.matches("[0-9a-f]{64}")||!Files.isRegularFile(file)||!digest.equals(hash(file)))throw new IOException("Managed package file changed: "+file);
            expected.add(file);hashes.put(file,digest);
        }
        var observed=new HashSet<Path>();try(var stream=Files.walk(home)){stream.filter(Files::isRegularFile).filter(p->!p.equals(marker)).forEach(observed::add);}
        if(!observed.equals(expected))throw new IOException("Managed package inventory has unsealed or missing files; preserve this library and use a new permitted profile");
        hashes.put(marker,hash(marker));
    }
    void run(Path script,Path request,Path directory,int timeout)throws Exception {
        String interfaceHash=hash(script),requestHash=hash(request);
        execute(List.of(rscript.toString(),"--vanilla",script.toString(),library.toString(),request.toString(),directory.toString()),directory,"upstream",timeout);
        if(!interfaceHash.equals(hash(script))||!requestHash.equals(hash(request)))throw new IOException("Packaged enrichment interface/request changed during original execution");
    }
    String doseLibrary(){return doseLibrary==null?null:doseLibrary.toString();}
    private void execute(List<String> command,Path directory,String stage,int timeout)throws Exception {
        session.put(stage+"_command",List.copyOf(command));var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve(stage+".log").toFile());
        var env=builder.environment();env.put("LC_ALL","C");env.put("R_LIBS_SITE","");env.put("R_LIBS_USER",library.toString());env.remove("R_HOME");
        for(String key:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","VECLIB_MAXIMUM_THREADS"))env.put(key,"1");
        Process child=builder.start();try {
            if(!child.waitFor(timeout,TimeUnit.SECONDS)){stop(child);throw new IOException("Original enrichment stage "+stage+" timed out; inspect "+stage+".log and rerun into a new directory after correcting the resource bound");}
            session.put(stage+"_exit_code",child.exitValue());
            if(child.exitValue()==3)throw new Unavailable("Original enrichment runtime could not load; inspect "+stage+".log and dependency-error.txt. "+setup(),null);
            if(child.exitValue()!=0)throw new IOException("Original enrichment stage "+stage+" exited "+child.exitValue()+"; inspect "+stage+".log");
        }catch(InterruptedException failure){stop(child);Thread.currentThread().interrupt();throw failure;}
    }
    private static void stop(Process process)throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(30,TimeUnit.SECONDS);
    }
    void verify()throws IOException {RegenieRuntime.verify(hashes);}
    static String setup(){return "Where permitted run Rscript --vanilla tools/literature/install_enrichment_dependencies.R build/omics-enrichment-r build/literature-review/enrichment-dependency allow. Legacy DOSE additionally requires install_dose_legacy.R with a separate library and the main dependency library. Supply R4.6.1 and these exact profiles. If installation is unavailable or forbidden,SKIP the dependent analysis until a permitted profile is supplied; preserve changed/incomplete libraries and all diagnostics.";}
}
