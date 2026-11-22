/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independent four-lead fixture: known, covered empty, uncovered empty and other trait. */
final class FumaNoveltyTestData {
    private FumaNoveltyTestData() { }
    static Map<String,String> options(Path base)throws Exception {
        Files.createDirectories(base);
        Files.writeString(base.resolve("leads.tsv"),"locus\ttrait\tchromosome\tposition\tinput_note\nL-known\ttrait-a\t1\t500000\tα\nL-empty\ttrait-a\t1\t2000000\tβ\nL-uncovered\ttrait-b\t2\t1000\tγ\nL-other-trait\ttrait-b\t1\t500000\tδ\n");
        Files.writeString(base.resolve("known.tsv"),"variant\ttrait\tchromosome\tposition\tstudy\nright\ttrait-a\t1\t750000\tstudy-one\nleft\ttrait-a\t1\t250000\tstudy-two\ncenter\ttrait-a\t1\t500000\tstudy-three\ncenter\ttrait-a\t1\t500000\tstudy-four\nother-trait\ttrait-c\t1\t500001\tstudy-five\noutside-chromosome\ttrait-a\t3\t100\tstudy-six\n");
        Files.writeString(base.resolve("coverage.tsv"),"trait\tchromosome\tstart\tend\ntrait-a\t1\t1\t1500000\ntrait-a\t1\t1500001\t2500000\ntrait-b\t1\t1\t1000000\n");
        var o=new LinkedHashMap<String,String>();o.put("method","locus-novelty");o.put("build","GRCh37");o.put("known-build","GRCh37");o.put("catalog-id","synthetic-known-v1");o.put("analysis-id","synthetic-leads-v1");o.put("radius","250000");o.put("boundary","inclusive");o.put("trait-policy","exact");for(String key:new String[]{"leads","known","coverage"})o.put(key,base.resolve(key+".tsv").toString());o.put("out",base.resolve("analysis").toString());return o;
    }
    static Path carrier(Path path,Map<?,?> values)throws Exception {Files.writeString(path,RegenieRuntime.json(values)+"\n");return path;}
    static Path parent(Path base,Map<String,String> supplied,Map<String,Object> other)throws Exception {
        var stage=new LinkedHashMap<>(supplied);stage.remove("out");stage.remove("build");var recipe=new LinkedHashMap<String,Object>();recipe.put("schema_version",1);recipe.put("build","GRCh37");recipe.put("out","workflow");recipe.put("locus_novelty",stage);recipe.putAll(other);return carrier(base.resolve("parent.json"),recipe);
    }
}
