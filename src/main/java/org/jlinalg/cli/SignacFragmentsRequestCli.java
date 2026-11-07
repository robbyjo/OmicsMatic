/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.SignacFragments;
import org.jlinalg.sequence.SignacFragmentQc;
import org.jlinalg.sequence.SignacMatrixIO;
import static org.jlinalg.cli.FollowupSupport.hash;

/** Native UTF-8 request workflow for fragment counts, feature matrices, nucleosome signal and sparse binarization. */
public final class SignacFragmentsRequestCli {
    private SignacFragmentsRequestCli() { }
    private static final String ACTION="Read WARNINGS.md, operation.json and the captured request/input inventory. Correct the named format, mapping, coordinate, sampling or capacity cause and rerun to a NEW directory. Native fragment operations require Java and the packaged classes; no R installation is needed. NucleosomeSignal is the legacy ratio/ECDF metric, not TSS enrichment. Full Signac integration remains incomplete.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,"SignacFragmentsRequestCli --request ASCII_PATH_TO_UTF8_JSON\nSignacFragmentsRequestCli --verify CAPTURED_DIRECTORY\nSchema1 modes: FEATURE_MATRIX, COUNT_FRAGMENTS, NUCLEOSOME_SIGNAL, BINARIZE_CSC.\nUse UTF-8 JSON paths relative to the request file; keep the request filename itself ASCII on legacy Windows launchers.\nVERIFY requests also accept {schema_version:1, mode:VERIFY, analysis:path}.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]).toAbsolutePath().normalize());emit(out,"Verified captured native Signac fragment calculation: "+args[1]);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_REQUEST.json or --verify CAPTURED_DIR");
            Path requestPath=Path.of(args[1]).toAbsolutePath().normalize();Map<String,Object> request=object(readJson(requestPath));
            if("VERIFY".equals(request.get("mode"))){keys(request,Set.of("schema_version","mode","analysis"));schema(request);verify(resolve(requestPath.getParent(),string(request,"analysis"),false));emit(out,"Verified captured native Signac fragment calculation");return 0;}
            validate(request);Path destination=resolve(requestPath.getParent(),string(request,"out"),false);int[] code={0};
            FollowupSupport.transaction(Map.of("out",destination.toString()),"signac-native-fragments-development",(stage,manifest)->{
                List<SignacFragments.Warning> warnings=new ArrayList<>();
                try{
                    String before=hash(requestPath);Files.copy(requestPath,stage.resolve("source-request.json"));if(!before.equals(hash(stage.resolve("source-request.json")))||!before.equals(hash(requestPath)))throw new IOException("Request changed during capture");
                    Map<String,Object> captured=capture(request,requestPath.getParent(),stage);Files.writeString(stage.resolve("request.json"),NextflowJson.json(captured),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                    execute(captured,stage,new WriteSink(stage),stage.resolve("sort"),warnings);
                    manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");manifest.put("mode",request.get("mode"));manifest.put("source_request_sha256",before);manifest.put("full_Signac_workflow_available",false);
                }catch(Exception error){code[0]=2;manifest.put("analysis_status","failed");manifest.put("error",error.toString());warnings.add(new SignacFragments.Warning("FAIL2_NO_RESULT_ADMITTED",error.toString(),ACTION));}
                String status=Objects.toString(manifest.get("analysis_status"));
                Files.writeString(stage.resolve("WARNINGS.md"),warningText(stage,warnings,code[0]!=0),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                Files.writeString(stage.resolve("analysis-status.json"),NextflowJson.json(Map.of("status",status)),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                Files.writeString(stage.resolve("manifest.json"),NextflowJson.json(manifest),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            });
            seal(destination);if(code[0]==0){verify(destination);emit(out,"Completed native Signac "+request.get("mode")+": "+destination);}else emit(err,"FAIL2 — no successful native result admitted: "+destination);
            emit(err,"WARNINGS / REQUIRED ACTION: "+destination.resolve("WARNINGS.md")+"\n"+ACTION);return code[0];
        }catch(Exception error){emit(err,"FAIL2 — native Signac request or captured verification failed: "+error+"\n"+ACTION);return 2;}
    }
    private static void schema(Map<String,Object> r)throws IOException{if(!(r.get("schema_version") instanceof Number n)||n.doubleValue()!=1)throw new IOException("Require schema_version1");}
    private static void validate(Map<String,Object> r)throws IOException{
        schema(r);String mode=string(r,"mode");Set<String> common=Set.of("schema_version","mode","out");Set<String> modeKeys=switch(mode){
            case "FEATURE_MATRIX"->Set.of("sources","regions","cells","cells_file","keep_all_features","process_n","sort_records","genome_build");
            case "COUNT_FRAGMENTS"->Set.of("files","cells","cells_file","max_lines");
            case "NUCLEOSOME_SIGNAL"->Set.of("sources","object_cells","object_cells_file","max_lines");
            case "BINARIZE_CSC"->Set.of("matrix","features","cells_file");default->throw new IOException("Unknown native fragment mode");};
        Set<String> allowed=new HashSet<>(common);allowed.addAll(modeKeys);keys(r,allowed);string(r,"out");
        if(r.containsKey("cells")&&r.containsKey("cells_file")||r.containsKey("object_cells")&&r.containsKey("object_cells_file"))throw new IOException("Choose an inline or file cell universe, not both");
        if(mode.equals("FEATURE_MATRIX")){string(r,"regions");string(r,"genome_build");integer(r,"process_n",2000,1,Integer.MAX_VALUE);integer(r,"sort_records",100000,1,1000000);bool(r,"keep_all_features",false);}
        if(mode.equals("BINARIZE_CSC")){string(r,"matrix");string(r,"features");string(r,"cells_file");}
        if(mode.equals("COUNT_FRAGMENTS")){if(list(r.get("files")).isEmpty())throw new IOException("Require at least one fragment file");}
        if(mode.equals("NUCLEOSOME_SIGNAL")&&!r.containsKey("object_cells")&&!r.containsKey("object_cells_file"))throw new IOException("Nucleosome signal requires an explicit object cell universe");
        if(mode.equals("FEATURE_MATRIX")||mode.equals("NUCLEOSOME_SIGNAL")){
            List<?> sources=list(r.get("sources"));if(sources.isEmpty())throw new IOException("Require at least one source");
            for(Object item:sources){Map<String,Object> source=object(item);keys(source,Set.of("path","map","map_file"));string(source,"path");if(source.containsKey("map")&&source.containsKey("map_file"))throw new IOException("Choose one map representation");if(source.containsKey("map"))for(Object entry:list(source.get("map"))){Map<String,Object> pair=object(entry);keys(pair,Set.of("cell","barcode"));string(pair,"cell");string(pair,"barcode");}}
        }
        if(r.containsKey("max_lines"))integer(r,"max_lines",0,0,9_007_199_254_740_991L);
    }
    private static Map<String,Object> capture(Map<String,Object> original,Path parent,Path stage)throws IOException{
        Map<String,Object> result=new LinkedHashMap<>(original);result.put("out",".");Files.createDirectory(stage.resolve("input"));Map<Path,String> copied=new LinkedHashMap<>();
        for(String key:List.of("regions","cells_file","object_cells_file","matrix","features"))if(result.containsKey(key))result.put(key,copyInput(string(result,key),parent,stage,copied));
        if(result.containsKey("files")){List<String> files=new ArrayList<>();for(Object file:list(result.get("files")))files.add(copyInput(valueString(file),parent,stage,copied));result.put("files",files);}
        if(result.containsKey("sources")){List<Map<String,Object>> sources=new ArrayList<>();for(Object item:list(result.get("sources"))){Map<String,Object> source=new LinkedHashMap<>(object(item));source.put("path",copyInput(string(source,"path"),parent,stage,copied));if(source.containsKey("map_file"))source.put("map_file",copyInput(string(source,"map_file"),parent,stage,copied));sources.add(source);}result.put("sources",sources);}
        return result;
    }
    private static String copyInput(String value,Path parent,Path stage,Map<Path,String> copied)throws IOException{
        Path source=resolve(parent,value,false);String old=copied.get(source);if(old!=null)return old;if(!Files.isRegularFile(source))throw new IOException("Input unavailable: "+source);
        String relative="input/file-"+copied.size();String before=hash(source);Files.copy(source,stage.resolve(relative));if(!before.equals(hash(stage.resolve(relative)))||!before.equals(hash(source)))throw new IOException("Input changed during capture: "+source);copied.put(source,relative);return relative;
    }
    @FunctionalInterface private interface Line {void add(String line)throws IOException;}
    @FunctionalInterface private interface Table {void write(Line line)throws IOException;}
    private interface Sink {void table(String name,Table table)throws IOException;}
    private record WriteSink(Path root)implements Sink{public void table(String name,Table table)throws IOException{try(var out=Files.newBufferedWriter(root.resolve(name),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){table.write(line->{out.write(line);out.write('\n');});}}}
    private record CheckSink(Path root)implements Sink{public void table(String name,Table table)throws IOException{try(var in=Files.newBufferedReader(root.resolve(name),StandardCharsets.UTF_8)){table.write(expected->{if(!expected.equals(in.readLine()))throw new IOException("Recomputed native result differs: "+name);});if(in.readLine()!=null)throw new IOException("Extra result rows: "+name);}}}
    private static void execute(Map<String,Object> r,Path root,Sink sink,Path scratch,List<SignacFragments.Warning> warnings)throws IOException{
        validate(r);String mode=string(r,"mode");var defaults=SignacFragments.Limits.defaults();List<SignacFragments.Scan> scans=List.of();long maxLines=0;
        if(mode.equals("COUNT_FRAGMENTS")){
            List<Path> files=new ArrayList<>();for(Object value:list(r.get("files")))files.add(resolve(root,valueString(value),true));maxLines=integer(r,"max_lines",0,0,9_007_199_254_740_991L);
            var result=SignacFragments.countFragments(files,cells(r,"cells","cells_file",root,false),maxLines,defaults,()->false);scans=result.scans();warnings.addAll(result.warnings());
            sink.table("counts.tsv",line->{line.add("CB\tfrequency_count\tmononucleosomal\tnucleosome_free\treads_count");for(var row:result.counts())line.add(row.barcode()+"\t"+row.frequencyCount()+"\t"+row.mononucleosomal()+"\t"+row.nucleosomeFree()+"\t"+row.readsCount());});
        }else if(mode.equals("FEATURE_MATRIX")){
            int memory=(int)integer(r,"sort_records",100000,1,1000000);var limits=new SignacFragments.Limits(defaults.features(),defaults.cells(),defaults.nonzeroValues(),defaults.records(),defaults.contributions(),defaults.decodedBytes(),defaults.lineBytes(),memory);
            var matrix=SignacFragments.featureMatrix(sources(r,root),regions(resolve(root,string(r,"regions"),true)),cells(r,"cells","cells_file",root,false),bool(r,"keep_all_features",false),(int)integer(r,"process_n",2000,1,Integer.MAX_VALUE),scratch,limits,()->false);
            scans=matrix.scans();warnings.addAll(matrix.warnings());writeMatrix(matrix,sink);
        }else if(mode.equals("NUCLEOSOME_SIGNAL")){
            List<String> cells=cells(r,"object_cells","object_cells_file",root,true);maxLines=integer(r,"max_lines",Math.multiplyExact(cells.size(),5000L),0,9_007_199_254_740_991L);
            var result=SignacFragmentQc.nucleosomeSignal(sources(r,root),cells,maxLines,defaults,()->false);scans=result.scans();warnings.addAll(result.warnings());
            sink.table("nucleosome.tsv",line->{line.add("cell\tnucleosome_signal\tratio_kind\tnucleosome_percentile\tpercentile_kind");for(var row:result.scores())line.add(row.cell()+"\t"+number(row.ratio())+"\t"+row.ratio().kind()+"\t"+number(row.percentile())+"\t"+row.percentile().kind());});
        }else{
            var input=SignacMatrixIO.readCounts(resolve(root,string(r,"matrix"),true),resolve(root,string(r,"features"),true),resolve(root,string(r,"cells_file"),true),SignacMatrixIO.Limits.defaults(),()->false);
            var binary=SignacFragmentQc.binarizeCsc(input.counts(),()->false);warnings.addAll(binary.warnings());var counts=binary.counts();double[] x=counts.values();long[] values=new long[x.length];Arrays.fill(values,1);
            writeMatrix(new SignacFragments.Matrix(counts.features(),counts.cells(),counts.columnPointers(),counts.rowIndices(),values,List.of(),List.of()),sink);
        }
        Map<String,Object> operation=new LinkedHashMap<>();operation.put("mode",mode);operation.put("profile","native-valid-input-contracts-validated-against-original-Signac-1.17.1");operation.put("genome_build",r.get("genome_build"));operation.put("max_lines_per_file",maxLines);
        operation.put("keep_all_features",r.getOrDefault("keep_all_features",false));operation.put("process_n",r.getOrDefault("process_n",2000));
        List<Map<String,Object>> survey=new ArrayList<>();for(var scan:scans){Map<String,Object> s=new LinkedHashMap<>();s.put("records",scan.records());s.put("decoded_bytes_consumed",scan.decodedBytes());s.put("compressed",scan.compressed());s.put("prefix_only",scan.prefixOnly());s.put("chromosomes_seen",scan.chromosomes());survey.add(s);}operation.put("source_scans",survey);operation.put("warning_codes",warnings.stream().map(SignacFragments.Warning::code).toList());
        sink.table("operation.json",line->line.add(NextflowJson.json(operation)));
    }
    private static void writeMatrix(SignacFragments.Matrix matrix,Sink sink)throws IOException{
        int[] p=matrix.pointers(),rows=matrix.rows();long[] values=matrix.values();sink.table("matrix.mtx",line->{line.add("%%MatrixMarket matrix coordinate integer general");line.add(matrix.features().size()+" "+matrix.cells().size()+" "+values.length);for(int cell=0;cell<matrix.cells().size();cell++)for(int k=p[cell];k<p[cell+1];k++)line.add((rows[k]+1)+" "+(cell+1)+" "+values[k]);});
        sink.table("features.tsv",line->{for(String id:matrix.features())line.add(id);});sink.table("cells.tsv",line->{for(String id:matrix.cells())line.add(id);});
    }
    private static List<SignacFragments.Source> sources(Map<String,Object> r,Path root)throws IOException{
        List<SignacFragments.Source> sources=new ArrayList<>();for(Object item:list(r.get("sources"))){Map<String,Object> source=object(item);List<SignacFragments.Cell> map=null;
            if(source.containsKey("map_file")){map=new ArrayList<>();for(String[] row:rows(resolve(root,string(source,"map_file"),true),"cell\tbarcode",2))map.add(new SignacFragments.Cell(row[0],row[1]));}
            else if(source.containsKey("map")){map=new ArrayList<>();for(Object entry:list(source.get("map"))){var pair=object(entry);map.add(new SignacFragments.Cell(string(pair,"cell"),string(pair,"barcode")));}}
            sources.add(new SignacFragments.Source(resolve(root,string(source,"path"),true),map));}return sources;
    }
    private static List<SignacFragments.Region> regions(Path path)throws IOException{List<SignacFragments.Region> regions=new ArrayList<>();for(String[] row:rows(path,"chromosome\tstart\tend",3))regions.add(new SignacFragments.Region(row[0],Integer.parseInt(row[1]),Integer.parseInt(row[2])));return regions;}
    private static List<String> cells(Map<String,Object> r,String inline,String file,Path root,boolean required)throws IOException{
        if(r.containsKey(file)){List<String> values=new ArrayList<>();try(var in=Files.newBufferedReader(resolve(root,string(r,file),true),StandardCharsets.UTF_8)){String line;while((line=in.readLine())!=null){if(values.size()>=2_000_000||line.length()>16384)throw new IOException("Cell file exceeds the workflow bound");values.add(line);}}return values;}
        if(r.containsKey(inline)){List<String> values=new ArrayList<>();for(Object value:list(r.get(inline)))values.add(valueString(value));return values;}
        if(required)throw new IOException("Cell universe is required");return null;
    }
    private static List<String[]> rows(Path path,String header,int columns)throws IOException{if(Files.size(path)>1_000_000_000L)throw new IOException("Excessive region/map file");List<String[]> rows=new ArrayList<>();try(var in=Files.newBufferedReader(path,StandardCharsets.UTF_8)){if(!header.equals(in.readLine()))throw new IOException("Expected header: "+header);String line;while((line=in.readLine())!=null){if(line.length()>16384||rows.size()>=2_000_000)throw new IOException("Excessive region/map rows or line");String[] fields=line.split("\t",-1);if(fields.length!=columns)throw new IOException("Ragged region/map table");rows.add(fields);}}return rows;}
    private static String number(SignacFragmentQc.Value value){return switch(value.kind()){case FINITE->Double.toString(value.value());case POSITIVE_INFINITY->"Inf";case NAN->"NaN";case NA->"NA";};}
    private static String warningText(Path root,List<SignacFragments.Warning> warnings,boolean failed){return "# "+(failed?"FAIL2 — NATIVE FRAGMENT ANALYSIS FAILED":"NATIVE SIGNAC FRAGMENT WARNINGS")+"\n\n"+warnings.stream().map(w->w.code()+": "+w.message().replace(root.toString(),"CAPTURED_DIRECTORY").replace('\\','/')+"\n\nRequired action: "+w.requiredAction()).collect(java.util.stream.Collectors.joining("\n\n"))+"\n\nScope: fragment quantification, counts, legacy nucleosome signal or sparse binarization only. No TSS enrichment, gene activity, motifs, links or full Signac workflow is implied.\n\n"+ACTION+"\n";}
    private static Object readJson(Path path)throws IOException{if(!Files.isRegularFile(path)||Files.size(path)>1_000_000)throw new IOException("Request unavailable or exceeds one million bytes");return SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));}
    private static Map<String,Object> object(Object value)throws IOException{if(!(value instanceof Map<?,?> map))throw new IOException("Expected JSON object");Map<String,Object> result=new LinkedHashMap<>();for(var entry:map.entrySet()){if(!(entry.getKey() instanceof String key))throw new IOException("Non-string JSON key");result.put(key,entry.getValue());}return result;}
    private static List<?> list(Object value)throws IOException{if(!(value instanceof List<?> list))throw new IOException("Expected JSON array");return list;}
    private static String valueString(Object value)throws IOException{if(!(value instanceof String s)||s.isBlank())throw new IOException("Expected nonempty string");return s;}
    private static String string(Map<String,Object> r,String key)throws IOException{return valueString(r.get(key));}
    private static void keys(Map<String,Object> r,Set<String> allowed)throws IOException{for(String key:r.keySet())if(!allowed.contains(key))throw new IOException("Unknown request field: "+key);}
    private static boolean bool(Map<String,Object> r,String key,boolean fallback)throws IOException{Object value=r.get(key);if(value==null)return fallback;if(!(value instanceof Boolean b))throw new IOException(key+" must be boolean");return b;}
    private static long integer(Map<String,Object> r,String key,long fallback,long min,long max)throws IOException{Object value=r.get(key);if(value==null)return fallback;if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IOException(key+" must be an integer within bounds");return n.longValue();}
    private static Path resolve(Path parent,String value,boolean captured)throws IOException{Path path=parent.resolve(Path.of(value)).normalize().toAbsolutePath();if(captured&&(!path.startsWith(parent)||Files.isSymbolicLink(path)))throw new IOException("Captured input escapes its bundle");return path;}
    private static void seal(Path root)throws IOException{try(var paths=Files.walk(root);var out=Files.newBufferedWriter(root.resolve("artifact-checksums.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){out.write("sha256\tpath\n");for(Path p:paths.filter(Files::isRegularFile).filter(p->!p.equals(root.resolve("artifact-checksums.tsv"))).sorted().toList())out.write(hash(p)+"\t"+root.relativize(p).toString().replace('\\','/')+"\n");}}
    private static void checkCapture(Map<String,Object> original,Map<String,Object> captured)throws IOException{
        validate(original);Map<String,Object> expected=new LinkedHashMap<>(original);expected.put("out",".");
        for(String key:List.of("regions","cells_file","object_cells_file","matrix","features"))if(original.containsKey(key)){capturedInput(captured.get(key));expected.put(key,captured.get(key));}
        if(original.containsKey("files")){List<?> old=list(original.get("files")),now=list(captured.get("files"));if(old.size()!=now.size())throw new IOException("Changed captured file count");for(Object value:now)capturedInput(value);expected.put("files",now);}
        if(original.containsKey("sources")){List<?> old=list(original.get("sources")),now=list(captured.get("sources"));if(old.size()!=now.size())throw new IOException("Changed captured source count");List<Map<String,Object>> values=new ArrayList<>();for(int i=0;i<old.size();i++){Map<String,Object> source=new LinkedHashMap<>(object(old.get(i))),copy=object(now.get(i));capturedInput(copy.get("path"));source.put("path",copy.get("path"));if(source.containsKey("map_file")){capturedInput(copy.get("map_file"));source.put("map_file",copy.get("map_file"));}values.add(source);}expected.put("sources",values);}
        if(!expected.equals(captured))throw new IOException("Captured request options differ from the original request");
    }
    private static void capturedInput(Object value)throws IOException{if(!valueString(value).matches("input/file-[0-9]+"))throw new IOException("Noncanonical captured input path");}
    private static void verify(Path root)throws IOException{
        Set<String> seen=new HashSet<>();List<String> lines=Files.readAllLines(root.resolve("artifact-checksums.tsv"),StandardCharsets.UTF_8);if(lines.isEmpty()||!lines.get(0).equals("sha256\tpath"))throw new IOException("Missing artifact inventory");
        for(String line:lines.subList(1,lines.size())){String[] f=line.split("\t",-1);if(f.length!=2||!f[0].matches("[0-9a-f]{64}")||f[1].contains(":")||f[1].contains("\\")||Arrays.stream(f[1].split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw new IOException("Unsafe artifact inventory");Path p=root.resolve(f[1]);if(!seen.add(f[1])||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!hash(p).equals(f[0]))throw new IOException("Artifact integrity mismatch: "+f[1]);}
        try(var paths=Files.walk(root)){for(Path p:paths.toList()){if(Files.isSymbolicLink(p))throw new IOException("Captured symbolic links forbidden");if(Files.isRegularFile(p)&&!p.equals(root.resolve("artifact-checksums.tsv"))&&!seen.contains(root.relativize(p).toString().replace('\\','/')))throw new IOException("Uninventoried artifact");}}
        Map<String,Object> manifest=object(readJson(root.resolve("manifest.json"))),status=object(readJson(root.resolve("analysis-status.json")));if(!Set.of("completed","completed-with-warnings").contains(manifest.get("analysis_status"))||!manifest.get("analysis_status").equals(status.get("status")))throw new IOException("Captured operation did not complete");
        Map<String,Object> request=object(readJson(root.resolve("request.json")));checkCapture(object(readJson(root.resolve("source-request.json"))),request);
        if(!Objects.equals(manifest.get("mode"),request.get("mode"))||!Boolean.FALSE.equals(manifest.get("full_Signac_workflow_available"))||!Objects.equals(manifest.get("source_request_sha256"),hash(root.resolve("source-request.json"))))throw new IOException("Captured manifest request binding differs");
        Path temp=Files.createTempDirectory("omicsmatic-signac-fragment-verify-");List<SignacFragments.Warning> warnings=new ArrayList<>();try{execute(request,root,new CheckSink(root),temp.resolve("sort"),warnings);}finally{Files.delete(temp);}
        if(!Objects.equals(manifest.get("analysis_status"),warnings.isEmpty()?"completed":"completed-with-warnings"))throw new IOException("Captured warning status differs from recomputation");
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warningText(root,warnings,false)))throw new IOException("Captured warnings or required actions differ from recomputation");
    }
    private static void emit(PrintStream stream,String message){stream.writeBytes((message+"\n").getBytes(StandardCharsets.UTF_8));}
}
