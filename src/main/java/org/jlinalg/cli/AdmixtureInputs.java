/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.PlinkBedDataset;

/** Explicit ADMIXTURE recipes and immutable, bounded genotype/reference snapshots. */
final class AdmixtureInputs {
    private AdmixtureInputs() { }
    record Limits(int samples,int markers,long cells,long parameters,long bytes,long retained,int line,long output) { }
    record Stop(String kind,double value) {
        String argument(){return kind.equals("iterations")?Long.toString((long)value):java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    }
    record Spec(Path recipe,String recipeHash,Path prefix,String operation,String version,String assembly,
                int k,int seed,int threads,int ploidy,String coding,String method,String acceleration,
                Stop major,Stop minor,int cv,int boot,int block,double lambda,double epsilon,
                boolean allowUnbounded,Path populations,Path model,String projectionPloidy,Limits limits,Map<?,?> source) { }
    record Prepared(Spec spec,Path prefix,List<PlinkBedDataset.Sample> samples,List<PlinkBedDataset.Variant> markers,
                    List<String> labels,Map<String,String> aliases,Map<Path,String> immutable,Map<String,String> staged,
                    AdmixtureOutputs.Model model) {
        Prepared {samples=List.copyOf(samples);markers=List.copyOf(markers);labels=List.copyOf(labels);aliases=Map.copyOf(aliases);immutable=Map.copyOf(immutable);staged=Map.copyOf(staged);}
        void verify()throws IOException {
            for(var e:immutable.entrySet())if(!FollowupSupport.hash(e.getKey()).equals(e.getValue()))throw new IOException("ADMIXTURE input/model changed: "+e.getKey());
            Path parent=prefix.getParent();for(var e:staged.entrySet())if(!FollowupSupport.hash(parent.resolve(e.getKey())).equals(e.getValue()))throw new IOException("ADMIXTURE staged input changed: "+e.getKey());
            if(model!=null)model.verify();
        }
        PlinkBedDataset open()throws IOException{return dataset(prefix,new LinkedHashSet<>(markers.stream().map(PlinkBedDataset.Variant::id).toList()),spec.limits);}
    }
    static Spec read(Path recipe)throws IOException {
        recipe=recipe.toAbsolutePath().normalize();byte[] raw=raw(recipe,2*1048576);String digest=digest(raw);Map<?,?> m=decode(raw);
        keys(m,Set.of("schema_version","operation","version","plink_prefix","assembly","input_review","k","seed","threads","model_ploidy","genotype_coding","method","acceleration","major_stop","minor_stop","cv_folds","bootstrap_replicates","bootstrap_block_markers","lambda","epsilon","printed_precision_policy","populations","model","projection_ploidy_policy","limits"));
        if(whole(m,"schema_version",1,1)!=1)throw new IOException("Require ADMIXTURE recipe schema1");
        String op=choice(m,"operation",Set.of("fit","supervised","project")),version=text(m,"version");
        if(version.length()>32)throw new IOException("Original version label is too long");
        String assembly=text(m,"assembly");Map<?,?> review=map(m.get("input_review"));keys(review,Set.of("organism","marker_scope","allele_harmonization","ld_pruning","sample_eligibility","reference_eligibility","ploidy_coding"));
        for(String key:List.of("organism","marker_scope","allele_harmonization","ld_pruning","sample_eligibility","reference_eligibility","ploidy_coding"))text(review,key);
        int k=(int)whole(m,"k",1,1024),seed=(int)whole(m,"seed",0,Integer.MAX_VALUE),threads=(int)whole(m,"threads",1,256),ploidy=(int)whole(m,"model_ploidy",1,2);
        String coding=choice(m,"genotype_coding",Set.of("diploid-hardcalls","homozygous-haploid","homozygous-pseudohaploid-diploid"));
        if(ploidy==1&&!coding.equals("homozygous-haploid")||ploidy==2&&coding.equals("homozygous-haploid"))throw new IOException("Declare coherent global model ploidy and genotype coding; no biological ploidy is inferred");
        String method=choice(m,"method",Set.of("block","EM")),acceleration=text(m,"acceleration");
        if(!acceleration.matches("none|(?:qn|sqs)(?:[1-9]|1[0-9]|20)"))throw new IOException("Acceleration must be none,qn1..20 or sqs1..20");
        Stop major=stop(m.get("major_stop")),minor=stop(m.get("minor_stop"));int cv=(int)whole(m,"cv_folds",0,1000),boot=(int)whole(m,"bootstrap_replicates",0,100000),block=(int)whole(m,"bootstrap_block_markers",0,Integer.MAX_VALUE);
        if(cv==1||boot==1||boot==0&&block!=0||boot>0&&block==0)throw new IOException("CV/replicates must be0 or>=2; bootstrap requires an explicit positive marker block, otherwise block must be0");
        double lambda=number(m,"lambda",0,1e12),epsilon=number(m,"epsilon",Double.MIN_VALUE,Math.nextDown(1.0));
        String precision=choice(m,"printed_precision_policy",Set.of("error-if-unbounded","retain-unbounded-with-warning"));
        Map<?,?> l=map(m.get("limits"));keys(l,Set.of("samples","markers","genotype_cells","parameter_cells","input_bytes","retained_bytes","line_bytes","output_bytes"));
        Limits limits=new Limits((int)whole(l,"samples",1,10000000),(int)whole(l,"markers",1,10000000),whole(l,"genotype_cells",1,1000000000000L),whole(l,"parameter_cells",1,Integer.MAX_VALUE),whole(l,"input_bytes",1,1000000000000L),whole(l,"retained_bytes",1024,1000000000000L),(int)whole(l,"line_bytes",64,16*1048576),whole(l,"output_bytes",1024,1000000000000L));
        Path populations=op.equals("supervised")?resolve(recipe,text(m,"populations")):null,model=op.equals("project")?resolve(recipe,text(m,"model")):null;
        String projectionPloidy=op.equals("project")?choice(m,"projection_ploidy_policy",Set.of("require-same-model","explicit-target-model")):"not-applicable";
        if(!op.equals("supervised")&&m.containsKey("populations")||!op.equals("project")&&(m.containsKey("model")||m.containsKey("projection_ploidy_policy")))throw new IOException("Population labels are supervised-only; model/ploidy policy are projection-only");
        return new Spec(recipe,digest,resolve(recipe,text(m,"plink_prefix")),op,version,assembly,k,seed,threads,ploidy,coding,method,acceleration,major,minor,cv,boot,block,lambda,epsilon,precision.equals("retain-unbounded-with-warning"),populations,model,projectionPloidy,limits,m);
    }
    private static Stop stop(Object value)throws IOException {Map<?,?> m=map(value);keys(m,Set.of("kind","value"));String kind=choice(m,"kind",Set.of("delta","iterations"));return new Stop(kind,kind.equals("iterations")?whole(m,"value",1,10000000):number(m,"value",Double.MIN_VALUE,Math.nextDown(1.0)));}
    static Prepared prepare(Spec spec,Path directory)throws IOException {
        var immutable=new LinkedHashMap<Path,String>();immutable.put(spec.recipe,spec.recipeHash);long bytes=0;
        for(String suffix:List.of(".bed",".bim",".fam")){Path p=Path.of(spec.prefix+suffix);regular(p,spec.limits.bytes);bytes=Math.addExact(bytes,Files.size(p));if(bytes>spec.limits.bytes)throw new IOException("Combined ADMIXTURE genotype input-byte bound exceeded");String h=FollowupSupport.hash(p);immutable.put(p,h);Files.copy(p,directory.resolve("cohort"+suffix));if(!FollowupSupport.hash(directory.resolve("cohort"+suffix)).equals(h))throw new IOException("Genotype changed during snapshot");}
        Path prefix=directory.resolve("cohort");var ids=new LinkedHashSet<String>();
        try(BufferedReader reader=Files.newBufferedReader(Path.of(prefix+".bim"))){for(String line;(line=line(reader,spec.limits.line))!=null;){if(line.isBlank())throw new IOException("Blank BIM row is not supported");String[] f=line.strip().split("\\s+");if(f.length!=6||!f[0].matches("[1-9][0-9]{0,8}")||!ids.add(f[1]))throw new IOException("Require six-column BIM, positive numeric contigs and unique marker IDs");if(ids.size()>spec.limits.markers)throw new IOException("ADMIXTURE marker bound exceeded");}}
        List<PlinkBedDataset.Sample> samples;List<PlinkBedDataset.Variant> markers;
        try(var data=dataset(prefix,ids,spec.limits)) {
            samples=data.samples();markers=data.selectedVariants();int n=samples.size(),m=markers.size();
            long parameters=Math.multiplyExact((long)n+m,spec.k),cells=Math.multiplyExact((long)n,m),retained=Math.addExact(Math.multiplyExact(32L*n,spec.k),Math.addExact(768L*m,512L*n));
            if(n<spec.k||m<spec.k||cells>spec.limits.cells||parameters>spec.limits.parameters||retained>spec.limits.retained)throw new IOException("ADMIXTURE K,genotype-cell,parameter or estimated native-memory bound exceeded");
            if(spec.boot>0&&spec.block>=m)throw new IOException("Bootstrap block must be explicitly smaller than retained marker count; oversized defaults can crash the original program");
            long observed=0;long[] sampleObserved=new long[n];
            for(var marker:markers){double[] calls=data.readAllele1Dosages(marker);long called=0;for(int i=0;i<n;i++)if(!Double.isNaN(calls[i])){if(!spec.coding.equals("diploid-hardcalls")&&calls[i]==1)throw new IOException("Heterozygous call in explicitly homozygous haploid/pseudohaploid coding at "+marker.id());called++;sampleObserved[i]++;}if(called==0)throw new IOException("All-missing marker has no estimable allele frequency: "+marker.id());observed=Math.addExact(observed,called);}
            for(int i=0;i<n;i++)if(sampleObserved[i]==0)throw new IOException("All-missing sample has no estimable Q row: "+samples.get(i).individualId());
            if(spec.cv>observed)throw new IOException("CV fold count exceeds observed genotype cells");
        }
        var labels=new ArrayList<String>();var aliases=new LinkedHashMap<String,String>();
        if(spec.populations!=null){regular(spec.populations,spec.limits.bytes);immutable.put(spec.populations,FollowupSupport.hash(spec.populations));var keyed=new HashMap<String,String>();
            try(var reader=Files.newBufferedReader(spec.populations)){if(!"fid\tiid\tpopulation".equals(line(reader,spec.limits.line)))throw new IOException("Supervised TSV header must be fid,iid,population separated by tabs");for(String line;(line=line(reader,spec.limits.line))!=null;){String[] f=line.split("\t",-1);if(f.length!=3||f[0].isBlank()||f[1].isBlank()||f[2].isBlank()||f[2].length()>256||f[2].chars().anyMatch(Character::isISOControl)||keyed.putIfAbsent(key(f[0],f[1]),f[2])!=null)throw new IOException("Invalid/duplicate supervised sample-label row");if(keyed.size()>samples.size())throw new IOException("Extra supervised-label samples");}}
            try(var pop=Files.newBufferedWriter(directory.resolve("cohort.pop"))){for(var s:samples){String label=keyed.remove(key(s.familyId(),s.individualId()));if(label==null)throw new IOException("Every FID/IID needs an explicit population or unknown '-' row");labels.add(label);if(!label.equals("-"))aliases.computeIfAbsent(label,x->"G"+(aliases.size()+1));pop.write(label.equals("-")?"-":aliases.get(label));pop.write('\n');}}
            if(!keyed.isEmpty()||aliases.size()!=spec.k)throw new IOException("Supervision requires exactly K distinct explicit reference populations and no extra samples");
        }else for(int i=0;i<samples.size();i++)labels.add("-");
        AdmixtureOutputs.Model model=spec.model==null?null:AdmixtureOutputs.model(spec.model,spec,directory);
        if(model!=null){immutable.putAll(model.immutable);Files.copy(model.p,directory.resolve("cohort."+spec.k+".P.in"));}
        try(var w=Files.newBufferedWriter(directory.resolve("sample-ledger.tsv"))){w.write("row\tfid\tiid\treference_population\n");for(int i=0;i<samples.size();i++){var s=samples.get(i);FollowupSupport.row(w,new String[]{Integer.toString(i+1),s.familyId(),s.individualId(),labels.get(i)});}}
        try(var w=Files.newBufferedWriter(directory.resolve("marker-ledger.tsv"))){w.write("row\tchromosome\tmarker\tposition\tcounted_BIM_allele2\tother_BIM_allele1\n");for(int i=0;i<markers.size();i++){var v=markers.get(i);FollowupSupport.row(w,new String[]{Integer.toString(i+1),v.chromosome(),v.id(),Long.toString(v.position()),v.allele2(),v.allele1()});}}
        var staged=new TreeMap<String,String>();for(String name:List.of("cohort.bed","cohort.bim","cohort.fam","sample-ledger.tsv","marker-ledger.tsv"))staged.put(name,FollowupSupport.hash(directory.resolve(name)));
        for(String name:List.of("cohort.pop","cohort."+spec.k+".P.in"))if(Files.exists(directory.resolve(name)))staged.put(name,FollowupSupport.hash(directory.resolve(name)));
        var result=new Prepared(spec,prefix,samples,markers,labels,aliases,immutable,staged,model);result.verify();return result;
    }
    private static PlinkBedDataset dataset(Path prefix,Set<String> ids,Limits l)throws IOException{return PlinkBedDataset.open(prefix,ids,new PlinkBedDataset.Limits(l.samples,l.markers,l.markers,l.bytes,l.retained,l.line));}
    static List<String> command(Prepared p){Spec s=p.spec;var a=new ArrayList<String>(List.of("--seed="+s.seed,"-j"+s.threads,"-C",s.major.argument(),"-c",s.minor.argument(),"-m",s.method,"-a",s.acceleration));if(s.cv>0)a.add("--cv="+s.cv);if(s.boot>0){a.add("-B"+s.boot);a.add("--blockSize="+s.block);}if(s.ploidy==1)a.add("--haploid=*");if(s.lambda>0)a.addAll(List.of("-l",Double.toString(s.lambda),"-e",Double.toString(s.epsilon)));if(s.operation.equals("supervised"))a.add("--supervised");if(s.operation.equals("project"))a.add("-P");a.add("cohort.bed");a.add(Integer.toString(s.k));return a;}
    static String key(String fid,String iid){return fid+'\0'+iid;}
    static Map<?,?> json(Path p,long max)throws IOException {return decode(raw(p,max));}
    static Map<?,?> decode(byte[] raw)throws IOException {return map(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString()));}
    static byte[] raw(Path p,long max)throws IOException {if(max<1||max>=Integer.MAX_VALUE)throw new IOException("Invalid bounded metadata byte limit");regular(p,max);try(var in=Files.newInputStream(p)){byte[] raw=in.readNBytes((int)max+1);if(raw.length>max)throw new IOException("Metadata grew beyond its byte bound: "+p);return raw;}}
    static String digest(byte[] raw){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static void regular(Path p,long max)throws IOException {if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p)||Files.size(p)>max)throw new IOException("Missing,linked or oversized ADMIXTURE file: "+p);}
    static void keys(Map<?,?> m,Set<String> allowed)throws IOException {for(Object k:m.keySet())if(!(k instanceof String s)||!allowed.contains(s))throw new IOException("Unknown ADMIXTURE field: "+k);}
    static Map<?,?> map(Object value)throws IOException {if(!(value instanceof Map<?,?> m))throw new IOException("Require an ADMIXTURE JSON object");return m;}
    static String text(Map<?,?> m,String key)throws IOException {if(!(m.get(key) instanceof String s)||s.isBlank()||s.length()>8192||s.chars().anyMatch(c->c<32))throw new IOException("Require explicit single-line string: "+key);return s;}
    static String choice(Map<?,?> m,String key,Set<String> values)throws IOException {String s=text(m,key);if(!values.contains(s))throw new IOException("Unsupported "+key+": "+s+"; expected "+values);return s;}
    static double number(Map<?,?> m,String key,double min,double max)throws IOException {if(!(m.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IOException("Require finite numeric "+key+" in ["+min+","+max+"]");return n.doubleValue();}
    static long whole(Map<?,?> m,String key,long min,long max)throws IOException {double n=number(m,key,min,max);if(n!=Math.rint(n))throw new IOException("Require integer "+key);return (long)n;}
    static Path resolve(Path recipe,String name){return recipe.getParent().resolve(name).toAbsolutePath().normalize();}
    static String line(BufferedReader r,int maximum)throws IOException {return SaigeGrmInputs.line(r,maximum);}
}
