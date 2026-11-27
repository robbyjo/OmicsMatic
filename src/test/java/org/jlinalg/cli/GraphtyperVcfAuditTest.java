package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.GraphtyperVcfAudit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import static org.junit.jupiter.api.Assertions.*;
final class GraphtyperVcfAuditTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path root;
    private static final String HEADER="##fileformat=VCFv4.2\n##fileDate=20261127\n##INFO=<ID=AC,Number=A,Type=Integer,Description=\"Original, unmodified counts\">\n##INFO=<ID=AN,Number=1,Type=Integer,Description=\"Number\">\n##INFO=<ID=AAScore,Number=A,Type=Float,Description=\"Rounded source score\">\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tA\tB\n";
    private static final String DATA="20\t10\t.\tA\tC,G\t42\tPASS\tAC=2,1;AN=4;AAScore=0.5,0.9\tGT\t1|2\t0/1\n20\t11\t.\tA\t*\t.\t.\tAC=0;AN=1;AAScore=.\tGT\t./.\t0/.\n";
    private static GraphtyperVcfAudit.Limits limits(){return new GraphtyperVcfAudit.Limits(100000,100,100,10000,100,10,100);}
    private Path input(String name,String value)throws IOException {Path p=root.resolve(name);Files.writeString(p,value,StandardCharsets.UTF_8);return p;}
    private GraphtyperVcfAudit.Result audit(Path input,String out)throws IOException{return GraphtyperVcfAudit.audit(input,Set.of("A","B"),root.resolve(out),limits(),()->{});}
    @Test void allLiteralAllelesMissingCallsAndPhaseAreAccounted()throws Exception {
        var result=audit(input("source.vcf",HEADER+DATA),"audit.tsv");assertEquals(2,result.records());assertEquals(4,result.genotypeCells());assertEquals(List.of("A","B"),result.sampleIds());
        assertEquals(new GraphtyperVcfAudit.SampleCount("A",1,1,0,1,2,2),result.sampleCounts().get(0));assertEquals(new GraphtyperVcfAudit.SampleCount("B",1,0,1,0,3,1),result.sampleCounts().get(1));
        var rows=Files.readAllLines(root.resolve("audit.tsv"));assertEquals("1,2,1",rows.get(1).split("\t")[9]);assertEquals("1,0",rows.get(2).split("\t")[9]);assertEquals(List.of("##fileDate=20261127"),result.retainedClockHeader());assertTrue(result.canonicalHeader().contains("##fileformat=VCFv4.2"));assertThrows(UnsupportedOperationException.class,()->result.sampleIds().add("NEW"));
    }
    @Test void completeDataDigestCoversUninterpretedSourceFieldsAndGzip()throws Exception {
        Path plain=input("a.vcf",HEADER+DATA);var a=audit(plain,"a.tsv");Path gzip=root.resolve("b.vcf.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(gzip))){out.write((HEADER+DATA).getBytes(StandardCharsets.UTF_8));}var b=audit(gzip,"b.tsv");assertEquals(a.allDataFieldsSha256(),b.allDataFieldsSha256());assertArrayEquals(Files.readAllBytes(root.resolve("a.tsv")),Files.readAllBytes(root.resolve("b.tsv")));
        var changed=audit(input("c.vcf",HEADER+DATA.replace("\tPASS\t","\tLOGF\t")),"c.tsv");assertNotEquals(a.allDataFieldsSha256(),changed.allDataFieldsSha256());
    }
    @Test void sampleOrGenotypeIdentityChangesCannotPass()throws Exception {
        Path source=input("a.vcf",HEADER+DATA);assertThrows(IOException.class,()->GraphtyperVcfAudit.audit(source,Set.of("A","OTHER"),root.resolve("wrong.tsv"),limits(),()->{}));
        assertThrows(IOException.class,()->audit(input("allele.vcf",HEADER+DATA.replace("1|2","1|3")),"allele.tsv"));assertThrows(IOException.class,()->audit(input("separator.vcf",HEADER+DATA.replace("1|2","1|2/0")),"separator.tsv"));
    }
    @Test void nonfiniteOriginalQualityIsRetainedAsFailureWithoutSubstitution()throws Exception {
        for(String invalid:List.of("NaN","Infinity","-Infinity")){
            String stem=invalid.replace("-","negative");assertThrows(IOException.class,()->audit(input(stem+".vcf",HEADER+DATA.replace("0.5,0.9",invalid+",0.9")),stem+".tsv"));
        }
    }
    @Test void capacitiesAndCancellationDoNotAdmitPartialRecords()throws Exception {
        Path source=input("a.vcf",HEADER+DATA);var small=new GraphtyperVcfAudit.Limits(100000,1,100,10000,100,10,100);assertThrows(IOException.class,()->GraphtyperVcfAudit.audit(source,Set.of("A","B"),root.resolve("cap.tsv"),small,()->{}));
        assertThrows(CancellationException.class,()->GraphtyperVcfAudit.audit(source,Set.of("A","B"),root.resolve("cancel.tsv"),limits(),()->{throw new CancellationException("cancel");}));
    }
    @Test void sitesOnlyPopulationAndExistingOutputAreExplicit()throws Exception {
        String text="##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n20\t3\t.\tA\tC\t.\t.\t.\n";Path source=input("sites.vcf",text);var out=root.resolve("sites.tsv");var result=GraphtyperVcfAudit.audit(source,Set.of(),out,limits(),()->{});assertEquals(1,result.records());assertEquals(0,result.genotypeCells());assertTrue(result.sampleIds().isEmpty());assertThrows(IOException.class,()->GraphtyperVcfAudit.audit(source,Set.of(),out,limits(),()->{}));
    }
}
