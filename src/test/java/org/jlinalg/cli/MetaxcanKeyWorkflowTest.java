/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.MetaxcanSummary.map;

class MetaxcanKeyWorkflowTest {
    @TempDir Path temporary;
    Map<?,?> fixtures()throws Exception {try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/metaxcan/keys/original-key-outputs.json")){assertNotNull(in);return object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}}
    Path inputs(String name,Map<?,?> source)throws Exception {
        Path root=temporary.resolve(name);Files.createDirectory(root);Files.createDirectories(root.resolve("inputs/models"));Files.createDirectory(root.resolve("outputs"));
        for(var entry:Map.of("model","inputs/models/Selected.db","gwas","inputs/gwas.tsv","covariance","inputs/covariance.tsv").entrySet())try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/metaxcan/keys/inputs/"+source.get(entry.getKey()))){assertNotNull(in);Files.copy(in,root.resolve(entry.getValue()));}return root;
    }
    Map<String,Object> request(String edition,Object key) {
        var r=MetaxcanToolsCli.example("summary");r.put("edition",edition);r.put("capacity",MetaxcanInputTest.request().get("capacity"));var data=map("models",List.of(map("name","Selected","file","/declared/Selected.db")),"gwas",map("file","/declared/gwas.tsv","compression","none","separator","tab","columns",map("snp","SNP_ID","effect_allele","EA","non_effect_allele","OA","zscore",null,"beta","BETA","se","SE","pvalue",null,"odds_ratio",null,"beta_sign",null)),"covariance",map("file","/declared/covariance.tsv","compression","none"));r.put("data",data);
        var settings=map("calibration",map("mode","source-uncalibrated"),"joint_cutoff",null,"regularization",null,"keep_non_rsid",true,"pvalue_policy","source-default-divergent-floor","model_key",map("column",key,"identifier_handling",key==null?"unchanged":"source-b38-first-row-rule"),"additional_output",true);r.put("settings",settings);return r;
    }
    Map<String,Map<String,String>> rows(Path file)throws Exception {
        var result=new LinkedHashMap<String,Map<String,String>>();try(var table=new MetaxcanTable(file,false,"tab",100,new MetaxcanTable.Budget(1048576,()->{}),true)){for(List<String> row;(row=table.next())!=null;){var r=new LinkedHashMap<String,String>();for(int i=0;i<row.size();i++)r.put(table.header().get(i),row.get(i));assertNull(result.put(r.get("gene"),r));}}return result;
    }
    @Test void matchesAll15UnmodifiedOriginalKeyAndAdditionalOutputCases()throws Exception {
        var fixture=fixtures();int checked=0;
        for(Object raw:list(fixture.get("records"))) {
            var original=object(raw);var c=list(fixture.get("cases")).stream().map(x->{try{return object(x);}catch(IOException e){throw new UncheckedIOException(e);}}).filter(x->x.get("name").equals(original.get("case"))).findFirst().orElseThrow();
            Path root=inputs("case"+checked,c);var spec=MetaxcanRequest.parse(request(text(original.get("source")),c.get("key")),false);var audit=MetaxcanSummary.run(root,spec,false,()->{});assertEquals(2,audit.get("schema_version"));assertEquals(Boolean.TRUE,audit.get("additional_output"));var actual=rows(root.resolve("outputs/summary.tsv"));assertEquals(2,actual.size());
            for(Object value:list(original.get("rows"))) {var expected=object(value);var row=actual.get(expected.get("gene"));assertNotNull(row);for(String column:List.of("zscore","effect_size","var_g","pvalue","n_snps_used","n_snps_in_cov","best_gwas_p","largest_weight")){double number=Double.parseDouble(text(expected.get(column)));assertEquals(number,Double.parseDouble(row.get(column)),Math.max(1e-12,Math.abs(number)*2e-10),original.get("source")+":"+original.get("case")+":"+column);}}
            var effective=new ArrayList<String>();for(String line:Files.readAllLines(root.resolve("MODEL-SNPS.jsonl"))){var record=object(SimpleJson.parse(line));effective.add(text(record.get("variant")));assertNotNull(record.get("source_variant"));}assertEquals(new HashSet<>(list(original.get("effective_ids"))),new HashSet<>(effective));
            assertTrue(Files.readString(root.resolve("MATRICES.jsonl")).contains("\"source_metadata\":{\"chr\":\"1\"}"));assertEquals(NextflowJson.json(audit),NextflowJson.json(MetaxcanSummary.run(root,spec,true,()->{})));checked++;
        }assertEquals(15,checked);
    }
    @Test void schemasAndExplicitIdentifierPolicyCannotBeMixedOrInferred()throws Exception {
        var r=request("tag-0.7.3",null);var settings=MetaxcanWorkflowTest.copy(object(r.get("settings")));settings.put("model_key",map("column","varID","identifier_handling","unchanged"));r.put("settings",settings);assertThrows(IOException.class,()->MetaxcanRequest.parse(r,false));
        settings.put("model_key",map("column","varID","identifier_handling","source-b38-first-row-rule"));assertTrue(MetaxcanRequest.parse(r,false).extended());settings.put("model_key",map("column","rsid;DROP TABLE weights","identifier_handling","source-b38-first-row-rule"));assertThrows(IOException.class,()->MetaxcanRequest.parse(r,false));
        var legacy=MetaxcanInputTest.request();assertFalse(MetaxcanRequest.parse(legacy,false).extended());var oldSettings=MetaxcanWorkflowTest.copy(object(legacy.get("settings")));oldSettings.put("additional_output",true);legacy.put("settings",oldSettings);assertThrows(IOException.class,()->MetaxcanRequest.parse(legacy,false));
    }
    @Test void missingModelKeyAndLegacyExtraColumnsAreRefused()throws Exception {
        var fixture=fixtures();var c=object(list(fixture.get("cases")).get(0));Path missing=inputs("missing-key",c);var spec=MetaxcanRequest.parse(request("tag-0.8.1","SNP_ID"),false);assertTrue(assertThrows(IOException.class,()->MetaxcanSummary.run(missing,spec,false,()->{})).getMessage().contains("absent: SNP_ID"));
        Path legacy=inputs("legacy-extra-columns",c);var r=request("tag-0.8.1",null);r.put("schema_version",1);var settings=MetaxcanWorkflowTest.copy(object(r.get("settings")));settings.remove("model_key");settings.remove("additional_output");r.put("settings",settings);var old=MetaxcanRequest.parse(r,false);assertEquals(MetaxcanBundle.PROFILE,MetaxcanBundle.profile(old));assertTrue(assertThrows(IOException.class,()->MetaxcanSummary.run(legacy,old,false,()->{})).getMessage().contains("Unexpected original covariance header"));
    }
    @Test void schema2RetainsReorderedJointMatrixMetadataAndDetectsChanges()throws Exception {
        var helper=new MetaxcanWorkflowTest();helper.temporary=temporary;Path root=helper.inputs("joint-metadata");Path p=root.resolve("inputs/model-product.tsv");var lines=Files.readAllLines(p);var transformed=new StringBuilder("origin value model2 gene model1\n");for(String line:lines.subList(1,lines.size())){var row=line.split("\\s+");transformed.append("synthetic ").append(row[3]).append(' ').append(row[2]).append(' ').append(row[0]).append(' ').append(row[1]).append('\n');}Files.writeString(p,transformed);var spec=MetaxcanRequest.parse(helper.request("summary-joint","tag-0.7.3",false),false);assertEquals(MetaxcanBundle.PROFILE_V2,MetaxcanBundle.profile(spec));MetaxcanSummary.run(root,spec,false,()->{});assertTrue(Files.readString(root.resolve("MATRICES.jsonl")).contains("\"origin\":\"synthetic\""));MetaxcanSummary.run(root,spec,true,()->{});Files.writeString(p,Files.readString(p).replace("synthetic","changed"));assertThrows(IOException.class,()->MetaxcanSummary.run(root,spec,true,()->{}));
    }
}
