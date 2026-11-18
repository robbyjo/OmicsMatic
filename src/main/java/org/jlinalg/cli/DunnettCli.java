/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import jdistlib.T;
import org.jlinalg.stats.DunnettModel;

/** Complete ordinary one-way Dunnett family: native fit audit and original multcomp inference. */
public final class DunnettCli {
    private static final String PROFILE="dunnett-multcomp1429-oneway-v1";
    private static final String SCOPE="Two-sided single-step Dunnett treatment-minus-control comparisons and simultaneous intervals for ALL declared treatments. Ordinary independent Gaussian groups with common variance; every observation contributes to the pooled residual variance. No repeated/paired, mixed, nested, covariate-adjusted or unequal-variance inference. Java independently fits means, residual SD/df, effects, SE, t and the shared-control correlation; original multcomp 1.4-29 / mvtnorm computes multivariate probabilities and intervals. Estimated integration errors are numerical diagnostics, not rigorous error bounds. This profile does not establish whole Prism/multcomp, a paper-specific workflow or completion of the 250-paper goal.";
    private static final String ACTION="Require exit 0, COMPLETED.json and analysis.json result_admitted=true. SKIP3: dependent analysis skipped for an unavailable/forbidden R runtime, package or unsupported design; install a permitted R runtime and multcomp 1.4-29 with its dependencies in a dedicated library, or use a qualified method for the required design, then retry NEW output. FAIL2: no complete family admitted; retain all inputs/partial outputs, read FAILURE.txt and worker logs, correct the named format, model, numeric, integration-budget or integrity problem and retry NEW output. No rows/groups or failed contrasts may be dropped to force success. Preserve original adjusted-p zeros and their resolution warnings; a numeric zero is not an exact mathematical zero.";
    private static final int MAX_BYTES=64<<20;
    private record Observations(List<String> labels,double[][] values,boolean bom) { }
    private DunnettCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path owned=null;
        try {
            if(args.length==1&&args[0].equals("--help")) {
                output.println("posthoc-tools dunnett --request UTF8_JSON\nRequired: schema_version=1, input (UTF-8 literal group/value TSV), out NEW, control (exact label), design=independent-common-variance, confidence_level (0.5<=level<1), data_provenance, assumptions_declaration, r_home, rscript, library, startup_warning_policy=FAIL|RECORD_BUILD_WARNINGS. Paths resolve against the request. Group order is first appearance. Complete finite observations only; no blank or quoted fields.\nOptional: seed=19870624 (0..2147483647), maxpts=1000000 (1000..100000000), absolute_tolerance=1e-6 (1e-12..1e-3), interval_probability_tolerance=1e-5 (1e-10..1e-2), timeout_seconds=600 (1..86400), maximum_output_bytes=268435456 (1MiB..2GiB). Original Genz-Bretz integration uses releps=0; qmvt maxiter=500. Two-sided confidence below 0.5 is unsupported by the selected original quantile method.\nWhole-input limits: 64MiB,2million observations,256 groups. Runtime/library are trusted code, fingerprinted before/after execution; their bytes are not copied into the result. Replay needs the same permitted provider. No automatic installation or OS sandbox.\n"+SCOPE+"\n"+ACTION);output.flush();return output.checkError()?2:0;
            }
            require(args.length==2&&args[0].equals("--request"),"Use dunnett --request JSON or --help");
            Path request=regular(Path.of(args[1]));var requestPin=NextflowFiles.pin(request,1048576,()->{});byte[] raw=NextflowFiles.bytes(request,1048576);
            require(requestPin.equals(pin(raw)),"Request changed during capture");
            Map<?,?> m=NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000));
            Set<String> allowed=Set.of("schema_version","input","out","control","design","confidence_level","data_provenance","assumptions_declaration","r_home","rscript","library","startup_warning_policy","seed","maxpts","absolute_tolerance","interval_probability_tolerance","timeout_seconds","maximum_output_bytes");
            require(m.keySet().stream().allMatch(allowed::contains),"Unknown request setting; no inferred design");NextflowFiles.integer(m.get("schema_version"),1,1);
            Path input=regular(resolve(request.getParent(),m.get("input")));Path dest=destination(resolve(request.getParent(),m.get("out")),List.of(request,input));
            Files.createDirectories(dest.getParent());Files.createDirectory(dest);owned=dest;
            write(owned.resolve("WARNINGS.md"),"# Scope, warnings and required actions\n\n"+SCOPE+"\n\n"+ACTION+"\n\nOnly exact package-build-version warnings can be retained under RECORD_BUILD_WARNINGS. Other original model/numerical warnings are fatal. Original numerical outputs, R fit, RNG/session, complete input/request, provider hashes and worker logs remain in this directory. No prerequisite software is redistributed in this result. A hash records integrity; it does not authenticate an upstream publisher.\n");
            Files.write(owned.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            String design=NextflowFiles.text(m.get("design"));
            if(Set.of("paired","repeated-measures","mixed","nested","unequal-variance","independent-unequal-variance").contains(design))throw new DunnettProcess.Unavailable("Requested design "+design+" requires a different qualified method; dependent analysis skipped");
            require(design.equals("independent-common-variance"),"Explicit independent-common-variance design required");
            String provenance=label(NextflowFiles.text(m.get("data_provenance"))),assumptions=label(NextflowFiles.text(m.get("assumptions_declaration")));
            String policy=NextflowFiles.text(m.get("startup_warning_policy"));require(Set.of("FAIL","RECORD_BUILD_WARNINGS").contains(policy),"Declare the startup warning policy");
            double confidence=number(m.get("confidence_level"));require(confidence>=.5&&confidence<1,"Selected original two-sided quantile requires 0.5<=confidence_level<1");
            long seed=integer(m,"seed",19870624,0,Integer.MAX_VALUE),maxpts=integer(m,"maxpts",1000000,1000,100000000);
            long seconds=integer(m,"timeout_seconds",600,1,86400),cap=integer(m,"maximum_output_bytes",268435456,1048576,2147483648L);
            double tolerance=optionalNumber(m,"absolute_tolerance",1e-6,1e-12,1e-3),ptol=optionalNumber(m,"interval_probability_tolerance",1e-5,1e-10,1e-2);
            var inputPin=NextflowFiles.pin(input,MAX_BYTES,()->{});byte[] inputBytes=NextflowFiles.bytes(input,MAX_BYTES);require(inputPin.equals(pin(inputBytes)),"Complete input changed during capture");
            Path source=owned.resolve("source-input.tsv");Files.write(source,inputBytes,StandardOpenOption.CREATE_NEW);Observations observations=observations(inputBytes);
            int control=observations.labels().indexOf(NextflowFiles.text(m.get("control")));require(control>=0,"Exact control label absent; no label normalization");
            DunnettModel.Result model=DunnettModel.fit(observations.values(),control);writeNative(owned,observations,model);
            Path home=directory(resolve(request.getParent(),m.get("r_home"))),library=directory(resolve(request.getParent(),m.get("library"))),rscript=regular(resolve(request.getParent(),m.get("rscript")));
            require(rscript.startsWith(home),"Declared Rscript must reside in declared R_HOME");
            for(Path provider:List.of(home,library))require(!owned.startsWith(provider)&&!provider.startsWith(owned),"Output overlaps a provider tree");
            Map<String,Object> before=provider(home,library);NextflowFiles.writeJson(owned.resolve("provider-before.json"),before);
            Path work=owned.resolve("worker");Files.createDirectory(work);
            try(var resource=DunnettCli.class.getResourceAsStream("/org/jlinalg/stats/dunnett-worker.R")){require(resource!=null,"Packaged fixed original-inference worker missing");Files.copy(resource,work.resolve("worker.R"));}
            write(work.resolve("runtime-paths.txt"),home+"\n"+library+"\n");
            write(work.resolve("settings.txt"),control+"\n"+confidence+"\n"+seed+"\n"+maxpts+"\n"+tolerance+"\n"+ptol+"\n"+policy+"\n");
            var prepared=new StringBuilder("group\tvalue\n");for(int i=0;i<observations.values().length;i++)for(double value:observations.values()[i])prepared.append(i).append('\t').append(value).append('\n');write(work.resolve("prepared-observations.tsv"),prepared.toString());
            var launchFiles=new LinkedHashMap<String,NextflowFiles.Pin>();for(String name:List.of("worker.R","runtime-paths.txt","settings.txt","prepared-observations.tsv"))launchFiles.put(name,NextflowFiles.pin(work.resolve(name),MAX_BYTES,()->{}));NextflowFiles.writeJson(owned.resolve("worker-inputs.json"),launchFiles);
            try{DunnettProcess.run(work,rscript,home,seconds,cap);}
            finally {Map<String,Object> after=provider(home,library);NextflowFiles.writeJson(owned.resolve("provider-after.json"),after);require(before.equals(after),"Runtime/library changed during execution; no result admitted");for(var file:launchFiles.entrySet())require(file.getValue().equals(NextflowFiles.pin(work.resolve(file.getKey()),MAX_BYTES,()->{})),"Fixed worker or prepared input changed during execution; no result admitted");}
            Map<String,Object> audit=audit(work,model,confidence,tolerance,ptol);
            require(!Thread.currentThread().isInterrupted()&&!Files.exists(owned.resolve("CANCEL")),"Dunnett cancelled; no complete family admitted");
            require(inputPin.equals(NextflowFiles.pin(input,MAX_BYTES,()->{}))&&inputPin.equals(NextflowFiles.pin(source,MAX_BYTES,()->{})),"Original/captured input changed during execution");
            require(requestPin.equals(NextflowFiles.pin(request,1048576,()->{}))&&requestPin.equals(NextflowFiles.pin(owned.resolve("source-request.json"),1048576,()->{})),"Request changed during execution");
            var report=new LinkedHashMap<String,Object>();report.put("profile",PROFILE);report.put("result_admitted",true);report.put("design",design);report.put("method","original multcomp 1.4-29 single-step two-sided Dunnett; ordinary lm, all treatments versus one control");report.put("control",observations.labels().get(control));report.put("group_labels",observations.labels());report.put("data_provenance",provenance);report.put("assumptions_declaration",assumptions);report.put("confidence_level",confidence);report.put("seed",seed);report.put("maxpts",maxpts);report.put("absolute_tolerance",tolerance);report.put("interval_probability_tolerance",ptol);report.put("startup_warning_policy",policy);report.put("utf8_bom_present",observations.bom());report.put("native_audit",audit);report.put("provider_bytes_bundled",false);report.put("whole_vendor_tool_qualified",false);report.put("whole_goal_complete",false);
            NextflowFiles.writeJson(owned.resolve("analysis.json"),report);
            boolean limited=(boolean)audit.get("adjusted_probability_resolution_warning");long warnings=((Number)audit.get("startup_warnings")).longValue();
            if(limited)write(owned.resolve("NUMERIC-WARNING.txt"),"Original adjusted probabilities include zero or values <=10*absolute_tolerance. Their relative precision is unresolved. Preserve original values and error estimates; native unadjusted log probabilities remain in native-comparisons.tsv. Increase a permitted integration budget/tolerance setting and retry NEW output if more precision is required. Never interpret an original numeric zero as exactly zero probability.\n");
            if(warnings>0)write(owned.resolve("RUNTIME-WARNING.txt"),"Original package build-version warnings were explicitly retained by request. Read worker/STARTUP-WARNINGS.txt and SESSION.txt. For a provider without this patch-version mismatch, install dependencies built for the chosen permitted R release and retry NEW output with startup_warning_policy=FAIL.\n");
            var manifest=new TreeMap<String,Object>();try(var files=Files.walk(owned)){for(Path p:files.filter(Files::isRegularFile).sorted().toList())manifest.put(owned.relativize(p).toString().replace('\\','/'),NextflowFiles.pin(p,2147483648L,()->{}));}NextflowFiles.writeJson(owned.resolve("manifest.json"),manifest);
            output.println("PASS Dunnett complete family: "+model.contrasts().size()+" treatments versus "+observations.labels().get(control)+". READ "+owned.resolve("WARNINGS.md")+(warnings>0?" AND RUNTIME-WARNING.txt":"")+(limited?" AND NUMERIC-WARNING.txt":""));output.flush();error.flush();require(!output.checkError()&&!error.checkError(),"Console recording failed; no complete family");
            NextflowFiles.writeJson(owned.resolve("COMPLETED.json"),Map.of("status","PASS_DUNNETT_COMPLETE_FAMILY","profile",PROFILE,"manifest",NextflowFiles.pin(owned.resolve("manifest.json"),1048576,()->{})));return 0;
        } catch(Exception e){return failed(owned,e,error);}
          catch(OutOfMemoryError e){return failed(owned,new IOException("Heap exhausted; no partial family admitted; preserve inputs and retry NEW output with sufficient resources",e),error);}
    }
    private static Map<String,Object> audit(Path work,DunnettModel.Result model,double confidence,double tolerance,double ptol)throws IOException {
        var metrics=new LinkedHashMap<String,Double>();for(String[] row:table(work.resolve("original-diagnostics.tsv"),"metric\tvalue",2)){require(metrics.put(row[0],finite(row[1]))==null,"Duplicate original diagnostic");}
        require(metrics.keySet().equals(Set.of("df","residual_sd","critical","p_error","coverage","coverage_error","startup_warnings")),"Incomplete original model diagnostics");
        require(metrics.get("df")==model.residualDegrees(),"Original residual degrees differ from native complete population");close(metrics.get("residual_sd"),model.residualSd(),0,"pooled residual SD");
        double pError=metrics.get("p_error"),coverageError=metrics.get("coverage_error"),critical=metrics.get("critical");
        require(pError>=0&&pError<=tolerance&&coverageError>=0&&coverageError<=tolerance,"Original integration error exceeds declared tolerance");
        require(Math.abs(metrics.get("coverage")-confidence)<=ptol+2*tolerance,"Independent original interval coverage failed");
        int n=model.contrasts().size();double lower=T.quantile((1-confidence)/2,model.residualDegrees(),false,false),upper=T.quantile((1-confidence)/(2*n),model.residualDegrees(),false,false);
        require(critical>0&&critical>=lower-1e-8&&critical<=upper+1e-8,"Original common critical value violates univariate/Bonferroni bounds");
        var groups=table(work.resolve("original-groups.tsv"),"index\tn\tmean",3);require(groups.size()==model.groups().size(),"Incomplete original group population");
        var seen=new HashSet<Integer>();for(String[] row:groups){int index=exactInt(row[0]);require(index>=0&&index<model.groups().size()&&seen.add(index),"Original group identity changed");var g=model.groups().get(index);require(exactInt(row[1])==g.size(),"Original group count differs");close(finite(row[2]),g.mean(),model.residualSd(),"group mean");}
        var comparisons=table(work.resolve("original-comparisons.tsv"),"treatment\testimate\tse\tt\tadjusted_p\tlower\tupper",7);require(comparisons.size()==n,"Original comparison family incomplete");boolean limited=false;
        for(int i=0;i<n;i++) {
            String[] row=comparisons.get(i);var expected=model.contrasts().get(i);require(exactInt(row[0])==expected.treatment(),"Original treatment order differs from complete declared family");
            double effect=finite(row[1]),se=finite(row[2]),t=finite(row[3]),p=finite(row[4]),lo=finite(row[5]),hi=finite(row[6]);
            close(effect,expected.difference(),model.residualSd()+Math.abs(model.groups().get(model.control()).mean()),"treatment minus control effect");close(se,expected.standardError(),0,"contrast SE");close(t,expected.t(),1,"contrast t");
            require(p>=0&&p<=1,"Invalid original adjusted probability");double univariate=expected.unadjustedP();
            require(p>=univariate-pError-1e-12&&p<=Math.min(1,n*univariate)+pError+1e-12,"Adjusted probability violates independent Student-t/Bonferroni bounds");
            require(lo<hi&&lo<=effect&&hi>=effect,"Original interval is invalid or unrepresentable");
            close(lo,effect-critical*se,Math.abs(effect)+critical*se,"lower simultaneous interval");close(hi,effect+critical*se,Math.abs(effect)+critical*se,"upper simultaneous interval");
            limited|=p<=10*tolerance;
        }
        double[][] correlation=model.correlation();var rows=table(work.resolve("original-correlation.tsv"),null,n);require(rows.size()==n,"Original correlation matrix incomplete");
        for(int i=0;i<n;i++)for(int j=0;j<n;j++)close(finite(rows.get(i)[j]),correlation[i][j],0,"shared-control correlation");
        double warnings=metrics.get("startup_warnings");require(warnings>=0&&warnings==Math.rint(warnings),"Invalid original warning count");
        return Map.of("passed",true,"observations",model.observations(),"residual_df",model.residualDegrees(),"residual_sd",model.residualSd(),"critical",critical,"estimated_p_error",pError,"coverage",metrics.get("coverage"),"estimated_coverage_error",coverageError,"startup_warnings",(long)warnings,"adjusted_probability_resolution_warning",limited);
    }
    private static void writeNative(Path out,Observations observations,DunnettModel.Result model)throws IOException {
        var groups=new StringBuilder("index\tgroup\tn\tmean\n");for(var g:model.groups())groups.append(g.index()).append('\t').append('"').append(observations.labels().get(g.index()).replace("\"","\"\"")).append('"').append('\t').append(g.size()).append('\t').append(g.mean()).append('\n');write(out.resolve("native-groups.tsv"),groups.toString());
        var contrasts=new StringBuilder("treatment_index\tcontrol_index\teffect\tse\tt\tunadjusted_p\tlog_unadjusted_p\n");for(var c:model.contrasts())contrasts.append(c.treatment()).append('\t').append(c.control()).append('\t').append(c.difference()).append('\t').append(c.standardError()).append('\t').append(c.t()).append('\t').append(c.unadjustedP()).append('\t').append(c.logUnadjustedP()).append('\n');write(out.resolve("native-comparisons.tsv"),contrasts.toString());
    }
    private static Map<String,Object> provider(Path home,Path library)throws IOException {
        return Map.of("r_home",home.toString(),"library",library.toString(),"r_home_files",NextflowFiles.manifest(NextflowFiles.tree(home,50000,4L<<30,1L<<30,()->{})),"library_files",NextflowFiles.manifest(NextflowFiles.tree(library,50000,4L<<30,1L<<30,()->{})));
    }
    private static List<String[]> table(Path p,String header,int columns)throws IOException {
        String text=NextflowFiles.utf8(NextflowFiles.bytes(p,16<<20));var result=new ArrayList<String[]>();try(var lines=text.lines()){var it=lines.iterator();if(header!=null)require(it.hasNext()&&it.next().equals(header),"Unexpected original output header: "+p.getFileName());while(it.hasNext()){String[] row=it.next().split("\t",-1);require(row.length==columns,"Incomplete original output row");result.add(row);require(result.size()<=100000,"Original table capacity exceeded");}}return result;
    }
    private static Observations observations(byte[] bytes)throws IOException {
        String text=NextflowFiles.utf8(bytes);boolean bom=text.startsWith("\ufeff");if(bom)text=text.substring(1);var grouped=new LinkedHashMap<String,ArrayList<Double>>();int count=0;
        try(var lines=text.lines()){var it=lines.iterator();require(it.hasNext()&&it.next().equals("group\tvalue"),"Require exact group<TAB>value header");while(it.hasNext()){String[] row=it.next().split("\t",-1);require(row.length==2,"Every observation row requires two literal fields; empty rows are not removed");String name=label(row[0]);require(++count<=DunnettModel.MAXIMUM_OBSERVATIONS,"Complete population exceeds two million rows");grouped.computeIfAbsent(name,k->new ArrayList<>()).add(finite(row[1]));require(grouped.size()<=DunnettModel.MAXIMUM_GROUPS,"Complete population exceeds 256 groups");}}
        var labels=List.copyOf(grouped.keySet());double[][] values=new double[labels.size()][];for(int i=0;i<values.length;i++)values[i]=grouped.get(labels.get(i)).stream().mapToDouble(Double::doubleValue).toArray();return new Observations(labels,values,bom);
    }
    private static int failed(Path owned,Exception cause,PrintStream error) {
        int code=cause instanceof NoSuchFileException||cause instanceof AccessDeniedException||cause instanceof SecurityException||cause instanceof DunnettProcess.Unavailable?3:2;
        String message=(code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED. ":"FAIL2: NO COMPLETE DUNNETT FAMILY ADMITTED. ")+cause+"\nREQUIRED ACTION: "+ACTION+"\n";
        if(owned!=null)try{Files.deleteIfExists(owned.resolve("COMPLETED.json"));if(Files.exists(owned.resolve("analysis.json")))Files.move(owned.resolve("analysis.json"),owned.resolve("unadmitted-analysis.json"));NextflowFiles.writeJson(owned.resolve("analysis.json"),Map.of("result_admitted",false,"status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED","cause",cause.toString(),"required_action",ACTION));write(owned.resolve("FAILURE.txt"),message);}catch(IOException recording){message+="Failure recording also failed: "+recording+"\n";}
        error.print(message);error.flush();return code;
    }
    private static void close(double actual,double expected,double scale,String item)throws IOException {require(Math.abs(actual-expected)<=1e-9*(Math.max(Math.abs(actual),Math.abs(expected))+scale)+64*Math.ulp(expected),"Original/native disagreement in "+item);}
    private static double finite(String x)throws IOException {require(x.length()<=256&&x.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require complete finite decimal values");double value=Double.parseDouble(x);require(Double.isFinite(value),"Nonfinite value; no row removal or repair");return value;}
    private static int exactInt(String x)throws IOException {require(x.matches("[0-9]{1,9}"),"Invalid original integer identity/count");return Integer.parseInt(x);}
    private static double number(Object x)throws IOException {double value;if(x instanceof NextflowParameters.NumberToken n)value=Double.parseDouble(n.text());else if(x instanceof Number n)value=n.doubleValue();else throw new IOException("Require JSON number");require(Double.isFinite(value),"Require finite number");return value;}
    private static long integer(Map<?,?> m,String key,long fallback,long min,long max)throws IOException{return NextflowFiles.integer(m.containsKey(key)?m.get(key):fallback,min,max);}
    private static double optionalNumber(Map<?,?> m,String key,double fallback,double min,double max)throws IOException{double value=m.containsKey(key)?number(m.get(key)):fallback;require(value>=min&&value<=max,"Invalid "+key);return value;}
    private static String label(String x)throws IOException{require(!x.isBlank()&&x.length()<=32768&&x.chars().noneMatch(c->c<32||c==127),"Literal nonblank labels/paths/provenance without control characters required");return x;}
    private static Path resolve(Path parent,Object x)throws IOException{Path p=Path.of(label(NextflowFiles.text(x)));return(p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    private static Path regular(Path p)throws IOException{p=p.toAbsolutePath().normalize();if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(p.toString());require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Regular unlinked input required");return p.toRealPath();}
    private static Path directory(Path p)throws IOException{if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(p.toString());return NextflowFiles.directory(p.toRealPath());}
    private static Path destination(Path absolute,List<Path> protectedInputs)throws IOException{require(!Files.exists(absolute,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and select NEW output");Path parent=absolute.getParent();require(parent!=null,"Output needs a parent directory");while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();Path resolved=parent.toRealPath().resolve(parent.relativize(absolute)).normalize();for(Path input:protectedInputs)require(!input.startsWith(resolved)&&!resolved.startsWith(input),"Output overlaps protected input");return resolved;}
    private static NextflowFiles.Pin pin(byte[] bytes){return new NextflowFiles.Pin(bytes.length,NextflowFiles.sha(bytes));}
    private static void write(Path p,String x)throws IOException{Files.writeString(p,x,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}
