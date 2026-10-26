package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SparseCountsTest {
    @TempDir Path dir;
    private Path write(String name,String text)throws IOException{Path p=dir.resolve(name);Files.writeString(p,text);return p;}
    @Test void diskSpillMergesDuplicatesBeforeCountsAndFiltersOnlySelectedFeatureType()throws Exception{
        Path f=write("features.tsv","G1\tgene1\tGene Expression\nG2\tgene2\tGene Expression\nA1\tantibody\tAntibody Capture\n"),c=write("barcodes.tsv","C1\nC2\nC3\n");
        Path m=write("matrix.mtx","%%MatrixMarket matrix coordinate integer general\n% comment\n3 3 7\n2 2 4\n1 1 2\n1 1 3\n3 1 100\n2 1 1\n2 2 0\n1 2 7\n");
        var s=SparseCounts.canonicalize(m,f,c,dir.resolve("canonical"),SparseCounts.Kind.RAW_UMI,"Gene Expression",1);
        assertEquals(7,s.inputEntries());assertEquals(5,s.selectedInputEntries());assertEquals(4,s.outputEntries());assertEquals(1,s.excludedFeatures());assertEquals(1,s.zeroInputEntries());
        assertArrayEquals(new double[]{6,11,0},s.cellTotals());assertArrayEquals(new int[]{2,2,0},s.detectedGenes());
        String result=Files.readString(dir.resolve("canonical/matrix.mtx"));assertTrue(result.endsWith("2 3 4\n1 1 5\n2 1 1\n1 2 7\n2 2 4\n"),result);assertFalse(Files.exists(dir.resolve("canonical/sort")));
        double[] changed=s.cellTotals();changed[0]=999;assertEquals(6,s.cellTotals()[0]);
    }
    @Test void gzipInputsFractionalEstimatesAndZeroEntriesAreExplicit()throws Exception{
        Path f=write("features.tsv","G1\n"),c=write("barcodes.tsv","C1\n");String text="%%MatrixMarket matrix coordinate real general\n1 1 2\n1 1 0.25\n1 1 0.5\n";Path m=dir.resolve("matrix.mtx.gz");
        try(var out=new GZIPOutputStream(Files.newOutputStream(m))){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        assertThrows(IOException.class,()->SparseCounts.canonicalize(m,f,c,dir.resolve("raw-fail"),SparseCounts.Kind.RAW_UMI,null,2));
        var s=SparseCounts.canonicalize(m,f,c,dir.resolve("estimates"),SparseCounts.Kind.NONNEGATIVE_ESTIMATE,null,2);assertEquals(.75,s.cellTotals()[0]);assertEquals(1,s.detectedGenes()[0]);assertEquals(1,s.outputEntries());
    }
    @Test void invalidDimensionsValuesDuplicatesAndDeclaredEntryCountsFail()throws Exception{
        Path f=write("features.tsv","G1\n"),c=write("barcodes.tsv","C1\n");int n=0;
        for(String text:new String[]{"%%MatrixMarket matrix coordinate integer general\n2 1 1\n1 1 2\n","%%MatrixMarket matrix coordinate integer general\n1 1 2\n1 1 2\n","%%MatrixMarket matrix coordinate real general\n1 1 1\n1 1 NaN\n","%%MatrixMarket matrix coordinate integer general\n1 1 1\n2 1 1\n","%%MatrixMarket matrix coordinate integer general\n1 1 2\n1 1 9007199254740991\n1 1 1\n"}){
            Path m=write("bad"+(n)+".mtx",text),target=dir.resolve("failure"+(n++));assertThrows(IOException.class,()->SparseCounts.canonicalize(m,f,c,target,SparseCounts.Kind.RAW_UMI,null,1));
        }
        Files.writeString(c,"C1\nC1\n");Path m=write("a.mtx","%%MatrixMarket matrix coordinate integer general\n1 2 0\n");assertThrows(IOException.class,()->SparseCounts.canonicalize(m,f,c,dir.resolve("duplicate-cells"),SparseCounts.Kind.RAW_UMI,null,1));
    }
    @Test void allZeroSparseMatrixRetainsEveryDeclaredCell()throws Exception{
        Path f=write("features.tsv","G1\n"),c=write("barcodes.tsv","C1\nC2\n"),m=write("m.mtx","%%MatrixMarket matrix coordinate integer general\n1 2 0\n");
        var s=SparseCounts.canonicalize(m,f,c,dir.resolve("zero"),SparseCounts.Kind.RAW_UMI,null,1);assertArrayEquals(new double[]{0,0},s.cellTotals());assertEquals(0,s.outputEntries());assertEquals(2,s.cells().size());
    }
}
