package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KallistoTablesTest {
    @TempDir Path directory;
    private Path file(String name,String text)throws Exception{Path path=directory.resolve(name);Files.writeString(path,text);return path;}
    private Path resource(String name)throws Exception{Path path=directory.resolve(name);try(var input=getClass().getResourceAsStream("/kallisto/"+name)){assertNotNull(input);Files.copy(input,path);}return path;}
    @Test void referenceIdsRawLengthsAndUpstreamTransformCountsAreLiteral()throws Exception {
        Path path=file("reference.fa",">001 human description\nacgtnu\nAAAAAAAAAA\n>tx.2|gene annotation\nCGTA\n");var targets=KallistoTables.reference(path,5,100,1000,false);
        assertEquals("001",targets.get(0).id());assertEquals(16,targets.get(0).length());assertEquals(1,targets.get(0).ambiguous());assertEquals(1,targets.get(0).uracils());assertEquals(10,targets.get(0).terminalAs());assertEquals("tx.2|gene",targets.get(1).id());
        assertThrows(java.io.IOException.class,()->KallistoTables.reference(path,5,100,1000,true));assertThrows(UnsupportedOperationException.class,()->targets.clear());
        Path zipped=directory.resolve("reference.fa.gz");try(var stream=new GZIPOutputStream(Files.newOutputStream(zipped))){stream.write(Files.readAllBytes(path));}assertEquals(targets,KallistoTables.reference(zipped,5,100,1000,false));
    }
    @Test void invalidReferenceAndResourceBoundsFailRatherThanRenameOrDrop()throws Exception {
        for(String text:List.of(">a\nACGT\n>a\nGCTA\n",">a\n>b\nCGTA\n","ACGT\n",">a\nAC-GT\n","> description\nACGT\n")){Path path=file("bad"+Math.abs(text.hashCode())+".fa",text);assertThrows(java.io.IOException.class,()->KallistoTables.reference(path,5,100,1000,false));}
        Path path=file("valid.fa",">a\nACGT\n>b\nCGTA\n");assertThrows(java.io.IOException.class,()->KallistoTables.reference(path,1,100,1000,false));assertThrows(java.io.IOException.class,()->KallistoTables.reference(path,5,3,1000,false));assertThrows(java.io.IOException.class,()->KallistoTables.reference(path,5,100,4,false));assertEquals(2,KallistoTables.reference(path,5,100,Long.MAX_VALUE,false).size());
    }
    @Test void independentFractionalCountsAndEffectiveLengthTpmMath()throws Exception {
        var targets=List.of(new KallistoTables.Target("001",200,0,0,0),new KallistoTables.Target("b",400,0,0,0));
        Path path=file("fractional.tsv","target_id\tlength\teff_length\test_counts\ttpm\n001\t200\t100\t1.5\t400000\nb\t400\t200\t4.5\t600000\n");var audit=KallistoTables.abundance(path,targets,6,1000);
        assertEquals(6,audit.totalEstimatedCounts());assertEquals(1e6,audit.totalTpm());assertEquals(1.5,audit.rows().get(0).estimatedCount());assertEquals(2,audit.nonzeroTargets());assertThrows(UnsupportedOperationException.class,()->audit.rows().clear());
    }
    @Test void originalSevenPositiveTwoRejectedAndThreeBootstrapTables()throws Exception {
        var targets=KallistoTables.reference(resource("transcripts.fa"),10,10000,10000,true);
        for(String name:List.of("paired","bootstrap17","bias","fr","single","single-fr","single-overhang")){var audit=KallistoTables.abundance(resource(name+".tsv"),targets,160,10000);assertEquals(160,audit.totalEstimatedCounts(),.001,name);}
        for(String name:List.of("rf","single-rf")){Path path=resource(name+".tsv");assertThrows(java.io.IOException.class,()->KallistoTables.abundance(path,targets,0,10000));}
        for(int i=0;i<3;i++){var audit=KallistoTables.abundance(resource("bootstrap17-draw"+i+".tsv"),targets,160,10000);assertEquals(i==0?32:37,audit.rows().get(0).estimatedCount());assertEquals(i==0?128:123,audit.rows().get(1).estimatedCount());}
    }
    @Test void outputIdentityDomainsTotalsAndArithmeticAreCheckedIndependently()throws Exception {
        var targets=List.of(new KallistoTables.Target("001",200,0,0,0));String header="target_id\tlength\teff_length\test_counts\ttpm\n";
        for(String row:List.of("1\t200\t100\t6\t1000000\n","001\t201\t100\t6\t1000000\n","001\t200\t0\t6\t1000000\n","001\t200\t100\t5\t1000000\n","001\t200\t100\t6\t100\n","001\t200\tNaN\t6\t1000000\n","001\t200\t100\t-6\t1000000\n","")){Path path=file("bad"+Math.abs(row.hashCode())+".tsv",header+row);assertThrows(java.io.IOException.class,()->KallistoTables.abundance(path,targets,6,10000));}
    }
}
