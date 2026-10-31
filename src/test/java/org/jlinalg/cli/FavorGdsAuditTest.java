/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.FavorVcfAudit;
import org.jlinalg.genetics.FavorVcfReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FavorGdsAuditTest {
    @TempDir Path directory;
    private int serial;
    private static final String SHA="0".repeat(64),OTHER="1".repeat(64),ANNOTATION="annotation/info/FunctionalAnnotation";
    private static final FavorGdsAudit.Limits LIMITS=new FavorGdsAudit.Limits(1048576,1000,100000,1000000);
    private record Fixture(FavorVcfAudit.Result expected,Map<String,Object> root,Map<String,Map<String,Object>> nodes) { }
    private static Map<String,Object> node(List<Integer> dimensions,boolean hidden) {
        var m=new LinkedHashMap<String,Object>();boolean array=!dimensions.isEmpty();long n=array?1:0;for(int d:dimensions)n*=d;
        m.put("storage",array?"dInt32":"");m.put("trait",array?"Int32":"");m.put("type",array?"Integer":"Folder");m.put("is_array",array);m.put("is_sparse",false);m.put("dimensions",dimensions);m.put("encoder",array?"LZMA_ra":null);m.put("compression",array?"LZMA_RA.def:256K":null);m.put("hidden",hidden);m.put("parameter_sha256",SHA);m.put("attributes_sha256",SHA);m.put("values",n);m.put("data_sha256",array?SHA:null);return m;
    }
    private Fixture fixture()throws Exception {
        Path vcf=directory.resolve("input-"+(serial++)+".vcf");Files.writeString(vcf,"##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS\n22\t100\tx\tA\tG\t.\tPASS\t.\tGT\t0|1\n",StandardCharsets.UTF_8);
        var expected=FavorVcfAudit.scan(vcf,new FavorVcfReader.Options(FavorVcfReader.Mode.DIPLOID_GT,22,FavorVcfReader.Chromosomes.EXACT,FavorVcfReader.Limits.defaults()),()->{});
        var hashes=new LinkedHashMap<String,Object>();for(var e:expected.digests().entrySet())hashes.put(e.getKey(),Map.of("kind",e.getValue().kind().name(),"values",e.getValue().values(),"sha256",e.getValue().sha256()));
        var nodes=new LinkedHashMap<String,Map<String,Object>>();for(String name:List.of("sample.id","variant.id","chromosome","position","allele"))nodes.put(name,node(List.of(1),false));for(String name:List.of("annotation","annotation/info","genotype","phase"))nodes.put(name,node(List.of(),false));nodes.put("annotation/id",node(List.of(1),false));nodes.put("genotype/data",node(List.of(2,1,1),false));nodes.put("phase/data",node(List.of(1,1),false));nodes.put("genotype/@data",node(List.of(1),true));
        var root=new LinkedHashMap<String,Object>();root.put("schema",FavorVcfAudit.SCHEMA);root.put("mode","DIPLOID_GT");root.put("variants",1);root.put("samples",1);root.put("digests",hashes);root.put("canonical_bytes",100);root.put("root_attributes_sha256",SHA);root.put("all_nodes_including_hidden",nodes);root.put("runtime",Map.of("R","R4.4.3","gdsfmt","1.42.0","SeqArray","1.46.0","digest","0.6.39"));return new Fixture(expected,root,nodes);
    }
    private FavorGdsAudit.Snapshot read(Fixture f)throws Exception {Path p=directory.resolve("audit-"+(serial++)+".json");Files.writeString(p,RegenieRuntime.json(f.root()),StandardCharsets.UTF_8);return FavorGdsAudit.read(p,LIMITS,()->{});}
    private static void annotate(Fixture f){f.nodes().put(ANNOTATION,node(List.of(),false));f.nodes().put(ANNOTATION+"/FunctionalAnnotation",node(List.of(),false));f.nodes().put(ANNOTATION+"/FunctionalAnnotation/VarInfo",node(List.of(1),false));}
    @Test void completeExpectedValuesAndAllOriginalHiddenNodesArePreserved()throws Exception {var f=fixture();var before=read(f);annotate(f);var after=read(f);assertEquals(new FavorGdsAudit.Preservation(13,1,3,2,1),FavorGdsAudit.preserved(f.expected(),before,after));}
    @Test void hiddenIndexValueChangesCannotPassGenotypeOnlyComparison()throws Exception {var f=fixture();var before=read(f);annotate(f);f.nodes().get("genotype/@data").put("data_sha256",OTHER);var after=read(f);FavorGdsAudit.matches(f.expected(),after);assertTrue(assertThrows(IOException.class,()->FavorGdsAudit.preserved(f.expected(),before,after)).getMessage().contains("genotype/@data"));}
    @Test void originalAttributesStorageAndVisibilityAreProtected()throws Exception {
        for(String key:List.of("attributes_sha256","storage","hidden")){var f=fixture();var before=read(f);annotate(f);f.nodes().get("genotype/data").put(key,key.equals("hidden")?true:key.equals("storage")?"dInt64":OTHER);var after=read(f);assertThrows(IOException.class,()->FavorGdsAudit.preserved(f.expected(),before,after));}
    }
    @Test void rootAttributesAndRuntimeMustRemainIdentical()throws Exception {for(String key:List.of("root_attributes_sha256","runtime")){var f=fixture();var before=read(f);annotate(f);f.root().put(key,key.equals("runtime")?Map.of("R","Rchanged","gdsfmt","1.42.0","SeqArray","1.46.0","digest","0.6.39"):OTHER);var after=read(f);assertThrows(IOException.class,()->FavorGdsAudit.preserved(f.expected(),before,after));}}
    @Test void originalNodeRemovalAndUnrelatedNewNodesAreRefused()throws Exception {for(boolean removal:List.of(true,false)){var f=fixture();var before=read(f);annotate(f);if(removal)f.nodes().remove("genotype/@data");else f.nodes().put("unexpected",node(List.of(1),false));var after=read(f);assertThrows(IOException.class,()->FavorGdsAudit.preserved(f.expected(),before,after));}}
    @Test void originalInputMustNotAlreadyContainAnnotation()throws Exception {var f=fixture();annotate(f);var before=read(f);assertThrows(IOException.class,()->FavorGdsAudit.preserved(f.expected(),before,before));}
    @Test void missingSourceAnnotationCannotBeReportedAsCompleted()throws Exception {var f=fixture();var before=read(f);assertThrows(IOException.class,()->FavorGdsAudit.preserved(f.expected(),before,before));}
    @Test void completeValueDigestMismatchIsRefused()throws Exception {var f=fixture();var expected=f.expected();var digestMap=new LinkedHashMap<String,Object>();for(var e:expected.digests().entrySet())digestMap.put(e.getKey(),Map.of("kind",e.getValue().kind().name(),"values",e.getValue().values(),"sha256",e.getKey().equals("phase")?OTHER:e.getValue().sha256()));f.root().put("digests",digestMap);var actual=read(f);assertThrows(IOException.class,()->FavorGdsAudit.matches(expected,actual));}
    @Test void completePopulationAndSerializationKindsAreChecked()throws Exception {for(String key:List.of("values","kind")){var f=fixture();var expected=f.expected();var digestMap=new LinkedHashMap<String,Object>();for(var e:expected.digests().entrySet()){var value=new LinkedHashMap<String,Object>();value.put("kind",e.getValue().kind().name());value.put("values",e.getValue().values());value.put("sha256",e.getValue().sha256());if(e.getKey().equals("genotype"))value.put(key,key.equals("values")?1:"UTF8_LENGTH_INT32_LE");digestMap.put(e.getKey(),value);}f.root().put("digests",digestMap);assertThrows(IOException.class,()->read(f));}}
    @Test void invalidDimensionsAndIncompleteParentNodesAreRefused()throws Exception {for(boolean missingParent:List.of(true,false)){var f=fixture();if(missingParent)f.nodes().remove("genotype");else f.nodes().get("genotype/data").put("dimensions",List.of(1,1,1));assertThrows(IOException.class,()->read(f));}}
    @Test void unknownMissingAndDuplicateJsonFieldsAreRefused()throws Exception {
        var f=fixture();f.root().put("unknown",1);assertThrows(IOException.class,()->read(f));f.root().remove("unknown");f.root().remove("schema");assertThrows(IOException.class,()->read(f));Path p=directory.resolve("duplicate.json");Files.writeString(p,"{\"schema\":\"a\",\"schema\":\"b\"}");assertThrows(IOException.class,()->FavorGdsAudit.read(p,LIMITS,()->{}));
    }
    @Test void allNodeAndCanonicalByteLimitsAreEnforced()throws Exception {
        var f=fixture();Path p=directory.resolve("limits.json");Files.writeString(p,RegenieRuntime.json(f.root()));for(var limit:List.of(new FavorGdsAudit.Limits(10,1000,100000,1000000),new FavorGdsAudit.Limits(1048576,1,100000,1000000),new FavorGdsAudit.Limits(1048576,1000,1,1000000),new FavorGdsAudit.Limits(1048576,1000,100000,1)))assertThrows(IOException.class,()->FavorGdsAudit.read(p,limit,()->{}));
    }
    @Test void parsedSnapshotsAreDeeplyImmutable()throws Exception {var s=read(fixture());assertThrows(UnsupportedOperationException.class,()->s.nodes().clear());assertThrows(UnsupportedOperationException.class,()->s.digests().clear());assertThrows(UnsupportedOperationException.class,()->s.nodes().get("genotype/data").dimensions().clear());assertThrows(UnsupportedOperationException.class,()->s.runtime().clear());}
    @Test void cancelledIntakeReturnsNoSnapshot()throws Exception {var f=fixture();Path p=directory.resolve("cancel.json");Files.writeString(p,RegenieRuntime.json(f.root()));assertThrows(IllegalStateException.class,()->FavorGdsAudit.read(p,LIMITS,()->{throw new IllegalStateException("cancelled");}));}
    @Test void malformedUtf8OrNonfiniteCountsAreNotRepaired()throws Exception {var f=fixture();f.root().put("variants",1.5);assertThrows(IOException.class,()->read(f));Path p=directory.resolve("utf8.json");Files.write(p,new byte[]{(byte)0xff});assertThrows(IOException.class,()->FavorGdsAudit.read(p,LIMITS,()->{}));}
    @Test void invalidGdsCapacitiesAreRefused() {assertThrows(IllegalArgumentException.class,()->new FavorGdsAudit.Limits(0,1,1,1));assertThrows(IllegalArgumentException.class,()->new FavorGdsAudit.Limits(1,0,1,1));assertThrows(IllegalArgumentException.class,()->new FavorGdsAudit.Limits(1,1,0,1));assertThrows(IllegalArgumentException.class,()->new FavorGdsAudit.Limits(1,1,1,Long.MAX_VALUE));}
}
