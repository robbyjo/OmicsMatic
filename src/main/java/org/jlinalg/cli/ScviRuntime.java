/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Live exact-profile admission. Fingerprints establish integrity, not installation authenticity. */
final class ScviRuntime {
    private ScviRuntime() { }
    static final String PROFILE="scvi-tools-1.3.0-linux-x86_64-py3123-cpu";
    private static final String RESOURCE="/org/jlinalg/omics/scvi_runtime_probe.py";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String m){super(m);}Unavailable(String m,Throwable t){super(m,t);}}
    record Limits(long runtimeBytes,int members,long retainedBytes,long reportBytes,ScviProcess.Limits process) {
        Limits{if(runtimeBytes<1||members<1||retainedBytes<1||reportBytes<1||reportBytes>67_108_864)throw new IllegalArgumentException("Positive complete scVI runtime capacities required");Objects.requireNonNull(process);}
        static Limits health(){return new Limits(4_294_967_296L,100_000,536_870_912,4_194_304,ScviProcess.Limits.health());}
    }
    record Member(String kind,long bytes,String sha256,String link,String resolved) { }
    record Tree(Path root,Map<String,Member> members) {Tree{members=Map.copyOf(members);}}
    record Software(Tree prefix,Tree stdlib,Map<String,Member> external,boolean zipPresent) {Software{external=Map.copyOf(external);}}
    record Health(long latentRows,long latentValues,long classificationRows,long scalarCases,Map<String,Object> stages) {Health{stages=Map.copyOf(stages);}}
    record Context(Path python,Path realPython,Path home,Path prefix,Path stdlib,Path stdlibZip,Software software,
                   Tree managed,Limits limits,Health health,String hostKernel,String probeSha256,String commonSha256) {
        void verify(Runnable check)throws IOException{
            if(!System.getProperty("os.name").equals("Linux")||!System.getProperty("os.version").equals(hostKernel))throw new IOException("Admitted scVI host/kernel changed");
            if(!capture(prefix,stdlib,stdlibZip,software.external.keySet(),limits,check).equals(software)||!tree(home,new Budget(limits),check).equals(managed))throw new IOException("Admitted scVI software,probe,health records or complete member population changed");
            if(!python.toRealPath().equals(realPython))throw new IOException("Admitted Python executable resolution changed");
        }
    }
    static Context admit(Path supplied,ScviModelRequest.Release release,Path output,Limits limits,Runnable check)throws IOException{
        Objects.requireNonNull(release);Objects.requireNonNull(limits);Objects.requireNonNull(check);
        if(release!=ScviModelRequest.Release.V1_3_0)throw unavailable("The whole "+release.version+" runtime is not admitted by this exact CPU profile; no version is substituted");
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw unavailable("Use Linux x86_64 Java (inside WSL on Windows) for this original CPU profile");
        Path python=supplied.toAbsolutePath().normalize();
        if(!Files.exists(python)||!Files.isExecutable(python))throw unavailable("Required private Python is missing or execution is forbidden: "+python);
        Path bin=python.getParent(),prefix=bin==null?null:bin.getParent();
        if(prefix==null||!bin.getFileName().toString().equals("bin")||!bin.toRealPath().equals(bin)||!prefix.toRealPath().equals(prefix))throw new IOException("Require an explicit canonical private virtualenv/bin/python path");
        Path config=prefix.resolve("pyvenv.cfg");Map<String,String> cfg=config(config,check);
        if(!"false".equalsIgnoreCase(cfg.get("include-system-site-packages")))throw unavailable("Create a private virtualenv without system site packages; preserve this provider");
        String homeValue=cfg.get("home");if(homeValue==null)throw new IOException("Private Python configuration lacks its original home");Path baseBin=Path.of(homeValue).toAbsolutePath().normalize();
        Path base=baseBin.getParent();if(base==null||!baseBin.toRealPath().equals(baseBin))throw new IOException("Private Python base home is not canonical");
        Path stdlib=base.resolve("lib/python3.12"),zip=base.resolve("lib/python312.zip"),realPython=python.toRealPath();
        if(!Files.isDirectory(stdlib,LinkOption.NOFOLLOW_LINKS))throw unavailable("Required Python3.12 standard library is unavailable: "+stdlib);
        Set<String> initialExternal=Set.of(realPython.toString());Software before=capture(prefix,stdlib,zip,initialExternal,limits,check);
        Path home=output.toAbsolutePath().normalize();if(home.getParent()==null||!home.getParent().toRealPath().equals(home.getParent())||home.startsWith(prefix)||prefix.startsWith(home)||home.startsWith(stdlib)||stdlib.startsWith(home))throw new IOException("Require a NEW separate scVI admission directory under a canonical parent");
        Files.createDirectory(home);Path script=home.resolve("probe.py");byte[] resource=resource();Files.write(script,resource,StandardOpenOption.CREATE_NEW);String probeHash=ScviText.hash(script,1_048_576,check);
        Path common=home.resolve("scvi_provider_common.py");Files.write(common,resource("/org/jlinalg/omics/scvi_provider_common.py"),StandardOpenOption.CREATE_NEW);String commonHash=ScviText.hash(common,1_048_576,check);
        var environment=environment();ScviProcess.Result process;
        try{
            process=ScviProcess.executeOwned(List.of(python.toString(),"-I","-B",script.toString(),home.resolve("run/health").toString()),environment,home.resolve("run"),limits.process,()->{
                if(!before.equals(capture(prefix,stdlib,zip,initialExternal,limits,check))||!probeHash.equals(ScviText.hash(script,1_048_576,check))||!commonHash.equals(ScviText.hash(common,1_048_576,check)))throw new IOException("scVI provider/probe/shared IO bytes changed during runtime admission");
            },check);
        }catch(IOException failure){
            Path report=home.resolve("run/health/probe-failure.json");
            if(Files.isRegularFile(report,LinkOption.NOFOLLOW_LINKS)){
                Map<?,?> value=object(SimpleJson.parse(bounded(report,limits.reportBytes,check)));
                if("SKIP3-unavailable-exact-profile".equals(value.get("status"))&&"dependency".equals(value.get("phase")))throw new Unavailable("Exact scVI CPU dependency is unavailable: "+value.get("error")+". Required action: supply the permitted exact profile or leave dependent analysis skipped;retain "+home,failure);
            }throw failure;
        }
        Path healthDir=home.resolve("run/health");Map<?,?> probe=object(SimpleJson.parse(bounded(healthDir.resolve("probe.json"),limits.reportBytes,check)));
        validateProbe(probe,prefix,stdlib,realPython,base,home.resolve("run"),healthDir,limits,check);
        Health health=audit(healthDir,probe,check);var external=new TreeSet<String>();external.add(realPython.toString());
        for(Object item:list(probe.get("mapped_executable_files"))){if(!(item instanceof String p))throw new IOException("Invalid mapped executable identity");Path path=Path.of(p);if(!path.isAbsolute()||!path.normalize().equals(path)||!path.toRealPath().equals(path))throw new IOException("Mapped executable path is not canonical");external.add(p);}
        Software admitted=capture(prefix,stdlib,zip,external,limits,check);if(!admitted.prefix.equals(before.prefix)||!admitted.stdlib.equals(before.stdlib)||admitted.zipPresent!=before.zipPresent)throw new IOException("Complete Python provider population changed after health");
        var receipt=new LinkedHashMap<String,Object>();receipt.put("schema_version",1);receipt.put("status","ADMITTED_FIXED_CPU_PROFILE_ONLY");receipt.put("profile",PROFILE);receipt.put("python",python.toString());receipt.put("python_real",realPython.toString());receipt.put("prefix",prefix.toString());receipt.put("stdlib",stdlib.toString());receipt.put("versions",probe.get("versions"));receipt.put("health",Map.of("latent_rows",health.latentRows,"latent_values",health.latentValues,"classification_rows",health.classificationRows,"scalar_cases",health.scalarCases,"stages",health.stages));receipt.put("process_ledger",process.ledger().toString());receipt.put("probe_sha256",probeHash);receipt.put("software",softwareJson(admitted));receipt.put("runtime_installation_authenticated",false);receipt.put("model_analysis_completed",false);receipt.put("scope","Integrity of the explicitly selected trusted local Python,complete virtualenv/stdlib and executable mappings observed in fixed health. Host OS is not sandboxed or certified. A receipt is evidence,not authorization to open an arbitrary saved model. This live Context must be verified before/after model execution;new executable mappings must be audited by that workflow.");
        receipt.put("provider_common_sha256",commonHash);Files.writeString(home.resolve("runtime-admission.json"),RegenieRuntime.json(receipt)+"\n",StandardOpenOption.CREATE_NEW);
        ScviProcess.workspaceBytes(home,limits.process,List.of());
        Context result=new Context(python,realPython,home,prefix,stdlib,zip,admitted,tree(home,new Budget(limits),check),limits,health,System.getProperty("os.version"),probeHash,commonHash);result.verify(check);return result;
    }
    static Map<String,String> environment(){return Map.ofEntries(Map.entry("PATH","/usr/bin:/bin"),Map.entry("LANG","C.UTF-8"),Map.entry("LC_ALL","C.UTF-8"),Map.entry("OMP_NUM_THREADS","1"),Map.entry("OPENBLAS_NUM_THREADS","1"),Map.entry("MKL_NUM_THREADS","1"),Map.entry("NUMBA_NUM_THREADS","1"),Map.entry("PYTHONNOUSERSITE","1"),Map.entry("PYTHONDONTWRITEBYTECODE","1"),Map.entry("HF_HUB_OFFLINE","1"),Map.entry("CUDA_VISIBLE_DEVICES",""));}
    private static Unavailable unavailable(String message){return new Unavailable(message+". Required action: provide the permitted exact private runtime;when installation/execution is forbidden,skip the dependent analysis (exit3).");}
    private static byte[] resource()throws IOException{return resource(RESOURCE);}
    static byte[] resource(String name)throws IOException{if(!Set.of(RESOURCE,"/org/jlinalg/omics/scvi_provider_common.py","/org/jlinalg/omics/scvi_model_inspect.py","/org/jlinalg/omics/scvi_model_run.py").contains(name))throw new IOException("Unknown fixed scVI provider resource");try(var in=ScviRuntime.class.getResourceAsStream(name)){if(in==null)throw new IOException("Missing bundled scVI provider resource: "+name);byte[] bytes=in.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("Bundled provider resource capacity exceeded");return bytes;}}
    private static String bounded(Path p,long maximum,Runnable check)throws IOException{ScviText.hash(p,maximum,check);return Files.readString(p,StandardCharsets.UTF_8);}
    private static Map<String,String> config(Path path,Runnable check)throws IOException{var result=new TreeMap<String,String>();for(String line:bounded(path,65_536,check).lines().toList()){if(line.isBlank())continue;int i=line.indexOf('=');if(i<1)throw new IOException("Invalid private Python config");String k=line.substring(0,i).trim(),v=line.substring(i+1).trim();if(result.put(k,v)!=null)throw new IOException("Duplicate private Python config key");}return result;}
    private static final class Budget {
        private final Limits limits;private long bytes,retained;private int members;
        Budget(Limits limits){this.limits=limits;}
        void add(Path p,long n)throws IOException{bytes=Math.addExact(bytes,n);retained=Math.addExact(retained,4096L+8L*p.toString().length());if(++members>limits.members||bytes>limits.runtimeBytes||retained>limits.retainedBytes)throw new IOException("Complete scVI runtime fingerprint capacity exceeded at "+p+";bytes="+bytes+"/"+limits.runtimeBytes+",members="+members+"/"+limits.members+",reserved-retained="+retained+"/"+limits.retainedBytes);}
    }
    static Tree tree(Path root,Limits limits,Runnable check)throws IOException{return tree(root,new Budget(limits),check);}
    private static Tree tree(Path supplied,Budget budget,Runnable check)throws IOException{
        Path root=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.toRealPath().equals(root))throw new IOException("Canonical ordinary runtime directory required: "+root);var result=new TreeMap<String,Member>();
        try(var paths=Files.walk(root)){for(var it=paths.iterator();it.hasNext();){Path p=it.next();check.run();var attr=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);Member value;
            if(attr.isSymbolicLink()){Path target=p.toRealPath();String link=Files.readSymbolicLink(p).toString();if(Files.isDirectory(target,LinkOption.NOFOLLOW_LINKS)){if(!target.startsWith(root))throw new IOException("External runtime directory link needs an explicit closure: "+p);value=new Member("directory-link",0,"",link,target.toString());}else{ScviText.ordinary(target);budget.add(p,Files.size(target));value=new Member("file-link",Files.size(target),ScviText.hash(target,budget.limits.runtimeBytes,check),link,target.toString());result.put(root.relativize(p).toString().replace('\\','/'),value);continue;}}
            else if(attr.isDirectory())value=new Member("directory",0,"","","");
            else if(attr.isRegularFile()){ScviText.ordinary(p);budget.add(p,attr.size());value=new Member("file",attr.size(),ScviText.hash(p,budget.limits.runtimeBytes,check),"","");result.put(root.relativize(p).toString().replace('\\','/'),value);continue;}
            else throw new IOException("Nonordinary runtime member: "+p);
            budget.add(p,0);result.put(root.relativize(p).toString().replace('\\','/'),value);
        }}return new Tree(root,result);
    }
    private static Software capture(Path prefix,Path stdlib,Path zip,Set<String> external,Limits limits,Runnable check)throws IOException{
        var budget=new Budget(limits);Tree p=tree(prefix,budget,check),s=tree(stdlib,budget,check);var values=new TreeMap<String,Member>();var paths=new TreeSet<>(external);boolean hasZip=Files.exists(zip,LinkOption.NOFOLLOW_LINKS);if(hasZip)paths.add(zip.toString());
        for(String name:paths){Path path=Path.of(name);ScviText.ordinary(path);long n=Files.size(path);budget.add(path,n);values.put(name,new Member("external-file",n,ScviText.hash(path,limits.runtimeBytes,check),"",""));}return new Software(p,s,values,hasZip);
    }
    private static void validateProbe(Map<?,?> p,Path prefix,Path stdlib,Path realPython,Path base,Path work,Path health,Limits limits,Runnable check)throws IOException{
        if(!"completed-fixed-original-CPU-health-only".equals(p.get("status")))throw new IOException("Fixed runtime health did not complete");validateEnvironment(p,prefix,stdlib,realPython,base,work,health,limits,check);
    }
    private static void validateEnvironment(Map<?,?> p,Path prefix,Path stdlib,Path realPython,Path base,Path work,Path health,Limits limits,Runnable check)throws IOException{
        if(!PROFILE.equals(p.get("profile"))||!"3.12.3".equals(p.get("python"))||!prefix.toString().equals(p.get("prefix"))||!stdlib.toString().equals(p.get("stdlib"))||!realPython.toString().equals(p.get("python_real"))||!base.toString().equals(p.get("base_prefix"))||!Boolean.TRUE.equals(p.get("bytecode_disabled"))||!Boolean.FALSE.equals(p.get("cuda_available"))||p.get("torch_cuda")!=null)throw new IOException("Actual Python/CPU profile differs");
        if(number(p.get("isolated"))!=1||number(p.get("torch_threads"))!=1||number(p.get("torch_interop_threads"))!=1)throw new IOException("Original CPU isolation/thread contract differs");
        Map<?,?> versions=object(p.get("versions"));for(var pin:Map.of("scvi-tools","1.3.0","torch","2.6.0+cpu","anndata","0.11.4","numpy","2.0.2","pandas","2.2.3","scipy","1.15.2").entrySet())if(!pin.getValue().equals(versions.get(pin.getKey())))throw new IOException("Actual provider version differs: "+pin.getKey());
        Map<?,?> host=object(p.get("host"));if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel")))throw new IOException("Actual Python host differs from this Java host");
        Set<Path> generated=generatedImports(p,work,health,limits,check);
        for(Object name:list(p.get("sys_path"))){if(!(name instanceof String value))throw new IOException("Invalid Python import path");Path path=Path.of(value).toAbsolutePath().normalize();if(!path.startsWith(stdlib)&&!path.startsWith(prefix)&&!path.equals(base.resolve("lib/python312.zip"))&&!generated.contains(path))throw new IOException("Unexpected external Python import path: "+path);}
    }
    static void verifyExecution(Context runtime,Map<?,?> report,Path work,Path output,Runnable check)throws IOException{
        validateEnvironment(report,runtime.prefix,runtime.stdlib,runtime.realPython,runtime.stdlib.getParent().getParent(),work,output,runtime.limits,check);
        for(Object item:list(report.get("mapped_executable_files"))){if(!(item instanceof String name))throw new IOException("Invalid executable mapping");Path path=Path.of(name);ScviText.ordinary(path);Member expected=runtime.software.external.get(name);
            if(expected==null&&path.startsWith(runtime.prefix))expected=runtime.software.prefix.members.get(runtime.prefix.relativize(path).toString().replace('\\','/'));
            if(expected==null&&path.startsWith(runtime.stdlib))expected=runtime.software.stdlib.members.get(runtime.stdlib.relativize(path).toString().replace('\\','/'));
            if(expected==null||!expected.sha256.equals(ScviText.hash(path,runtime.limits.runtimeBytes,check)))throw new IOException("Executable mapping outside the admitted scVI source closure: "+path+". Required action: review/admit the matching exact profile;no model result is accepted.");
        }
    }
    private static Set<Path> generatedImports(Map<?,?> probe,Path work,Path health,Limits limits,Runnable check)throws IOException{
        var result=new HashSet<Path>();int index=0;for(Object item:list(probe.get("generated_import_dirs"))){Map<?,?> value=object(item);Object original=value.get("original");if(!(original instanceof String pathValue))throw new IOException("Generated import path missing");Path path=Path.of(pathValue);if(!path.isAbsolute()||!path.normalize().equals(path)||!path.startsWith(work)||path.equals(work)||!result.add(path))throw new IOException("Unowned or repeated generated Python import directory");
            if("absent".equals(value.get("disposition"))){if(value.get("snapshot")!=null||!object(value.get("members")).isEmpty()||Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Reported absent generated import changed");}
            else{
                String relative="generated-imports/"+index;if(!"snapshot-preserved-before-original-temporary-cleanup".equals(value.get("disposition"))||!relative.equals(value.get("snapshot")))throw new IOException("Generated import snapshot role differs");
                var bounded=new Limits(Math.min(67_108_864,limits.process.fileBytes()),Math.min(1024,limits.process.fileCount()),Math.min(16_777_216,limits.retainedBytes),limits.reportBytes,limits.process);Tree actual=tree(health.resolve(relative),bounded,check);var expected=new TreeMap<String,Member>();
                for(var entry:object(value.get("members")).entrySet()){if(!(entry.getKey() instanceof String name))throw new IOException("Invalid generated member name");Map<?,?> m=object(entry.getValue());if(!(m.get("kind") instanceof String kind)||!(m.get("sha256") instanceof String sha)||!(m.get("link") instanceof String link)||!(m.get("resolved") instanceof String resolved)||!Set.of("file","directory").contains(kind)||!link.isEmpty()||!resolved.isEmpty())throw new IOException("Generated import member metadata differs");expected.put(name,new Member(kind,number(m.get("bytes")),sha,link,resolved));}
                if(!actual.members.equals(expected))throw new IOException("Complete generated Python import snapshot differs");
            }index++;if(index>limits.members)throw new IOException("Generated import directory population exceeds capacity");
        }return Set.copyOf(result);
    }
    private static Health audit(Path directory,Map<?,?> probe,Runnable check)throws IOException{
        var stages=list(probe.get("stages"));List<String> names=List.of("scvi","scanvi","query","sysvi-normal","sysvi-vamp"),genes=new ArrayList<>();for(int i=0;i<8;i++)genes.add("g"+i);if(stages.size()!=names.size())throw new IOException("Incomplete original health model family population");long rows=0,values=0,classRows=0;var audits=new TreeMap<String,Object>();var limits=ScviOutputAudit.Limits.health();
        for(int index=0;index<names.size();index++){String name=names.get(index);Map<?,?> stage=object(stages.get(index));int n=name.equals("query")?8:32;var cells=new ArrayList<String>();for(int i=0;i<n;i++)cells.add((name.equals("query")?"query_β_":"health_β_")+i);List<String> classes=List.of("type_B","type_A");
            if(!name.equals(stage.get("name"))||!cells.equals(stage.get("cells"))||!genes.equals(stage.get("genes"))||number(stage.get("latent_dimensions"))!=3)throw new IOException("Complete original health identities/dimensions differ: "+name);
            var a=ScviOutputAudit.latent(directory.resolve(name+"-latent.tsv"),cells,3,limits,check);var b=ScviOutputAudit.latent(directory.resolve(name+"-reload-latent.tsv"),cells,3,limits,check);if(!a.equals(b))throw new IOException("Original saved-model reload latent values differ: "+name);rows+=a.rows();values+=a.values();
            if(name.equals("scanvi")||name.equals("query")){if(!classes.equals(stage.get("classes")))throw new IOException("Original learned class order differs");var p=ScviOutputAudit.classification(directory.resolve(name+"-probabilities.tsv"),directory.resolve(name+"-labels.tsv"),cells,classes,limits,check);var q=ScviOutputAudit.classification(directory.resolve(name+"-reload-probabilities.tsv"),directory.resolve(name+"-labels.tsv"),cells,classes,limits,check);if(!p.equals(q))throw new IOException("Original reloaded classification differs");classRows+=p.labels();}else if(stage.get("classes")!=null)throw new IOException("Unclassified family unexpectedly reports classes");
            var split=ScviOutputAudit.splits(directory.resolve(name+"-splits.tsv"),cells,limits,check);if(split.train()!=(name.equals("query")?8:24)||split.validation()!=(name.equals("query")?0:8)||split.test()!=0)throw new IOException("Actual original health training split differs");var history=ScviOutputAudit.history(directory.resolve(name+"-history.tsv"),1,limits,check);audits.put(name,Map.of("latent_rows",a.rows(),"latent_values",a.values(),"ordered_float32_sha256",a.orderedFloat32Sha256(),"history_metrics",history.metrics(),"history_observations",history.observations(),"train",split.train(),"validation",split.validation(),"test",split.test()));
        }return new Health(rows,values,classRows,ScviOutputAudit.kernels(directory.resolve("kernels.tsv"),limits,check),audits);
    }
    private static Map<String,Object> softwareJson(Software s){var m=new LinkedHashMap<String,Object>();m.put("prefix",treeJson(s.prefix));m.put("stdlib",treeJson(s.stdlib));m.put("external",memberJson(s.external));m.put("stdlib_zip_present",s.zipPresent);return m;}
    private static Map<String,Object> treeJson(Tree t){return Map.of("root",t.root.toString(),"members",memberJson(t.members));}
    private static Map<String,Object> memberJson(Map<String,Member> values){var m=new TreeMap<String,Object>();values.forEach((n,v)->m.put(n,Map.of("kind",v.kind,"bytes",v.bytes,"sha256",v.sha256,"link",v.link,"resolved",v.resolved)));return m;}
    private static Map<?,?> object(Object x)throws IOException{if(!(x instanceof Map<?,?> m))throw new IOException("Expected scVI runtime JSON object");return m;}
    private static List<?> list(Object x)throws IOException{if(!(x instanceof List<?> v))throw new IOException("Expected complete scVI runtime list");return v;}
    private static long number(Object x)throws IOException{if(!(x instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.longValue())throw new IOException("Expected whole scVI runtime value");return n.longValue();}
}
