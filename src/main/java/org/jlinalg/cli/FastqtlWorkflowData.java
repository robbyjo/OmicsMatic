/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.readers.TabixReader;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.FastqtlWorkflowContracts.cap;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Literal prepared-format checks; numerical estimation stays in the pinned upstream program. */
final class FastqtlWorkflowData {
    private FastqtlWorkflowData() { }
    private static final String NUMBER="[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?";
    static final class Lines implements AutoCloseable {
        private final Reader reader;private final long maxBytes,maxLine,maxRows;private long bytes,rows;
        Lines(Path file,Map<String,Object> request)throws IOException {
            var in=new BufferedInputStream(Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS));in.mark(2);int a=in.read(),b=in.read();in.reset();InputStream decoded;
            try{decoded=a==31&&b==139?new GZIPInputStream(in):in;}catch(IOException error){in.close();throw error;}
            reader=new BufferedReader(new InputStreamReader(decoded,StandardCharsets.ISO_8859_1),32768);maxBytes=cap(request,"maximum_decompressed_bytes");maxLine=cap(request,"maximum_line_characters");maxRows=cap(request,"maximum_rows");
        }
        String next()throws IOException {
            var line=new StringBuilder();int c;while((c=reader.read())!=-1&&c!='\n'){require(++bytes<=maxBytes&&line.length()<maxLine,"Text decompression/line capacity exceeded");require(c=='\t'||c>=32&&c<127,"Profile requires literal ASCII without CR/control/Unicode repair");line.append((char)c);}
            if(c==-1&&line.isEmpty())return null;require(++bytes<=maxBytes&&++rows<=maxRows+100000,"Text decompression/row capacity exceeded");return line.toString();
        }
        public void close()throws IOException{reader.close();}
    }
    record Region(String chromosome,long start,long end) { }
    static Region region(Map<String,Object> request,long window){String[] s=request.get("region").toString().split("[:-]");return new Region(s[0],Math.max(1,Long.parseLong(s[1])-window),Long.parseLong(s[2])+window);}
    record Variant(long position) { }
    record Phenotype(long anchor) { }
    record Inputs(Map<String,Variant> variants,Map<String,Phenotype> phenotypes,Map<String,Object> summary) { }
    private static final class Indexed implements AutoCloseable {
        private TabixReader fullReader,selectedReader;private final TabixReader.Iterator full,selected;private final Region region;
        Indexed(Path file,Region r)throws IOException {
            try(var in=new BufferedInputStream(Files.newInputStream(file))){require(BlockCompressedInputStream.isValidFile(in),"VCF/BED must be valid BGZF, with caller-supplied matching TBI indexes");}
            try(var in=new GZIPInputStream(Files.newInputStream(Path.of(file+".tbi")))){require(Arrays.equals(in.readNBytes(4),new byte[]{'T','B','I',1}),"Expected TBI index, not CSI or arbitrary gzip");}
            region=r;
            try {
                // HTSJDK iterators seek their reader's shared BGZF stream. Independent
                // readers are required when full-contig and regional cursors coexist.
                fullReader=new TabixReader(file.toString());selectedReader=new TabixReader(file.toString());
                full=fullReader.query(r.chromosome(),0,Integer.MAX_VALUE);selected=selectedReader.query(r.chromosome(),(int)r.start()-1,(int)r.end());
            }catch(IOException|RuntimeException failure){close();throw failure;}
        }
        boolean check(String line,long start,long end)throws IOException {require(full!=null&&line.equals(full.next()),"TBI full-contig lookup differs from sequential source rows; rebuild the index from this exact BGZF file");boolean hit=start<=region.end()&&end>=region.start();if(hit)require(selected!=null&&line.equals(selected.next()),"TBI regional lookup differs from sequential source rows");return hit;}
        void finish()throws IOException {require((full==null||full.next()==null)&&(selected==null||selected.next()==null),"TBI lookup has unexpected/extra indexed records");}
        public void close(){if(fullReader!=null)fullReader.close();if(selectedReader!=null)selectedReader.close();}
    }
    static Inputs inputs(Path root,Map<String,Object> request)throws IOException {
        var summary=map("assembly",request.get("assembly"),"region",request.get("region"),"anchor","BED zero-based start + 1, regardless of strand; no TSS/peak inference","sample_policy","identical sets, original ID mapping may reorder columns","values_changed_by_Java",false);
        var phenotypes=new LinkedHashMap<String,Phenotype>();var variants=new LinkedHashMap<String,Variant>();List<String> samples=null;String line;long n=0,missingP=0,missingG=0;var allPhenotypes=new HashSet<String>();
        Region pr=region(request,0),gr=region(request,number(object(request.get("options")),"window"));
        try(var lines=new Lines(FastqtlWorkflowContracts.input(root,"phenotypes"),request);var index=new Indexed(FastqtlWorkflowContracts.input(root,"phenotypes"),pr)) {
            line=lines.next();require(line!=null&&line.startsWith("#"),"BED requires one #chrom/start/end/id/sample header");var header=line.split("\t",-1);require(header.length>=8,"BED requires four metadata columns and at least four samples");samples=identities(header,4,request);long previous=-1;
            while((line=lines.next())!=null){var f=line.split("\t",-1);require(f.length==4+samples.size()&&f[0].equals(pr.chromosome()),"BED row width/chromosome mismatch; prepare one chromosome");long start=coordinate(f[1],0),end=coordinate(f[2],1);require(start<end&&start>=previous,"BED must use sorted nonempty zero-based intervals");previous=start;id(f[3]);require(allPhenotypes.add(f[3]),"Duplicate phenotype ID");
                for(int i=4;i<f.length;i++){if(f[i].equals("NA"))missingP++;else numeric(f[i]);}require(++n<=cap(request,"maximum_rows")&&n*samples.size()<=cap(request,"maximum_values"),"Phenotype row/value capacity exceeded");if(index.check(line,start+1,end))phenotypes.put(f[3],new Phenotype(start+1));
            }index.finish();require(!phenotypes.isEmpty(),"No phenotype intervals overlap the requested region");
        }
        summary.put("samples",samples);summary.put("phenotype_rows",n);summary.put("selected_phenotypes",phenotypes.size());summary.put("phenotype_NA_tokens",missingP);n=0;
        var allVariants=new HashSet<String>();
        try(var lines=new Lines(FastqtlWorkflowContracts.input(root,"vcf"),request);var index=new Indexed(FastqtlWorkflowContracts.input(root,"vcf"),gr)) {
            line=lines.next();require(line!=null&&line.startsWith("##fileformat=VCFv4."),"Require literal VCFv4 header");while((line=lines.next())!=null&&line.startsWith("##")){/* Source metadata retained, no guessed assembly or annotations. */}
            require(line!=null&&line.startsWith("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t"),"Wrong VCF column header");var header=line.split("\t",-1);var vSamples=identities(header,9,request);sameSamples(samples,vSamples);long previous=0;
            while((line=lines.next())!=null){var f=line.split("\t",-1);require(f.length==9+samples.size()&&f[0].equals(gr.chromosome()),"VCF row width/chromosome mismatch; prepare one chromosome");long pos=coordinate(f[1],1);require(pos>=previous,"VCF must be position sorted");previous=pos;id(f[2]);require(!f[2].equals(".")&&allVariants.add(f[2]),"Require unique explicit variant IDs");
                if(!f[3].matches("[ACGT]")||!f[4].matches("[ACGT]")||f[3].equals(f[4]))throw new FastqtlWorkflowRuntime.Unavailable("This prepared v1 profile supports biallelic SNVs only; indels, SVs and multiallelic/ploidy transformations require a separate qualified profile");
                require(!f[7].contains("END="),"SNV INFO must not override its indexed span with END");String[] format=f[8].split(":",-1);require(new HashSet<>(Arrays.asList(format)).size()==format.length,"Duplicate VCF FORMAT key");int field=Arrays.asList(format).indexOf("DS");boolean ds=field>=0;if(!ds)field=Arrays.asList(format).indexOf("GT");require(field>=0,"VCF requires scalar DS or diploid GT");
                for(int i=9;i<f.length;i++) {if(Set.of(".","NA","NN").contains(f[i])){missingG++;continue;}String[] values=f[i].split(":",-1);require(values.length==format.length,"VCF FORMAT/value width mismatch");String token=values[field];if(ds){if(token.equals(".")){missingG++;continue;}double value=numeric(token);require(value>=0&&value<=2,"DS must lie in [0,2]");}else{require(token.matches("[01.][/|][01.]"),"Only diploid biallelic GT tokens are supported");if(token.contains("."))missingG++;}}
                require(++n<=cap(request,"maximum_rows")&&n*samples.size()<=cap(request,"maximum_values"),"VCF row/value capacity exceeded");if(index.check(line,pos,pos))variants.put(f[2],new Variant(pos));
            }index.finish();require(n>0,"No VCF records");
        }
        summary.put("variant_rows",n);summary.put("variants_in_expanded_region",variants.size());summary.put("missing_genotype_tokens",missingG);summary.put("dosage_selection","DS takes precedence over GT per record; rounded dosages determine source MAF; source imputes missing values");
        long covariates=0;
        if(object(request.get("inputs")).containsKey("covariates"))try(var lines=new Lines(FastqtlWorkflowContracts.input(root,"covariates"),request)) {
            line=lines.next();require(line!=null,"Empty covariate file");sameSamples(samples,identities(line.split("\t",-1),1,request));var ids=new HashSet<String>();
            while((line=lines.next())!=null){var f=line.split("\t",-1);require(f.length==samples.size()+1,"Covariate width mismatch");id(f[0]);require(ids.add(f[0]),"Duplicate covariate ID");for(int i=1;i<f.length;i++)numeric(f[i]);require(++covariates<=cap(request,"maximum_rows")&&covariates*samples.size()<=cap(request,"maximum_values"),"Covariate capacity exceeded");}
            require(covariates>0,"Empty covariate matrix");
        }
        require(samples.size()>covariates+2,"Need positive nominal residual degrees of freedom");summary.put("numeric_covariates",covariates);summary.put("rank_policy","Original residualizer decides dependence; nominal and permutation DF policies can differ. No rank repair or categorical expansion by Java");summary.put("index_checks","Full contig and requested regional lookups compared row-for-row against sequential decoded BGZF source");
        return new Inputs(variants,phenotypes,summary);
    }
    private static List<String> identities(String[] row,int start,Map<String,Object> request)throws IOException {require(row.length>start&&row.length-start<=cap(request,"maximum_samples"),"Sample capacity/empty header");var result=new ArrayList<String>();var seen=new HashSet<String>();for(int i=start;i<row.length;i++){id(row[i]);require(seen.add(row[i]),"Duplicate sample ID");result.add(row[i]);}return List.copyOf(result);}
    private static void sameSamples(List<String> a,List<String> b)throws IOException{require(new HashSet<>(a).equals(new HashSet<>(b))&&a.size()==b.size(),"Require identical sample ID sets; no silent sample dropping, trimming or repair");}
    private static void id(String s)throws IOException {require(!s.isEmpty()&&s.length()<=1024&&s.matches("[!-~]+"),"IDs must be nonempty literal ASCII without whitespace");}
    private static long coordinate(String s,long min)throws IOException {require(s.matches("[0-9]+"),"Invalid coordinate/count");try{long n=Long.parseLong(s);require(n>=min&&n<=2000000000L,"Coordinate/count outside supported range");return n;}catch(NumberFormatException e){throw new IOException("Invalid coordinate/count",e);}}
    private static double numeric(String token)throws IOException {require(token.matches(NUMBER),"Require finite literal numeric token: "+token);try{double value=Double.parseDouble(token);require(Double.isFinite(value)&&Float.isFinite(Float.parseFloat(token)),"Value exceeds source finite float range");return value;}catch(NumberFormatException e){throw new IOException("Invalid numeric token",e);}}
    static Map<String,Object> outputs(Path root,Map<String,Object> request,Inputs inputs)throws IOException {return outputs(root,request,inputs,false);}
    static Map<String,Object> inspectOutputs(Path root,Map<String,Object> request,Inputs inputs)throws IOException {return outputs(root,request,inputs,true);}
    /** Compare regenerated audit characters directly; inspection never writes to the saved capture. */
    private static final class ComparingWriter extends Writer {
        private final Reader expected;
        ComparingWriter(Path file)throws IOException {expected=Files.newBufferedReader(file,StandardCharsets.UTF_8);}
        @Override public void write(char[] text,int start,int length)throws IOException {for(int i=start;i<start+length;i++)require(expected.read()==text[i],"Saved result-audit.tsv differs from literal source-output semantics");}
        @Override public void flush(){ }
        @Override public void close()throws IOException {try{require(expected.read()==-1,"Extra saved result-audit.tsv content");}finally{expected.close();}}
    }
    private static Map<String,Object> outputs(Path root,Map<String,Object> request,Inputs inputs,boolean inspect)throws IOException {
        boolean perm="PERMUTATION".equals(request.get("operation"));String header=perm?"phenotype_id\tvariants_in_cis\tbeta_shape1\tbeta_shape2\teffective_df\tpval_effective_df\tvariant_id\tdistance\tma_samples\tma_count\tmaf\tref_factor\tpval_nominal\tslope\tslope_se\tpval_empirical\tpval_beta":"phenotype_id\tvariant_id\tdistance\tma_samples\tma_count\tmaf\tpval_nominal\tslope\tslope_se";
        long rows=0,nulls=0,nonfinite=0;var seen=new HashSet<String>();
        try(var lines=new Lines(root.resolve("worker/result.txt"),request);Writer audit=inspect?new ComparingWriter(root.resolve("result-audit.tsv")):Files.newBufferedWriter(root.resolve("result-audit.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            audit.write("row_status\t"+header+"\n");String line;
            while((line=lines.next())!=null){String[] f=line.split("\t",-1);require(f.length==(perm?17:9),"Original result column count differs from named source edition");require(inputs.phenotypes().containsKey(f[0]),"Unknown output phenotype ID");String key=perm?f[0]:f[0]+"\t"+f[1];require(seen.add(key),"Duplicate output key");boolean empty=perm&&coordinate(f[1],0)==0;String status="SOURCE_NUMERIC_RESULT";
                if(empty){for(int i=2;i<17;i++)require(f[i].equals("NA"),"No-cis row has unexpected non-NA fields");nulls++;status="NO_CIS_VARIANTS_NO_RESULT";}
                else {
                    int vi=perm?6:1,di=perm?7:2;Variant variant=inputs.variants().get(f[vi]);require(variant!=null,"Unknown output variant ID");long distance;try{distance=Long.parseLong(f[di]);}catch(NumberFormatException e){throw new IOException("Invalid output distance",e);}require(distance==variant.position()-inputs.phenotypes().get(f[0]).anchor()&&Math.abs(distance)<=number(object(request.get("options")),"window"),"Output coordinate/anchor differs");
                    boolean bad=false;for(int i=1;i<f.length;i++){if(i==vi)continue;String token=f[i];if(token.equals("NA")||token.toLowerCase(Locale.ROOT).matches("[+-]?(?:nan|inf|infinity)")){bad=true;continue;}double v;try{v=Double.parseDouble(token);}catch(NumberFormatException e){throw new IOException("Unexpected output token: "+token,e);}if(!Double.isFinite(v))bad=true;}
                    if(bad){nonfinite++;status="UNREPORTABLE_SOURCE_NONFINITE_PRESERVED";}else {
                        for(int i:perm?new int[]{5,12,15,16}:new int[]{6}){double v=Double.parseDouble(f[i]);require(v>=0&&v<=1,"Original probability outside [0,1]");}
                        if(perm)require(Double.parseDouble(f[2])>0&&Double.parseDouble(f[3])>0&&Double.parseDouble(f[4])>0,"Original beta shape/DF invalid");
                    }
                }
                audit.write(status+"\t"+line+"\n");require(++rows<=cap(request,"maximum_rows"),"Output row capacity exceeded");
            }
        }
        if(perm)require(rows==inputs.phenotypes().size(),"Permutation output must represent every selected phenotype, including no-cis rows");
        var result=map("operation",request.get("operation"),"rows",rows,"no_cis_variant_rows",nulls,"nonfinite_rows",nonfinite,"numeric_rows",rows-nulls-nonfinite,"raw_source_output_changed",false,"FDR_or_qvalues_computed",false,"scientific_admission",nonfinite==0?"SCOPED_SOURCE_OUTPUT_ONLY":"NO_ADMITTED_RESULT_NONFINITE_SOURCE_OUTPUT","whole_paper_reproduced",false);
        if(!inspect)write(root.resolve("scientific-audit.json"),result);return result;
    }
}
