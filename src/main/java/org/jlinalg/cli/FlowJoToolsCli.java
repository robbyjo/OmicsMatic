/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.cytometry.CytometryLookupTable;
import org.jlinalg.cytometry.FcsReader;
import org.jlinalg.cytometry.FlowJoFcsInput;
import org.jlinalg.cytometry.FlowJoWorkspace;

/** Captured complete native WSP membership/count runs; verification checks bytes, replay reruns science. */
public final class FlowJoToolsCli {
    private FlowJoToolsCli() { }
    private static final String PROFILE="native-flowjo10-wsp20-flowkit132-v1";
    private static final String ACTION="Require exit 0, COMPLETED.json and analysis.json result_admitted=true. SKIP3 means the complete dependent sample analysis was skipped: supply the named permitted input, supported export or exact biex calibration, then use NEW output. FAIL2 means no complete result is admitted: retain inputs and partial output, correct the named integrity, numerical, capacity or saved-count disagreement and retry NEW output. Never alter gates, drop events, repair identifiers, replace nonfinite values or substitute logicle for biex to force agreement. VERIFY checks captured bytes only; REPLAY requires the exact original producer JAR. Saved FlowJo counts and FlowKit reference outputs can disagree; neither is silently used to fit gates.";
    private static final String SCOPE="Native complete sample-local FlowJo 10.x / WSP20.0 gate memberships/counts, using the declared FlowKit1.3.2 interpretation. Square WSP compensation, linear/log/logicle/fasinh and explicit supplied biex tables; rectangles, polygons, 128-vertex ellipse approximations and hierarchical And/Or/Not. No gate learning, vendor runtime, FACSDiva acquisition/import, spectral WSP matrices or descriptive intensity statistics. No whole-tool, whole-paper or whole-goal equivalence claim.";
    private record Capture(Path source,Path copy,NextflowFiles.Pin pin,long maximum) { }
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path owned=null;
        try {
            if(args.length>0&&args[0].equals("SUMMARIZE"))return CytometryIntensityCli.run(java.util.Arrays.copyOfRange(args,1,args.length),output,error);
            if (args.length==0 || args.length==1 && args[0].equals("--help")) {
                output.println("flowjo-tools GATE --request request.json\nflowjo-tools VERIFY --run completed-directory\nflowjo-tools REPLAY --run completed-directory --out NEW-directory\nflowjo-tools SUMMARIZE --help (separate explicit unbinned measurement-statistics profile)\nGATE profile: "+SCOPE+"\n"+ACTION+"\nSee docs/FLOWJO_WORKSPACES.md for the complete explicit request and calibration format."); output.flush(); return output.checkError()?2:0;
            }
            if (args.length==3 && args[0].equals("VERIFY") && args[1].equals("--run")) { verify(Path.of(args[2])); output.println("PASS capture integrity only; no scientific fit rerun. "+ACTION); return output.checkError()?2:0; }
            if (args.length==5 && args[0].equals("REPLAY") && args[1].equals("--run") && args[3].equals("--out")) {
                Path prior=Path.of(args[2]).toAbsolutePath().normalize(); verify(prior); var producer=NextflowFiles.object(NextflowFiles.json(prior.resolve("producer.json"),1048576)); require(NextflowFiles.fingerprint(producer.get("jar"),1L<<31).equals(NextflowFiles.pin(producerJar(),1L<<31,()->{})),"REPLAY needs the exact retained original whole producer JAR; no current-build substitution");
                var request=new LinkedHashMap<String,Object>(); for (var e:NextflowFiles.object(NextflowFiles.json(prior.resolve("replay-request.json"),1048576)).entrySet()) request.put((String)e.getKey(),e.getValue()); request.put("out",Path.of(args[4]).toAbsolutePath().normalize().toString());
                Path target=destination(Path.of((String)request.get("out")),List.of(prior)); Files.createDirectories(target.getParent()); Files.createDirectory(target); owned=target; NextflowFiles.writeJson(target.resolve("replay-invocation.json"),request);
                return gate(target.resolve("replay-invocation.json"),request,target,output,error);
            }
            require(args.length==3 && args[0].equals("GATE") && args[1].equals("--request"),"Use GATE --request JSON, VERIFY --run directory, or REPLAY --run directory --out NEW");
            Path request=regular(Path.of(args[2])); var m=NextflowFiles.object(NextflowFiles.json(request,1048576));
            return gate(request,m,null,output,error);
        } catch (Exception failure) { return failed(owned,failure,error); }
    }
    private static int gate(Path request,Map<?,?> m,Path preowned,PrintStream output,PrintStream error) {
        Path owned=preowned;
        try {
            Set<String> required=Set.of("schema","profile","operation","workspace","workspace_pin","fcs","fcs_pin","sample_id","calibrations","out","legacy_charset","text_policy","numeric_policy","label_policy","time_policy","time_channel","legacy_zero_log_minimum_as_one","svd_relative_cutoff","saved_count_policy","population_declaration","gating_provenance");
            Set<String> optional=Set.of("maximum_input_bytes","maximum_events","maximum_membership_bytes","maximum_event_gate_pairs");
            require(m.keySet().containsAll(required) && m.keySet().stream().allMatch(k->required.contains(k)||optional.contains(k)),"Unexpected or missing explicit FlowJo request fields");
            require(NextflowFiles.integer(m.get("schema"),1,1)==1 && text(m,"profile").equals(PROFILE) && text(m,"operation").equals("MEMBERSHIP_COUNTS"),"Select exact FlowJo request schema/profile/operation");
            require(m.get("legacy_zero_log_minimum_as_one") instanceof Boolean,"Declare legacy interpretation Boolean");
            String saved=text(m,"saved_count_policy"); require(Set.of("REQUIRE_MATCH_WHEN_RECORDED","REPORT_DIFFERENCES").contains(saved),"Declare saved-count policy");
            text(m,"population_declaration"); text(m,"gating_provenance"); String id=text(m,"sample_id");
            long maxInput=limit(m,"maximum_input_bytes",1L<<30,58,1L<<36), maxEvents=limit(m,"maximum_events",10000000,0,100000000), maxOutput=limit(m,"maximum_membership_bytes",1L<<29,32,1L<<31), maxPairs=limit(m,"maximum_event_gate_pairs",250000000,0,2000000000);
            Path parent=request.getParent(), wsp=regular(resolve(parent,m.get("workspace"))), fcs=regular(resolve(parent,m.get("fcs"))); var protectedInputs=new ArrayList<Path>(List.of(request,wsp,fcs)); var tables=NextflowFiles.list(m.get("calibrations")); require(tables.size()<=512,"Too many complete calibration definitions");
            var tablePaths=new ArrayList<Path>(); for (Object table:tables) { var c=NextflowFiles.object(table); NextflowFiles.keys(c,"parameter","attributes","table","pin","provenance"); Path p=regular(resolve(parent,c.get("table"))); tablePaths.add(p); protectedInputs.add(p); }
            Path jar=producerJar(); var jarPin=NextflowFiles.pin(jar,1L<<31,()->{}); protectedInputs.add(jar); var requestPin=NextflowFiles.pin(request,1048576,()->{});
            Path target=owned==null?destination(resolve(parent,m.get("out")),protectedInputs):owned;
            if (owned==null) { Files.createDirectories(target.getParent()); Files.createDirectory(target); owned=target; }
            else require(owned.equals(resolve(parent,m.get("out"))),"Replay output mismatch");
            write(owned.resolve("WARNINGS.md"),"# Read before using these results\n\n"+SCOPE+"\n\n"+ACTION+"\n\nSaved-count policy: "+saved+". Saved statistics are not computed by this membership/count operation.\n"); Files.createDirectory(owned.resolve("inputs"));
            var captures=new ArrayList<Capture>(); captures.add(capture(request,owned.resolve("source-request.json"),1048576,requestPin));
            captures.add(capture(wsp,owned.resolve("inputs/workspace.wsp"),16777216,NextflowFiles.fingerprint(m.get("workspace_pin"),16777216))); captures.add(capture(fcs,owned.resolve("inputs/source.fcs"),maxInput,NextflowFiles.fingerprint(m.get("fcs_pin"),maxInput)));
            var calibrations=new ArrayList<FlowJoWorkspace.Calibration>(); var replayTables=new ArrayList<Object>();
            for (int i=0;i<tables.size();i++) {
                var c=NextflowFiles.object(tables.get(i)); Path dest=owned.resolve("inputs/calibration-"+i+".tsv"); captures.add(capture(tablePaths.get(i),dest,1048576,NextflowFiles.fingerprint(c.get("pin"),1048576)));
                var attrs=new LinkedHashMap<String,String>(); for (var e:NextflowFiles.object(c.get("attributes")).entrySet()) attrs.put(literal(NextflowFiles.text(e.getKey())),literal(NextflowFiles.text(e.getValue())));
                calibrations.add(new FlowJoWorkspace.Calibration(new FlowJoWorkspace.TransformDefinition(text(c,"parameter"),"biex",attrs),table(dest),text(c,"provenance")));
                var rc=new LinkedHashMap<Object,Object>(c); rc.put("table",dest.toString()); replayTables.add(rc);
            }
            var replay=new LinkedHashMap<Object,Object>(m); replay.put("workspace",owned.resolve("inputs/workspace.wsp").toString()); replay.put("fcs",owned.resolve("inputs/source.fcs").toString()); replay.put("calibrations",replayTables); NextflowFiles.writeJson(owned.resolve("replay-request.json"),replay);
            NextflowFiles.writeJson(owned.resolve("producer.json"),Map.of("profile",PROFILE,"jar",jarPin,"retained_jar_path",jar.toString(),"java_version",System.getProperty("java.version"),"java_vendor",System.getProperty("java.vendor"),"native_java_membership_engine",true,"vendor_runtime_executed",false));
            var workspace=FlowJoWorkspace.read(owned.resolve("inputs/workspace.wsp"),FlowJoWorkspace.Limits.defaults()); var report=new LinkedHashMap<String,Object>(); var differences=new ArrayList<Object>();
            try (FcsReader reader=FcsReader.open(owned.resolve("inputs/source.fcs"),new FcsReader.Limits(maxInput,maxEvents,512,16777216),Charset.forName(text(m,"legacy_charset")),FcsReader.TextPolicy.valueOf(text(m,"text_policy")),FcsReader.NumericPolicy.valueOf(text(m,"numeric_policy")))) {
                String time=m.get("time_channel")==null?null:text(m,"time_channel"); var bridge=new FlowJoFcsInput(reader,FlowJoFcsInput.LabelPolicy.valueOf(text(m,"label_policy")),FlowJoFcsInput.TimePolicy.valueOf(text(m,"time_policy")),time,(Boolean)m.get("legacy_zero_log_minimum_as_one"));
                var program=workspace.compile(id,bridge.names(),calibrations,number(m.get("svd_relative_cutoff"))); var gates=program.gates(); long events=reader.info().events(), pairs=Math.multiplyExact(events,gates.size()); require(pairs<=maxPairs,"Complete event-by-gate analysis exceeds declared pair capacity"); require(Math.addExact(Math.multiplyExact(events,24),Math.multiplyExact(pairs,2))+32768<=maxOutput,"Complete membership output exceeds declared conservative byte capacity; no prefix result");
                var notes=new ArrayList<>(reader.info().warnings()); notes.addAll(bridge.warnings()); notes.addAll(program.warnings()); for (String note:notes) Files.writeString(owned.resolve("WARNINGS.md"),"\n- "+note+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
                long[] counts=new long[gates.size()]; double[] row=new double[reader.info().parameters().size()]; long index=0;
                try (BufferedWriter writer=Files.newBufferedWriter(owned.resolve("memberships.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                    writer.write("event_index"); for (int i=0;i<gates.size();i++) writer.write("\tg"+i); writer.write('\n');
                    while(reader.readEvent(row)) { boolean[] membership; try { membership=program.evaluate(bridge.scale(row)); } catch(RuntimeException failure) { throw new IOException("Source event "+(index+1)+": "+failure,failure); } writer.write(Long.toString(++index)); for(int i=0;i<membership.length;i++){writer.write(membership[i]?"\t1":"\t0");if(membership[i])counts[i]++;} writer.write('\n'); }
                }
                require(index==events,"Incomplete FCS event population"); var summary=reader.summary(); require(Files.size(owned.resolve("memberships.tsv"))<=maxOutput,"Membership byte capacity exceeded; result refused");
                var gs=new ArrayList<Object>(); var paths=new LinkedHashMap<List<String>,Long>(); for(int i=0;i<gates.size();i++) paths.put(gates.get(i).path(),counts[i]);
                try(BufferedWriter writer=Files.newBufferedWriter(owned.resolve("counts.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
                    writer.write("gate_id\tcount\tparent_events\tall_events\tpercent_parent\tpercent_all\tsaved_count\n");
                    for(int i=0;i<gates.size();i++) { var g=gates.get(i); List<String> parentPath=g.path().subList(0,g.path().size()-1); long parentCount=parentPath.isEmpty()?events:paths.get(parentPath); var gm=new LinkedHashMap<String,Object>(); gm.put("gate_id","g"+i);gm.put("path",g.path());gm.put("type",g.type());gm.put("complement",g.complement());gm.put("count",counts[i]);gm.put("parent_events",parentCount);gm.put("saved_workspace_count",g.savedCount());gs.add(gm); savedDifference(g.path(),g.savedCount(),counts[i],differences); writer.write("g"+i+"\t"+counts[i]+"\t"+parentCount+"\t"+events+"\t"+percent(counts[i],parentCount)+"\t"+percent(counts[i],events)+"\t"+(g.savedCount()==null?"UNRECORDED":g.savedCount())+"\n"); }
                }
                var sample=workspace.samples().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(); savedDifference(List.of(),sample.savedCount(),events,differences);
                report.put("profile",PROFILE);report.put("operation","MEMBERSHIP_COUNTS");report.put("sample_id",id);report.put("sample_name",sample.name());report.put("saved_uri_metadata_only",sample.uri());report.put("flowjo_version",workspace.flowJoVersion());report.put("events",events);report.put("event_gate_pairs",pairs);report.put("gates",gs);report.put("source_fcs",sourceInfo(reader.info()));report.put("fcs_summary",Map.of("events_read",summary.eventsRead(),"integer_values_with_unused_bits",summary.integerValuesWithUnusedBits(),"nonfinite_values",summary.nonfiniteValues()));report.put("mapped_channels",bridge.names());report.put("compensation_diagnostics",diagnostics(program.compensationDiagnostics()));report.put("saved_count_policy",saved);report.put("saved_count_differences",differences);report.put("saved_count_agreement",differences.isEmpty());report.put("warnings",notes);report.put("population_declaration",m.get("population_declaration"));report.put("gating_provenance",m.get("gating_provenance"));report.put("descriptive_intensity_statistics_computed",false);report.put("whole_vendor_tool_qualified",false);report.put("whole_paper_reproduced",false);report.put("whole_goal_complete",false);
            }
            if(!differences.isEmpty()) { String warning="\n**WARNING: "+differences.size()+" saved FlowJo count disagreements. Native FlowKit-profile results do not reproduce those saved counts. Read saved_count_differences in analysis.json, verify the exact workspace/FCS pairing and obtain independent current vendor results before claiming workspace reproduction. No gates or events were changed to force agreement.**\n"; Files.writeString(owned.resolve("WARNINGS.md"),warning,StandardCharsets.UTF_8,StandardOpenOption.APPEND);error.print(warning); }
            for(Capture c:captures) require(c.pin.equals(NextflowFiles.pin(c.source,c.maximum,()->{})) && c.pin.equals(NextflowFiles.pin(c.copy,c.maximum,()->{})),"Original or captured input changed during analysis"); require(jarPin.equals(NextflowFiles.pin(jar,1L<<31,()->{})),"Producer JAR changed during analysis");
            boolean admitted=saved.equals("REPORT_DIFFERENCES")||differences.isEmpty(); report.put("result_admitted",admitted); report.put("status",admitted?"PASS_NATIVE_DECLARED_PROFILE":"FAIL2_SAVED_COUNT_DISAGREEMENT"); NextflowFiles.writeJson(owned.resolve("analysis.json"),report);
            if(!admitted) { write(owned.resolve("FAILURE.txt"),"FAIL2: saved-count policy requires agreement; no result admitted.\n"+ACTION+"\n"); error.println("FAIL2: saved-count disagreement. READ "+owned.resolve("WARNINGS.md")); return 2; }
            var manifest=new LinkedHashMap<String,Object>(); try(var files=Files.walk(owned)) { for(Path p:files.filter(Files::isRegularFile).sorted().toList())manifest.put(owned.relativize(p).toString().replace('\\','/'),NextflowFiles.pin(p,Math.max(16777216,Math.max(maxInput,maxOutput)),()->{})); } NextflowFiles.writeJson(owned.resolve("manifest.json"),manifest);
            output.println("PASS native declared FlowKit-profile memberships/counts; saved count differences="+differences.size()+". READ "+owned.resolve("WARNINGS.md"));output.flush();error.flush();require(!output.checkError()&&!error.checkError(),"Console recording failed");
            NextflowFiles.writeJson(owned.resolve("COMPLETED.json"),Map.of("profile",PROFILE,"status","PASS_NATIVE_DECLARED_PROFILE","manifest",NextflowFiles.pin(owned.resolve("manifest.json"),4194304,()->{}))); return 0;
        } catch(Exception failure) { return failed(owned,failure,error); }
        catch(OutOfMemoryError failure) { return failed(owned,new IOException("Java heap exhausted; preserve evidence and retry NEW output with sufficient resources",failure),error); }
    }
    static void verify(Path run) throws IOException {
        Path root=run.toAbsolutePath().normalize().toRealPath(); var marker=NextflowFiles.object(NextflowFiles.json(root.resolve("COMPLETED.json"),1048576)); require(PROFILE.equals(marker.get("profile")) && "PASS_NATIVE_DECLARED_PROFILE".equals(marker.get("status")),"No admitted FlowJo completion marker"); require(NextflowFiles.fingerprint(marker.get("manifest"),4194304).equals(NextflowFiles.pin(root.resolve("manifest.json"),4194304,()->{})),"Manifest differs from completion marker");
        var manifest=NextflowFiles.object(NextflowFiles.json(root.resolve("manifest.json"),4194304)); var expected=new HashSet<String>(Set.of("manifest.json","COMPLETED.json"));
        for(var entry:manifest.entrySet()) { String name=NextflowFiles.member((String)entry.getKey()); require(expected.add(name),"Duplicate manifest member"); var pin=NextflowFiles.fingerprint(entry.getValue(),1L<<36); require(pin.equals(NextflowFiles.pin(root.resolve(name),1L<<36,()->{})),"Captured file differs: "+name); }
        var expectedDirectories=new HashSet<Path>();expectedDirectories.add(root);for(String name:expected){Path parent=root.resolve(name).getParent();while(parent!=null&&parent.startsWith(root)){expectedDirectories.add(parent);parent=parent.getParent();}}
        var actual=new HashSet<String>();var actualDirectories=new HashSet<Path>();try(var files=Files.walk(root)){for(Path p:files.toList()){var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(!a.isSymbolicLink()&&p.toRealPath().equals(p),"Linked run entry is not admitted");if(a.isDirectory())actualDirectories.add(p);else{require(a.isRegularFile(),"Unsupported run entry");actual.add(root.relativize(p).toString().replace('\\','/'));}}}require(expected.equals(actual)&&expectedDirectories.equals(actualDirectories),"Run contains missing or unrecorded entries");require(Boolean.TRUE.equals(NextflowFiles.object(NextflowFiles.json(root.resolve("analysis.json"),16777216)).get("result_admitted")),"No admitted analysis");
    }
    static CytometryLookupTable table(Path p) throws IOException { List<String> rows=Files.readAllLines(p,StandardCharsets.UTF_8); require(rows.size()==4098 && rows.get(0).equals("input\toutput"),"Supply exact header and 4097 complete biex calibration rows"); double[] x=new double[4097],y=new double[4097]; for(int i=0;i<4097;i++){String[] c=rows.get(i+1).split("\t",-1);require(c.length==2,"Calibration table shape");x[i]=decimal(c[0]);y[i]=decimal(c[1]);}return new CytometryLookupTable(x,y); }
    private static Capture capture(Path source,Path copy,long maximum,NextflowFiles.Pin pin) throws IOException { require(pin.equals(NextflowFiles.pin(source,maximum,()->{})),"Input does not match declared fingerprint: "+source);long count=0;try(var input=Files.newInputStream(source);var output=Files.newOutputStream(copy,StandardOpenOption.CREATE_NEW)){byte[] b=new byte[65536];for(int n;(n=input.read(b))>=0;){count=Math.addExact(count,n);require(count<=pin.bytes()&&count<=maximum,"Input grew during capture");output.write(b,0,n);}}require(pin.equals(NextflowFiles.pin(source,maximum,()->{}))&&pin.equals(NextflowFiles.pin(copy,maximum,()->{})),"Input changed during bounded capture");return new Capture(source,copy,pin,maximum); }
    private static Path producerJar() throws Exception { Path p=Path.of(FlowJoToolsCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize(); if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new FcsReader.UnsupportedFormatException("Packaged analysis/replay requires the retained complete executable JAR; dependent analysis skipped"); return regular(p); }
    private static void savedDifference(List<String> path,String token,long count,List<Object> out) throws IOException { if(token==null||token.equals("-1"))return;require(token.matches("[0-9]+"),"Invalid saved workspace count; no trimming or repair");long value;try{value=Long.parseLong(token);}catch(NumberFormatException e){throw new IOException("Saved count capacity exceeded",e);}if(value!=count)out.add(Map.of("path",path,"saved_count",token,"computed_count",count)); }
    private static String percent(long count,long denominator) { return denominator==0?"UNDEFINED_EMPTY_POPULATION":Double.toString(100.0*count/denominator); }
    private static Map<String,Object> sourceInfo(FcsReader.Info info) {
        var parameters=new ArrayList<Object>(); for(var p:info.parameters()) { var row=new LinkedHashMap<String,Object>();row.put("index",p.index());row.put("name",p.name());row.put("label",p.label());row.put("bits",p.bits());row.put("range",p.range());row.put("amplification",p.amplification());row.put("gain",p.gain());parameters.add(row); }
        var keywords=new ArrayList<Object>();for(var k:info.keywords())keywords.add(Map.of("name",k.name(),"value",k.value()));
        return Map.of("version",info.version(),"data_type",info.dataType(),"byte_order",info.byteOrder().toString(),"parameters",parameters,"keywords",keywords);
    }
    private static Map<String,Object> diagnostics(org.jlinalg.cytometry.CytometryCompensation.Diagnostics d) { return d==null?null:Map.of("method",d.method(),"rank",d.rank(),"fluorochromes",d.fluorochromes(),"detectors",d.detectors(),"relative_cutoff",d.relativeCutoff(),"singular_values",d.singularValues()); }
    private static int failed(Path owned,Exception failure,PrintStream error) { int code=failure instanceof FlowJoWorkspace.UnsupportedDefinitionException||failure instanceof FcsReader.UnsupportedFormatException||failure instanceof NoSuchFileException||failure instanceof AccessDeniedException||failure instanceof SecurityException?3:2;String message=(code==3?"SKIP3: COMPLETE DEPENDENT SAMPLE ANALYSIS SKIPPED. ":"FAIL2: NO COMPLETE RESULT ADMITTED. ")+failure+"\nREQUIRED ACTION: "+ACTION+"\n";if(owned!=null)try{Files.deleteIfExists(owned.resolve("COMPLETED.json"));if(Files.exists(owned.resolve("analysis.json")))Files.move(owned.resolve("analysis.json"),owned.resolve("unadmitted-analysis.json"));NextflowFiles.writeJson(owned.resolve("analysis.json"),Map.of("result_admitted",false,"status",code==3?"SKIP3":"FAIL2","cause",failure.toString(),"required_action",ACTION));write(owned.resolve("FAILURE.txt"),message);}catch(IOException recording){message+="Failure recording failed: "+recording+"\n";}error.print(message);error.flush();return code; }
    private static long limit(Map<?,?> m,String key,long fallback,long minimum,long maximum) throws IOException { return m.containsKey(key)?NextflowFiles.integer(m.get(key),minimum,maximum):fallback; }
    private static double number(Object value) throws IOException { return decimal(value instanceof NextflowParameters.NumberToken token?token.text():value instanceof Number?value.toString():""); }
    private static double decimal(String text) throws IOException { require(text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require complete finite decimal");double x=Double.parseDouble(text);require(Double.isFinite(x),"Nonfinite decimal");return x; }
    private static String text(Map<?,?> m,String key) throws IOException { return literal(NextflowFiles.text(m.get(key))); }
    private static String literal(String s) throws IOException { require(!s.isBlank() && s.length()<=32768 && s.chars().noneMatch(c->c<32||c==127),"Declare complete literal text without control characters");return s; }
    private static Path resolve(Path parent,Object value) throws IOException { Path p=Path.of(literal(NextflowFiles.text(value)));return(p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize(); }
    private static Path regular(Path p) throws IOException { p=p.toAbsolutePath().normalize();if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(p.toString());require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.toRealPath().equals(p),"Require unlinked regular input and ancestors");return p; }
    private static Path destination(Path target,List<Path> inputs) throws IOException { require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output exists; select NEW output");Path parent=target.getParent();require(parent!=null,"Output needs parent");while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();Path resolved=parent.toRealPath().resolve(parent.relativize(target)).normalize();for(Path input:inputs)require(!input.startsWith(resolved)&&!resolved.startsWith(input),"Output overlaps protected input");return resolved; }
    private static void write(Path p,String text) throws IOException { Files.writeString(p,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
    private static void require(boolean b,String s) throws IOException { if(!b)throw new IOException(s); }
}
