/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.jlinalg.genetics.TranscriptSiteMapping;
import org.jlinalg.genetics.TranscriptSiteMapping.Boundary;
import org.jlinalg.genetics.TranscriptSiteMapping.Limits;
import org.jlinalg.genetics.TranscriptSiteMapping.Missing;
import org.jlinalg.genetics.TranscriptSiteMapping.Options;
import org.jlinalg.genetics.TranscriptSiteMapping.Reference;
import org.jlinalg.genetics.TranscriptSiteMapping.Site;
import org.jlinalg.genetics.TranscriptSiteMapping.Status;
import org.jlinalg.genetics.TranscriptSiteMapping.Transcript;
import org.jlinalg.genetics.TranscriptSiteMapping.Variant;

/** Synthetic source-API recipe; no patient data, downloads or external tools. */
public final class TranscriptSiteExample {
    private TranscriptSiteExample() { }
    /** Run the fixed synthetic population and print every disposition and site hit. */
    public static void main(String[] args) {
        if(args.length!=0)throw new IllegalArgumentException("This fixed synthetic API example accepts no arguments");
        var variants=List.of(
            v("start","1",100,"1e-6"),v("interior","1",500_000,"1e-6"),
            v("end-tiny-p","1",1_000_000,"1e-1000"),v("missing","1",100,null),
            v("above-cutoff","1",100,"0.0000100000000000000000000001"),
            v("exact-cutoff","1",100,"1e-5"),v("reverse","2",600,"1e-6"),
            v("exact-window-edge","1",10_100,"1e-6"),v("outside-window","1",10_101,"1e-6"));
        var transcripts=List.of(new Transcript("geneA","txA","1",100,1_000_000,"+","protein_coding"),
            new Transcript("geneB","txB","2",500,600,"-","protein_coding"));
        var reference=new Reference("synthetic-only-v1","synthetic-build","synthetic-genes","synthetic-transcripts",transcripts);
        var options=new Options("synthetic-build",new BigDecimal("1e-5"),10_000,Boundary.INCLUSIVE,
            Set.of(Site.TSS,Site.TES),Set.of("protein_coding"),Missing.EXCLUDE,new Limits(100,100,10_000));
        var result=TranscriptSiteMapping.map(variants,reference,options,()->{});
        var expected=List.of(Status.MAPPED,Status.UNMAPPED_IN_SUPPLIED_REFERENCE,Status.MAPPED,
            Status.MISSING_P_EXCLUDED,Status.ABOVE_P_CUTOFF,Status.MAPPED,Status.MAPPED,Status.MAPPED,
            Status.UNMAPPED_IN_SUPPLIED_REFERENCE);
        if(!result.variants().stream().map(TranscriptSiteMapping.VariantResult::status).toList().equals(expected)
                ||result.hits().size()!=6||result.genes().size()!=5)throw new IllegalStateException("Synthetic example mapping audit failed");
        System.out.print("# VARIANTS\nrow\tvariant_id\tp\tstatus\tsite_hits\ttranscripts\tgenes\tnearest_hit_bp\n");
        for(var row:result.variants())System.out.print(row.row()+"\t"+row.variant().id()+"\t"+(row.variant().p()==null?"NA":row.variant().p())+"\t"+row.status()+"\t"+row.siteHits()+"\t"+row.transcripts()+"\t"+row.genes()+"\t"+(row.nearestHitDistanceBp()==null?"NA":row.nearestHitDistanceBp())+"\n");
        System.out.print("# SITE HITS\nvariant_row\ttranscript_row\tgene\ttranscript\tsite\tposition\tsigned_bp\tdistance_bp\n");
        for(var hit:result.hits())System.out.print(hit.variantRow()+"\t"+hit.transcriptRow()+"\t"+hit.transcript().gene()+"\t"+hit.transcript().transcript()+"\t"+hit.site()+"\t"+hit.sitePosition()+"\t"+hit.signedDistanceBp()+"\t"+hit.distanceBp()+"\n");
        for(String warning:result.warnings())System.err.print("WARNING: "+warning+"\n");
    }
    private static Variant v(String id,String chromosome,long position,String p){return new Variant("synthetic-trait",id,chromosome,position,p==null?null:new BigDecimal(p));}
}
