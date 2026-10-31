/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class EagleSourceInputsTest {
    @TempDir Path temporary;
    private Map<String,Object> source(Path root,boolean reference,boolean index)throws Exception {
        var value=EagleTestRecipes.source(root,reference);Path inputs=root.resolve(value.get("resource_root").toString());
        for(String name:List.of("missing-bgzf.bcf","missing-bgzf.bcf.csi","reference-bgzf.bcf","reference-bgzf.bcf.csi","sites-raw.bcf","missing-raw.bcf")){
            Path target=inputs.resolve(name);if(Files.exists(target))continue;
            try(var stream=getClass().getResourceAsStream("/org/jlinalg/cli/eagle-bcf/"+name)){assertNotNull(stream,name);Files.write(target,stream.readAllBytes());}
        }
        var target=EagleTestRecipes.section(value,"target");target.put("path","missing-bgzf.bcf");target.put("format","bcf");if(index){target.put("index","missing-bgzf.bcf.csi");target.put("index_policy","snapshot-only-regenerate");}
        if(reference){var ref=new java.util.LinkedHashMap<String,Object>(Map.of("path","reference-bgzf.bcf","format","bcf"));if(index){ref.put("index","reference-bgzf.bcf.csi");ref.put("index_policy","snapshot-only-regenerate");}value.put("reference",ref);}
        return value;
    }
    private Path recipe(Path root,Map<String,Object> source)throws Exception{return EagleTestRecipes.write(root,source);}
    private int cli(Path recipe,Path output){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=EagleToolsCli.run(new String[]{"--recipe",recipe.toString(),"--out",output.toString()},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));assertTrue(err.toString(StandardCharsets.UTF_8).contains("WARNINGS.md"),err.toString(StandardCharsets.UTF_8));return code;}
    private Map<?,?> read(Path path)throws Exception{return (Map<?,?>)SimpleJson.parse(Files.readString(path));}
    @Test void nativeBcfCheckProtectsRawAndIndexAndNeverQueriesTheSourceIndex()throws Exception {
        Path path=recipe(temporary,source(temporary,false,true)),out=temporary.resolve("out-α-测");assertEquals(0,cli(path,out));assertTrue(Files.exists(out.resolve("COMPLETED.json")));assertFalse(Files.exists(out.resolve("prepared/engine")));
        Map<?,?> input=read(out.resolve("input-audit.json")),sources=(Map<?,?>)input.get("sources"),target=(Map<?,?>)((Map<?,?>)sources.get("roles")).get("target");assertEquals(false,target.get("sourceIndexConsumed"));assertEquals(false,target.get("sourceIndexCorrespondenceCertified"));
        Map<?,?> conversion=(Map<?,?>)target.get("conversion");assertEquals(128,((Number)conversion.get("genotypeCells")).longValue());assertEquals(FollowupSupport.hash(out.resolve("source-intake/target/source.bcf")),conversion.get("sourceSha256"));
        assertTrue(Files.exists(out.resolve("source-intake/target/source-index")));assertTrue(Files.exists(out.resolve("prepared/target.vcf.gz.csi")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("PROVENANCE ONLY"));
    }
    @Test void mixedVcfBcfAndAllBcfReferenceRolesUseTheSameNativeAdmission()throws Exception {
        for(boolean mixed:new boolean[]{false,true}){
            Path root=temporary.resolve(mixed?"mixed":"all");Files.createDirectory(root);var value=source(root,true,true);
            if(mixed)value.put("reference",Map.of("path","reference.vcf.gz","format","vcf"));else {value.put("exclusion",Map.of("path","sites-raw.bcf","format","bcf"));EagleTestRecipes.section(value,"policies").put("unmatched","DROP_WITH_WARNING");}
            Path path=recipe(root,value),out=root.resolve("out");int code=cli(path,out);assertEquals(0,code,Files.readString(out.resolve("WARNINGS.md")));assertTrue(Files.exists(out.resolve("COMPLETED.json")));var input=read(out.resolve("input-audit.json"));Map<?,?> roles=(Map<?,?>)((Map<?,?>)input.get("sources")).get("roles");
            assertEquals(mixed,((Map<?,?>)roles.get("reference")).get("conversion")==null);
            if(!mixed){Map<?,?> exclusion=(Map<?,?>)roles.get("exclusion");assertEquals(0,((Number)((Map<?,?>)exclusion.get("conversion")).get("samples")).longValue());assertTrue(Files.exists(out.resolve("prepared/exclusion.vcf.gz.csi")));}
        }
    }
    @Test void explicitRefusalPolicyDoesNotSilentlyDropADeclaredBcfExclusion()throws Exception {
        var value=source(temporary,true,false);value.put("exclusion",Map.of("path","sites-raw.bcf","format","bcf"));Path out=temporary.resolve("excluded-refused");assertEquals(2,cli(recipe(temporary,value),out));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("EXCLUDED_BY_SITE"));assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void headerOnlyBcfExclusionRetainsItsSourceButHasNoEngineExclusionFlag()throws Exception {
        var value=source(temporary,true,false);Path inputs=temporary.resolve(value.get("resource_root").toString());byte[] raw=Files.readAllBytes(inputs.resolve("sites-raw.bcf"));int size=ByteBuffer.wrap(raw,5,4).order(ByteOrder.LITTLE_ENDIAN).getInt();Files.write(inputs.resolve("empty.bcf"),java.util.Arrays.copyOf(raw,9+size));value.put("exclusion",Map.of("path","empty.bcf","format","bcf"));Path out=temporary.resolve("empty-out");assertEquals(0,cli(recipe(temporary,value),out));
        assertTrue(Files.exists(out.resolve("source-intake/exclusion/source.bcf")));assertFalse(Files.exists(out.resolve("prepared/exclusion.vcf.gz.csi")));var arguments=(List<?>)read(out.resolve("results.json")).get("engineArguments");assertTrue(arguments.stream().noneMatch(x->x.toString().startsWith("--vcfExclude=")));
    }
    @Test void binaryIndexSnapshotsAndConvertedTextAreAllImmutableFences()throws Exception {
        for(String changed:List.of("binary-original","binary-copy","index-original","index-copy","conversion","conversion-ledger")){
            Path root=Files.createDirectory(temporary.resolve(changed));var value=source(root,false,true);var spec=EagleRecipe.read(recipe(root,value));var input=EaglePhasing.prepare(spec,root.resolve("prepared"),()->{});var role=input.sources().roles().get("target");
            Path path=switch(changed){case "binary-original"->role.declared().path();case "binary-copy"->role.protectedBinary();case "index-original"->role.declared().index();case "index-copy"->role.protectedIndex();case "conversion"->role.data();case "conversion-ledger"->role.data().getParent().resolve("conversion.json");default->throw new IllegalStateException();};
            Files.write(path,new byte[]{0},java.nio.file.StandardOpenOption.APPEND);assertThrows(IOException.class,()->input.verify(()->{}),changed);
        }
    }
    @Test void explicitIndexSnapshotPolicyIsRequiredAndOpaqueIndexIsNotCertified()throws Exception {
        var value=source(temporary,false,true);EagleTestRecipes.section(value,"target").remove("index_policy");Path path=recipe(temporary,value);assertThrows(IOException.class,()->EagleRecipe.read(path));
        EagleTestRecipes.section(value,"target").put("index_policy","snapshot-only-regenerate");Path index=temporary.resolve(value.get("resource_root").toString()).resolve("missing-bgzf.bcf.csi");Files.writeString(index,"opaque provenance only");var input=EaglePhasing.prepare(EagleRecipe.read(recipe(temporary,value)),temporary.resolve("prepared"),()->{});input.verify(()->{});assertFalse(input.sources().roles().get("target").sourceIndexCorrespondenceCertified());assertFalse(input.sources().roles().get("target").sourceIndexConsumed());
    }
    @Test void unsupportedBcfEditionSkipsAfterProtectingTheOriginalBinary()throws Exception {
        var value=source(temporary,false,false);var target=EagleTestRecipes.section(value,"target");target.put("path","missing-raw.bcf");Path raw=temporary.resolve(value.get("resource_root").toString()).resolve("missing-raw.bcf");byte[] data=Files.readAllBytes(raw);data[4]=1;Files.write(raw,data);Path out=temporary.resolve("unsupported");assertEquals(3,cli(recipe(temporary,value),out));assertTrue(Files.exists(out.resolve("source-intake/target/source.bcf")));assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("SKIP3"));
    }
    private long bytes(Path root)throws Exception {try(var paths=Files.walk(root)){long size=0;for(Path path:paths.toList())if(Files.isRegularFile(path))size+=Files.size(path);return size;}}
    @Test void combinedSourceAndNativeWorkspaceQuotaIsEnforcedBeforeAnyProcess()throws Exception {
        Path roomy=Files.createDirectory(temporary.resolve("roomy"));var value=source(roomy,false,true);EagleTestRecipes.section(value,"process_limits").remove("workspace_bytes");var input=EaglePhasing.prepare(EagleRecipe.read(recipe(roomy,value)),roomy.resolve("prepared"),()->{});long sources=bytes(input.sources().work()),nativeBytes=bytes(input.work());long quota=Math.max(sources,nativeBytes)+Math.min(sources,nativeBytes)/2;
        Path tight=Files.createDirectory(temporary.resolve("tight"));value=source(tight,false,true);EagleTestRecipes.section(value,"process_limits").remove("workspace_bytes");EagleTestRecipes.section(value,"limits").put("workspace_bytes",quota);var spec=EagleRecipe.read(recipe(tight,value));var failure=assertThrows(IOException.class,()->EaglePhasing.prepare(spec,tight.resolve("prepared"),()->{}));assertTrue(failure.getMessage().contains("Combined"),failure.toString());assertFalse(Files.exists(tight.resolve("prepared/engine")));
    }
}
