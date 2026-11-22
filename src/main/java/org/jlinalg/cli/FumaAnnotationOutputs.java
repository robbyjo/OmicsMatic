/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jlinalg.genetics.FumaVariantAnnotations.Result;

/** Stream complete source-style results and the full supplied-resource audit ledgers. */
final class FumaAnnotationOutputs {
    private FumaAnnotationOutputs() { }
    static String value(Object v){if(v==null)return "NA";if(v instanceof Double d){if(d.isNaN())return "NA";if(d==Double.POSITIVE_INFINITY)return "Inf";if(d==Double.NEGATIVE_INFINITY)return "-Inf";}return v.toString();}
    private static String[] fields(Object... values){var row=new String[values.length];for(int i=0;i<row.length;i++)row[i]=value(values[i]);return row;}
    private static void updated(Path path,FumaAnnotationInputs.Data input,List<String> columns,
            FumaAnnotationInputs.Output output,FumaAnnotationInputs.Output.Row additions)throws IOException {
        var header=new ArrayList<>(input.header());for(String column:columns)if(!header.contains(column))header.add(column);int[] target=columns.stream().mapToInt(header::indexOf).toArray();
        output.table(path,header,input.rows().size(),i->{String[] row=Arrays.copyOf(input.rows().get(i),header.size()),extra=additions.get(i);if(extra.length!=target.length)throw new IOException("Internal annotation output width differs");for(int j=0;j<extra.length;j++)row[target[j]]=extra[j];return row;});
    }
    static void write(Path directory,FumaAnnotationInputs.Prepared p,Result result,FumaAnnotationInputs.Output out)throws IOException {
        updated(directory.resolve("snps.tsv"),p.snps(),List.of("nearestGene","dist","func","CADD","RDB","minChrState","commonChrState"),out,i->{var r=result.variants().get(i);return fields(out.joinedNearest(r.nearest(),true),out.joinedNearest(r.nearest(),false),r.function(),r.cadd(),r.rdb(),r.minimumState(),r.commonState());});
        updated(directory.resolve("annov.tsv"),p.consequences(),List.of("symbol","chr","pos"),out,i->{var r=result.consequences().get(i);return fields(r.symbol(),r.variant().chromosome(),r.variant().position());});
        updated(directory.resolve("independent.tsv"),p.independent(),List.of("nSNPs","nGWASSNPs"),out,i->{var r=result.independent().get(i);return fields(r.ldRows(),r.gwasLdRows());});
        out.table(directory.resolve("annotation-statistics.tsv"),List.of("annot","ref.count","ref.prop","count","prop","enrichment","fisher.P"),result.categories().size(),i->{var r=result.categories().get(i);return fields(r.reference().annotation(),r.reference().count(),r.reference().proportion(),r.count(),r.proportion(),r.enrichment(),r.fisherP());});
        long population=result.categories().stream().mapToLong(c->c.reference().count()).sum(),selected=result.categories().stream().mapToLong(c->c.count()).sum();
        out.table(directory.resolve("reference-population.tsv"),List.of("reference_row","annot","reference_total","selected_total","selected_category","selected_other","unselected_category","unselected_other"),result.categories().size(),i->{var r=result.categories().get(i);return fields(i+1,r.reference().annotation(),population,selected,r.count(),r.otherSelected(),r.categoryUnselected(),r.otherUnselected());});
        out.table(directory.resolve("consequence-ledger.tsv"),List.of("source_row","uniqID","gene","annot","symbol","chr","pos","first_variant_category","reference_category"),result.consequences().size(),i->{var r=result.consequences().get(i);return fields(r.sourceRow(),r.source().uid(),r.source().gene(),r.source().annotation(),r.symbol(),r.variant().chromosome(),r.variant().position(),r.firstVariantCategory(),r.referenceCategory());});
        out.table(directory.resolve("annotation-disposition.tsv"),List.of("source_row","uniqID","variant_row","selected","reason"),result.annotationRows().size(),i->{var r=result.annotationRows().get(i);return fields(r.sourceRow(),r.uid(),r.variantRow(),r.selected(),r.reason());});
        out.table(directory.resolve("nearest-gene-evidence.tsv"),List.of("variant_row","uniqID","gene_row","gene","symbol","effective_chr","gene_start1","gene_end1","distance"),result.nearest().size(),i->{var r=result.nearest().get(i);var g=p.genes().get(r.geneRow()-1);return fields(r.variantRow(),r.uid(),r.geneRow(),r.gene(),r.symbol(),g.effectiveChromosome(),g.start(),g.end(),r.distance());});
        out.table(directory.resolve("gene-resource.tsv"),List.of("source_row","gene","symbol","raw_chr","effective_chr","start1","end1"),p.genes().size(),i->{var g=p.genes().get(i);return fields(i+1,g.id(),g.symbol(),g.chromosome(),g.effectiveChromosome(),g.start(),g.end());});
        var header=new ArrayList<>(List.of("uniqID","CADD","RDB"));header.addAll(p.stateColumns());for(var track:result.tracks())if(track.sourceColumnWritten())header.add(track.column());int[] annotation=new int[p.variants().size()];Arrays.fill(annotation,-1);for(var row:result.annotationRows())if(row.selected())annotation[row.variantRow()-1]=row.sourceRow()-1;
        out.table(directory.resolve("annotations.tsv"),header,p.variants().size(),i->{var r=result.variants().get(i);var a=p.annotations().get(annotation[i]);var row=new ArrayList<String>(List.of(r.variant().uid(),value(a.cadd()),value(a.rdb())));for(Integer state:a.states())row.add(value(state));for(var track:result.tracks())if(track.sourceColumnWritten())row.add(value(r.bedFlags().get(track.column())));return row.toArray(String[]::new);});
        out.table(directory.resolve("bed-tracks.tsv"),List.of("resource_id","column","dataset","hits","source_column_written"),result.tracks().size(),i->{var r=result.tracks().get(i);return fields(r.id(),r.column(),r.dataset(),r.hits(),r.sourceColumnWritten());});
        out.table(directory.resolve("bed-hits.tsv"),List.of("variant_row","uniqID","resource_id","region_row","chr","start0","end0","start1","dataset"),result.bedHits().size(),i->{var r=result.bedHits().get(i);return fields(r.variantRow(),r.uid(),r.track(),r.regionRow(),r.region().chromosome(),r.region().start(),r.region().end(),r.region().start()+1,r.dataset());});
        if(!result.bedHits().isEmpty())out.table(directory.resolve("annot.bed"),List.of("chr","start","end","dataset"),result.bedHits().size(),i->{var r=result.bedHits().get(i);return fields(r.region().chromosome(),r.region().start()+1,r.region().end(),r.dataset());});
    }
}
