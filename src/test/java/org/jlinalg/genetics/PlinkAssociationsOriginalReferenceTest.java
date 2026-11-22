package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.genetics.PlinkAssociations.*;

class PlinkAssociationsOriginalReferenceTest {
    @TempDir Path directory;
    private Path unpack(String name)throws Exception {
        Path path=directory.resolve(name.replace(".gz",""));
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/genetics/plink-associations/"+name)) {
            assertNotNull(raw);try(var zipped=new GZIPInputStream(raw)){Files.copy(zipped,path);}
        }return path;
    }
    @Test void all768ActualOriginalRowsRetainAlleleMeaningAndErrorReportability()throws Exception {
        var alleles=new LinkedHashMap<String,Alleles>();
        for(String line:Files.readAllLines(unpack("variants.tsv.gz")).subList(1,97)) {
            String[] fields=line.split("\t");alleles.put(fields[0],new Alleles(fields[1],fields[2]));
        }
        Object[][] cases={{"plink1-linear",Engine.PLINK1,Model.LINEAR},{"plink1-logistic",Engine.PLINK1,Model.LOGISTIC},
            {"plink2-linear",Engine.PLINK2,Model.LINEAR},{"plink2-logistic",Engine.PLINK2,Model.LOGISTIC},{"plink2-firth-fallback",Engine.PLINK2,Model.FIRTH_FALLBACK},
            {"separated-no-firth",Engine.PLINK2,Model.LOGISTIC},{"separated-firth-fallback",Engine.PLINK2,Model.FIRTH_FALLBACK},{"separated-firth",Engine.PLINK2,Model.FIRTH}};
        long total=0;int finiteSuppressed=0;
        for(Object[] fixture:cases) {
            var values=new ArrayList<Row>();Counts counts=scan(unpack(fixture[0]+".tsv.gz"),(Engine)fixture[1],(Model)fixture[2],alleles,128,Limits.defaults(),values::add);
            assertEquals(96,counts.rows());assertEquals(96,values.size());total+=counts.rows();
            for(Row row:values) {
                assertEquals(128,row.observedSamples());Alleles pair=alleles.get(row.variant());assertNotNull(pair);
                assertEquals(row.effectIsBimA1()?pair.bimA1():pair.bimA2(),row.effectAllele());assertEquals(row.effectIsBimA1()?pair.bimA2():pair.bimA1(),row.otherAllele());
                if(!row.upstreamError().equals(".")) {
                    assertEquals(Status.UNREPORTABLE_UPSTREAM_ERROR,row.status());assertFalse(row.effectReportable());assertFalse(row.pReportable());assertTrue(Double.isNaN(row.beta()));assertTrue(Double.isNaN(row.pValue()));
                    if(row.upstreamError().equals("UNFINISHED")){assertTrue(Double.isFinite(Double.parseDouble(row.rawFields().get("OR"))));finiteSuppressed++;}
                } else {assertTrue(row.effectReportable());assertTrue(row.pReportable());}
            }
            if(fixture[0].equals("plink2-linear")) {
                assertEquals(40,values.stream().filter(r->!r.effectIsBimA1()).count());
                Row first=values.get(0);assertEquals(.382292,first.beta());assertEquals(.211202,first.standardError());assertEquals(.0727059,first.pValue());
            }
            if(fixture[0].equals("separated-firth-fallback")) {Row first=values.get(0);assertTrue(first.firth());assertEquals("UNFINISHED",first.upstreamError());assertEquals("2731.98",first.rawFields().get("OR"));}
        }
        assertEquals(768,total);assertTrue(finiteSuppressed>=2);
    }
}
