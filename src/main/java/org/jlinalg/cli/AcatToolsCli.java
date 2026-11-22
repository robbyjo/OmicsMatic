/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.settest.Acat091;

/** Local native ACAT0.91 combination captures with complete recomputation and replay. */
public final class AcatToolsCli {
    private static final int REQUEST_BYTES=16_777_216;
    private static final String RESOURCE="/org/jlinalg/omics/acat091/";
    private static final String SOURCE_PROFILE_SHA256="901662bbc6ac4eb8f0ee4f9709c680c656cdc155970fdef8e5038a70dc2b3075";
    private static final String SCOPE="Native ACAT0.91 checked matrix p-value combination only. Each column is one declared combination;no genes,models,weights or missing values are inferred. Original R is not executed. ACAT-V/null-model fitting,ACAT-O,SKAT,genotype preparation,FDR and signed Z reconstruction are separate operations. Source warnings and raw nonfinite/overflow returns remain retained without silent repair or an engine fallback.";
    private static final String ACTION="SKIP3: DEPENDENT ANALYSIS SKIPPED; supply the named permitted Java/runtime or packaged source resource,or retain the skip. Download/install where permitted;if prohibited,identify the affected analysis and exact prerequisite. FAIL2: NO COMPLETE RESULT;preserve raw requests,diagnostics and failed/partial captures,resolve the named input,numerical,capacity or integrity cause and retry NEW output. Read analysis-status.json and WARNINGS.md. Do not replace0/1,drop zero-weight rows,repair NaN/Inf or substitute a stable mathematical method silently. Source numeric zero does not imply infinite biological significance. Hashes establish integrity,not producer authentication.";
    private AcatToolsCli(){ }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}

    public static int run(String[] args,PrintStream output,PrintStream error) {
        try {
            if(args.length==1&&args[0].equals("--help")){output.println("acat-tools --request JSON | --verify CAPTURE\nSchema1 modes:RUN,VERIFY,REPLAY. RUN requires engine=native,profile=0.91,operation=COMBINE,out,p_values(row-major matrix);optional same-shape weights,column_ids and limits.maximum_cells(default1000000,max10000000),maximum_columns(default10000,max25000). Column IDs preserve literal text and allow1-128 UTF-16 units without controls or unpaired surrogates.\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));output.println("PASS native ACAT0.91 capture bytes,source/classes and complete recomputation;no independent R execution or producer authentication.");return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request JSON,--verify CAPTURE or --help");
            Path path=Path.of(args[1]).toAbsolutePath().normalize().toRealPath();byte[] bytes=readBytes(path,REQUEST_BYTES);Map<String,Object> request=object(SimpleJson.parse(utf8(bytes)));String mode=text(request,"mode");
            if(mode.equals("VERIFY")){keys(request,Set.of("schema_version","mode","analysis"));schema(request);verify(resolve(path.getParent(),text(request,"analysis")));output.println("PASS complete saved native ACAT capture recomputation. "+SCOPE);return 0;}
            if(mode.equals("REPLAY")) {
                keys(request,Set.of("schema_version","mode","analysis","out"));schema(request);Path parent=resolve(path.getParent(),text(request,"analysis")).toRealPath(),destination=resolvedOutput(resolve(path.getParent(),text(request,"out")));
                if(destination.startsWith(parent)||parent.startsWith(destination))throw new IOException("Replay output must be outside its parent capture");verify(parent);String seal=hash(Files.readAllBytes(parent.resolve("capture-files.json")));
                Map<String,Object> replay=object(SimpleJson.parse(utf8(readBytes(parent.resolve("source-request.json"),REQUEST_BYTES))));replay=new LinkedHashMap<>(replay);replay.put("out",destination.toString());
                int code=execute(path.getParent(),replay,RsemJson.bytes(replay),map("analysis",parent.toString(),"seal_sha256",seal),output,error);
                verify(parent);if(!seal.equals(hash(Files.readAllBytes(parent.resolve("capture-files.json")))))throw new IOException("Replay changed its parent capture");return code;
            }
            if(!mode.equals("RUN"))throw new IOException("Unknown ACAT request mode");return execute(path.getParent(),request,bytes,Map.of(),output,error);
        }catch(FileNotFoundException missing){error.println("SKIP3 - DEPENDENT ANALYSIS SKIPPED: "+missing.getMessage()+"\n"+ACTION);return 3;}
        catch(OutOfMemoryError exhausted){error.println("FAIL2 - NO COMPLETE RESULT: Java heap exhausted;preserve the partial capture and retry NEW output with explicit heap/cell limits.\n"+ACTION);return 2;}
        catch(Exception failure){error.println("FAIL2 - NO COMPLETE RESULT: "+failure+"\n"+ACTION);return 2;}
    }

    private static int execute(Path directory,Map<String,Object> request,byte[] raw,Map<String,Object> replay,PrintStream output,PrintStream error)throws Exception {
        Path root=newOutput(resolve(directory,text(request,"out")));json(root.resolve("analysis-status.json"),map("status","RUNNING_NO_RESULT_ADMITTED","result_admitted",false));
        try {
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve("WARNINGS.initial.md"),provisional(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);if(!replay.isEmpty())json(root.resolve("replay-source.json"),replay);
            source(root,false);json(root.resolve("implementation-classes.json"),classes());Map<String,Object> evaluated=evaluate(request);json(root.resolve("native-result.json"),evaluated);
            boolean admitted=Boolean.TRUE.equals(evaluated.get("result_admitted"));Files.writeString(root.resolve("WARNINGS.md"),warnings(evaluated),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            if(admitted)Files.writeString(root.resolve("p-values.tsv"),table(evaluated),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            json(root.resolve("analysis-status.json"),status(evaluated));seal(root);
            if(admitted){output.println("Completed native ACAT0.91 combination. Read "+root.resolve("WARNINGS.md")+". "+SCOPE);return 0;}
            error.println("FAIL2 - NO COMPLETE RESULT: "+evaluated.get("error")+"\nRead "+root.resolve("WARNINGS.md")+"\n"+ACTION);return 2;
        }catch(Exception failure){json(root.resolve("analysis-status.json"),map("status",failure instanceof FileNotFoundException?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT","result_admitted",false,"error",failure.toString(),"original_R_executed",false));Files.writeString(root.resolve("WARNINGS.failure.md"),SCOPE+"\n\n"+failure+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);seal(root);throw failure;}
    }

    private static Map<String,Object> evaluate(Map<String,Object> request) {
        var sourceWarnings=new ArrayList<String>();var admissionWarnings=new ArrayList<String>();var columns=new ArrayList<Object>();String failure=null;int rows=0,count=0;
        try {
            keys(request,Set.of("schema_version","mode","engine","profile","operation","out","p_values","weights","column_ids","limits"));schema(request);
            if(!text(request,"mode").equals("RUN")||!text(request,"engine").equals("native")||!text(request,"profile").equals("0.91")||!text(request,"operation").equals("COMBINE"))throw new IOException("Require RUN,engine native,profile0.91 and operation COMBINE");
            int cap=1_000_000,columnCap=10_000;if(request.containsKey("limits")){var limits=object(request.get("limits"));keys(limits,Set.of("maximum_cells","maximum_columns"));cap=integer(limits,"maximum_cells",cap,1,10_000_000);columnCap=integer(limits,"maximum_columns",columnCap,1,25_000);}
            double[][] values=matrix(request.get("p_values"),cap,columnCap),weights=request.containsKey("weights")?matrix(request.get("weights"),cap,columnCap):null;rows=values.length;count=values[0].length;List<?> ids=request.containsKey("column_ids")?list(request.get("column_ids")):null;
            if(ids!=null){if(ids.size()!=count)throw new IOException("column_ids must match the complete matrix columns");var unique=new java.util.HashSet<String>();for(Object item:ids){if(!(item instanceof String id)||id.isBlank()||id.length()>128||id.codePoints().anyMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff)||!unique.add(id))throw new IOException("Column IDs must be unique literal Unicode text,1-128 UTF-16 units,without control/unpaired-surrogate characters;no trimming or repair is performed");}}
            var result=Acat091.combineMatrix(values,weights);sourceWarnings.addAll(result.sourceWarnings());
            for(int j=0;j<count;j++){
                var column=result.columns().get(j);var row=map("column_index",j+1,"column_id",ids==null?null:ids.get(j),"native_statistic",Double.toString(column.statistic()),"raw_source_profile_p",Double.toString(column.rawPValue()),"result_admitted",column.admitted(),"admission_issue",column.admissionIssue());columns.add(row);
                if(column.admissionIssue()!=null)admissionWarnings.add("Column"+(j+1)+": "+column.admissionIssue());
                else if(column.rawPValue()==0)admissionWarnings.add("Column"+(j+1)+": source numeric zero retained;input zeros or source floating-point behavior do not establish an infinitely precise probability");
            }
            if(!result.admitted())failure="At least one source-profile column is numerically unadmitted;the complete batch is withheld. Raw per-column diagnostics remain retained.";
        }catch(Acat091.Failure refused){sourceWarnings.addAll(refused.sourceWarnings());failure=refused.getMessage();}
        catch(Exception refused){failure=refused.toString();}
        return map("schema_version",1,"engine","native","profile","0.91","operation","COMBINE","rows",rows,"column_count",count,"columns",columns,"source_warnings",sourceWarnings,"admission_warnings",admissionWarnings,"result_admitted",failure==null,"error",failure,"original_R_executed",false,"scope",SCOPE);
    }

    private static void verify(Path capture)throws Exception {
        Path root=capture.toAbsolutePath().normalize().toRealPath();Map<String,Object> expected=object(SimpleJson.parse(utf8(readBytes(root.resolve("capture-files.json"),4_194_304))));
        if(!RsemJson.json(expected).equals(RsemJson.json(inventory(root))))throw new IOException("Changed/missing/extra capture files");source(root,true);
        if(!RsemJson.json(classes()).equals(utf8(Files.readAllBytes(root.resolve("implementation-classes.json"))).trim()))throw new IOException("Captured native implementation classes differ from this verifier");
        Map<String,Object> request=object(SimpleJson.parse(utf8(readBytes(root.resolve("source-request.json"),REQUEST_BYTES))));var actual=evaluate(request);
        if(!Arrays.equals(RsemJson.bytes(actual),readBytes(root.resolve("native-result.json"),REQUEST_BYTES)))throw new IOException("Complete native recomputation differs from captured diagnostics");
        boolean admitted=Boolean.TRUE.equals(actual.get("result_admitted"));var status=object(SimpleJson.parse(utf8(Files.readAllBytes(root.resolve("analysis-status.json")))));
        if(!RsemJson.json(status(actual)).equals(RsemJson.json(status)))throw new IOException("Completion/admission state contradicts recomputed native outcome");
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings(actual))||!Files.readString(root.resolve("WARNINGS.initial.md"),StandardCharsets.UTF_8).equals(provisional()))throw new IOException("Changed warning/recovery text");
        if(admitted){if(!Files.readString(root.resolve("p-values.tsv"),StandardCharsets.UTF_8).equals(table(actual)))throw new IOException("Complete exported p-values differ");}
        else if(Files.exists(root.resolve("p-values.tsv")))throw new IOException("Unadmitted batch carries a result export");
    }

    private static String warnings(Map<String,Object> evaluated){return "# Warnings and required actions\n\n"+SCOPE+"\n\n"+ACTION+"\n\nSource warnings: "+evaluated.get("source_warnings")+"\n\nAdmission warnings: "+evaluated.get("admission_warnings")+"\n\nResult admitted: "+evaluated.get("result_admitted")+"\n\nError: "+evaluated.get("error")+"\n";}
    private static String provisional(){return "# PROVISIONAL: NO RESULT ADMITTED\n\nRead final analysis-status.json,capture-files.json and WARNINGS.md before using any export.\n\n"+SCOPE+"\n\n"+ACTION+"\n";}
    private static Map<String,Object> status(Map<String,Object> evaluated)throws IOException {boolean admitted=Boolean.TRUE.equals(evaluated.get("result_admitted"));String state=admitted?(list(evaluated.get("source_warnings")).isEmpty()&&list(evaluated.get("admission_warnings")).isEmpty()?"COMPLETED":"COMPLETED_WITH_WARNINGS"):"FAIL2_NO_COMPLETE_RESULT";return map("schema_version",1,"status",state,"result_admitted",admitted,"original_R_executed",false,"profile","0.91","operation","COMBINE");}
    private static String table(Map<String,Object> evaluated)throws IOException {var text=new StringBuilder("column_index\tcolumn_id\tp_value\n");for(Object item:list(evaluated.get("columns"))){var row=object(item);text.append(row.get("column_index")).append('\t').append(row.get("column_id")==null?"":row.get("column_id")).append('\t').append(row.get("raw_source_profile_p")).append('\n');}return text.toString();}
    private static double[][] matrix(Object input,int capacity,int columnCapacity)throws IOException {List<?> rows=list(input);if(rows.isEmpty())throw new IOException("Require a nonempty matrix");int width=list(rows.get(0)).size();if(width==0||width>columnCapacity||(long)rows.size()*width>capacity)throw new IOException("Declared matrix cell/column capacity exceeded or empty columns");double[][] result=new double[rows.size()][width];for(int i=0;i<result.length;i++){var row=list(rows.get(i));if(row.size()!=width)throw new IOException("Require rectangular row-major matrices");for(int j=0;j<width;j++){Object value=row.get(j);if(!(value instanceof Number number)||!Double.isFinite(number.doubleValue()))throw new IOException("Require finite numeric matrix entries;no parsing or repair of NaN/Inf strings");result[i][j]=number.doubleValue();}}return result;}
    private static void source(Path root,boolean compare)throws Exception {byte[] descriptor=resource("SOURCE.json");if(!hash(descriptor).equals(SOURCE_PROFILE_SHA256))throw new IOException("Changed packaged source descriptor");Map<String,Object> profile=object(SimpleJson.parse(utf8(descriptor)));if(!profile.get("edition").equals("0.91")||!profile.get("commit").equals(Acat091.SOURCE_COMMIT))throw new IOException("Different packaged ACAT source edition");Map<String,Object> files=object(profile.get("files"));if(!files.keySet().equals(Set.of("R/ACAT.R","DESCRIPTION","NAMESPACE")))throw new IOException("Different source file population");for(String name:List.of("R/ACAT.R","DESCRIPTION","NAMESPACE","SOURCE.json")){byte[] bytes=resource(name);if(!name.equals("SOURCE.json")&&!RsemJson.json(pin(bytes)).equals(RsemJson.json(files.get(name))))throw new IOException("Changed packaged reference source");if(name.equals("R/ACAT.R")&&!hash(bytes).equals(Acat091.SOURCE_SHA256))throw new IOException("Changed selected original ACAT function source");Path target=root.resolve("source").resolve(name);if(compare){if(!Arrays.equals(bytes,Files.readAllBytes(target)))throw new IOException("Changed captured source: "+name);}else{Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);}}}
    private static Map<String,Object> classes()throws Exception {var result=new TreeMap<String,Object>();for(String name:List.of("org/jlinalg/settest/Acat091","org/jlinalg/settest/Acat091$Column","org/jlinalg/settest/Acat091$Result","org/jlinalg/settest/Acat091$Failure","org/jlinalg/settest/RLongDoubleSum","org/jlinalg/cli/AcatToolsCli","org/jlinalg/cli/SimpleJson","org/jlinalg/cli/RsemJson","org/jlinalg/cli/RegenieRuntime")){try(var stream=AcatToolsCli.class.getResourceAsStream("/"+name+".class")){if(stream==null)throw new FileNotFoundException("Missing qualified Java class: "+name);result.put(name+".class",pin(stream.readAllBytes()));}}return result;}
    private static byte[] resource(String name)throws IOException {try(var stream=AcatToolsCli.class.getResourceAsStream(RESOURCE+name)){if(stream==null)throw new FileNotFoundException("Supply a complete source-built JAR;missing "+RESOURCE+name);return stream.readAllBytes();}}
    private static Path newOutput(Path output)throws IOException {Path normalized=output.toAbsolutePath().normalize();if(Files.exists(normalized,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists;preserve it and choose NEW output");Path parent=normalized.getParent();if(parent==null)throw new IOException("Invalid output root");Files.createDirectories(parent);return Files.createDirectory(parent.toRealPath().resolve(normalized.getFileName()));}
    private static Map<String,Object> inventory(Path root)throws Exception {var result=new TreeMap<String,Object>();try(var paths=Files.walk(root)){for(Path path:paths.toList()){if(Files.isSymbolicLink(path))throw new IOException("Capture contains a symbolic link");if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&!path.equals(root.resolve("capture-files.json"))){if(Files.size(path)>REQUEST_BYTES)throw new IOException("Capture member exceeds fixed byte capacity");result.put(root.relativize(path).toString().replace('\\','/'),pin(Files.readAllBytes(path)));}}}return result;}
    private static void seal(Path root)throws Exception {json(root.resolve("capture-files.json"),inventory(root));}
    private static Map<String,Object> pin(byte[] bytes)throws Exception {return map("bytes",bytes.length,"sha256",hash(bytes));}
    private static String hash(byte[] bytes)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] readBytes(Path path,int maximum)throws IOException {if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new FileNotFoundException("Supply the exact permitted regular file: "+path);if(Files.size(path)>maximum)throw new IOException("Input byte capacity exceeded: "+path);byte[] bytes=Files.readAllBytes(path);if(bytes.length>maximum)throw new IOException("Input grew beyond byte capacity");return bytes;}
    private static String utf8(byte[] bytes)throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
    private static Path resolve(Path parent,String path){Path value=Path.of(path);return(value.isAbsolute()?value:parent.resolve(value)).toAbsolutePath().normalize();}
    private static Path resolvedOutput(Path candidate)throws IOException {Path cursor=candidate.toAbsolutePath().normalize();var missing=new ArrayList<Path>();while(!Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)){if(cursor.getFileName()==null||cursor.getParent()==null)throw new IOException("Cannot resolve output ancestor");missing.add(cursor.getFileName());cursor=cursor.getParent();}Path result=cursor.toRealPath();for(int i=missing.size()-1;i>=0;i--)result=result.resolve(missing.get(i));return result.normalize();}
    private static void json(Path path,Object value)throws IOException {Files.write(path,RsemJson.bytes(value));}
    private static Map<String,Object> map(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put(pairs[i].toString(),pairs[i+1]);return result;}
    private static Map<String,Object> object(Object value)throws IOException {if(!(value instanceof Map<?,?> input))throw new IOException("Require JSON object");var result=new LinkedHashMap<String,Object>();for(var entry:input.entrySet()){if(!(entry.getKey() instanceof String key))throw new IOException("Require string JSON keys");result.put(key,entry.getValue());}return result;}
    private static List<?> list(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Require JSON array");return list;}
    private static String text(Map<String,Object> value,String key)throws IOException {if(!(value.get(key) instanceof String text)||text.isBlank())throw new IOException("Require literal nonblank "+key);return text;}
    private static void keys(Map<String,Object> values,Set<String> allowed)throws IOException {for(String key:values.keySet())if(!allowed.contains(key))throw new IOException("Unknown request field: "+key);}
    private static int integer(Map<String,Object> value,String key,int fallback,int minimum,int maximum)throws IOException {if(!value.containsKey(key))return fallback;if(!(value.get(key) instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()!=Math.rint(number.doubleValue())||number.doubleValue()<minimum||number.doubleValue()>maximum)throw new IOException("Invalid bounded integer: "+key);return number.intValue();}
    private static void schema(Map<String,Object> value)throws IOException {if(integer(value,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version1");}
}
