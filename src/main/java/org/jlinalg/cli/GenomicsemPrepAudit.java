/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import jdistlib.Normal;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;
import static org.jlinalg.cli.GenomicsemPrepTables.*;
import static org.jlinalg.cli.GenomicsemPrepMatrices.*;

/** Source-specific products, joint matrix relationships and enrichment tails. */
final class GenomicsemPrepAudit {
    private GenomicsemPrepAudit() { }
    static List<String> traits(Map<String,Object>request)throws IOException {List<String> names=new ArrayList<>();for(Object t:list(object(request.get("options")).get("trait_names")))names.add(text(t,"trait name"));return names;}
    static Path typed(Path root,String operation){return root.resolve(operation.equals("S_LDSC")?"products/stratified":"worker/typed-result");}
    private static boolean fatal(String message){String s=message.toLowerCase(Locale.ROOT);return s.contains("not converg")||s.contains("failed to converge")||s.contains("convergence issues")||s.contains("estimation failed");}
    private static void probability(double actual,double expected)throws IOException {require(Double.isFinite(actual)&&actual>=0&&actual<=1&&Math.abs(actual-expected)<=1e-315+1e-8*Math.abs(expected),"Original one-sided enrichment probability differs from its estimate/SE");}
    static Map<String,Object> input(Path root,Map<String,Object>request)throws IOException {
        var paths=object(request.get("paths"));String op=text(request.get("operation"),"operation");var rows=new ArrayList<Object>();long total=0;Path bundle=root.resolve("inputs/bundle");
        for(String key:List.of("files","traits"))if(paths.containsKey(key))for(Object value:list(paths.get(key))){String name=text(value,"source file");try(var table=new Text(bundle.resolve(name),request,key.equals("traits"))){while(table.next()!=null){/* bounded structural read; original source still parses the exact captured bytes */}total=Math.addExact(total,table.rows);rows.add(map("path",name,"header",table.header,"rows",table.rows,"uncompressed_bytes",table.characters));}}
        if(op.equals("ENRICH")){
            var tree=GenomicsemPrepObject.readTree(bundle.resolve(text(paths.get("stratified"),"stratified")),request);var source=tree.root.named(tree);require(source.keySet().equals(Set.of("S","V","S_Tau","V_Tau","I","N","m","Prop","Select")),"Typed stratified input must contain the original nine components");
            var annotationNames=source.get("S").names(tree);require(!annotationNames.isEmpty()&&new HashSet<>(annotationNames).size()==annotationNames.size(),"Invalid original annotation labels");for(String field:List.of("V","S_Tau","V_Tau"))require(source.get(field).names(tree).equals(annotationNames),"Different annotation populations/order across original matrix families");
            var chosen=annotationSelection(request,annotationNames);require(chosen.get(0).equals(annotationNames.get(0)),"Keep the original baseline annotation first");
            rows.add(map("typed_annotations",annotationNames,"selected_annotations",chosen,"opaque_RDS_interpreted",false));
        }
        return map("operation",op,"traits",traits(request),"inputs",rows,"source_rows",total,"source_values_repaired",false,"independent_source_parser_or_optimizer",false);
    }
    static List<String> annotationSelection(Map<String,Object>request,List<String>all)throws IOException {Object value=object(request.get("options")).get("annotations");if("ALL".equals(value))return all;List<String> selected=new ArrayList<>();for(Object v:list(value)){String name=text(v,"annotation");require(all.contains(name),"Unknown requested annotation: "+name);selected.add(name);}require(!selected.isEmpty()&&new HashSet<>(selected).size()==selected.size(),"Empty/duplicated annotation selection");return selected;}
    static Map<String,Object> evaluate(Path root,Map<String,Object>request,boolean inspect)throws IOException {
        String op=text(request.get("operation"),"operation");var options=object(request.get("options"));var status=read(root.resolve("worker/worker-status.json"));require("ORIGINAL_PREPARATION_RETURNED_REVIEW_REQUIRED".equals(status.get("status"))&&op.equals(status.get("operation"))&&number(status,"original_function_calls")==1,"No complete original preparation call recorded");
        require(root.relativize(typed(root,op)).toString().replace('\\','/').equals(status.get("typed_object")),"Changed original typed object location");var tree=GenomicsemPrepObject.readTree(typed(root,op),request);var primitive=tree.validate();var conditions=read(root.resolve("worker/conditions.json"));require(number(conditions,"original_function_calls")==1&&op.equals(conditions.get("operation")),"Changed original call provenance");
        long warnings=0,errors=0;for(Object item:list(conditions.get("conditions"))){String message=text(object(item).get("message"),"original warning");warnings++;if(fatal(message))errors++;}long initializationWarnings=list(read(root.resolve("worker/initial-conditions.json")).get("conditions")).size();
        Map<String,Object> result=switch(op){case "MUNGE"->GenomicsemPrepMunge.audit(root,request,inspect);case "LDSC"->ldsc(root,request,tree);case "SUMSTATS"->associations(root,request,tree,false,inspect);case "READ_FUSION"->associations(root,request,tree,true,inspect);case "S_LDSC"->stratified(root,request,tree,inspect);case "ENRICH"->enrich(root,request,tree,inspect);default->throw new IOException("Unknown preparation operation");};
        closedProducts(root,request,tree);
        long available=number(result,"available_units"),unavailable=number(result,"unavailable_units");boolean retain="RETAIN_SOURCE_AND_FLAG".equals(options.get("unavailable_output_policy"));boolean admitted=available>0&&errors==0&&(!"FAIL_OPERATION_WARNINGS".equals(options.get("warning_policy"))||warnings==0)&&(retain||unavailable==0)&&!Boolean.FALSE.equals(result.get("structurally_complete"));
        return map("operation",op,"status",admitted?(unavailable==0?"COMPLETE_SCOPED_ORIGINAL_PREPARATION":"COMPLETE_WITH_UNAVAILABLE_UNITS_RETAINED_AND_FLAGGED"):"FAIL2_NO_COMPLETE_ADMITTED_PREPARATION","products_admitted",admitted,"all_retained_units_admitted",admitted&&unavailable==0,"available_units",available,"unavailable_units",unavailable,"original_operation_warnings",warnings,"original_initialization_warnings",initializationWarnings,"original_error_or_nonconvergence_messages",errors,"primitive_structure",primitive,"scientific_products",result,"original_RDS_interpreted_by_Java",false,"independent_original_optimizer_or_full_method_replication",false,"whole_goal_complete",false);
    }
    private static void closedProducts(Path root,Map<String,Object>request,GenomicsemPrepObject.Tree tree)throws IOException {
        String op=text(request.get("operation"),"operation");Set<String> expected=new TreeSet<>();
        switch(op){
            case "MUNGE"->{expected.addAll(Set.of("munged-status.tsv","munged-row-status.tsv"));for(String trait:traits(request))expected.add("munged/"+trait+".sumstats.gz");}
            case "LDSC"->{expected.addAll(Set.of("S.tsv","V.tsv","I.tsv"));if(Boolean.TRUE.equals(object(request.get("options")).get("standardized")))expected.addAll(Set.of("S_Stand.tsv","V_Stand.tsv"));}
            case "SUMSTATS","READ_FUSION"->expected.addAll(Set.of("associations.tsv","association-status.tsv"));
            case "S_LDSC"->{expected.addAll(Set.of("annotation-status.tsv","stratified/tree.json"));for(String name:tree.vectorFiles)expected.add("stratified/"+name);}
            case "ENRICH"->{expected.add("results.tsv");if(Boolean.TRUE.equals(object(request.get("options")).get("include_baseline")))expected.add("baseline-model.tsv");}
            default->throw new IOException("Unknown product operation");
        }
        Set<String> actual=new TreeSet<>();Path products=root.resolve("products");try(var stream=Files.walk(products)){for(Path p:stream.toList()){require(!Files.isSymbolicLink(p)&&p.toRealPath().equals(p.toAbsolutePath().normalize()),"Aliased product member");if(Files.isDirectory(p))continue;require(Files.isRegularFile(p),"Nonregular product member");actual.add(products.relativize(p).toString().replace('\\','/'));}}require(actual.equals(expected),"Unexpected/missing scientific product files");
    }
    private static Map<String,Object> ldsc(Path root,Map<String,Object>request,GenomicsemPrepObject.Tree tree)throws IOException {
        var names=traits(request);int k=names.size(),q=k*(k+1)/2;var object=tree.root.named(tree);boolean standardized=Boolean.TRUE.equals(object(request.get("options")).get("standardized"));Set<String> expected=new HashSet<>(Set.of("S","V","I","N","m"));if(standardized)expected.addAll(Set.of("S_Stand","V_Stand"));require(object.keySet().equals(expected),"Incomplete/unexpected original LDSC components");
        var s=matrix(tree,object.get("S"),false);var v=matrix(tree,object.get("V"),false);var i=matrix(tree,object.get("I"),false);square(s,k);square(i,k);square(v,q);labels(s,names);var n=matrix(tree,object.get("N"),false);require(n.rows()==1&&n.columns()==q,"Incorrect original joint sample-size vector");for(double d:n.data())require(d>0,"Nonpositive original mean sample size");double[] m=object.get("m").numeric(tree);require(m.length==1&&m[0]>0,"Invalid original reference variant count");
        var pairs=GenomicsemData.pairs(names);projection(root.resolve("products/S.tsv"),s,names,request);projection(root.resolve("products/V.tsv"),v,pairs,request);projection(root.resolve("products/I.tsv"),i,names,request);
        if(standardized){
            var sc=matrix(tree,object.get("S_Stand"),false);var vc=matrix(tree,object.get("V_Stand"),false);square(sc,k);square(vc,q);labels(sc,names);double[] scale=new double[q];int index=0;
            for(int a=0;a<k;a++)for(int b=a;b<k;b++){require(s.get(a,a)>0&&s.get(b,b)>0,"Standardization requires positive original heritabilities");scale[index++]=1/Math.sqrt(s.get(a,a)*s.get(b,b));close(sc.get(a,b),s.get(a,b)/Math.sqrt(s.get(a,a)*s.get(b,b)),"standardized genetic covariance");}
            for(int a=0;a<q;a++)for(int b=0;b<q;b++)close(vc.get(a,b),v.get(a,b)*scale[a]*scale[b],"full standardized joint sampling covariance");projection(root.resolve("products/S_Stand.tsv"),sc,names,request);projection(root.resolve("products/V_Stand.tsv"),vc,pairs,request);
        }
        var p=read(root.resolve("worker/product-projection.json"));require(Boolean.TRUE.equals(p.get("full_joint_sampling_covariance_from_one_original_ldsc_call"))&&same(p.get("traits"),names)&&same(p.get("sampling_pairs"),pairs)&&Boolean.FALSE.equals(p.get("original_numeric_values_changed")),"Changed joint-matrix projection provenance");
        return map("available_units",1,"unavailable_units",0,"structurally_complete",true,"trait_count",k,"joint_sampling_dimension",q,"joint_sample_size_elements",q,"reference_variants",m[0],"S_spectrum",spectrum(s),"V_spectrum",spectrum(v),"S_maximum_asymmetry",s.maximumAsymmetry(),"V_maximum_asymmetry",v.maximumAsymmetry(),"nonpositive_heritability_estimates",s.nonpositiveDiagonal(),"nonpositive_intercept_estimates",i.nonpositiveDiagonal(),"original_cells_projected_exactly",true,"independent_LDSC_optimizer",false);
    }
    private static Map<String,Object> associations(Path root,Map<String,Object>request,GenomicsemPrepObject.Tree tree,boolean tw,boolean inspect)throws IOException {
        var wanted=new ArrayList<>(tw?List.of("Gene","Panel","HSQ"):List.of("SNP","CHR","BP","MAF","A1","A2"));for(String trait:traits(request))wanted.addAll(List.of("beta."+trait,"se."+trait));long available=0,unavailable=0;Set<String> units=new HashSet<>();
        try(var source=new Frame(tree,tree.root);var product=new Text(root.resolve("products/associations.tsv"),request,true);var flags=lines(root.resolve("products/association-status.tsv"),inspect)){
            flags.line("source_row\tidentity\tpanel\tfinite_usable_all_traits\toriginal_fields_changed");
            require(source.names.containsAll(wanted)&&product.header.equals(wanted),"Changed original association projection schema");Map<String,GenomicsemPrepObject.Cell> row;
            while((row=source.next())!=null){List<String> fields=product.next();require(fields!=null,"Missing projected association row");for(int j=0;j<wanted.size();j++){var c=cell(row,wanted.get(j));require(fields.get(j).equals(display(c)),"Projected association changed an original source field");}
                String id=label(row,tw?"Gene":"SNP"),panel=tw?label(row,"Panel"):"";require(!id.isBlank()&&id.codePoints().noneMatch(c->c<32||c==127)&&panel.codePoints().noneMatch(c->c<32||c==127)&&units.add(id+'\u0000'+panel),"Blank/control/duplicated original SNP or Gene/Panel identity");boolean good=true;
                if(tw){var h=cell(row,"HSQ");good=!h.missing()&&!h.nonfinite()&&h.number()>0;}else{
                    var maf=cell(row,"MAF");good=!maf.missing()&&!maf.nonfinite()&&maf.number()>0&&maf.number()<=.5;var bp=cell(row,"BP");good&=!bp.missing()&&!bp.nonfinite()&&bp.number()>=1&&bp.number()<=Integer.MAX_VALUE&&bp.number()==Math.rint(bp.number());good&=!label(row,"A1").equals(label(row,"A2"));
                }
                for(String trait:traits(request)){var beta=cell(row,"beta."+trait);var se=cell(row,"se."+trait);good&=!beta.missing()&&!beta.nonfinite()&&!se.missing()&&!se.nonfinite()&&(se.missing()||se.nonfinite()||se.number()>0);}
                if(good)available++;else unavailable++;flags.line(source.row+"\t"+quote(id)+"\t"+quote(panel)+"\t"+good+"\tfalse");
            }require(product.next()==null,"Extra projected association rows");
        }
        var projection=read(root.resolve("worker/product-projection.json"));var originalColumns=tree.root.names(tree);var omitted=new ArrayList<>(originalColumns);omitted.removeAll(wanted);require(same(projection.get("original_columns"),originalColumns)&&same(projection.get("omitted_original_columns"),omitted)&&same(projection.get("columns"),wanted)&&Boolean.FALSE.equals(projection.get("original_values_or_identifiers_repaired")),"Changed named-column projection provenance");
        return map("available_units",available,"unavailable_units",unavailable,"structurally_complete",true,"original_rows",units.size(),"source_projection_values_exact",true,"column_order_projection_explicit",true,"all_source_rows_preserved",true,"unavailable_rows_not_finite_association_results",true);
    }
    private static Map<String,Object> stratified(Path root,Map<String,Object>request,GenomicsemPrepObject.Tree tree,boolean inspect)throws IOException {
        var source=tree.root.named(tree);require(source.keySet().equals(Set.of("S","V","S_Tau","V_Tau","I","N","m","Prop","Select")),"Original stratified covariance lacks a component");var annotations=source.get("S").names(tree);require(!annotations.isEmpty()&&new HashSet<>(annotations).size()==annotations.size(),"Invalid stratified annotation labels");int k=traits(request).size(),q=k*(k+1)/2;long available=0,unavailable=0;
        for(String family:List.of("V","S_Tau","V_Tau"))require(source.get(family).names(tree).equals(annotations),"Stratified matrix families have different annotations/order");
        try(var out=lines(root.resolve("products/annotation-status.tsv"),inspect)){
            out.line("annotation\tmode\tsource_S_finite\tsource_V_finite\tS_spectral_status\tV_spectral_status\toriginal_cells_changed");
            for(int a=0;a<annotations.size();a++)for(boolean tau:List.of(false,true)){
                var s=matrix(tree,source.get(tau?"S_Tau":"S").children.get(a),true);var v=matrix(tree,source.get(tau?"V_Tau":"V").children.get(a),true);square(s,k);square(v,q);labels(s,traits(request));var ss=spectrum(s);var vs=spectrum(v);boolean finite=s.unavailable()==0&&v.unavailable()==0;if(finite)available++;else unavailable++;
                out.line(String.join("\t",quote(annotations.get(a)),tau?"tau":"zero_order",Boolean.toString(s.unavailable()==0),Boolean.toString(v.unavailable()==0),ss.get("status").toString(),vs.get("status").toString(),"false"));
            }
        }
        var m=matrix(tree,source.get("m"),false);require(m.rows()==annotations.size()&&m.columns()==1,"Stratified reference-count dimensions differ");for(double d:m.data())require(d>0,"Nonpositive annotation reference count");
        try(var props=new Frame(tree,source.get("Prop"))){require(props.names.equals(List.of("Prop")),"Changed original annotation proportion schema");for(int a=0;a<annotations.size();a++){var row=props.next();require(row!=null,"Missing annotation proportion");close(number(row,"Prop"),m.get(a,0)/m.get(0,0),"original relative annotation proportion");}require(props.next()==null,"Extra annotation proportions");}
        var intercept=matrix(tree,source.get("I"),false);square(intercept,k);var n=matrix(tree,source.get("N"),false);require(n.rows()==1&&n.columns()==q,"Incorrect stratified joint N dimensions");
        try(var selection=new Frame(tree,source.get("Select"))){require(selection.names.equals(List.of("V1","V2"))&&selection.length==annotations.size()-1,"Changed original continuous/flanking annotation selection population");Map<String,GenomicsemPrepObject.Cell> row;while((row=selection.next())!=null)require(Set.of(1.0,2.0).contains(number(row,"V2")),"Unknown source annotation selection flag");}
        return map("available_units",available,"unavailable_units",unavailable,"structurally_complete",true,"annotations",annotations,"unit_definition","annotation and zero-order/tau mode","trait_count",k,"joint_sampling_dimension",q,"original_fields_or_cells_repaired",false,"nonpositive_definite_estimates_require_explicit_downstream_smoothing_review",true);
    }
    private static Map<String,Object> enrich(Path root,Map<String,Object>request,GenomicsemPrepObject.Tree tree,boolean inspect)throws IOException {
        var paths=object(request.get("paths"));var options=object(request.get("options"));var input=GenomicsemPrepObject.readTree(root.resolve("inputs/bundle").resolve(text(paths.get("stratified"),"stratified")),request);var all=input.root.component(input,"S").names(input);var selected=annotationSelection(request,all);var selection=read(root.resolve("worker/enrichment-selection.json"));require(same(selection.get("original_annotations"),all)&&same(selection.get("selected_annotations"),selected)&&Boolean.TRUE.equals(selection.get("original_baseline_retained_first"))&&Boolean.FALSE.equals(selection.get("original_values_changed")),"Changed explicit annotation projection");
        Set<String> parameters=new LinkedHashSet<>();for(Object p:list(options.get("parameters")))parameters.add(text(p,"parameter").replaceAll("\\s+",""));List<GenomicsemPrepObject.Node> frames=tree.root.frame(tree)?List.of(tree.root):tree.root.children;Set<String> seen=new HashSet<>();long available=0,unavailable=0,warnings=0,errors=0,smoothing=0;boolean baselineSeen=false;
        try(var out=lines(root.resolve("products/results.tsv"),inspect);var baseline=Boolean.TRUE.equals(options.get("include_baseline"))?lines(root.resolve("products/baseline-model.tsv"),inspect):null){
            out.line("annotation\tlhs\toperator\trhs\tenrichment\tstandard_error\tp_value_one_sided\tCov_Smooth\tZ_smooth\terror\twarning");if(baseline!=null)baseline.line("lhs\toperator\trhs\testimate\toriginal_standard_error\tfixed_enrichment");
            for(var node:frames)try(var frame=new Frame(tree,node)){
                Map<String,GenomicsemPrepObject.Cell> row;
                if(!frame.names.contains("Annotation")){
                    require(baseline!=null&&!baselineSeen&&frame.names.equals(List.of("lhs","op","rhs","est","SE","Fixed_Enrich")),"Unexpected/missing original enrichment baseline table");baselineSeen=true;
                    while((row=frame.next())!=null){String[] values={label(row,"lhs"),label(row,"op"),label(row,"rhs"),display(cell(row,"est")),display(cell(row,"SE")),display(cell(row,"Fixed_Enrich"))};for(int i=0;i<values.length;i++)values[i]=quote(values[i]);baseline.line(String.join("\t",values));}continue;
                }
                while((row=frame.next())!=null){
                    String ann=label(row,"Annotation"),lhs=label(row,"lhs"),operator=label(row,"op"),rhs=label(row,"rhs"),parameter=lhs+operator+rhs;require(selected.contains(ann)&&parameters.contains(parameter)&&seen.add(ann+'\u0000'+parameter),"Unknown/duplicated original enrichment annotation/parameter");var error=cell(row,"Error");var warning=cell(row,"Warning");if(error.missing()||error.nonfinite()||!error.value().equals("0"))errors++;if(!warning.missing()&&!Set.of("0","").contains(warning.value())){warnings++;if(fatal(warning.value()))errors++;}
                    var estimate=cell(row,"Enrichment");var se=cell(row,"Enrichment_SE");var p=cell(row,"Enrichment_p_value");boolean usable=!estimate.missing()&&!estimate.nonfinite()&&!se.missing()&&!se.nonfinite()&&!p.missing()&&!p.nonfinite();
                    if(usable){require(se.number()>0,"Nonpositive original enrichment SE");probability(p.number(),Normal.cumulative((estimate.number()-1)/se.number(),0,1,false,false));available++;}else unavailable++;
                    for(String name:List.of("Cov_Smooth","Z_smooth")){var value=cell(row,name);if(!value.missing()&&!value.nonfinite()&&value.number()!=0)smoothing++;}
                    String[] values={ann,lhs,operator,rhs,display(estimate),display(se),display(p),display(cell(row,"Cov_Smooth")),display(cell(row,"Z_smooth")),display(error),display(warning)};for(int i=0;i<values.length;i++)values[i]=quote(values[i]);out.line(String.join("\t",values));
                }
            }
        }
        require(baselineSeen==Boolean.TRUE.equals(options.get("include_baseline")),"Requested original baseline table is absent");
        // Source may omit continuous/flanking annotations when rm_flank=TRUE.
        // Only its own declared selection flags permit such an omission.
        var flags=new ArrayList<Double>();flags.add(1.0);try(var f=new Frame(input,input.root.component(input,"Select"))){Map<String,GenomicsemPrepObject.Cell> row;while((row=f.next())!=null)flags.add(number(row,"V2"));}
        for(String ann:selected)for(String parameter:parameters)if(!seen.contains(ann+'\u0000'+parameter))require(Boolean.TRUE.equals(options.get("remove_flanking"))&&flags.get(all.indexOf(ann))==2,"An eligible requested annotation/parameter result is missing");
        boolean structural=errors==0&&(!"FAIL_OPERATION_WARNINGS".equals(options.get("warning_policy"))||warnings==0)&&(!"FAIL".equals(options.get("smoothing_policy"))||smoothing==0);
        return map("available_units",available,"unavailable_units",unavailable,"structurally_complete",structural,"source_result_pairs",seen.size(),"source_warning_fields",warnings,"source_error_fields",errors,"nonzero_smoothing_diagnostics",smoothing,"baseline_model_retained",baselineSeen,"selected_annotations",selected,"requested_parameters",new ArrayList<>(parameters),"P_tail","one_sided_upper_for_enrichment_above1","tail_absolute_tolerance",1e-315,"tail_relative_tolerance",1e-8,"unavailable_source_fields_repaired",false);
    }
}
