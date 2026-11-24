/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.RoundedGenotypeProbabilities;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ImputeWorkflowContracts.cap;

/** Streaming literal Oxford formats; no inferred people, allele repair or probability normalization. */
final class ImputeWorkflowData {
    private ImputeWorkflowData() { }
    private record Study(long rows,int samples,long missing,long nonunit) { }
    private record Reference(long rows,int haplotypes) { }
    /** A bound applies to every decompressed pass, including plain text; lines cannot grow without limit. */
    private static final class Lines implements AutoCloseable {
        private final BufferedReader reader;private final long maximumBytes,maximumLine,maximumRows;
        private long characters,rows;
        Lines(Path path,Map<String,Object> request)throws IOException {
            require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require regular captured Oxford file: "+path);
            var input=new BufferedInputStream(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS));input.mark(2);int a=input.read(),b=input.read();input.reset();
            InputStream decoded;
            try{decoded=a==31&&b==139?new GZIPInputStream(input):input;}catch(IOException error){input.close();throw error;}
            reader=new BufferedReader(new InputStreamReader(decoded,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),32768);
            maximumBytes=cap(request,"maximum_decompressed_bytes");maximumLine=cap(request,"maximum_line_characters");maximumRows=cap(request,"maximum_rows");
        }
        String[] next()throws IOException {
            var line=new StringBuilder();int c;
            while((c=reader.read())!=-1&&c!='\n') {
                require(++characters<=maximumBytes&&line.length()<maximumLine,"Oxford text decompression/line capacity exceeded");
                require(c=='\r'||c=='\t'||c>=32&&c<127,"Oxford profile requires literal ASCII text without control characters");line.append((char)c);
            }
            if(c==-1&&line.isEmpty())return null;
            require(++characters<=maximumBytes&&++rows<=maximumRows+1,"Oxford text decompression/row capacity exceeded");
            String value=line.toString().trim();return value.isEmpty()?new String[0]:value.split("\\s+");
        }
        @Override public void close()throws IOException{reader.close();}
    }
    private static final class Ledger implements AutoCloseable {
        private final OutputStream stream;private final long limit;private long bytes;
        Ledger(Path path,long limit)throws IOException{this.limit=limit;stream=new BufferedOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW));}
        void row(String value)throws IOException{byte[] raw=(value+"\n").getBytes(StandardCharsets.UTF_8);bytes=Math.addExact(bytes,raw.length);require(bytes<=limit,"Audit ledger byte capacity exceeded");stream.write(raw);}
        @Override public void close()throws IOException{stream.close();}
    }
    static Map<String,Object> inputs(Path root,Map<String,Object> request)throws IOException {
        var audit=map("operation",ImputeWorkflowContracts.operation(request),"assembly",request.get("assembly"),"chromosome",request.get("chromosome"),"ploidy","autosomal-diploid","sample_identity","unlabelled ordered columns; no people or independence inferred","input_values_changed",false);
        audit.put("map",mapFile(ImputeWorkflowContracts.input(root,"map"),request));
        String operation=ImputeWorkflowContracts.operation(request);
        if(!operation.equals("PREPHASE")) {
            Reference reference=reference(ImputeWorkflowContracts.input(root,"reference_legend"),ImputeWorkflowContracts.input(root,"reference_haps"),request);
            audit.put("reference",map("rows",reference.rows(),"haplotypes",reference.haplotypes()));
        }
        if(operation.equals("MERGE_REFERENCE")) {
            Reference reference=reference(ImputeWorkflowContracts.input(root,"second_reference_legend"),ImputeWorkflowContracts.input(root,"second_reference_haps"),request);
            audit.put("second_reference",map("rows",reference.rows(),"haplotypes",reference.haplotypes()));
        }else {
            boolean phased=operation.equals("IMPUTE_PREPHASED");Study study=study(ImputeWorkflowContracts.input(root,phased?"study_haps":"study_genotypes"),request,phased);
            audit.put("study",map("rows",study.rows(),"samples",study.samples(),"all_zero_missing_genotypes",study.missing(),"nonunit_nonmissing_probability_sums",study.nonunit(),"normalization","none by Java; original software semantics apply"));
            if(!operation.equals("PREPHASE"))audit.put("strand",strand(ImputeWorkflowContracts.input(root,"strand"),request));
        }
        return audit;
    }
    private static Map<String,Object> mapFile(Path path,Map<String,Object> request)throws IOException {
        long rows=0,previous=-1;double previousCm=-Double.MAX_VALUE;String[] fields;
        try(var lines=new Lines(path,request)) {
            while((fields=lines.next())!=null) {
                if(rows==0&&fields.length==3&&fields[0].equals("position"))continue;
                require(fields.length==3,"Map requires three columns");long position=position(fields[0]);double rate=finite(fields[1]),cm=finite(fields[2]);
                require(position>previous&&rate>=0&&cm>=previousCm,"Map positions must increase, rates be nonnegative and genetic positions not decrease");previous=position;previousCm=cm;rows++;
            }
        }
        require(rows>=2,"Require at least two map rows");return map("rows",rows,"coverage_policy","No coordinates invented; original map extrapolation is possible outside supplied knots");
    }
    static Reference reference(Path legend,Path haps,Map<String,Object> request)throws IOException {
        long rows=0,previous=-1;int columns=-1;String[] l,h;
        try(var legends=new Lines(legend,request);var haplotypes=new Lines(haps,request)) {
            l=legends.next();require(l!=null&&l.length>=4&&Set.of("rsID","id").contains(l[0])&&l[1].equals("position")&&l[2].equals("a0")&&l[3].equals("a1"),"Legend requires id/rsID position a0 a1 header; optional annotations are retained");
            while((l=legends.next())!=null) {
                require(l.length>=4,"Short legend row");long pos=position(l[1]);require(pos>=previous,"Legend positions must not decrease");previous=pos;alleles(l[2],l[3]);
                h=haplotypes.next();require(h!=null&&h.length>0,"Reference HAPS has fewer rows than legend");
                if(columns<0)columns=h.length;require(h.length==columns&&columns<=2*cap(request,"maximum_samples"),"Reference HAPS width changed/exceeded capacity");
                for(String value:h)binary(value);require(++rows<=cap(request,"maximum_rows"),"Reference row capacity exceeded");
            }
            require(haplotypes.next()==null,"Reference HAPS has more rows than legend");
        }
        require(rows>0&&columns>0,"Empty reference panel");return new Reference(rows,columns);
    }
    private static Study study(Path path,Map<String,Object> request,boolean phased)throws IOException {
        long rows=0,previous=-1,missing=0,nonunit=0;int samples=-1,stride=phased?2:3;String[] f;
        try(var lines=new Lines(path,request)) {
            while((f=lines.next())!=null) {
                require(f.length>5&&(f.length-5)%stride==0,"Study requires five metadata columns and "+stride+" values per sample");
                int n=(f.length-5)/stride;if(samples<0)samples=n;require(n==samples&&n<=cap(request,"maximum_samples"),"Study sample width changed/exceeded capacity");
                long pos=metadata(f);require(pos>=previous,"Study positions must not decrease");previous=pos;
                for(int i=5;i<f.length;i+=stride) {
                    if(phased){binary(f[i]);binary(f[i+1]);}
                    else {double p=prob(f[i]),q=prob(f[i+1]),r=prob(f[i+2]);double sum=p+q+r;if(sum==0)missing++;else if(Math.abs(sum-1)>1e-12)nonunit++;}
                }
                require(++rows<=cap(request,"maximum_rows")&&rows<=cap(request,"maximum_genotypes")/samples,"Study row/genotype capacity exceeded");
            }
        }
        require(rows>0,"Empty study data");return new Study(rows,samples,missing,nonunit);
    }
    private static Map<String,Object> strand(Path path,Map<String,Object> request)throws IOException {
        long rows=0,minus=0;String[] f;
        try(var lines=new Lines(path,request)) {
            while((f=lines.next())!=null) {require(f.length==2&&Set.of("+","-").contains(f[1]),"Strand requires position and literal + or -");position(f[0]);if(f[1].equals("-"))minus++;rows++;}
        }
        require(rows>0,"Empty explicit strand file");return map("rows",rows,"minus_entries",minus,"policy","Original program aligns alleles using this explicit input; inspect its raw strand and mismatch warnings");
    }
    static List<String> scientificFiles(String operation)throws IOException {
        return switch(operation) {
            case "PREPHASE" -> List.of("result","result_info","result_info_by_sample","result_haps","result_haps_confidence");
            case "IMPUTE_GENOTYPES","IMPUTE_PREPHASED" -> List.of("result","result_info","result_info_by_sample");
            case "MERGE_REFERENCE" -> List.of("merged.hap","merged.legend");
            default -> throw new IOException("Unknown operation");
        };
    }
    static Map<String,Object> outputs(Path root,Map<String,Object> request,Map<String,Object> inputAudit)throws IOException {
        String operation=ImputeWorkflowContracts.operation(request);Path worker=root.resolve("worker");var answer=map("operation",operation,"probability_precision",3,"no_normalization",true,"allele1_is_genome_alt",false,"scientific_files",scientificFiles(operation),"bounds_contract","CONDITIONAL_UNIT_MASS_AND_SUBUNIT_ZERO_CENSORING_THEN_NEAREST","unit_mass_established_by_output_tokens",false,"quantizer_evidence","Empirical original v2.3.2 example controls at 3/6/9 decimal places; not a formal proof for every input");
        if(operation.equals("MERGE_REFERENCE")) {
            Reference ref=reference(worker.resolve("merged.legend"),worker.resolve("merged.hap"),request);answer.put("merged_reference",map("rows",ref.rows(),"haplotypes",ref.haplotypes()));return answer;
        }
        int samples=Math.toIntExact(number(object(inputAudit.get("study")),"samples"));boolean bounds="rounding-bounds".equals(object(request.get("options")).get("genotype_audit"));
        long rows=0,missing=0,nonunit=0,infeasible=0,invalid=0,sentinels=0,nonfinite=0;String[] gen,info;
        long maxBytes=cap(request,"maximum_output_bytes");
        try(var gens=new Lines(worker.resolve("result"),request);var infos=new Lines(worker.resolve("result_info"),request);
            var ledger=new Ledger(root.resolve("variant-audit.tsv"),maxBytes);
            var moments=bounds?new Ledger(root.resolve("genotype-bounds.tsv"),maxBytes):null;
            var exceptional=new Ledger(root.resolve("unreportable-genotypes.tsv"),maxBytes)) {
            String[] header=infos.next();List<String> required=List.of("snp_id","rs_id","position","a0","a1","exp_freq_a1","info","certainty","type");
            require(header!=null&&header.length>=9&&Arrays.asList(header).subList(0,9).equals(required),"Unexpected original INFO header");
            ledger.row("variant_ordinal\tsnp_id\trs_id\tposition\ta0\ta1\tmissing_GP\tnonunit_rounded_GP\tinfeasible_GP\tinvalid_GP\tnegative_sentinel_fields\tnonfinite_fields");
            exceptional.row("variant_ordinal\tsample_column_ordinal\traw_p0\traw_p1\traw_p2\tstatus");
            if(moments!=null)moments.row("variant_ordinal\tsample_column_ordinal\traw_p0\traw_p1\traw_p2\tconditional_unit_mass_dosage_lower\tconditional_unit_mass_dosage_upper\tconditional_unit_mass_variance_lower\tconditional_unit_mass_variance_upper\tconditional_unit_mass_maximum_probability_lower\tconditional_unit_mass_maximum_probability_upper");
            while((gen=gens.next())!=null) {
                require(gen.length==5+3*samples,"Original GEN output sample width differs");metadata(gen);info=infos.next();
                require(info!=null&&info.length==header.length&&Arrays.equals(Arrays.copyOf(gen,5),Arrays.copyOf(info,5)),"Original GEN/INFO metadata or row population differs; no ID/allele join repair");
                long m=0,n=0,unfit=0,bad=0,neg=0,nf=0;
                for(int i=5;i<info.length;i++)if(i!=8) {Double value=numeric(info[i]);if(value==null)nf++;else if(value==-1)neg++;}
                integerToken(info[8],0,3,"INFO type");
                for(int sample=0;sample<samples;sample++) {
                    int col=5+3*sample;String p=gen[col],q=gen[col+1],r=gen[col+2],status=null;
                    Double a=numeric(p),b=numeric(q),c=numeric(r);
                    if(a==null||b==null||c==null||a<0||a>1||b<0||b>1||c<0||c>1){bad++;status="UNREPORTABLE_INVALID_GP";}
                    else if(a==0&&b==0&&c==0){m++;status="MISSING_ALL_ZERO_NO_DOSAGE";}
                    else {
                        try {
                            int x=grid(p),y=grid(q),z=grid(r),sum=x+y+z;
                            if(sum!=1000)n++;
                            int lows=lowerGrid(x)+lowerGrid(y)+lowerGrid(z);
                            int highs=upperGrid(x)+upperGrid(y)+upperGrid(z);
                            if(lows>2000||highs<2000){unfit++;status="UNREPORTABLE_CONDITIONAL_BOUNDS_INFEASIBLE_AT_3_DECIMALS";}
                            else if(moments!=null) {
                                var value=RoundedGenotypeProbabilities.bounds(p,q,r,3,RoundedGenotypeProbabilities.Quantization.NEAREST_WITH_SUBUNIT_ZERO_CENSORING);
                                moments.row((rows+1)+"\t"+(sample+1)+"\t"+p+"\t"+q+"\t"+r+"\t"+value.allele1Dosage().lower()+"\t"+value.allele1Dosage().upper()+"\t"+value.posteriorVariance().lower()+"\t"+value.posteriorVariance().upper()+"\t"+value.maximumProbability().lower()+"\t"+value.maximumProbability().upper());
                            }
                        }catch(IllegalArgumentException error){bad++;status="UNREPORTABLE_PRECISION_OR_GP: "+error.getClass().getSimpleName();}
                    }
                    if(status!=null)exceptional.row((rows+1)+"\t"+(sample+1)+"\t"+p+"\t"+q+"\t"+r+"\t"+status);
                }
                require(++rows<=cap(request,"maximum_rows")&&rows<=cap(request,"maximum_genotypes")/samples,"Original output row/genotype capacity exceeded");
                ledger.row(rows+"\t"+String.join("\t",Arrays.copyOf(gen,5))+"\t"+m+"\t"+n+"\t"+unfit+"\t"+bad+"\t"+neg+"\t"+nf);
                missing+=m;nonunit+=n;infeasible+=unfit;invalid+=bad;sentinels+=neg;nonfinite+=nf;
            }
            require(rows>0&&infos.next()==null,"Empty output or extra original INFO rows");
        }
        answer.putAll(map("variants",rows,"samples",samples,"all_zero_missing_GP",missing,"nonunit_GP_sums",nonunit,"infeasible_conditional_unit_mass_GP",infeasible,"invalid_GP",invalid,"negative_one_metric_fields",sentinels,"nonfinite_INFO_fields",nonfinite,"conditional_bounds_emitted",bounds));
        answer.put("sample_QC",sampleQc(worker.resolve("result_info_by_sample"),request,samples));
        if(operation.equals("PREPHASE"))answer.put("phase",phase(worker,request,samples));
        return answer;
    }
    private static Map<String,Object> sampleQc(Path path,Map<String,Object> request,int samples)throws IOException {
        long rows=0,negative=0,nonfinite=0;String[] f;
        try(var lines=new Lines(path,request)) {
            String[] header=lines.next();
            if(header==null||header.length==0){require(lines.next()==null,"Unexpected sample-QC data after blank header");return map("rows",0,"status","NOT_RETURNED_BY_ORIGINAL_OPERATION");}
            require(Arrays.equals(header,new String[]{"concord_type0","r2_type0"}),"Unexpected sample QC header");
            while((f=lines.next())!=null){require(f.length==2,"Malformed sample QC row");for(String token:f){Double n=numeric(token);if(n==null)nonfinite++;else if(n==-1)negative++;}rows++;}
        }
        require(rows==samples,"Sample QC row count differs from unlabelled ordered study columns");return map("rows",rows,"negative_one_sentinels",negative,"nonfinite_fields",nonfinite);
    }
    private static Map<String,Object> phase(Path worker,Map<String,Object> request,int samples)throws IOException {
        long rows=0,stars=0,numbers=0;String[] h,c;
        try(var haps=new Lines(worker.resolve("result_haps"),request);var confidence=new Lines(worker.resolve("result_haps_confidence"),request)) {
            while((h=haps.next())!=null) {
                require(h.length==5+2*samples,"Phased output width changed");metadata(h);c=confidence.next();
                require(c!=null&&c.length==5+samples&&Arrays.equals(Arrays.copyOf(h,5),Arrays.copyOf(c,5)),"Phase confidence and HAPS metadata/population differs");
                for(int i=0;i<samples;i++) {binary(h[5+2*i]);binary(h[6+2*i]);String token=c[5+i];
                    if(token.equals("*")){require(h[5+2*i].equals(h[6+2*i]),"Phase * for a heterozygote");stars++;}
                    else {double value=finite(token);require(value>=.5&&value<=1&&!h[5+2*i].equals(h[6+2*i]),"Unexpected relative heterozygote phase confidence");numbers++;}
                }
                require(++rows<=cap(request,"maximum_rows")&&rows<=cap(request,"maximum_genotypes")/samples,"Phased output capacity exceeded");
            }
            require(confidence.next()==null&&rows>0,"Extra phase confidence rows or empty phase output");
        }
        return map("rows_including_buffer",rows,"homozygote_stars",stars,"relative_heterozygote_confidences",numbers,"semantics","Relative to previous heterozygote; first-heterozygote convention is not independent accuracy evidence");
    }
    private static int lowerGrid(int value){return value==0?0:Math.max(2,2*value-1);}
    private static int upperGrid(int value){return value==0?2:Math.min(2000,2*value+1);}
    private static int grid(String token) {try{return new BigDecimal(token).movePointRight(3).intValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException("Not a three-decimal probability",e);}}
    private static long metadata(String[] f)throws IOException {require(f.length>=5,"Short Oxford metadata");alleles(f[3],f[4]);return position(f[2]);}
    private static void alleles(String a,String b)throws IOException {require(!a.isEmpty()&&!b.isEmpty()&&!a.equals(b),"Alleles must be literal distinct nonempty tokens");}
    private static void binary(String token)throws IOException {require(token.equals("0")||token.equals("1"),"HAPS requires literal 0/1, without missing or inferred phase");}
    private static long position(String token)throws IOException{return integerToken(token,1,Integer.MAX_VALUE,"position");}
    private static long integerToken(String token,long low,long high,String name)throws IOException {try{require(token.matches("[0-9]+"),"Invalid "+name);long n=Long.parseLong(token);require(n>=low&&n<=high,"Out-of-range "+name);return n;}catch(NumberFormatException e){throw new IOException("Invalid "+name,e);}}
    private static Double numeric(String token)throws IOException {
        require(token.length()<=128,"Numeric token capacity exceeded");
        if(Set.of("NaN","nan","Inf","inf","+Inf","+inf","-Inf","-inf","Infinity","-Infinity").contains(token))return null;
        require(token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Malformed numeric token");
        try{double n=Double.parseDouble(token);return Double.isFinite(n)?n:null;}catch(NumberFormatException e){throw new IOException("Malformed numeric token",e);}
    }
    private static double finite(String token)throws IOException{Double n=numeric(token);require(n!=null,"Nonfinite required input value");return n;}
    private static double prob(String token)throws IOException{double n=finite(token);require(n>=0&&n<=1,"Input probability outside [0,1]");return n;}
}
