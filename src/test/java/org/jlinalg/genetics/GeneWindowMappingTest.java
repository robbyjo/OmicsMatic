package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneWindowMappingTest {
    @TempDir Path dir;
    @Test void exactCurrentUpstreamAnnotationMatchesEverySyntheticMembership()throws Exception{
        Path f=Path.of("src/test/resources/magma");var v=GeneWindowMapping.variants(f.resolve("snps.tsv"),false,false);var g=GeneWindowMapping.genes(f.resolve("genes.tsv"),false);var a=GeneWindowMapping.assign(v,g,0,0,false);assertEquals(202,a.size());assertEquals(995,v.size());assertTrue(a.stream().filter(x->x.gene().id().equals("EMPTY")).findFirst().orElseThrow().variants().isEmpty());GeneWindowMapping.verifyMagma(f.resolve("annotation.genes.annot.txt"),a);Path out=dir.resolve("native.annot");GeneWindowMapping.writeMagma(out,a,0,0,false);GeneWindowMapping.verifyMagma(out,a);
    }
    @Test void strandAwareAsymmetryClosedBoundariesAndZeroSentinelAreExplicit(){
        var v=new ArrayList<GeneWindowMapping.Variant>();for(int p:new int[]{70,45,1,10,20,71})v.add(new GeneWindowMapping.Variant("S"+p,"1",p));var genes=List.of(new GeneWindowMapping.Gene("P","1",10,20,"+"),new GeneWindowMapping.Gene("M","1",10,20,"-"));var a=GeneWindowMapping.assign(v,genes,50,25,false);assertEquals(0,a.get(0).windowStart());assertEquals(45,a.get(0).windowEnd());assertEquals(List.of("S45","S1","S10","S20"),a.get(0).variants().stream().map(GeneWindowMapping.Variant::id).toList());assertEquals(70,a.get(1).windowEnd());assertEquals(5,a.get(1).variants().size());assertEquals(45,GeneWindowMapping.assign(v,genes,50,25,true).get(1).windowEnd());
    }
    @Test void indexedMembershipAgreesWithIndependentBruteForceAcrossRandomChromosomes(){
        var random=new Random(661);var v=new ArrayList<GeneWindowMapping.Variant>();for(int i=0;i<200;i++)v.add(new GeneWindowMapping.Variant("V"+i,Integer.toString(1+random.nextInt(3)),1+random.nextInt(10000)));var g=new ArrayList<GeneWindowMapping.Gene>();for(int i=0;i<60;i++){int s=1+random.nextInt(9900);g.add(new GeneWindowMapping.Gene("G"+i,Integer.toString(1+random.nextInt(3)),s,s+50,random.nextBoolean()?"+":"-"));}var a=GeneWindowMapping.assign(v,g,150,30,false);for(int i=0;i<g.size();i++){var gene=g.get(i);int lower=gene.start()-(gene.strand().equals("+")?150:30),upper=gene.end()+(gene.strand().equals("+")?30:150);var expected=v.stream().filter(s->s.chromosome().equals(gene.chromosome())&&s.position()>=lower&&s.position()<=upper).toList();assertEquals(expected,a.get(i).variants());}
    }
    @Test void duplicateIdsUnknownStrandsOverflowAndAmbiguousChromosomeAliasesFail(){
        var v=new GeneWindowMapping.Variant("V","1",10);var gene=new GeneWindowMapping.Gene("G","1",1,20,".");assertThrows(IllegalArgumentException.class,()->GeneWindowMapping.assign(List.of(v,v),List.of(gene),0,0,false));assertThrows(IllegalArgumentException.class,()->GeneWindowMapping.assign(List.of(v),List.of(gene),10,0,false));assertThrows(ArithmeticException.class,()->GeneWindowMapping.assign(List.of(v),List.of(new GeneWindowMapping.Gene("L","1",Integer.MAX_VALUE-1,Integer.MAX_VALUE,"+")),0,1,false));assertEquals("X",GeneWindowMapping.chromosome("23",false));assertEquals("23",GeneWindowMapping.chromosome("23",true));assertThrows(IllegalArgumentException.class,()->GeneWindowMapping.chromosome("chr1",false));assertThrows(IllegalArgumentException.class,()->GeneWindowMapping.chromosome("01",false));assertThrows(IllegalArgumentException.class,()->GeneWindowMapping.verifyVariantLocations(List.of(v),List.of(new GeneWindowMapping.Variant("V","1",11))));
    }
}
