/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.HomerEnrichment;
import org.jlinalg.sequence.HomerMotifs;

/** Complete original output audits; source statistics are retained beside native checks. */
final class HomerCoreAudit {
    private HomerCoreAudit() { }
    static long known(Path work,HomerCoreData.Data data,HomerEnrichment.Statistic statistic)throws Exception{
        Map<String,Integer> models=new HashMap<>();for(int i=0;i<data.models().size();i++)models.put(data.models().get(i).alias(),i);
        var seen=new HashSet<String>();long count=0;double previous=Double.NEGATIVE_INFINITY;
        try(var input=reader(work.resolve("original.stdout"));var output=data.budget().writer(work.resolve("results.tsv"))){
            String[] header=line(input,4096).split("\t",-1);
            String target="# target sequences with Motif(total="+data.targetCount()+")",background="# background sequences with Motif(total="+data.backgroundCount()+")";
            if(header.length!=9||!header[0].equals("Motif Name")||!header[1].equals("Consensus")||!header[2].equals("p-value")||!header[3].equals("Log p-value")||!header[4].equals("Log2 enrichment ratio")||!header[5].equals(target)||!header[6].equals("% of target sequences with Motif")||!header[7].equals(background)||!header[8].equals("% of background sequences with Motif"))throw new IOException("Original HOMER known result header/populations differ from complete staged inputs");
            output.write("original_rank\tmotif_alias\tmotif_ordinal\tmotif_name_json\tconsensus_label_json\toriginal_coarse_p\toriginal_log_p\toriginal_log2_enrichment\toriginal_target_with_motif\toriginal_target_percent\toriginal_background_with_motif\toriginal_background_percent\tnative_log_p\tnative_log2_enrichment\tobserved_background_rate\tbinomial_rate_floor_ceiling\n");
            for(String row;(row=next(input,4096))!=null;){
                String[] fields=row.split("\t",-1);if(fields.length!=9||!models.containsKey(fields[0])||!seen.add(fields[0])||!fields[1].equals("N"))throw new IOException("Original HOMER known result has an unknown, duplicate or malformed motif row");
                int index=models.get(fields[0]);var model=data.models().get(index);var motif=model.compiled().motif();
                var counts=new HomerEnrichment.Counts(Math.toIntExact(data.targetCount()),Math.toIntExact(data.presence()[index][0]),Math.toIntExact(data.backgroundCount()),Math.toIntExact(data.presence()[index][1]));
                var expected=HomerEnrichment.test(counts,statistic);double logP=number(fields[3]);
                if(integer(fields[5])!=counts.targetWithMotif()||integer(fields[7])!=counts.backgroundWithMotif())throw new IOException("Original HOMER known sequence-presence counts differ from the native complete scan");
                if(!fields[2].equals("1e"+expected.originalDisplayExponent())||Math.abs(logP-expected.logP())>.000501+1e-10*Math.abs(expected.logP())||Math.abs(number(fields[4])-expected.log2Enrichment())>.000501)throw new IOException("Original HOMER known log statistic, one-sequence enrichment ratio or coarse display differs from the native audit");
                percent(fields[6],100.0*counts.targetWithMotif()/counts.targets());percent(fields[8],100.0*counts.backgroundWithMotif()/counts.background());
                if(logP<previous)throw new IOException("Original HOMER known rows are not ordered by log enrichment P");previous=logP;
                output.write(++count+"\t"+model.alias()+"\t"+motif.ordinal()+"\t"+RegenieRuntime.json(motif.name())+"\t"+RegenieRuntime.json(motif.consensus())+"\t"+String.join("\t",java.util.Arrays.copyOfRange(fields,2,9))+"\t"+expected.logP()+"\t"+expected.log2Enrichment()+"\t"+expected.observedBackgroundRate()+"\t"+expected.binomialBackgroundRate()+"\n");
            }
        }
        if(count!=data.models().size())throw new IOException("Original HOMER known result omits supplied motifs");return count;
    }
    static long find(Path work,HomerCoreData.Data data)throws Exception{
        Map<String,HomerCoreData.Sequence> sequences=new HashMap<>();for(var seq:data.sequences())sequences.put(seq.alias(),seq);
        Map<String,HomerCoreData.Model> models=new HashMap<>();for(var model:data.models())models.put(model.alias(),model);
        var seen=new HashSet<String>();long count=0;
        try(var input=reader(work.resolve("original.stdout"));var output=data.budget().writer(work.resolve("results.tsv"))){
            output.write("original_row\tmotif_alias\tmotif_ordinal\tmotif_name_json\tsequence_alias\tsequence_ordinal\tsequence_id_json\tleft0\tend_exclusive\tstrand\toriginal_homer_position\toriginal_forward_word\toriginal_score\tnative_score\n");
            for(String row;(row=next(input,8192))!=null;){
                if(++count>data.limits().hits())throw new IOException("Original HOMER find exceeds the declared complete hit capacity");
                String[] fields=row.split("\t",-1);if(fields.length!=6||!sequences.containsKey(fields[0])||!models.containsKey(fields[3])||!List.of("+","-").contains(fields[4]))throw new IOException("Original HOMER find has unknown identities, strand or malformed columns");
                var seq=sequences.get(fields[0]);var model=models.get(fields[3]);var motif=model.compiled().motif();char strand=fields[4].charAt(0);
                if(data.strands()==HomerMotifs.Strands.FORWARD&&strand!='+'||data.strands()==HomerMotifs.Strands.REVERSE&&strand!='-')throw new IOException("Original HOMER returned an unrequested strand");
                int position=integer(fields[1]);long offset=data.offset()==null?-(long)(seq.dna().length()/2):data.offset();
                long anchor=position-offset,left=anchor-(strand=='-'?motif.width()-1L:0);long end=left+motif.width();
                if(left<0||end>seq.dna().length()||!seq.dna().substring((int)left,(int)end).equals(fields[2]))throw new IOException("Original HOMER find interval/forward word does not match its complete staged sequence");
                String key=seq.alias()+"/"+model.alias()+"/"+left+"/"+strand;if(!seen.add(key))throw new IOException("Original HOMER find duplicates a motif/sequence/position/strand site");
                var expected=new ArrayList<HomerMotifs.Hit>(1);
                HomerMotifs.scan(fields[2],model.compiled(),new HomerMotifs.Options(strand=='+'?HomerMotifs.Strands.FORWARD:HomerMotifs.Strands.REVERSE,0,false,motif.width(),motif.width(),1),expected::add);
                if(expected.size()!=1||Math.abs(number(fields[5])-expected.get(0).logOdds())>.000000501+1e-12*Math.abs(expected.get(0).logOdds()))throw new IOException("Original HOMER find score/threshold differs from the native fixed supplied model");
                output.write(count+"\t"+model.alias()+"\t"+motif.ordinal()+"\t"+RegenieRuntime.json(motif.name())+"\t"+seq.alias()+"\t"+seq.ordinal()+"\t"+RegenieRuntime.json(seq.id())+"\t"+left+"\t"+end+"\t"+strand+"\t"+fields[1]+"\t"+fields[2]+"\t"+fields[5]+"\t"+expected.get(0).logOdds()+"\n");
            }
        }
        if(count!=data.hits())throw new IOException("Original HOMER find site set is incomplete: every row is valid and unique, but its count differs from the complete native scan");
        return count;
    }
    static BufferedReader reader(Path path)throws IOException{
        return new BufferedReader(new java.io.InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));
    }
    private static String line(BufferedReader input,int max)throws IOException{String value=next(input,max);if(value==null)throw new IOException("Missing original HOMER result header");return value;}
    static String next(BufferedReader input,int max)throws IOException{
        var text=new StringBuilder();int c;while((c=input.read())!=-1&&c!='\n'){if(text.length()>=max||c<32&&c!='\t')throw new IOException("Oversized/control-containing original HOMER result line");text.append((char)c);}
        return c==-1&&text.isEmpty()?null:text.toString();
    }
    private static double number(String text)throws IOException{
        try{double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new NumberFormatException();return value;}
        catch(NumberFormatException failure){throw new IOException("Original HOMER numeric field is nonfinite/invalid",failure);}
    }
    private static int integer(String text)throws IOException{try{return Integer.parseInt(text);}catch(NumberFormatException failure){throw new IOException("Original HOMER count/position is not an exact signed integer",failure);}}
    private static void percent(String text,double expected)throws IOException{if(!text.endsWith("%")||Math.abs(number(text.substring(0,text.length()-1))-expected)>.005001)throw new IOException("Original HOMER percentage differs from complete sequence-presence counts");}
}
