/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.RsubreadSupport.*;

/** Typed bulk counting declarations; sample units and strand are mandatory per input. */
final class RsubreadContracts {
    private RsubreadContracts() { }
    static final Map<String,Boolean> BOOLEAN_DEFAULTS=Map.ofEntries(
        Map.entry("useMetaFeatures",true),Map.entry("allowMultiOverlap",false),Map.entry("countMultiMappingReads",true),
        Map.entry("fraction",false),Map.entry("ignoreDup",false),Map.entry("juncCounts",false),Map.entry("isLongRead",false),
        Map.entry("requireBothEndsMapped",false),Map.entry("checkFragLength",false),Map.entry("countChimericFragments",true),
        Map.entry("autosort",true),Map.entry("primaryOnly",false),Map.entry("splitOnly",false),Map.entry("nonSplitOnly",false),Map.entry("largestOverlap",false));
    static final Map<String,Long> INTEGER_DEFAULTS=Map.of("minOverlap",1L,"minMQS",0L,"minFragLength",50L,"maxFragLength",600L,"nthreads",1L);
    static Map<String,Object> normalize(Map<String,Object> raw) throws IOException {
        keys(raw,Set.of("schema_version","mode","out","binding","allow_execution","limits","cases"));
        if(integer(raw.get("schema_version"),1,1)!=1||!"ANALYZE".equals(raw.get("mode")))throw new IOException("Require schema_version1/mode ANALYZE");
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1L);result.put("mode","ANALYZE");
        result.put("out",text(raw.get("out")));result.put("binding",text(raw.get("binding")));result.put("allow_execution",bool(raw.getOrDefault("allow_execution",true)));
        var limits=raw.containsKey("limits")?object(raw.get("limits")):Map.<String,Object>of();keys(limits,Set.of("maximum_input_bytes","maximum_output_bytes","timeout_seconds","maximum_rows","maximum_columns","maximum_cells","maximum_text_bytes","maximum_total_text_bytes"));
        Map<String,Object> effective=new LinkedHashMap<>();
        effective.put("maximum_input_bytes",integer(limits.getOrDefault("maximum_input_bytes",512000000L),1,1000000000000L));
        effective.put("maximum_output_bytes",integer(limits.getOrDefault("maximum_output_bytes",1000000000L),1,1000000000000L));
        effective.put("timeout_seconds",integer(limits.getOrDefault("timeout_seconds",3600L),1,86400));
        effective.put("maximum_rows",integer(limits.getOrDefault("maximum_rows",1000000L),1,10000000));
        effective.put("maximum_columns",integer(limits.getOrDefault("maximum_columns",10000L),16,100000));
        effective.put("maximum_cells",integer(limits.getOrDefault("maximum_cells",25000000L),1,Integer.MAX_VALUE));
        effective.put("maximum_text_bytes",integer(limits.getOrDefault("maximum_text_bytes",1048576L),1,16777216));
        effective.put("maximum_total_text_bytes",integer(limits.getOrDefault("maximum_total_text_bytes",67108864L),1,1000000000000L));result.put("limits",effective);
        var cases=new ArrayList<Map<String,Object>>();Set<String> ids=new HashSet<>();
        for(Object value:list(raw.get("cases"))) {
            var spec=object(value);keys(spec,Set.of("id","files","annotation","genome","options"));String id=text(spec.get("id"));
            if(!id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")||!ids.add(id.toLowerCase(Locale.ROOT)))throw new IOException("Case IDs require unique portable filenames");
            var item=new LinkedHashMap<String,Object>();item.put("id",id);var files=new ArrayList<Map<String,Object>>();
            for(Object library:list(spec.get("files"))) {
                var file=object(library);keys(file,Set.of("path","paired_end","count_read_pairs","strand"));
                boolean paired=bool(file.get("paired_end")),pairs=bool(file.get("count_read_pairs"));
                files.add(Map.of("path",text(file.get("path")),"paired_end",paired,"count_read_pairs",pairs,"effective_count_read_pairs",paired&&pairs,"strand",integer(file.get("strand"),0,2)));
            }
            if(files.isEmpty()||files.size()>1000)throw new IOException("Declare1..1000 ordered input libraries per case");item.put("files",files);
            var annotation=object(spec.get("annotation"));keys(annotation,Set.of("path","format","feature_types","attribute","extra_attributes"));String format=text(annotation.get("format"));
            if(!Set.of("GTF","SAF").contains(format))throw new IOException("Annotation format must be GTF or SAF");
            var types=new ArrayList<String>();for(Object type:list(annotation.getOrDefault("feature_types",List.of("exon"))))types.add(identifier(type));
            if(types.isEmpty()||types.size()>64)throw new IOException("Declare1..64 GTF feature types");
            var extras=new ArrayList<String>();for(Object extra:list(annotation.getOrDefault("extra_attributes",List.of())))extras.add(identifier(extra));
            if(extras.size()>16||new HashSet<>(extras).size()!=extras.size())throw new IOException("At most16 distinct extra GTF attributes");
            item.put("annotation",Map.of("path",text(annotation.get("path")),"format",format,"feature_types",types,"attribute",identifier(annotation.getOrDefault("attribute","gene_id")),"extra_attributes",extras));
            item.put("genome",spec.get("genome")==null?null:text(spec.get("genome")));
            var options=spec.containsKey("options")?object(spec.get("options")):Map.<String,Object>of();Set<String> allowed=new HashSet<>(BOOLEAN_DEFAULTS.keySet());allowed.addAll(INTEGER_DEFAULTS.keySet());allowed.addAll(Set.of("fracOverlap","fracOverlapFeature"));keys(options,allowed);
            var settings=new TreeMap<String,Object>();for(var e:BOOLEAN_DEFAULTS.entrySet())settings.put(e.getKey(),bool(options.getOrDefault(e.getKey(),e.getValue())));
            for(var e:INTEGER_DEFAULTS.entrySet())settings.put(e.getKey(),integer(options.getOrDefault(e.getKey(),e.getValue()),e.getKey().equals("minOverlap")||e.getKey().equals("nthreads")?1:0,e.getKey().equals("nthreads")?64:Integer.MAX_VALUE));
            for(String name:List.of("fracOverlap","fracOverlapFeature"))settings.put(name,real(options.getOrDefault(name,0.0),0,1));
            if(Boolean.TRUE.equals(settings.get("isLongRead")))throw new IOException("Long-read counting is outside the validated bulk short-read workflow; skip dependent analysis or validate a separate profile");
            if(Boolean.TRUE.equals(settings.get("splitOnly"))&&Boolean.TRUE.equals(settings.get("nonSplitOnly")))throw new IOException("splitOnly and nonSplitOnly cannot both be enabled");
            if((long)settings.get("minFragLength")>(long)settings.get("maxFragLength"))throw new IOException("minFragLength exceeds maxFragLength");
            if(Boolean.TRUE.equals(settings.get("checkFragLength"))&&!Boolean.TRUE.equals(settings.get("requireBothEndsMapped")))throw new IOException("Original source requires requireBothEndsMapped=TRUE when checkFragLength=TRUE; no option is repaired");
            item.put("options",settings);cases.add(item);
        }
        if(cases.isEmpty()||cases.size()>256)throw new IOException("Declare1..256 cases; the whole batch is admitted atomically");result.put("cases",cases);return result;
    }
    private static String identifier(Object raw) throws IOException {
        String result=text(raw);if(!result.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}"))throw new IOException("Invalid literal GTF field/attribute name");return result;
    }
}
