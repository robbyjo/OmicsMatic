/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.GctaHardcallGrm;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.GctaWorkflowFiles.*;

/** Checks captured source exports; original model VERIFY is explicitly not a refit. */
final class GctaWorkflowAudit {
    private GctaWorkflowAudit() { }
    static Map<String,Object> outputs(Path root,Map<String,Object> r,Prepared input,boolean verify) throws Exception {
        String model=text(r,"model");boolean nativeEngine=text(r,"engine").equals("native");
        var report=new LinkedHashMap<String,Object>();report.put("model",model);report.put("engine",r.get("engine"));
        report.put("verification_scope","Input/inventory integrity, deterministic intake and declared export checks. Native GRM is recomputed; original model VERIFY does not independently refit or authenticate coordinated replacement.");
        if(!nativeEngine)sourceSteps(root,r);
        switch(model) {
            case "GRM" -> grm(root,r,input,report,nativeEngine);
            case "PCA" -> pca(root,r,input,report);
            case "COJO_SELECT","COJO_JOINT","COJO_CONDITION" -> cojo(root,r,input,report);
            case "REML" -> reml(root,r,input,report);
            case "LD_SCORE","LD_SCORE_REGION" -> ld(root,r,input,report);
            case "SPARSE_GRM" -> sparse(root,r,input,report);
            case "FASTGWA_BINARY" -> binary(root,r,input,report,verify);
            case "MTCOJO" -> GctaMtcojoAudit.outputs(root,r,input,report);
            default -> throw new IOException("Unsupported output model");
        }
        compareOrWrite(root.resolve("model-objects.json"),RsemJson.bytes(report),verify);return report;
    }
    private static void sourceSteps(Path root,Map<String,Object> r) throws Exception {
        var binding=read(root.resolve("worker/runtime-bindings.json"));String runtime=text(binding,"runtime");
        var pins=object(binding.get("runtime_sha256"),"runtime pins");var digests=new HashSet<>(pins.values());
        if(!(digests.equals(Set.of(GctaWorkflowRuntime.LINUX))||digests.equals(Set.of(GctaWorkflowRuntime.WINDOWS,GctaWorkflowRuntime.LIBOMP)))||!digests.contains(pins.get(runtime)))
            throw new IOException("Captured runtime profile differs from qualified source binaries");
        for(String step:text(r,"model").equals("FASTGWA_BINARY")?List.of("null-model","association"):List.of("analysis")) {
            var command=read(root.resolve("worker/"+step+"-command.json"));
            if(integer(command,"exit_code",-1,0,0)!=0)throw new IOException("Original process did not complete");
            var expected=new ArrayList<String>();expected.add(runtime);expected.addAll(GctaWorkflowRuntime.command(r,step.equals("association")));
            if(!same(expected,command.get("command")))throw new IOException("Original command differs from the closed scientific request");
            String output=log(root.resolve("worker/"+step+".stdout"),r);
            if(!output.contains("version v1.94.1")||!output.contains("Analysis finished at")||output.lines().anyMatch(s->s.startsWith("Error:")))
                throw new IOException("Original source did not report a complete successful analysis; retain all diagnostics");
        }
    }
    private static Path result(Path root,Map<String,Object> r) throws IOException {
        return root.resolve(text(r,"model").equals("FASTGWA_BINARY")?"worker/association/result":"worker/result");
    }
    private static void cohort(List<GctaHardcallGrm.SampleId> observed,List<GctaHardcallGrm.SampleId> expected) throws IOException {
        if(observed.size()!=expected.size()||!new HashSet<>(observed).equals(new HashSet<>(expected)))throw new IOException("Exported model cohort differs from complete prepared cohort");
    }
    private static void grm(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report,boolean nativeEngine) throws Exception {
        Path prefix=result(root,r);var observed=ids(Path.of(prefix+".grm.id"),r,new long[]{0});cohort(observed,input.samples());
        long entries=pairs(observed.size());checkFloats(Path.of(prefix+".grm.bin"),entries,false);checkFloats(Path.of(prefix+".grm.N.bin"),entries,true);
        long max=0;try(var n=new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(prefix+".grm.N.bin"))))) {
            for(long i=0;i<entries;i++){float value=Float.intBitsToFloat(Integer.reverseBytes(n.readInt()));if(value>(float)input.variants().size())throw new IOException("GRM pair counts exceed selected marker population");max=Math.max(max,(long)value);}
        }
        if(nativeEngine) {
            var expected=GctaWorkflowFiles.nativeGrm(root,r,input);
            if(!observed.equals(expected.samples()))throw new IOException("Native source sample order differs");
            try(var a=new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(prefix+".grm.bin"))));var n=new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(prefix+".grm.N.bin")))) ) {
                for(int i=0;i<observed.size();i++)for(int j=0;j<=i;j++) {
                    if(Integer.reverseBytes(a.readInt())!=Float.floatToRawIntBits(expected.relationship(i,j))||Integer.reverseBytes(n.readInt())!=Float.floatToRawIntBits((float)expected.jointlyCalledMarkers(i,j)))
                        throw new IOException("Native GRM/count recomputation differs");
                }
            }
            final long[] ordinal={0};rows(Path.of(prefix+".grm.N.exact.tsv"),r,entries+1,f->{
                if(ordinal[0]++==0){if(!Arrays.equals(f,new String[]{"row","column","jointly_called_markers"}))throw new IOException("Exact count header differs");return;}
                width(f,3,"exact count");int i=whole(f[0],0,observed.size()-1,"row"),j=whole(f[1],0,i,"column");
                if((long)i*(i+1)/2+j!=ordinal[0]-2||longInteger(f[2],1,Integer.MAX_VALUE,"exact count")!=expected.jointlyCalledMarkers(i,j))throw new IOException("Exact native count ledger differs");
            });if(ordinal[0]!=entries+1)throw new IOException("Exact native count ledger incomplete");
            final int[] m={-1};rows(Path.of(prefix+".markers.tsv"),r,expected.markers().size()+1L,f->{
                if(m[0]++<0){if(!Arrays.equals(f,new String[]{"marker","called_samples","supplied_allele_mean","hwe_variance","disposition"}))throw new IOException("Marker audit header differs");return;}
                width(f,5,"marker audit");var e=expected.markers().get(m[0]-1);
                if(!f[0].equals(e.id())||Integer.parseInt(f[1])!=e.calledSamples()||!f[2].equals(e.suppliedAlleleMean()==null?"NA":e.suppliedAlleleMean().toString())||!f[3].equals(e.hweVariance()==null?"NA":e.hweVariance().toString())||!f[4].equals(e.disposition().name()))throw new IOException("Native marker audit differs");
            });if(m[0]!=expected.markers().size())throw new IOException("Native marker audit incomplete");
            report.put("native_included_markers",expected.includedMarkers());report.put("source_output_weight",expected.sourceOutputWeight());
        }
        report.put("sample_count",observed.size());report.put("pairs",entries);report.put("maximum_exported_pair_count",max);
        report.put("original_pair_count_float32",true);report.put("native_integer_counts_retained",nativeEngine);
    }
    private static void sparse(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report) throws Exception {
        Path prefix=result(root,r);var ids=ids(Path.of(prefix+".grm.id"),r,new long[]{0});
        if(!ids.equals(input.samples()))throw new IOException("Sparse matrix output identity order differs from source GRM");
        float cutoff=((Number)r.get("sparse_cutoff")).floatValue();long[] expected={0};
        try(var matrix=new DataInputStream(new BufferedInputStream(Files.newInputStream(root.resolve("inputs/grm0.grm.bin"))));
                var actual=Files.newBufferedReader(Path.of(prefix+".grm.sp"),StandardCharsets.UTF_8)) {
            for(int i=0;i<ids.size();i++)for(int j=0;j<=i;j++) {
                float value=Float.intBitsToFloat(Integer.reverseBytes(matrix.readInt()));
                if(value>cutoff) {
                    String line=actual.readLine();if(line==null)throw new IOException("Sparse threshold output incomplete");String[] f=line.split("\\s+");width(f,3,"sparse output");
                    if(whole(f[0],0,ids.size()-1,"sparse row")!=i||whole(f[1],0,ids.size()-1,"sparse column")!=j||!f[2].equals(GctaWorkflowRuntime.sourceFloat8(value)))throw new IOException("Sparse GRM differs from exact source float threshold/format");expected[0]++;
                }
            }
            if(actual.readLine()!=null)throw new IOException("Unexpected sparse matrix entry");
        }
        report.put("sample_count",ids.size());report.put("sparse_entries",expected[0]);report.put("source_float32_cutoff",(double)cutoff);
        report.put("cutoff_rule","Original float value > float32 cutoff; this rule also applies to diagonal entries. Implicit entries are zero.");
    }
    private static void pca(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report) throws Exception {
        Path prefix=result(root,r);int n=input.samples().size(),k=(int)number(r,"pca_components");
        if((long)n*k>number(r,"maximum_audit_entries")||(long)n*n>number(r,"maximum_work")/k)throw new IOException("PCA audit entry/work capacity exceeded");
        var values=new ArrayList<Double>();rows(Path.of(prefix+".eigenval"),r,n,f->{width(f,1,"eigenvalue");double x=finite(f[0],"eigenvalue");if(!values.isEmpty()&&x>values.get(values.size()-1))throw new IOException("Eigenvalues are not descending");values.add(x);});
        if(values.size()!=n)throw new IOException("Full eigenvalue spectrum missing");
        var order=new ArrayList<GctaHardcallGrm.SampleId>();var vectors=new LinkedHashMap<GctaHardcallGrm.SampleId,double[]>();
        rows(Path.of(prefix+".eigenvec"),r,n,f->{width(f,k+2,"eigenvector");var id=id(f[0],f[1]);double[] row=new double[k];for(int c=0;c<k;c++)row[c]=finite(f[c+2],"eigenvector");if(vectors.putIfAbsent(id,row)!=null)throw new IOException("Duplicate eigenvector identity");order.add(id);});cohort(order,input.samples());
        double[][] v=new double[n][k],product=new double[n][k];for(int i=0;i<n;i++)v[i]=vectors.get(input.samples().get(i));
        double normA=0;
        try(var matrix=new DataInputStream(new BufferedInputStream(Files.newInputStream(root.resolve("inputs/grm0.grm.bin"))))) {
            for(int i=0;i<n;i++)for(int j=0;j<=i;j++) {
                double x=Float.intBitsToFloat(Integer.reverseBytes(matrix.readInt()));normA+=x*x*(i==j?1:2);
                for(int c=0;c<k;c++){product[i][c]+=x*v[j][c];if(i!=j)product[j][c]+=x*v[i][c];}
            }
        }
        normA=Math.sqrt(normA);double maximumResidual=0,orthogonality=0;
        for(int c=0;c<k;c++) {
                double normV=0,residual=0;for(int i=0;i<n;i++){normV=StrictMath.hypot(normV,v[i][c]);residual=StrictMath.hypot(residual,product[i][c]-values.get(c)*v[i][c]);}
            if(normV==0)throw new IOException("Zero eigenvector");double relative=residual/Math.max(Double.MIN_NORMAL,(normA+Math.abs(values.get(c)))*normV);maximumResidual=Math.max(maximumResidual,relative);
            for(int d=0;d<=c;d++){double dot=0;for(int i=0;i<n;i++)dot+=v[i][c]*v[i][d];orthogonality=Math.max(orthogonality,Math.abs(dot-(c==d?1:0)));}
        }
        if(maximumResidual>1e-4||orthogonality>1e-4)throw new IOException("PCA export fails the declared 1e-4 matrix-relative residual/orthonormality checks; no vector repair");
        report.put("sample_count",n);report.put("components",k);report.put("eigenvalues",values);report.put("maximum_matrix_relative_residual",maximumResidual);report.put("orthonormality_maximum_error",orthogonality);report.put("diagnostic_tolerance",1e-4);
        report.put("diagnostic_scope","Streaming eigenpair backward residual using (Frobenius norm(A)+abs(lambda))*norm(v); not a refit or proof of exact top-K subspace recovery.");
    }
    private static void cojo(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report) throws Exception {
        Path prefix=result(root,r);String model=text(r,"model");var tables=new LinkedHashMap<String,Object>();
        if(model.equals("COJO_SELECT")&&!Files.exists(Path.of(prefix+".jma.cojo"))) {
            if(!log(Path.of(prefix+".log"),r).contains("No SNPs have been selected."))throw new IOException("No complete joint model or explicit original null selection");
            report.put("result_kind","null-selection");report.put("selected_markers",0);report.put("joint_model",null);return;
        }
        if(!model.equals("COJO_CONDITION"))tables.put("joint",cojoTable(Path.of(prefix+".jma.cojo"),r,input,true));
        if(!model.equals("COJO_JOINT"))tables.put("conditional",cojoTable(Path.of(prefix+".cma.cojo"),r,input,false));
        report.put("tables",tables);report.put("sample_size_semantics","Original estimated effective n is a finite positive real and can exceed the input N; it is not coerced or clamped.");
    }
    private static Map<String,Object> cojoTable(Path path,Map<String,Object> r,Prepared input,boolean joint) throws Exception {
        String[] header=joint?new String[]{"Chr","SNP","bp","refA","freq","b","se","p","n","freq_geno","bJ","bJ_se","pJ","LD_r"}:new String[]{"Chr","SNP","bp","refA","freq","b","se","p","n","freq_geno","bC","bC_se","pC"};
        var known=new HashMap<String,Variant>();for(var v:input.variants())known.put(v.id(),v);var ids=new LinkedHashSet<String>();boolean[] first={true};double[] nRange={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
        rows(path,r,number(r,"maximum_result_rows")+1,f->{
            if(first[0]){if(!Arrays.equals(f,header))throw new IOException("GCTA COJO schema differs");first[0]=false;return;}
            width(f,header.length,"COJO output");var v=known.get(f[1]);var summary=input.summary().get(f[1]);
            if(v==null||summary==null||!ids.add(v.id())||Integer.parseInt(f[0])!=v.chromosome()||Long.parseLong(f[2])!=v.position()||!f[3].equals(summary.get(1)))throw new IOException("COJO marker identity/allele metadata differs");
            double[] x=new double[f.length];for(int i=4;i<f.length;i++)x[i]=finite(f[i],"COJO "+header[i]);
            for(int i:new int[]{4,7,9,12})if(x[i]<0||x[i]>1)throw new IOException("COJO frequency/P outside range");
            if(x[6]<=0||x[8]<=0||x[11]<=0||(joint&&Math.abs(x[13])>1.000001))throw new IOException("Undefined COJO effect, SE, effective sample size or LD correlation");
            nRange[0]=Math.min(nRange[0],x[8]);nRange[1]=Math.max(nRange[1],x[8]);
        });
        if(first[0])throw new IOException("COJO table lacks a schema");
        var result=new LinkedHashMap<String,Object>();result.put("file",path.getFileName().toString());result.put("rows",ids.size());if(!ids.isEmpty())result.put("effective_n_range",List.of(nRange[0],nRange[1]));return result;
    }
    private static void reml(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report) throws Exception {
        Path prefix=result(root,r);var lines=log(Path.of(prefix+".hsq"),r).lines().filter(s->!s.isBlank()).toList();
        if(lines.isEmpty()||!lines.get(0).equals("Source\tVariance\tSE"))throw new IOException("REML hsq schema differs");
        var objects=new LinkedHashMap<String,Object>();var fixed=new ArrayList<List<Double>>();boolean fixedSection=false;
        for(int i=1;i<lines.size();i++) {
            String line=lines.get(i);if(line.equals("Fix_eff\tSE")){if(fixedSection)throw new IOException("Duplicate REML fixed-effect section");fixedSection=true;continue;}
            String[] f=line.split("\t",-1);
            if(fixedSection){width(f,2,"fixed effect");double b=finite(f[0],"fixed effect"),se=finite(f[1],"fixed SE");if(se<0)throw new IOException("Negative fixed-effect SE");fixed.add(List.of(b,se));}
            else {if(f.length<2||f.length>3||objects.containsKey(f[0]))throw new IOException("Unexpected REML model row");var numbers=new ArrayList<Double>();for(int j=1;j<f.length;j++)numbers.add(finite(f[j],"REML "+f[0]));if(numbers.size()==2&&numbers.get(1)<0)throw new IOException("Negative REML SE");objects.put(f[0],numbers);}
        }
        if(fixed.size()!=input.fixedColumns())throw new IOException("Original fixed-effect design dimension changed; no automatic column removal");
        int q=input.grmSamples().size()+1;var names=new ArrayList<String>();for(int i=0;i<q-1;i++)names.add(q==2?"V(G)":"V(G"+(i+1)+")");names.add("V(e)");
        remlSummarySchema(objects,names);
        double total=0,absoluteTotal=0;for(String name:names){double value=estimate(objects,name);if(!Boolean.TRUE.equals(r.get("reml_unconstrained"))&&value<0)throw new IOException("Negative constrained variance component");total+=value;absoluteTotal+=Math.abs(value);}
        double sumBound=.5000001e-6*(q+1)+4*q*Math.ulp(absoluteTotal);
        if(Math.abs(total-estimate(objects,"Vp"))>sumBound||estimate(objects,"n")!=input.samples().size())throw new IOException("REML variance total or complete sample cohort differs");
        estimate(objects,"logL");String raw=log(Path.of(prefix+".log"),r);
        if(!raw.contains("Log-likelihood ratio converged.")||raw.contains("not converged")||raw.contains("Results are not reliable"))throw new IOException("Original REML did not establish convergence");
        var logLines=raw.lines().filter(s->!s.isBlank()).toList();int start=-1;
        for(int i=0;i<logLines.size();i++)if(logLines.get(i).contains("Sampling variance/covariance of the estimates of variance components:"))start=i+1;
        if(start<0||start+q>logLines.size())throw new IOException("Complete original variance-component covariance export missing");
        var covariance=new ArrayList<List<Double>>();BigDecimal covarianceSum=BigDecimal.ZERO,covarianceRadius=BigDecimal.ZERO;
        for(int i=0;i<q;i++) {
            String[] f=logLines.get(start+i).strip().split("\\s+");width(f,q,"variance-component covariance");var row=new ArrayList<Double>();for(String s:f)row.add(finite(s,"variance covariance"));
            for(String value:f){var printed=new BigDecimal(value);covarianceSum=covarianceSum.add(printed);covarianceRadius=covarianceRadius.add(printed.ulp().abs().multiply(new BigDecimal("0.5")));}
            if(row.get(i)<0)throw new IOException("Negative original sampling covariance diagonal");
            double rounding=.5*new java.math.BigDecimal(f[i]).ulp().doubleValue();
            double lower=Math.sqrt(Math.max(0,row.get(i)-rounding)),upper=Math.sqrt(row.get(i)+rounding),reported=se(objects,names.get(i));
            double seRadius=.5000001e-6+8*Math.ulp(Math.max(upper,reported));
            if(reported+seRadius<lower||reported-seRadius>upper)throw new IOException("REML exported covariance/SE disagree beyond their original decimal rounding intervals");covariance.add(row);
        }
        for(int i=0;i<q;i++)for(int j=0;j<i;j++)if(!covariance.get(i).get(j).equals(covariance.get(j).get(i)))throw new IOException("Asymmetric original sampling covariance");
        remlSummaryRelations(objects,names,covarianceSum,covarianceRadius);
        var seen=new HashSet<GctaHardcallGrm.SampleId>();long[] rows={0};
        GctaWorkflowFiles.rows(Path.of(prefix+".indi.blp"),r,number(r,"maximum_samples"),f->{
            width(f,2+2*q,"complete BLP export");var id=id(f[0],f[1]);if(!seen.add(id))throw new IOException("Duplicate BLP identity");for(int j=2;j<f.length;j++)finite(f[j],"BLP column");rows[0]++;
        });cohort(new ArrayList<>(seen),input.samples());
        report.put("hsq",objects);report.put("fixed_effects",fixed);report.put("variance_component_covariance",covariance);report.put("variance_component_order",names);
        report.put("BLP_rows",rows[0]);report.put("BLP_column_pairs","For each component including residual: original Py[i]*variance followed by original component BLUP; complete raw table retained");
        report.put("unconstrained",r.get("reml_unconstrained"));report.put("uncertainty_semantics","Original AI-REML sampling covariance, not a generic expected-Fisher replacement. Original logs/hsq/BLP and available null-fit log exports are retained.");
        report.put("variance_export_precision","Original .hsq variance/SE values use six fixed decimals; log sampling covariance uses scientific notation with six decimal places. Printed zeros are not imputed or interpreted as exact internal zeros.");
        report.put("rounded_zero_variance_total",estimate(objects,"Vp")==0);report.put("variance_sum_rounding_bound",sumBound);
    }
    @SuppressWarnings("unchecked") private static double estimate(Map<String,Object> values,String key) throws IOException {
        if(!(values.get(key) instanceof List<?> list)||list.isEmpty())throw new IOException("Missing REML object: "+key);return ((Number)list.get(0)).doubleValue();
    }
    private static double se(Map<String,Object> values,String key) throws IOException {
        if(!(values.get(key) instanceof List<?> list)||list.size()!=2)throw new IOException("Missing REML uncertainty: "+key);return ((Number)list.get(1)).doubleValue();
    }
    private static void remlSummarySchema(Map<String,Object> values,List<String> components) throws IOException {
        var expected=new LinkedHashSet<String>(components);expected.add("Vp");
        for(int i=0;i<components.size()-1;i++)expected.add(components.get(i)+"/Vp");
        if(components.size()>2)expected.add("Sum of V(G)/Vp");
        var scalars=new HashSet<>(Set.of("logL","n"));
        // This closed profile uses the original default single-GRM LRT. With
        // --mgrm and no explicit --reml-lrt, the original omits that test.
        if(components.size()==2)scalars.addAll(Set.of("logL0","LRT","df","Pval"));
        expected.addAll(scalars);
        if(!values.keySet().equals(expected))throw new IOException("REML summary schema is incomplete or differs from the declared observed-scale univariate profile");
        for(String key:expected)if(!(values.get(key) instanceof List<?> row)||row.size()!=(scalars.contains(key)?1:2))
            throw new IOException("REML summary schema has an incomplete estimate/uncertainty row: "+key);
    }
    private static void remlSummaryRelations(Map<String,Object> values,List<String> components,
            BigDecimal covarianceSum,BigDecimal covarianceRadius) throws IOException {
        BigDecimal genetic=BigDecimal.ZERO,geneticRadius=BigDecimal.ZERO;
        for(int i=0;i<components.size()-1;i++) {
            String key=components.get(i);double value=estimate(values,key);
            remlRatio(values,key+"/Vp",BigDecimal.valueOf(value),fixedRadius(value,6));
            genetic=genetic.add(BigDecimal.valueOf(value));geneticRadius=geneticRadius.add(fixedRadius(value,6));
        }
        if(components.size()>2)remlRatio(values,"Sum of V(G)/Vp",genetic,geneticRadius);
        double totalSE=se(values,"Vp");BigDecimal center=BigDecimal.valueOf(totalSE),radius=fixedRadius(totalSE,6);
        BigDecimal lower=center.subtract(radius).max(BigDecimal.ZERO),upper=center.add(radius);
        if(lower.multiply(lower).compareTo(covarianceSum.add(covarianceRadius))>0
                ||upper.multiply(upper).compareTo(covarianceSum.subtract(covarianceRadius))<0)
            throw new IOException("REML total variance SE disagrees with the full original component covariance beyond printed rounding intervals");
        if(components.size()==2) {
            double statistic=estimate(values,"LRT"),probability=estimate(values,"Pval");
            if(statistic<0||estimate(values,"df")!=1||probability<0||probability>.5)
                throw new IOException("REML likelihood-test statistic, degrees of freedom or original one-sided P value is invalid");
            double full=estimate(values,"logL"),reduced=estimate(values,"logL0");
            BigDecimal difference=BigDecimal.valueOf(full).subtract(BigDecimal.valueOf(reduced));
            BigDecimal differenceRadius=fixedRadius(full,3).add(fixedRadius(reduced,3));
            BigDecimal two=BigDecimal.valueOf(2),stat=BigDecimal.valueOf(statistic),statRadius=fixedRadius(statistic,3);
            BigDecimal minimum=difference.subtract(differenceRadius).multiply(two).max(BigDecimal.ZERO);
            BigDecimal maximum=difference.add(differenceRadius).multiply(two).max(BigDecimal.ZERO);
            if(stat.subtract(statRadius).compareTo(maximum)>0||stat.add(statRadius).compareTo(minimum)<0)
                throw new IOException("REML likelihood-test statistic disagrees with the original full/null log likelihoods beyond printed rounding intervals");
        }
    }
    private static void remlRatio(Map<String,Object> values,String key,BigDecimal numerator,BigDecimal numeratorRadius) throws IOException {
        double ratio=estimate(values,key),total=estimate(values,"Vp");
        BigDecimal h=BigDecimal.valueOf(ratio),vp=BigDecimal.valueOf(total),hRadius=fixedRadius(ratio,6),vpRadius=fixedRadius(total,6);
        BigDecimal residual=numerator.subtract(h.multiply(vp)).abs();
        BigDecimal bound=numeratorRadius.add(h.abs().multiply(vpRadius)).add(vp.abs().multiply(hRadius)).add(hRadius.multiply(vpRadius));
        // Cross multiplication remains defined when printed Vp rounds to zero.
        // A signed or >1 ratio is valid when consistent; never clamp it.
        if(residual.compareTo(bound)>0)throw new IOException("REML variance ratio disagrees with original component/total exports beyond printed rounding intervals: "+key);
    }
    private static BigDecimal fixedRadius(double value,int places) {
        return BigDecimal.ONE.scaleByPowerOfTen(-places).multiply(new BigDecimal("0.5000001"))
            .add(BigDecimal.valueOf(Math.ulp(value)).multiply(BigDecimal.valueOf(4)));
    }
    private static void ld(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report) throws Exception {
        Path path=Path.of(result(root,r)+".score.ld");boolean region=text(r,"model").equals("LD_SCORE_REGION");
        String[] header=region?new String[]{"SNP","chr","bp","freq","mean_rsq","snp_num","max_rsq","ldscore_SNP","ldscore_region"}:new String[]{"SNP","chr","bp","MAF","mean_rsq","snp_num","max_rsq","ldscore"};
        var known=new HashMap<String,Variant>();for(var v:input.variants())known.put(v.id(),v);var seen=new HashSet<String>();boolean[] first={true};
        rows(path,r,number(r,"maximum_result_rows")+1,f->{
            if(first[0]){if(!Arrays.equals(f,header))throw new IOException("LD score schema differs");first[0]=false;return;}
            width(f,header.length,"LD score");var v=known.get(f[0]);if(v==null||!seen.add(v.id())||Integer.parseInt(f[1])!=v.chromosome()||Long.parseLong(f[2])!=v.position())throw new IOException("LD marker metadata differs");
            for(int i=3;i<f.length;i++)finite(f[i],"LD score field");double frequency=Double.parseDouble(f[3]),max=Double.parseDouble(f[6]);
            if(frequency<0||frequency>1||max<0||max>1.000001||longInteger(f[5],0,Integer.MAX_VALUE,"LD neighbor count")>input.variants().size())throw new IOException("Invalid LD frequency/correlation/count");
            if(!Boolean.TRUE.equals(r.get("ld_adjusted"))&&Double.parseDouble(f[4])<0)throw new IOException("Negative unadjusted mean r-squared");
        });
        if(first[0]||seen.isEmpty())throw new IOException("No complete LD score table");report.put("rows",seen.size());
        report.put("source_absent_markers",input.variants().stream().map(Variant::id).filter(id->!seen.contains(id)).toList());
        report.put("adjusted",r.get("ld_adjusted"));report.put("negative_adjusted_values_preserved",true);report.put("source_columns",List.of(header));
    }
    private static void binary(Path root,Map<String,Object> r,Prepared input,Map<String,Object> report,boolean verify) throws Exception {
        Path prefix=root.resolve("worker/null-model/result.fastGWA");var ids=ids(Path.of(prefix+".mdl.id"),r,new long[]{0});cohort(ids,input.samples());
        Path file=Path.of(prefix+".mdl.bin2");int n,p;boolean precise;double tao,gamma;double[][] sections;
        try(var data=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if(!Arrays.equals(data.readNBytes(5),new byte[]{'f','G','L','M',0}))throw new IOException("Binary GLMM model magic differs");
            n=Integer.reverseBytes(data.readInt());p=Integer.reverseBytes(data.readInt());int flag=data.readUnsignedByte();if(flag>1)throw new IOException("Invalid source bool encoding");precise=flag==1;
            long cells=2L*n*(p+1L);if(n!=ids.size()||p!=input.fixedColumns()||cells>number(r,"maximum_audit_entries")||cells>Integer.MAX_VALUE||Files.size(file)!=30+cells*8)throw new IOException("Complete binary GLMM model dimensions/capacity differ");
            tao=readDouble(data);gamma=readDouble(data);if(tao<0||gamma<=0)throw new IOException("Invalid original binary variance/calibration parameter");
            sections=new double[][]{new double[n],new double[n],new double[n*p],new double[n*p]};for(double[] values:sections)for(int i=0;i<values.length;i++)values[i]=readDouble(data);
            if(data.read()!=-1)throw new IOException("Unexpected model trailer");for(double value:sections[0])if(value<=0||value>=1)throw new IOException("Undefined original binary model probability");
        }
        var decoded=new StringBuilder("FID\tIID\tmu\tphenoVec");for(String name:List.of("covar","H"))for(int c=0;c<p;c++)decoded.append('\t').append(name).append('_').append(c);decoded.append('\n');
        for(int i=0;i<n;i++) {
            decoded.append(ids.get(i).familyId()).append('\t').append(ids.get(i).individualId()).append('\t').append(sections[0][i]).append('\t').append(sections[1][i]);
            for(int matrix=2;matrix<4;matrix++)for(int c=0;c<p;c++)decoded.append('\t').append(sections[matrix][c*n+i]);decoded.append('\n');
        }
        compareOrWrite(root.resolve("null-model-vectors.tsv"),decoded.toString().getBytes(StandardCharsets.UTF_8),verify);
        var known=new HashMap<String,Variant>();for(var v:input.variants())known.put(v.id(),v);var seen=new HashSet<String>();boolean[] first={true};
        String[] header={"CHR","SNP","POS","A1","A2","N","AF1","T","SE_T","P_noSPA","BETA","SE","P","CONVERGE"};
        rows(Path.of(result(root,r)+".fastGWA"),r,number(r,"maximum_result_rows")+1,f->{
            if(first[0]){if(!Arrays.equals(f,header))throw new IOException("Original binary association schema differs");first[0]=false;return;}
            width(f,header.length,"binary association");var v=known.get(f[1]);if(v==null||!seen.add(v.id())||Integer.parseInt(f[0])!=v.chromosome()||Long.parseLong(f[2])!=v.position()||!f[3].equals(v.a1())||!f[4].equals(v.a2()))throw new IOException("Binary association allele/marker identity differs");
            for(int i=5;i<f.length;i++)finite(f[i],"binary association "+header[i]);
            if(whole(f[5],1,n,"SNP called sample count")<1||Double.parseDouble(f[6])<0||Double.parseDouble(f[6])>1||Double.parseDouble(f[8])<=0||Double.parseDouble(f[11])<=0)throw new IOException("Undefined binary association estimate");
            for(int i:new int[]{9,12})if(Double.parseDouble(f[i])<0||Double.parseDouble(f[i])>1)throw new IOException("Invalid binary association P");
            if(!f[13].equals("1"))throw new IOException("Original SPA did not converge for every exported variant; retain complete null model and partial association table");
        });
        if(first[0]||seen.isEmpty())throw new IOException("No complete binary association table");
        String log=log(root.resolve("worker/null-model/result.log"),r);if(!log.contains("fastGWA-BB-REML converged."))throw new IOException("Original binary null model did not converge");
        report.put("null_model",Map.of("format","GCTA1.94.1 fGLM bin2 little-endian","samples",n,"covariate_columns",p,"bPreciseCovar",precise,"taoVal",tao,"c_inf",gamma,"raw_complete_model","worker/null-model/result.fastGWA.mdl.bin2","decoded_complete_arrays","null-model-vectors.tsv"));
        report.put("association_rows",seen.size());report.put("source_absent_markers",input.variants().stream().map(Variant::id).filter(id->!seen.contains(id)).toList());
        report.put("platform_calibration_scope","Original model calibration can differ by runtime flavor at the same seed. No forced cross-platform numeric equality or source p-value replacement.");
    }
    private static double readDouble(DataInputStream data) throws IOException {double x=Double.longBitsToDouble(Long.reverseBytes(data.readLong()));if(!Double.isFinite(x))throw new IOException("Nonfinite original model object");return x;}
    private static String log(Path path,Map<String,Object> r) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>number(r,"maximum_log_bytes"))throw new IOException("Require bounded original log/model text: "+path.getFileName());
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
    }
    static void provenance(Path root,Map<String,Object> r,Map<String,Object> manifest,Map<String,Object> objects) throws Exception {
        if(text(r,"engine").equals("original")) {
            var binding=read(root.resolve("worker/runtime-bindings.json"));var pins=object(binding.get("runtime_sha256"),"runtime pins");
            String flavor=pins.containsValue(GctaWorkflowRuntime.WINDOWS)?"GCTA1.94.1-Windows-x86_64":"GCTA1.94.1-Linux-x86_64";
            if(!same(pins,manifest.get("runtime_sha256"))||!GctaWorkflowRuntime.SOURCE.equals(manifest.get("upstream_source_commit"))||!flavor.equals(manifest.get("runtime_flavor")))throw new IOException("Manifest runtime flavor/source/hash records differ");
            var steps=new ArrayList<Map<String,Object>>();for(String step:text(r,"model").equals("FASTGWA_BINARY")?List.of("null-model","association"):List.of("analysis"))steps.add(read(root.resolve("worker/"+step+"-command.json")));
            if(!same(steps,manifest.get("original_steps")))throw new IOException("Manifest process records differ from retained step records");
        }else if(text(r,"model").equals("GRM")) {
            if(!GctaHardcallGrm.SOURCE_PROFILE.equals(manifest.get("native_source_profile"))||!same(objects.get("source_output_weight"),manifest.get("native_source_output_weight"))||!same(objects.get("native_included_markers"),manifest.get("native_included_markers")))throw new IOException("Manifest native model/source records differ");
        }else if(!"GCTA1.94.1-float-threshold-sparse-GRM".equals(manifest.get("native_source_profile"))||!same(objects.get("source_float32_cutoff"),manifest.get("source_effective_float32_cutoff"))||!same(objects.get("sparse_entries"),manifest.get("sparse_entries")))throw new IOException("Manifest native sparse model/source records differ");
    }
}
