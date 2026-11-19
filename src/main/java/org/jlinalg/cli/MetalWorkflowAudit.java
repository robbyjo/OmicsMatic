/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.MetalWorkflowFiles.*;
import org.jlinalg.cli.MetalWorkflowRequest.Config;
import org.jlinalg.cli.MetalWorkflowInputs.Population;

/** Complete row/contributor admission; preserves original numerical conventions and precision. */
final class MetalWorkflowAudit {
    interface Sink {void line(String line)throws IOException;}
    record Report(long markers,long inputRows,long warnings){ }
    static final String SUMMARY="marker\tallele1\tallele2\tcohorts\teffect\tstderr\tz\tprobability_scale\tprobability\tprimary_het_q\tprimary_het_df\tprimary_het_probability_scale\tprimary_het_probability\tprimary_i2\tprimary_freq\tprimary_freq_se\tprimary_freq_min\tprimary_freq_max\tdeclared_total_n\tprimary_weight\tprimary_overlap_adjusted_n\tpost_gc";
    private MetalWorkflowAudit(){ }
    static List<String> header(Config c,boolean post){
        var h=new ArrayList<>(List.of("MarkerName","Allele1","Allele2"));if(c.on("frequency"))h.addAll(List.of("Freq1","FreqSE","MinFreq","MaxFreq"));
        h.addAll(c.stderr()?List.of("Effect","StdErr"):List.of("Weight","Zscore"));if(!post&&c.on("overlap"))h.add("N");
        boolean log=(!c.post()||post)&&c.get("output_probability").equals("log10");h.add(log?"log(P)":"P-value");h.add("Direction");
        if(!post&&c.on("heterogeneity"))h.addAll(List.of("HetISq","HetChiSq","HetDf",log?"logHetP":"HetPVal"));h.add("DeclaredN");return List.copyOf(h);
    }
    static long diagnostics(Path root,Config c,boolean post,long expectedMarkers,Sink warnings)throws IOException {
        String stage=post?"post-gc":"primary";Path folder=root.resolve("stages/"+stage);long found=0;boolean version=false,completed=false;
        for(String stream:List.of("original.stdout","original.stderr"))try(var lines=new Lines(folder.resolve(stream),c.integer("max_line_bytes"))){
            long ordinal=0;for(String line;(line=lines.next())!=null;){ordinal++;if(line.contains("2020-05-05"))version=true;if(line.equals("## Completed meta-analysis for "+expectedMarkers+" markers!"))completed=true;
                require(!line.contains("ERROR:")&&!line.contains("FATAL:")&&!line.contains("Unrecognized command"),"Original error text even if exit code is zero; inspect "+stage+"/"+stream);
                if(line.contains("WARNING:")){found++;warnings.line(stage+"\t"+stream+"\t"+ordinal+"\t"+line.replace("\t","\\t"));}
            }
        }
        require(version&&completed,"Original release/completion report missing; exit zero alone is insufficient");return found;
    }
    static void table(Path root,Config c,Population p,boolean post,Sink summary)throws IOException {
        var names=header(c,post);boolean log=names.contains("log(P)");long rows=0;for(var m:p.markers().values())m.seen=false;
        try(var in=new Lines(root.resolve("stages/"+(post?"post-gc":"primary")+"/result1.tbl"),c.integer("max_line_bytes"))){
            String first=in.next();require(first!=null&&List.of(first.split("\t",-1)).equals(names),"Original result header differs from the declared scheme/settings");
            for(String line;(line=in.next())!=null;){
                String[] f=line.split("\t",-1);require(f.length==names.size(),"Malformed original result row");var values=new HashMap<String,String>();for(int i=0;i<f.length;i++)values.put(names.get(i),f[i]);
                String id=values.get("MarkerName");var m=p.markers().get(id);require(m!=null&&!m.seen,"Unknown/duplicate original result marker: "+id);m.seen=true;rows++;
                if(c.get("allele_mode").equals("none"))require(f[1].isEmpty()&&f[2].isEmpty(),"Non-allelic analysis unexpectedly acquired alleles");
                else require(MetalWorkflowInputs.compatible(f[1].toUpperCase(Locale.ROOT),f[2].toUpperCase(Locale.ROOT),m.a1,m.a2),"Original output alleles incompatible with captured marker: "+id);
                if(post)require(f[1].equals(m.primaryAllele1)&&f[2].equals(m.primaryAllele2),"Post-GC orientation differs from the primary heterogeneity/frequency lineage");else{m.primaryAllele1=f[1];m.primaryAllele2=f[2];}
                String direction=values.get("Direction");require(direction.length()==(post?1:p.studies().size()),"Original direction vector dimension differs");
                for(int i=0;i<direction.length();i++){char x=direction.charAt(i);boolean present=post||m.studies.get(i);require(present?"+-0".indexOf(x)>=0:x=='?',"Original omitted or invented a study contribution: "+id);}
                double declared=number(values.get("DeclaredN"),"original DeclaredN");double tolerance=.50000001*Math.pow(10,Math.floor(Math.log10(m.totalN))-5)+Math.abs(m.totalN)*1e-12;require(Math.abs(declared-m.totalN)<=tolerance,"Original declared-N sum differs at six-significant-digit source precision");
                if(c.stderr()){number(values.get("Effect"),"original effect");require(number(values.get("StdErr"),"original standard error")>0,"Nonpositive/unrepresentable printed original standard error; no complete result or silent precision repair");}
                else{
                    double weight=number(values.get("Weight"),"original source Weight");require(weight>=0,"Negative original source Weight");number(values.get("Zscore"),"original Z");
                    if(!post){m.primaryWeight=values.get("Weight");if(!c.on("overlap"))require(Math.abs(weight-m.totalN)<=.005000001+Math.abs(m.totalN)*1e-12,"Original weight does not match complete input at printed precision");}
                    if(!post&&c.on("overlap")){require(number(values.get("N"),"original overlap-adjusted N")>0,"Nonpositive overlap-adjusted source N");m.primaryOverlapN=values.get("N");}
                }
                probability(values.get(log?"log(P)":"P-value"),log,"association probability");
                if(!post&&c.on("heterogeneity")){
                    double q=number(values.get("HetChiSq"),"original Q"),i2=number(values.get("HetISq"),"original I2");int df=Integer.parseInt(values.get("HetDf"));require(q>=0&&i2>=0&&i2<=100&&df==m.studies.cardinality()-1,"Invalid original heterogeneity or lost cohort contribution");String hp=values.get(log?"logHetP":"HetPVal");probability(hp,log,"original heterogeneity probability");m.het=qToken(values,"HetChiSq")+'\t'+values.get("HetDf")+'\t'+(log?"log10":"ordinary")+'\t'+hp+'\t'+values.get("HetISq");
                }
                if(c.on("frequency")){
                    for(String key:List.of("Freq1","MinFreq","MaxFreq")){double x=number(values.get(key),"original "+key);require(x>=0&&x<=1,"Invalid original allele frequency");}double sd=number(values.get("FreqSE"),"original frequency SD");require(sd>=0&&sd<=1,"Invalid original frequency SD");
                    if(!post)m.freq=values.get("Freq1")+'\t'+values.get("FreqSE")+'\t'+values.get("MinFreq")+'\t'+values.get("MaxFreq");
                }
                if(!post&&c.post()&&!c.stderr())require(number(values.get("P-value"),"post-GC input P")>0&&number(values.get(c.on("overlap")?"N":"Weight"),"post-GC input weight")>0,"Printed primary P/weight cannot be represented by original post-GC input. Retain failure; do not insert a floor or substitute a different weight");
                if(post||!c.post())summary.line(String.join("\t",id,f[1],f[2],Integer.toString(m.studies.cardinality()),c.stderr()?values.get("Effect"):"-",c.stderr()?values.get("StdErr"):"-",c.stderr()?"-":values.get("Zscore"),log?"log10":"ordinary",values.get(log?"log(P)":"P-value"),m.het,m.freq,values.get("DeclaredN"),m.primaryWeight,m.primaryOverlapN,Boolean.toString(c.post())));
            }
        }
        require(rows==p.markers().size()&&p.markers().values().stream().allMatch(m->m.seen),"Original result does not contain the complete captured marker union; no dropped rows admitted");
    }
    private static String qToken(Map<String,String> values,String key){return values.get(key);}
    private static void probability(String raw,boolean log,String label)throws IOException {
        if(log)require(number(raw,label)<=0,"Positive original base-10 log probability");
        else{try{BigDecimal p=new BigDecimal(raw);require(p.signum()>0&&p.compareTo(BigDecimal.ONE)<=0,"Original probability outside (0,1] or literal zero lost its log probability; choose log10 output in a NEW request, never insert a floor");}catch(NumberFormatException e){throw new IOException("Nonfinite/unparseable original "+label,e);}}
    }
    static Report audit(Path root,Config c,Population p,Sink summary,Sink warnings)throws IOException {
        warnings.line("stage\tstream\tline\toriginal_warning");long count=diagnostics(root,c,false,p.markers().size(),warnings);if(c.post())count+=diagnostics(root,c,true,p.markers().size(),warnings);
        require(count==0||c.get("warning_policy").equals("allow-recorded"),"Original warnings forbidden by request; inspect native/WARNINGS.tsv and raw consoles, then make an explicit decision in a NEW request");
        summary.line(SUMMARY);table(root,c,p,false,summary);if(c.post())table(root,c,p,true,summary);return new Report(p.markers().size(),p.rows(),count);
    }
}
