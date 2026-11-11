/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

/** Native Java complete bedGraph-to-bigWig conversion and captured whole-bundle verification. */
public final class BigWigToolsCli {
    private BigWigToolsCli() { }
    /** @param args typed unique options; use --help */
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Native bigWig interrupted");};try{
        if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}if(args.length==2&&args[0].equals("--verify")){report(BigWigWorkflow.verify(path(args[1]),check),true,out,err);return 0;}
        var options=new LinkedHashMap<String,String>();var allowed=Set.of("--input","--sizes","--genome-build","--units","--input-format","--float32","--dictionary","--compression","--records","--line-chars","--input-bytes","--decoded-bytes","--output-bytes","--workspace-bytes","--items-per-section","--tree-fanout","--out");for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!allowed.contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique typed native bigWig value options; use --help");if(!options.containsKey("--out"))throw new IOException("Require --out NEW_DIRECTORY");for(String name:new String[]{"--input","--sizes"})if(options.containsKey(name))options.put(name,path(options.get(name)).toString());var request=BigWigRequest.cli(options);report(BigWigWorkflow.start(request,path(options.get("--out")),check),false,out,err);return 0;
    }catch(Exception failure){boolean skip=failure instanceof AccessDeniedException;emit(err,"Native bigWig "+(skip?"SKIP3 — CONVERSION SKIPPED":"FAIL2 — NO RESULT ADMITTED")+": "+failure+"\n"+BigWigWorkflow.ACTION);return skip?3:2;}}
    private static Path path(String value){return Path.of(value).toAbsolutePath().normalize();}
    private static void report(BigWigWorkflow.Result result,boolean verified,PrintStream out,PrintStream err)throws IOException {emit(out,(verified?"Verified captured":"Completed native")+" bigWig: "+result.report().records()+" complete intervals; "+result.report().coveredBases()+" represented bases; "+result.report().dictionary().size()+" stored chromosomes. "+result.root());for(String line:NextflowFiles.utf8(NextflowFiles.bytes(result.root().resolve("WARNINGS.md"),2097152)).lines().toList())if(line.startsWith("WARNING:"))emit(err,line);}
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        bigwig-tools --input SIGNAL.bedGraph --sizes REFERENCE.chrom.sizes --genome-build DECLARED_BUILD
          --units DECLARED_SIGNAL_UNITS --float32 exact|round --dictionary used|all --compression zlib|none --out NEW_DIRECTORY
        bigwig-tools --verify COMPLETE_BUNDLE
        Native Java; no external runtime, dependency installation or network acquisition.
        Plain input is default; --input-format gzip reads complete concatenated members with CRC and strict EOF.
        Sizes are local headerless two-column chromosome/length text; no automatic assembly/resource inference.
        Coordinates are zero-based half-open unsigned32. One input block per chromosome, sorted nonoverlapping
        intervals within each block. Initial track/browser metadata and comments are retained and counted.
        exact refuses any literal-decimal information lost in stored float32; round explicitly accepts it.
        Every value/precision disposition and declared/stored/omitted reference row is retained in TSV ledgers.
        Complete raw indexes/data are written; no zoom approximations. Native double summaries differ from UCSC.
        Optional capacities (bytes are positive integers):
          --records 1000000 --line-chars 65536 --input-bytes 268435456 --decoded-bytes 268435456
          --output-bytes 1073741824 --workspace-bytes 4294967296 --items-per-section 1024 --tree-fanout 256
        Input bytes cover BOTH unchanged source files; decoded bytes cover BOTH complete UTF8 streams.
        The requested output maximum and16MiB final metadata reserve must fit beside all prepared evidence.
        Source/target paths may contain Unicode/spaces; the output parent must exist and be canonical.
        Verification works on copied Windows/Linux bundles without opening recorded producer paths.
        """+BigWigWorkflow.ACTION;}
}
