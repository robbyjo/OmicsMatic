/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jlinalg.multipletesting.WeightedBenjaminiHochberg;
import org.jlinalg.spatial.RegressionMoran;

/** Complete prepared-response families with explicit design, weights and residual Moran inference. */
public final class RegressionMoranCli {
    private RegressionMoranCli() { }
    private static final int MAX_FILE_BYTES=64*1024*1024;
    private static final long MAX_TOTAL_BYTES=128L*1024*1024;
    private static final long MAX_MATRIX_ENTRIES=16_000_000;
    private static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final String SCOPE="Native Java unweighted linear-model residual Moran tests on a complete caller-prepared expression population, explicit numeric design (including any intercept/factor contrasts), and positive directed graph weights. U=(W+W')/2; weights are not row-normalized. zero_policy explicitly selects original spdep effective-N behavior without dropping model rows. Normal-reference p-values and complete-family BH are reported. No R runtime, graph learning, response transformation, covariate encoding or missing-value removal is implicit. This primitive does not establish the complete paper-specific Monocle1.0.0 pipeline.";
    private static final String ACTION="SKIP3: dependent analysis skipped for missing or forbidden input/output access; provide permitted accessible paths and retry NEW output. Windows launchers can lose non-ASCII command-line characters: use an ASCII request pathname and declare Unicode input/output paths inside UTF-8 JSON. FAIL2: no complete testing family admitted; preserve raw inputs and partial files, read FAILURE.txt and WARNINGS.md, correct the named input, rank, variance, capacity or integrity cause and retry NEW output. Never drop failed genes/cells, repair identifiers or replace nonfinite values to force success. Require exit 0, COMPLETED.json and analysis.json with result_admitted=true. Numeric-zero probabilities are floating-point limits; retain log probabilities and read NUMERIC-WARNING.txt.";
    private record Capture(Path source,Path copy,NextflowFiles.Pin pin) { }
    private record Design(List<String> cells,List<String> columns,double[][] values,Map<String,Integer> indices) { }
    private record Responses(List<String> genes,List<double[]> values) { }

    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path owned=null;
        try {
            if(args.length==1&&args[0].equals("--help")) {
                output.println("monocle-tools MORAN-LM --request UTF8_JSON\nRequired fields: schema_version=1, design, weights, expression, response_units=prepared-linear-response, response_provenance, population_declaration, zero_policy boolean, alternative=greater|less|two.sided, out NEW. Optional rank_tolerance (default1e-7), maximum_operations (default500000000). Paths resolve relative to the request.\nDesign TSV: cell_id followed by explicit numeric design-column names. Weights TSV: from_cell<TAB>to_cell<TAB>weight. Expression TSV: gene_id followed by the complete design cell axis in the same order; one finite prepared response per cell. Input TSV is literal/unquoted UTF-8, optional BOM recorded. No duplicate, missing or nonfinite fields.\nLimits: 64MiB/file,128MiB total input,1000000 cells,128 design columns,16000000 entries per matrix,2000000 directed arcs,100000 genes; complete declared operation bound at most2000000000. These are refusal bounds, not OS quotas.\n"+SCOPE+"\n"+ACTION);
                output.flush();return output.checkError()?2:0;
            }
            require(args.length==2&&args[0].equals("--request"),"Use MORAN-LM --request JSON or --help");
            Path request=regular(Path.of(args[1]));var requestPin=NextflowFiles.pin(request,1048576,()->{});
            byte[] raw=NextflowFiles.bytes(request,1048576);require(requestPin.equals(new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw))),"Request changed during capture");
            var m=NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000));
            Set<String> allowed=Set.of("schema_version","design","weights","expression","response_units","response_provenance","population_declaration","zero_policy","alternative","out","rank_tolerance","maximum_operations");
            require(m.keySet().stream().allMatch(allowed::contains),"Unknown request field; no executable model/formula strings accepted");
            NextflowFiles.integer(m.get("schema_version"),1,1);
            require(NextflowFiles.text(m.get("response_units")).equals("prepared-linear-response"),"Declare response_units=prepared-linear-response; no count/log transformation is inferred");
            String provenance=label(NextflowFiles.text(m.get("response_provenance"))),population=label(NextflowFiles.text(m.get("population_declaration")));
            require(m.get("zero_policy") instanceof Boolean,"Declare zero_policy explicitly");boolean zeroPolicy=(Boolean)m.get("zero_policy");
            String alternative=NextflowFiles.text(m.get("alternative"));
            RegressionMoran.Alternative alt=switch(alternative) {case "greater"->RegressionMoran.Alternative.GREATER;case "less"->RegressionMoran.Alternative.LESS;case "two.sided"->RegressionMoran.Alternative.TWO_SIDED;default->throw new IOException("Declare greater, less or two.sided alternative");};
            double tolerance=m.containsKey("rank_tolerance")?finite(m.get("rank_tolerance")):1e-7;
            long operations=m.containsKey("maximum_operations")?NextflowFiles.integer(m.get("maximum_operations"),1,2_000_000_000L):RegressionMoran.DEFAULT_MAXIMUM_OPERATIONS;
            var inputs=new LinkedHashMap<String,Path>();
            for(String name:List.of("design","weights","expression"))inputs.put(name,regular(resolve(request.getParent(),m.get(name))));
            var protectedPaths=new ArrayList<>(inputs.values());protectedPaths.add(request);
            Path destination=destination(resolve(request.getParent(),m.get("out")),protectedPaths);
            Files.createDirectories(destination.getParent());Files.createDirectory(destination);owned=destination;
            write(owned.resolve("WARNINGS.md"),"# Model scope and required actions\n\n"+SCOPE+"\n\n"+ACTION+"\n");
            Files.write(owned.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);Path captured=Files.createDirectory(owned.resolve("inputs"));
            var captures=new ArrayList<Capture>();var files=new LinkedHashMap<String,Path>();var boms=new LinkedHashMap<String,Boolean>();long total=0;
            for(var entry:inputs.entrySet()) {
                var before=NextflowFiles.pin(entry.getValue(),MAX_FILE_BYTES,()->{});total=Math.addExact(total,before.bytes());require(total<=MAX_TOTAL_BYTES,"Complete input population exceeds128MiB; no prefix admitted");
                byte[] bytes=NextflowFiles.bytes(entry.getValue(),MAX_FILE_BYTES);require(before.equals(new NextflowFiles.Pin(bytes.length,NextflowFiles.sha(bytes))),"Input changed during capture: "+entry.getKey());
                Path copy=captured.resolve(entry.getKey()+".tsv");Files.write(copy,bytes,StandardOpenOption.CREATE_NEW);captures.add(new Capture(entry.getValue(),copy,before));files.put(entry.getKey(),copy);boms.put(entry.getKey(),bytes.length>=3&&(bytes[0]&255)==239&&(bytes[1]&255)==187&&(bytes[2]&255)==191);
            }
            Design design=design(files.get("design"));List<RegressionMoran.Arc> weights=weights(files.get("weights"),design);
            RegressionMoran.Prepared prepared=RegressionMoran.prepare(design.values,weights,zeroPolicy,tolerance,operations);
            Responses responses=responses(files.get("expression"),design.cells);
            long completeWork=Math.addExact(prepared.preparationOperations(),Math.multiplyExact(prepared.operationsPerResponse(),responses.genes.size()));
            require(completeWork<=operations,"Complete response family exceeds declared operation limit; no partial gene family admitted");
            var results=new ArrayList<RegressionMoran.Result>();boolean numericZero=false;
            for(int i=0;i<responses.genes.size();i++) {
                try {var r=prepared.test(responses.values.get(i),alt);results.add(r);numericZero|=r.pValue()==0;}
                catch(IllegalArgumentException|ArithmeticException failure){throw new IOException("Gene "+responses.genes.get(i)+": "+failure.getMessage()+"; entire testing family refused",failure);}
            }
            double[] q=WeightedBenjaminiHochberg.adjust(results.stream().mapToDouble(RegressionMoran.Result::pValue).toArray());
            StringBuilder table=new StringBuilder("gene_id\tmorans_I\texpectation\tvariance\tz\tp_value\tlog_p_value\tq_value\tresponse_scale\tscaled_residual_norm\n");var rows=new ArrayList<Map<String,Object>>();
            for(int i=0;i<results.size();i++) {
                var r=results.get(i);String id=responses.genes.get(i);Object log=Double.isFinite(r.logPValue())?r.logPValue():"-Inf";
                table.append(quoted(id)).append('\t').append(r.moranI()).append('\t').append(r.expectation()).append('\t').append(r.variance()).append('\t').append(r.statistic()).append('\t').append(r.pValue()).append('\t').append(log).append('\t').append(q[i]).append('\t').append(r.responseScale()).append('\t').append(r.scaledResidualNorm()).append('\n');
                var row=new LinkedHashMap<String,Object>();row.put("gene_id",id);row.put("morans_I",r.moranI());row.put("expectation",r.expectation());row.put("variance",r.variance());row.put("z",r.statistic());row.put("p_value",r.pValue());row.put("log_p_value",log);row.put("q_value",q[i]);row.put("response_scale",r.responseScale());row.put("scaled_residual_norm",r.scaledResidualNorm());rows.add(row);
            }
            write(owned.resolve("tests.tsv"),table.toString());NextflowFiles.writeJson(owned.resolve("tests.json"),rows);
            var report=new LinkedHashMap<String,Object>();report.put("profile","native-regression-residual-moran-v1");report.put("result_admitted",true);report.put("observations",prepared.observations());report.put("effective_observations",prepared.effectiveObservations());report.put("original_outgoing_isolates",prepared.outgoingIsolates());report.put("model_rank",prepared.rank());report.put("model_residual_df",prepared.observations()-prepared.rank());report.put("test_reference_df",prepared.effectiveObservations()-prepared.rank());report.put("design_columns",design.columns);report.put("cell_order",design.cells);report.put("genes",results.size());report.put("directed_arcs",prepared.directedArcs());report.put("symmetric_edges",prepared.symmetricEdges());report.put("weight_sum",prepared.weightSum());report.put("weight_semantics","U=(W+W')/2; no row normalization");report.put("zero_policy",zeroPolicy);report.put("alternative",alternative);report.put("rank_tolerance",tolerance);report.put("response_units","prepared-linear-response");report.put("response_provenance",provenance);report.put("population_declaration",population);report.put("maximum_operations",operations);report.put("complete_operation_upper_bound",completeWork);report.put("multiple_testing","BH over the complete declared gene family");report.put("utf8_bom_present",boms);report.put("numerical_zero_probability_observed",numericZero);report.put("whole_tool_qualified",false);report.put("whole_goal_complete",false);
            NextflowFiles.writeJson(owned.resolve("analysis.json"),report);
            if(numericZero)write(owned.resolve("NUMERIC-WARNING.txt"),"Some p/q values reach zero at floating-point resolution. Zero is not an exactly zero mathematical probability. Preserve log_p_value, z and the full model/graph metadata. A -Inf log token explicitly records an unrepresentable log tail; it is not repaired.\n");
            for(Capture c:captures) {
                require(c.pin.equals(NextflowFiles.pin(c.source,MAX_FILE_BYTES,()->{})),"Source input changed during analysis; original captured bytes retained");
                require(c.pin.equals(NextflowFiles.pin(c.copy,MAX_FILE_BYTES,()->{})),"Captured input changed during analysis; no result admitted");
            }
            require(requestPin.equals(NextflowFiles.pin(request,1048576,()->{})),"Request changed during analysis");
            require(requestPin.equals(NextflowFiles.pin(owned.resolve("source-request.json"),1048576,()->{})),"Captured request changed during analysis");
            var manifest=new LinkedHashMap<String,Object>();
            try(var all=Files.walk(owned)) {for(Path path:all.filter(Files::isRegularFile).sorted().toList())manifest.put(owned.relativize(path).toString().replace('\\','/'),NextflowFiles.pin(path,256L*1024*1024,()->{}));}
            NextflowFiles.writeJson(owned.resolve("manifest.json"),manifest);
            output.println("PASS native residual Moran: "+results.size()+" genes; "+prepared.observations()+" model rows; effective N="+prepared.effectiveObservations()+". Read "+owned.resolve("WARNINGS.md")+(numericZero?" and NUMERIC-WARNING.txt":""));output.flush();error.flush();
            require(!output.checkError()&&!error.checkError(),"Console recording failed; no complete family");
            NextflowFiles.writeJson(owned.resolve("COMPLETED.json"),Map.of("status","PASS_NATIVE_REGRESSION_RESIDUAL_MORAN","profile","native-regression-residual-moran-v1","genes",results.size(),"manifest",NextflowFiles.pin(owned.resolve("manifest.json"),1048576,()->{})));
            return 0;
        } catch(Exception failure) {return failed(owned,failure,error);}
        catch(OutOfMemoryError failure) {return failed(owned,new IOException("Java heap exhausted; no partial family admitted. Preserve inputs, choose suitable declared capacities/resources and retry NEW output",failure),error);}
    }

    private static int failed(Path owned,Exception failure,PrintStream error) {
        int code=failure instanceof NoSuchFileException||failure instanceof AccessDeniedException||failure instanceof SecurityException?3:2;
        String message=(code==3?"SKIP3: dependent analysis skipped. ":"FAIL2: no complete result admitted. ")+failure+"\n"+ACTION+"\n";
        if(owned!=null)try {
            Files.deleteIfExists(owned.resolve("COMPLETED.json"));
            if(Files.exists(owned.resolve("analysis.json")))Files.move(owned.resolve("analysis.json"),owned.resolve("unadmitted-analysis.json"));
            NextflowFiles.writeJson(owned.resolve("analysis.json"),Map.of("result_admitted",false,"status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED","cause",failure.toString(),"required_action",ACTION));
            write(owned.resolve("FAILURE.txt"),message);
        }catch(IOException recording){message+="Failure recording also failed: "+recording+"\n";}
        error.print(message);error.flush();return code;
    }
    private static Design design(Path path)throws IOException {
        List<String> lines=lines(path);require(!lines.isEmpty(),"Empty design");String[] header=fields(lines.get(0));require(header.length>=2&&header.length<=129&&header[0].equals("cell_id"),"Design header must be cell_id then1..128 explicit numeric columns");
        var columns=List.copyOf(Arrays.asList(header).subList(1,header.length));var names=new HashSet<String>();for(String name:header)require(names.add(label(name)),"Duplicate design-column name");
        require(lines.size()>=4&&lines.size()<=1_000_001&&(long)(lines.size()-1)*columns.size()<=MAX_MATRIX_ENTRIES,"Complete design dimensions exceed supported limits");
        var cells=new ArrayList<String>();var indices=new LinkedHashMap<String,Integer>();double[][] values=new double[lines.size()-1][columns.size()];
        for(int i=1;i<lines.size();i++) {String[] f=fields(lines.get(i));require(f.length==header.length,"Design column population differs at row "+i);String id=label(f[0]);require(indices.putIfAbsent(id,cells.size())==null,"Duplicate cell ID");cells.add(id);for(int j=1;j<f.length;j++)values[i-1][j-1]=decimal(f[j]);}
        return new Design(List.copyOf(cells),columns,values,Map.copyOf(indices));
    }
    private static List<RegressionMoran.Arc> weights(Path path,Design design)throws IOException {
        List<String> lines=lines(path);require(!lines.isEmpty()&&lines.get(0).equals("from_cell\tto_cell\tweight"),"Require exact from_cell<TAB>to_cell<TAB>weight header");require(lines.size()>=2&&lines.size()<=2_000_001,"Complete directed-arc population exceeds limits");var result=new ArrayList<RegressionMoran.Arc>();
        for(int i=1;i<lines.size();i++) {String[] f=fields(lines.get(i));require(f.length==3,"Require three literal weight fields");Integer from=design.indices.get(f[0]),to=design.indices.get(f[1]);require(from!=null&&to!=null,"Graph cell ID is absent from the complete design axis");result.add(new RegressionMoran.Arc(from,to,decimal(f[2])));}
        return result;
    }
    private static Responses responses(Path path,List<String> cells)throws IOException {
        List<String> lines=lines(path);require(!lines.isEmpty(),"Empty expression population");String[] header=fields(lines.get(0));require(header.length==cells.size()+1&&header[0].equals("gene_id")&&Arrays.asList(header).subList(1,header.length).equals(cells),"Expression cell axis differs from complete design order; no reorder/subset is inferred");
        require(lines.size()>=2&&lines.size()<=100_001&&(long)(lines.size()-1)*cells.size()<=MAX_MATRIX_ENTRIES,"Complete response population exceeds limits");var ids=new ArrayList<String>();var values=new ArrayList<double[]>();var seen=new HashSet<String>();
        for(int i=1;i<lines.size();i++) {String[] f=fields(lines.get(i));require(f.length==header.length,"Expression row population differs at gene row "+i);String id=label(f[0]);require(seen.add(id),"Duplicate gene ID");ids.add(id);double[] row=new double[cells.size()];for(int j=1;j<f.length;j++)row[j-1]=decimal(f[j]);values.add(row);}
        return new Responses(List.copyOf(ids),List.copyOf(values));
    }
    private static List<String> lines(Path path)throws IOException {String text=NextflowFiles.utf8(NextflowFiles.bytes(path,MAX_FILE_BYTES));if(text.startsWith("\ufeff"))text=text.substring(1);return text.lines().toList();}
    private static String[] fields(String line)throws IOException {require(!line.isEmpty(),"Empty rows are refused, not dropped");return line.split("\t",-1);}
    private static double decimal(String value)throws IOException {require(value.length()<=256&&DECIMAL.matcher(value).matches(),"Require finite explicit decimal values; missing values are not removed");double d=Double.parseDouble(value);require(Double.isFinite(d),"Nonfinite decimal value");return d;}
    private static double finite(Object value)throws IOException {String s=value instanceof NextflowParameters.NumberToken token?token.text():value instanceof Number?value.toString():"";return decimal(s);}
    private static String label(String value)throws IOException {require(!value.isBlank()&&value.length()<=32768&&value.chars().noneMatch(c->c<32||c==127),"Require nonblank literal labels without control characters");return value;}
    private static String quoted(String value) {return "\""+value.replace("\"","\"\"")+"\"";}
    private static Path resolve(Path parent,Object value)throws IOException {Path p=Path.of(label(NextflowFiles.text(value)));return(p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    private static Path regular(Path path)throws IOException {Path p=path.toAbsolutePath().normalize();if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(p.toString());require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Require a regular unlinked input file");return p.toRealPath();}
    private static Path destination(Path absolute,List<Path> inputs)throws IOException {require(!Files.exists(absolute,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and select NEW output");Path parent=absolute.getParent();require(parent!=null,"Output needs a parent directory");while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();Path resolved=parent.toRealPath().resolve(parent.relativize(absolute)).normalize();for(Path input:inputs)require(!resolved.startsWith(input)&&!input.startsWith(resolved),"Output overlaps a protected input");return resolved;}
    private static void write(Path path,String text)throws IOException {Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void require(boolean condition,String cause)throws IOException {if(!condition)throw new IOException(cause);}
}
