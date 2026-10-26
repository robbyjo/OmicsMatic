package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import htsjdk.samtools.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class HisatToolsCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path dir;
    private String output;
    private String home(){String value=System.getenv("JLINALG_HISAT2_HOME");assumeTrue(value!=null,"Set JLINALG_HISAT2_HOME for real HISAT2 tests");return value;}
    private int run(String name,String method,String... extra)throws Exception{
        var args=new ArrayList<>(List.of("hisat-tools","--method",method,"--out",dir.resolve(name).toString(),"--no-config"));args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();
        for(String f:List.of("WARNINGS.md","alignment.log","index.log","version.log"))if(code!=0&&Files.exists(dir.resolve(name).resolve(f)))output+=Files.readString(dir.resolve(name).resolve(f));return code;
    }
    private String dna(){var random=new Random(626);var b=new StringBuilder();for(int i=0;i<4000;i++)b.append("ACGT".charAt(random.nextInt(4)));b.replace(400,402,"GT");b.replace(598,600,"AG");return b.toString();}
    private Path fasta()throws Exception{Path p=dir.resolve("reference with spaces.fa");Files.writeString(p,">chr1\n"+dna()+"\n");return p;}
    private Path annotation()throws Exception{Path p=dir.resolve("genes.gtf");Files.writeString(p,"chr1\tt\texon\t201\t400\t.\t+\t.\tgene_id \"G1\"; transcript_id \"T1\";\nchr1\tt\texon\t601\t900\t.\t+\t.\tgene_id \"G1\"; transcript_id \"T1\";\n");return p;}
    private Path index(String name,boolean annotated,String mode)throws Exception{
        var args=new ArrayList<>(List.of("--home",home(),"--reference",fasta().toString(),"--index-mode",mode));if(annotated)args.addAll(List.of("--annotation",annotation().toString()));assertEquals(0,run(name,"index",args.toArray(String[]::new)),output);return dir.resolve(name);
    }
    private Path reads(String name,int mate,String... sequences)throws Exception{Path p=dir.resolve(name);try(var w=new FastqIO.Writer(p)){for(int i=0;i<sequences.length;i++)w.write(new FastqIO.Read("@r"+i+(mate==0?"":"/"+mate),sequences[i],"I".repeat(sequences[i].length())));}return p;}
    private List<SAMRecord> sam(String name)throws Exception{try(var r=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(dir.resolve(name+"/alignment.sam"));var it=r.iterator()){var rows=new ArrayList<SAMRecord>();while(it.hasNext())rows.add(it.next());return rows;}}
    private int align(String name,Path index,Path reads,String... extra)throws Exception{var args=new ArrayList<>(List.of("--home",home(),"--index",index.toString(),"--reads",reads.toString(),"--sample","S"));args.addAll(List.of(extra));return run(name,"align",args.toArray(String[]::new));}
    @Test void annotatedGraphAndGzipRnaRecoverExactSpliceAndUnmappedRead()throws Exception{
        Path index=index("annotated",true,"small"),q=reads("reads.fq.gz",0,(dna().substring(350,400)+dna().substring(600,650)).toLowerCase(java.util.Locale.ROOT),MinimapToolsCli.reverse(dna().substring(650,750)),"N".repeat(100));
        assertEquals("chr1\t399\t600\t+\n",Files.readString(index.resolve("known-splicesites.tsv")));
        assertEquals(0,align("rna",index,q,"--max-intron","15000","--dta","true","--novel-splice-learning","false"),output);
        var records=sam("rna");assertEquals(3,records.size());assertEquals(351,records.get(0).getAlignmentStart());assertEquals("50M200N50M",records.get(0).getCigarString());assertTrue(records.get(1).getReadNegativeStrandFlag());assertTrue(records.get(2).getReadUnmappedFlag());assertEquals("S",records.get(0).getReadGroup().getSample());assertNotNull(records.get(0).getAttribute("NH"));
    }
    @Test void largeIndexFamilyAndLinearKnownSpliceListAreAudited()throws Exception{
        Path index=index("large",false,"large"),q=reads("reads.fq",0,dna().substring(350,400)+dna().substring(600,650));assertTrue(Files.exists(index.resolve("reference.1.ht2l")));
        Path sites=dir.resolve("sites.tsv");Files.writeString(sites,"chr1\t399\t600\t+\n");assertEquals(0,align("known",index,q,"--known-splicesites",sites.toString(),"--strand","F","--min-intron","20","--max-intron","40000"),output);assertEquals("50M200N50M",sam("known").get(0).getCigarString());
        Files.writeString(sites,"chr1\t399\t4000\t+\n");assertEquals(2,align("wrong-site",index,q,"--known-splicesites",sites.toString()),output);assertFalse(Files.exists(dir.resolve("wrong-site/alignment.log")));assertTrue(output.contains("exonic-flank"));
    }
    @Test void pairedStrandAndDirectInputStagePreserveEveryMate()throws Exception{
        Path index=index("linear",false,"small"),a=reads("R1.fq",1,dna().substring(1000,1100),"N".repeat(100)),b=reads("R2.fq",2,MinimapToolsCli.reverse(dna().substring(1200,1300)),"N".repeat(100));
        assertEquals(0,align("paired",index,a,"--reads2",b.toString(),"--strand","RF","--dta","true","--library","LIB"),output);var rows=sam("paired");assertEquals(4,rows.size());assertEquals(2,rows.stream().filter(SAMRecord::getFirstOfPairFlag).count());assertEquals(2,rows.stream().filter(SAMRecord::getReadUnmappedFlag).count());assertEquals("LIB",rows.get(0).getReadGroup().getLibrary());
    }
    @Test void indexCorruptionAndPairMismatchFailWithoutPublishingResults()throws Exception{
        Path index=index("linear",false,"small"),a=reads("R1.fq",1,dna().substring(1000,1100)),b=reads("R2.fq",2,MinimapToolsCli.reverse(dna().substring(1200,1300)));
        Files.writeString(b,Files.readString(b).replace("@r0/2","@different/2"));assertEquals(2,align("bad-pair",index,a,"--reads2",b.toString()),output);assertTrue(output.contains("pair count/identity/order"));assertFalse(Files.exists(dir.resolve("bad-pair/alignment.log")));
        Files.writeString(index.resolve("reference.1.ht2"),"changed");assertEquals(2,align("corrupt",index,a),output);assertTrue(output.contains("changed during analysis"));assertFalse(Files.exists(dir.resolve("corrupt/results.tsv")));
    }
    @Test void unsplicedAndZeroMappedModesRemainExplicit()throws Exception{
        Path index=index("linear",false,"small"),a=reads("unmapped.fq",0,"N".repeat(100));
        assertEquals(0,align("unspliced",index,a,"--spliced","false","--softclip","false","--score-slope","-0.1"),output);assertTrue(sam("unspliced").get(0).getReadUnmappedFlag());assertTrue(Files.readString(dir.resolve("unspliced/WARNINGS.md")).contains("ZERO MAPPED"));
    }
    @Test void unavailableDependencySkipsAndWrongStrandFailsEarly()throws Exception{
        assertEquals(3,run("skip","index","--home",dir.resolve("absent").toString(),"--reference","absent"),output);assertTrue(Files.readString(dir.resolve("skip/WARNINGS.md")).contains("install_rna_aligners.py --profile hisat2"));
        assertEquals(2,run("bad","align","--home","absent","--index","absent","--reads","absent","--sample","S","--strand","RF"),output);assertFalse(Files.exists(dir.resolve("bad")));
    }
}
