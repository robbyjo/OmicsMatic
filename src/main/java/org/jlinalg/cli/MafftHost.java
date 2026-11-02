/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Declared MAFFT host bindings using the existing complete file/ELF/Perl capture primitive.
 * Captures dependencies only; scientific admission still requires complete input/output audits. */
final class MafftHost {
    private MafftHost() { }
    static RsemHost.Limits defaults() {
        return new RsemHost.Limits(new RepeatHostFiles.Limits(67108864,2147483648L,100000,128,67108864),
            new RsemProcess.Limits(120000,2147483648L,60,33554432,512,268435456,10000,32,128,1073741824,25),1024);
    }
    record Admission(MafftRuntime.Context runtime,RsemHost.Result capture,RsemHost.Limits limits) {
        void verify(Runnable cancellation)throws IOException {
            cancellation.run();RsemHost.verifyFiles(capture.snapshot(),new Provider(runtime),limits,cancellation);cancellation.run();
        }
    }
    static Map<String,String> environment(Path work,int threads)throws IOException {
        if(!work.isAbsolute()||!work.normalize().equals(work)||!work.toString().matches("/[A-Za-z0-9/._+-]+")||threads<1||threads>256)throw new IOException("MAFFT owned work requires an absolute ASCII path and explicit 1..256 threads");
        return Map.ofEntries(Map.entry("PATH","/usr/bin:/bin"),Map.entry("MAFFT_TMPDIR",work.toString()),Map.entry("LANG","C"),Map.entry("LC_ALL","C"),
            Map.entry("TZ","UTC"),Map.entry("OMP_NUM_THREADS",Integer.toString(threads)),Map.entry("OPENBLAS_NUM_THREADS","1"),Map.entry("MKL_NUM_THREADS","1"));
    }
    static Admission capture(MafftRuntime.Context runtime,Path newWork,RsemHost.Limits limits,Runnable cancellation)throws IOException {
        Objects.requireNonNull(runtime);Objects.requireNonNull(limits);Objects.requireNonNull(cancellation);
        try {
            var capture=RsemHost.capture(new Provider(runtime),newWork,limits,cancellation);
            var result=new Admission(runtime,capture,limits);result.verify(cancellation);return result;
        }catch(RsemProcess.UnavailableDependency unavailable){throw new MafftRuntime.Unavailable("MAFFT declared host dependency unavailable: "+unavailable.getMessage(),unavailable);}
        catch(IOException failed){throw new IOException("MAFFT declared host/library/Perl binding failed; preserve diagnostics: "+failed.getMessage(),failed);}
    }
    /** The shared provider interface carries the MAFFT payload and its additional launcher helpers. */
    private record Provider(MafftRuntime.Context runtime) implements RsemProvider {
        public Path home(){return runtime.home();}
        public String profile(){return runtime.profile();}
        public String lockSha256(){return "e66f242f6f3ee4dfe34bc971869bd301ac4d4eec1ef629d786062472764cffcb";}
        public List<Path> libraryDirectories(){return List.of();} // Original MAFFT numeric programs are static.
        public List<Path> providerRoots(){return List.of(runtime.home());}
        public List<Path> executablePaths(){
            var paths=new TreeSet<Path>();runtime.helpers().keySet().forEach(name->paths.add(Path.of(name)));
            for(String name:runtime.files().keySet())if(Files.isExecutable(runtime.home().resolve(name)))paths.add(runtime.home().resolve(name));
            return List.copyOf(paths);
        }
        public Map<String,String> environment(Path work)throws IOException {
            return MafftHost.environment(work,1);
        }
        public void verify(Runnable cancellation)throws IOException{cancellation.run();runtime.verify();cancellation.run();}
    }
}
