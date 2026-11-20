/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.enrichment.BroadGseaEngine;
import static org.jlinalg.cli.BroadGseaFiles.*;

/** Deterministic exact binary objects plus human-readable tables and retained prefixes. */
final class BroadGseaOutputs {
    private BroadGseaOutputs() { }
    static final class Progress implements BroadGseaEngine.Progress,AutoCloseable {
        private final Path root;
        private final BufferedWriter dispositions,observed,nulls;
        Progress(Path root)throws IOException{
            this.root=root;Files.createDirectory(root);
            dispositions=writer(root.resolve("dispositions.jsonl"));observed=writer(root.resolve("observed.jsonl"));nulls=writer(root.resolve("null-prefix.tsv"));
            nulls.write("set_index\tset_name_json\tpermutation_index\tes_float32_hex\n");nulls.flush();
        }
        @Override public void disposition(BroadGseaEngine.Disposition d){
            try{dispositions.write(NextflowJson.json(BroadGseaOutputs.disposition(d)));dispositions.write('\n');dispositions.flush();}catch(IOException e){throw new UncheckedIOException(e);}
        }
        @Override public void ranking(int p,BroadGseaEngine.Ranked r){if(p==-1)try{rank(root.resolve("real-ranking.tsv"),r);}catch(IOException e){throw new UncheckedIOException(e);}}
        @Override public void observed(int g,String name,org.jlinalg.enrichment.BroadGseaStatistics.Score score){
            try{observed.write(NextflowJson.json(Map.of("set_index",g,"name",name,"es",score.es(),"rank_at_es",score.rankAtEs(),"rank_score_at_es",score.rankScoreAtEs(),"hits",score.hits(),"hit_profile",score.hitProfile(),"full_profile",score.fullProfile())));observed.write('\n');observed.flush();}
            catch(IOException e){throw new UncheckedIOException(e);}
        }
        @Override public void nullValue(int g,String name,int p,float es){
            try{nulls.write(g+"\t"+NextflowJson.json(name)+"\t"+p+"\t"+Float.toHexString(es)+"\n");if(p%256==0)nulls.flush();}catch(IOException e){throw new UncheckedIOException(e);}
        }
        @Override public void close()throws IOException{
            IOException failure=null;for(Writer w:new Writer[]{dispositions,observed,nulls})try{w.close();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}if(failure!=null)throw failure;
        }
    }
    private static BufferedWriter writer(Path path)throws IOException{return Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static Map<String,Object> disposition(BroadGseaEngine.Disposition d){return Map.of("name",d.name(),"input_members",d.inputMembers(),"matched_members",d.matchedMembers(),"absent_members",d.absentMembers(),"status",d.status(),"source_size_filtering_disabled",d.sourceSizeFilteringDisabled());}
    static void rank(Path path,BroadGseaEngine.Ranked rank)throws IOException{
        String[] names=rank.genes();float[] scores=rank.scores();int[] rows=rank.sourceRows();double[] raw=rank.metricValues();
        try(var w=writer(path)){w.write("rank_index\tgene_id_json\tscore_float32\tsource_row_index\traw_metric_float64\n");
            for(int i=0;i<names.length;i++)w.write(i+"\t"+NextflowJson.json(names[i])+"\t"+scores[i]+"\t"+rows[i]+"\t"+raw[i]+"\n");}
    }
    static void write(Path root,BroadGseaEngine.Result result)throws IOException{
        rank(root.resolve("ranking.tsv"),result.ranking());
        var dispositions=new ArrayList<Object>();for(var d:result.dispositions())dispositions.add(disposition(d));json(root.resolve("dispositions.json"),dispositions);
        var family=result.statistics();float[] nes=family.nes(),np=family.nominalP(),fdr=family.fdr(),fwer=family.fwer();float[][] normalized=family.normalizedNull();
        try(var table=writer(root.resolve("scores.tsv"));var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(root.resolve("result.bin"),StandardOpenOption.CREATE_NEW)))){
            table.write("set_index\tset_name_json\tmatched_size\tes\tnes\tnominal_p\tfdr\tfwer\trank_at_es\trank_score_at_es\n");
            out.writeInt(0x42475331);out.writeInt(1);text(out,result.edition());text(out,result.nullModel());out.writeLong(result.workUsed());
            var ranking=result.ranking();String[] genes=ranking.genes();float[] scores=ranking.scores();int[] rows=ranking.sourceRows();double[] raw=ranking.metricValues();out.writeInt(genes.length);
            for(int i=0;i<genes.length;i++){text(out,genes[i]);out.writeFloat(scores[i]);out.writeInt(rows[i]);out.writeDouble(raw[i]);}
            out.writeInt(result.dispositions().size());
            for(var d:result.dispositions()){text(out,d.name());out.writeInt(d.inputMembers());strings(out,d.matchedMembers());strings(out,d.absentMembers());text(out,d.status());out.writeBoolean(d.sourceSizeFilteringDisabled());}
            out.writeInt(result.sets().size());int g=0;
            for(var set:result.sets()){
                var score=set.observed();text(out,set.name());strings(out,set.members());out.writeFloat(score.es());out.writeInt(score.rankAtEs());out.writeFloat(score.rankScoreAtEs());
                int[] hits=score.hits();out.writeInt(hits.length);for(int hit:hits)out.writeInt(hit);floats(out,score.hitProfile());floats(out,score.fullProfile());
                floats(out,set.nullEs());floats(out,normalized[g]);out.writeFloat(nes[g]);out.writeFloat(np[g]);out.writeFloat(fdr[g]);out.writeFloat(fwer[g]);
                table.write(g+"\t"+NextflowJson.json(set.name())+"\t"+set.members().size()+"\t"+score.es()+"\t"+nes[g]+"\t"+np[g]+"\t"+fdr[g]+"\t"+fwer[g]+"\t"+score.rankAtEs()+"\t"+score.rankScoreAtEs()+"\n");g++;
            }
        }
        json(root.resolve("result-summary.json"),Map.of("schema_version",1,"engine",BroadGseaRecipe.ENGINE,"edition",result.edition(),"null_model",result.nullModel(),"ranked_genes",result.ranking().genes().length,"complete_family_sets",result.sets().size(),"work_used",result.workUsed(),"binary_format","BGS1/version1/big-endian IEEE754/literal UTF8"));
    }
    private static void text(DataOutputStream out,String text)throws IOException{byte[] raw=text.getBytes(StandardCharsets.UTF_8);out.writeInt(raw.length);out.write(raw);}
    private static void strings(DataOutputStream out,List<String> text)throws IOException{out.writeInt(text.size());for(String value:text)text(out,value);}
    private static void floats(DataOutputStream out,float[] values)throws IOException{out.writeInt(values.length);for(float value:values)out.writeFloat(value);}
}
