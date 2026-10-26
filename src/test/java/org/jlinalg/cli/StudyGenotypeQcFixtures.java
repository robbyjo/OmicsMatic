/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
final class StudyGenotypeQcFixtures {
    private StudyGenotypeQcFixtures() { }
    static Path resource(String name)throws Exception{return Path.of(StudyGenotypeQcFixtures.class.getResource("/genotype-sample-qc/"+name).toURI());}
    static Context context(Path root,String name,Map<String,Object> overrides,Map<String,Object> association,Map<String,Object> metadata,boolean imputed,boolean separate)throws Exception {
        Path project=Files.createDirectories(root.resolve(name));var source=DelimitedData.readResult(resource("hard-calls.tsv"));var ids=source.header().subList(5,source.header().size());
        Files.copy(resource("phenotype.tsv"),project.resolve("phenotype.tsv"));
        var panel=new StringBuilder("##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");var reverse=new ArrayList<>(ids);java.util.Collections.reverse(reverse);for(String id:reverse)panel.append('\t').append(id);panel.append('\n');
        for(String[] row:source.rows()){panel.append(row[1]).append('\t').append(row[2]).append('\t').append(row[0]).append("\tA\tG\t.\tPASS\t.\tGT");for(String id:reverse){String value=row[source.column(id)];panel.append('\t').append(value.equals("NA")?"./.":value.equals("0")?"0/0":value.equals("1")?"0/1":"1/1");}panel.append('\n');}
        Files.writeString(project.resolve("panel.vcf"),panel);
        var main=new StringBuilder("##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");for(String id:ids)main.append('\t').append(id);main.append('\n');int variants=0;
        for(String[] row:source.rows()) {
            int count=0,sum=0;for(String id:ids){String value=row[source.column(id)];if(!value.equals("NA")){count++;sum+=Integer.parseInt(value);}}
            if(count!=ids.size()||Math.min(sum,2*count-sum)<32)continue;
            main.append(row[1]).append('\t').append(row[2]).append("\tassoc").append(++variants).append("\tA\tG\t.\tPASS\tR2=0.99\t").append(imputed?"DS":"GT");
            for(String id:ids){int g=Integer.parseInt(row[source.column(id)]);main.append('\t').append(imputed?Double.toString(g*.98+.02):g==0?"0/0":g==1?"0/1":"1/1");}main.append('\n');if(variants==3)break;
        }
        main.append("1\t9999999\trare\tA\tG\t.\tPASS\tR2=0.99\t").append(imputed?"DS":"GT");for(int i=0;i<ids.size();i++)main.append('\t').append(imputed?(i==10||i==20||i==30?"1":"0"):(i==10||i==20||i==30?"0/1":"0/0"));main.append('\n');Files.writeString(project.resolve("main.vcf"),main);
        var policy=new LinkedHashMap<String,Object>(Map.of("genome_build","GRCh38","rationale","Synthetic software verification only","relatedness_action","exclude","include_pcs",true,"pca_components",2,"panel_markers",2048));policy.putAll(overrides);
        var options=new LinkedHashMap<String,Object>(Map.of("model","ols"));options.putAll(association);if(imputed)options.put("genotype_kind","imputed");
        var files=new LinkedHashMap<String,Object>(Map.of("matrix",separate?"main.vcf":imputed?"main.vcf":"panel.vcf","phenotype","phenotype.tsv"));if(separate)files.put("qc_genotypes","panel.vcf");if("lmm".equals(options.get("model"))) {files.put("grm","grm.tsv");Files.writeString(project.resolve("grm.tsv"),"Supplied covariance is verified by its downstream model consumer, not by this panel screen.\n");}
        boolean include=Boolean.TRUE.equals(policy.get("include_pcs"));var hypothesis=Map.<String,Object>of("id","H","outcome","y","covariates",include?List.of("age","GPC1","GPC2"):List.of("age"),"analysis",Map.of("mode","manual","goals",List.of("association"),"association",options,"genotype_sample_qc",policy));
        var dataset=Map.<String,Object>of("id","a","type","gwas","stage","processed_matrix","files",files,"columns",Map.of("sample_id","id"),"metadata",metadata);
        return new Context(project,Files.createDirectories(project.resolve("qc")),Map.of("id","qc","type","participant_qc","dataset_id","a","datasets",List.of("a"),"hypothesis","H"),Map.of("datasets",List.of(dataset),"hypotheses",List.of(hypothesis)),Map.of(),Map.of());
    }
}
