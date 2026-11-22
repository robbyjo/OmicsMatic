/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jlinalg.settest.RSourceColumnSum;
import static org.jlinalg.cli.SkatWorkflowContracts.require;

/** Independent structural relationships among literal inputs, original exported fields and batch admission.
 * Does not deserialize RDS, independently refit R models or certify unreported upstream convergence.
 */
final class SkatWorkflowAudit {
    private SkatWorkflowAudit() { }
    static final String SCOPE="Original source/byte integrity and exported-table relationships, masks, declared variant mappings, selected x86 R colSums MAC and batch admission. No RDS deserialization, independent null/association refit, authenticated producer signature or complete convergence certificate.";
    private static final List<String> FIELD_HEADER=List.of("path","type","index","element_name_hex","value","encoding","dimensions");
    private static final List<String> SUMMARY_HEADER=List.of("set_index","set_id","original_call_attempted","source_returned","raw_p","export_p","p_kind","source_test_type_hex","input_variants","tested_variants","result_admitted","issues_hex","notes_hex","bound_hex");
    private static final Pattern NUMERICAL_WARNING=Pattern.compile("integration fail|failed to converge|non.?converg|NaNs produced|roundoff|maximum number of subdivisions|unable to calculate",Pattern.CASE_INSENSITIVE);
    record Field(String type,int index,String name,String value,String encoding,String dimensions) { }
    record Condition(String phase,int set,String kind,String message) { }
    record Variant(int global,String id,int column,String name,double weight,double mac) { }
    static int integer(String value)throws IOException {
        require(value.matches("0|[1-9][0-9]{0,9}"),"Invalid nonnegative original integer");try{return Integer.parseInt(value);}catch(NumberFormatException error){throw new IOException("Original integer exceeds capacity",error);}
    }
    static boolean logical(String value)throws IOException {require(value.equals("TRUE")||value.equals("FALSE"),"Invalid literal original logical value");return value.equals("TRUE");}
    static void same(double expected,double actual,String label)throws IOException {
        require(Double.doubleToLongBits(expected)==Double.doubleToLongBits(actual)||expected==0&&actual==0,"Contradictory original numeric field: "+label);
    }
    static double finite(String value)throws IOException{return SkatWorkflowFiles.finite(value);}
    static double probability(String value)throws IOException {double p=finite(value);require(p>=0&&p<=1,"Invalid original probability");return p;}
    static void header(List<String> actual,List<String> expected,String label)throws IOException {require(actual.equals(expected),"Invalid "+label+" table header");}
    static void nonempty(Path path,long maximum)throws IOException {require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&Files.size(path)>0&&Files.size(path)<=maximum,"Missing,empty or oversized retained original evidence: "+path);}
    static Map<String,List<Field>> fields(Path path,Map<String,Object> request,Set<String> wanted,boolean convergence)throws IOException {
        long cap=SkatWorkflowFiles.cap(request,"maximum_member_bytes");var result=new LinkedHashMap<String,List<Field>>();var counts=new HashMap<String,Integer>();
        header(SkatWorkflowFiles.rows(path,cap,7,(int)Math.min(Integer.MAX_VALUE,Math.max(1,cap/7)),(i,v)->{
            require(v[0].startsWith("/")&&!v[0].contains("\u0000"),"Invalid original field path");int index=integer(v[2]);Integer previous=counts.put(v[0],index);
            require(previous==null?index==0||index==1:previous>0&&index==previous+1,"Duplicate,missing or reordered original field index");
            require(Set.of("double","integer","logical","character","NULL","list").contains(v[1]),"Unsupported original field type");
            String name=SkatWorkflowRuntime.unhex(v[3]);require(Set.of("number","logical","utf8-hex","literal","missing").contains(v[5]),"Invalid original field encoding");
            if(v[5].equals("utf8-hex"))SkatWorkflowRuntime.unhex(v[4]);
            if(wanted.contains(v[0])||convergence&&v[0].matches("/param/Is_Converged(?:\\..*)?"))result.computeIfAbsent(v[0],key->new ArrayList<>()).add(new Field(v[1],index,name,v[4],v[5],v[6]));
        }),FIELD_HEADER,"original fields");return result;
    }
    private static Field scalar(Map<String,List<Field>> values,String key)throws IOException {
        List<Field> rows=values.get(key);require(rows!=null&&rows.size()==1&&rows.get(0).index()==1,"Missing/nonscalar original field: "+key);return rows.get(0);
    }
    private static double number(Map<String,List<Field>> values,String key)throws IOException {Field row=scalar(values,key);require(row.encoding().equals("number"),"Expected original numeric field: "+key);return finite(row.value());}
    private static String text(Map<String,List<Field>> values,String key,String separator)throws IOException {
        List<Field> rows=values.get(key);require(rows!=null,"Missing original text field: "+key);var result=new ArrayList<String>();
        for(Field row:rows) {
            if(row.type().equals("NULL")){require(rows.size()==1&&row.index()==0&&row.value().equals("NULL"),"Invalid original NULL");return "";}
            if(row.type().equals("logical")&&row.encoding().equals("logical")&&row.value().equals("NA")){result.add("NA");continue;}
            require(row.type().equals("character")&&(row.encoding().equals("utf8-hex")||row.encoding().equals("missing")),"Invalid original text encoding");result.add(row.encoding().equals("missing")?"NA":SkatWorkflowRuntime.unhex(row.value()));
        }
        return String.join(separator,result);
    }
    private static String nullableText(Map<String,List<Field>> values,String key,String separator)throws IOException {return values.containsKey(key)?text(values,key,separator):"";}
    private static List<Condition> conditions(Path root,Map<String,Object> request,int sets)throws IOException {
        var result=new ArrayList<Condition>();header(SkatWorkflowFiles.rows(root.resolve("worker/conditions.tsv"),SkatWorkflowFiles.cap(request,"maximum_member_bytes"),5,1_000_000,(i,v)->{
            require(integer(v[0])==i,"Changed condition sequence");int set=integer(v[2]);require(set<=sets&&Set.of("warning","message","error").contains(v[3]),"Invalid original condition context");result.add(new Condition(v[1],set,v[3],SkatWorkflowRuntime.unhex(v[4])));
        },true),List.of("sequence","phase","set_index","kind","message_hex"),"conditions");
        if(!result.isEmpty())nonempty(root.resolve("worker/conditions.rds"),SkatWorkflowFiles.cap(request,"maximum_member_bytes"));return result;
    }
    static Map<String,Object> completed(Path root,Map<String,Object> request)throws IOException {
        try{return completedFields(root,request);}catch(SkatWorkflowContracts.Unavailable error){throw new IOException("Incomplete captured original SKAT evidence;no complete result is admitted",error);}
    }
    private static Map<String,Object> completedFields(Path root,Map<String,Object> request)throws IOException {
        SkatWorkflowRuntime.sources(root,true);SkatWorkflowRuntime.verifyNamespace(root,"namespace-before");SkatWorkflowRuntime.verifyNamespace(root,"namespace-after");
        var info=SkatWorkflowRuntime.keyValues(root.resolve("worker/runtime-info.tsv"));require(info.getOrDefault("R_platform","").startsWith("x86_64"),"This selected R colSums audit requires the declared x86_64 runtime profile");
        SkatWorkflowFiles.Prepared prepared=SkatWorkflowFiles.prepare(root,request,true);SkatWorkflowRuntime.Outcome outcome=SkatWorkflowRuntime.outcome(root);
        require(outcome.status().equals("COMPLETED_WITH_WARNINGS")&&outcome.nullFits()==1&&outcome.setCalls()==prepared.sets().size(),"Original completed status/call counts contradict the declared batch");
        long maximum=SkatWorkflowFiles.cap(request,"maximum_member_bytes");Path worker=root.resolve("worker");
        for(String name:List.of("null-model.rds","rng-before-null.rds","rng-after-null.rds","rng-after-all.rds","worker-report.rds","SESSION.txt"))nonempty(worker.resolve(name),maximum);
        List<Condition> conditions=conditions(root,request,prepared.sets().size());require(conditions.stream().noneMatch(c->c.kind().equals("error")),"A completed capture contains a retained original error");
        var nullModel=SkatWorkflowFiles.map(request.get("null_model"));var admission=SkatWorkflowFiles.map(request.get("admission"));var test=SkatWorkflowFiles.map(request.get("test"));
        var nullFields=fields(worker.resolve("null-fields.tsv"),request,Set.of("/null_class","/null_type","/n.all","/out_type","/n.Resampling","/type.Resampling","/id_include","/s2","/design_dimensions","/resampling_dimensions","/kurtosis_dimensions"),false);
        same(prepared.samples().size(),number(nullFields,"/n.all"),"null n.all");require(text(nullFields,"/out_type",",").equals(nullModel.get("out_type")),"Original null outcome type differs");
        List<Field> included=nullFields.get("/id_include");require(included!=null&&included.size()==prepared.included().size(),"Original null sample-mask field size differs");
        for(int i=0;i<included.size();i++)same(prepared.included().get(i),finite(included.get(i).value()),"null id_include");
        int nr=((Number)nullModel.get("n_resampling")).intValue();same(nr,number(nullFields,"/n.Resampling"),"null resampling count");
        require(text(nullFields,"/type.Resampling",",").equals(nullModel.get("resampling"))||Boolean.TRUE.equals(admission.get("allow_reported_fallback")),"Original null resampling substitution was not accepted");
        boolean adjusted=text(nullFields,"/null_class",",").contains("SKAT_NULL_Model_ADJ");
        var expectedMask=new HashSet<>(prepared.included());header(SkatWorkflowFiles.rows(worker.resolve("sample-mask.tsv"),maximum,3,prepared.samples().size(),(i,v)->require(integer(v[0])==i&&v[1].equals(prepared.samples().get(i-1))&&logical(v[2])==expectedMask.contains(i),"Original sample-mask table contradicts literal input complete cases")),List.of("sample_index","sample_id","included"),"sample mask");
        require(SkatWorkflowFiles.countRows(worker.resolve("sample-mask.tsv"))==prepared.samples().size(),"Incomplete original sample-mask table");
        List<Field> dimensions=nullFields.get("/design_dimensions");require(dimensions!=null&&dimensions.size()==2,"Missing original null design dimensions");same(prepared.included().size(),finite(dimensions.get(0).value()),"null design rows");int columns=integer(dimensions.get(1).value());require(columns>0&&columns==prepared.covariates().size()+(Boolean.TRUE.equals(nullModel.get("intercept"))?1:0),"Original null design column count differs");
        matrix(worker.resolve("null-design.tsv"),request,prepared,columns,null);
        header(SkatWorkflowFiles.rows(worker.resolve("null-residuals.tsv"),maximum,4,prepared.included().size(),(i,v)->{
            require(v[0].equals(prepared.samples().get(prepared.included().get(i-1)-1)),"Original null residual sample order differs");finite(v[1]);double variance=finite(v[3]);require(variance>0,"Invalid original null variance");
            if(nullModel.get("out_type").equals("D")){double mu=finite(v[2]);require(mu>0&&mu<1,"Invalid original binary null probability");}else{require(v[2].equals("NA"),"Continuous null must not invent a binary mean");same(number(nullFields,"/s2"),variance,"continuous null residual variance");}
        }),List.of("sample_id","residual","mu","variance"),"null residuals");require(SkatWorkflowFiles.countRows(worker.resolve("null-residuals.tsv"))==prepared.included().size(),"Incomplete original null residuals");
        var summary=new ArrayList<String[]>();header(SkatWorkflowFiles.rows(worker.resolve("set-results.tsv"),maximum,14,prepared.sets().size(),(i,v)->summary.add(v)),SUMMARY_HEADER,"set summary");require(summary.size()==prepared.sets().size(),"Missing declared set outcomes");
        double[] suppliedWeights=request.containsKey("weights")?new double[prepared.variants().size()]:null;
        if(suppliedWeights!=null)SkatWorkflowFiles.rows(root.resolve("inputs/weights.tsv"),maximum,2,suppliedWeights.length,(i,v)->suppliedWeights[i-1]=finite(v[1]));
        int testedSets=0,expectedDiagnostics=0;var export=new ArrayList<List<String>>();var reports=new ArrayList<Map<String,Object>>();
        for(int si=0;si<summary.size();si++) {
            String[] row=summary.get(si);var gene=prepared.sets().get(si);Path directory=worker.resolve(String.format(java.util.Locale.ROOT,"sets/%06d",si+1));
            require(integer(row[0])==si+1&&row[1].equals(gene.id())&&logical(row[2])&&logical(row[3]),"Original call/set identities differ");require(integer(row[8])==gene.columns().size(),"Input variant count differs");int nt=integer(row[9]);require(nt<=gene.columns().size(),"Too many original tested variants");
            require(SkatWorkflowRuntime.unhex(row[11]).isEmpty(),"Completed set retains an unadmitted issue");SkatWorkflowRuntime.unhex(row[12]);String bound=SkatWorkflowRuntime.unhex(row[13]);
            for(String name:List.of("rng-before-fit.rds","rng-after-fit.rds","original-result.rds","original-genotype-diagnostic.rds"))nonempty(directory.resolve(name),maximum);
            var original=fields(directory.resolve("original-fields.tsv"),request,Set.of("/p.value","/Test.Type","/pval.zero.msg","/test.snp.mac","/param/n.marker","/param/n.marker.test","/p.value.resampling"),true);
            double p=probability(row[4]);same(p,number(original,"/p.value"),"original p-value");same(gene.columns().size(),number(original,"/param/n.marker"),"original marker count");same(nt,number(original,"/param/n.marker.test"),"original tested marker count");
            require(SkatWorkflowRuntime.unhex(row[7]).equals(nullableText(original,"/Test.Type",","))&&bound.equals(nullableText(original,"/pval.zero.msg","\n")),"Original test-type/bound fields contradict summary");
            if(nt==0) {
                require(Boolean.TRUE.equals(admission.get("allow_empty_sets"))&&!logical(row[10])&&row[5].equals("NA")&&row[6].equals("not-tested-no-variants"),"Untested set must be explicitly accepted,unadmitted and exported NA");same(1,p,"source no-test sentinel");variants(directory,request,prepared,gene,0,List.of(),suppliedWeights);expectedDiagnostics++;
            }else {
                testedSets++;expectedDiagnostics+=2;require(logical(row[10]),"A tested set is unadmitted in a completed batch");
                List<Field> mac=original.get("/test.snp.mac");require(mac!=null&&mac.size()==nt&&mac.stream().allMatch(f->f.index()>0),"Original tested-marker MAC size differs");
                List<Variant> variants=variants(directory,request,prepared,gene,nt,mac,suppliedWeights);RSourceColumnSum[] sums=new RSourceColumnSum[nt];for(int j=0;j<nt;j++)sums[j]=new RSourceColumnSum();matrix(directory.resolve("processed-genotypes.tsv"),request,prepared,nt,sums);
                for(int j=0;j<nt;j++)same(variants.get(j).mac(),sums[j].value(),"original processed-genotype MAC "+j);
                nonempty(directory.resolve("identity-only-diagnostic.rds"),maximum);nonempty(directory.resolve("source-method-resolution.rds"),maximum);
                var resolved=fields(directory.resolve("method-resolution.tsv"),request,Set.of("/method","/r.corr"),false);List<Field> rho=resolved.get("/r.corr");require(rho!=null&&!rho.isEmpty(),"Missing original method/rho resolution");for(Field f:rho)require(finite(f.value())>=0&&finite(f.value())<=1,"Invalid original rho");
                boolean substituted=rho.size()>1&&(nt==1||identicalColumns(directory.resolve("processed-genotypes.tsv"),request,prepared,nt));
                require(!substituted||Boolean.TRUE.equals(admission.get("allow_test_substitution")),"Original adaptive-test substitution was not accepted");
                require(!adjusted||Set.of("linear","linear.weighted").contains(test.get("kernel"))||Boolean.TRUE.equals(admission.get("allow_adjustment_omission")),"Original adjustment omission was not accepted");
                boolean fallback=false;for(var entry:original.entrySet())if(entry.getKey().matches("/param/Is_Converged(?:\\..*)?"))for(Field f:entry.getValue())if(f.index()>0&&!((f.encoding().equals("number")&&finiteOrNonfinite(f.value())==1)||(f.encoding().equals("logical")&&f.value().equals("TRUE"))))fallback=true;
                final int set=si+1;fallback|=conditions.stream().anyMatch(c->c.set()==set&&c.phase().equals("set-fit")&&c.kind().equals("warning")&&NUMERICAL_WARNING.matcher(c.message()).find());
                require(!fallback||Boolean.TRUE.equals(admission.get("allow_reported_fallback")),"Reported original numerical fallback/warning was not accepted");
                List<Field> resampling=original.get("/p.value.resampling");require(resampling!=null,"Missing original resampling field");
                if(nr==0)require(resampling.size()==1&&resampling.get(0).type().equals("NULL"),"Unexpected original resampling p-values");
                else{require(resampling.size()==nr,"Incomplete original resampling p-values");for(Field f:resampling)require(probability(f.value())>0,"Zero resampling p-value lacks an individual bound");nonempty(directory.resolve("original-resampling-summary.rds"),maximum);nonempty(directory.resolve("resampling-summary.tsv"),maximum);}
                if(p==0)require(admission.get("zero_policy").equals("retain-source-bound")&&!bound.isEmpty()&&row[5].equals("NA")&&row[6].equals("source-reported-bound"),"Numeric zero cannot be exported as an exact probability");
                else{require(row[6].equals("numeric-pvalue"),"Invalid admitted p-value kind");same(p,probability(row[5]),"exported p-value");}
            }
            export.add(List.of(row[0],row[1],row[5],row[6],row[10],row[13]));reports.add(Map.of("set_id",gene.id(),"tested_variants",nt,"result_admitted",logical(row[10]),"p_kind",row[6]));
        }
        require(testedSets>0&&outcome.diagnostics()==expectedDiagnostics,"No tested set or inconsistent original diagnostic-call count");
        header(SkatWorkflowFiles.rows(root.resolve("results/p-values.tsv"),maximum,6,export.size(),(i,v)->require(List.of(v).equals(export.get(i-1)),"Complete p-value export contradicts per-set source admission")),List.of("set_index","set_id","export_p","p_kind","result_admitted","bound_hex"),"batch export");require(SkatWorkflowFiles.countRows(root.resolve("results/p-values.tsv"))==export.size(),"Incomplete batch export");
        return Map.of("status","PASS_COMPLETED_ORIGINAL_OUTPUT_RELATIONSHIPS","scope",SCOPE,"tested_sets",testedSets,"sets",reports,"original_null_fits",outcome.nullFits(),"original_set_calls",outcome.setCalls(),"source_conditions",conditions.size());
    }
    private static double finiteOrNonfinite(String value)throws IOException {if(Set.of("NA","NaN","Inf","-Inf").contains(value))return Double.NaN;return finite(value);}
    private static List<Variant> variants(Path directory,Map<String,Object> request,SkatWorkflowFiles.Prepared prepared,SkatWorkflowFiles.GeneSet gene,int nt,List<Field> mac,double[] suppliedWeights)throws IOException {
        var columns=new Variant[nt];long maximum=SkatWorkflowFiles.cap(request,"maximum_member_bytes");
        header(SkatWorkflowFiles.rows(directory.resolve("variants.tsv"),maximum,7,gene.columns().size(),(i,v)->{
            int global=integer(v[0]);require(global==gene.columns().get(i-1)&&v[1].equals(prepared.variants().get(global-1)),"Original variant map contradicts explicit set membership/order");boolean tested=logical(v[2]);int column=integer(v[3]);String name=SkatWorkflowRuntime.unhex(v[4]);
            if(!tested){require(column==0&&name.isEmpty()&&v[5].equals("NA")&&v[6].equals("NA"),"Excluded variant retains tested source values");return;}
            require(column>0&&column<=nt&&columns[column-1]==null,"Invalid/duplicated original source column mapping");require(name.isEmpty()||name.equals(v[1]),"Raw source column name contradicts its separately declared identity");Field original=mac.get(column-1);require(original.name().equals(name),"Raw original MAC name differs from source column name");double value=finite(v[6]);same(value,finite(original.value()),"original MAC field/map");
            double weight=v[5].equals("NA")?Double.NaN:finite(v[5]);if(SkatWorkflowFiles.map(request.get("test")).get("kernel").toString().endsWith("weighted"))require(Double.isFinite(weight),"Missing original weighted-kernel weight");if(suppliedWeights!=null)same(suppliedWeights[global-1],weight,"declared supplied source weight");columns[column-1]=new Variant(global,v[1],column,name,weight,value);
        }),List.of("input_index","variant_id","tested","source_column","source_name_hex","source_weight","source_mac"),"variant map");
        require(SkatWorkflowFiles.countRows(directory.resolve("variants.tsv"))==gene.columns().size(),"Incomplete original variant map");for(Variant v:columns)require(v!=null,"Missing tested source column");return List.of(columns);
    }
    private static void matrix(Path path,Map<String,Object> request,SkatWorkflowFiles.Prepared prepared,int columns,RSourceColumnSum[] sums)throws IOException {
        var names=new ArrayList<>(List.of("sample_index","sample_id","source_row_name_hex"));for(int j=1;j<=columns;j++)names.add("value_"+j);
        header(SkatWorkflowFiles.rows(path,SkatWorkflowFiles.cap(request,"maximum_member_bytes"),columns+3,prepared.included().size(),(i,v)->{
            String expected=prepared.samples().get(prepared.included().get(i-1)-1);String source=SkatWorkflowRuntime.unhex(v[2]);require(integer(v[0])==i&&v[1].equals(expected)&&(source.isEmpty()||source.equals(expected)),"Original processed matrix row/mask identity differs");for(int j=0;j<columns;j++){double z=finite(v[j+3]);if(sums!=null)sums[j].add(z);}
        }),names,"original processed matrix");require(SkatWorkflowFiles.countRows(path)==prepared.included().size(),"Incomplete original processed matrix");
    }
    private static boolean identicalColumns(Path path,Map<String,Object> request,SkatWorkflowFiles.Prepared prepared,int columns)throws IOException {
        boolean[] same={true};SkatWorkflowFiles.rows(path,SkatWorkflowFiles.cap(request,"maximum_member_bytes"),columns+3,prepared.included().size(),(i,v)->{double first=finite(v[3]);for(int j=1;j<columns;j++)same[0]&=finite(v[j+3])==first;});return same[0];
    }
}
