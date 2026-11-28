/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

/** Explicit complete-source RNA command; missing capabilities never produce a partial success. */
public final class NfcoreRnasplice104ToolsCli {
    private NfcoreRnasplice104ToolsCli() { }
    static final String ACTION="Required action: require the latest attempt's COMPLETED.json and successful --verify before downstream use. Read WARNINGS.md, FAILURE.json when present, engine-process/process.stderr and the original engine trace/logs. SKIP3 means a required exact runtime, permitted dependency or task capability is missing/forbidden: skip dependent analysis, complete permitted setup separately and retry NEW paths. No analysis downloads or installs software. FAIL2 means invalid/changed input, process, numerical, model, capacity, cancellation or finalization failure: no result is admitted. Preserve every attempt; never reduce data, repair scientific IDs or substitute a model to pass. Resume uses the same captured inputs, producing JAR and original cache; a live/indeterminate run must not be restarted. All source-default FASTQ branches remain selected, including the original skip_bigwig=true default. MISO read length/plot genes and rMATS read length/statistical pairing are explicit user choices; paired-end FASTQs do not establish biological pairing. For paired statistics the actual original BAM arrival order must match every declared biological pair; the adapter never reorders it. Post-fit selection is raw P < the declared maximum and absolute delta PSI >= the declared minimum, without FDR substitution; original NA values and both ID columns remain intact. Source Salmon tables are checked for complete cohort/target census, finite domains and printed-precision TPM totals; source rMATS tables are streamed in full with explicit capacities. Other original fits are executed and retained but do not have independent numerical parity contracts here; MISO can be stochastic. Original STAR2.7.10a differs from P027's reported2.7.9a, and the human test data do not reproduce that worm paper. Source usage documentation mentions plain FASTQs but its locked checker accepts only .fq.gz/.fastq.gz. The source replicate checker consumes its reader early; this adapter explicitly checks distinct biological replicates. Portable verification recomputes selected source-output checks from the full retained original engine bundle; it does not rerun fits, readmit a host or certify biological accuracy or whole-paper reproduction. Hashes establish integrity, not authenticity. Cell Ranger remains excluded.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==0||args.length==1&&args[0].equals("--help")){emit(out,help());return out.checkError()?2:0;}
            if(args.length==2&&args[0].equals("--verify")){var verified=NfcoreRnasplice104Bundle.verify(Path.of(args[1]));emit(out,"Original rnasplice1.0.4 selected contracts verified: "+verified.attempt()+"; manifest SHA-256 "+verified.manifest().sha256()+".\n"+ACTION);return out.checkError()?2:0;}
            var options=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--request","--out","--resume","--home","--setup-report").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique typed RNA value options; see --help");boolean resume=options.containsKey("--resume");if(resume&&(options.containsKey("--request")||options.containsKey("--out"))||!resume&&(!options.containsKey("--request")||!options.containsKey("--out")))throw new IOException("New execution requires --request and --out; resume uses only the saved session plus runtime home/report");Path root=Path.of(options.get(resume?"--resume":"--out")).toAbsolutePath().normalize();Path attempt=NfcoreRnasplice104Workflow.run(root,resume?null:Path.of(options.get("--request")).toAbsolutePath().normalize(),optional(options,"--home"),optional(options,"--setup-report"),resume);emit(out,"Original rnasplice1.0.4 complete default FASTQ graph and selected contracts completed: "+attempt+".\n"+ACTION);return out.checkError()?2:0;
        }catch(Exception|LinkageError failure){int code=NfcoreRnasplice104Workflow.exit(failure);emit(err,(code==3?"SKIP3 - ANALYSIS SKIPPED: ":"FAIL2 - NO RESULT ADMITTED: ")+failure+"\n"+ACTION);return code;}
    }
    private static Path optional(java.util.Map<String,String> options,String name){return options.containsKey(name)?Path.of(options.get(name)).toAbsolutePath().normalize():null;}
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        nfcore-tools RNASPLICE-1.0.4 --request REQUEST.json --home MANAGED_NEXTFLOW24_HOME --setup-report COMPLETED_SETUP --out NEW_SESSION
        nfcore-tools RNASPLICE-1.0.4 --resume SESSION --home MANAGED_NEXTFLOW24_HOME --setup-report COMPLETED_SETUP
        nfcore-tools RNASPLICE-1.0.4 --verify SESSION
        Full exact rnasplice1.0.4 source, original31 images, nf-validation1.1.3,
        original standard Nextflow24.04.4 and admitted private Apptainer runtime.
        Declare all biological samples/technical libraries, contrasts, references,
        MISO choices, rMATS model/pairing, task/executor and result capacities.
        Use a permitted native Linux filesystem supporting the original STAR FIFO.
        See docs/nfcore-rnasplice104.md and the generated example request.
        """+ACTION;}
}
