/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.splicing.RmatsResults;
import static org.jlinalg.cli.NextflowFiles.*;

/** Native post-fit result selection with complete original input and semantic saved verification. */
public final class RmatsResultsCli {
    private RmatsResultsCli() { }
    private static final int MAX_BYTES=67108864;
    private static final int MAX_ROWS=1000000,MAX_LINE=1048576;
    private static final List<String> PAYLOAD=List.of("input.tsv","request.json","selected.tsv","unassessable.tsv","decisions.tsv","summary.json","WARNINGS.md");
    static final String ACTION="Native rMATS statistical-result selection only. This command does not count BAM reads, fit rMATS models or qualify the whole rMATS/nf-core workflow. It retains original field tokens, positional ID columns and NA masks. Raw P must be strictly below --max-p; absolute source delta PSI must be at least --min-abs-dpsi. FDR is retained and never substituted. Missing raw P or delta PSI is UNASSESSABLE, not a negative result. Read summary.json, decisions.tsv and unassessable.tsv before downstream use. Require COMPLETED.json and fresh --verify. SKIP3 (exit3): a named input is missing; restore it or retain the skipped dependent analysis. FAIL2 (exit2): invalid data, capacity, changed bytes or failed output; preserve the directory, correct the cause and retry NEW paths. No partial result is admitted. Saved verification recomputes every derived file from captured input and request; it does not refit biology or authenticate execution history. Hashes establish integrity, not authenticity.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){Path root=null;boolean owned=false;
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));emit(out,"Native rMATS selection and all saved derived bytes verified.\n"+ACTION);return 0;}
            var options=new LinkedHashMap<String,String>();Set<String> allowed=Set.of("--input","--event","--mode","--group1","--group2","--max-p","--min-abs-dpsi","--out");
            for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!allowed.contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique value options; see rmats-results --help");
            if(!options.keySet().equals(allowed))throw new IOException("Declare input, event, JC/JCEC mode, both group replicate counts, both thresholds and NEW output");
            root=Path.of(options.get("--out")).toAbsolutePath().normalize();directory(root.getParent());Files.createDirectory(root);owned=true;
            Path input=Path.of(options.get("--input")).toAbsolutePath().normalize();if(!Files.exists(input))throw new Missing("Original result table is missing: "+input);
            var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("event",options.get("--event"));request.put("mode",options.get("--mode"));request.put("group1",Integer.parseInt(options.get("--group1")));request.put("group2",Integer.parseInt(options.get("--group2")));request.put("max_p",options.get("--max-p"));request.put("min_abs_dpsi",options.get("--min-abs-dpsi"));
            validateRequest(request);writeJson(root.resolve("request.json"),request);Pin before=pin(input,MAX_BYTES,()->{});Files.copy(input,root.resolve("input.tsv"));if(!before.equals(pin(input,MAX_BYTES,()->{}))||!before.equals(pin(root.resolve("input.tsv"),MAX_BYTES,()->{})))throw new IOException("Complete original input changed during capture");
            for(var row:render(root,request).entrySet())Files.write(root.resolve(row.getKey()),row.getValue(),StandardOpenOption.CREATE_NEW);
            var members=new TreeMap<String,Pin>();for(String name:PAYLOAD)members.put(name,pin(root.resolve(name),MAX_BYTES,()->{}));writeJson(root.resolve("manifest.json"),Map.of("schema_version",1,"files",members));writeJson(root.resolve("COMPLETED.json"),Map.of("status","complete-native-rmats-result-selection","manifest",pin(root.resolve("manifest.json"),1048576,()->{}),"whole_tool_reimplemented",false,"whole_paper_reproduced",false));verify(root);
            emit(out,"Native rMATS result selection completed: "+root+"\n"+ACTION);emit(err,"WARNING: "+ACTION);return 0;
        }catch(Exception|LinkageError failure){int code=failure instanceof Missing?3:2;String status=code==3?"SKIP3 - ANALYSIS SKIPPED":"FAIL2 - NO RESULT ADMITTED";emit(err,status+": "+failure+"\n"+ACTION);
            if(owned&&root!=null)try{Files.deleteIfExists(root.resolve("COMPLETED.json"));writeJson(root.resolve("FAILURE.json"),Map.of("status",status,"exit_code",code,"error",failure.toString(),"required_action",ACTION));Files.writeString(root.resolve(Files.exists(root.resolve("WARNINGS.md"))?"WARNINGS_FAILURE.md":"WARNINGS.md"),"# "+status+"\n\n"+failure+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){emit(err,"Diagnostic write failed: "+diagnostic+"; preserve the new directory");}return code;
        }
    }
    private static void validateRequest(Map<?,?> request)throws IOException {
        keys(request,"schema_version","event","mode","group1","group2","max_p","min_abs_dpsi");integer(request.get("schema_version"),1,1);RmatsResults.Event.valueOf(text(request.get("event")));if(!Set.of("JC","JCEC").contains(text(request.get("mode"))))throw new IOException("Declare original JC or JCEC count mode; no inference from identical headers");integer(request.get("group1"),1,1000000);integer(request.get("group2"),1,1000000);threshold(request);
    }
    private static RmatsResults.Threshold threshold(Map<?,?> request)throws IOException {String p=text(request.get("max_p")),psi=text(request.get("min_abs_dpsi"));if(p.length()>128||psi.length()>128||!p.equals(p.strip())||!psi.equals(psi.strip()))throw new IOException("Invalid threshold decimal tokens");return new RmatsResults.Threshold(new BigDecimal(p),new BigDecimal(psi));}
    private static Map<String,byte[]> render(Path root,Map<?,?> request)throws IOException {
        validateRequest(request);var event=RmatsResults.Event.valueOf(text(request.get("event")));var thresholds=threshold(request);RmatsResults.Table table;
        try(var reader=Files.newBufferedReader(root.resolve("input.tsv"),StandardCharsets.UTF_8)){table=RmatsResults.read(reader,event,new RmatsResults.Limits(MAX_ROWS,MAX_LINE,(int)integer(request.get("group1"),1,1000000),(int)integer(request.get("group2"),1,1000000)));}
        var selected=new StringBuilder(table.rawHeader()+"\n");var unresolved=new StringBuilder(table.rawHeader()+"\n");var decisions=new StringBuilder("source_line\tdecision\n");var counts=new TreeMap<String,Long>();for(var decision:RmatsResults.Decision.values())counts.put(decision.name(),0L);
        for(var row:table.rows()){var decision=thresholds.classify(row);counts.merge(decision.name(),1L,Math::addExact);decisions.append(row.sourceLine()).append('\t').append(decision.name()).append('\n');if(decision==RmatsResults.Decision.SELECTED)selected.append(row.rawLine()).append('\n');if(decision==RmatsResults.Decision.UNASSESSABLE)unresolved.append(row.rawLine()).append('\n');}
        var summary=new LinkedHashMap<String,Object>();summary.put("schema_version",1);summary.put("event",event.name());summary.put("mode",text(request.get("mode")));summary.put("rows",table.rows().size());summary.put("decisions",counts);summary.put("source_NA_fields",new TreeMap<>(table.missingFields()));summary.put("original_ID_column_positions_zero_based",table.idPositions());summary.put("raw_p_comparison","strictly_less_than");summary.put("absolute_delta_psi_comparison","greater_than_or_equal");summary.put("FDR_substituted",false);summary.put("whole_tool_reimplemented",false);summary.put("whole_paper_reproduced",false);
        return Map.of("selected.tsv",selected.toString().getBytes(StandardCharsets.UTF_8),"unassessable.tsv",unresolved.toString().getBytes(StandardCharsets.UTF_8),"decisions.tsv",decisions.toString().getBytes(StandardCharsets.UTF_8),"summary.json",(NextflowJson.json(summary)+"\n").getBytes(StandardCharsets.UTF_8),"WARNINGS.md",("# Native rMATS result selection\n\n"+ACTION+"\n\nWARNING: Original NA field values: "+table.missingFields().values().stream().mapToLong(Long::longValue).sum()+"; unassessable selection rows: "+counts.get("UNASSESSABLE")+". Inspect every missingness category before interpretation. Derived TSV files use LF line endings and retain each source field token and row order; input.tsv retains the exact original bytes.\n").getBytes(StandardCharsets.UTF_8));
    }
    static void verify(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();directory(root);var expected=new java.util.HashSet<>(PAYLOAD);expected.add("manifest.json");expected.add("COMPLETED.json");try(var paths=Files.list(root)){var actual=paths.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());if(!actual.equals(expected))throw new IOException("Incomplete/extra/failed native rMATS bundle members");}
        Map<?,?> complete=object(json(root.resolve("COMPLETED.json"),1048576));keys(complete,"status","manifest","whole_tool_reimplemented","whole_paper_reproduced");if(!"complete-native-rmats-result-selection".equals(complete.get("status"))||!Boolean.FALSE.equals(complete.get("whole_tool_reimplemented"))||!Boolean.FALSE.equals(complete.get("whole_paper_reproduced"))||!fingerprint(complete.get("manifest"),1048576).equals(pin(root.resolve("manifest.json"),1048576,()->{})))throw new IOException("Native rMATS completion claim/manifest differs");
        Map<?,?> manifest=object(json(root.resolve("manifest.json"),1048576));keys(manifest,"schema_version","files");integer(manifest.get("schema_version"),1,1);Map<?,?> files=object(manifest.get("files"));if(!files.keySet().equals(Set.copyOf(PAYLOAD)))throw new IOException("Native rMATS payload family differs");for(String name:PAYLOAD)if(!fingerprint(files.get(name),MAX_BYTES).equals(pin(root.resolve(name),MAX_BYTES,()->{})))throw new IOException("Native rMATS captured bytes differ: "+name);
        Map<?,?> request=object(json(root.resolve("request.json"),1048576));for(var row:render(root,request).entrySet())if(!Arrays.equals(row.getValue(),bytes(root.resolve(row.getKey()),MAX_BYTES)))throw new IOException("Native rMATS semantic recomputation differs: "+row.getKey());
    }
    private static final class Missing extends IOException{@java.io.Serial private static final long serialVersionUID=1L;Missing(String message){super(message);}}
    private static void emit(PrintStream stream,String text){stream.println(text);}
    static String help(){return "rmats-results --input ORIGINAL.tsv --event SE|MXE|A3SS|A5SS|RI --mode JC|JCEC --group1 N --group2 N --max-p DECIMAL --min-abs-dpsi DECIMAL --out NEW_DIRECTORY\nrmats-results --verify DIRECTORY\nJava only. Capacities: 64 MiB input/derived file, 1,000,000 rows, 1,048,576 characters/line. Declare actual count mode and replicate counts; mode cannot be inferred from identical headers. Zero-result tables retain their header.\n"+ACTION;}
}
