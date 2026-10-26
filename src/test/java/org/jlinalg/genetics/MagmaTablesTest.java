package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MagmaTablesTest {
    @TempDir Path dir;
    private static final Path DATA=Path.of("src/test/resources/magma");
    @Test void originalCurrentModelTablesPreserveProbabilitiesAndCoordinates()throws Exception{
        var a=GeneWindowMapping.assign(GeneWindowMapping.variants(DATA.resolve("snps.tsv"),false,false),GeneWindowMapping.genes(DATA.resolve("genes.tsv"),false),0,0,false);for(String model:List.of("summary-mean","summary-top","summary-multi","raw-linreg")){var t=MagmaTables.geneResults(DATA.resolve(model+".genes.out.txt"),a);assertEquals(201,t.rows().size());if(!model.equals("raw-linreg")){var r=t.rows().stream().filter(x->x.get("GENE").equals("SINGLE")).findFirst().orElseThrow();assertEquals(.05,Double.parseDouble(r.get(model.endsWith("multi")?"P_SNPWISE_MEAN":"P")),2e-6);if(!model.endsWith("multi"))assertEquals(1.6449,Double.parseDouble(r.get("ZSTAT")),1e-4);}}
    }
    @Test void exactPanelAndVariableSampleSizesAreValidated()throws Exception{
        var panel=MagmaTables.panel(DATA.resolve("reference"),false);assertEquals(160,panel.samples());assertEquals(995,panel.variants().size());var rows=MagmaTables.pvalues(DATA.resolve("gwas.tsv"),panel,false,null,false);assertEquals(995,rows.size());assertEquals("10000",rows.get(0).n());assertEquals("10100",rows.get(1).n());assertTrue(rows.stream().allMatch(r->r.status().equals("matched")));
    }
    @Test void mismatchedCoordinatesDuplicatesZeroPAndSilentNSubstitutionAreRejected()throws Exception{
        var panel=MagmaTables.panel(DATA.resolve("reference"),false);String head="variant_id\tchromosome\tposition\tp\tn\n";Path f=dir.resolve("p.tsv");for(String value:List.of("rs1\t1\t10026\t.05\t1000\n","rs1\t1\t10025\t0\t1000\n","rs1\t1\t10025\t.05\t1000\nrs1\t1\t10025\t.02\t1000\n","rs1\t1\t10025\t.05\tNA\n")){Files.writeString(f,head+value);assertThrows(java.io.IOException.class,()->MagmaTables.pvalues(f,panel,false,null,false));}assertThrows(java.io.IOException.class,()->MagmaTables.pvalues(DATA.resolve("gwas.tsv"),panel,false,1000L,false));
        Files.writeString(f,head+"rs1\t1\t10025\t.05\t1000\nabsent\t1\t10\t.3\t1000\n");assertThrows(java.io.IOException.class,()->MagmaTables.pvalues(f,panel,false,null,false));assertEquals("absent-reference",MagmaTables.pvalues(f,panel,false,null,true).get(1).status());
    }
    @Test void incompleteOrCorruptBedFamilyNeverReachesUpstream()throws Exception{
        Path prefix=dir.resolve("panel");for(String s:List.of("bed","bim","fam"))Files.copy(Path.of(DATA.resolve("reference")+"."+s),Path.of(prefix+"."+s));assertEquals(995,MagmaTables.panel(prefix,false).variants().size());Files.write(Path.of(prefix+".bed"),new byte[]{0x6c,0x1b,0});assertThrows(java.io.IOException.class,()->MagmaTables.panel(prefix,false));
    }
}
