package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneFeaturesTest {
    @TempDir Path dir;
    @Test void unionLengthDoesNotDoubleCountIsoformsOrStrandsAndSafRoundTrips() throws Exception {
        Path p=dir.resolve("genes.gtf");
        Files.writeString(p,"chr1\ttest\texon\t1\t100\t.\t+\t.\tgene_id \"g\";\nchr1\ttest\texon\t50\t150\t.\t-\t.\tgene_id \"g\"; transcript_id \"t\";\nchr2\ttest\texon\t1\t10\t.\t+\t.\tgene_id \"g\";\n");
        var d=Map.of("chr1",1000L,"chr2",1000L); var a=GeneFeatures.read(p,"GTF",Set.of("exon"),"gene_id",d);
        assertEquals(160,a.unionLengths().get("g")); assertEquals(3,a.features().size());
        Path saf=dir.resolve("genes.saf"); GeneFeatures.writeSaf(a,saf);
        var b=GeneFeatures.read(saf,"SAF",Set.of("exon"),"gene_id",d); assertEquals(a.unionLengths(),b.unionLengths());
        assertThrows(java.io.IOException.class,()->GeneFeatures.spliceSites(a,5));
    }
    @Test void spliceBoundariesUseExonicFlanksAndHandleNestedAndSingleBaseExons() throws Exception {
        Path p=dir.resolve("genes.gtf"); StringBuilder b=new StringBuilder();
        for (int[] e:List.of(new int[]{1,100},new int[]{20,30},new int[]{106,106},new int[]{111,130},new int[]{200,220})) b.append("chr1\tt\texon\t").append(e[0]).append('\t').append(e[1]).append("\t.\t-\t.\tgene_id \"g\"; transcript_id \"t\";\n");
        Files.writeString(p,b);var a=GeneFeatures.read(p,"GTF",Set.of("exon"),"gene_id",Map.of("chr1",1000L));
        assertEquals(List.of(new GeneFeatures.SpliceSite("chr1",99,105,"-"),new GeneFeatures.SpliceSite("chr1",129,199,"-")),GeneFeatures.spliceSites(a,5));
        assertEquals(3,GeneFeatures.spliceSites(a,1).size());
    }
    @Test void conflictingTranscriptAndMalformedReferenceRowsFail() throws Exception {
        Path p=dir.resolve("a.saf");Files.writeString(p,"GeneID\tChr\tStart\tEnd\tStrand\ng\t1\t1\t2\t+\n");
        assertThrows(java.io.IOException.class,()->GeneFeatures.read(p,"SAF",Set.of("exon"),"gene_id",Map.of("chr1",1000L)));
        Files.writeString(p,"GeneID\tChr\tStart\tEnd\tStrand\ng\tchr1\t0\t2\t+\n");assertThrows(java.io.IOException.class,()->GeneFeatures.read(p,"SAF",Set.of("exon"),"gene_id",Map.of("chr1",1000L)));
        var a=new GeneFeatures.Annotation(List.of(new GeneFeatures.Feature("g","chr1",1,10,"+","t"),new GeneFeatures.Feature("other","chr1",30,50,"+","t")),Map.of("g",10L,"other",21L));
        assertThrows(java.io.IOException.class,()->GeneFeatures.spliceSites(a,5));
    }
    @Test void duplicateAttributesAndWrongFeatureSelectionsFail() throws Exception {
        Path p=dir.resolve("a.gtf");Files.writeString(p,"chr1\tt\texon\t1\t2\t.\t+\t.\tgene_id \"g\"; gene_id \"h\";\n");
        assertThrows(java.io.IOException.class,()->GeneFeatures.read(p,"GTF",Set.of("exon"),"gene_id",Map.of("chr1",1000L)));
        Files.writeString(p,"chr1\tt\texon\t1\t2\t.\t+\t.\tgene_id=g;\n");assertThrows(java.io.IOException.class,()->GeneFeatures.read(p,"GTF",Set.of("exon"),"gene_id",Map.of("chr1",1000L)));
    }
    @Test void graphIndexExonUnionsMergeOverlapsAcrossGenesButNotGaps() throws Exception {
        var a=new GeneFeatures.Annotation(List.of(new GeneFeatures.Feature("g1","chr1",1,100,"+","t1"),new GeneFeatures.Feature("g2","chr1",20,30,"-","t2"),new GeneFeatures.Feature("g3","chr1",90,120,"-","t3"),new GeneFeatures.Feature("g4","chr1",122,140,"+","t4")),Map.of("g1",100L,"g2",11L,"g3",31L,"g4",19L));
        Path p=dir.resolve("exons.tsv");GeneFeatures.writeIndexExons(a,p);assertEquals("chr1\t0\t119\t+\nchr1\t121\t139\t+\n",Files.readString(p));
    }
}
