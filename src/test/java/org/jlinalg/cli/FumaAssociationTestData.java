/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synthetic complete families with known, novel, unassessable and unselected targets. */
final class FumaAssociationTestData {
    private FumaAssociationTestData() { }
    static Map<String,String> options(Path base)throws Exception {
        Files.createDirectories(base);
        Files.writeString(base.resolve("sources.tsv"),"source\trole\tmodel\tresource\tgene_namespace\ttrait_namespace\tgene_annotation\tgenome_build\nM\tBASELINE\tsynthetic-MAGMA-model\tM-v1\tgene-v1\ttrait-v1\tannotation-v1\tGRCh37\nB\tBASELINE\tsynthetic-bulk-model\tB-v1\tgene-v1\ttrait-v1\tannotation-v2\tGRCh38\nT\tTARGET\tsynthetic-nucleus-model\tT-v1\tgene-v1\ttrait-v1\tannotation-v2\tGRCh38\n");
        Files.writeString(base.resolve("families.tsv"),"family\tsource\tencoding\tadjustment\tmissing\treported_zero\tduplicates\tprobability_definition\nA\tM\tP_VALUE\tBH\tERROR\tERROR\tERROR\tsynthetic raw test; original direction unchanged\nB\tB\tP_VALUE\tBH\tERROR\tERROR\tERROR\tsynthetic raw test; original direction unchanged\nC\tT\tP_VALUE\tBH\tERROR\tERROR\tERROR\tsynthetic raw test; original direction unchanged\n");
        Files.writeString(base.resolve("members.tsv"),"family\ttrait\tcontext\texpected_rows\nA\tT1\tmagma\t2\nA\tT2\tmagma\t2\nB\tT1\tbulk\t1\nB\tT2\tbulk\t2\nC\tT1\tclass1\t2\nC\tT2\tclass2\t2\n");
        Files.writeString(base.resolve("tests.tsv"),"family\ttest_id\tgene\ttrait\tcontext\tvalue\tsource_note\nA\ta1\tG1\tT1\tmagma\t0.001\tα\nA\ta2\tG2\tT1\tmagma\t0.04\tβ\nA\ta3\tG3\tT2\tmagma\t0.03\tγ\nA\ta4\tG4\tT2\tmagma\t0.9\tδ\nB\tb1\tG2\tT1\tbulk\t0.001\tε\nB\tb2\tG1\tT2\tbulk\t0.002\tζ\nB\tb3\tG3\tT2\tbulk\t0.5\tη\nC\tc1\tG1\tT1\tclass1\t0.001\tθ\nC\tc2\tG3\tT2\tclass2\t0.002\tι\nC\tc3\tG4\tT1\tclass1\t0.03\tκ\nC\tc4\tG2\tT2\tclass2\t0.8\tλ\n");
        var options=new LinkedHashMap<String,String>();options.put("method","known-associations");options.put("analysis-id","synthetic-v1");options.put("gene-namespace","gene-v1");options.put("trait-namespace","trait-v1");options.put("cutoff","0.05");options.put("matching","gene-trait");options.put("absence","require-baseline-tests");for(String key:new String[]{"sources","families","members","tests"})options.put(key,base.resolve(key+".tsv").toString());options.put("out",base.resolve("analysis").toString());return options;
    }
    static Path carrier(Path path,Map<?,?> values)throws Exception {Files.writeString(path,RegenieRuntime.json(values)+"\n");return path;}
    static Path parent(Path base,Map<String,String> supplied,Map<String,Object> other)throws Exception {
        var stage=new LinkedHashMap<>(supplied);stage.remove("out");var recipe=new LinkedHashMap<String,Object>();recipe.put("schema_version",1);recipe.put("build","GRCh37");recipe.put("out","workflow");recipe.put("known_associations",stage);recipe.putAll(other);return carrier(base.resolve("parent.json"),recipe);
    }
}
