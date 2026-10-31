package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jlinalg.sequence.HomerMotifs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerGenomeMatricesTest {
    @TempDir Path root;
    private HomerGenomeMatrices.Matrix read(String rows,boolean original)throws Exception{Path p=root.resolve("model-"+Math.abs(rows.hashCode())+"-"+original);Files.writeString(p,rows);return HomerGenomeMatrices.read(p,HomerGenomeData.Limits.read(Map.of()),original).get(0);}
    @Test void originalWeightScoringDoesNotNormalizeCountRows()throws Exception{var m=read(">A\tweights\t2\n2\t1\t0\t0\n",true);assertEquals(1,m.nonunitRows());var s=new HomerGenomeMatrices.Scorer(m);assertTrue(s.present("A",HomerMotifs.Strands.FORWARD));assertFalse(s.present("C",HomerMotifs.Strands.FORWARD));assertThrows(IOException.class,()->read(">A\tweights\t2\n2\t1\t0\t0\n",false));}
    @Test void strictProbabilityPolicyStillRejectsNonunitRows()throws Exception{assertThrows(IOException.class,()->read(">A\tx\t0\n.4\t.4\t.4\t.4\n",false));assertEquals(0,read(">A\tx\t0\n.25\t.25\t.25\t.25\n",false).nonunitRows());}
    @Test void originalProbabilityFloorAppliesWithoutRowRescaling()throws Exception{var m=read(">A\tzero\t-6\n0\t0\t0\t0\n",true);var s=new HomerGenomeMatrices.Scorer(m);assertTrue(s.present("T",HomerMotifs.Strands.FORWARD));assertFalse(s.present("N",HomerMotifs.Strands.BOTH));}
    @Test void reverseComplementAndAmbiguousWindowsAreExplicit()throws Exception{var s=new HomerGenomeMatrices.Scorer(read(">A\tx\t2\n2\t0\t0\t0\n",true));assertFalse(s.present("T",HomerMotifs.Strands.FORWARD));assertTrue(s.present("T",HomerMotifs.Strands.REVERSE));assertThrows(IOException.class,()->s.present("a",HomerMotifs.Strands.BOTH));}
    @Test void metadataAndMatricesRemainImmutable()throws Exception{var m=read(">A\tname α\t2\t-123\t0\tT:5,B:2\n2\t1\t0\t0\n",true);assertEquals(-123,m.sourceLogP());assertEquals("T:5,B:2",m.extraHeader().get(1));double[][] values=m.values();values[0][0]=0;assertEquals(2,m.values()[0][0]);assertThrows(UnsupportedOperationException.class,()->m.extraHeader().add("x"));}
    @Test void nonfiniteNegativeAndOverflowingWeightsAreRejected()throws Exception{for(String value:new String[]{"NaN","Infinity","-1","1e308"})assertThrows(IOException.class,()->read(">A\tx\t2\n"+value+"\t0\t0\t0\n",true));}
    @Test void boundedRowsAndEmptyFamiliesCannotReachOriginal()throws Exception{Path p=root.resolve("models");Files.writeString(p,">A\tx\t0\n.25\t.25\t.25\t.25\n.25\t.25\t.25\t.25\n");assertThrows(IOException.class,()->HomerGenomeMatrices.read(p,HomerGenomeData.Limits.read(Map.of("maximum-motif-rows","1")),false));Files.writeString(p,"");assertThrows(IOException.class,()->HomerGenomeMatrices.read(p,HomerGenomeData.Limits.read(Map.of()),true));}
    @Test void explicitCustomOriginalWeightsRetainHeaderMetadataInStaging()throws Exception{var o=HomerGenomeFixture.options(root);Path p=Path.of(o.get("known-motifs"));Files.writeString(p,">A\tweighted\t2\t-123\t0\tT:5,B:2\n2\t1\t0\t0\n");o.put("matrix-values","original-weights");var data=HomerGenomeFixture.stage(o,root.resolve("work"));assertEquals(2,data.known().get(0).motif().values()[0][0]);assertTrue(Files.readString(root.resolve("work/stage-known.motif")).contains("\t-123.0\t0\tT:5,B:2\n"));}
    @Test void originalFixedHeaderPointerCapacityIsCheckedBeforeLaunch(){assertThrows(IOException.class,()->read(">A\tx\t0"+"\tmetadata".repeat(1000)+"\n.25\t.25\t.25\t.25\n",true));}

}
