/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jlinalg.genetics.FavorCsvJoin;
import org.jlinalg.genetics.FavorCsvReader;
import org.jlinalg.genetics.FavorVcfAudit;
import org.jlinalg.genetics.FavorVcfReader;

/** Complete internal offline FAVOR parent; scientific admission is repeated for each run. */
final class FavorAnnotatorWorkflow {
    record Input(Path path,String sha256) {
        Input {Objects.requireNonNull(path,"path");if(sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Require protected input SHA256");}
    }
    record RuntimeInput(Input inventory,Path sourceRoot) {RuntimeInput {Objects.requireNonNull(inventory,"inventory");Objects.requireNonNull(sourceRoot,"sourceRoot");}}
    record Reference(String identity,String build,Input split,List<Input> shards) {
        Reference {token(identity);token(build);Objects.requireNonNull(split,"split");shards=List.copyOf(shards);if(shards.size()<2||shards.size()>4096)throw new IllegalArgumentException("Require2..4096 declared selected-chromosome shards");}
    }
    record Limits(FavorRuntimeFingerprint.Limits runtime,FavorVcfReader.Limits vcf,FavorCsvReader.Limits csv,
            FavorCsvJoin.Limits join,FavorGdsAudit.Limits gds,FavorAnnotationAudit.Limits scalar,
            int timeoutSeconds,long xsvTimeoutMillis,long workBytes,long upstreamRssBytes,int chunkRows) {
        Limits {Objects.requireNonNull(runtime);Objects.requireNonNull(vcf);Objects.requireNonNull(csv);Objects.requireNonNull(join);Objects.requireNonNull(gds);Objects.requireNonNull(scalar);if(timeoutSeconds<1||timeoutSeconds>86400||xsvTimeoutMillis<1||xsvTimeoutMillis>3600000||workBytes<1||upstreamRssBytes<1||chunkRows<1||chunkRows>1000000)throw new IllegalArgumentException("Invalid complete FAVOR process/work/RSS/chunk capacities");}
        static Limits defaults(){return new Limits(new FavorRuntimeFingerprint.Limits(67108864,100000,2048,4096,1073741824,21474836480L),FavorVcfReader.Limits.defaults(),FavorCsvReader.Limits.defaults(),FavorCsvJoin.Limits.defaults(),new FavorGdsAudit.Limits(33554432,10000,1000000000,4294967296L),new FavorAnnotationAudit.Limits(33554432,1024,1000000,1000000000,1048576,4294967296L,65536,67108864,4294967296L),3600,600000,17179869184L,4294967296L,1024);}
    }
    record Request(RuntimeInput runtime,Input vcf,String build,int chromosome,FavorVcfReader.Mode mode,
            FavorVcfReader.Chromosomes chromosomes,FavorCsvReader.BlankRecords blankRecords,
            Reference reference,Path output,Limits limits,Input carrier) {
        Request(RuntimeInput runtime,Input vcf,String build,int chromosome,FavorVcfReader.Mode mode,FavorVcfReader.Chromosomes chromosomes,FavorCsvReader.BlankRecords blankRecords,Reference reference,Path output,Limits limits){this(runtime,vcf,build,chromosome,mode,chromosomes,blankRecords,reference,output,limits,null);}
        Request {Objects.requireNonNull(runtime);Objects.requireNonNull(vcf);token(build);if(chromosome<1||chromosome>22)throw new IllegalArgumentException("Require one selected autosome1..22");Objects.requireNonNull(mode);Objects.requireNonNull(chromosomes);Objects.requireNonNull(blankRecords);Objects.requireNonNull(reference);Objects.requireNonNull(output);Objects.requireNonNull(limits);if(!build.equals(reference.build()))throw new IllegalArgumentException("Query and FAVOR resource builds must match exactly;no liftover");}
    }
    record Outcome(int exitCode,String status,Path session,Path warnings,Path values,Path cellStates) { }
    private record Captured(Path original,Path staged,long bytes,String sha256) {
        void verify(Runnable check)throws IOException {check.run();for(Path p:List.of(original,staged))if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!p.toRealPath().equals(p)||Files.size(p)!=bytes||!FollowupSupport.hash(p).equals(sha256))throw new IOException("Protected FAVOR original/snapshot changed: "+p);}
    }
    private record Software(FavorRuntimeFingerprint.Checked fingerprint,List<Captured> files,List<Path> classpath,Path convert,Path annotate,Path gdsAudit,Path scalarAudit) {
        Software {files=List.copyOf(files);classpath=List.copyOf(classpath);}
        void verify(Runnable check)throws IOException {for(var file:files)file.verify(check);}
    }
    private record Job(FavorAnnotationAudit.Export export,FavorXsvLedger.Ledger ledger,FavorGdsAudit.Preservation preservation,List<Captured> inputs,FavorAnnotationAudit.Prepared scalars,List<String> warnings) {
        Job {inputs=List.copyOf(inputs);warnings=List.copyOf(warnings);}
        void verify(Runnable check)throws IOException {for(var input:inputs)input.verify(check);scalars.verify(check);ledger.verify(check);}
    }
    private record PreparedJob(Path work,int chromosome,FavorVcfReader.Mode mode,FavorCsvReader.BlankRecords blanks,String vcfName,List<Split> selected,FavorVcfAudit.Result expected,List<FavorCsvJoin.Variant> variants,List<String> headers,FavorCsvJoin.Result joined,List<Captured> inputs) {
        PreparedJob {selected=List.copyOf(selected);variants=List.copyOf(variants);headers=List.copyOf(headers);inputs=List.copyOf(inputs);}
        void verify(Runnable check)throws IOException {for(var input:inputs)input.verify(check);}
    }
    private record Split(int chromosome,int start,int end) { }
    private static final String HEALTH_VALUES="543463e383d6cd3e8ee1d6d81feba8d6dc534ff417aa6bcbdb249e47af1f7d0d";
    private static final String HEALTH_STATES="a886659a392c87e3d11cad0628495ad7c8db638fbb1876ff8e37ae7fa570e7d5";
    private FavorAnnotatorWorkflow() { }

