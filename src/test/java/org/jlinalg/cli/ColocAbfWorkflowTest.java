/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;

/** File transport, original-package references and adversarial complete-bundle replay. */
class ColocAbfWorkflowTest {
    static Path root,fixture;
    @BeforeAll static void prepare()throws Exception {
        Path parent=Path.of(System.getProperty("jlinalg.test.artifacts","build/coloc-abf-workflow-tests")).toAbsolutePath();Files.createDirectories(parent);
        int attempt=1;while(Files.exists(parent.resolve("attempt-"+attempt)))attempt++;root=parent.resolve("attempt-"+attempt);Files.createDirectory(root);
        fixture=root.resolve("reference");Files.createDirectory(fixture);byte[] raw;
        try(var input=ColocAbfWorkflowTest.class.getResourceAsStream("/org/jlinalg/coloc/abf-original603.zip")){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals("de00e6dfa5f7057fe16c8d59651868b9df46aa34365a6d8e4e451f9c677ed533",ColocAbfFiles.sha(raw));
        long bytes=0;int count=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                Path path=fixture.resolve(entry.getName()).normalize();assertTrue(path.startsWith(fixture));assertFalse(entry.isDirectory());
                byte[] content=zip.readAllBytes();bytes+=content.length;assertTrue(bytes<64_000_000);assertTrue(content.length<16_000_000);
                Files.createDirectories(path.getParent());Files.write(path,content,StandardOpenOption.CREATE_NEW);count++;
            }
        }
        assertEquals(265,count);
    }
    private static Path space(String name)throws IOException {Path p=root.resolve(name);Files.createDirectory(p);return p;}
    private static int call(Path logs,String label,String...args)throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int status=JLinAlgCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));
        Files.write(logs.resolve(label+".stdout"),out.toByteArray(),StandardOpenOption.CREATE_NEW);Files.write(logs.resolve(label+".stderr"),err.toByteArray(),StandardOpenOption.CREATE_NEW);return status;
    }
    private static Map<String,Object> trait(String file){
        var t=new LinkedHashMap<String,Object>();t.put("file",file);t.put("type","quant");t.put("sdY",1.0);t.put("columns",Map.of("snp","snp","beta","beta","varbeta","varbeta"));return t;
    }
    private static Map<String,Object> request(Path p)throws IOException {
        Files.writeString(p.resolve("first.tsv"),"snp\tbeta\tvarbeta\na\t0.1\t0.01\nb\t0.5\t0.01\nc\t-0.15\t0.02\n",StandardCharsets.UTF_8);
        Files.writeString(p.resolve("second.tsv"),"snp\tbeta\tvarbeta\na\t0.08\t0.01\nb\t0.45\t0.02\nc\t0.02\t0.02\n",StandardCharsets.UTF_8);
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("out","analysis");r.put("trait1",trait("first.tsv"));r.put("trait2",trait("second.tsv"));return r;
    }
    private static int run(Path p,Map<String,Object> r)throws Exception {ColocAbfFiles.json(p.resolve("run.json"),r);return call(p,"run","coloc-abf","--request",p.resolve("run.json").toString());}
    private static void refused(Path p,int code)throws Exception {
        assertFalse(Files.exists(p.resolve("analysis/COMPLETED.json")));
        String error=Files.readString(p.resolve("run.stderr"));assertTrue(error.contains(code==3?"SKIP3":"FAIL2"));assertTrue(error.contains("NEW output"));
        if(Files.isDirectory(p.resolve("analysis")))assertEquals(false,ColocAbfFiles.read(p.resolve("analysis/analysis-status.json")).get("results_admitted"));
    }
    private static Map<String,Object> referenceRequest(String name)throws Exception {
        Path input=fixture.resolve("inputs/"+name);var settings=new HashMap<String,String>();
        for(String line:Files.readAllLines(input.resolve("settings.tsv")).subList(1,Files.readAllLines(input.resolve("settings.tsv")).size())){
            String[] cells=line.split("\t",-1);settings.put(cells[0],cells[1]);
        }
        var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("mode","RUN");request.put("out","analysis");
        for(int n=1;n<=2;n++){
            Path file=input.resolve("trait"+n+".tsv");var t=new LinkedHashMap<String,Object>();t.put("file",file.toString());t.put("type",settings.get("type"+n));
            var columns=new LinkedHashMap<String,Object>();for(String header:Files.readAllLines(file).get(0).split("\t"))if(ColocAbfContracts.ROLES.contains(header))columns.put(header,header);
            t.put("columns",columns);for(String key:List.of("N","sdY","s"))if(settings.containsKey(key+n))t.put(key,Double.valueOf(settings.get(key+n)));
            request.put("trait"+n,t);
        }
        var priors=new LinkedHashMap<String,Object>();for(String key:List.of("p1","p2","p12"))if(settings.containsKey(key))priors.put(key,Double.valueOf(settings.get(key)));request.put("priors",priors);return request;
    }
    private static List<String[]> table(Path path)throws Exception {
        var result=new ArrayList<String[]>();long number=0;
        for(String line:Files.readAllLines(path,StandardCharsets.UTF_8))result.add(DelimitedData.parse(line,'\t',++number,path).toArray(String[]::new));return result;
    }
    private static void originalValues(String name,Path output)throws Exception {
        var expectedSummary=table(fixture.resolve("original/"+name+"/summary.tsv"));var actualSummary=table(output.resolve("summary.tsv"));
        var actual=new HashMap<String,Double>();for(var row:actualSummary.subList(1,actualSummary.size()))if(!row[1].equals("."))actual.put(row[0],Double.valueOf(row[1]));
        for(var row:expectedSummary.subList(1,expectedSummary.size())){
            double value=Double.parseDouble(row[1]);assertEquals(value,actual.get(row[0].equals("nsnps")?"common_variants":row[0]),2e-12*Math.max(1,Math.abs(value)));
        }
        var a=table(output.resolve("variants.tsv"));var b=table(fixture.resolve("original/"+name+"/variants.tsv"));assertEquals(b.size(),a.size());
        for(int i=1;i<a.size();i++)for(int j=0;j<b.get(0).length;j++){
            String header=b.get(0)[j];int column=Arrays.asList(a.get(0)).indexOf(header);assertTrue(column>=0,header);
            if(header.equals("snp"))assertEquals(b.get(i)[j],a.get(i)[column]);
            else {double value=Double.parseDouble(b.get(i)[j]);assertEquals(value,Double.parseDouble(a.get(i)[column]),2e-12*Math.max(1,Math.abs(value)),name+" "+header);}
        }
    }
    @TestFactory Stream<DynamicTest> allThirtyOriginalPackageCases()throws Exception {
        var cases=Files.readAllLines(fixture.resolve("inputs/cases.tsv"));assertEquals(31,cases.size());
        return cases.subList(1,cases.size()).stream().map(line->DynamicTest.dynamicTest(line.split("\t")[0],()->{
            String[] fields=line.split("\t");String name=fields[0];int code=Integer.parseInt(fields[1]);Path p=space("original-"+name);
            assertEquals(code,run(p,referenceRequest(name)),Files.readString(p.resolve("run.stderr")));
            if(code==0){
                originalValues(name,p.resolve("analysis"));assertEquals(0,call(p,"verify","coloc-abf","--verify",p.resolve("analysis").toString()));
                for(int n=1;n<=2;n++)assertArrayEquals(Files.readAllBytes(fixture.resolve("inputs/"+name+"/trait"+n+".tsv")),Files.readAllBytes(p.resolve("analysis/inputs/trait"+n+".tsv")));
            }else refused(p,2);
        }));
    }
    @Test void missingInputIsProminentSkippedAnalysisWithCapturedFirstInput()throws Exception {
        Path p=space("missing-input");var r=request(p);Files.move(p.resolve("second.tsv"),p.resolve("second-kept.tsv"));assertEquals(3,run(p,r));refused(p,3);
        assertArrayEquals(Files.readAllBytes(p.resolve("first.tsv")),Files.readAllBytes(p.resolve("analysis/inputs/trait1.tsv")));
        assertEquals(2,call(p,"verify-failed","coloc-abf","--verify",p.resolve("analysis").toString()));
    }
    @Test void missingRequestIsSkip3()throws Exception {
        Path p=space("missing-request");assertEquals(3,call(p,"run","coloc-abf","--request",p.resolve("absent.json").toString()));assertTrue(Files.readString(p.resolve("run.stderr")).contains("SKIP3"));
    }
    @Test void existingOutputIsPreserved()throws Exception {
        Path p=space("existing-output");var r=request(p);Files.createDirectory(p.resolve("analysis"));Files.writeString(p.resolve("analysis/keep.txt"),"keep");assertEquals(2,run(p,r));assertEquals("keep",Files.readString(p.resolve("analysis/keep.txt")));
    }
    @TestFactory Stream<DynamicTest> capacityRefusalsRetainWholeInputsWithoutCompleteResult(){
        return Stream.of("maximum_variants","maximum_input_bytes","maximum_output_bytes").map(key->DynamicTest.dynamicTest(key,()->{
            Path p=space("capacity-"+key);var r=request(p);r.put(key,key.equals("maximum_variants")?2:10);byte[] first=Files.readAllBytes(p.resolve("first.tsv"));assertEquals(2,run(p,r));refused(p,2);assertArrayEquals(first,Files.readAllBytes(p.resolve("first.tsv")));
        }));
    }
    @TestFactory Stream<DynamicTest> literalInvalidScientificFieldsAreNeverRepaired(){
        return Stream.of("NaN","Inf","1e999","0.01 ","0").map(value->DynamicTest.dynamicTest("variance="+value,()->{
            Path p=space("invalid-variance-"+Integer.toUnsignedString(value.hashCode()));var r=request(p);Files.writeString(p.resolve("first.tsv"),Files.readString(p.resolve("first.tsv")).replace("0.01",value));
            byte[] original=Files.readAllBytes(p.resolve("first.tsv"));assertEquals(2,run(p,r));refused(p,2);assertArrayEquals(original,Files.readAllBytes(p.resolve("analysis/inputs/trait1.tsv")));
        }));
    }
    @Test void blankRowsAreNotSilentlyFiltered()throws Exception {
        Path p=space("blank-row");var r=request(p);Files.writeString(p.resolve("first.tsv"),Files.readString(p.resolve("first.tsv"))+"\n");assertEquals(2,run(p,r));refused(p,2);
    }
    @Test void nonzeroUnderflowingBetaIsRefusedInsteadOfBecomingZero()throws Exception {
        Path p=space("underflow-beta");var r=request(p);Files.writeString(p.resolve("first.tsv"),Files.readString(p.resolve("first.tsv")).replace("0.5","1e-999"));
        assertEquals(2,run(p,r));refused(p,2);assertTrue(Files.readString(p.resolve("run.stderr")).contains("underflows to zero"));
    }
    @Test void exactWhitespaceAndUnicodeIdentifiersArePreserved()throws Exception {
        Path p=space("exact-identities");var r=request(p);
        for(String file:List.of("first.tsv","second.tsv"))Files.writeString(p.resolve(file),Files.readString(p.resolve(file)).replace("\na\t","\n a \t").replace("\nb\t","\nα😀\t"));
        assertEquals(0,run(p,r));String output=Files.readString(p.resolve("analysis/variants.tsv"));assertTrue(output.contains("\n a \t"));assertTrue(output.contains("\nα😀\t"));
        assertEquals(0,call(p,"verify","coloc-abf","--verify",p.resolve("analysis").toString()));
    }
    @TestFactory Stream<DynamicTest> replayUsesCapturedInputsAndPreservesOldBundle(){
        return Stream.of("p-case-control","both-weight-vectors","unicode-identifiers-C-collation","estimated-sdy").map(name->DynamicTest.dynamicTest(name,()->{
            Path p=space("replay-"+name);assertEquals(0,run(p,referenceRequest(name)));Path previous=p.resolve("analysis");var pins=ColocAbfFiles.inventory(previous);byte[] marker=Files.readAllBytes(previous.resolve("COMPLETED.json"));
            ColocAbfFiles.json(p.resolve("replay.json"),Map.of("schema_version",1,"mode","REPLAY","analysis","analysis","out","replayed"));
            assertEquals(0,call(p,"replay","coloc-abf","--request",p.resolve("replay.json").toString()));originalValues(name,p.resolve("replayed"));
            assertTrue(SeuratDifferentialContracts.same(pins,ColocAbfFiles.inventory(previous)));assertArrayEquals(marker,Files.readAllBytes(previous.resolve("COMPLETED.json")));
            assertEquals(0,call(p,"verify-replay","coloc-abf","--verify",p.resolve("replayed").toString()));
        }));
    }
    @Test void sourceFilesAreNotRequiredForVerificationOrReplayAfterCapture()throws Exception {
        Path p=space("capture-independent");assertEquals(0,run(p,request(p)));Files.move(p.resolve("first.tsv"),p.resolve("first-retained.tsv"));Files.move(p.resolve("second.tsv"),p.resolve("second-retained.tsv"));
        assertEquals(0,call(p,"verify","coloc-abf","--verify",p.resolve("analysis").toString()));
        ColocAbfFiles.json(p.resolve("replay.json"),Map.of("schema_version",1,"mode","REPLAY","analysis","analysis","out","replayed"));assertEquals(0,call(p,"replay","coloc-abf","--request",p.resolve("replay.json").toString()));
    }
    private static void reseal(Path bundle)throws Exception {
        ColocAbfWorkflowCli.seal(bundle);var marker=ColocAbfFiles.read(bundle.resolve("COMPLETED.json"));marker.put("manifest_sha256",ColocAbfFiles.pin(bundle.resolve("manifest.json")).get("sha256"));Files.writeString(bundle.resolve("COMPLETED.json"),NextflowJson.json(marker)+"\n");
    }
    @TestFactory Stream<DynamicTest> resealedScientificOrScopeCorruptionIsRefused(){
        return Stream.of("results","variants","input","settings","producer","warnings","status","extra-file","missing-input").map(kind->DynamicTest.dynamicTest(kind,()->{
            Path p=space("corruption-"+kind);assertEquals(0,run(p,request(p)));Path b=p.resolve("analysis");
            switch(kind){
                case "results"->{var r=ColocAbfFiles.read(b.resolve("results.json"));r.put("hypothesis_posteriors",List.of(1,0,0,0,0));Files.writeString(b.resolve("results.json"),NextflowJson.json(r)+"\n");}
                case "variants"->Files.writeString(b.resolve("variants.tsv"),Files.readString(b.resolve("variants.tsv")).replace("\nb\t","\nwrong-id\t"));
                case "input"->Files.writeString(b.resolve("inputs/trait1.tsv"),Files.readString(b.resolve("inputs/trait1.tsv")).replace("0.5","5.0"));
                case "settings"->{var r=ColocAbfFiles.read(b.resolve("request.json"));r.put("maximum_variants",2);Files.writeString(b.resolve("request.json"),NextflowJson.json(r)+"\n");}
                case "producer"->{var r=ColocAbfFiles.read(b.resolve("producer.json"));r.put("selected_class_sha256",Map.of("fake","0".repeat(64)));Files.writeString(b.resolve("producer.json"),NextflowJson.json(r)+"\n");}
                case "warnings"->Files.writeString(b.resolve("WARNINGS.md"),"No warnings\n");
                case "status"->{var r=ColocAbfFiles.read(b.resolve("analysis-status.json"));r.put("results_admitted",false);Files.writeString(b.resolve("analysis-status.json"),NextflowJson.json(r)+"\n");}
                case "extra-file"->Files.writeString(b.resolve("unrecorded-result.tsv"),"extra\n");
                case "missing-input"->Files.move(b.resolve("inputs/trait1.tsv"),p.resolve("captured-trait1-retained.tsv"));
                default->throw new AssertionError(kind);
            }
            reseal(b);assertEquals(2,call(p,"verify-corruption","coloc-abf","--verify",b.toString()));
        }));
    }
    @TestFactory Stream<DynamicTest> tinyPositiveProbabilitiesCannotBeResealedAsZero(){
        return Stream.of("conditional","hypothesis").map(kind->DynamicTest.dynamicTest(kind,()->{
            Path p=space("tiny-to-zero-"+kind);var r=request(p);
            for(String file:List.of("first.tsv","second.tsv"))Files.writeString(p.resolve(file),"snp\tbeta\tvarbeta\na\t0.1\t0.01\nb\t0.5\t0.001\nc\t0.02\t0.02\n");
            assertEquals(0,run(p,r));Path bundle=p.resolve("analysis");
            if(kind.equals("conditional")){
                var lines=Files.readAllLines(bundle.resolve("variants.tsv"));String[] fields=lines.get(1).split("\t",-1);double value=Double.parseDouble(fields[fields.length-1]);assertTrue(value>0&&value<1e-20);
                fields[fields.length-1]="0.0";lines.set(1,String.join("\t",fields));Files.write(bundle.resolve("variants.tsv"),lines,StandardCharsets.UTF_8);
            }else{
                var result=ColocAbfFiles.read(bundle.resolve("results.json"));var probabilities=new ArrayList<Object>((List<?>)result.get("hypothesis_posteriors"));double value=((Number)probabilities.get(0)).doubleValue();assertTrue(value>0&&value<1e-20);
                probabilities.set(0,0.0);result.put("hypothesis_posteriors",probabilities);Files.writeString(bundle.resolve("results.json"),NextflowJson.json(result)+"\n");
            }
            reseal(bundle);assertEquals(2,call(p,"verify-corruption","coloc-abf","--verify",bundle.toString()));
        }));
    }
    @Test void unknownColumnsSettingsAndDuplicateJsonKeysAreRefused()throws Exception {
        for(String kind:List.of("unknown","duplicate","missing-column")){
            Path p=space("schema-"+kind);var r=request(p);
            if(kind.equals("unknown"))r.put("automatically_fix_alleles",true);
            if(kind.equals("missing-column"))Files.writeString(p.resolve("first.tsv"),Files.readString(p.resolve("first.tsv")).replace("varbeta","se"));
            if(kind.equals("duplicate")){
                Files.writeString(p.resolve("run.json"),NextflowJson.json(r).replace("\"mode\":\"RUN\"","\"mode\":\"RUN\",\"mode\":\"RUN\""));
                assertEquals(2,call(p,"run","coloc-abf","--request",p.resolve("run.json").toString()));
            }else assertEquals(2,run(p,r));refused(p,2);
        }
    }
    @Test void commandRoutingRetainsDistinctSusieContract()throws Exception {
        Path p=space("help");assertEquals(0,call(p,"abf","coloc-abf","--help"));assertTrue(Files.readString(p.resolve("abf.stdout")).contains("varbeta"));
        assertEquals(0,call(p,"susie-coloc","coloc","--help"));String existing=Files.readString(p.resolve("susie-coloc.stdout"));assertTrue(existing.contains("effect_index"));assertTrue(existing.contains("5e-6"));
        assertEquals(0,call(p,"main","--help"));assertTrue(Files.readString(p.resolve("main.stdout")).contains("coloc-abf"));
    }
}
