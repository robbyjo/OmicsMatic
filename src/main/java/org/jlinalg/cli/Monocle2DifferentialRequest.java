/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Explicit response units, VGAM families and nested typed formulae; no executable R input. */
final class Monocle2DifferentialRequest {
    private Monocle2DifferentialRequest() { }
    record Request(String version,String format,String units,Path expression,Path cells,Path genes,
                   Map<String,Object> types,Path out,Path rscript,Path rHome,List<Path> libraries,
                   List<Map<String,Object>> analyses,long inputBytes,int maximumGenes,int maximumCells,
                   int denseEntries,int sparseEntries,int jsonBytes,long outputBytes,int timeout) {
        Map<String,Object> parameters(){return Map.of("version",version,"matrix_format",format,"expression_units",units,"metadata_types",types,"analyses",analyses);}
        Map<String,Object> effective(){
            var m=new LinkedHashMap<String,Object>(parameters());m.put("schema_version",1);m.put("mode","DIFF");m.put("expression",expression.toString());m.put("cell_metadata",cells.toString());m.put("gene_metadata",genes.toString());
            m.put("out",out.toString());m.put("rscript",rscript.toString());m.put("r_home",rHome.toString());m.put("r_libraries",libraries.stream().map(Path::toString).toList());m.put("maximum_input_bytes",inputBytes);
            m.put("maximum_genes",maximumGenes);m.put("maximum_cells",maximumCells);m.put("maximum_dense_entries",denseEntries);m.put("maximum_nonzero_entries",sparseEntries);m.put("maximum_json_bytes",jsonBytes);m.put("maximum_output_bytes",outputBytes);m.put("timeout_seconds",timeout);return m;
        }
    }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","mode","version","matrix_format","expression_units","expression","cell_metadata","gene_metadata","metadata_types","out","rscript","r_home","r_libraries","analyses","maximum_input_bytes","maximum_genes","maximum_cells","maximum_dense_entries","maximum_nonzero_entries","maximum_json_bytes","maximum_output_bytes","timeout_seconds");
        integer(m.get("schema_version"),1,1);require(text(m.get("mode")).equals("DIFF"),"Require mode DIFF");String version=label(m.get("version"));require(version.matches("[0-9]+[.][0-9]+[.][0-9]+"),"Declare an exact original version; only 2.26.0 has this admitted runtime profile");
        String format=text(m.get("matrix_format")),units=text(m.get("expression_units"));require(Set.of("dense-tsv","matrix-market").contains(format),"Choose dense-tsv or matrix-market");require(Set.of("raw-integer-counts","continuous","binary","proportions").contains(units),"Declare response units explicitly");
        var types=object(m.get("metadata_types"));require(types.size()<=128,"At most 128 metadata columns");
        for(var e:types.entrySet()){
            covariate(e.getKey());var spec=object(e.getValue());allowed(spec,"type","levels");String type=text(spec.get("type"));require(Set.of("numeric","factor").contains(type),"Metadata type must be numeric or factor");
            if(type.equals("factor")){var levels=strings(spec.get("levels"),1024);require(!levels.isEmpty()&&new HashSet<>(levels).size()==levels.size(),"Unique ordered factor levels required");}else require(!spec.containsKey("levels"),"Numeric metadata cannot declare factor levels");
        }
        var libraries=new ArrayList<Path>();for(Object x:list(m.get("r_libraries"))){require(libraries.size()<64,"Too many libraries");libraries.add(resolve(parent,label(x)));}require(!libraries.isEmpty(),"Declare exact private R libraries explicitly");
        var cases=new ArrayList<Map<String,Object>>();var ids=new HashSet<String>();
        for(Object raw:list(m.get("analyses"))){
            require(cases.size()<32,"At most 32 analyses");var a=object(raw);allowed(a,"id","seed","family","size","lower_detection_limit","estimate_size_factors","dispersion","relative_expr","full_model","reduced_model");String id=text(a.get("id"));
            require(id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")&&ids.add(id.toLowerCase(Locale.ROOT)),"Unique portable analysis IDs required, including case folding");integer(a.get("seed"),1,2147483646);
            String family=text(a.get("family"));require(Set.of("negbinomial.size","negbinomial","uninormal","binomialff").contains(family),"Unsupported typed original VGAM family");boolean nb=family.startsWith("negbinomial");
            if(nb)require(units.equals("raw-integer-counts"),"Negative-binomial models require declared raw integer counts in this interface");
            if(family.equals("binomialff"))require(Set.of("binary","proportions").contains(units),"binomialff requires binary or proportions units; no conversion into presence/absence");
            if(family.equals("uninormal"))require(Set.of("continuous","raw-integer-counts").contains(units),"uninormal requires an explicit continuous or raw-count response declaration");
            if(a.containsKey("size")){require(family.equals("negbinomial.size"),"size applies only to negbinomial.size");finite(a.get("size"),Double.MIN_NORMAL,1e12);}
            finite(a.get("lower_detection_limit"),0,1e12);boolean sf=bool(a.get("estimate_size_factors")),relative=bool(a.get("relative_expr"));
            require(!sf||units.equals("raw-integer-counts"),"Size-factor estimation is supported only for raw counts");require(nb||!relative,"Non-NB response families do not use relative_expr; declare false");require(!relative||sf,"Relative NB expression requires explicit size-factor estimation");
            var dispersion=object(a.get("dispersion"));boolean estimate=bool(dispersion.get("estimate"));
            if(estimate){allowed(dispersion,"estimate","model","relative_expr","min_cells_detected","remove_outliers");require(nb&&sf,"Original dispersion estimation requires NB counts and size factors");formula(object(dispersion.get("model")),types);bool(dispersion.get("relative_expr"));integer(dispersion.get("min_cells_detected"),1,1000000);bool(dispersion.get("remove_outliers"));}
            else allowed(dispersion,"estimate");
            var full=object(a.get("full_model"));var reduced=object(a.get("reduced_model"));Map<String,String> f=formula(full,types),r=formula(reduced,types);
            require(bool(full.get("intercept"))==bool(reduced.get("intercept")),"Use the same explicit intercept flag for nested models");require(f.size()>r.size()&&f.entrySet().containsAll(r.entrySet()),"Reduced model must contain a strict subset of identical full-model terms; no spline-knot nesting is inferred");
            cases.add(Collections.unmodifiableMap(a));
        }
        require(!cases.isEmpty(),"Require 1..32 explicit analyses");
        return new Request(version,format,units,resolve(parent,label(m.get("expression"))),resolve(parent,label(m.get("cell_metadata"))),resolve(parent,label(m.get("gene_metadata"))),Collections.unmodifiableMap(types),resolve(parent,label(m.get("out"))),resolve(parent,label(m.get("rscript"))),resolve(parent,label(m.get("r_home"))),List.copyOf(libraries),List.copyOf(cases),
            number(m,"maximum_input_bytes",67108864,1,268435456),(int)number(m,"maximum_genes",20000,1,1000000),(int)number(m,"maximum_cells",100000,2,1000000),(int)number(m,"maximum_dense_entries",1000000,1,25000000),(int)number(m,"maximum_nonzero_entries",1000000,1,25000000),(int)number(m,"maximum_json_bytes",67108864,1,268435456),number(m,"maximum_output_bytes",536870912,1,4294967296L),(int)number(m,"timeout_seconds",600,1,86400));
    }
    private static String covariate(String s)throws IOException {column(s);require(!Set.of("Size_Factor","Pseudotime","State","Cluster","f_expression","NA_integer_","NA_real_","NA_complex_","NA_character_").contains(s),"Reserved original model/R covariate: "+s);return s;}
    private static Map<String,String> formula(Map<String,Object> model,Map<String,Object> types)throws IOException {
        allowed(model,"intercept","terms");boolean intercept=bool(model.get("intercept"));var terms=list(model.get("terms"));require(terms.size()<=64&&(intercept||!terms.isEmpty()),"Require an intercept or terms, with at most 64 additive terms");var columns=new LinkedHashMap<String,String>();
        for(Object raw:terms){var term=object(raw);allowed(term,"column","spline_df");String c=covariate(text(term.get("column")));require(types.containsKey(c),"Undeclared model covariate: "+c);String signature="linear";
            if(term.containsKey("spline_df")){require(text(object(types.get(c)).get("type")).equals("numeric"),"sm.ns requires a numeric covariate");signature="sm.ns:"+integer(term.get("spline_df"),1,50);}
            require(columns.putIfAbsent(c,signature)==null,"Duplicate model covariate: "+c);
        }return columns;
    }
}
