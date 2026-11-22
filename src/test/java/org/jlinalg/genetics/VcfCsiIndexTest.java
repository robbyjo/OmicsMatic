package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VcfCsiIndexTest {
    @TempDir Path directory;
    private static final String HEADER="##fileformat=VCFv4.2\n##contig=<ID=1,length=536870913>\n##contig=<ID=2,length=200000>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
    private static String row(String contig,int position){return contig+"\t"+position+"\t.\tA\tG\t.\t.\t.\n";}
    private Path bgzf(String text)throws IOException {Path path=Files.createTempFile(directory,"input-",".vcf.gz");try(var out=new BlockCompressedOutputStream(path.toFile())){out.write(text.getBytes(StandardCharsets.UTF_8));}return path;}
    private Path output(){return directory.resolve("new.csi");}
    @Test void specHeaderAndFinalChunkExcludeEmptyEofBlock()throws Exception {
        Path input=bgzf(HEADER+row("1",1)+row("1",16384)+row("1",16385));byte[] before=Files.readAllBytes(input);
        var inventory=VcfCsiIndex.create(input,output());assertEquals(1,inventory.contigs());assertEquals(2,inventory.bins());assertArrayEquals(before,Files.readAllBytes(input));
        try(var zip=new GZIPInputStream(Files.newInputStream(output()))){var b=ByteBuffer.wrap(zip.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);assertEquals('C',b.get());assertEquals('S',b.get());assertEquals('I',b.get());assertEquals(1,b.get());assertEquals(14,b.getInt());assertEquals(5,b.getInt());int aux=b.getInt();assertEquals(30,aux);assertEquals(2,b.getInt());assertEquals(1,b.getInt());assertEquals(2,b.getInt());assertEquals(0,b.getInt());assertEquals('#',b.getInt());assertEquals(0,b.getInt());assertEquals(2,b.getInt());assertEquals('1',b.get());assertEquals(0,b.get());assertEquals(1,b.getInt());int n=b.getInt();long last=0;for(int i=0;i<n;i++){int id=b.getInt();assertTrue(id==4681||id==4682);long offset=b.getLong();int chunks=b.getInt();for(int j=0;j<chunks;j++){long start=b.getLong(),end=b.getLong();assertTrue(start<=end);assertTrue(offset<=start);last=Math.max(last,end);}}assertEquals((Files.size(input)-28)<<16,last);assertEquals(0,b.getLong());assertFalse(b.hasRemaining());}
    }
    @Test void multipleContigsAreIndexedInObservedOrder()throws Exception {var s=VcfCsiIndex.create(bgzf(HEADER+row("2",15)+row("1",1)),output());assertEquals(2,s.contigs());assertEquals(2,s.bins());}
    @Test void existingDestinationIsPreserved()throws Exception {Files.writeString(output(),"immutable");assertThrows(IOException.class,()->VcfCsiIndex.create(bgzf(HEADER+row("1",1)),output()));assertEquals("immutable",Files.readString(output()));}
    @Test void emptyInputProducesNoIndexOrTemporary()throws Exception {assertThrows(IOException.class,()->VcfCsiIndex.create(bgzf(HEADER),output()));assertFalse(Files.exists(output()));try(var files=Files.list(directory)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".vcf-csi-")));}}
    @Test void plainAndOrdinaryGzipAreRejected()throws Exception {Path plain=directory.resolve("plain.vcf");Files.writeString(plain,HEADER+row("1",1));assertThrows(IOException.class,()->VcfCsiIndex.create(plain,output()));Path gzip=directory.resolve("ordinary.vcf.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(gzip))){out.write((HEADER+row("1",1)).getBytes(StandardCharsets.UTF_8));}assertThrows(IOException.class,()->VcfCsiIndex.create(gzip,output()));assertFalse(Files.exists(output()));}
    @Test void missingEofTerminatorIsRejected()throws Exception {Path input=bgzf(HEADER+row("1",1));byte[] bytes=Files.readAllBytes(input);Files.write(input,java.util.Arrays.copyOf(bytes,bytes.length-28));assertThrows(IOException.class,()->VcfCsiIndex.create(input,output()));assertFalse(Files.exists(output()));}
    @Test void unsortedPositionsAndRepeatedContigsAreRejected()throws Exception {assertThrows(IOException.class,()->VcfCsiIndex.create(bgzf(HEADER+row("1",10)+row("1",1)),output()));assertThrows(IOException.class,()->VcfCsiIndex.create(bgzf(HEADER+row("1",1)+row("2",1)+row("1",10)),output()));assertFalse(Files.exists(output()));}
    @Test void coordinateBeyondTabixCapacityIsRejected()throws Exception {assertThrows(IOException.class,()->VcfCsiIndex.create(bgzf(HEADER+row("1",536870913)),output()));assertFalse(Files.exists(output()));}
    @Test void lastAdmittedCoordinateIsNotTruncated()throws Exception {assertEquals(1,VcfCsiIndex.create(bgzf(HEADER+row("1",536870912)),output()).bins());}
}
