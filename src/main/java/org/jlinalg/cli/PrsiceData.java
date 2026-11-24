/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.pin;

/** Captured summary/weight rows and distinct original sample-mask semantics. */
final class PrsiceData {
    private PrsiceData() { }
    record Variant(long line,String id,int chromosome,long position,String rawA1,String rawA2,String rawEffect,double effect,double p,String status) {
        String a1(){return PrsiceTables.asciiUpper(rawA1);}
        String a2(){return PrsiceTables.asciiUpper(rawA2);}
    }
    record SampleState(PrsiceGenotypes.Sample sample,int compactIndex,boolean sourceRegressionFlag,boolean modelRow,boolean knownCase,boolean standardizationRow,String phenotypeStatus,String covariateStatus,double phenotype) { }
    static final class Inputs implements AutoCloseable {
        final PrsiceGenotypes.Dataset target,reference;
        final Map<String,Variant> base;
        final List<SampleState> samples;
        final Map<String,Object> summary;
        final List<String> warnings;
        Inputs(PrsiceGenotypes.Dataset target,PrsiceGenotypes.Dataset reference,Map<String,Variant> base,List<SampleState> samples,Map<String,Object> summary,List<String> warnings){this.target=target;this.reference=reference;this.base=Collections.unmodifiableMap(base);this.samples=List.copyOf(samples);this.summary=summary;this.warnings=List.copyOf(warnings);}
        @Override public void close()throws IOException {IOException failure=null;try{target.close();}catch(IOException e){failure=e;}if(reference!=null)try{reference.close();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}if(failure!=null)throw failure;}
    }
    @FunctionalInterface interface Visit {void row(Variant variant)throws IOException;}
    static Inputs read(Path root,Map<String,Object> request)throws IOException {
        if("PREDICT".equals(request.get("operation"))&&"REQUIRE_REPORTED_CONVERGENCE".equals(object(request.get("policies")).get("solver_convergence")))throw new PrsiceContracts.Unavailable("Original PRSice does not expose its GLM convergence flags. Dependent fitting is skipped before launch under REQUIRE_REPORTED_CONVERGENCE. Keep the skip or explicitly review SOURCE_UNREPORTED_WITH_WARNING; finite fitted fields are not a convergence certificate.");
        PrsiceGenotypes.Dataset target=null,reference=null;
        try {
            target=PrsiceGenotypes.read(root,request,"target");if(object(request.get("inputs")).containsKey("reference_bed"))reference=PrsiceGenotypes.read(root,request,"reference");
            var parameters=object(request.get("parameters"));
            if(parameters.get("scoring")!=null&&"REFERENCE".equals(object(parameters.get("scoring")).get("frequency_source"))){var filter=object(object(parameters.get("filters")).get("reference"));if(PrsiceContracts.equal(PrsiceContracts.finite(filter.get("missing"),"reference missingness"),1)&&PrsiceContracts.equal(PrsiceContracts.finite(filter.get("maf"),"reference MAF"),0))throw new PrsiceContracts.Unavailable("Original PRSice skips filling reference counts when reference missingness=1 and MAF=0, and --use-ref-maf then falls back to target counts. Requested REFERENCE frequencies are therefore unavailable. Retain this skip, explicitly select TARGET, or supply scientifically justified active reference QC thresholds; the adapter never changes those choices automatically.");}
            var warnings=new ArrayList<String>();var base=new LinkedHashMap<String,Variant>();var counts=new TreeMap<String,Long>();
            rows(root,request,target,v->{require(base.size()<PrsiceContracts.cap(request,"maximum_variants"),"Base/weight rows exceed maximum_variants");require(base.putIfAbsent(v.id(),v)==null,"Duplicate literal base/weight SNP ID "+v.id()+"; no first/last-row substitution is performed");counts.merge(v.status(),1L,Long::sum);});
            require(!base.isEmpty(),"No base/weight rows");require(base.size()<=PrsiceContracts.cap(request,"maximum_variants"),"Base/weight rows exceed maximum_variants");
            for(var entry:counts.entrySet())if(entry.getKey().startsWith("INVALID"))policy(request,"unmatched_variants",entry.getValue()+" base rows have "+entry.getKey()+"; raw values are retained and no correction is inferred",warnings);
            if(target.unknownSelectionIds>0)policy(request,"incomplete_samples",target.unknownSelectionIds+" target keep/remove keys are absent from the complete FAM; original set membership is retained",warnings);
            if(reference!=null&&reference.unknownSelectionIds>0)policy(request,"incomplete_samples",reference.unknownSelectionIds+" reference keep/remove keys are absent from its complete FAM",warnings);
            require(target.selectedFounders()>0,"No selected target founders under the original parent-membership convention");
            if(reference!=null)require(reference.selectedFounders()>0,"No selected reference founders under the original parent-membership convention");
            var samples=samples(root,request,target,warnings);long modelRows=samples.stream().filter(SampleState::modelRow).count(),stdRows=samples.stream().filter(SampleState::standardizationRow).count();
            var summary=map("base_rows",base.size(),"base_row_statuses",counts,"target_FAM_rows",target.samples.size(),"target_selected_samples",target.selected.size(),"target_selected_founders",target.selectedFounders(),"target_BIM_rows",target.variants.size(),"target_duplicate_BIM_IDs",target.duplicateVariantIds.size(),"source_model_map_rows",modelRows,"source_standardization_rows",stdRows,"operation",request.get("operation"),"unrequested_or_unknown_values_repaired",false,"source_quantitative_minus9_is_observed",true,"source_standardization_is_not_fitted_complete_case_selection",true);
            if(reference!=null)summary.put("reference",map("FAM_rows",reference.samples.size(),"selected_samples",reference.selected.size(),"selected_founders",reference.selectedFounders(),"BIM_rows",reference.variants.size(),"duplicate_BIM_IDs",reference.duplicateVariantIds.size()));
            return new Inputs(target,reference,base,samples,summary,warnings);
        }catch(IOException|RuntimeException|Error error){if(target!=null)try{target.close();}catch(IOException second){error.addSuppressed(second);}if(reference!=null)try{reference.close();}catch(IOException second){error.addSuppressed(second);}throw error;}
    }
    static void policy(Map<String,Object> request,String name,String message,List<String> warnings)throws IOException {if("ERROR".equals(object(request.get("policies")).get(name)))throw new IOException(message+"; policy "+name+"=ERROR");warnings.add(message);}
    static void rows(Path root,Map<String,Object> request,PrsiceGenotypes.Dataset target,Visit visitor)throws IOException {
        var parameters=object(request.get("parameters"));var base=object(parameters.get("base"));var columns=object(base.get("columns"));var filters=object(parameters.get("filters"));boolean weights="PREPARED_WEIGHTS".equals(base.get("kind"));
        try(var rows=new PrsiceTables.Rows(PrsiceRuntime.input(root,"base"),request,false)){
            var header=PrsiceTables.header(rows.next());var index=new HashMap<String,Integer>();for(var entry:columns.entrySet())if(entry.getValue()!=null)index.put(entry.getKey(),PrsiceTables.column(header,entry.getValue()));
            int info=filters.get("base_info")==null?-1:PrsiceTables.column(header,object(filters.get("base_info")).get("column")),maf=filters.get("base_maf")==null?-1:PrsiceTables.column(header,object(filters.get("base_maf")).get("column"));
            for(String[] row;(row=rows.next())!=null;){
                require(row.length==header.size(),"Base/weight row width differs from its exact header at physical line "+rows.lineNumber());
                String id=row[index.get("snp")],a1=row[index.get("a1")],a2=row[index.get("a2")],raw=row[index.get("effect")];String status="CANDIDATE";int chr=-1;long bp=0;double effect=Double.NaN,p=weights?1:Double.NaN;
                try {
                    effect=PrsiceTables.finite(raw,"base effect at line "+rows.lineNumber());if("OR".equals(base.get("effect_scale"))){require(effect>0,"OR must be positive");effect=Math.log(effect);}
                    if(!weights){p=PrsiceTables.finite(row[index.get("pvalue")],"base P");require(p>=0&&p<=1,"Base P outside [0,1]");}
                    if(index.containsKey("chromosome")){chr=PrsiceGenotypes.chromosome(row[index.get("chromosome")]);bp=PrsiceGenotypes.nonnegative(row[index.get("position")],"base position");}
                    else {var bim=target.byId.get(id);if(bim==null)status="MISSING_TARGET_FOR_PREPARED_COORDINATE";else{require(!target.duplicateVariantIds.contains(id),"Ambiguous prepared-weight coordinate from duplicate target BIM ID "+id);chr=bim.chromosome();bp=bim.position();}}
                    if(info>=0&&PrsiceTables.finite(row[info],"base INFO")<PrsiceContracts.finite(object(filters.get("base_info")).get("minimum"),"INFO minimum"))status="EXPLICIT_BASE_INFO_FILTER";
                    if(maf>=0&&PrsiceTables.finite(row[maf],"base MAF")<PrsiceContracts.finite(object(filters.get("base_maf")).get("minimum"),"MAF minimum"))status="EXPLICIT_BASE_MAF_FILTER";
                    if(status.equals("CANDIDATE")&&(chr<1||chr>22))status="OUTSIDE_AUTOSOMAL_PROFILE";
                    if(status.equals("CANDIDATE")&&PrsiceGenotypes.palindromic(PrsiceTables.asciiUpper(a1),PrsiceTables.asciiUpper(a2))&&"DROP".equals(object(parameters.get("alleles")).get("palindromic")))status="EXPLICIT_PALINDROMIC_DROP";
                }catch(IOException invalid){status="INVALID_BASE_VALUE: "+invalid.getMessage();}
                visitor.row(new Variant(rows.lineNumber(),id,chr,bp,a1,a2,raw,effect,p,status));
            }
        }
    }
    private static List<SampleState> samples(Path root,Map<String,Object> request,PrsiceGenotypes.Dataset target,List<String> warnings)throws IOException {
        var p=object(request.get("parameters"));boolean ignore=Boolean.TRUE.equals(p.get("ignore_fid")),nonfounders=Boolean.TRUE.equals(p.get("include_nonfounders"));String delimiter=ignore?"":text(p.get("id_delimiter"),"ID delimiter");
        Map<String,Object> phenotype=p.get("phenotype")==null?null:object(p.get("phenotype"));Map<String,String[]> phenotypeRows=null,covariateRows=null;int phenotypeColumn=-1;
        if(phenotype!=null&&"FILE".equals(phenotype.get("source"))){var table=sampleTable(PrsiceRuntime.input(root,"phenotype"),request,ignore,delimiter);phenotypeRows=table.rows();phenotypeColumn=PrsiceTables.column(table.header(),phenotype.get("column"));}
        var covariateIndex=new LinkedHashMap<String,Integer>();Set<String> categorical=Set.of();
        if(p.get("regression")!=null){var regression=object(p.get("regression"));categorical=PrsiceContracts.columnList(regression.get("categorical"));if(!list(regression.get("covariates")).isEmpty()){var table=sampleTable(PrsiceRuntime.input(root,"covariates"),request,ignore,delimiter);covariateRows=table.rows();for(Object name:list(regression.get("covariates")))covariateIndex.put((String)name,PrsiceTables.column(table.header(),name));}}
        int n=target.selected.size();double[] values=new double[n];Arrays.fill(values,Double.NaN);String[] phenotypeStatus=new String[n],covariateStatus=new String[n];boolean[] model=new boolean[n];int maxBinary=0;boolean binary=phenotype!=null&&"BINARY".equals(phenotype.get("kind"));long incomplete=0,observedMinus9=0;
        for(int i=0;i<n;i++){
            var sample=target.selected.get(i);boolean sourceFlag=sample.founder()||nonfounders;phenotypeStatus[i]=phenotype==null?"NOT_REQUESTED":!sourceFlag?"NONFOUNDER_NOT_IN_MODEL":"OBSERVED";covariateStatus[i]=covariateRows==null?"NOT_REQUESTED":"COMPLETE";
            if(phenotype==null||!sourceFlag)continue;
            String value=sample.phenotype();if(phenotypeRows!=null){String[] row=phenotypeRows.get(sample.key());if(row==null){phenotypeStatus[i]="MISSING_PHENOTYPE_ID";incomplete++;continue;}value=row[phenotypeColumn];}
            if(PrsiceTables.missing(value)||binary&&value.equals("-9")){phenotypeStatus[i]="SOURCE_MISSING_PHENOTYPE";incomplete++;continue;}
            try {
                if(binary){require(value.matches("[-+]?[0-9]+"),"Original binary phenotype requires integer syntax");int code=Integer.parseInt(value);require(code>=0&&code<=2,"Original binary phenotype code outside 0,1,2");values[i]=code;maxBinary=Math.max(maxBinary,code);}
                else {values[i]=PrsiceTables.finite(value,"quantitative phenotype");if(values[i]==-9)observedMinus9++;}
                model[i]=true;
            }catch(IOException|NumberFormatException invalid){phenotypeStatus[i]="SOURCE_INVALID_PHENOTYPE";incomplete++;}
        }
        int minBinary=maxBinary==1?0:1;
        if(binary)for(int i=0;i<n;i++)if(model[i]){require(values[i]>=minBinary&&values[i]<=maxBinary,"Original binary phenotype coding mixes incompatible 0/1 and 1/2 values; no recoding is inferred");values[i]-=minBinary;}
        for(int i=0;i<n;i++)if(model[i]&&covariateRows!=null){
            String[] row=covariateRows.get(target.selected.get(i).key());if(row==null){model[i]=false;covariateStatus[i]="MISSING_COVARIATE_ID";incomplete++;continue;}
            for(var entry:covariateIndex.entrySet()){
                String value=row[entry.getValue()];boolean valid=!PrsiceTables.missing(value);
                if(valid&&!categorical.contains(entry.getKey()))try{PrsiceTables.finite(value,"numeric covariate "+entry.getKey());}catch(IOException invalid){valid=false;}
                if(!valid){model[i]=false;covariateStatus[i]="SOURCE_MISSING_OR_INVALID_COVARIATE";break;}
            }
            if(!model[i])incomplete++;
        }
        if(incomplete>0)policy(request,"incomplete_samples",incomplete+" selected source-model candidates lack a valid requested phenotype/covariate row; raw values and exclusions are retained",warnings);
        if(observedMinus9>0)warnings.add("SOURCE QUANTITATIVE CONVENTION: "+observedMinus9+" quantitative -9 values are observed, not missing; they are not silently removed or recoded.");
        boolean standard=p.get("scoring")!=null&&Set.of("std","con-std").contains(text(object(p.get("scoring")).get("scale"),"scale")),controls=standard&&"con-std".equals(object(p.get("scoring")).get("scale"));var result=new ArrayList<SampleState>();int gap=0;
        for(int i=0;i<n;i++){
            var sample=target.selected.get(i);boolean sourceFlag=sample.founder()||nonfounders;
            // Original standardize_prs indexes the unfiltered PRS mask with a
            // compact sample index. Keep/remove gaps can therefore affect a
            // different compact row. It separately uses in_regression, not
            // the fitted complete-case flag. Preserve both source decisions.
            boolean std=standard&&target.selectedMask[i]&&sourceFlag;
            boolean knownCase=binary&&model[i]&&PrsiceContracts.equal(values[i],1);
            if(controls&&knownCase)std=false;
            if(std&&phenotype!=null&&!model[i])gap++;
            result.add(new SampleState(sample,i,sourceFlag,model[i],knownCase,std,phenotypeStatus[i],covariateStatus[i],values[i]));
        }
        if(gap>0){String message="SOURCE STANDARDIZATION CONVENTION: "+gap+" rows enter standardization despite not entering the fitted phenotype/covariate map; unknown outcomes are not automatically excluded as controls.";if("FAIL_ON_INCOMPLETE_FIT_ROWS".equals(object(request.get("policies")).get("standardization")))throw new IOException(message);warnings.add(message);}
        if(standard)warnings.add("SOURCE STANDARDIZATION: original founder/nonfounder flags, original raw-versus-compact keep/remove indexing and source case-exclusion flags are preserved. This is not a declaration that standardization uses fitted complete cases or only observed controls.");
        return result;
    }
    record SampleTable(Map<String,Integer> header,Map<String,String[]> rows) { }
    static SampleTable sampleTable(Path path,Map<String,Object> request,boolean ignore,String delimiter)throws IOException {
        try(var rows=new PrsiceTables.Rows(path,request,false)){
            String[] first=rows.next();var header=PrsiceTables.header(first);require(first.length>=(ignore?2:3),"Sample table requires ID prefix and named data columns");var values=new HashMap<String,String[]>();
            // The original phenotype map reads its header as a keyed row too.
            // Retaining it also makes literal header/data ID collisions fail.
            for(String[] row=first;row!=null;row=rows.next()){require(row.length==first.length,"Sample-table row width differs from the exact header");String key=ignore?row[0]:PrsiceGenotypes.id(row[0],row[1],false,delimiter);require(values.putIfAbsent(key,row)==null,"Duplicate/colliding sample table key "+key);}
            return new SampleTable(header,values);
        }
    }
    static Map<String,Object> prepared(Path root,Map<String,Object> request,Inputs inputs,boolean create)throws IOException {
        boolean weights="PREPARED_WEIGHTS".equals(object(object(request.get("parameters")).get("base")).get("kind"));if(!weights)return map("requested",false);
        Path destination=root.resolve("worker/prepared-base.tsv");MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
        long[] counts={0,0};OutputStream sink=create?Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW):OutputStream.nullOutputStream();
        try(var counted=new FilterOutputStream(new DigestOutputStream(sink,digest)){
            @Override public void write(byte[] b,int off,int len)throws IOException{counts[0]=Math.addExact(counts[0],len);require(counts[0]<=PrsiceContracts.cap(request,"maximum_member_bytes"),"Prepared base exceeds member byte capacity");out.write(b,off,len);}
            @Override public void write(int b)throws IOException{counts[0]++;require(counts[0]<=PrsiceContracts.cap(request,"maximum_member_bytes"),"Prepared base exceeds member byte capacity");out.write(b);}
        };var writer=new BufferedWriter(new OutputStreamWriter(counted,StandardCharsets.UTF_8))){
            writer.write("SNP\tCHR\tBP\tA1\tA2\tEFFECT\tP\n");
            for(var variant:inputs.base.values()){
                if(!variant.status().equals("CANDIDATE")){policy(request,"unmatched_variants","Prepared-weight row "+variant.line()+" is unadmitted: "+variant.status(),new ArrayList<>());continue;}
                require(inputs.target.byId.containsKey(variant.id())&&!inputs.target.duplicateVariantIds.contains(variant.id()),"Prepared weight lacks an unambiguous exact target BIM ID: "+variant.id());
                writer.write(PrsiceTables.tsv(variant.id())+"\t"+variant.chromosome()+"\t"+variant.position()+"\t"+PrsiceTables.tsv(variant.rawA1())+"\t"+PrsiceTables.tsv(variant.rawA2())+"\t"+variant.rawEffect()+"\t1\n");counts[1]++;
            }
        }
        require(counts[1]>0,"No admitted prepared-weight rows");var identity=map("bytes",counts[0],"sha256",HexFormat.of().formatHex(digest.digest()));require(ScenicWorkflowFiles.same(identity,pin(destination)),"Prepared source base differs from the deterministic requested transformation");
        return map("requested",true,"rows",counts[1],"identity",identity,"pvalue_column_meaning","Fixed score inclusion marker 1, not association evidence","coordinate_origin",object(object(object(request.get("parameters")).get("base")).get("columns")).get("chromosome")==null?"Explicit prepared-weight mapping from exact target BIM IDs":"Declared original weight coordinates","original_weight_file_modified",false);
    }
}
