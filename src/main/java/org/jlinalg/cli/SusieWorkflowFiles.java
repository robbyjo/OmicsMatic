/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Exact labeled TSV intake and deterministic float64 transport, without allele or model repair. */
final class SusieWorkflowFiles {
    private SusieWorkflowFiles() { }
    private static final Pattern DECIMAL=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    record Table(String[] header,List<String[]> rows) { }
    static List<String> prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        String model=text(r,"model");int maxP=((Number)r.get("maximum_variables")).intValue();
        List<String> ids;long samples=0;var parameters=new LinkedHashMap<String,Object>();
        for(String key:List.of("profile","model","sample_size","variance_y","yty","ld_origin","effects","maximum_iterations","tolerance","coverage",
            "min_abs_corr","estimate_prior_variance","estimate_prior_method","scaled_prior_variance","prior_variance","residual_variance",
            "estimate_residual_variance","standardize","intercept","refine","n_purity","check_prior","prior_tol","seed"))
            if(r.containsKey(key))parameters.put(key,r.get(key));
        String variantText;
        if(model.equals("INDIVIDUAL")) {
            Table design=table(resolve(root,text(r,"design")),((Number)r.get("maximum_samples")).intValue(),maxP+1);
            if(design.header().length<2||!design.header()[0].equals("sample_id")||design.rows().size()<3)throw new IOException("Design requires sample_id, variant columns and at least three samples");
            ids=identities(Arrays.asList(design.header()).subList(1,design.header().length));samples=design.rows().size();
            budget(r,ids.size(),samples,true);
            Table phenotype=table(resolve(root,text(r,"phenotype")),design.rows().size(),2);
            if(!Arrays.equals(phenotype.header(),new String[]{"sample_id","response"})||phenotype.rows().size()!=samples)throw new IOException("Phenotype requires sample_id,response and the exact design sample population/order");
            var seen=new HashSet<String>();double[] minima=new double[ids.size()],maxima=new double[ids.size()];Arrays.fill(minima,Double.POSITIVE_INFINITY);Arrays.fill(maxima,Double.NEGATIVE_INFINITY);
            var x=new StringBuilder(String.join("\t",design.header())).append('\n');var y=new StringBuilder("sample_id\tresponse\n");
            double yMin=Double.POSITIVE_INFINITY,yMax=Double.NEGATIVE_INFINITY;
            for(int i=0;i<design.rows().size();i++) {
                poll();String[] row=design.rows().get(i),p=phenotype.rows().get(i);exactIdentity(row[0]);
                if(!seen.add(row[0])||!row[0].equals(p[0]))throw new IOException("Duplicate or differently ordered sample identity");
                x.append(row[0]);for(int j=0;j<ids.size();j++){double v=number(row[j+1]);minima[j]=Math.min(minima[j],v);maxima[j]=Math.max(maxima[j],v);x.append('\t').append(v);}x.append('\n');
                double v=number(p[1]);yMin=Math.min(yMin,v);yMax=Math.max(yMax,v);y.append(p[0]).append('\t').append(v).append('\n');
            }
            for(int j=0;j<ids.size();j++)if(minima[j]==maxima[j])throw new IOException("Constant design variable is unsupported: "+ids.get(j));
            if(yMin==yMax)throw new IOException("Constant response has no admitted regression variance");
            transport(root,"design.tsv",x.toString(),compare);transport(root,"phenotype.tsv",y.toString(),compare);
            if(r.containsKey("variants"))variantText=variants(resolve(root,text(r,"variants")),List.of("variant_id","prior_weight"),ids,maxP,r);
            else variantText="variant_id\n"+String.join("\n",ids)+"\n";
        }else {
            Table variants=table(resolve(root,text(r,"variants")),maxP,4);
            List<String> columns=model.equals("RSS_Z")?List.of("variant_id","z"):model.equals("SUFFICIENT")?List.of("variant_id","xty"):List.of("variant_id","beta","standard_error");
            checkVariantColumns(variants.header(),columns);ids=identities(variants.rows().stream().map(row->row[0]).toList());
            samples=r.get("sample_size") instanceof Number n?n.longValue():0;budget(r,ids.size(),samples,false);
            variantText=canonicalVariants(variants,columns,r);
            double[] matrix=matrix(resolve(root,text(r,"matrix")),ids,model.equals("SUFFICIENT"));
            if(model.equals("SUFFICIENT")) {
                int p=ids.size(),size=p+1;double[] joint=new double[Math.multiplyExact(size,size)];
                for(int i=0;i<p;i++){System.arraycopy(matrix,i*p,joint,i*size,p);double xy=number(variants.rows().get(i)[1]);joint[i*size+p]=xy;joint[p*size+i]=xy;}
                joint[joint.length-1]=((Number)r.get("yty")).doubleValue();positiveSemidefinite(joint,size);
            }else positiveSemidefinite(matrix,ids.size());
            var out=new StringBuilder("variant_id\t").append(String.join("\t",ids)).append('\n');
            for(int i=0;i<ids.size();i++){poll();out.append(ids.get(i));for(int j=0;j<ids.size();j++)out.append('\t').append(matrix[i*ids.size()+j]);out.append('\n');}
            transport(root,"matrix.tsv",out.toString(),compare);
        }
        parameters.put("variables",ids.size());parameters.put("samples",samples);parameters.put("transport","explicit-float64-exact-identity-TSV");
        transport(root,"variants.tsv",variantText,compare);
        byte[] bytes=RsemJson.bytes(parameters);Path file=root.resolve("worker/parameters.json");
        if(compare){if(!Arrays.equals(bytes,Files.readAllBytes(file)))throw new IOException("Prepared parameters differ");}
        else Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
        return ids;
    }
    private static String variants(Path path,List<String> columns,List<String> ids,int cap,Map<String,Object> r)throws IOException {
        Table t=table(path,cap,columns.size());if(!Arrays.equals(t.header(),columns.toArray(String[]::new))||!t.rows().stream().map(row->row[0]).toList().equals(ids))throw new IOException("Individual prior weights require exact design variant identities/order");
        return canonicalVariants(t,List.of("variant_id"),r);
    }
    private static String canonicalVariants(Table t,List<String> required,Map<String,Object> r)throws IOException {
        boolean weighted=t.header().length>required.size();double sum=0;var weights=new ArrayList<Double>();
        var out=new StringBuilder(String.join("\t",t.header())).append('\n');
        for(String[] row:t.rows()) {
            poll();exactIdentity(row[0]);out.append(row[0]);double beta=0;
            for(int j=1;j<row.length;j++) {
                double value=number(row[j]);String field=t.header()[j];
                if(field.equals("prior_weight")){if(value<0)throw new IOException("Prior weights must be nonnegative");sum+=value;weights.add(value);}
                if(field.equals("beta"))beta=value;
                if(field.equals("standard_error")) {
                    if(!(value>0))throw new IOException("Standard errors must be positive");double z=beta/value;
                    if(!Double.isFinite(z)||(beta!=0&&z==0)||!Double.isFinite(value*value)||value*value==0)throw new IOException("Positive summary-statistic model terms exceed float64 range");
                    pve(z,r);
                }
                if(field.equals("z"))pve(value,r);
                out.append('\t').append(value);
            }out.append('\n');
        }
        if(weighted) {
            if(!(sum>0)||!Double.isFinite(sum))throw new IOException("Prior weight total must be finite and positive");
            for(double w:weights)if(w>0&&w/sum==0)throw new IOException("Positive prior weight lost during source normalization");
        }
        return out.toString();
    }
    private static void pve(double z,Map<String,Object> r)throws IOException {
        if(!SusieWorkflowContracts.legacy(r)&&r.get("sample_size") instanceof Number n) {
            double square=z*z,denominator=square+n.doubleValue()-2,adjust=(n.doubleValue()-1)/denominator;
            if(!Double.isFinite(square)||!Double.isFinite(denominator)||!(adjust>0)||!Double.isFinite(adjust)||(z!=0&&Math.sqrt(adjust)*z==0))throw new IOException("Original PVE adjustment would lose or overflow a model term");
        }
    }
    private static void checkVariantColumns(String[] actual,List<String> required)throws IOException {
        var expected=new ArrayList<>(required);if(actual.length==required.size()+1)expected.add("prior_weight");
        if(!Arrays.equals(actual,expected.toArray(String[]::new)))throw new IOException("Require exact variant columns: "+required+" with optional final prior_weight");
    }
    private static List<String> identities(List<String> input)throws IOException {
        if(input.isEmpty())throw new IOException("Empty variant population");var seen=new HashSet<String>();
        for(String id:input){exactIdentity(id);if(!seen.add(id))throw new IOException("Repeated exact variant identity");}return List.copyOf(input);
    }
    static void exactIdentity(String id)throws IOException {
        identity(id);for(int i=0;i<id.length();i++){char c=id.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=id.length()||!Character.isLowSurrogate(id.charAt(++i)))throw new IOException("Malformed Unicode identity");}else if(Character.isLowSurrogate(c))throw new IOException("Malformed Unicode identity");}
    }
    static Table table(Path path,int rows,int columns)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require a regular captured TSV file");
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line=reader.readLine();if(line==null||line.length()>2_000_000)throw new IOException("Missing or oversized TSV header");
            String[] header=line.split("\t",-1);if(header.length>columns)throw new IOException("TSV column capacity exceeded");var data=new ArrayList<String[]>();
            while((line=reader.readLine())!=null){poll();if(line.length()>2_000_000||data.size()>=rows)throw new IOException("TSV row/line capacity exceeded");String[] fields=line.split("\t",-1);if(fields.length!=header.length)throw new IOException("TSV width differs");data.add(fields);}
            return new Table(header,List.copyOf(data));
        }
    }
    private static double[] matrix(Path path,List<String> ids,boolean crossproduct)throws IOException {
        int p=ids.size();Table t=table(path,p,p+1);
        if(t.header().length!=p+1||!t.header()[0].equals("variant_id")||!Arrays.asList(t.header()).subList(1,p+1).equals(ids)||t.rows().size()!=p)throw new IOException("Matrix labels/dimensions must exactly match variant identities/order");
        double[] values=new double[Math.multiplyExact(p,p)];
        for(int i=0;i<p;i++){if(!t.rows().get(i)[0].equals(ids.get(i)))throw new IOException("Matrix row identity/order differs");for(int j=0;j<p;j++)values[i*p+j]=number(t.rows().get(i)[j+1]);
            if(crossproduct?!(values[i*p+i]>0):values[i*p+i]!=1)throw new IOException("Require positive cross-product diagonal or exact unit LD diagonal");}
        for(int i=0;i<p;i++)for(int j=0;j<i;j++)if(values[i*p+j]!=values[j*p+i])throw new IOException("Matrix must be symmetric; no averaging/repair is performed");return values;
    }
    static double number(String text)throws IOException {
        if(text.length()>1024||!DECIMAL.matcher(text).matches())throw new IOException("Require finite decimal numeric tokens; no trimming or NA/Inf repair");
        try{double value=Double.parseDouble(text);if(!Double.isFinite(value)||(value==0&&new BigDecimal(text).signum()!=0))throw new NumberFormatException();return value;}
        catch(NumberFormatException error){throw new IOException("Decimal input overflows or loses a nonzero float64 value",error);}
    }
    private static void budget(Map<String,Object> r,int p,long n,boolean individual)throws IOException {
        try {
            long dimension=p+(individual?0:1L),matrix=Math.multiplyExact(dimension,dimension);
            if(matrix>((Number)r.get("maximum_matrix_entries")).longValue()||individual&&Math.multiplyExact(n,p)>((Number)r.get("maximum_matrix_entries")).longValue())throw new IOException("maximum_matrix_entries exceeded");
            long work=Math.multiplyExact((long)p,p);
            work=Math.multiplyExact(work,Math.multiplyExact(((Number)r.get("maximum_iterations")).longValue(),Math.min(p,((Number)r.get("effects")).longValue())));
            work=Math.addExact(work,individual?Math.multiplyExact(n,Math.multiplyExact((long)p,p)):Math.multiplyExact(dimension,matrix));
            if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("maximum_work exceeded by declared preparation/model estimate");
        }catch(ArithmeticException error){throw new IOException("Declared work/dimensions exceed integer capacity",error);}
    }
    private static void transport(Path root,String name,String text,boolean compare)throws IOException {
        Path path=root.resolve("worker/"+name);byte[] raw=text.getBytes(StandardCharsets.UTF_8);
        if(compare){if(!Arrays.equals(raw,Files.readAllBytes(path)))throw new IOException("Prepared transport differs: "+name);}
        else Files.write(path,raw,StandardOpenOption.CREATE_NEW);
    }
    private static void poll()throws InterruptedIOException {if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("SuSiE input/validation interrupted; no result admitted");}
    private static void positiveSemidefinite(double[] input,int n)throws IOException {
        double[] scales=new double[n],a=new double[input.length];int[] order=new int[n];
        for(int i=0;i<n;i++){poll();double d=input[i*n+i];if(!(d>0)||!Double.isFinite(d))throw new IOException("Positive finite covariance diagonal required");scales[i]=Math.sqrt(d);order[i]=i;}
        for(int i=0;i<n;i++)for(int j=0;j<=i;j++) {
            double x=input[i*n+j],y=input[j*n+i];if(x!=y)throw new IOException("Cross-products must be exactly symmetric");
            double value=x/scales[i]/scales[j];if(!Double.isFinite(value)||Math.abs(value)>1+1e-10||(x!=0&&value==0))throw new IOException("Invalid or lost covariance term");a[i*n+j]=a[j*n+i]=value;
        }
        for(int k=0;k<n;k++) {
            poll();int best=k;for(int i=k+1;i<n;i++)if(a[order[i]*n+order[i]]>a[order[best]*n+order[best]])best=i;
            int swap=order[k];order[k]=order[best];order[best]=swap;int pivot=order[k];double d=a[pivot*n+pivot];
            if(d<=1e-10){for(int i=k;i<n;i++){poll();for(int j=k;j<=i;j++)if(Math.abs(a[order[i]*n+order[j]])>1e-10)throw new IOException("Matrix is not positive semidefinite");}return;}
            for(int i=k+1;i<n;i++){poll();int row=order[i];double f=a[row*n+pivot]/d;for(int j=k+1;j<=i;j++){int column=order[j];double v=Math.fma(-f,a[column*n+pivot],a[row*n+column]);a[row*n+column]=a[column*n+row]=v;}}
        }
    }
}
