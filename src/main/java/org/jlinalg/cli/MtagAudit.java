/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.MtagJson.*;

/** Complete common-SNP source-result admission and replay; original fitting/QC remains upstream. */
final class MtagAudit {
    private MtagAudit() { }
    private record Raw(long row,String id,boolean missing,String chr,String bp,String a1,String a2,double z,double n,double freq,double p,double info) { }
    private record TraitData(List<Raw> rows,Map<String,Raw> first) { }
    private static final class Table implements AutoCloseable {
        final MtagInputs.Lines lines;final String[] header;final Map<String,Integer> index=new HashMap<>();
        Table(Path path,long bytes,String... expected)throws IOException {
            lines=new MtagInputs.Lines(path,bytes);String first=lines.next();need(first!=null,"Missing complete original table: "+path);header=first.split("\t",-1);
            for(int i=0;i<header.length;i++)need(index.put(header[i],i)==null,"Duplicate original table column: "+path);
            if(expected.length>0)need(List.of(header).equals(List.of(expected)),"Original table schema differs: "+path);
        }
        String[] next()throws IOException {String s=lines.next();if(s==null)return null;String[] f=s.split("\t",-1);need(f.length==header.length,"Incomplete original table row");return f;}
        String get(String[] row,String name)throws IOException {need(index.containsKey(name),"Original field missing: "+name);return row[index.get(name)];}
        @Override public void close()throws IOException {lines.close();}
    }
    private static double hex(String s)throws IOException {
        return switch(s){case "nan","NaN"->Double.NaN;case "inf","Infinity"->Double.POSITIVE_INFINITY;case "-inf","-Infinity"->Double.NEGATIVE_INFINITY;default->{try{yield Double.parseDouble(s);}catch(NumberFormatException e){throw new IOException("Invalid original hexadecimal numeric field: "+s,e);}}};
    }
    private static double required(String s,String label)throws IOException {try{double v=Double.parseDouble(s);need(Double.isFinite(v),"Unassessable required original scientific field: "+label);return v;}catch(NumberFormatException e){throw new IOException("Unassessable required original scientific field: "+label+"="+s,e);}}
    private static void exact(double a,double b,String label)throws IOException {need(Double.doubleToLongBits(a)==Double.doubleToLongBits(b),"Original effective input differs from complete parsed input/declared source allele operation: "+label);}
    private static void near(double a,double b,String label)throws IOException {need(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=5e-12*Math.max(1,Math.abs(b)),"Original MTAG algebra mismatch: "+label+"; original="+a+",native="+b);}
    private static void effectNear(double a,double b,double uncertaintyUnit,String label)throws IOException {
        double scale=Math.max(Math.max(Math.abs(a),Math.abs(b)),Math.abs(uncertaintyUnit));
        need(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=5e-11*scale+16*Math.ulp(scale),"Original MTAG effect/SE mismatch in its declared units: "+label+"; original="+a+",native="+b);
    }
    private static void coordinate(String a,String b,String label)throws IOException {need(required(a,label)==required(b,label),"Original cross-trait genomic coordinates disagree: "+label);}
    private static Writer writer(Path directory,Path bundle,String name,long maximumBytes)throws IOException {
        if(directory!=null)return Files.newBufferedWriter(directory.resolve(name),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        // Read-only replay checks every diagnostic row as well as aggregate
        // scientific fields. Rehashed or incomplete ledgers are not trusted.
        MtagInputs.Lines saved=new MtagInputs.Lines(bundle.resolve("native-audit").resolve(name),maximumBytes);
        return new Writer(){
            final StringBuilder line=new StringBuilder();long row=0;boolean closed=false;
            @Override public void write(char[] chars,int offset,int length)throws IOException {
                for(int i=offset;i<offset+length;i++)if(chars[i]=='\n'){
                    String actual=saved.next();need(actual!=null,"Saved native diagnostic is incomplete: "+name);
                    compareDiagnosticLine(name,++row,actual,line.toString());line.setLength(0);
                }else{line.append(chars[i]);need(line.length()<=1048576,"Native diagnostic line capacity exceeded");}
            }
            @Override public void flush(){ }
            @Override public void close()throws IOException {
                if(closed)return;closed=true;
                try{need(line.isEmpty()&&saved.next()==null,"Saved native diagnostic has an incomplete or extra row: "+name);}finally{saved.close();}
            }
        };
    }
    private static void compareDiagnosticLine(String name,long row,String actual,String recomputed)throws IOException {
        if(!name.equals("association-audit.tsv")||row==1){need(actual.equals(recomputed),"Saved native diagnostic differs: "+name+":"+row);return;}
        String[] a=actual.split("\t",-1),b=recomputed.split("\t",-1);need(a.length==10&&b.length==10,"Saved association diagnostic schema differs");
        for(int i:new int[]{0,1,2,6,7,9})need(a[i].equals(b[i]),"Saved association diagnostic identity/original value differs at row"+row);
        double unit=required(b[4],"native SE");effectNear(required(a[3],"saved native beta"),required(b[3],"native beta"),unit,"saved diagnostic beta");
        effectNear(required(a[4],"saved native SE"),unit,unit,"saved diagnostic SE");near(required(a[5],"saved native Z"),required(b[5],"native Z"),"saved diagnostic Z");
        double x=required(a[8],"saved native P"),y=required(b[8],"native P");need(y==0?x==0:x>0&&Math.abs(x-y)/y<=2e-12,"Saved diagnostic source-bound native P differs");
    }
    private static boolean ambiguous(String a,String b){return (a.equals("A")&&b.equals("T"))||(a.equals("T")&&b.equals("A"))||(a.equals("C")&&b.equals("G"))||(a.equals("G")&&b.equals("C"));}
    private static List<List<Double>> matrixJson(double[][] a){var rows=new ArrayList<List<Double>>();for(double[] row:a){var values=new ArrayList<Double>();for(double value:row)values.add(value);rows.add(values);}return rows;}
    static double[][] matrix(Path path,int traits,long bytes)throws IOException {
        double[][] result=new double[traits][traits];int i=0;
        try(MtagInputs.Lines lines=new MtagInputs.Lines(path,bytes)){for(String s;(s=lines.next())!=null;){String[] f=s.trim().split("\\s+");need(i<traits&&f.length==traits,"Complete source covariance dimensions differ: "+path);for(int j=0;j<traits;j++)result[i][j]=required(f[j],path+":"+i+":"+j);i++;}}
        need(i==traits,"Incomplete original covariance matrix: "+path);return result;
    }
    static Map<String,Object> inspect(MtagRequest.Request r,Path bundle,Path diagnosticDirectory)throws IOException {
        int traits=r.traits().size();Path captured=bundle.resolve("captured"),original=bundle.resolve("original");
        requireOriginalCompleted(original,r.limits().outputBytes());
        Map<?,?> record=object(MtagJson.read(captured.resolve("capture.json"),1048576));
        need(text(record.get("status")).equals("CAPTURED_ORIGINAL_PARSING_AND_QC_ONLY_NO_FIT")&&integer(record.get("traits"),2,8)==traits
            &&!bool(record.get("source_changed"))&&!bool(record.get("analysis_or_covariance_refitted")),"Original input-sidecar contract differs");
        need(text(record.get("effective_file")).equals("effective-inputs.tsv")&&text(record.get("original_frame")).equals("original-merged-frame.tsv"),"Original capture table paths differ; only fixed bundle-local tables are admitted");
        long expectedRows=integer(record.get("variants"),1,r.limits().rows());
        List<?> rawFiles=list(record.get("raw_files")),rawCounts=list(record.get("raw_rows"));need(rawFiles.size()==traits&&rawCounts.size()==traits,"Complete source raw-frame census differs");
        var data=new ArrayList<TraitData>();long inputRows=0,parserChanges=0,flips=0,ambiguousCount=0,underflowCount=0;
        try(Writer differences=writer(diagnosticDirectory,bundle,"input-parser-differences.tsv",r.limits().outputBytes())) {
            differences.write("trait\tinput_row\tSNP\tfield\traw_decimal\tnative_decimal_parse_hex\toriginal_pandas_hex\n");
            for(int t=0;t<traits;t++){
                String name=String.format(java.util.Locale.ROOT,"raw-trait-%03d.tsv",t+1);need(text(rawFiles.get(t)).equals(name),"Unexpected source raw-frame identity/path");
                var rows=new ArrayList<Raw>();var first=new HashMap<String,Raw>();
                try(Table source=new Table(captured.resolve(name),r.limits().outputBytes(),"input_row","snp_missing","SNP","CHR","BP","A1","A2","Z_hex","N_hex","FRQ_hex","P_hex","INFO_hex");
                    MtagInputs.Lines raw=new MtagInputs.Lines(r.traits().get(t).input(),r.limits().inputBytes())) {
                    Map<String,Integer> columns=MtagInputs.header(raw.next());
                    for(String[] row;(row=source.next())!=null;){String line=raw.next();need(line!=null&&rows.size()<r.limits().rows(),"Original source raw-frame count exceeds complete input");String[] input=MtagInputs.fields(line,columns.size());long index=rows.size()+1L;
                        need(source.get(row,"input_row").equals(Long.toString(index)),"Original raw input order differs");String ident=source.get(row,"SNP"),rawId=input[columns.get(r.columns().get("snp"))];boolean missing=source.get(row,"snp_missing").equals("true");
                        need(missing?(ident.isEmpty()&&MtagInputs.missing(rawId)):ident.equals(rawId),"Original pandas changed SNP identity; no relabelling or ID repair");
                        String[] kinds={"z","n","freq","p"};String[] fields={"Z_hex","N_hex","FRQ_hex","P_hex"};double[] v=new double[5];v[4]=Double.NaN;
                        for(int k=0;k<4;k++){
                            String token=input[columns.get(r.columns().get(kinds[k]))];double before=MtagInputs.numeric(token,true),after=hex(source.get(row,fields[k]));v[k]=after;
                            need(Double.isNaN(before)==Double.isNaN(after),"Original numeric missingness changed unexpectedly: "+kinds[k]);
                            if(Double.doubleToLongBits(before)!=Double.doubleToLongBits(after)){
                                differences.write((t+1)+"\t"+index+"\t"+rawId+"\t"+kinds[k]+"\t"+token+"\t"+Double.toHexString(before)+"\t"+source.get(row,fields[k])+"\n");parserChanges++;
                            }
                        }
                        if(r.filters().get("info_min")!=null){String token=input[columns.get("info")];double before=MtagInputs.numeric(token,true);v[4]=hex(source.get(row,"INFO_hex"));need(Double.isNaN(before)==Double.isNaN(v[4]),"Original INFO missingness changed unexpectedly");
                            if(Double.doubleToLongBits(before)!=Double.doubleToLongBits(v[4])){differences.write((t+1)+"\t"+index+"\t"+rawId+"\tinfo\t"+token+"\t"+Double.toHexString(before)+"\t"+source.get(row,"INFO_hex")+"\n");parserChanges++;}}
                        Raw item=new Raw(index,rawId,missing,source.get(row,"CHR"),source.get(row,"BP"),source.get(row,"A1"),source.get(row,"A2"),v[0],v[1],v[2],v[3],v[4]);
                        for(String key:List.of("chr","bp")){String token=input[columns.get(r.columns().get(key))],parsed=key.equals("chr")?item.chr():item.bp();if(MtagInputs.missing(token))need(parsed.isEmpty(),"Original missing coordinate changed");else coordinate(token,parsed,key);}
                        for(String key:List.of("a1","a2")){String token=input[columns.get(r.columns().get(key))],parsed=key.equals("a1")?item.a1():item.a2();need(MtagInputs.missing(token)?(parsed.isEmpty()||parsed.equals(token)):parsed.equals(token),"Original raw allele token changed before source harmonization");}
                        rows.add(item);if(!missing)first.putIfAbsent(ident,item);
                    }
                    need(raw.next()==null&&rows.size()==integer(rawCounts.get(t),1,r.limits().rows()),"Original source raw-frame count differs from complete original input");
                }
                inputRows+=rows.size();data.add(new TraitData(rows,first));
            }
        }
        double[][] sigma=matrix(original.resolve("mtag_sigma_hat.txt"),traits,r.limits().outputBytes());
        String constraint=text(r.covariance().get("constraint"));double[][] omega;
        if(constraint.equals("equal_h2")){need(!Files.exists(original.resolve("mtag_omega_hat.txt")),"Unexpected Omega export in original equal-h2 branch");omega=new double[traits][traits];for(double[] row:omega)java.util.Arrays.fill(row,1);}
        else omega=matrix(original.resolve("mtag_omega_hat.txt"),traits,r.limits().outputBytes());
        if(text(r.covariance().get("omega")).equals("provided")){
            double[][] given=matrix(MtagRequest.path(r.source().getParent(),r.covariance().get("omega_file")),traits,r.limits().inputBytes());
            for(int i=0;i<traits;i++)for(int j=0;j<traits;j++)exact(given[i][j],omega[i][j],"provided Omega:"+i+":"+j);
        }
        MtagAlgebra.Covariance covariance=new MtagAlgebra.Covariance(omega,sigma,constraint);
        var outputs=new ArrayList<Table>();Table meta=null;long rows=0;var retained=new HashSet<String>();long[] changedOrder={0};long previousRow=0;
        double[][] gmm=new double[traits][traits];long gmmRows=0;
        boolean computeGmm=text(r.covariance().get("omega")).equals("gmm")&&!constraint.equals("equal_h2");
        try(Table effective=new Table(captured.resolve(text(record.get("effective_file"))),r.limits().outputBytes());
            Writer numerical=writer(diagnosticDirectory,bundle,"association-audit.tsv",r.limits().outputBytes())) {
            numerical.write("output_row\tSNP\ttrait\tnative_standardized_beta_hex\tnative_standardized_se_hex\tnative_z_hex\toriginal_z_hex\toriginal_p_hex\tnative_scipy_p_hex\toriginal_p_underflowed\n");
            if(constraint.equals("equal_h2"))meta=new Table(original.resolve("mtag_mtag_meta.txt"),r.limits().outputBytes(),"SNP","CHR","BP","A1","A2","meta_freq","mtag_beta","mtag_se","mtag_z","mtag_pval");
            else for(int t=0;t<traits;t++)outputs.add(new Table(original.resolve("mtag_trait_"+(t+1)+".txt"),r.limits().outputBytes(),"SNP","CHR","BP","A1","A2","Z","N","FRQ","mtag_beta","mtag_se","mtag_z","mtag_pval"));
            for(String[] row;(row=effective.next())!=null;){need(++rows<=r.limits().rows(),"Complete MTAG result exceeds declared row capacity");String id=effective.get(row,"SNP");need(retained.add(id),"Original result contains duplicate SNP identities; no deduplication");
                String a1=effective.get(row,"A1"),a2=effective.get(row,"A2");need(List.of("A","C","G","T").contains(a1)&&List.of("A","C","G","T").contains(a2)&&!a1.equals(a2),"Original retained variant is outside the biallelic SNP profile");
                boolean amb=ambiguous(a1,a2);need(effective.get(row,"strand_ambiguous").equals(Boolean.toString(amb)),"Original strand-ambiguity flag differs");if(amb)ambiguousCount++;
                need(!amb||bool(r.filters().get("include_ambiguous")),"Original result violates declared strand-ambiguity exclusion");
                double[] z=new double[traits],n=new double[traits],freq=new double[traits];
                for(int t=0;t<traits;t++){
                    Raw raw=data.get(t).first().get(id);need(raw!=null,"Original output SNP absent from complete raw source inputs");
                    coordinate(effective.get(row,"CHR"),raw.chr(),id+":CHR");coordinate(effective.get(row,"BP"),raw.bp(),id+":BP");
                    coordinate(effective.get(row,"CHR"+t),raw.chr(),id+":source CHR");coordinate(effective.get(row,"BP"+t),raw.bp(),id+":source BP");
                    String beforeA1=raw.a1().toUpperCase(java.util.Locale.ROOT),beforeA2=raw.a2().toUpperCase(java.util.Locale.ROOT);
                    boolean swap=beforeA1.equals(a2)&&beforeA2.equals(a1);need(swap||beforeA1.equals(a1)&&beforeA2.equals(a2),"Original retained variant requires an unsupported allele-complement/substitution operation");
                    if(swap)flips++;
                    need(effective.get(row,"A1_"+t).equals(a1)&&effective.get(row,"A2_"+t).equals(a2),"Original effective allele order differs across traits");
                    z[t]=hex(effective.get(row,"Z"+t+"_hex"));n[t]=hex(effective.get(row,"N"+t+"_hex"));freq[t]=hex(effective.get(row,"FRQ"+t+"_hex"));
                    exact(z[t],swap?-raw.z():raw.z(),id+":Z");exact(n[t],raw.n(),id+":N");exact(freq[t],swap?1-raw.freq():raw.freq(),id+":FRQ");
                    need(Double.isFinite(z[t])&&Double.isFinite(n[t])&&n[t]>0&&n[t]<=1e9&&freq[t]>0&&freq[t]<1,"Unassessable original effective Z/N/frequency");
                    double maf=Math.min(raw.freq(),1-raw.freq()),limit=number(list(r.filters().get("maf_min")).get(t));need(maf>limit,"Original retained variant violates the declared strict MAF cutoff; original MTAG1.0.8 does not recognize default freq during internal munging. No implicit prefilter, header rename or result repair");
                    need(raw.p()>0&&raw.p()<=1,"Original retained row violates source P-value QC");
                    if(r.filters().get("n_min")!=null)need(raw.n()>=number(list(r.filters().get("n_min")).get(t)),"Original row violates declared minimum N");
                    if(r.filters().get("info_min")!=null)need(Double.isFinite(raw.info())&&raw.info()>=number(list(r.filters().get("info_min")).get(t)),"Original row violates declared INFO cutoff");
                    if(!list(r.filters().get("chromosomes")).isEmpty())need(list(r.filters().get("chromosomes")).stream().anyMatch(value->((Number)value).doubleValue()==Double.parseDouble(raw.chr())),"Original result violates declared chromosome selection");
                    if(t==0){if(raw.row()<previousRow)changedOrder[0]++;previousRow=raw.row();}
                }
                if(!amb){gmmRows++;if(computeGmm)for(int i=0;i<traits;i++)for(int j=0;j<traits;j++){double term=(z[i]*z[j]-sigma[i][j])/Math.sqrt(n[i]*n[j]);need(Double.isFinite(term),"Unassessable native GMM term");gmm[i][j]+=term;}}
                MtagAlgebra.Effect predicted=covariance.estimate(z,n);
                String[] metaRow=meta==null?null:meta.next();if(meta!=null)need(metaRow!=null,"Original meta output is incomplete");
                for(int t=0;t<traits;t++){
                    Table output=meta==null?outputs.get(t):meta;String[] actual=meta==null?output.next():metaRow;need(actual!=null&&output.get(actual,"SNP").equals(id),"Complete source trait output ordering/identity differs");
                    for(String name:List.of("CHR","BP"))coordinate(output.get(actual,name),effective.get(row,name),id+":"+name);
                    need(output.get(actual,"A1").equals(a1)&&output.get(actual,"A2").equals(a2),"Original output allele identity differs");
                    double f=freq[t];if(meta!=null){double sum=0;for(double value:freq)sum+=value;double expectedMean=sum/traits;f=required(output.get(actual,"meta_freq"),"meta frequency");need(f>0&&f<1&&Math.abs(f-expectedMean)<=traits*Math.ulp(expectedMean),"Original meta mean frequency differs beyond its summation-roundoff bound");}
                    else {exact(z[t],required(output.get(actual,"Z"),"Z"),"output Z");exact(n[t],required(output.get(actual,"N"),"N"),"output N");exact(f,required(output.get(actual,"FRQ"),"FRQ"),"output frequency");}
                    double scale=r.standardized()?1:Math.sqrt(2*f*(1-f));
                    double beta=required(output.get(actual,"mtag_beta"),"mtag_beta"),se=required(output.get(actual,"mtag_se"),"mtag_se");need(se>0,"Original standard error is not positive");
                    effectNear(beta,predicted.beta()[t]/scale,se,id+":beta");effectNear(se,predicted.se()[t]/scale,0,id+":SE");
                    double sourceZ=required(output.get(actual,"mtag_z"),"mtag_z"),sourceP=required(output.get(actual,"mtag_pval"),"mtag_pval");
                    near(sourceZ,predicted.z()[t],id+":Z algebra");near(sourceZ,beta/se,id+":written beta/SE ratio");double nativeP=MtagAlgebra.p(sourceZ);
                    need(sourceP>=0&&sourceP<=1&&(sourceP==0?nativeP==0:Math.abs(nativeP-sourceP)/sourceP<=2e-12),"Original P differs from source-bound SciPy1.2.1 tail on the original Z; no generic P clipping");
                    if(sourceP==0)underflowCount++;
                    numerical.write(rows+"\t"+id+"\t"+(t+1)+"\t"+Double.toHexString(predicted.beta()[t])+"\t"+Double.toHexString(predicted.se()[t])+"\t"+Double.toHexString(predicted.z()[t])+"\t"+Double.toHexString(sourceZ)+"\t"+Double.toHexString(sourceP)+"\t"+Double.toHexString(nativeP)+"\t"+(sourceP==0)+"\n");
                }
            }
            need(rows==expectedRows,"Complete original prepared/output row count differs");
            if(meta!=null)need(meta.next()==null,"Unexpected extra original meta result rows");for(Table output:outputs)need(output.next()==null,"Unexpected extra original trait result rows");
        } finally {if(meta!=null)meta.close();for(Table output:outputs)output.close();}
        try(Writer dispositions=writer(diagnosticDirectory,bundle,"variant-dispositions.tsv",r.limits().outputBytes())) {
            dispositions.write("trait\tinput_row\tSNP\tretained_by_original_pipeline\tfirst_original_occurrence\tdisposition\n");
            for(int t=0;t<traits;t++)for(Raw raw:data.get(t).rows()){
                boolean first=!raw.missing()&&data.get(t).first().get(raw.id())==raw,kept=first&&retained.contains(raw.id());
                dispositions.write((t+1)+"\t"+raw.row()+"\t"+raw.id()+"\t"+kept+"\t"+first+"\t"+(raw.missing()?"source_missing_id":!first?"source_duplicate_nonfirst":kept?"retained":"excluded_by_original_pipeline_see_source_logs")+"\n");
            }
        }
        MtagRuntime.pin(original.resolve("mtag.log"),Math.min(r.limits().outputBytes(),16777216L));
        String log=Files.readString(original.resolve("mtag.log"),StandardCharsets.UTF_8);
        if(text(r.covariance().get("omega")).equals("numerical"))need(log.contains("Successful termination? Yes")&&!log.contains("Successful termination? No"),"Original numerical-Omega optimization did not establish successful convergence; no result admitted");
        need(!log.contains("max number of iterations reached in adjustment procedure"),"Original covariance adjustment reached its iteration limit; retain its warning and unadmitted outputs without repair");
        Map<String,Object> fdr=auditFdr(r,original,log,traits);
        var warnings=new ArrayList<String>();if(parserChanges>0)warnings.add("Original pandas changed "+parserChanges+" numeric decimal interpretations; every difference is retained without repair.");
        if(r.columns().get("freq").equals("freq"))warnings.add("Original MTAG1.0.8 does not recognize the default freq header during internal MAF munging. The native audit requires every retained variant to satisfy each declared strict MAF cutoff, but does not reconstruct complete source QC. Inspect original column mappings and author settings; no header renaming or prefilter is performed automatically.");
        if(flips>0)warnings.add("Original MTAG flipped "+flips+" trait-variant allele pairs; exact Z signs and frequencies are audited. Strand complements were not inferred.");
        if(underflowCount>0)warnings.add("Original SciPy returned numerical P=0 for "+underflowCount+" trait rows. These original underflows are preserved; zero is not a mathematically zero tail probability.");
        if(r.force())warnings.add("Caller explicitly selected original force_low_signal; this can admit unstable covariance estimation. Inspect all original low-chi-squared warnings.");
        if(log.contains("matrix is not positive definite, performing adjustment"))warnings.add("Original MTAG applied its covariance adjustment procedure. Inspect the unchanged input and estimated covariance matrices and full source log; the project performed no covariance repair.");
        if(log.contains("Mean chi^2 of SNPs used to estimate Omega is low")||log.contains("mean chi2 statistic")&&log.contains("less 1.02"))warnings.add("Original MTAG reported low GWAS signal; inspect its unchanged warnings and covariance output before interpretation.");
        return map("status","PASS_COMPLETE_ORIGINAL_MTAG_RESULTS_AND_STATED_NATIVE_CONTRACTS","variants",rows,"traits",(long)traits,"raw_input_rows",inputRows,
            "trait_rows_checked",rows*traits,"parsed_numeric_difference_cells",parserChanges,"original_trait_allele_flips",flips,"strand_ambiguous_variants",ambiguousCount,
            "original_p_underflow_rows",underflowCount,"original_order_inversions_from_trait1",changedOrder[0],"omega",matrixJson(omega),"sigma",matrixJson(sigma),
            "genetic_constraint",constraint,"native_covariance_assessment","exact_binary64_PSD_except_explicit_source_perfect_constraint", 
            "source_gmm_eligible_variants",gmmRows,"native_unadjusted_gmm",!computeGmm||gmmRows==0?null:matrixJson(mean(gmm,gmmRows)),
            "source_preparation_reexecuted_without_refit",true,"full_source_qc_independently_reimplemented",false,
            "original_effective_values_repaired",false,"ldsc_independently_refitted",false,"fdr",fdr,"warnings",warnings);
    }
    private static double[][] mean(double[][] sums,long n){double[][] result=new double[sums.length][sums.length];for(int i=0;i<sums.length;i++)for(int j=0;j<sums.length;j++)result[i][j]=sums[i][j]/n;return result;}
    static void requireOriginalCompleted(Path original,long maximumBytes)throws IOException {
        boolean complete=false,error=false;
        try(MtagInputs.Lines lines=new MtagInputs.Lines(original.resolve("mtag.log"),maximumBytes)){
            for(String line;(line=lines.next())!=null;){if(line.contains("MTAG complete. Time elapsed:"))complete=true;if(line.contains("Analysis terminated from error at"))error=true;}
        }
        need(complete&&!error,"Original MTAG did not complete its requested analysis; its Python2 exception handler can return exit0 after a scientific error. Inspect unchanged original logs; no partial result admitted");
    }
    private static Map<String,Object> auditFdr(MtagRequest.Request r,Path original,String log,int traits)throws IOException {
        String mode=text(r.fdr().get("mode"));if(mode.equals("none")){
            need(!Files.exists(original.resolve("mtag_fdr_mat.txt"))&&!Files.exists(original.resolve("mtag_prob_grid.txt")),"Unexpected maxFDR output when not requested");return map("mode","none","calculated",false);
        }
        long gridRows=0;try(MtagInputs.Lines lines=new MtagInputs.Lines(original.resolve("mtag_prob_grid.txt"),r.limits().outputBytes())){
            for(String line;(line=lines.next())!=null;){need(++gridRows<=r.limits().gridPoints(),"Original maxFDR output grid exceeds declared capacity");String[] f=line.trim().split("\\s+");need(f.length==(1<<traits),"Original maxFDR state count differs");double sum=0;
                for(String token:f){double value=required(token,"maxFDR prior");need(value>=0&&value<=1,"Original maxFDR prior out of range");sum+=value;}need(Math.abs(sum-1)<=1e-12,"Original maxFDR prior sum differs; no renormalization");}
        }
        need(gridRows>0,"Original maxFDR produced an empty search grid");double[] maxima=new double[traits];long count=0;
        try(MtagInputs.Lines lines=new MtagInputs.Lines(original.resolve("mtag_fdr_mat.txt"),r.limits().outputBytes())){
            for(String line;(line=lines.next())!=null;){String[] f=line.trim().split("\\s+");need(++count<=gridRows&&f.length==traits,"Original maxFDR result/grid dimensions differ");for(int t=0;t<traits;t++){double v=required(f[t],"maxFDR result");need(v>=0&&v<=1,"Unassessable original maxFDR value; no filtering or clipping");maxima[t]=Math.max(maxima[t],v);}}
        }
        need(count==gridRows,"Incomplete original maxFDR matrix");
        var expression=java.util.regex.Pattern.compile("(?:Max FDR|FDR) of Trait (\\d+): ([0-9.eE+\\-]+) at probs").matcher(log);var observed=new HashMap<Integer,Double>();
        while(expression.find()){int t=Integer.parseInt(expression.group(1));need(observed.put(t,Double.parseDouble(expression.group(2)))==null,"Repeated original maxFDR maximum");}
        need(observed.size()==traits,"Original maxFDR maxima are missing");var values=new ArrayList<Double>();for(int t=0;t<traits;t++){need(observed.containsKey(t+1),"Original maxFDR trait maximum missing");near(observed.get(t+1),maxima[t],"maxFDR logged maximum");values.add(maxima[t]);}
        return map("mode",mode,"calculated",true,"grid_rows",gridRows,"original_maxima",values,"original_mean_N_approximation",true,"formula_independently_reimplemented",false);
    }
}
