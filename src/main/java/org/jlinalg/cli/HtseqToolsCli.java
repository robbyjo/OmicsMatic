/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Closed original file-counting and portable verification entry point. */
public final class HtseqToolsCli {
    private HtseqToolsCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            int engineIndex=java.util.List.of(args).indexOf("--engine");
            if(engineIndex>=0&&java.util.List.of(args).contains("--help")) {
                if(engineIndex+1>=args.length)throw new java.io.IOException("engine requires native or upstream");
                if(args[engineIndex+1].equals("native"))return HtseqNativeCli.run(new String[]{"--help"},out,err);
                if(!args[engineIndex+1].equals("upstream"))throw new java.io.IOException("engine must be native or upstream");
                args=new String[]{"--help"};
            }
            if(java.util.List.of(args).contains("--engine")) {
                var nativeOptions=FollowupSupport.options(args,java.util.Set.of("engine","request"));
                String engine=FollowupSupport.required(nativeOptions,"engine");
                if(engine.equals("native"))return HtseqNativeCli.run(new String[]{"--request",FollowupSupport.required(nativeOptions,"request")},out,err);
                if(!engine.equals("upstream"))throw new java.io.IOException("engine must be native or upstream");
                args=new String[]{"--request",FollowupSupport.required(nativeOptions,"request")};
            }
            if(args.length==1&&args[0].equals("--help")){
                emit(out,"Java-only alternative: htseq-tools --engine native --request REQUEST.json (explicit HTSeq2.1.2 file counts and native replay VERIFY; no runtime_home). Use --engine native --help or docs/native-tool-replacements.md for that schema.\nThe default upstream interface follows:");
                emit(out,"HTSeq file counting: --request UTF8_JSON\nCOUNT requires schema_version1, operation COUNT, exact version0.5.3|0.6.0|0.11.1|0.13.5|2.1.2, runtime_home, alignment, annotation, format sam|bam, order name|pos, stranded yes|no|reverse, overlap union|intersection-strict|intersection-nonempty, nonunique none|all|fraction, secondary/supplementary ignore|score, minimum_mapq0..255, feature_type, id_attribute, genome_label, annotation_label, counting_unit original-auto and NEW out. Edition capability restrictions apply; no RANDOM or compressed SAM/GTF. Optional explicit capacities: max_input_bytes(each), max_output_bytes, max_count_rows(including5summary rows), timeout_seconds, memory_bytes. Relative paths resolve against the request JSON.\nVERIFY requires only schema_version1, operation VERIFY, run and NEW out. It needs Java only and checks bundle integrity/schema/exact derived tables, without native SAM/GTF parsing/pairing, HTSeq re-execution, execution authentication or biological certification.\nCOUNT needs Linux Java and the declared private HTSeq/Python/NumPy/pysam profile. The original parser determines read/pair units. No normalization, proper-pair filter, barcode/UMI/custom peak-by-cell counting or full-paper workflow is supplied. Keep filenames ASCII on legacy Windows launchers; exact UTF8 data paths belong inside JSON.\n"+HtseqFileContract.ACTION);return 0;
            }
            if(args.length==1&&args[0].equals("--source-report")){out.writeBytes(HtseqFileContract.resource("PROFILES.json"));return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new java.io.IOException("Require --request UTF8_JSON, --source-report or --help");
            var request=HtseqFileContract.request(Path.of(args[1]));
            if(request.operation().equals("COUNT"))HtseqFileCounting.count(request);else HtseqFileCounting.verify(request);
            emit(out,"HTSeq "+request.operation()+" completed with scope warnings: "+request.out());emit(err,"HTSEQ WARNINGS AND REQUIRED ACTION: "+request.out().resolve("WARNINGS.md"));return 0;
        }catch(Exception failure){
            int code=failure instanceof HtseqCountRuntime.Unavailable?3:2;
            emit(err,(code==3?"SKIP3: HTSEQ ANALYSIS SKIPPED":"FAIL2: NO HTSEQ RESULT ADMITTED")+": "+failure+"\n"+HtseqFileContract.ACTION);return code;
        }
    }
    private static void emit(PrintStream out,String value){out.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
