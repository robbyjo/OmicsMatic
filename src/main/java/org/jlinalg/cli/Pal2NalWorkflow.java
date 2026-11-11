/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.Pal2Nal;

/** Deterministic streamed ledgers and complete native recomputation on relocation. */
final class Pal2NalWorkflow {
    private Pal2NalWorkflow() { }
    static Map<String,Pal2NalBundle.Text> outputs(Pal2NalRecipe.Spec spec,Pal2NalInputs.Data input,Pal2Nal.Result result,Runnable check) {
        var files=new TreeMap<String,Pal2NalBundle.Text>();var warnings=new ArrayList<>(input.warnings());warnings.addAll(result.warnings());
        files.put("input-report.json",w->line(w,Map.of("schema_version",1,"protein_format",spec.format(),"protein_headers",spec.proteinHeaders(),"dna_headers",spec.dnaHeaders(),"selection_mode",spec.selection(),"statistics",input.statistics())));
        files.put("summary.json",w->line(w,Map.ofEntries(Map.entry("schema_version",1),Map.entry("tool",Pal2NalBundle.TOOL),Map.entry("scope",Pal2NalBundle.SCOPE),Map.entry("profile",spec.profile()),Map.entry("options",result.options()),Map.entry("protein_columns",result.proteinColumns()),Map.entry("expanded_columns",result.expandedColumns()),Map.entry("output_columns",result.outputColumns()),Map.entry("rows",result.rows().size()),Map.entry("work_steps",result.workSteps()),Map.entry("warning_count",warnings.size()),Map.entry("original_tool_executed",false),Map.entry("biological_accuracy_certified",false),Map.entry("whole_paper_reproduced",false))));
        files.put("rows.ndjson",w->{int index=0;for(var row:result.rows()){
            check.run();var fields=new LinkedHashMap<String,Object>();fields.put("row",++index);fields.put("output_id",outputId(index));fields.put("protein_id",row.proteinId());fields.put("dna_id",row.dnaId());fields.put("protein_header",input.proteinHeaders().get(row.proteinId()));fields.put("dna_header",input.dnaHeaders().get(row.dnaId()));fields.put("dna_length",row.originalDna().length());fields.put("dna_start",row.dnaStart());fields.put("dna_end",row.dnaEnd());fields.put("relaxed",row.relaxed());fields.put("placements_at_least",row.placementsAtLeast());fields.put("verified_residues",row.verifiedResidues());fields.put("wildcard_residues",row.wildcardResidues());fields.put("mismatched_residues",row.mismatchedResidues());line(w,fields);
        }});
        files.put("cells.tsv",w->{w.write("row\tprotein_column\tdna_start\tbases\tstate\n");int index=0;for(var row:result.rows()){
            index++;for(var cell:row.cells()){check.run();w.write(index+"\t"+cell.proteinColumn()+"\t"+cell.dnaStart()+"\t"+cell.bases()+"\t"+cell.state()+"\n");}
        }});
        files.put("triplets.tsv",w->{w.write("expanded_start\tprotein_column\tpart\toutput_start\tdrops\n");for(var triplet:result.triplets()){
            check.run();w.write(triplet.expandedStart()+"\t"+triplet.proteinColumn()+"\t"+triplet.part()+"\t"+triplet.outputStart()+"\t"+String.join(",",triplet.drops().stream().map(Enum::name).sorted().toList())+"\n");
        }});
        files.put("protein.normalized.fasta",w->{for(var row:result.rows()){check.run();fasta(w,row.proteinId(),row.normalizedProtein());}});
        files.put("alignment.expanded.fasta",w->{for(var row:result.rows()){check.run();fasta(w,row.proteinId(),row.expandedAlignment());}});
        files.put("alignment.fasta",w->{for(var row:result.rows()){check.run();fasta(w,row.proteinId(),row.alignment());}});
        files.put("alignment.phy",w->{
            w.write(result.rows().size()+" "+result.outputColumns()+"\n");int row=0;
            for(var item:result.rows()){check.run();w.write(outputId(++row)+" ");for(int i=0;i<item.alignment().length();i+=80){w.write(item.alignment(),i,Math.min(80,item.alignment().length()-i));w.write('\n');}}
        });
        files.put("alignment.aln",w->{
            w.write("CLUSTAL W native PAL2NAL codon alignment; opaque row IDs are recorded in rows.ndjson\n\n");
            for(int start=0;start<result.outputColumns();start+=60){int row=0;for(var item:result.rows()){check.run();w.write(outputId(++row)+"    ");w.write(item.alignment(),start,Math.min(60,result.outputColumns()-start));w.write('\n');}w.write('\n');}
        });
        files.put("WARNINGS.md",w->{w.write("# PAL2NAL NATIVE — WARNINGS REQUIRE REVIEW\n\n");for(String warning:warnings){check.run();w.write(warning);w.write("\n\n");}w.write(Pal2NalToolsCli.ACTION);w.write('\n');});
        return files;
    }
    private static void line(Writer writer,Object object)throws IOException{writer.write(Pal2NalJson.json(object));writer.write('\n');}
    static String outputId(int row){if(row<1||row>99_999_999)throw new IllegalArgumentException("Opaque PHYLIP row ID exceeds ten-column capacity");return String.format(Locale.ROOT,"PN%08d",row);}
    private static void fasta(Writer writer,String id,String sequence)throws IOException {
        writer.write('>');writer.write(id);writer.write('\n');for(int i=0;i<sequence.length();i+=80){writer.write(sequence,i,Math.min(80,sequence.length()-i));writer.write('\n');}if(sequence.isEmpty())writer.write('\n');
    }
    static byte[] raw(Path path,int maximum)throws IOException {
        IqtreeFiles.identity(path,maximum,()->{});byte[] bytes;try(var in=Files.newInputStream(path)){bytes=in.readNBytes(maximum+1);}if(bytes.length>maximum)throw new IOException("Complete PAL2NAL metadata capacity exceeded");return bytes;
    }
    static void verify(Path root,Pal2NalRecipe.Limits declared,Map<String,Pal2NalBundle.Pin> actual,Runnable check)throws IOException {
        byte[] original=raw(root.resolve("recipe-request.json"),2097152),replay=raw(root.resolve("recipe-replay.json"),2097152);
        if(!Arrays.equals(replay,Pal2NalRecipe.replay(original)))throw new IOException("PAL2NAL replay recipe does not preserve every original setting except the declared rebased input paths");
        var spec=Pal2NalRecipe.parse(replay,root.resolve("recipe-replay.json"));
        if(!Pal2NalJson.json(spec.limits()).equals(Pal2NalJson.json(declared)))throw new IOException("PAL2NAL manifest and declared recipe capacities differ");
        Map<?,?> session=Pal2NalJson.object(root.resolve("session.json"),2097152);
        Pal2NalRecipe.keys(session,Set.of("schema_version","tool","scope","status","exit_code","scientific_result_admitted","original_tool_executed","recipe_sha256","input_snapshot_pins","runtime_heap_bytes","effective_limits"),Set.of());
        if(!Long.valueOf(1).equals(session.get("schema_version"))||!Pal2NalBundle.TOOL.equals(session.get("tool"))||!Pal2NalBundle.SCOPE.equals(session.get("scope"))||!"audited-provisional".equals(session.get("status"))||!Long.valueOf(0).equals(session.get("exit_code"))||!Boolean.FALSE.equals(session.get("scientific_result_admitted"))||!Boolean.FALSE.equals(session.get("original_tool_executed"))||!actual.get("recipe-request.json").sha256().equals(session.get("recipe_sha256")))throw new IOException("PAL2NAL provisional session/completion binding differs");
        long heap=Pal2NalRecipe.integer(session.get("runtime_heap_bytes"),33554432,Long.MAX_VALUE);
        if(!Pal2NalJson.json(declared.atHeap(heap)).equals(Pal2NalJson.json(session.get("effective_limits"))))throw new IOException("PAL2NAL effective capacities do not match the recorded heap bound");
        var snapshots=new TreeMap<String,Pal2NalBundle.Pin>();snapshots.put("recipe-request.json",actual.get("recipe-request.json"));long total=0;
        for(Path path:spec.inputs()){
            if(!path.startsWith(root.resolve("inputs")))throw new IOException("PAL2NAL replay input escapes its owned snapshot directory");
            String name=Pal2NalBundle.relative(root,path);Pal2NalBundle.Pin pin=actual.get(name);if(pin==null)throw new IOException("Missing complete PAL2NAL input snapshot");total=Math.addExact(total,pin.bytes());snapshots.put(name,pin);
        }
        if(total>declared.inputBytes()||!Pal2NalJson.json(snapshots).equals(Pal2NalJson.json(session.get("input_snapshot_pins"))))throw new IOException("PAL2NAL complete snapshot population/capacity differs");
        Map<?,?> captures=Pal2NalJson.object(root.resolve("capture-receipts.json"),8388608);Pal2NalRecipe.keys(captures,Set.of("schema_version","captures"),Set.of());
        if(!Long.valueOf(1).equals(captures.get("schema_version"))||!(captures.get("captures") instanceof List<?> list)||list.size()!=snapshots.size())throw new IOException("PAL2NAL capture census differs");
        var captured=new HashSet<String>();
        for(Object item:list){
            Map<?,?> row=Pal2NalRecipe.object(item);Pal2NalRecipe.keys(row,Set.of("role","originalPath","original","snapshot"),Set.of());
            String name=Pal2NalRecipe.text(row,"snapshot");if(!captured.add(name)||!snapshots.containsKey(name))throw new IOException("Duplicate/extra PAL2NAL capture receipt");
            Pal2NalRecipe.text(row,"role");Pal2NalRecipe.text(row,"originalPath");Map<?,?> identity=Pal2NalRecipe.object(row.get("original"));Pal2NalRecipe.keys(identity,Set.of("bytes","sha256","fileKey","modifiedTime"),Set.of());
            if(Pal2NalRecipe.integer(identity.get("bytes"),0,declared.bundleBytes())!=snapshots.get(name).bytes()||!snapshots.get(name).sha256().equals(identity.get("sha256")))throw new IOException("PAL2NAL captured/original byte identity differs");
            Pal2NalRecipe.text(identity,"fileKey");Pal2NalRecipe.text(identity,"modifiedTime");
        }
        var effective=declared.atHeap(Math.min(heap,Runtime.getRuntime().maxMemory()));
        var inputs=Pal2NalInputs.read(spec,effective,check);var result=Pal2Nal.align(inputs.proteins(),inputs.dna(),Pal2NalInputs.options(spec.options(),inputs.selection()),effective.engine(),check);
        if(result.outputColumns()==0)throw new IOException("Empty filtered PAL2NAL alignment is not admitted for downstream analysis");
        var generated=outputs(spec,inputs,result,check);var expectedNames=new HashSet<>(snapshots.keySet());expectedNames.addAll(generated.keySet());expectedNames.addAll(Set.of("recipe-replay.json","capture-receipts.json","session.json"));
        if(!expectedNames.equals(actual.keySet()))throw new IOException("PAL2NAL member census includes missing/extra scientific evidence");
        for(var entry:generated.entrySet())if(!Pal2NalBundle.digest(entry.getValue()).equals(actual.get(entry.getKey())))throw new IOException("PAL2NAL complete native ledger/output recomputation differs: "+entry.getKey());
        check.run();
    }
}
