/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Typed single-alignment analysis with explicit refusal states and final portable integrity. */
final class IqtreeToolsCli {
    private IqtreeToolsCli() { }
    private static final String ACTION="Required action: read WARNINGS.md, session.json, output-audit.json, analyzed-population.json and the retained process logs. A scientific result requires COMPLETED.json and its matching manifest/member hashes; verify with iqtree-tools --verify DIRECTORY. SKIP3 means an exact runtime or implemented capability is unavailable/forbidden: skip the dependent analysis. Export runtime-only setup with iqtree-tools --export-runtime-setup NEW_DIRECTORY, then use Linux x86_64 Python3.11+ and the explicit permitted install/download flags in its README. FAIL2 means invalid input, integrity, execution, capacity or finalization failure: preserve all partial output, correct the stated cause and rerun to a NEW directory. All original rows/sites are preserved. Any source-internal duplicate pruning follows the explicit policy and is reported with its analyzed population and restored names. Editions and upstream-generated replacement alignments are never silently substituted. Partitions, dating, other genetic codes and unimplemented formats remain unavailable. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
        Path work=null;boolean owns=false;
        try {
            if(args.length==2&&args[0].equals("--verify")){var result=IqtreeBundle.verify(Path.of(args[1]));emit(out,"IQ-TREE bundle byte integrity verified; manifest SHA-256 "+result.sha256()+". This does not rerun inference or certify biological accuracy.");return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-setup")){IqtreeRuntime.exportSetup(Path.of(args[1]));emit(out,"IQ-TREE runtime-only setup exported. "+ACTION);return 0;}
            var options=new LinkedHashMap<String,String>();
            for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--recipe","--home","--out").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique supported IQ-TREE --recipe/--home/--out value options");
            if(!options.containsKey("--recipe")||!options.containsKey("--out"))throw new IOException("IQ-TREE requires --recipe and --out");
            work=Path.of(options.get("--out")).toAbsolutePath().normalize();
            Path home=options.containsKey("--home")?Path.of(options.get("--home")).toAbsolutePath().normalize():null;
            if(home!=null&&(home.startsWith(work)||work.startsWith(home)))throw new IOException("IQ-TREE runtime and output trees must be separate");
            IqtreeBundle.create(work);owns=true;int code=0;var session=new LinkedHashMap<String,Object>();session.put("tool","IQ-TREE");session.put("requested_options",options);
            var warnings=new ArrayList<String>();IqtreeRecipe.Spec spec=null;IqtreeRuntime.Context runtime=null;IqtreeAnalysis.Result result=null;
            Runnable cancellation=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("IQ-TREE analysis interrupted");};
            try {
                if(home==null)throw new IqtreeRuntime.Unavailable("No exact managed IQ-TREE --home was supplied");
                spec=IqtreeRecipe.read(Path.of(options.get("--recipe")));runtime=IqtreeRuntime.open(home,spec.profile());
                session.put("recipe",spec);IqtreeBundle.write(work.resolve("runtime-admission.json"),runtime);
                byte[] request;try(var input=Files.newInputStream(spec.source())){request=input.readNBytes(2097153);}if(request.length>2097152)throw new IOException("Recipe exceeds complete byte capacity");
                Files.write(work.resolve("recipe-request.json"),request,StandardOpenOption.CREATE_NEW);spec.verify();
                result=IqtreeAnalysis.run(spec,runtime,work,cancellation);warnings.addAll(result.warnings());spec.verify();runtime.verify();
                replay(work,request,spec.tree()!=null);session.put("result",result);session.put("status","audited-provisional-awaiting-finalization");
            } catch(IqtreeRuntime.Unavailable unavailable){code=3;session.put("status","SKIP3-unavailable-runtime-or-capability");session.put("error",unavailable.toString());warnings.add("ANALYSIS SKIPPED: "+unavailable.getMessage());}
            catch(Exception failure){code=2;session.put("status","FAIL2-no-scientific-result-admitted");session.put("error",failure.toString());warnings.add("ANALYSIS FAILED: "+failure.getMessage());}
            session.put("exit_code",code);session.put("warnings",warnings);session.put("scientific_result_admitted",false);session.put("admission_rule","This session is provisional history. Only a valid COMPLETED.json admits the result.");
            Files.writeString(work.resolve("WARNINGS.md"),"# IQ-TREE "+(code==0?"WARNINGS REQUIRE REVIEW":code==3?"SKIP3 ANALYSIS SKIPPED":"FAIL2 ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            IqtreeBundle.write(work.resolve("session.json"),session);
            long maximumBytes=spec==null?4294967296L:spec.limits().bundleBytes();int maximumFiles=spec==null?10000:spec.limits().bundleFiles();
            var census=IqtreeBundle.inventory(work,maximumBytes,maximumFiles,cancellation);Path manifest=work.resolve("manifest.json");
            IqtreeBundle.write(manifest,Map.of("schema_version",1,"tool","IQ-TREE","scope","single-alignment","status",session.get("status"),"files",IqtreeBundle.portable(census),"directories",new TreeSet<>(census.directories()),"maximum_bundle_bytes",maximumBytes,"maximum_bundle_files",maximumFiles));
            if(code==0) {
                Objects.requireNonNull(spec).verify();Objects.requireNonNull(runtime).verify();
                var finalCensus=IqtreeBundle.inventory(work,maximumBytes,maximumFiles,cancellation);var finalFiles=new TreeMap<>(finalCensus.files());finalFiles.remove("manifest.json");if(!new IqtreeBundle.Inventory(finalFiles,finalCensus.directories()).equals(census))throw new IOException("IQ-TREE bundle changed during finalization");
                Path provisional=work.resolve("COMPLETION-PROVISIONAL.json");
                IqtreeBundle.write(provisional,Map.of("schema_version",1,"tool","IQ-TREE","scope","single-alignment","status","completed-audited-single-alignment",
                        "manifest_sha256",IqtreeFiles.identity(manifest,16777216,cancellation).sha256(),"scientific_result_admitted",true,"biological_accuracy_certified",false));
                IqtreeBundle.verify(work,"COMPLETION-PROVISIONAL.json");
                Files.move(provisional,work.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
            }
            emit(code==0?out:err,"IQ-TREE "+(code==0?"completed-audited-single-alignment":session.get("status"))+"; output: "+work+"\n"+String.join("\n",warnings)+"\n"+ACTION);return code;
        } catch(Exception failure) {
            emit(err,"IQ-TREE FAIL2: "+failure+"\n"+ACTION);
            if(owns)try{Files.writeString(work.resolve("FINALIZATION_FAILED.md"),"# IQ-TREE FAIL2 INCOMPLETE BUNDLE\n\n"+failure+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){emit(err,"IQ-TREE FAIL2 diagnostic write failed: "+diagnostic+". "+ACTION);}return 2;
        }
    }
    @SuppressWarnings("unchecked") private static void replay(Path work,byte[] raw,boolean tree)throws IOException {
        Map<String,Object> root=(Map<String,Object>)SimpleJson.parse(new String(raw,StandardCharsets.UTF_8));var copy=new LinkedHashMap<>(root);
        var alignment=new LinkedHashMap<>((Map<String,Object>)root.get("alignment"));alignment.put("path","inputs/alignment.original");copy.put("alignment",alignment);
        if(tree){var t=new LinkedHashMap<>((Map<String,Object>)root.get("tree"));t.put("path","inputs/tree.original");copy.put("tree",t);}
        IqtreeBundle.write(work.resolve("recipe-replay.json"),copy);
    }
    private static void emit(PrintStream out,String text){out.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        iqtree-tools --recipe RECIPE.json --home EXACT_PRIVATE_HOME --out NEW_DIRECTORY
        iqtree-tools --export-runtime-setup NEW_DIRECTORY
        iqtree-tools --verify DIRECTORY
        Single complete DNA, protein or standard-code codon alignment; explicit FASTA/PHYLIP layout.
        Fixed built-in models, ModelFinder/standard selection, ML inference/fixed-tree evaluation,
        standard/ultrafast bootstrap and SH-aLRT. Explicit seed, threads, outgroup and capacities.
        Exact editions: 1.6.12, 2.1.2, 2.1.4-beta, 2.2.0.3, 2.3.1, 2.3.5, 2.3.6, 2.4.0.
        Linux Java and an admitted private runtime are required; use WSL on Windows.
        Preserves every input row/site, original source outputs and reversible taxon identities.
        Complete output/model/support audits and final portable member hashes are required.
        Original clamped AICc and signed roundoff remain explicit; no biological-accuracy certificate.
        Partitions, dating, batch gene trees, other codes/formats and custom/mixture models remain open.
        """+ACTION;}
}