    static Outcome run(Request request,Runnable cancellation)throws IOException {
        Objects.requireNonNull(cancellation);Path root=newOutput(request.output());var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
        session.put("schema","favor-offline-parent-v1");session.put("status","started");session.put("started_at",Instant.now().toString());session.put("query_build",request.build());session.put("reference_identity",request.reference().identity());session.put("reference_build",request.reference().build());session.put("chromosome",request.chromosome());session.put("mode",request.mode().name());session.put("chromosome_policy",request.chromosomes().name());session.put("blank_record_policy",request.blankRecords().name());session.put("historical_paper_pipeline_certified",false);session.put("effective_limits",limitMap(request.limits()));
        warnings.add("ORIGINAL EXPORT FAILURE: the tested original full-table exporter fails on its restored zero-row frame metadata. Required action: use the audited scalar export and cell-state/canonical bundle;do not claim the original text exporter succeeded. Original GDS nodes are never repaired.");
        warnings.add("REFERENCE BUILD: the query and annotation builds are explicitly declared and must agree. Required action: verify that the supplied resource provenance and allele representation suit the study;this workflow performs no liftover or allele normalization.");
        int code=2;Path values=null,states=null;
        try {
            json(root.resolve("session.json"),session,false);softwareWarning(root,"STARTED - RESULTS ARE PROVISIONAL",warnings);
            Captured carrier=request.carrier()==null?null:copy(request.carrier(),root.resolve("run-request.json"),1048576,root,request.limits(),cancellation);if(carrier!=null)session.put("request_sha256",carrier.sha256());
            Software software=software(request.runtime(),root,request.limits(),cancellation);session.put("runtime_inventory_sha256",software.fingerprint().sha256());session.put("runtime_policy_sha256",software.fingerprint().policySha256());
            PreparedJob analysis=prepare(request.vcf(),request.reference(),request.chromosome(),request.mode(),request.chromosomes(),request.blankRecords(),root.resolve("analysis"),request.limits(),cancellation);session.put("native_input_admitted",true);replaceJson(root.resolve("session.json"),session);
            Path fixtures=Files.createDirectory(root.resolve("health-inputs"));Reference health=healthInputs(fixtures);Input healthVcf=input(fixtures.resolve("input.vcf"));
            var healthJobs=new ArrayList<Job>();var healthRecords=new ArrayList<Map<String,Object>>();
            for(var mode:FavorVcfReader.Mode.values()) {
                Input vcf=healthVcf;
                if(mode==FavorVcfReader.Mode.VARIANTS_ONLY){Path p=fixtures.resolve("variants.vcf");var lines=new ArrayList<String>();for(String line:Files.readAllLines(healthVcf.path(),StandardCharsets.UTF_8)){if(line.startsWith("##FORMAT="))continue;lines.add(line.startsWith("##")?line:String.join("\t",List.of(line.split("\t",-1)).subList(0,8)));}Files.writeString(p,String.join("\n",lines)+"\n",StandardOpenOption.CREATE_NEW);vcf=input(p);}
                Job job=one(software,prepare(vcf,health,22,mode,FavorVcfReader.Chromosomes.EXACT,FavorCsvReader.BlankRecords.IGNORE,root.resolve("health-"+mode.name().toLowerCase(java.util.Locale.ROOT).replace('_','-')),request.limits(),cancellation),request.limits(),session,cancellation);
                if(job.export().rows()!=8||job.export().values()!=48||!job.export().valuesSha256().equals(HEALTH_VALUES)||!job.export().statesSha256().equals(HEALTH_STATES)||job.preservation().genotypeAlleles()!=(mode==FavorVcfReader.Mode.DIPLOID_GT?48:0)||job.preservation().phaseValues()!=(mode==FavorVcfReader.Mode.DIPLOID_GT?24:0))throw new IOException("FAVOR scientific health fixture differs from frozen original/native scalar,genotype or phase expectations");
                healthJobs.add(job);healthRecords.add(Map.of("mode",mode.name(),"rows",8,"scalar_values",48,"values_sha256",job.export().valuesSha256(),"cell_states_sha256",job.export().statesSha256(),"nested_commands",job.ledger().events().size()));
            }
            software.verify(cancellation);software.fingerprint().verify(cancellation);for(var job:healthJobs)job.verify(cancellation);
            json(root.resolve("health-admission.json"),Map.of("status","passed-fresh-scientific-runtime-health","runtime_inventory_sha256",software.fingerprint().sha256(),"runtime_policy_sha256",software.fingerprint().policySha256(),"profiles",healthRecords,"scope","Same pinned runtime,source scripts,staged supervisor code and audit helpers;fresh fixed synthetic fixture only;not a claim of whole-tool or historical-paper parity."),true);
            session.put("scientific_health_admitted",true);
            if(carrier!=null)carrier.verify(cancellation);
            session.put("analysis_execution_started",true);replaceJson(root.resolve("session.json"),session);
            Job result=one(software,analysis,request.limits(),session,cancellation);
            warnings.addAll(result.warnings());result.verify(cancellation);software.verify(cancellation);software.fingerprint().verify(cancellation);
            for(var job:healthJobs)job.verify(cancellation);HomerRuntime.bound(root,request.limits().workBytes());
            if(carrier!=null)carrier.verify(cancellation);
            values=result.export().valuesTsv();states=result.export().statesTsv();session.put("values",root.relativize(values).toString().replace('\\','/'));session.put("cell_states",root.relativize(states).toString().replace('\\','/'));session.put("values_sha256",result.export().valuesSha256());session.put("cell_states_sha256",result.export().statesSha256());session.put("nested_commands",result.ledger().events().size());session.put("nested_ledger_sha256",result.ledger().sha256());session.put("rows",result.export().rows());session.put("scalar_values",result.export().values());code=0;
        } catch(FavorRuntimeFingerprint.Unavailable unavailable) {
            code=3;session.put("cause",unavailable.toString());warnings.add("RUNTIME UNAVAILABLE - ANALYSIS SKIPPED: "+unavailable.getMessage());
        } catch(HomerRuntime.Unavailable unavailable) {
            code=unavailableExit(Boolean.TRUE.equals(session.get("analysis_execution_started")),unavailable);session.put("cause",unavailable.toString());
            warnings.add((code==3?"RUNTIME OR REQUIRED MONITOR UNAVAILABLE - ANALYSIS SKIPPED: ":"ANALYSIS FAILED AFTER EXECUTION STARTED: ")+unavailable.getMessage()+". Required action: use a permitted Linux runtime where the pinned executable and required owned-process monitoring are available;preserve this bundle and rerun to a NEW output. No analysis installs software or bypasses operating-environment restrictions.");
        } catch(Exception failure) {
            session.put("cause",failure.toString());for(var suppressed:failure.getSuppressed())warnings.add("CLEANUP OR SECONDARY FAILURE: "+suppressed);
            warnings.add("ANALYSIS FAILED: "+failure.getMessage()+". Required action: retain this output and original logs,correct the named input,capacity,execution,health or validation cause,and rerun to a NEW output directory. Partial scalar exports are not completed analyses.");
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
        }
        String status=code==0?"complete-audited-offline-analysis":code==3?"skipped-runtime-unavailable":"failed";
        session.put("status",status);session.put("exit_code",code);session.put("finished_at",Instant.now().toString());session.put("warnings",warnings);session.put("required_action",code==0?"Keep this entire bundle,including raw joined CSV,unaltered converted GDS,annotated working GDS,canonical values,cell states,health and process ledgers. Interpret missing annotations and source class losses using WARNINGS.md and scalar export provenance.":"Inspect WARNINGS.md,session.json and original per-process stdout/stderr;preserve this failed/skipped bundle and rerun to new paths after correcting the named cause.");
        softwareWarning(root,status.toUpperCase(java.util.Locale.ROOT),warnings);replaceJson(root.resolve("session.json"),session);return new Outcome(code,status,root.resolve("session.json"),root.resolve("WARNINGS.md"),code==0?values:null,code==0?states:null);
    }

