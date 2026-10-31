/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.FumaFisherExact;
import org.jlinalg.genetics.FumaFisherFamilies;
import org.jlinalg.genetics.FumaFisherGroups;
import org.jlinalg.genetics.FumaKnownAssociations;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Strict streamed Fisher tables and a verified, coordinate-free known-association import. */
final class FumaFisherInputs {
    record Prepared(List<FumaFisherFamilies.Family> families,List<FumaFisherFamilies.Table> tables,
            FumaFisherGroups.Result groups,Map<String,Object> imported,List<String[]> sources,List<String> warnings) { }
    private record Definition(String id,FumaFisherFamilies.Correction correction,FumaFisherFamilies.Encoding encoding,
            FumaFisherFamilies.Missing missing,FumaFisherFamilies.Empty empty,int pseudocount,double confidence) { }
    private record Original(int row,String source,String family,String test,String gene,String trait,String context,
            boolean target,boolean missing,boolean selected) { }
    private record Bundle(List<FumaFisherGroups.Observation> observations,Map<String,Object> metadata,
            List<String[]> sources,List<String> warnings) { }
    @FunctionalInterface private interface Row {void read(String[] row,List<String> header,int line)throws Exception;}
    final FumaAssociationInputs transport;
    private final FumaAssociationInputs.Limits limits;
    private final Runnable check;
    private long rows,cells;
    private String context="Fisher options";
    FumaFisherInputs(Path directory,FumaAssociationInputs.Limits limits,Runnable check)throws IOException {
        this.limits=limits;this.check=check;transport=new FumaAssociationInputs(directory,limits,check);
    }
    private void read(Path path,String role,List<String> required,Row consumer)throws Exception {
        Path snapshot=transport.capture(path,role,limits.bytes());readCaptured(snapshot,role,required,consumer);
    }
    private void readCaptured(Path snapshot,String role,List<String> required,Row consumer)throws Exception {
        context=role+": "+snapshot;
        try(var stream=new BufferedInputStream(Files.newInputStream(snapshot),8192)) {
            String first=line(stream);if(first==null)throw new IOException("Missing Fisher TSV header: "+context);
            var header=List.of(first.split("\t",257));
            if(header.size()>256||new HashSet<>(header).size()!=header.size()||header.stream().anyMatch(s->s.isBlank()||s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Invalid/duplicate Fisher TSV header (maximum256 columns): "+context);
            for(String name:required)if(!header.contains(name))throw new IOException("Missing Fisher TSV column "+name+": "+context);
            count(header.size());String value;int n=0;
            while((value=line(stream))!=null) {
                check.run();context=role+": "+snapshot+", line "+(n+2);String[] fields=value.split("\t",257);
                if(fields.length!=header.size()||Arrays.stream(fields).anyMatch(s->s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Malformed Fisher TSV row: "+context);
                if(++rows>limits.rows())throw new IOException("Combined Fisher --max-rows exceeded: "+context);count(fields.length);consumer.read(fields,header,++n);
            }
        }
    }
    private String line(BufferedInputStream stream)throws IOException {
        var bytes=new ByteArrayOutputStream();int value;boolean any=false;
        while((value=stream.read())!=-1){any=true;if(value=='\n')break;if(bytes.size()>=limits.line())throw new IOException("Fisher input exceeds --max-line-bytes: "+context);bytes.write(value);if((bytes.size()&8191)==0)check.run();}
        if(!any)return null;byte[] raw=bytes.toByteArray();int size=raw.length;if(size>0&&raw[size-1]=='\r')size--;
        try{return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,size)).toString();}catch(IOException invalid){throw new IOException("Invalid Fisher UTF-8: "+context,invalid);}
    }
    private void count(long n)throws IOException{if(n>limits.cells()-cells)throw new IOException("Combined Fisher --max-cells exceeded: "+context);cells+=n;}
    static String field(String[] row,List<String> header,String key){return row[header.indexOf(key)];}
    static String identity(String value)throws IOException{if(value==null||value.isBlank()||value.length()>1024||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require bounded printable Fisher identity/metadata");return value;}
    static long integer(String value)throws IOException {
        if(!value.matches("[0-9]+"))throw new IOException("Require an exact nonnegative decimal integer Fisher count/row/limit: "+value);
        try{return Long.parseLong(value);}catch(NumberFormatException invalid){throw new IOException("Fisher integer exceeds supported range",invalid);}
    }
    static double decimal(String raw)throws IOException {
        if(!raw.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require a finite decimal Fisher value");
        double value=Double.parseDouble(raw);if(!Double.isFinite(value)||value==0&&new java.math.BigDecimal(raw).signum()!=0)throw new IOException("Fisher decimal overflows or underflows binary64");return value;
    }
    private static boolean bool(String value)throws IOException{if(value.equals("true"))return true;if(value.equals("false"))return false;throw new IOException("Require literal true/false in known-association evidence");}
    private static <T extends Enum<T>> T policy(Class<T> type,String value)throws IOException {
        try{return Enum.valueOf(type,value);}catch(IllegalArgumentException invalid){throw new IOException("Unknown explicit Fisher "+type.getSimpleName()+" policy: "+value,invalid);}
    }
    Prepared prepare(Map<String,String> options,FumaFisherGroups.Options groupOptions,int maximumBundleFiles)throws Exception {
        var definitions=new LinkedHashMap<String,Definition>();var planned=new LinkedHashMap<String,List<String>>();
        read(Path.of(options.get("families")),"Fisher families",List.of("family","correction","encoding","missing","empty","pseudocount","confidence"),(r,h,n)->{
            String id=identity(field(r,h,"family"));var d=new Definition(id,policy(FumaFisherFamilies.Correction.class,field(r,h,"correction")),policy(FumaFisherFamilies.Encoding.class,field(r,h,"encoding")),policy(FumaFisherFamilies.Missing.class,field(r,h,"missing")),policy(FumaFisherFamilies.Empty.class,field(r,h,"empty")),Math.toIntExact(integer(field(r,h,"pseudocount"))),decimal(field(r,h,"confidence")));
            if(definitions.putIfAbsent(id,d)!=null)throw new IOException("Duplicate Fisher family ID");planned.put(id,new ArrayList<>());
        });
        read(Path.of(options.get("plan")),"Fisher planned comparisons",List.of("family","test_id"),(r,h,n)->{
            String id=field(r,h,"family");if(!planned.containsKey(id))throw new IOException("Unknown planned Fisher family: "+id);planned.get(id).add(identity(field(r,h,"test_id")));
        });
        var families=new ArrayList<FumaFisherFamilies.Family>();for(var d:definitions.values()){check.run();families.add(new FumaFisherFamilies.Family(d.id(),planned.get(d.id()),d.correction(),d.encoding(),d.missing(),d.empty(),d.pseudocount(),d.confidence()));}
        var tables=new ArrayList<FumaFisherFamilies.Table>();
        if(options.get("mode").equals("counts")) {
            read(Path.of(options.get("counts")),"Fisher count tables",List.of("family","test_id","trait","context","finer_novel","finer_known","comparator_novel","comparator_known"),(r,h,n)->{
                var values=new ArrayList<String>();for(String key:List.of("finer_novel","finer_known","comparator_novel","comparator_known"))values.add(field(r,h,key));
                long missing=values.stream().filter("NA"::equals).count();if(missing!=0&&missing!=4)throw new IOException("Fisher missing counts require all four cells to be literal NA;partial missing tables are invalid");
                var counts=missing==4?null:new FumaFisherExact.Counts(integer(values.get(0)),integer(values.get(1)),integer(values.get(2)),integer(values.get(3)));
                tables.add(new FumaFisherFamilies.Table(field(r,h,"family"),field(r,h,"test_id"),field(r,h,"trait"),field(r,h,"context"),counts));
            });
            return new Prepared(List.copyOf(families),List.copyOf(tables),null,Map.of(),List.of(),List.of());
        }
        var bundle=bundle(Path.of(options.get("known-bundle")),options,maximumBundleFiles);
        var members=new LinkedHashMap<String,List<FumaFisherGroups.Member>>();
        read(Path.of(options.get("groups")),"Fisher group domains",List.of("group","source","trait","context","expected_rows"),(r,h,n)->members.computeIfAbsent(identity(field(r,h,"group")),ignored->new ArrayList<>()).add(new FumaFisherGroups.Member(field(r,h,"source"),field(r,h,"trait"),field(r,h,"context"),Math.toIntExact(integer(field(r,h,"expected_rows"))))));
        var groups=new ArrayList<FumaFisherGroups.Group>();for(var e:members.entrySet()){check.run();groups.add(new FumaFisherGroups.Group(e.getKey(),e.getValue()));}
        var comparisons=new ArrayList<FumaFisherGroups.Comparison>();
        read(Path.of(options.get("comparisons")),"Fisher group comparisons",List.of("family","test_id","trait","context","finer_group","comparator_group","overlap"),(r,h,n)->comparisons.add(new FumaFisherGroups.Comparison(field(r,h,"family"),field(r,h,"test_id"),field(r,h,"trait"),field(r,h,"context"),field(r,h,"finer_group"),field(r,h,"comparator_group"),policy(FumaFisherGroups.Overlap.class,field(r,h,"overlap")))));
        var grouped=FumaFisherGroups.count(bundle.observations(),groups,comparisons,groupOptions,check);
        return new Prepared(List.copyOf(families),grouped.tables(),grouped,bundle.metadata(),bundle.sources(),bundle.warnings());
    }
    private Map<?,?> metadata(Path snapshot)throws IOException{return AdmixtureInputs.decode(AdmixtureInputs.raw(snapshot,1048576));}
    private Bundle bundle(Path supplied,Map<String,String> options,int maximumFiles)throws Exception {
        Path root=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root))throw new IOException("Known-association bundle is missing or linked: "+root);
        var manifestPath=transport.capture(root.resolve("manifest.yaml"),"known-association manifest",1048576);
        var loader=new LoaderOptions();loader.setAllowDuplicateKeys(false);loader.setMaxAliasesForCollections(0);loader.setCodePointLimit(1048576);loader.setNestingDepthLimit(32);
        Object loaded=new Yaml(new SafeConstructor(loader)).load(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(AdmixtureInputs.raw(manifestPath,1048576))).toString());
        if(!(loaded instanceof Map<?,?> manifest)||!(manifest.get("schema_version") instanceof Number schema)||schema.doubleValue()!=1||!"fuma-known-associations".equals(manifest.get("operation"))||!"completed-with-review-warnings".equals(manifest.get("analysis_status"))||!(manifest.get("output_sha256") instanceof Map<?,?> outputs))throw new IOException("Require a completed fuma-known-associations schema1 manifest with output hashes");
        if(outputs.size()>maximumFiles)throw new IOException("Known-association bundle exceeds --max-bundle-files");
        var snapshots=new LinkedHashMap<String,Path>();var digests=new LinkedHashMap<String,String>();
        for(var entry:outputs.entrySet()) {
            check.run();if(!(entry.getKey() instanceof String raw)||!(entry.getValue() instanceof String digest)||!digest.matches("[0-9a-f]{64}"))throw new IOException("Invalid known-association output hash/member");
            String name=raw.replace('\\','/');if(name.isBlank()||name.startsWith("/")||name.contains(":")||name.contains("//")||Arrays.stream(name.split("/",-1)).anyMatch(p->p.isBlank()||p.equals(".")||p.equals(".."))||name.equals("manifest.yaml")||name.codePoints().anyMatch(Character::isISOControl))throw new IOException("Unsafe known-association bundle member: "+raw);
            Path file=root.resolve(name).normalize();if(!file.startsWith(root))throw new IOException("Known-association member escapes bundle");
            for(Path part=file;!part.equals(root);part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Linked known-association bundle member: "+raw);
            Path snapshot=transport.capture(file,"known-association member "+name,limits.bytes());if(!digest.equals(transport.hashes.get(file)))throw new IOException("Known-association output SHA-256 differs: "+raw);
            if(snapshots.putIfAbsent(name,snapshot)!=null)throw new IOException("Duplicate normalized known-association bundle member");digests.put(name,digest);
        }
        Set<String> required=Set.of("session.json","analysis-status.json","WARNINGS.md","resource-ledger.tsv","analysis-sources.tsv","all-tests.tsv","family-audit.tsv","family-members.tsv","known-union.tsv","known-contributors.tsv","target-decisions.tsv","baseline-coverage.tsv","known-links.tsv");
        if(!snapshots.keySet().containsAll(required))throw new IOException("Incomplete known-association bundle: required scientific/status ledgers are missing");
        try(var files=Files.walk(root)){var iterator=files.iterator();long entries=0;while(iterator.hasNext()){check.run();Path file=iterator.next();if(++entries>2L*maximumFiles+2)throw new IOException("Known-association bundle directory population exceeds admission bound");if(Files.isSymbolicLink(file))throw new IOException("Linked file/directory in known-association bundle");if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&!file.equals(root.resolve("manifest.yaml"))&&!snapshots.containsKey(root.relativize(file).toString().replace('\\','/')))throw new IOException("Unmanifested known-association bundle file: "+file);}}
        var session=metadata(snapshots.get("session.json"));var status=metadata(snapshots.get("analysis-status.json"));
        if(!"completed-with-review-warnings".equals(session.get("status"))||!"completed-with-review-warnings".equals(status.get("status"))||!(status.get("exit_code") instanceof Number code)||code.doubleValue()!=0)throw new IOException("Known-association completion/status markers disagree");
        for(String key:List.of("matching","gene-namespace","trait-namespace"))if(!options.get(key).equals(session.get(key)))throw new IOException("Known-association source "+key+" differs;recompute original classifications explicitly before changing matching or namespaces");
        var sourceRoles=new LinkedHashMap<String,String>();var sources=new ArrayList<String[]>();
        readCaptured(snapshots.get("analysis-sources.tsv"),"imported association sources",List.of("source","role","model","resource","gene_namespace","trait_namespace","gene_annotation","genome_build"),(r,h,n)->{
            String id=identity(field(r,h,"source")),role=field(r,h,"role");if(!Set.of("TARGET","BASELINE").contains(role)||sourceRoles.putIfAbsent(id,role)!=null)throw new IOException("Invalid/duplicate imported association source");
            if(!options.get("gene-namespace").equals(field(r,h,"gene_namespace"))||options.get("matching").equals("gene-trait")&&!options.get("trait-namespace").equals(field(r,h,"trait_namespace")))throw new IOException("Imported source namespaces differ from original matching contract");
            var fields=new ArrayList<String>();for(String key:List.of("source","role","model","resource","gene_namespace","trait_namespace","gene_annotation","genome_build"))fields.add(identity(field(r,h,key)));sources.add(fields.toArray(String[]::new));
        });
        var originals=new ArrayList<Original>();var targets=new ArrayList<Integer>();
        readCaptured(snapshots.get("all-tests.tsv"),"imported association tests",List.of("input_row","family","source","role","test_id","gene","trait","context","input_value","selected"),(r,h,n)->{
            int row=Math.toIntExact(integer(field(r,h,"input_row")));String source=field(r,h,"source"),role=field(r,h,"role");if(row!=n||!role.equals(sourceRoles.get(source)))throw new IOException("Imported test row/source provenance differs");
            boolean target=role.equals("TARGET"),missing=field(r,h,"input_value").equals("NA"),selected=bool(field(r,h,"selected"));if(missing&&selected)throw new IOException("Missing imported association test is selected");
            originals.add(new Original(row,source,identity(field(r,h,"family")),identity(field(r,h,"test_id")),identity(field(r,h,"gene")),identity(field(r,h,"trait")),identity(field(r,h,"context")),target,missing,selected));if(target)targets.add(row);
        });
        var observations=new ArrayList<FumaFisherGroups.Observation>();
        readCaptured(snapshots.get("target-decisions.tsv"),"imported target decisions",List.of("target_input_row","source","family","test_id","gene","source_trait","source_context","matched_trait","matching","status","matched_known_tests"),(r,h,n)->{
            int row=Math.toIntExact(integer(field(r,h,"target_input_row")));if(n>targets.size()||row!=targets.get(n-1))throw new IOException("Incomplete,duplicate or reordered imported target decisions");var original=originals.get(row-1);
            for(var pair:List.of(new String[]{"source",original.source()},new String[]{"family",original.family()},new String[]{"test_id",original.test()},new String[]{"gene",original.gene()},new String[]{"source_trait",original.trait()},new String[]{"source_context",original.context()},new String[]{"matching",options.get("matching")}))if(!pair[1].equals(field(r,h,pair[0])))throw new IOException("Imported target/test provenance differs: "+pair[0]);
            String matched=field(r,h,"matched_trait");boolean across=options.get("matching").equals("gene-across-traits");if(across?!matched.equals("NA"):!matched.equals(original.trait()))throw new IOException("Imported target matching-key provenance differs");
            var decision=policy(FumaKnownAssociations.Decision.class,field(r,h,"status"));boolean excluded=decision==FumaKnownAssociations.Decision.MISSING_TARGET_VALUE||decision==FumaKnownAssociations.Decision.TARGET_NOT_SELECTED;
            if(decision==FumaKnownAssociations.Decision.MISSING_TARGET_VALUE?!original.missing()||original.selected():decision==FumaKnownAssociations.Decision.TARGET_NOT_SELECTED?original.missing()||original.selected():original.missing()||!original.selected())throw new IOException("Imported target decision/selection provenance differs");
            String hits=field(r,h,"matched_known_tests");if(excluded?!hits.equals("NA"):decision==FumaKnownAssociations.Decision.KNOWN?integer(hits)<1:integer(hits)!=0)throw new IOException("Imported target matched-known count/decision differs");
            observations.add(new FumaFisherGroups.Observation(row,original.source(),original.trait(),original.context(),new FumaKnownAssociations.Key(original.gene(),across?null:original.trait()),decision));
        });
        if(observations.size()!=targets.size())throw new IOException("Missing imported target decisions");
        if(!(session.get("test_rows") instanceof Number tests)||tests.longValue()!=originals.size()||!(session.get("target_rows") instanceof Number targetCount)||targetCount.longValue()!=observations.size()||!(session.get("source_rows") instanceof Number sourceCount)||sourceCount.longValue()!=sources.size())throw new IOException("Known-association session population counts differ");
        var imported=new LinkedHashMap<String,Object>();for(String key:List.of("analysis-id","gene-namespace","trait-namespace","matching","absence","inclusive_adjusted_cutoff")){if(!session.containsKey(key))throw new IOException("Missing known-association scientific metadata: "+key);imported.put(key,session.get(key));}imported.put("bundle",root.toString());imported.put("manifest_sha256",transport.hashes.get(root.resolve("manifest.yaml")));imported.put("member_sha256",digests);imported.put("test_rows",originals.size());imported.put("target_rows",observations.size());
        var warnings=new ArrayList<String>();warnings.add("IMPORTED KNOWN-ASSOCIATION CLASSIFICATIONS: completion markers,all manifest members and raw snapshots were verified;original selected decisions and matching keys are reused without recomputing upstream adjusted values. Required action: review the original bundle WARNINGS.md and source namespaces/build labels;SHA-256 integrity is not independent scientific certification or historical paper equivalence.");
        if(!(session.get("warnings") instanceof List<?> originalWarnings))throw new IOException("Missing upstream known-association review warnings");for(Object warning:originalWarnings){check.run();if(!(warning instanceof String value))throw new IOException("Invalid upstream known-association warning");warnings.add("UPSTREAM KNOWN ASSOCIATIONS: "+value);}
        return new Bundle(List.copyOf(observations),imported,List.copyOf(sources),List.copyOf(warnings));
    }
    void verify()throws Exception{transport.verify();}
    String context(){return context;}
    long cells(){return cells;}
}
