/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal source-call, raw-artifact and returned-row projections; not an independent LD refit. */
final class TwoSampleMrClumpAudit {
    private TwoSampleMrClumpAudit() { }
    static void outputs(Path root,Map<String,Object> r,boolean compare)throws IOException {
        Path worker=root.resolve("worker");var report=read(worker.resolve("report.json"),64000000);var runtime=read(worker.resolve("runtime-profile.json"));
        require("completed-with-warnings".equals(report.get("status"))&&Boolean.TRUE.equals(report.get("results_admitted"))&&Boolean.TRUE.equals(report.get("selection_complete"))&&Boolean.TRUE.equals(report.get("no_statistical_estimate_produced")),"Original selection/status is incomplete");
        require(Boolean.FALSE.equals(report.get("TwoSampleMR_namespace_rewritten"))&&Boolean.TRUE.equals(report.get("ieugwasr_bindings_restored"))&&list(report.get("observation_failures"),"observation failures").isEmpty(),"Original binding restoration or observation failed");
        for(var value:List.of(report,runtime)) {
            require(integer(value,"TwoSampleMR_source_functions_guarded",0,152,152)==152&&integer(value,"ieugwasr_source_functions_guarded",0,7,7)==7,"Original source guard population differs");
        }
        require("4.6.1".equals(runtime.get("R"))&&"0.7.14".equals(runtime.get("TwoSampleMR"))&&"c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6".equals(runtime.get("TwoSampleMR_commit"))&&"1.2.1".equals(runtime.get("ieugwasr"))&&"ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6".equals(runtime.get("ieugwasr_commit")),"Original runtime identity differs");
        var binding=read(worker.resolve("runtime-bindings.json"));var plinkBinding=read(worker.resolve("plink-binding.json"));
        require(TwoSampleMrClumpContracts.PROFILE.equals(binding.get("profile"))&&TwoSampleMrClumpContracts.PLINK_SHA256.equals(binding.get("plink_sha256"))&&TwoSampleMrClumpContracts.PLINK_SHA256.equals(plinkBinding.get("sha256"))&&text(binding,"plink_executable").equals(text(plinkBinding,"executable")),"Exact PLINK/R binding differs");
        if(compare) {
            var manifest=read(root.resolve("manifest.json"));require(integer(manifest,"process_exit_code",-1,0,0)==0&&TwoSampleMrClumpContracts.PLINK_SHA256.equals(manifest.get("plink_executable_sha256"))&&same(manifest.get("command"),List.of(text(binding,"rscript"),"--vanilla","../source/twosamplemr_clump.R")),"Original owned process status/binding differs");
        }
        var calls=list(report.get("calls"),"original calls");require(calls.size()==1,"Expected one original top-level clump_data call");
        var top=object(calls.get(0),"original call");require("clump_data".equals(top.get("label")),"Wrong original top-level operation");objects(worker,top,"objects/001-clump_data");
        var metadata=object(report.get("tables"),"table metadata");var loaded=new HashMap<String,TwoSampleMrWorkflowFiles.Table>();
        for(var entry:metadata.entrySet()) {
            String name=entry.getKey();require(name.matches("(?:clumped|decisions)[.]tsv|ld-calls/[0-9]{3}-(?:ld_clump|ld_clump_local)/(?:input|returned)[.]tsv"),"Unsafe or unknown original table");
            var table=TwoSampleMrClumpFiles.table(worker.resolve(name),r);var shape=object(entry.getValue(),"table shape");
            require(integer(shape,"rows",-1,0,2000000)==table.rows().size()&&list(shape.get("columns"),"columns").equals(table.columns())&&list(shape.get("types"),"types").size()==table.columns().size()&&list(shape.get("row_names"),"row names").size()==table.rows().size(),"Original table shape differs: "+name);loaded.put(name,table);
        }
        var intake=TwoSampleMrClumpFiles.prepare(root,r,true);var input=intake.instruments();var ld=list(report.get("ld_calls"),"grouped calls");require(!ld.isEmpty()&&ld.size()<=1000000,"Invalid grouped-call population");
        var batch=object(ld.get(0),"batch call");require("ld_clump".equals(batch.get("name")),"Missing original batch call");String batchFolder="ld-calls/001-ld_clump";objects(worker,batch,batchFolder);options(batch,r);
        var batchInput=export(loaded,batch,"input_export");require(batchInput.columns().equals(List.of("rsid","pval","id"))&&batchInput.rows().size()==input.rows().size(),"Original batch input dimensions differ");
        var groups=new LinkedHashMap<String,List<List<String>>>();String generated=null;
        for(int i=0;i<input.rows().size();i++) {
            var source=input.rows().get(i);var row=batchInput.rows().get(i);String group=row.get(2);
            require(row.get(0).equals(input.cell(source,"SNP")),"Original batch SNP row lineage differs");
            number(row.get(1),intake.pcolumn().isEmpty()?"0.99":input.cell(source,intake.pcolumn()),"batch P value");
            if(input.columns().contains("id.exposure"))require(group.equals(input.cell(source,"id.exposure")),"Original group differs");
            else {if(generated==null)generated=group;require(group.equals(generated)&&group.matches("[A-Za-z0-9]{6}"),"Original generated group is not one six-character source ID");}
            groups.computeIfAbsent(group,key->new ArrayList<>()).add(row);
        }
        var concatenated=new ArrayList<List<String>>();int at=1;long singleton=0;var requiredTables=new HashSet<String>(Set.of("clumped.tsv","decisions.tsv",batchFolder+"/input.tsv"));
        boolean coerced=false,formattingTies=false;
        for(var entry:groups.entrySet()) {
            var rows=entry.getValue();if(rows.size()==1){concatenated.add(rows.get(0));singleton++;continue;}
            require(at<ld.size(),"Missing original local PLINK call");var local=object(ld.get(at),"local call");String folder=String.format(Locale.ROOT,"ld-calls/%03d-ld_clump_local",at+1);at++;
            require("ld_clump_local".equals(local.get("name"))&&entry.getKey().equals(local.get("group_id"))&&integer(local,"plink_exit_status",-1,0,0)==0&&"".equals(local.get("plink_resource_flags")),"Failed/misgrouped PLINK call or resource substitution");objects(worker,local,folder);options(local,r);
            var localInput=export(loaded,local,"input_export");require(localInput.columns().equals(batchInput.columns()),"Local input columns differ");rows(localInput.rows(),rows,"original local group input");requiredTables.add(folder+"/input.tsv");
            var artifacts=artifacts(worker,local,folder);require(artifacts.containsKey("")&&artifacts.containsKey(".log"),"Original PLINK input/log not retained");command(local,binding,r);
            formattingTies|=association(artifacts.get(""),rows);
            var keys=list(local.get("source_parsed_index_keys"),"parsed original index keys");String type=text(local,"source_parsed_index_type");
            if(artifacts.containsKey(".clumped")) {
                List<String> rawKeys=rawKeys(artifacts.get(".clumped"));require(rawKeys.size()==keys.size(),"Raw/parsed index population differs");
                for(int i=0;i<rawKeys.size();i++){String parsed=String.valueOf(keys.get(i));parsedKey(rawKeys.get(i),parsed,type);if(!rawKeys.get(i).equals(parsed))coerced=true;}
            }else require(type.equals("no-clumped-file")&&keys.isEmpty()&&Files.readString(artifacts.get(".log"),StandardCharsets.UTF_8).contains("No significant --clump results"),"No complete original PLINK selection/report");
            var selected=new ArrayList<List<String>>();for(var row:rows)if(keys.contains(row.get(0)))selected.add(row);
            var returned=export(loaded,local,"return_export");require(returned.columns().equals(batchInput.columns()),"Local returned columns differ");rows(returned.rows(),selected,"local source-parsed selection");shape(local,returned);requiredTables.add(folder+"/returned.tsv");concatenated.addAll(selected);
        }
        require(at==ld.size(),"Unexpected original local calls");
        if(!groups.isEmpty()) {
            var returned=export(loaded,batch,"return_export");require(returned.columns().equals(batchInput.columns()),"Batch returned columns differ");rows(returned.rows(),concatenated,"batch concatenation");shape(batch,returned);requiredTables.add(batchFolder+"/returned.tsv");
        }else require(integer(batch,"original_return_rows",-1,0,0)==0&&list(batch.get("original_return_columns"),"empty batch columns").isEmpty()&&!batch.containsKey("return_export"),"Empty source batch shape differs");
        var selected=new ArrayList<List<String>>();var selectedIndices=new ArrayList<String>();var columns=new ArrayList<>(input.columns());
        if(intake.pcolumn().isEmpty())columns.add("pval.exposure");if(!input.columns().contains("id.exposure"))columns.add("id.exposure");
        var pairs=new HashSet<List<String>>();for(var row:concatenated)pairs.add(List.of(row.get(0),row.get(2)));
        var decision=loaded.get("decisions.tsv");require(decision!=null&&decision.columns().equals(List.of("input_row","SNP","source_group","source_pval","source_group_rows","kept","source_rule"))&&decision.rows().size()==input.rows().size(),"Decision ledger differs");
        for(int i=0;i<input.rows().size();i++) {
            var original=input.rows().get(i);var source=batchInput.rows().get(i);boolean keep=pairs.contains(List.of(source.get(0),source.get(2)));int n=groups.get(source.get(2)).size();
            var expected=List.of(Integer.toString(i+1),source.get(0),source.get(2),source.get(1),Integer.toString(n),keep?"TRUE":"FALSE",n==1?"SOURCE_SINGLE_VARIANT_BYPASS":keep?"SOURCE_RETURNED":"SOURCE_NOT_RETURNED");row(decision.rows().get(i),expected,"decision row");
            if(keep){var out=new ArrayList<>(original);if(intake.pcolumn().isEmpty())out.add("0.99");if(!input.columns().contains("id.exposure"))out.add(source.get(2));selected.add(out);selectedIndices.add(Integer.toString(i+1));}
        }
        var clumped=loaded.get("clumped.tsv");require(clumped!=null&&clumped.columns().equals(columns),"Original clumped columns differ");
        tableRows(clumped,selected);require(list(object(metadata.get("clumped.tsv"),"clumped metadata").get("row_names"),"original retained row names").equals(selectedIndices),"Original retained input order/duplicates differ");
        require(metadata.keySet().equals(requiredTables),"Original export population differs");
        var population=object(report.get("population"),"selection population");
        require(integer(population,"input_rows",-1,0,2000000)==input.rows().size()&&integer(population,"retained_rows",-1,0,2000000)==selected.size()&&integer(population,"excluded_rows",-1,0,2000000)==input.rows().size()-selected.size()&&integer(population,"groups",-1,0,2000000)==groups.size()&&integer(population,"single_variant_groups",-1,0,2000000)==singleton,"Selection denominators differ");
        var audit=new LinkedHashMap<String,Object>();audit.put("scope","Original source-return and raw PLINK report projections; no independent LD likelihood refit or full R read.table type-inference reproduction");audit.put("input_rows",input.rows().size());audit.put("retained_rows",selected.size());audit.put("groups",groups.size());audit.put("singleton_bypasses",singleton);audit.put("real_PLINK_calls",ld.size()-1);audit.put("source_index_coercion_observed",coerced);audit.put("reference_population_verified",false);audit.put("no_statistical_estimate_produced",true);
        byte[] bytes=RsemJson.bytes(audit);Path auditFile=root.resolve("audit.json");if(compare)require(Arrays.equals(bytes,Files.readAllBytes(auditFile)),"Semantic audit differs");else Files.write(auditFile,bytes,StandardOpenOption.CREATE_NEW);
        for(String name:List.of("clumped.tsv","decisions.tsv")) {
            Path target=root.resolve("results/"+name);byte[] contents=Files.readAllBytes(worker.resolve(name));if(compare)require(Arrays.equals(contents,Files.readAllBytes(target)),"Public/source export differs");else Files.write(target,contents,StandardOpenOption.CREATE_NEW);
        }
        var warnings=new ArrayList<String>();warnings.add("REFERENCE_POPULATION_DECLARED_ONLY: the supplied BED/BIM/FAM is used intact; the ancestry declaration is not inferred or verified. Review prepared.json and reference provenance before interpreting selection.");
        if(coerced)warnings.add("SOURCE_INDEX_COERCION: original R read.table changed one or more raw PLINK SNP tokens when parsing .clumped. Raw tokens, source-parsed keys and the resulting selection are retained exactly; leading zeros may disappear and matching instruments may be excluded. Inspect every raw plink.clumped and source_parsed_index_keys before proceeding. Retain this source outcome or prepare and document an explicit consistent input/reference identifier mapping in NEW output; no automatic repair is performed.");
        if(formattingTies)warnings.add("SOURCE_FORMAT_CREATED_TIE: original write.table formatting converted distinct numeric P values to equal PLINK input P values. Inspect retained association-input.txt, original values and PLINK logs; no extra precision or tie-breaking rule is substituted.");
        if(singleton>0)warnings.add("SOURCE_SINGLE_VARIANT_BYPASS: "+singleton+" single-row groups bypassed PLINK. Retention does not establish reference membership, significance or LD independence; inspect decisions.tsv before downstream MR.");
        if(selected.isEmpty())warnings.add("COMPLETE_EMPTY_SELECTION: zero instruments were returned. This is a completed filtering outcome, with no downstream MR estimate. Review source logs and identifiers; retain the outcome or retry explicitly revised inputs in NEW output.");
        if(generated!=null)warnings.add("SOURCE_GENERATED_ID: original R generated group "+generated+" using the declared R seed. This seed does not control PLINK's wall-clock seed; retain RNG objects and PLINK logs.");
        if(intake.pcolumn().isEmpty())warnings.add("SOURCE_DEFAULT_PVALUES: explicit opt-in allowed original clump_data to add pval.exposure=0.99. These are source defaults, not measured association P values.");
        if(input.columns().containsAll(List.of("pval.exposure","pval.outcome")))warnings.add("SOURCE_PVALUE_PRECEDENCE: original clump_data used pval.exposure for clumping; pval.outcome remains unused metadata.");
        byte[] warningBytes=(String.join("\n",warnings)+"\n").getBytes(StandardCharsets.UTF_8);Path warningFile=root.resolve("audit-warnings.txt");if(compare)require(Arrays.equals(warningBytes,Files.readAllBytes(warningFile)),"Semantic warning ledger differs");else Files.write(warningFile,warningBytes,StandardOpenOption.CREATE_NEW);
    }
    private static void tableRows(TwoSampleMrWorkflowFiles.Table table,List<List<String>> expected)throws IOException {
        require(table.rows().size()==expected.size(),"Original top-level retained population differs");
        for(int i=0;i<expected.size();i++)for(int j=0;j<table.columns().size();j++) {
            String column=table.columns().get(j),actual=table.rows().get(i).get(j),wanted=expected.get(i).get(j);
            if(TwoSampleMrWorkflowFiles.numeric(column))number(actual,wanted,"retained numeric metadata");else require(actual.equals(wanted),"Original retained metadata/identity differs");
        }
    }
    private static void shape(Map<String,Object> call,TwoSampleMrWorkflowFiles.Table table)throws IOException {require(integer(call,"original_return_rows",-1,0,2000000)==table.rows().size()&&list(call.get("original_return_columns"),"return columns").equals(table.columns()),"Original internal return shape differs");}
    private static TwoSampleMrWorkflowFiles.Table export(Map<String,TwoSampleMrWorkflowFiles.Table> loaded,Map<String,Object> call,String key)throws IOException {String path=text(call,key);var table=loaded.get(path);require(table!=null&&path.equals(text(call,"folder")+"/"+(key.equals("input_export")?"input.tsv":"returned.tsv")),"Original exported table binding differs");return table;}
    private static void options(Map<String,Object> call,Map<String,Object> r)throws IOException {var o=object(call.get("scientific_options"),"original options");require(same(o,Map.of("clump_kb",r.get("clump_kb"),"clump_r2",r.get("clump_r2"),"clump_p",r.get("clump_p1"))),"Original scientific options differ");}
    private static void objects(Path worker,Map<String,Object> call,String folder)throws IOException {
        require(folder.equals(call.get("folder"))&&Boolean.TRUE.equals(call.get("complete"))&&!call.containsKey("error"),"Incomplete/unsafe original call");
        for(String name:List.of("arguments.rds","original-return.rds","rng-before.rds","rng-after.rds"))require(Files.isRegularFile(worker.resolve(folder).resolve(name),LinkOption.NOFOLLOW_LINKS),"Missing complete original object: "+name);
    }
    private static Map<String,Path> artifacts(Path worker,Map<String,Object> call,String folder)throws IOException {
        var result=new HashMap<String,Path>();var snapshots=list(call.get("artifact_snapshots"),"artifact snapshots");require(snapshots.size()==1,"Expected one pre-cleanup raw PLINK snapshot");
        var snapshot=object(snapshots.get(0),"snapshot");require("before-original-unlink".equals(snapshot.get("stage")),"Original cleanup stage differs");String prefix=text(snapshot,"original_prefix");
        for(Object value:list(snapshot.get("files"),"original artifacts")) {
            var file=object(value,"artifact");String suffix=(String)file.get("suffix");require(suffix!=null&&suffix.matches("(?:[.][A-Za-z0-9_.-]+)?"),"Unsafe original artifact suffix");
            String saved=folder+"/artifacts-001/"+(suffix.isEmpty()?"association-input.txt":"plink"+suffix);require(saved.equals(file.get("saved"))&&(prefix+suffix).equals(file.get("original"))&&!result.containsKey(suffix),"Original artifact lineage differs");
            Path path=worker.resolve(saved);require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Files.size(path)==integer(file,"bytes",-1,0,40000000000L),"Original artifact size/type differs");
            String md5;try(var input=Files.newInputStream(path)){try{var digest=MessageDigest.getInstance("MD5");byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);md5=HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
            require(md5.equals(file.get("source_md5_before"))&&md5.equals(file.get("source_md5_after"))&&md5.equals(file.get("saved_md5")),"Raw PLINK artifact changed before/after cleanup copy");result.put(suffix,path);
        }
        return result;
    }
    private static void command(Map<String,Object> call,Map<String,Object> binding,Map<String,Object> r)throws IOException {
        var snapshot=object(list(call.get("artifact_snapshots"),"snapshots").get(0),"snapshot");String prefix=text(snapshot,"original_prefix");
        int split=prefix.lastIndexOf("/worker/owned-tmp/");require(split>0&&!prefix.contains("/../")&&prefix.substring(split).matches("/worker/owned-tmp/Rtmp[A-Za-z0-9]+/file[A-Za-z0-9]+"),"Original PLINK prefix is not an owned temporary file");
        String origin=prefix.substring(0,split),exe=text(binding,"plink_executable").replace('\\','/');
        String start="\""+exe+"\" --bfile \""+origin+"/inputs/reference\" --clump \""+prefix+"\" --clump-p1 ";
        var match=Pattern.compile(Pattern.quote(start)+"([^ ]+) --clump-r2 ([^ ]+) --clump-kb ([^ ]+)"+Pattern.quote(" --out \""+prefix+"\"")).matcher(text(call,"plink_command").replace('\\','/'));
        require(match.matches(),"Original PLINK command/reference/resource flags differ");
        for(int i=1;i<=3;i++) {
            double actual=TwoSampleMrWorkflowFiles.number(match.group(i)),wanted=((Number)r.get(List.of("clump_p1","clump_r2","clump_kb").get(i-1))).doubleValue();
            require(Double.isFinite(actual)&&Math.abs(actual-wanted)<=Math.max(8*Math.ulp(wanted),Math.abs(wanted)*6e-15),"Original PLINK formatted threshold differs");
        }
    }
    private static boolean association(Path path,List<List<String>> rows)throws IOException {
        var lines=Files.readAllLines(path,StandardCharsets.UTF_8);require(lines.size()==rows.size()+1&&lines.get(0).equals("SNP P"),"Raw original PLINK association table differs");
        var encoded=new HashMap<Double,Double>();boolean tied=false;
        for(int i=0;i<rows.size();i++){String[] fields=lines.get(i+1).split(" ",-1);require(fields.length==2&&fields[0].equals(rows.get(i).get(0)),"Raw PLINK association row lineage differs");
            double actual=TwoSampleMrWorkflowFiles.number(fields[1]),wanted=TwoSampleMrWorkflowFiles.number(rows.get(i).get(1));require(Double.isNaN(actual)&&Double.isNaN(wanted)||Double.isFinite(actual)&&Double.isFinite(wanted)&&Math.abs(actual-wanted)<=Math.abs(wanted)*6e-15,"Original write.table P formatting differs");if(Double.isFinite(actual)){Double previous=encoded.putIfAbsent(actual,wanted);if(previous!=null&&previous.doubleValue()!=wanted)tied=true;}}
        return tied;
    }
    private static List<String> rawKeys(Path path)throws IOException {
        var lines=Files.readAllLines(path,StandardCharsets.UTF_8);var keys=new ArrayList<String>();int column=-1;
        for(String line:lines)if(!line.isBlank()){var fields=List.of(line.trim().split("\\s+"));if(column<0){column=fields.indexOf("SNP");require(column>=0,"Missing SNP header in original .clumped");}else {require(fields.size()>column,"Truncated original .clumped");keys.add(fields.get(column));}}
        require(column>=0,"Empty original .clumped");return keys;
    }
    private static void parsedKey(String raw,String parsed,String type)throws IOException {
        switch(type) {
            case "character" -> require(raw.equals(parsed),"Original character index key differs");
            case "integer","double" -> number(raw,parsed,"source numeric index coercion");
            case "logical" -> require(raw.equals(parsed)||(raw.equals("T")&&parsed.equals("TRUE"))||(raw.equals("F")&&parsed.equals("FALSE")),"Original logical index coercion differs");
            default -> throw new IOException("Unsupported original parsed index type: "+type);
        }
    }
    private static void rows(List<List<String>> actual,List<List<String>> expected,String label)throws IOException {require(actual.size()==expected.size(),label+" row population differs");for(int i=0;i<expected.size();i++)row(actual.get(i),expected.get(i),label);}
    private static void row(List<String> actual,List<String> expected,String label)throws IOException {require(actual.size()==expected.size(),label+" width differs");for(int j=0;j<actual.size();j++)if(j==1&&actual.size()==3||j==3&&actual.size()==7)number(actual.get(j),expected.get(j),label);else require(actual.get(j).equals(expected.get(j)),label+" identity/order/selection differs");}
    private static void number(String a,String b,String label)throws IOException {double x=TwoSampleMrWorkflowFiles.number(a),y=TwoSampleMrWorkflowFiles.number(b);require(x==y||Double.isNaN(x)&&Double.isNaN(y),label+" differs");}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
