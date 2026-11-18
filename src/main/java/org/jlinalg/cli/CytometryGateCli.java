/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.cytometry.FcsGatingInput;
import org.jlinalg.cytometry.FcsReader;
import org.jlinalg.cytometry.GatingMl20;

/** Complete native FCS-to-Gating-ML populations, with explicit policies and preserved source captures. */
public final class CytometryGateCli {
    private CytometryGateCli() { }
    private static final String PROFILE="native-fcs-gatingml20-v1";
    private static final String SCOPE="Native Java complete single-dataset FCS stored channels, explicit amplifier-to-scale interpretation and Gating-ML 2.0 gates. No gate learning, proprietary FlowJo/FACSDiva workspace import, vendor equivalence, calibration, time-in-seconds conversion or paper-specific settings are inferred. Negative results are retained; undefined/nonfinite events refuse the complete requested population. The historical ISAC test archive contains unresolved reference contradictions; this profile does not claim full standard-suite compliance.";
    private static final String ACTION="Require exit 0, COMPLETED.json and analysis.json result_admitted=true before use. SKIP3: dependent analysis skipped because a required format/capability or permitted file access is unavailable. Supply a supported complete FCS/Gating-ML export and permitted paths, then retry NEW output. FAIL2: no complete result admitted. Preserve original inputs and partial output; read FAILURE.txt and WARNINGS.md, correct the named definition, numerical domain, metadata, integrity or capacity problem and retry NEW output. Never drop events, repair identifiers, clip negatives, replace nonfinite values or alter original gates to force success. Windows launchers may lose Unicode arguments: use an ASCII request pathname with Unicode paths inside UTF-8 JSON.";
    private record Capture(Path source,Path copy,NextflowFiles.Pin pin,long maximum) { }
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path owned=null;
        try {
            if(args.length==1&&args[0].equals("--help")) {
                output.println("cytometry-tools GATE --request UTF8_JSON\nRequired: schema_version=1, fcs, gating_ml, out NEW, legacy_charset, text_policy=EXACT|ALLOW_TRAILING_SPACE_PADDING, numeric_policy=EXACT|ALLOW_ASCII_SPACE_PADDING, compensation=AUTO|NONE|$SPILLOVER|SPILL, legacy_zero_log_minimum_as_one Boolean, svd_relative_cutoff number in [0,1), population_declaration and gating_provenance. All paths resolve against the request. No external runtime/install needed.\nOptional maximum_input_bytes: default1GiB/max64GiB; maximum_events: default10000000/max100000000; maximum_membership_bytes: default512MiB/max2GiB; maximum_event_gate_pairs: default250000000/max2000000000. Other fixed refusal bounds: FCS512 channels/16MiB TEXT, Gating-ML16MiB/4096 definitions/128 dimensions/100000 polygon vertices. Bounds admit the whole population or refuse it; they are not OS quotas or CPU-time guarantees.\nOutputs: complete event-by-gate memberships.tsv (one-based source event order), counts.tsv, captured inputs, explicit policies/diagnostics, WARNINGS.md, checksummed manifest and completion marker. No descriptive intensity statistics are supplied.\n"+SCOPE+"\n"+ACTION);
                output.flush();return output.checkError()?2:0;
            }
            require(args.length==3&&args[0].equals("GATE")&&args[1].equals("--request"),"Use cytometry-tools GATE --request JSON or --help");
            Path request=regular(Path.of(args[2]));var requestPin=NextflowFiles.pin(request,1048576,()->{});byte[] raw=NextflowFiles.bytes(request,1048576);
            require(requestPin.equals(new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw))),"Request changed during capture");
            var m=NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000));
            Set<String> allowed=Set.of("schema_version","fcs","gating_ml","out","legacy_charset","text_policy","numeric_policy","compensation","legacy_zero_log_minimum_as_one","svd_relative_cutoff","population_declaration","gating_provenance","maximum_input_bytes","maximum_events","maximum_membership_bytes","maximum_event_gate_pairs");
            require(m.keySet().stream().allMatch(allowed::contains),"Unknown request field; no inferred gate/parameter settings");NextflowFiles.integer(m.get("schema_version"),1,1);
            Charset charset=Charset.forName(NextflowFiles.text(m.get("legacy_charset")));var policy=FcsReader.TextPolicy.valueOf(NextflowFiles.text(m.get("text_policy")));
            var numericPolicy=FcsReader.NumericPolicy.valueOf(NextflowFiles.text(m.get("numeric_policy")));
            String selected=NextflowFiles.text(m.get("compensation"));require(Set.of("AUTO","NONE","$SPILLOVER","SPILL").contains(selected),"Declare AUTO, NONE, $SPILLOVER or SPILL compensation");
            require(m.get("legacy_zero_log_minimum_as_one") instanceof Boolean,"Declare the legacy zero log-minimum policy explicitly");boolean legacy=(Boolean)m.get("legacy_zero_log_minimum_as_one");
            double cutoff=number(m.get("svd_relative_cutoff"));require(cutoff>=0&&cutoff<1,"Declare svd_relative_cutoff in [0,1)");
            String population=label(NextflowFiles.text(m.get("population_declaration"))),provenance=label(NextflowFiles.text(m.get("gating_provenance")));
            long maximumInput=limit(m,"maximum_input_bytes",1L<<30,58,64L<<30),maximumEvents=limit(m,"maximum_events",10000000,0,100000000),maximumOutput=limit(m,"maximum_membership_bytes",512L<<20,1,2L<<30),maximumPairs=limit(m,"maximum_event_gate_pairs",250000000,1,2000000000);
            Path source=regular(resolve(request.getParent(),m.get("fcs"))),gates=regular(resolve(request.getParent(),m.get("gating_ml")));
            Path dest=destination(resolve(request.getParent(),m.get("out")),List.of(source,gates,request));Files.createDirectories(dest.getParent());Files.createDirectory(dest);owned=dest;
            write(owned.resolve("WARNINGS.md"),"# Scope, warnings and required actions\n\n"+SCOPE+"\n\n"+ACTION+"\n");Files.write(owned.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            Path captured=Files.createDirectory(owned.resolve("inputs"));var captures=new ArrayList<Capture>();
            captures.add(capture(source,captured.resolve("source.fcs"),maximumInput));captures.add(capture(gates,captured.resolve("gates.xml"),16L<<20));
            var warnings=new ArrayList<String>();var report=new LinkedHashMap<String,Object>();
            try(var reader=FcsReader.open(captured.resolve("source.fcs"),new FcsReader.Limits(maximumInput,maximumEvents,512,16<<20),charset,policy,numericPolicy)) {
                var bridge=FcsGatingInput.prepare(reader,selected,legacy,cutoff);warnings.addAll(reader.info().warnings());warnings.addAll(bridge.warnings());
                // Record input warnings before model binding, including any later SKIP3/FAIL2.
                appendWarnings(owned,warnings);
                var program=GatingMl20.read(captured.resolve("gates.xml"),bridge.names(),bridge.compensation(),cutoff,GatingMl20.Limits.defaults());
                appendWarnings(owned,program.warnings());warnings.addAll(program.warnings());require(!program.gates().isEmpty(),"No population gates in complete definition");
                long events=reader.info().events();int n=program.gates().size();require(Math.multiplyExact(events,(long)n)<=maximumPairs,"Complete event/gate population exceeds declared pair capacity; no prefix admitted");
                String header="event_index\t"+String.join("\t",program.gates().stream().map(g->quoted(g.id())).toList())+"\n";
                long upper=Math.addExact(header.getBytes(StandardCharsets.UTF_8).length,Math.multiplyExact(events,22L+2L*n));require(upper<=maximumOutput,"Complete membership byte upper bound exceeds declared capacity; no truncated result");
                long[] counts=new long[n];boolean[] inside=new boolean[n];double[] stored=new double[bridge.names().size()];var evaluator=program.evaluator();long event=0;
                try(var writer=Files.newBufferedWriter(owned.resolve("memberships.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                    writer.write(header);
                    while(reader.readEvent(stored)) {
                        try {evaluator.evaluate(bridge.scale(stored),inside);}
                        catch(IllegalArgumentException|ArithmeticException failure) {throw new IOException("Source event "+(event+1)+": "+failure.getMessage()+"; complete population refused",failure);}
                        writer.write(Long.toString(++event));
                        for(int i=0;i<n;i++) {writer.write(inside[i]?"\t1":"\t0");if(inside[i])counts[i]++;}writer.write('\n');
                    }
                }
                require(reader.summary().eventsRead()==events&&event==events,"Incomplete original event population");
                var indices=new LinkedHashMap<String,Integer>();for(int i=0;i<n;i++)indices.put(program.gates().get(i).id(),i);
                var table=new StringBuilder("gate_id\tparent_id\tcount\ttotal_events\tparent_events\tpercent_total\tpercent_parent\n");
                for(int i=0;i<n;i++) {var g=program.gates().get(i);long parent=g.parent()==null?events:counts[indices.get(g.parent())];table.append(quoted(g.id())).append('\t').append(quoted(g.parent()==null?"":g.parent())).append('\t').append(counts[i]).append('\t').append(events).append('\t').append(parent).append('\t').append(events==0?"":Double.toString(100.0*counts[i]/events)).append('\t').append(parent==0?"":Double.toString(100.0*counts[i]/parent)).append('\n');}
                write(owned.resolve("counts.tsv"),table.toString());
                report.put("profile",PROFILE);report.put("result_admitted",true);report.put("events",events);report.put("gates",program.gates());report.put("counts",Arrays.stream(counts).boxed().toList());report.put("source_parameter_order",bridge.names());report.put("source_fcs_version",reader.info().version());report.put("source_data_type",reader.info().dataType());report.put("legacy_charset",charset.name());report.put("text_policy",policy.name());report.put("compensation_selection",selected);report.put("selected_compensation_keyword",bridge.compensationKeyword());report.put("legacy_zero_log_minimum_as_one",legacy);report.put("svd_relative_cutoff",cutoff);report.put("used_compensation_diagnostics",program.compensationDiagnostics());report.put("population_declaration",population);report.put("gating_provenance",provenance);report.put("warnings",warnings);report.put("limits",Map.of("maximum_input_bytes",maximumInput,"maximum_events",maximumEvents,"maximum_membership_bytes",maximumOutput,"maximum_event_gate_pairs",maximumPairs));report.put("integer_values_with_unused_bits",reader.summary().integerValuesWithUnusedBits());report.put("whole_vendor_tool_qualified",false);report.put("full_standard_suite_passed",false);report.put("whole_goal_complete",false);
            }
            for(Capture c:captures) {require(c.pin.equals(NextflowFiles.pin(c.source,c.maximum,()->{})),"Source changed during analysis; captured bytes retained");require(c.pin.equals(NextflowFiles.pin(c.copy,c.maximum,()->{})),"Captured input changed during analysis");}
            require(requestPin.equals(NextflowFiles.pin(request,1048576,()->{}))&&requestPin.equals(NextflowFiles.pin(owned.resolve("source-request.json"),1048576,()->{})),"Request changed during analysis");
            var gateRows=new ArrayList<Map<String,Object>>();
            for(Object item:(List<?>)report.get("gates")) {var g=(GatingMl20.Gate)item;var row=new LinkedHashMap<String,Object>();row.put("id",g.id());row.put("parent",g.parent());row.put("type",g.type());gateRows.add(row);}report.put("gates",gateRows);
            var diagnosticRows=new ArrayList<Map<String,Object>>();
            for(Object item:(List<?>)report.get("used_compensation_diagnostics")) {var d=(org.jlinalg.cytometry.CytometryCompensation.Diagnostics)item;diagnosticRows.add(Map.of("method",d.method(),"rank",d.rank(),"fluorochromes",d.fluorochromes(),"detectors",d.detectors(),"relative_cutoff",d.relativeCutoff(),"singular_values",d.singularValues()));}report.put("used_compensation_diagnostics",diagnosticRows);
            report.put("numeric_policy",numericPolicy.name());
            NextflowFiles.writeJson(owned.resolve("analysis.json"),report);var manifest=new LinkedHashMap<String,Object>();
            try(var all=Files.walk(owned)) {for(Path path:all.filter(Files::isRegularFile).sorted().toList())manifest.put(owned.relativize(path).toString().replace('\\','/'),NextflowFiles.pin(path,Math.max(16L<<20,Math.max(maximumInput,maximumOutput)),()->{}));}
            NextflowFiles.writeJson(owned.resolve("manifest.json"),manifest);
            output.println("PASS native cytometry: "+report.get("events")+" complete events; "+((List<?>)report.get("gates")).size()+" gates. READ "+owned.resolve("WARNINGS.md"));output.flush();error.flush();require(!output.checkError()&&!error.checkError(),"Console recording failed");
            NextflowFiles.writeJson(owned.resolve("COMPLETED.json"),Map.of("status","PASS_NATIVE_COMPLETE_FCS_GATING","profile",PROFILE,"manifest",NextflowFiles.pin(owned.resolve("manifest.json"),1048576,()->{})));return 0;
        } catch(Exception failure) {return failed(owned,failure,error);}
        catch(OutOfMemoryError failure) {return failed(owned,new IOException("Java heap exhausted; complete population refused. Preserve inputs and retry NEW output with suitable resources/capacities",failure),error);}
    }
    private static Capture capture(Path source,Path copy,long maximum)throws IOException {
        var before=NextflowFiles.pin(source,maximum,()->{});long count=0;
        try(var input=Files.newInputStream(source);var output=Files.newOutputStream(copy,StandardOpenOption.CREATE_NEW)) {byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))>=0;) {count=Math.addExact(count,n);require(count<=before.bytes()&&count<=maximum,"Input grew during bounded capture");output.write(buffer,0,n);}}
        require(before.equals(NextflowFiles.pin(source,maximum,()->{}))&&before.equals(NextflowFiles.pin(copy,maximum,()->{})),"Input changed during complete capture");return new Capture(source,copy,before,maximum);
    }
    private static int failed(Path owned,Exception failure,PrintStream error) {
        int code=failure instanceof FcsReader.UnsupportedFormatException||failure instanceof GatingMl20.UnsupportedDefinitionException||failure instanceof NoSuchFileException||failure instanceof AccessDeniedException||failure instanceof SecurityException?3:2;
        String message=(code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED. ":"FAIL2: NO COMPLETE RESULT ADMITTED. ")+failure+"\nREQUIRED ACTION: "+ACTION+"\n";
        if(owned!=null)try {Files.deleteIfExists(owned.resolve("COMPLETED.json"));if(Files.exists(owned.resolve("analysis.json")))Files.move(owned.resolve("analysis.json"),owned.resolve("unadmitted-analysis.json"));NextflowFiles.writeJson(owned.resolve("analysis.json"),Map.of("result_admitted",false,"status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED","cause",failure.toString(),"required_action",ACTION));write(owned.resolve("FAILURE.txt"),message);}catch(IOException recording) {message+="Failure recording also failed: "+recording+"\n";}
        error.print(message);error.flush();return code;
    }
    private static void appendWarnings(Path out,List<String> notes)throws IOException {for(String note:notes)Files.writeString(out.resolve("WARNINGS.md"),"\n- "+note+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);}
    private static long limit(Map<?,?> m,String key,long fallback,long minimum,long maximum)throws IOException {return m.containsKey(key)?NextflowFiles.integer(m.get(key),minimum,maximum):fallback;}
    private static double number(Object value)throws IOException {String s=value instanceof NextflowParameters.NumberToken token?token.text():value instanceof Number?value.toString():"";require(s.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require finite numeric SVD cutoff");double d=Double.parseDouble(s);require(Double.isFinite(d),"Nonfinite cutoff");return d;}
    private static String label(String text)throws IOException {require(!text.isBlank()&&text.length()<=32768&&text.chars().noneMatch(c->c<32||c==127),"Declare complete literal provenance without control characters");return text;}
    private static String quoted(String text) {return "\""+text.replace("\"","\"\"")+"\"";}
    private static Path resolve(Path parent,Object value)throws IOException {Path p=Path.of(label(NextflowFiles.text(value)));return(p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    private static Path regular(Path path)throws IOException {Path p=path.toAbsolutePath().normalize();if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(p.toString());require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Require an unlinked regular input file");return p.toRealPath();}
    private static Path destination(Path absolute,List<Path> protectedInputs)throws IOException {require(!Files.exists(absolute,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and select NEW output");Path parent=absolute.getParent();require(parent!=null,"Output needs a parent");while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();Path resolved=parent.toRealPath().resolve(parent.relativize(absolute)).normalize();for(Path input:protectedInputs)require(!input.startsWith(resolved)&&!resolved.startsWith(input),"Output overlaps protected input");return resolved;}
    private static void write(Path path,String text)throws IOException {Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
