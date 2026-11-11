/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.genetics.RepeatAnnotations;

/** Fixed original scientific controls; passing these alone does not admit a runtime or public SEARCH. */
final class RepeatHealth {
    private RepeatHealth() { }
    static final String LOCK_SHA256="915899f81eb0ac8b885d5a2be0dfb063bdd5dab31b70f0d551ede428d8d9d856";
    private static final String PREFIX="/org/jlinalg/omics/repeat-health/";
    private static final RepeatBundle.Limits FIXTURE_LIMITS=new RepeatBundle.Limits(1048576,128);
    private static final RepeatFasta.Limits FASTA=new RepeatFasta.Limits(1048576,1048576,1000000,1000000,100,4096,2048,262144);
    private static final RepeatAnnotations.Limits ANNOTATIONS=new RepeatAnnotations.Limits(1048576,1000,4096,4194304);
    private record Pin(long bytes,String sha256) { }
    record Fixture(String label,String profile,String recipe,String expectedAnnotations,String expectedMask) { }
    private record Lock(byte[] raw,Map<String,Pin> files,List<Fixture> cases) {
        Lock { raw=raw.clone();files=Map.copyOf(files);cases=List.copyOf(cases); }
        @Override public byte[] raw(){return raw.clone();}
    }
    private static final Lock LOCK=load();
    private static final String STATUS="PASSED_FIXED_ORIGINAL_CONTROLS_RUNTIME_NOT_ADMITTED";
    record Prepared(Path work) {
        void verify(Runnable check)throws IOException{verifyFixtures(work,check);}
        List<Fixture> cases(String profile)throws IOException{
            if(!Set.of(RepeatRecipe.CURRENT,RepeatRecipe.HISTORICAL).contains(profile))
                throw new RepeatRecipe.UnavailableCapability("No fixed original health controls for requested profile");
            return LOCK.cases().stream().filter(item->item.profile().equals(profile)).toList();
        }
        Path path(String name){return work.resolve(name);}
    }
    record Check(String label,int annotationRows,boolean originalNoHitMaskAbsent,int queryRecords,long queryBases,
            String annotationFieldContractSha256,String maskedSequenceContractSha256) { }
    record Result(Path work,String profile,List<Check> controls,String ledgerSha256) {
        Result {controls=List.copyOf(controls);}
    }
    static Prepared prepare(Path supplied,Runnable check)throws IOException{
        Path work=supplied.toAbsolutePath().normalize();canonicalParent(work);Files.createDirectory(work);
        try{
            for(var entry:new TreeMap<>(LOCK.files()).entrySet()){
                check.run();Path target=work.resolve(entry.getKey());Files.createDirectories(target.getParent());
                byte[] raw=resource(entry.getKey(),entry.getValue().bytes(),check);
                if(!AdmixtureInputs.digest(raw).equals(entry.getValue().sha256()))throw new IOException("Pinned health resource differs: "+entry.getKey());
                Files.write(target,raw,StandardOpenOption.CREATE_NEW);
            }
            Files.write(work.resolve("lock.json"),LOCK.raw(),StandardOpenOption.CREATE_NEW);
            return open(work,check);
        }catch(IOException|RuntimeException failure){
            diagnostic(work,"HEALTH_FIXTURES_FAILED.txt",failure);throw failure;
        }
    }
    static Prepared open(Path supplied,Runnable check)throws IOException{
        Path root=supplied.toAbsolutePath().normalize();verifyFixtures(root,check);return new Prepared(root);
    }
    private static void verifyFixtures(Path work,Runnable check)throws IOException{
        var expected=new TreeMap<String,String>();long bytes=LOCK.raw().length;
        var directories=new java.util.HashSet<String>();directories.add("");
        for(var entry:LOCK.files().entrySet()){
            expected.put(entry.getKey(),entry.getValue().sha256());bytes=Math.addExact(bytes,entry.getValue().bytes());
            for(Path parent=Path.of(entry.getKey()).getParent();parent!=null;parent=parent.getParent())directories.add(parent.toString().replace('\\','/'));
        }
        expected.put("lock.json",LOCK_SHA256);
        var actual=RepeatBundle.inventory(work,FIXTURE_LIMITS,true,check);
        if(!actual.hashes().equals(expected)||actual.bytes()!=bytes||actual.members()!=expected.size()+directories.size())
            throw new IOException("Complete fixed original health fixture bytes or directory population differ");
    }
    /** Executes every locked control for this profile, using one explicitly owned resource/cache tree. */
    static Result execute(Prepared fixtures,RepeatResources.Prepared resources,Path supplied,RepeatBundle.Limits capacity,Runnable check)throws IOException{
        var cases=fixtures.cases(resources.payload().profile());fixtures.verify(check);resources.verify(check);
        Path work=supplied.toAbsolutePath().normalize();RepeatPayload.originalPath(work);canonicalParent(work);
        for(Path protectedRoot:List.of(fixtures.work(),resources.work(),resources.payload().home()))
            if(work.startsWith(protectedRoot)||protectedRoot.startsWith(work))throw new IOException("Health work must be NEW and disjoint from fixtures,resources and payload");
        Files.createDirectory(work);var results=new ArrayList<Check>();Map<String,String> completed=Map.of();
        try{
            for(Fixture fixture:cases){
                check.run();fixtures.verify(check);resources.verify(check);
                var previous=RepeatBundle.inventory(work,capacity,true,check);preserved(completed,previous.hashes());
                var spec=RepeatRecipe.read(fixtures.path(fixture.recipe()));
                if(!spec.profile().equals(resources.payload().profile())||spec.operation()!=RepeatRecipe.Operation.SEARCH)
                    throw new IOException("Fixed health recipe profile/operation differs");
                spec=component(spec,previous,RepeatBundle.inventory(fixtures.work(),FIXTURE_LIMITS,false,check),capacity);
                var input=RepeatInputs.prepare(spec,work.resolve(fixture.label()+"-prepared"),check);
                var result=RepeatOriginalStages.execute(input,resources,work.resolve(fixture.label()+"-run"),check);
                if(result.search().exitCode()!=0||result.postprocess().exitCode()!=0||!result.search().ownedLiveAfterCleanup().isEmpty()||!result.postprocess().ownedLiveAfterCleanup().isEmpty())
                    throw new IOException("Fixed original health requires both independently owned zero exits and complete cleanup");
                results.add(validate(fixtures,fixture,result.root(),check));
                var after=RepeatBundle.inventory(work,capacity,true,check);preserved(previous.hashes(),after.hashes());completed=after.hashes();
                budget(work,fixtures,resources,capacity,check);
            }
            fixtures.verify(check);resources.verify(check);
            var record=new LinkedHashMap<String,Object>();record.put("status",STATUS);
            record.put("profile",resources.payload().profile());record.put("health_lock_sha256",LOCK_SHA256);record.put("controls",results);
            record.put("scientific_runtime_admitted",false);record.put("public_original_SEARCH_available",false);
            record.put("scope","Fixed synthetic original controls with complete ordered annotation fields and masked header/base hashes. Shared generated resource caches are retained;global logical bytes/member capacities include all prior controls,fixtures and one complete owned resource tree. Per-stage process limits are not a whole-health wall deadline. Host admission and public SEARCH remain separate gates.");
            byte[] raw=RepeatBundle.json(record);
            if(Math.addExact(budget(work,fixtures,resources,capacity,check),raw.length+65536L)>capacity.bytes())throw new IOException("Health metadata/diagnostic capacity exceeded");
            Files.write(work.resolve("health.json"),raw,StandardOpenOption.CREATE_NEW);
            budget(work,fixtures,resources,capacity,check);preserved(completed,RepeatBundle.inventory(work,capacity,true,check).hashes());
            return new Result(work,resources.payload().profile(),results,AdmixtureInputs.digest(raw));
        }catch(IOException|RuntimeException failure){diagnostic(work,"HEALTH_FAILED.txt",failure);throw failure;}
    }
    private static RepeatRecipe.Spec component(RepeatRecipe.Spec s,RepeatBundle.Inventory previous,RepeatBundle.Inventory fixtures,RepeatBundle.Limits capacity)throws IOException{
        long bytes=Math.subtractExact(Math.min(s.limits().workspaceBytes(),capacity.bytes()),Math.addExact(previous.bytes(),fixtures.bytes()+65536L));
        int members=Math.subtractExact(Math.min(s.limits().fileCount(),capacity.members()),Math.addExact(previous.members(),fixtures.members()+16));
        if(bytes<1048576||members<128)throw new IOException("Complete fixed health control/metadata capacity is insufficient");
        var l=s.limits();var p=s.process();
        var limits=new RepeatRecipe.Limits(l.fasta(),l.annotations(),l.inputBytes(),l.indexBytes(),l.retainedBytes(),bytes,members);
        var process=new RepeatRecipe.ProcessLimits(p.wallMillis(),p.addressSpaceBytes(),p.cpuSeconds(),p.fileBytes(),p.openFiles(),Math.min(bytes,p.workspaceBytes()),Math.min(members,p.fileCount()),p.processes(),p.threads(),p.sampledRssBytes(),p.monitorMillis());
        return new RepeatRecipe.Spec(s.recipe(),s.recipeSha256(),s.resourceRoot(),s.operation(),s.mode(),s.profile(),s.assembly(),s.review(),s.query(),s.library(),s.annotations(),s.masked(),s.species(),s.staging(),s.mask(),s.selection(),s.parameters(),limits,process);
    }
    /** Rechecks retained fixed controls without rerunning an original program or trusting health.json flags. */
    static List<Check> verifyCompleted(Prepared fixtures,RepeatResources.Prepared resources,Path work,Runnable check)throws IOException{
        fixtures.verify(check);resources.verify(check);var actual=new ArrayList<Check>();Fixture last=null;
        for(Fixture fixture:fixtures.cases(resources.payload().profile())){
            var spec=RepeatRecipe.read(fixtures.path(fixture.recipe()));Path stage=work.resolve(fixture.label()+"-run");
            RepeatStageEvidence.verify(spec,resources,work.resolve(fixture.label()+"-prepared"),stage,check);
            Check verified=validate(fixtures,fixture,stage,check);actual.add(verified);last=fixture;
            var result=RepeatBundle.object(stage.resolve("metadata/results.json"),4194304);
            if(!Long.valueOf(verified.annotationRows()).equals(result.get("annotation_rows"))
                    ||!Boolean.valueOf(verified.originalNoHitMaskAbsent()).equals(result.get("original_no_hit_mask_and_table_absence"))
                    ||!java.util.Objects.equals(verified.originalNoHitMaskAbsent()?null:stage.resolve("post-input/query.fa.masked").toString(),result.get("original_mask")))throw new IOException("Retained original mask/no-hit result scope differs");
        }
        var ledger=RepeatBundle.object(work.resolve("health.json"),4194304);
        if(!ledger.keySet().equals(java.util.Set.of("status","profile","health_lock_sha256","controls","scientific_runtime_admitted","public_original_SEARCH_available","scope"))
                ||!STATUS.equals(ledger.get("status"))||!resources.payload().profile().equals(ledger.get("profile"))||!LOCK_SHA256.equals(ledger.get("health_lock_sha256"))
                ||!Boolean.FALSE.equals(ledger.get("scientific_runtime_admitted"))||!Boolean.FALSE.equals(ledger.get("public_original_SEARCH_available"))
                ||!RepeatBundle.object(RepeatBundle.json(Map.of("controls",actual))).get("controls").equals(ledger.get("controls")))throw new IOException("Retained fixed health ledger differs from independently checked controls");
        if(last==null||!RepeatBundle.object(RepeatBundle.json(resources.generatedCache(check))).equals(RepeatBundle.object(work.resolve(last.label()+"-run/metadata/generated-cache.json"),4194304)))throw new IOException("Final complete fixed-health resource cache differs");
        return List.copyOf(actual);
    }
    private static long budget(Path work,Prepared fixtures,RepeatResources.Prepared resources,RepeatBundle.Limits capacity,Runnable check)throws IOException{
        long bytes=0;int members=0;
        for(Path root:List.of(work,fixtures.work(),resources.work())){
            var used=RepeatBundle.inventory(root,capacity,false,check);bytes=Math.addExact(bytes,used.bytes());members=Math.addExact(members,used.members());
        }
        if(bytes>capacity.bytes()||members+16>capacity.members())throw new IOException("Whole fixed health logical-byte/member capacity exceeded");return bytes;
    }
    /** Re-reads actual files independently of the in-memory stage result or a success marker. */
    static Check validate(Prepared fixtures,Fixture fixture,Path stage,Runnable check)throws IOException{
        if(!LOCK.cases().contains(fixture))throw new IOException("Unknown fixed original health case");
        fixtures.verify(check);
        Path actual=stage.resolve("post-input/query.fa.out");var expected=annotations(fixtures.path(fixture.expectedAnnotations()));var observed=annotations(actual);
        var expectedFields=expected.hits().stream().map(RepeatAnnotations.Hit::rawFields).toList();
        var fields=observed.hits().stream().map(RepeatAnnotations.Hit::rawFields).toList();
        if(expected.noHitsMessage()!=observed.noHitsMessage()||!expectedFields.equals(fields))throw new IOException("Complete ordered original health annotation fields differ: "+fixture.label());
        var spec=RepeatRecipe.read(fixtures.path(fixture.recipe()));var query=RepeatFasta.read(spec.query().path(),RepeatFasta.Encoding.PLAIN_UTF8,FASTA,check);
        Path mask=stage.resolve("post-input/query.fa.masked");String maskedContract=null;
        boolean noMask=fixture.expectedMask()==null;
        if(noMask){
            if(Files.exists(mask,LinkOption.NOFOLLOW_LINKS)||!observed.hits().isEmpty()||!observed.noHitsMessage()
                    ||Files.exists(stage.resolve("post-input/query.fa.tbl"),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Original no-hit mask/table must remain absent: "+fixture.label());
        }else{
            var expectedMask=RepeatFasta.read(fixtures.path(fixture.expectedMask()),RepeatFasta.Encoding.PLAIN_UTF8,FASTA,check);
            var actualMask=RepeatFasta.read(mask,RepeatFasta.Encoding.PLAIN_UTF8,FASTA,check);
            if(!expectedMask.sequences().equals(actualMask.sequences())||expectedMask.residues()!=actualMask.residues())
                throw new IOException("Complete original health masked headers/base population differs: "+fixture.label());
            RepeatMaskAudit.compare(spec.query().path(),RepeatFasta.Encoding.PLAIN_UTF8,mask,RepeatFasta.Encoding.PLAIN_UTF8,observed.hits(),RepeatMaskAudit.Selection.all(),spec.mask(),FASTA,2097152,check);
            maskedContract=AdmixtureInputs.digest(RepeatBundle.json(actualMask.sequences()));
        }
        return new Check(fixture.label(),observed.hits().size(),noMask,query.sequences().size(),query.residues(),AdmixtureInputs.digest(RepeatBundle.json(fields)),maskedContract);
    }
    private static RepeatAnnotations.Table annotations(Path path)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toAbsolutePath().normalize().toRealPath().equals(path)||Files.size(path)>1048576)
            throw new IOException("Fixed health annotation must be a complete canonical bounded file");
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){return RepeatAnnotations.read(reader,path.toString(),RepeatAnnotations.Format.OUT_WITH_ID,ANNOTATIONS);}
    }
    private static void preserved(Map<String,String> before,Map<String,String> after)throws IOException{
        for(var entry:before.entrySet())if(!entry.getValue().equals(after.get(entry.getKey())))throw new IOException("A completed original health control changed: "+entry.getKey());
    }
    private static Lock load(){
        try{
            byte[] raw=resource("lock.json",1048576,()->{});if(!AdmixtureInputs.digest(raw).equals(LOCK_SHA256))throw new IOException("Fixed original health lock differs");
            Map<?,?> value=RepeatBundle.object(raw);if(!Long.valueOf(1).equals(value.get("schema_version")))throw new IOException("Invalid health lock schema");
            var files=new TreeMap<String,Pin>();Map<?,?> members=map(value.get("members"));
            for(var entry:members.entrySet()){
                String name=entry.getKey().toString();if(!name.matches("[A-Za-z0-9-]+/[A-Za-z0-9.-]+"))throw new IOException("Unsafe fixed health member");
                var pin=map(entry.getValue());if(!(pin.get("bytes") instanceof Long size)||size<1||size>1048576||!(pin.get("sha256") instanceof String hash)||!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid fixed health pin");
                files.put(name,new Pin(size,hash));
            }
            if(!(value.get("cases") instanceof List<?> cases)||cases.size()!=13||files.size()!=56)throw new IOException("Incomplete fixed original health population");
            var fixtures=new ArrayList<Fixture>();
            for(Object item:cases){
                var c=map(item);String label=text(c,"label"),profile=text(c,"profile"),recipe=text(c,"recipe"),out=text(c,"expected_annotations");
                String mask=c.get("expected_mask")==null?null:text(c,"expected_mask");
                if(!files.containsKey(recipe)||!files.containsKey(out)||mask!=null&&!files.containsKey(mask)||!Set.of(RepeatRecipe.CURRENT,RepeatRecipe.HISTORICAL).contains(profile)
                        ||fixtures.stream().anyMatch(f->f.label().equals(label)))throw new IOException("Invalid fixed original health case");
                fixtures.add(new Fixture(label,profile,recipe,out,mask));
            }
            if(fixtures.stream().filter(f->f.profile().equals(RepeatRecipe.CURRENT)).count()!=4)throw new IOException("Fixed profile health roles differ");
            return new Lock(raw,files,fixtures);
        }catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static byte[] resource(String name,long maximum,Runnable check)throws IOException{
        try(var input=RepeatHealth.class.getResourceAsStream(PREFIX+name);var out=new java.io.ByteArrayOutputStream()){
            if(input==null)throw new IOException("Missing fixed original health resource: "+name);
            byte[] buffer=new byte[16384];int count;long total=0;
            while((count=input.read(buffer))>=0){check.run();total=Math.addExact(total,count);if(total>maximum)throw new IOException("Fixed health resource exceeds pinned byte capacity");out.write(buffer,0,count);}
            return out.toByteArray();
        }
    }
    private static Map<?,?> map(Object value)throws IOException{if(!(value instanceof Map<?,?> m))throw new IOException("Expected fixed health object");return m;}
    private static String text(Map<?,?> m,String key)throws IOException{if(!(m.get(key) instanceof String text)||text.isBlank())throw new IOException("Missing fixed health text: "+key);return text;}
    private static void canonicalParent(Path path)throws IOException{
        if(path.getParent()==null||!path.getParent().toRealPath().equals(path.getParent()))throw new IOException("Fixed health output requires a canonical existing parent and NEW directory");
    }
    private static void diagnostic(Path work,String name,Exception failure){
        try{String text=failure.toString();if(text.length()>8192)text=text.substring(0,8192);
            Files.writeString(work.resolve(name),"FIXED ORIGINAL HEALTH FAILED: no scientific runtime or result admitted.\n"+text+"\nRequired action: preserve all logs,ledgers,source fixtures and partials. Missing/forbidden prerequisites mean SKIP3 and a skipped dependent analysis. Integrity,input,execution or capacity errors mean FAIL2. Correct the named cause and use NEW paths;never fabricate missing masks or infer biological absence. No installation is performed.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }catch(IOException retained){failure.addSuppressed(retained);}
    }
}
