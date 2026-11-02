/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Typed original MAFFT alignment/addition with complete populations and portable final receipts. */
final class MafftToolsCli {
    private MafftToolsCli() { }
    private static final String ACTION="Required action: read WARNINGS.md, session.json, output-audit.json, taxon-map.json, residue-map.tsv and raw process/host-admission logs. Require valid COMPLETED.json and matching manifest/member hashes; verify with mafft-tools --verify DIRECTORY. SKIP3 means an exact runtime, permitted dependency or implemented capability is unavailable: skip dependent analysis. Export setup with mafft-tools --export-runtime-setup NEW_DIRECTORY and use Linux x86_64 Python3.11+ with the README's explicit permitted install/download flags. Analysis requires Linux Java, an exact managed home and a NEW canonical ASCII Linux output path. Original FASTA paths and headers can be Unicode. FAIL2 means invalid input, integrity, process, capacity or finalization failure: no result is admitted; preserve all partial files, correct the cause and use NEW paths. Keep-length deliberately deletes insertions; review every deletion in residue-map.tsv. Gap-only backbone-column identities can remain unresolved. Literal unusual symbols and multithreaded refinement require source-behavior review. A marker, successful process or one valid row alone is not a completed result. No biological alignment-accuracy or whole-paper reconstruction certificate is claimed. P052 major-only v7 is unresolved; do not substitute another edition. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
        if(args.length==1&&(args[0].equals("--example")||args[0].equals("--example-add"))){try{emit(out,MafftJson.json(example(args[0].equals("--example-add"))));return 0;}catch(IOException failure){emit(err,"MAFFT FAIL2: "+failure+"\n"+ACTION);return 2;}}
        Path work=null;boolean owns=false;
        try {
            if(args.length==2&&args[0].equals("--verify")){var verified=MafftBundle.verify(Path.of(args[1]));emit(out,"MAFFT bundle integrity and complete native alignment/residue-ledger replay verified; manifest SHA-256 "+verified.sha256()+". The aligner is not rerun and biological accuracy is not certified.");return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-setup")){MafftRuntime.exportSetup(Path.of(args[1]));emit(out,"MAFFT runtime-only setup exported.\n"+ACTION);return 0;}
            var options=new LinkedHashMap<String,String>();
            for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--recipe","--home","--out").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique supported MAFFT --recipe/--home/--out value options");
            if(!options.containsKey("--recipe")||!options.containsKey("--out"))throw new IOException("MAFFT requires --recipe and --out");
            work=Path.of(options.get("--out")).toAbsolutePath().normalize();Path home=options.containsKey("--home")?Path.of(options.get("--home")).toAbsolutePath().normalize():null;
            if(home!=null&&(home.startsWith(work)||work.startsWith(home)))throw new IOException("MAFFT runtime and output trees must be separate");
            MafftBundle.create(work);owns=true;var session=new LinkedHashMap<String,Object>();session.put("tool","MAFFT");session.put("requested_options",options);var warnings=new ArrayList<String>();
            MafftRecipe.Spec spec=null;MafftRuntime.Context runtime=null;MafftAnalysis.Result result=null;int code=0;
            Runnable cancellation=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("MAFFT interrupted");};
            try {
                Path source=Path.of(options.get("--recipe")).toAbsolutePath().normalize();var original=IqtreeFiles.identity(source,2097152,cancellation);byte[] request;
                try(var in=Files.newInputStream(source)){request=in.readNBytes(2097153);}if(request.length>2097152)throw new IOException("Complete MAFFT recipe capacity exceeded");
                Files.write(work.resolve("recipe-request.json"),request,StandardOpenOption.CREATE_NEW);if(!original.equals(IqtreeFiles.identity(source,2097152,cancellation)))throw new IOException("MAFFT recipe changed during capture");
                spec=MafftRecipe.read(source);session.put("recipe",spec);
                if(home==null)throw new MafftRuntime.Unavailable("No exact managed MAFFT --home was supplied");
                runtime=MafftRuntime.open(home,spec.profile());MafftBundle.write(work.resolve("runtime-admission.json"),runtime);
                if(runtime.knownWarning()!=null)emit(err,"MAFFT ORIGINAL RELEASE WARNING: "+runtime.knownWarning()+"\nThe exact requested edition is retained; review this before downstream interpretation.");
                if(!work.toString().matches("/[A-Za-z0-9/._+-]+"))throw new MafftRuntime.Unavailable("Original MAFFT owned-work paths require ASCII without whitespace/metacharacters; use a new canonical ASCII Linux output directory");
                result=MafftAnalysis.run(spec,runtime,work,cancellation);warnings.addAll(result.warnings());spec.verify();runtime.verify();replay(work,request);
                session.put("result",result);session.put("status","audited-provisional-awaiting-finalization");
            }catch(MafftRuntime.Unavailable unavailable){code=3;session.put("status","SKIP3-unavailable-runtime-or-capability");session.put("error",unavailable.toString());warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage());}
            catch(Exception failed){code=2;session.put("status","FAIL2-no-alignment-result-admitted");session.put("error",failed.toString());warnings.add("ANALYSIS FAILED: "+failed.getMessage());}
            session.put("exit_code",code);session.put("warnings",warnings);session.put("scientific_result_admitted",false);session.put("admission_rule","Session is provisional history; only a valid COMPLETED.json admits the result.");
            Files.writeString(work.resolve("WARNINGS.md"),"# MAFFT "+(code==0?"WARNINGS REQUIRE REVIEW":code==3?"SKIP3 — ANALYSIS SKIPPED":"FAIL2 — NO RESULT ADMITTED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            MafftBundle.write(work.resolve("session.json"),session);
            long bytes=spec==null?4294967296L:spec.limits().bundleBytes();int files=spec==null?20000:spec.limits().bundleFiles();
            var census=MafftBundle.inventory(work,bytes,files,cancellation);
            MafftBundle.write(work.resolve("manifest.json"),Map.of("schema_version",1,"tool","MAFFT","scope","complete-alignment","status",session.get("status"),"files",MafftBundle.portable(census),"directories",new TreeSet<>(census.directories()),"maximum_bundle_bytes",bytes,"maximum_bundle_files",files));
            if(code==0) {
                Objects.requireNonNull(spec).verify();Objects.requireNonNull(runtime).verify();
                var now=MafftBundle.inventory(work,bytes,files,cancellation);var finalFiles=new TreeMap<>(now.files());finalFiles.remove("manifest.json");
                if(!new MafftBundle.Inventory(finalFiles,now.directories()).equals(census))throw new IOException("MAFFT bundle changed during finalization");
                Path provisional=work.resolve("COMPLETION-PROVISIONAL.json");MafftBundle.write(provisional,Map.of("schema_version",1,"tool","MAFFT","scope","complete-alignment","status","completed-audited-alignment","manifest_sha256",IqtreeFiles.identity(work.resolve("manifest.json"),16777216,cancellation).sha256(),"scientific_result_admitted",true,"biological_accuracy_certified",false));
                MafftBundle.verify(work,"COMPLETION-PROVISIONAL.json");
                Files.createLink(work.resolve("COMPLETED.json"),provisional);Files.delete(provisional);MafftBundle.verify(work);
            }
            emit(code==0?out:err,"MAFFT "+(code==0?"completed-audited-alignment":session.get("status"))+"; output: "+work+"\n"+String.join("\n",warnings)+"\n"+ACTION);return code;
        }catch(Exception failed){
            emit(err,"MAFFT FAIL2: "+failed+"\n"+ACTION);
            if(owns)try{Files.writeString(work.resolve("FINALIZATION_FAILED.md"),"# MAFFT FAIL2 — INCOMPLETE BUNDLE\n\n"+failed+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){emit(err,"MAFFT FAIL2 diagnostic could not be written: "+diagnostic+"\n"+ACTION);}return 2;
        }
    }
    @SuppressWarnings("unchecked") private static void replay(Path root,byte[] original)throws IOException {
        var request=new LinkedHashMap<>((Map<String,Object>)SimpleJson.parse(new String(original,StandardCharsets.UTF_8)));request.put("input","inputs/input.original.fasta");if(request.containsKey("additions"))request.put("additions","inputs/additions.original.fasta");MafftBundle.write(root.resolve("recipe-replay.json"),request);
    }
    private static Map<String,Object> example(boolean add){
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("profile","linux-x86_64-mafft-7.526");r.put("operation",add?"ADD":"DE_NOVO");r.put("input",add?"backbone.fasta":"input.fasta");
        if(add){r.put("additions","added.fasta");r.put("addition_gaps","REMOVE");}
        r.put("alphabet",add?"PROTEIN":"NUCLEOTIDE");r.put("symbols","BIOLOGICAL");r.put("headers","WHOLE_HEADER");r.put("input_gaps",add?"PRESERVE_ALIGNMENT":"REMOVE");r.put("strategy","AUTO");r.put("scoring",Map.of("source_defaults",true));r.put("parameters",Map.of("threads",1,"output_order","INPUT","keep_length",add));return r;
    }
    private static void emit(PrintStream out,String value){out.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        mafft-tools --recipe RECIPE.json --home EXACT_PRIVATE_HOME --out NEW_DIRECTORY
        mafft-tools --export-runtime-setup NEW_DIRECTORY
        mafft-tools --verify DIRECTORY
        mafft-tools --example | --example-add
        Complete nucleotide/protein plain UTF-8 FASTA, de novo or add to an existing alignment.
        Explicit alphabet/symbol/header/gap policies, source-default/auto/local/global/generalized
        pairwise and FFT strategies, maximum refinement iterations, source-default or explicit
        BLOSUM/gap scoring, declared threads/output order and complete capacities.
        Keep-length includes a complete original-residue deletion map. All original input files,
        full source outputs, restored headers/case and the residue ledger are retained.
        Exact original Linux x86_64 editions7.313,7.475,7.487,7.490,7.505,7.526.
        RNA structural extensions,DASH/network,custom matrices,codon translation,add-fragments,
        add-profile,direction inference and batch contracts remain separate unfinished capabilities.
        """+ACTION;}
}
