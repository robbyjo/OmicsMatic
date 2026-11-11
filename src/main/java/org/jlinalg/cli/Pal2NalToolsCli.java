/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.Pal2Nal;

/** Native typed codon back-translation and portable full-result verification. */
final class Pal2NalToolsCli {
    private Pal2NalToolsCli() { }
    static final String ACTION="Required action: inspect WARNINGS.md, rows.ndjson, cells.tsv, triplets.tsv, capture-receipts.json and the complete raw inputs. Require a valid COMPLETED.json and pal2nal-tools --verify DIRECTORY before downstream use. This is a native v14-matching contract, not a full original PAL2NAL executable or biological-accuracy certificate. Source table10 retains TGA=stop and tables3/11/13 retain historical initiation differences. V14_NOGAP uses uppercase universal stops; SELECTED_V14_TABLE_NOGAP is an explicit native filtering departure that retains original table10. Choose pairing, matching, placement, normalization, filters and verification thresholds explicitly. Relaxed success alone does not establish correspondence; review every mismatch, wildcard, unplaced DNA flank, multiple placement and filtered triplet. SKIP3 means an unavailable edition/code/capability or empty filtered alignment: skip dependent analysis and resolve the named cause. FAIL2 means invalid input, integrity, capacity, cancellation or finalization failure: no completed result is admitted. Preserve all partials, correct the cause and retry to a NEW directory; increase -Xmx and explicit capacities when needed. No external analysis executable, download or installation is required. Paper-exact builds and omitted author settings remain unresolved; do not guess paper-exact execution. Hashes prove integrity, not authenticity. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        Path root=null;boolean owns=false;
        Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("PAL2NAL interrupted");};
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==1&&args[0].equals("--example")){emit(out,Pal2NalJson.json(example()));return 0;}
            if(args.length==2&&args[0].equals("--verify")){var verified=Pal2NalBundle.verify(Path.of(args[1]),"COMPLETED.json",check);emit(out,"PAL2NAL native complete input, mapping, filtering and result recomputation verified; manifest SHA-256 "+verified.sha256()+". Biological correspondence and paper reproduction are not certified.");return 0;}
            var options=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--recipe","--out").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique PAL2NAL --recipe and --out values");
            if(options.size()!=2)throw new IOException("PAL2NAL requires --recipe and --out");
            root=Path.of(options.get("--out")).toAbsolutePath().normalize();Pal2NalBundle.create(root);owns=true;Files.createDirectory(root.resolve("inputs"));
            var initial=Pal2NalRecipe.Limits.defaults();var captures=new ArrayList<Pal2NalInputs.Capture>();
            Path request=Path.of(options.get("--recipe")).toAbsolutePath().normalize();captures.add(Pal2NalInputs.capture(root,request,"recipe","recipe-request.json",initial,2097152,check));
            byte[] raw=Pal2NalWorkflow.raw(root.resolve("recipe-request.json"),2097152);var spec=Pal2NalRecipe.parse(raw,request);var effective=spec.limits().forHeap();
            byte[] replay=Pal2NalRecipe.replay(raw);Pal2NalBundle.text(root,"recipe-replay.json",spec.limits(),w->w.write(new String(replay,StandardCharsets.UTF_8)),check);
            var ownedSpec=Pal2NalRecipe.parse(replay,root.resolve("recipe-replay.json"));var sources=spec.inputs();var targets=ownedSpec.inputs();long total=0;
            for(int i=0;i<sources.size();i++){
                long remaining=spec.limits().inputBytes()-total;if(remaining<1)throw new IOException("Complete combined PAL2NAL input byte capacity exceeded");
                String name=Pal2NalBundle.relative(root,targets.get(i));var capture=Pal2NalInputs.capture(root,sources.get(i),i==0?"protein":"input-"+i,name,spec.limits(),remaining,check);total=Math.addExact(total,capture.original().bytes());captures.add(capture);
            }
            Pal2NalBundle.json(root,"capture-receipts.json",spec.limits(),Map.of("schema_version",1,"captures",captures),check);
            var data=Pal2NalInputs.read(ownedSpec,effective,check);var result=Pal2Nal.align(data.proteins(),data.dna(),Pal2NalInputs.options(spec.options(),data.selection()),effective.engine(),check);
            if(result.outputColumns()==0)throw new Pal2NalRecipe.Unavailable("All expanded triplets were filtered; no nonempty codon alignment is available. Skip downstream fits and review the explicit filters");
            var pins=new TreeMap<String,Pal2NalBundle.Pin>();for(var capture:captures)pins.put(capture.snapshot(),Pal2NalBundle.pin(root.resolve(capture.snapshot()),Math.max(spec.limits().inputBytes(),2097152),check));
            Pal2NalBundle.json(root,"session.json",spec.limits(),Map.ofEntries(Map.entry("schema_version",1),Map.entry("tool",Pal2NalBundle.TOOL),Map.entry("scope",Pal2NalBundle.SCOPE),Map.entry("status","audited-provisional"),Map.entry("exit_code",0),Map.entry("scientific_result_admitted",false),Map.entry("original_tool_executed",false),Map.entry("recipe_sha256",pins.get("recipe-request.json").sha256()),Map.entry("input_snapshot_pins",pins),Map.entry("runtime_heap_bytes",Runtime.getRuntime().maxMemory()),Map.entry("effective_limits",effective)),check);
            for(var entry:Pal2NalWorkflow.outputs(ownedSpec,data,result,check).entrySet())Pal2NalBundle.text(root,entry.getKey(),spec.limits(),entry.getValue(),check);
            Pal2NalInputs.verifyOriginals(captures,spec.limits().inputBytes(),check);Pal2NalBundle.complete(root,spec.limits(),check);
            emit(out,"PAL2NAL native audited conversion completed: "+root+"\n"+ACTION);
            for(String warning:data.warnings())emit(err,"PAL2NAL WARNING: "+warning);for(String warning:result.warnings())emit(err,"PAL2NAL WARNING: "+warning);return 0;
        }catch(Exception failure){
            int code=failure instanceof Pal2NalRecipe.Unavailable?3:2;String label=code==3?"SKIP3 — ANALYSIS SKIPPED":"FAIL2 — NO RESULT ADMITTED";
            String detail=failure.toString();if(detail.length()>16384)detail=detail.substring(0,16384)+" [diagnostic text bounded; retained inputs are complete]";
            emit(err,"PAL2NAL "+label+": "+detail+"\n"+ACTION);
            if(owns)try{
                Files.writeString(root.resolve("FAILURE.md"),"# PAL2NAL "+label+"\n\n"+detail+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                Pal2NalJson.write(root.resolve("FAILURE.json"),Map.of("schema_version",1,"tool",Pal2NalBundle.TOOL,"exit_code",code,"status",label,"error",detail,"scientific_result_admitted",false));
            }catch(IOException diagnostic){emit(err,"PAL2NAL failure diagnostic could not be fully written: "+diagnostic+"\n"+ACTION);}return code;
        }
    }
    static Map<String,Object> example(){return Map.of("schema_version",1,"profile",Pal2NalRecipe.PROFILE,"protein",Map.of("path","protein.fasta","format","FASTA","headers","FIRST_FIELD"),"dna",Map.of("paths",List.of("dna.fasta"),"headers","FIRST_FIELD"),"selection",Map.of("mode","NONE"),"options",Map.of("codon_table",1,"pairing","EXACT_ID","matching","EXACT_ONLY","placement","REQUIRE_UNIQUE","gap_stop_filter","KEEP","remove_mismatch_columns",false,"normalize_documented_symbols",false,"normalize_tfastx",false,"minimum_verified_residues",3,"minimum_verified_fraction",1.0));}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        pal2nal-tools --recipe RECIPE.json --out NEW_DIRECTORY
        pal2nal-tools --verify DIRECTORY
        pal2nal-tools --example | --help
        Native Java v14/v14.1 matching: complete UTF-8 protein FASTA/CLUSTAL and 1..128 DNA FASTA files.
        Explicit headers, pairing, placement, exact/relaxed matching, original genetic code,
        normalization, selection and filtering. Native complete-cell/triplet ledgers and
        original DNA flanks are preserved; no external PAL2NAL executable is invoked.
        """+ACTION;}
}