    private static Software software(RuntimeInput requested,Path root,Limits limits,Runnable check)throws Exception {
        var fingerprint=FavorRuntimeFingerprint.inspect(requested.inventory().path(),requested.inventory().sha256(),limits.runtime(),check);Path directory=Files.createDirectory(root.resolve("software"));var files=new ArrayList<Captured>();
        Map<?,?> policy;try(var in=FavorAnnotatorWorkflow.class.getResourceAsStream("/org/jlinalg/omics/favor-runtime-policy.json")){if(in==null)throw new IOException("Missing bundled FAVOR source policy");policy=AdmixtureInputs.decode(in.readAllBytes());}Map<?,?> hashes=(Map<?,?>)policy.get("original_source_sha256");
        Path convert=directory.resolve("convertVCFtoGDS.r"),annotate=directory.resolve("FAVORannotatorv2aGDS.r");
        try {for(String name:List.of("Scripts/CSV/convertVCFtoGDS.r","Scripts/CSV/FAVORannotatorv2aGDS.r"))files.add(copy(new Input(requested.sourceRoot().resolve(name),(String)hashes.get(name)),directory.resolve(Path.of(name).getFileName()),4194304,root,limits,check));}
        catch(IOException failure){throw new FavorRuntimeFingerprint.Unavailable("Pinned permitted FAVOR source scripts are missing or changed. Required action: provide the unmodified pinned source checkout;skip analysis until source admission succeeds. "+failure.getMessage(),failure);}
        Path gds=directory.resolve("favor_gds_audit.R"),scalar=directory.resolve("favor_annotation_audit.R");for(Path p:List.of(gds,scalar)){byte[] bytes=resource("/org/jlinalg/cli/"+p.getFileName());Files.write(p,bytes,StandardOpenOption.CREATE_NEW);files.add(new Captured(p,p,bytes.length,AdmixtureInputs.digest(bytes)));}
        var classpath=new ArrayList<Path>();var locations=new java.util.LinkedHashSet<Path>();for(Class<?> type:List.of(FavorAnnotatorWorkflow.class,FavorXsvSupervisor.class,AdmixtureInputs.class))locations.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
        int index=0;for(Path source:locations){Path staged=directory.resolve("code-"+(++index)+(Files.isDirectory(source)?"":".jar"));classpath.add(staged);if(Files.isDirectory(source)){Files.createDirectory(staged);try(var stream=Files.walk(source)){for(Path member:stream.sorted().toList()){check.run();if(member.equals(source))continue;Path target=staged.resolve(source.relativize(member));if(Files.isDirectory(member,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(target);else files.add(copy(input(member),target,limits.workBytes(),root,limits,check));}}}else files.add(copy(input(source),staged,limits.workBytes(),root,limits,check));}
        var software=new Software(fingerprint,files,classpath,convert,annotate,gds,scalar);software.verify(check);return software;
    }
    private static PreparedJob prepare(Input vcf,Reference reference,int chromosome,FavorVcfReader.Mode mode,FavorVcfReader.Chromosomes chromosomes,FavorCsvReader.BlankRecords blanks,Path supplied,Limits limits,Runnable check)throws Exception {
        Path work=Files.createDirectory(supplied);Path parentRoot=work.getParent();var inputs=new ArrayList<Captured>();Path database=Files.createDirectory(work.resolve("database"));Files.createDirectory(work.resolve("output"));Files.createDirectory(work.resolve("empty-library"));
        String vcfName;try(var stream=Files.newInputStream(vcf.path())){byte[] magic=stream.readNBytes(2);vcfName=magic.length==2&&(magic[0]&255)==31&&(magic[1]&255)==139?"input.vcf.gz":"input.vcf";}
        Captured stagedVcf=copy(vcf,work.resolve(vcfName),limits.vcf().maximumBytes(),parentRoot,limits,check);inputs.add(stagedVcf);Captured split=copy(reference.split(),work.resolve("splits.csv"),limits.csv().maximumBytes(),parentRoot,limits,check);inputs.add(split);var selected=splits(split.staged(),chromosome,limits.csv(),blanks,check);if(selected.size()!=reference.shards().size())throw new IOException("Selected original DBsplit population differs from the declared ordered shard files");FavorXsvSupervisor.plan(chromosome,selected.size());
        var options=new FavorVcfReader.Options(mode,chromosome,chromosomes,limits.vcf());var expected=FavorVcfAudit.scan(stagedVcf.staged(),options,check);var variants=new ArrayList<FavorCsvJoin.Variant>();try(var reader=FavorVcfReader.open(stagedVcf.staged(),options,check)){FavorVcfReader.Row row;while((row=reader.next())!=null){if(variants.size()>=limits.join().maximumVariants())throw new IOException("Complete native FAVOR query capacity exceeded");variants.add(row.variant());}}
        if(variants.isEmpty()||variants.size()!=expected.variants())throw new IOException("Empty or inconsistent complete query population");var shards=new ArrayList<FavorCsvJoin.Shard>();List<String> headers=null;
        for(int i=0;i<selected.size();i++){Captured file=copy(reference.shards().get(i),database.resolve("chr"+chromosome+"_"+(i+1)+".csv"),limits.csv().maximumBytes(),parentRoot,limits,check);inputs.add(file);List<String> current;try(var reader=FavorCsvReader.open(file.staged(),limits.csv(),blanks,check)){current=reader.header();}if(headers==null)headers=current;else if(!headers.equals(current))throw new IOException("Declared FAVOR shard headers differ");Split bounds=selected.get(i);List<String> columns=current;shards.add(new FavorCsvJoin.Shard("shard-"+(i+1),chromosome,bounds.start(),bounds.end(),consumer->{try(var reader=FavorCsvReader.open(file.staged(),limits.csv(),blanks,check)){if(!reader.header().equals(columns))throw new IOException("FAVOR shard schema changed");List<String> row;while((row=reader.next())!=null)consumer.accept(row);if(!reader.report().complete())throw new IOException("Incomplete FAVOR shard scan");}}));}
        var joined=FavorCsvJoin.join(variants,new FavorCsvJoin.Reference(reference.identity(),reference.build(),headers,shards),new FavorCsvJoin.Options(reference.build(),chromosome,limits.join()),check);joinLedgers(work,joined,headers,check);if(!joined.exactlyOneRowPerVariant()||!joined.sourceOrderAligned())throw new IOException("Unsafe positional FAVOR attachment:source join drops,multiplies or reorders query rows;inspect complete native join ledgers. No upstream annotation was run for this input.");
        return new PreparedJob(work,chromosome,mode,blanks,vcfName,selected,expected,variants,headers,joined,inputs);
    }
    private static Job one(Software software,PreparedJob preparedJob,Limits limits,Map<String,Object> parent,Runnable check)throws Exception {
        preparedJob.verify(check);Path work=preparedJob.work(),parentRoot=work.getParent();int chromosome=preparedJob.chromosome();var mode=preparedJob.mode();var blanks=preparedJob.blanks();String vcfName=preparedJob.vcfName();var selected=preparedJob.selected();var expected=preparedJob.expected();var variants=preparedJob.variants();var headers=preparedJob.headers();var joined=preparedJob.joined();var inputs=new ArrayList<>(preparedJob.inputs());
        var supervisor=new LinkedHashMap<String,Object>();supervisor.put("schema_version",1);supervisor.put("work",work.toString());supervisor.put("xsv",software.fingerprint().xsv().toString());supervisor.put("chromosome",Integer.toString(chromosome));supervisor.put("shards",Integer.toString(selected.size()));supervisor.put("timeout_ms",Long.toString(limits.xsvTimeoutMillis()));Path request=work.resolve("supervisor.json");json(request,supervisor,true);
        var command=new ArrayList<String>(List.of(software.fingerprint().javaExecutable().toString(),"-Xmx256m","-cp",String.join(":",software.classpath().stream().map(Path::toString).toList()),FavorXsvSupervisor.class.getName(),"--request","./supervisor.json","--request-sha256",FollowupSupport.hash(request),"--"));
        String config="file_DBsplit <- \"splits.csv\"\noutput_path <- \"./output\"\nDB_path <- \"./database\"\nvcf.chr"+chromosome+".fn <- "+RegenieRuntime.json(vcfName)+"\ngds.chr"+chromosome+".fn <- \"input.gds\"\nxsv <- "+RegenieRuntime.json(String.join(" ",command.stream().map(FavorAnnotatorWorkflow::shellQuote).toList()))+"\n";Path configuration=work.resolve("config.R");Files.writeString(configuration,config,StandardOpenOption.CREATE_NEW);Captured protectedConfig=new Captured(configuration,configuration,Files.size(configuration),FollowupSupport.hash(configuration));
        execute(software,work,"convert",List.of(software.convert().toString(),Integer.toString(chromosome)),limits,parent,protectedConfig,check);Path gds=work.resolve("input.gds");auditGds(software,work,"before",gds,mode,limits,parent,protectedConfig,check);var before=FavorGdsAudit.read(work.resolve("before/audit.json"),limits.gds(),check);runtime(before.runtime());FavorGdsAudit.matches(expected,before);
        Path converted=work.resolve("converted.gds");Files.move(gds,converted,StandardCopyOption.ATOMIC_MOVE);Captured convertedCopy=copy(input(converted),gds,limits.workBytes(),parentRoot,limits,check);inputs.add(new Captured(converted,converted,convertedCopy.bytes(),convertedCopy.sha256()));
        Instant start=Instant.now();execute(software,work,"annotate",List.of(software.annotate().toString(),Integer.toString(chromosome)),limits,parent,protectedConfig,check);Instant finish=Instant.now();var ledger=FavorXsvLedger.read(work.resolve("xsv-events"),FollowupSupport.hash(request),software.fingerprint().xsv().toString(),chromosome,selected.size(),start,finish,new FavorXsvLedger.Limits(1048576,33554432,10000),check);
        Path csv=work.resolve("output/chr"+chromosome+"/Anno_chr"+chromosome+".csv");var fullHeaders=new ArrayList<String>();fullHeaders.add("VarInfo");fullHeaders.addAll(headers);compareJoined(csv,joined,fullHeaders,limits.csv(),blanks,check);
        auditGds(software,work,"after",gds,mode,limits,parent,protectedConfig,check);var after=FavorGdsAudit.read(work.resolve("after/audit.json"),limits.gds(),check);runtime(after.runtime());var preserved=FavorGdsAudit.preserved(expected,before,after);
        var arguments=List.of(software.scalarAudit().toString(),gds.toString(),csv.toString(),work.resolve("scalars").toString(),Long.toString(limits.csv().maximumBytes()),Integer.toString(limits.scalar().rows()),Integer.toString(limits.scalar().columns()),Long.toString(limits.scalar().cells()),Integer.toString(limits.scalar().scalarBytes()),Long.toString(limits.scalar().canonicalBytes()),Integer.toString(limits.chunkRows()));execute(software,work,"scalars",arguments,limits,parent,protectedConfig,check);
        var prepared=FavorAnnotationAudit.read(work.resolve("scalars/audit.json"),FollowupSupport.hash(csv),FollowupSupport.hash(gds),fullHeaders,variants.size(),limits.scalar(),check);runtime(prepared.runtime());var exported=FavorAnnotationAudit.export(prepared,variants.stream().map(FavorCsvJoin.Variant::key).toList(),work.resolve("export"),limits.scalar(),check);
        var warnings=new ArrayList<String>(joined.warnings());warnings.addAll(exported.warnings());protectedConfig.verify(check);software.verify(check);HomerRuntime.bound(parentRoot,limits.workBytes());
        Path valueAudit=work.resolve("value-audit.json");json(valueAudit,Map.of("status","values-audited-parent-completion-required","rows",variants.size(),"raw_joined_csv_sha256",FollowupSupport.hash(csv),"annotated_gds_sha256",FollowupSupport.hash(gds),"original_converted_gds_sha256",convertedCopy.sha256(),"nested_command_ledger_sha256",ledger.sha256(),"genotype_alleles",preserved.genotypeAlleles(),"phase_values",preserved.phaseValues(),"warnings",warnings),true);
        for(Path file:List.of(gds,csv,configuration,request,work.resolve("before/audit.json"),work.resolve("after/audit.json"),work.resolve("query-ledger.tsv"),work.resolve("shard-ledger.tsv"),work.resolve("native-join.tsv"),exported.valuesTsv(),exported.statesTsv(),exported.manifest(),exported.readme(),valueAudit))inputs.add(new Captured(file,file,Files.size(file),FollowupSupport.hash(file)));
        var job=new Job(exported,ledger,preserved,inputs,prepared,warnings);job.verify(check);return job;
    }
    private static void auditGds(Software software,Path work,String label,Path gds,FavorVcfReader.Mode mode,Limits limits,Map<String,Object> parent,Captured config,Runnable check)throws Exception {
        execute(software,work,label,List.of(software.gdsAudit().toString(),gds.toString(),work.resolve(label).toString(),mode.name(),Integer.toString(limits.chunkRows()),Integer.toString(Math.max(1,limits.vcf().maximumSamples())),Long.toString(limits.vcf().maximumVariants()),"10000000",Long.toString(limits.gds().nodeValues()),Long.toString(limits.gds().canonicalBytes()),Integer.toString(limits.gds().nodes())),limits,parent,config,check);
    }
    private static void execute(Software software,Path work,String label,List<String> arguments,Limits limits,Map<String,Object> parent,Captured config,Runnable check)throws Exception {
        check.run();var command=new ArrayList<String>(List.of(software.fingerprint().rscript().toString(),"--vanilla"));command.addAll(arguments);var process=new LinkedHashMap<String,Object>();parent.put(work.getFileName()+"-"+label,process);process.put("started_at",Instant.now().toString());
        String prefix=software.fingerprint().rscript().getParent().getParent().toString();var env=Map.of("PATH",prefix+"/bin:/usr/bin:/bin","LANG","C.UTF-8","LC_ALL","C.UTF-8","R_LIBS_USER",work.resolve("empty-library").toString(),"R_LIBS_SITE",prefix+"/lib/R/library","R_ENVIRON_USER","/dev/null","R_PROFILE_USER","/dev/null");
        Path sessionFile=work.getParent().resolve("session.json");parent.put("current_phase",work.getFileName()+"/"+label);replaceJson(sessionFile,parent);int exit;Exception primary=null;
        try(var guard=FavorProcessGuard.register(work,label)) {
            try{exit=HomerRuntime.executeCommand(command,work,label,limits.timeoutSeconds(),limits.workBytes(),limits.upstreamRssBytes(),env,()->{check.run();software.verify(check);config.verify(check);},()->{check.run();guard.observe();config.verify(check);HomerRuntime.bound(work.getParent(),limits.workBytes());replaceJson(sessionFile,parent);},process);}
            catch(Exception failure){primary=failure;throw failure;}
            finally{process.put("finished_at",Instant.now().toString());try{replaceJson(sessionFile,parent);}catch(IOException publication){if(primary!=null)primary.addSuppressed(publication);else throw publication;}}
        }
        if(exit!=0)throw new IOException("Original FAVOR "+label+" process exited "+exit+";inspect "+work.resolve(label+".stderr"));check.run();HomerRuntime.bound(work.getParent(),limits.workBytes());
    }
    static List<Split> splits(Path file,int chromosome,FavorCsvReader.Limits limits,FavorCsvReader.BlankRecords blanks,Runnable check)throws IOException {
        var selected=new ArrayList<Split>();try(var reader=FavorCsvReader.open(file,limits,blanks,check)){if(!reader.header().equals(List.of("Chr","Start_Pos","End_Pos")))throw new IOException("Require the exact original DBsplit columns Chr,Start_Pos,End_Pos");List<String> row;while((row=reader.next())!=null){int chr=positive(row.get(0)),start=positive(row.get(1)),end=positive(row.get(2));if(chr>22||end<start)throw new IOException("Invalid canonical autosomal DBsplit interval");if(chr==chromosome)selected.add(new Split(chr,start,end));}if(!reader.report().complete())throw new IOException("Incomplete DBsplit scan");}if(selected.size()<2||selected.size()>4096)throw new IOException("Require2..4096 selected shards;original single-shard source workflow is unsafe");return List.copyOf(selected);
    }
    static void compareJoined(Path csv,FavorCsvJoin.Result joined,List<String> headers,FavorCsvReader.Limits limits,FavorCsvReader.BlankRecords blanks,Runnable check)throws IOException {
        try(var reader=FavorCsvReader.open(csv,limits,blanks,check)){if(!reader.header().equals(headers))throw new IOException("Original joined CSV header differs from native join");int index=0;List<String> row;while((row=reader.next())!=null){if(index>=joined.joined().size())throw new IOException("Original joined CSV has extra rows");var expected=joined.joined().get(index++);var cells=new ArrayList<String>();cells.add(expected.key());cells.addAll(expected.databaseValues());if(!row.equals(cells))throw new IOException("Original joined CSV differs from exact raw native source cells at row "+index);}if(index!=joined.joined().size()||!reader.report().complete())throw new IOException("Incomplete original joined CSV");}
    }
    private static void joinLedgers(Path work,FavorCsvJoin.Result joined,List<String> headers,Runnable check)throws IOException {
        try(var query=Files.newBufferedWriter(work.resolve("query-ledger.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);var shards=Files.newBufferedWriter(work.resolve("shard-ledger.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);var output=Files.newBufferedWriter(work.resolve("native-join.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            tsv(query,List.of("query_row","variant","selected_shards","output_rows","database_matches","disposition","source_output_rows"));for(var row:joined.variants()){check.run();tsv(query,List.of(row.row(),row.variant().key(),row.selectedShards(),row.outputRows(),row.databaseMatches(),row.disposition().name(),row.sourceOutputRows().toString()));}
            tsv(shards,List.of("shard_row","identity","chromosome","start","end","selected","selected_variants","database_rows","matched_database_rows","unused_database_rows","output_rows"));for(var row:joined.shards()){check.run();tsv(shards,java.util.Arrays.asList(row.row(),row.identity(),row.chromosome(),row.start(),row.end(),row.selected(),row.selectedVariants(),row.databaseRows(),row.matchedDatabaseRows(),row.unusedDatabaseRows(),row.outputRows()));}
            var columns=new ArrayList<Object>(List.of("query_row","shard_row","database_row","VarInfo"));columns.addAll(headers);tsv(output,columns);for(var row:joined.joined()){check.run();var cells=new ArrayList<Object>(List.of(row.variantRow(),row.shardRow(),row.databaseRow(),row.key()));cells.addAll(row.databaseValues());tsv(output,cells);}
        }
    }
    private static Captured copy(Input input,Path target,long maximum,Path root,Limits limits,Runnable check)throws IOException {
        check.run();Path source=input.path().toAbsolutePath().normalize();if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||!source.toRealPath().equals(source)||Files.size(source)>maximum||!FollowupSupport.hash(source).equals(input.sha256()))throw new IOException("Missing,linked,oversized or changed protected FAVOR input: "+source);long expected=Files.size(source);HomerRuntime.bound(root,limits.workBytes()-Math.min(limits.workBytes(),expected));long size=0;
        try(var from=Files.newInputStream(source);var to=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];int n;while((n=from.read(buffer))!=-1){check.run();if(n>expected-size)throw new IOException("Protected FAVOR input grew during snapshot");to.write(buffer,0,n);size+=n;}}
        var captured=new Captured(source,target.toAbsolutePath().normalize(),expected,input.sha256());captured.verify(check);HomerRuntime.bound(root,limits.workBytes());return captured;
    }
    private static Reference healthInputs(Path root)throws IOException {
        Map<?,?> provenance=AdmixtureInputs.decode(resource("/org/jlinalg/omics/favor-health/provenance.json"));Object value=provenance.get("resource_sha256");if(!(value instanceof Map<?,?> hashes)||!hashes.keySet().equals(java.util.Set.of("input.vcf","splits.csv","chr22_1.csv","chr22_2.csv")))throw new IOException("Incomplete fixed FAVOR scientific health resource policy");
        for(String name:List.of("input.vcf","splits.csv","chr22_1.csv","chr22_2.csv")){byte[] bytes=resource("/org/jlinalg/omics/favor-health/"+name);if(!AdmixtureInputs.digest(bytes).equals(hashes.get(name)))throw new IOException("Fixed FAVOR scientific health fixture SHA256 differs: "+name);Files.write(root.resolve(name),bytes,StandardOpenOption.CREATE_NEW);}
        return new Reference("fixed-synthetic-health","synthetic-no-biological-reference",input(root.resolve("splits.csv")),List.of(input(root.resolve("chr22_1.csv")),input(root.resolve("chr22_2.csv"))));
    }
    private static Input input(Path path)throws IOException {return new Input(path.toAbsolutePath().normalize(),FollowupSupport.hash(path));}
    private static byte[] resource(String name)throws IOException {try(var in=FavorAnnotatorWorkflow.class.getResourceAsStream(name)){if(in==null)throw new IOException("Missing bundled FAVOR resource: "+name);byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized bundled FAVOR resource");return raw;}}
    static Path newOutput(Path supplied)throws IOException {Path root=supplied.toAbsolutePath().normalize();if(!root.toString().matches("/[A-Za-z0-9._/-]+")||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Original FAVOR shell workflow requires a NEW output below an existing canonical ASCII parent,without whitespace;input paths are independently snapshotted");return Files.createDirectory(root);}
    private static void runtime(Map<String,String> versions)throws IOException {Map<String,String> expected=Map.of("gdsfmt","1.42.0","SeqArray","1.46.0","digest","0.6.39","readr","2.2.0");if(!versions.getOrDefault("R","").startsWith("R version 4.4.3 "))throw new IOException("Actual FAVOR audit R version differs from admitted runtime");for(var e:versions.entrySet())if(!e.getKey().equals("R")&&!e.getValue().equals(expected.get(e.getKey())))throw new IOException("Actual FAVOR audit package version differs: "+e.getKey());}
    static int unavailableExit(boolean analysisStarted,IOException failure){return !analysisStarted&&failure.getSuppressed().length==0?3:2;}
    private static Map<String,Object> limitMap(Record record){var result=new LinkedHashMap<String,Object>();for(var component:record.getClass().getRecordComponents())try{Object value=component.getAccessor().invoke(record);result.put(component.getName(),value instanceof Record nested?limitMap(nested):value);}catch(ReflectiveOperationException invalid){throw new IllegalStateException("Cannot record typed FAVOR capacities",invalid);}return Map.copyOf(result);}
    private static void token(String value){if(value==null||!value.matches("[A-Za-z0-9._-]{1,1024}"))throw new IllegalArgumentException("Require bounded explicit FAVOR resource/build identity");}
    private static int positive(String value)throws IOException {try{if(!value.matches("[1-9][0-9]{0,9}"))throw new NumberFormatException();return Integer.parseInt(value);}catch(NumberFormatException invalid){throw new IOException("Require canonical positive signed32 DBsplit coordinate/chromosome",invalid);}}
    private static String shellQuote(String value){return "'"+value.replace("'","'\\''")+"'";}
    private static void tsv(java.io.Writer writer,List<?> cells)throws IOException {for(int i=0;i<cells.size();i++){if(i>0)writer.write('\t');String value=cells.get(i)==null?"":cells.get(i).toString();writer.write('"');writer.write(value.replace("\"","\"\""));writer.write('"');}writer.write('\n');}
    private static void softwareWarning(Path root,String status,List<String> warnings)throws IOException {Files.writeString(root.resolve("WARNINGS.md"),"# FAVOR "+status+"\n\n"+String.join("\n\n",warnings)+"\n\nCompletion requires session.json status complete-audited-offline-analysis and exit_code 0. All other outputs are provisional diagnostics. Keep the entire bundle.\n",StandardCharsets.UTF_8);}
    private static void json(Path path,Map<String,?> value,boolean create)throws IOException {Files.writeString(path,RegenieRuntime.json(value)+"\n",StandardCharsets.UTF_8,create?StandardOpenOption.CREATE_NEW:StandardOpenOption.CREATE,StandardOpenOption.WRITE);}
    private static void replaceJson(Path path,Map<String,?> value)throws IOException {Path temporary=path.resolveSibling(path.getFileName()+".partial");json(temporary,value,true);Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
}
