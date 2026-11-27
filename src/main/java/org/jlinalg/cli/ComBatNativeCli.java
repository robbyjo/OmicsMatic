/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.confounding.ComBat;
import static org.jlinalg.cli.NextflowFiles.*;

/** Continuous original-rule ComBat with literal inputs and complete fresh verification. */
public final class ComBatNativeCli {
    private ComBatNativeCli() { }
    private static final String PASS="PASS_NATIVE_SVA346_CONTINUOUS_COMBAT";
    private static final String RESOURCE="/org/jlinalg/omics/combat346/SOURCE.json";
    private static final String SCOPE="Native Java FP64 continuous ComBat using sva3.46.0 rules: explicit parametric/nonparametric, mean-only, reference batch and supplied numeric keep-design; source signed stopping, global missing-data sample variance and direct likelihood underflow. Exact sample/feature order and first-occurrence batch factor levels are retained. Unadjusted rows remain original; their effect entries are NOT_ESTIMATED. Original undefined outputs are retained as failed evidence, never repaired/admitted. Numerical conditioning/capacity guards may refuse inputs the R implementation can fit. No ComBat_seq, longitudinal mixed model, SVA factor estimation, author recipe or whole-paper qualification.";
    private static final String ACTION="Require analysis-status.json result_admitted=true, inspect WARNINGS.md and perform fresh --verify into NEW output. SKIP3: dependent analysis skipped; restore the named permitted input/packaged prerequisite or allow explicit execution, then retry NEW paths. FAIL2: no partial result admitted; retain original/failed evidence, fix the named data/design, nonfinite, convergence, integrity, cancellation or capacity cause and retry NEW paths. This native command installs nothing and needs no R runtime. Never reinterpret counts/VST/z-scores or substitute continuous ComBat for a differently named model.";
    private static final Set<String> PATHS=Set.of("matrix","batch","design");
    private static final Set<String> KEYS=Set.of("schema_version","source_version","operation","units","matrix","batch","design","par_prior","mean_only","reference_batch","max_iterations","max_input_bytes","max_entries","max_work","max_seconds","execution_policy","population","preparation","out");
    private static final Set<String> RESULTS=Set.of("adjusted.tsv","gamma.tsv","delta.tsv","features.tsv","results.json");
    private record Matrix(List<String> ids,List<String> columns,double[][] values) { }
    private static final class Skipped extends IOException {
        private static final long serialVersionUID=1L;
        Skipped(String message){super(message);}
    }
    private static final class Clock implements Runnable {
        private final long started=System.nanoTime(),seconds;
        Clock(long seconds){this.seconds=seconds;}
        public void run(){require(!Thread.currentThread().isInterrupted(),"Analysis cancelled; no result admitted");require((System.nanoTime()-started)/1e9<=seconds,"Complete analysis time capacity exceeded");}
    }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    public static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        Path out=null;boolean owned=false,admitted=false;int code=2;String status="FAIL2: Invalid request";
        try {
            if(Arrays.equals(args,new String[]{"--help"})){stdout.println("combat-native --request UTF8_JSON\ncombat-native --verify COMPLETE_BUNDLE --out NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION+"\nSchema/example: docs/combat-native.md");return 0;}
            if(args.length==4&&args[0].equals("--verify")&&args[2].equals("--out")) {
                Path saved=absolute(args[1]);directory(saved);out=absolute(args[3]);newOutput(out,List.of(saved));owned=true;
                var state=object(json(saved.resolve("analysis-status.json"),1048576));
                require(PASS.equals(state.get("status"))&&Boolean.TRUE.equals(state.get("result_admitted"))&&integer(state.get("exit_code"),0,0)==0,"Saved capture is failed, skipped or incomplete");
                var manifest=object(json(saved.resolve("manifest.json"),4194304));var producer=producer();require(producer.equals(manifest.get("producer")),"Saved/current defining code or source profile differs");
                require(Arrays.equals(profile(),bytes(saved.resolve("SOURCE.json"),1048576)),"Saved source profile differs from the packaged original-source profile");
                var initial=tree(saved,128,4294967296L,2147483646L,()->{});var names=new TreeSet<>(initial.keySet());names.removeAll(Set.of("manifest.json","analysis-status.json"));var wanted=object(manifest.get("files"));require(names.equals(wanted.keySet()),"Saved member population differs");
                for(String name:names)require(initial.get(name).kind().equals("file")&&initial.get(name).pin().equals(fingerprint(wanted.get(name),2147483646L)),"Saved member changed: "+name);
                Map<?,?> original=object(NextflowParameters.parse(utf8(bytes(saved.resolve("request.json"),1048576)),1048576,100000));validate(original);
                var parameters=parameters(original);require(NextflowJson.json(parameters).equals(NextflowJson.json(object(json(saved.resolve("parameters.json"),1048576)))),"Saved effective parameters differ from literal request");
                var clock=new Clock(integer(parameters.get("max_seconds"),1,86400));source(out);analyze(parameters,captured(saved,parameters),out,clock);
                for(String name:RESULTS)require(pin(out.resolve(name),2147483646L,clock).equals(pin(saved.resolve(name),2147483646L,clock)),"Full saved/fresh native result differs: "+name);
                require(initial.equals(tree(saved,128,4294967296L,2147483646L,clock)),"Saved evidence changed during recomputation");
                writeJson(out.resolve("verification.json"),Map.of("status","PASS_COMPLETE_SAVED_INPUT_RECOMPUTATION","saved_manifest",pin(saved.resolve("manifest.json"),4194304,clock),"producer",producer,"producer_authenticated",false));
                status="PASS_COMPLETE_SAVED_INPUT_RECOMPUTATION";admitted=true;code=0;
            } else {
                require(args.length==2&&args[0].equals("--request"),"Require --request JSON or --verify BUNDLE --out NEW_DIRECTORY");
                Path request=absolute(args[1]);var requestPin=pin(request,1048576,()->{});byte[] raw=bytes(request,1048576);require(requestPin.equals(new Pin(raw.length,sha(raw))),"Request changed during capture");
                Map<?,?> p=object(NextflowParameters.parse(utf8(raw),1048576,100000));require(p.keySet().equals(KEYS),"Unexpected/missing request fields");
                out=resolve(request.getParent(),text(p.get("out")));newOutput(out,List.of(request));owned=true;Files.write(out.resolve("request.json"),raw,StandardOpenOption.CREATE_NEW);validate(p);
                if(text(p.get("execution_policy")).equals("FORBID"))throw new Skipped("Native execution explicitly FORBID; dependent analysis skipped");
                var clock=new Clock(integer(p.get("max_seconds"),1,86400));var paths=new TreeMap<String,Path>();var pins=new TreeMap<String,Pin>();long total=0,maximum=integer(p.get("max_input_bytes"),1,2147483646L);Files.createDirectory(out.resolve("inputs"));
                for(String name:new TreeSet<>(PATHS)) {
                    if(p.get(name)==null)continue;Path path=resolve(request.getParent(),text(p.get(name)));require(!out.startsWith(path)&&!path.startsWith(out),"Output overlaps an input");
                    if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new Skipped("Required permitted "+name+" input is missing: "+path);
                    var identity=pin(path,maximum,clock);total=Math.addExact(total,identity.bytes());require(total<=maximum,"Complete input byte capacity exceeded");Path target=out.resolve("inputs/"+name+".tsv");Files.copy(path,target);require(identity.equals(pin(target,maximum,clock)),"Input changed during capture");paths.put(name,path);pins.put(name,identity);
                }
                var parameters=parameters(p);writeJson(out.resolve("parameters.json"),parameters);var provenance=new TreeMap<String,Object>();for(String name:paths.keySet())provenance.put(name,Map.of("source",paths.get(name).toString(),"identity",pins.get(name)));writeJson(out.resolve("input-provenance.json"),provenance);
                var producer=producer();source(out);analyze(parameters,captured(out,parameters),out,clock);
                for(String name:paths.keySet())require(pins.get(name).equals(pin(paths.get(name),maximum,clock)),"Original input changed during analysis");require(requestPin.equals(pin(request,1048576,clock)),"Request changed during analysis");require(producer.equals(producer()),"Defining code/profile changed during analysis");
                writeWarnings(out,PASS);var files=new TreeMap<String,Object>();for(var entry:tree(out,128,4294967296L,2147483646L,clock).entrySet()){require(entry.getValue().kind().equals("file"),"Unexpected output member");files.put(entry.getKey(),entry.getValue().pin());}
                writeJson(out.resolve("manifest.json"),Map.of("schema_version",1,"producer",producer,"files",files,"producer_authenticated",false,"java_runtime",System.getProperty("java.runtime.version"),"os",System.getProperty("os.name")));status=PASS;admitted=true;code=0;
            }
        }catch(Skipped e){code=3;status="SKIP3: "+e.getMessage();}
        catch(LinkageError e){code=3;status="SKIP3: Missing/incompatible packaged Java prerequisite: "+e;}
        catch(OutOfMemoryError e){code=2;status="FAIL2: Java heap exhausted; no partial result admitted";}
        catch(Exception e){code=2;status="FAIL2: "+e;}
        try {
            if(owned){if(!Files.exists(out.resolve("WARNINGS.md")))writeWarnings(out,status);else if(code!=0)Files.writeString(out.resolve("WARNINGS.md"),"\n\n**"+status+"**\nNo partial result admitted.\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);writeJson(out.resolve("analysis-status.json"),Map.of("status",status,"exit_code",code,"result_admitted",admitted&&code==0,"required_action",ACTION,"whole_package_qualified",false,"whole_goal_complete",false));}
        }catch(IOException e){code=2;status="FAIL2: Cannot retain complete status/warnings: "+e;}
        (code==0?stdout:stderr).println(status+(out==null?"":"\nOutput: "+out));stderr.println("WARNINGS / "+ACTION);return code;
    }
    private static void analyze(Map<?,?> p,Map<String,Path> paths,Path out,Clock clock)throws IOException {
        long maximum=integer(p.get("max_entries"),1,100000000);Matrix data=matrix(paths.get("matrix"),"feature_id",maximum,true,clock);long entries=Math.multiplyExact((long)data.ids().size(),data.columns().size());
        var rows=table(paths.get("batch"),clock);require(rows.size()==data.columns().size()+1&&Arrays.equals(rows.get(0),new String[]{"sample_id","batch"}),"Complete batch header/population required");String[] batch=new String[data.columns().size()];
        for(int i=0;i<batch.length;i++){require(rows.get(i+1).length==2&&rows.get(i+1)[0].equals(data.columns().get(i)),"Batch sample IDs/order differ; no alignment or intersection");batch[i]=rows.get(i+1)[1];require(!batch[i].isBlank()&&batch[i].chars().noneMatch(c->c<32||c==127),"Invalid literal batch label");}
        double[][] design=null;int columns=0;
        if(paths.containsKey("design")){Matrix d=matrix(paths.get("design"),"sample_id",maximum-entries,false,clock);require(d.ids().equals(data.columns()),"Design sample IDs/order differ");design=d.values();columns=d.columns().size();entries+=Math.multiplyExact((long)d.ids().size(),columns);}
        long features=data.ids().size(),samples=data.columns().size();require(features>=2&&samples>=3,"Require at least two features and three samples");int batches=new LinkedHashSet<>(List.of(batch)).size();long iterations=integer(p.get("max_iterations"),1,1000000);
        long work=bool(p.get("par_prior"))?Math.multiplyExact(Math.multiplyExact(features,samples),Math.addExact(Math.multiplyExact((long)batches+columns,(long)batches+columns),iterations)):Math.multiplyExact(Math.multiplyExact(features,features),samples);require(work<=integer(p.get("max_work"),1,9007199254740991L),"Declared complete numerical work capacity exceeded");
        ComBat.Options options=new ComBat.Options(bool(p.get("par_prior")),bool(p.get("mean_only")),p.get("reference_batch")==null?null:text(p.get("reference_batch")),1e-4,(int)iterations);clock.run();ComBat.Result fit=ComBat.adjustSva346(data.values(),batch,design,options,clock);
        var excluded=new HashSet<Integer>();for(int f:fit.unadjustedFeatures())excluded.add(f);writeMatrix(out.resolve("adjusted.tsv"),"feature_id",data.ids(),data.columns(),fit.adjusted(),null,data.values(),clock);writeMatrix(out.resolve("gamma.tsv"),"batch",fit.batches(),data.ids(),fit.gamma(),excluded,null,clock);writeMatrix(out.resolve("delta.tsv"),"batch",fit.batches(),data.ids(),fit.delta(),excluded,null,clock);
        StringBuilder status=new StringBuilder("feature_id\tstatus\n");for(int f=0;f<data.ids().size();f++)status.append(data.ids().get(f)).append('\t').append(excluded.contains(f)?"UNADJUSTED_ORIGINAL":"ADJUSTED").append('\n');Files.writeString(out.resolve("features.tsv"),status,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        long missing=0,undefined=0;for(int f=0;f<features;f++)for(int s=0;s<samples;s++){if(Double.isNaN(data.values()[f][s])){missing++;require(Double.isNaN(fit.adjusted()[f][s]),"Original missing value changed");}else if(!Double.isFinite(fit.adjusted()[f][s]))undefined++;}
        writeJson(out.resolve("results.json"),Map.of("profile","sva-3.46.0-continuous-ComBat","features",features,"samples",samples,"batches",fit.batches(),"effective_mean_only",fit.meanOnly(),"missing_values_retained",missing,"undefined_observed_values",undefined,"unadjusted_features",excluded.stream().sorted().toList(),"maximum_work_bound",work,"result_admitted",undefined==0));clock.run();require(undefined==0,"Original-rule fit produced "+undefined+" undefined/nonfinite observed values; raw outputs retained, no result admitted");
    }
    private static Matrix matrix(Path path,String header,long maximum,boolean missing,Clock clock)throws IOException {
        var records=table(path,clock);require(!records.isEmpty()&&records.get(0).length>=2&&records.get(0)[0].equals(header),"Require literal "+header+" TSV matrix");List<String> columns=List.of(Arrays.copyOfRange(records.get(0),1,records.get(0).length));unique(columns);var ids=new ArrayList<String>();require(Math.multiplyExact((long)records.size()-1,columns.size())<=maximum,"Complete matrix entry capacity exceeded");double[][] values=new double[records.size()-1][columns.size()];
        for(int i=1;i<records.size();i++){clock.run();String[] row=records.get(i);require(row.length==columns.size()+1,"Ragged/blank matrix record");ids.add(row[0]);for(int j=0;j<columns.size();j++){String token=row[j+1];if(missing&&token.equals("NA"))values[i-1][j]=Double.NaN;else{require(token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Only finite numeric literals or explicit NA missing cells are allowed");values[i-1][j]=Double.parseDouble(token);require(Double.isFinite(values[i-1][j]),"Nonfinite numeric input");}}}unique(ids);return new Matrix(List.copyOf(ids),columns,values);
    }
    private static List<String[]> table(Path path,Clock clock)throws IOException {var rows=new ArrayList<String[]>();for(String line:utf8(bytes(path,2147483646)).lines().toList()){clock.run();rows.add(line.split("\t",-1));}return rows;}
    private static void writeMatrix(Path path,String header,List<String> ids,List<String> columns,double[][] data,Set<Integer> notEstimated,double[][] missingMask,Clock clock)throws IOException {
        try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){out.write(header);for(String column:columns){out.write('\t');out.write(column);}out.write('\n');for(int i=0;i<ids.size();i++){clock.run();out.write(ids.get(i));for(int j=0;j<columns.size();j++){out.write('\t');out.write(notEstimated!=null&&notEstimated.contains(j)?"NOT_ESTIMATED":missingMask!=null&&Double.isNaN(missingMask[i][j])?"NA":token(data[i][j]));}out.write('\n');}}
    }
    private static Map<String,Object> parameters(Map<?,?> p)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:p.entrySet())if(!PATHS.contains(e.getKey())&&!e.getKey().equals("out"))result.put(text(e.getKey()),e.getValue());result.put("input_names",PATHS.stream().filter(k->p.get(k)!=null).sorted().toList());return result;}
    private static Map<String,Path> captured(Path root,Map<?,?> p)throws IOException {var files=new TreeMap<String,Path>();for(Object value:list(p.get("input_names"))){String key=text(value);require(PATHS.contains(key)&&!files.containsKey(key),"Unknown/repeated captured input");files.put(key,root.resolve("inputs/"+key+".tsv"));}require(files.containsKey("matrix")&&files.containsKey("batch"),"Missing captured matrix/batch");return files;}
    private static void validate(Map<?,?> p)throws IOException {
        require(p.keySet().equals(KEYS),"Unexpected/missing request fields");require(integer(p.get("schema_version"),1,1)==1,"Schema 1 required");if(!"3.46.0".equals(text(p.get("source_version")))||!"ComBat".equals(text(p.get("operation"))))throw new Skipped("Requested source edition/model is not implemented by this native continuous ComBat profile");
        require(Set.of("continuous-values","log2-expression","VST-expression","methylation-M").contains(text(p.get("units"))),"Declare prepared continuous units; count and longitudinal models are separate");for(String key:List.of("matrix","batch","population","preparation","out"))require(!text(p.get(key)).isBlank(),"Explicit nonempty "+key+" required");if(p.get("design")!=null)require(!text(p.get("design")).isBlank(),"Design path is blank");if(p.get("reference_batch")!=null)require(!text(p.get("reference_batch")).isBlank(),"Reference batch is blank");bool(p.get("par_prior"));bool(p.get("mean_only"));integer(p.get("max_iterations"),1,1000000);integer(p.get("max_input_bytes"),1,2147483646L);integer(p.get("max_entries"),1,100000000);integer(p.get("max_work"),1,9007199254740991L);integer(p.get("max_seconds"),1,86400);require(Set.of("ALLOW","FORBID").contains(text(p.get("execution_policy"))),"Execution policy ALLOW/FORBID required");
    }
    private static Map<String,String> producer()throws IOException {
        var result=new TreeMap<String,String>();for(Class<?> type:List.of(ComBatNativeCli.class,ComBat.class,NextflowFiles.class,NextflowParameters.class,NextflowJson.class,SimpleJson.class))classes(type,result);
        try{classes(Class.forName("org.jlinalg.confounding.ConfounderMath",false,ComBat.class.getClassLoader()),result);}catch(ClassNotFoundException e){throw new Skipped("Packaged numerical helper is missing");}result.put("source_profile",sha(profile()));return result;
    }
    private static void classes(Class<?> type,Map<String,String> result)throws IOException {String name=type.getName().replace('.','/')+".class";try(var in=type.getResourceAsStream("/"+name)){if(in==null)throw new Skipped("Defining Java class is missing: "+name);result.put(name,sha(in.readAllBytes()));}for(Class<?> nested:type.getDeclaredClasses())classes(nested,result);}
    private static byte[] profile()throws IOException {try(var in=ComBatNativeCli.class.getResourceAsStream(RESOURCE)){if(in==null)throw new Skipped("Packaged original-source profile is missing");byte[] raw=in.readAllBytes();var profile=object(SimpleJson.parse(utf8(raw)));require("sva-3.46.0-continuous-ComBat".equals(profile.get("profile")),"Invalid original-source profile");return raw;}}
    private static void source(Path root)throws IOException {Files.write(root.resolve("SOURCE.json"),profile(),StandardOpenOption.CREATE_NEW);}
    private static boolean bool(Object value)throws IOException {if(!(value instanceof Boolean b))throw new IOException("Explicit Boolean required");return b;}
    private static void unique(List<String> ids){require(!ids.isEmpty()&&ids.stream().noneMatch(s->s.isBlank()||s.chars().anyMatch(c->c<32||c==127))&&new HashSet<>(ids).size()==ids.size(),"Empty/control/duplicate literal identifier");}
    private static String token(double value){return Double.isNaN(value)?"NaN":value==Double.POSITIVE_INFINITY?"Inf":value==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(value);}
    private static Path absolute(String path){return Path.of(path).toAbsolutePath().normalize();}
    private static Path resolve(Path parent,String path){return parent.resolve(path).toAbsolutePath().normalize();}
    private static void newOutput(Path out,List<Path> protectedPaths)throws IOException {require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS),"Output must be NEW; preserve prior attempts");directory(out.getParent());for(Path path:protectedPaths)require(!out.startsWith(path)&&!path.startsWith(out),"Output overlaps original evidence");Files.createDirectory(out);}
    private static void writeWarnings(Path out,String status)throws IOException {Files.writeString(out.resolve("WARNINGS.md"),"# "+status+"\n\n"+SCOPE+"\n\n**Required actions:** "+ACTION+"\n\nThe original iteration tolerance is fixed at 1e-4. max_iterations, time, heap and declared work/input limits are caller capacity guards, not author settings or OS-enforced quotas. Missing cells must be explicit NA and remain missing; numeric NaN/Inf inputs are refused. New NaN/Inf at observed cells makes the entire analysis FAIL2. Derived R NA and NaN share Java Double.NaN and export as NaN; the supplied NA mask remains NA. Gamma/delta for excluded features are NOT_ESTIMATED, not fitted zeros/ones. VERIFY checks every saved byte and freshly recomputes all native result files; hashes detect inconsistency but do not authenticate coordinated replacement. No P086 count workflow, P128 longitudinal model or P185 surrogate-variable procedure is inferred.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
