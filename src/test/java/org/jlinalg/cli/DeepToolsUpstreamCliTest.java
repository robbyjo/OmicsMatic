package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DeepToolsUpstreamCliTest {
    @TempDir Path directory;
    private String message;
    private Path file(String name,String contents)throws Exception {Path path=directory.resolve(name);Files.writeString(path,contents);return path;}
    private int run(String name,String method,Path input,Path sizes,String... extra)throws Exception {
        var args=new ArrayList<>(List.of("deep-tools","--method",method,"--input",input.toString(),"--sizes",sizes.toString(),"--genome-build","SYNTHETIC","--out",directory.resolve(name).toString(),"--no-config"));args.addAll(List.of(extra));var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);message=bytes.toString();Path warning=directory.resolve(name+"/WARNINGS.md");if(Files.exists(warning))message+=Files.readString(warning);return code;
    }
    private String home(){String value=System.getenv("JLINALG_DEEPTOOLS_HOME");Assumptions.assumeTrue(value!=null&&!value.isBlank(),"Original sealed deepTools runtime not configured");return value;}
    private Path bam(String name,boolean paired,boolean complex)throws Exception {
        var header=new SAMFileHeader();header.setSortOrder(SAMFileHeader.SortOrder.coordinate);header.addSequence(new SAMSequenceRecord("chr1",1000));header.addReadGroup(new SAMReadGroupRecord("original-group"));Path path=directory.resolve(name);
        try(var writer=new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header,true,path.toFile())){
            int[] positions=paired?new int[]{101,131}:new int[]{1,6,21};
            for(int i=0;i<positions.length;i++){var read=new SAMRecord(header);read.setReadName(paired?"pair":"read"+i);read.setReadString("AAAAAAAAAA");read.setBaseQualityString("IIIIIIIIII");read.setReferenceName("chr1");read.setAlignmentStart(positions[i]);read.setMappingQuality(40);read.setCigarString(complex?"5M1D5M":"10M");read.setAttribute("RG","original-group");read.setAttribute("CB","cell-1");read.setAttribute("RX","umi-1");read.setAttribute("NM",0);
                if(paired){read.setFlags(i==0?99:147);read.setMateReferenceName("chr1");read.setMateAlignmentStart(positions[1-i]);read.setInferredInsertSize(i==0?40:-40);}writer.addAlignment(read);}
        }return path;
    }
    private Path resource(String name)throws Exception {Path target=directory.resolve(name);try(var stream=getClass().getResourceAsStream("/deeptools/"+name)){assertNotNull(stream);Files.copy(stream,target);}return target;}
    private double[] dense(String output)throws Exception {double[] values=new double[1000];Arrays.fill(values,Double.NaN);for(String line:Files.readAllLines(directory.resolve(output+"/signal.bedgraph"))){String[] f=line.split("\t");Arrays.fill(values,Integer.parseInt(f[1]),Integer.parseInt(f[2]),Double.parseDouble(f[3]));}return values;}
    @Test void dependencySkipPrecedesInputAccessAndHasCopyableRecovery()throws Exception {
        assertEquals(3,run("skip","bam-coverage",directory.resolve("missing.bam"),directory.resolve("missing.sizes"),"--runtime-home",directory.resolve("absent-runtime").toString(),"--bin-size","10","--normalization","NONE","--exact-scaling","true","--uncovered","zero"),message);assertTrue(message.contains("ANALYSIS SKIPPED"));assertTrue(message.contains("--environment NEW_RUNTIME_HOME --allow-download"));assertTrue(message.contains("If installation is forbidden"));assertFalse(Files.exists(directory.resolve("skip/results.tsv")));assertEquals("skipped-dependency",RegenieRuntime.object(directory.resolve("skip/analysis-status.json")).get("status"));
    }
    @Test void typedOptionsRejectInapplicablePoliciesAndUnacknowledgedTagLoss()throws Exception {
        Path input=directory.resolve("input"),sizes=directory.resolve("sizes");assertEquals(2,run("wrong","atac-shift",input,sizes,"--cigar-policy","match-only"),message);assertTrue(message.contains("drops SEQ,QUAL"));assertFalse(Files.exists(directory.resolve("wrong")));
        assertEquals(2,run("mix","bam-coverage",input,sizes,"--input2",input.toString()),message);assertTrue(message.contains("does not apply"));
        assertEquals(2,run("missing","bigwig-compare",input,sizes,"--input2",input.toString(),"--bin-size","10","--operation","log2","--missing","retain","--invalid","error"),message);assertTrue(message.contains("requires invalid retain"));
    }
    @Test void changedOrUnmanagedRuntimeIsSkippedWithoutLaunchingPython()throws Exception {
        Path runtime=directory.resolve("unmanaged-runtime");Files.createDirectory(runtime);Files.writeString(runtime.resolve("keep.txt"),"preserve");assertEquals(3,run("unmanaged","bam-coverage",directory.resolve("input"),directory.resolve("sizes"),"--runtime-home",runtime.toString(),"--bin-size","10","--normalization","NONE","--exact-scaling","true","--uncovered","zero"),message);assertEquals("preserve",Files.readString(runtime.resolve("keep.txt")));assertTrue(message.contains("DEPENDENCY"));
        String lock;try(var stream=getClass().getResourceAsStream("/org/jlinalg/omics/deeptools-runtime-lock.json")){assertNotNull(stream);lock=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}Path sourceLock=file("lock.json",lock),changed=file("sealed.txt","changed");var profile=new LinkedHashMap<String,Object>();profile.put("schema","jlinalg-deeptools-runtime-v1");profile.put("kind","native");profile.put("version","3.5.6");profile.put("lock_sha256",FollowupSupport.hash(sourceLock));profile.put("files",Map.of(changed.toString(),"0".repeat(64)));Files.writeString(runtime.resolve(".jlinalg-deeptools.json"),RegenieRuntime.json(profile));assertEquals(3,run("tampered","bam-coverage",changed,changed,"--runtime-home",runtime.toString(),"--bin-size","10","--normalization","NONE","--exact-scaling","true","--uncovered","zero"),message);assertTrue(message.contains("changed during analysis"));
    }
    @Test void actualBamCoverageMatchesIndependentCountsAndCpmWithoutChangingInputs()throws Exception {
        String runtime=home();Path input=bam("reads with spaces.bam",false,false),sizes=file("sizes","chr1\t1000\n");String before=FollowupSupport.hash(input);
        assertEquals(0,run("coverage","bam-coverage",input,sizes,"--runtime-home",runtime,"--bin-size","10","--normalization","CPM","--exact-scaling","true","--uncovered","zero"),message);double[] values=dense("coverage");for(int i=0;i<1000;i++){double count=i<10?2:i<30?1:0;assertEquals(count*1e6/3,values[i],4,"base"+i);}assertEquals(before,FollowupSupport.hash(input));assertTrue(message.contains("BAM COVERAGE MODEL"));assertTrue(Files.readString(directory.resolve("coverage/runtime-probe-execution.json")).contains("probe-completed"));assertFalse(Files.exists(directory.resolve("coverage/partial")));
    }
    @Test void actualBigwigComparisonKeepsTerminalZeroAndRetainsUndefinedRatiosExplicitly()throws Exception {
        String runtime=home();Path first=resource("ramp.bw"),second=resource("two.bw"),sizes=file("sizes","chr1\t1000\n");
        assertEquals(0,run("difference","bigwig-compare",first,sizes,"--runtime-home",runtime,"--input2",second.toString(),"--bin-size","10","--operation","subtract","--scale1","0","--scale2","0","--missing","error","--invalid","error"),message);for(double value:dense("difference"))assertEquals(0,value);assertEquals(101,Files.readAllLines(directory.resolve("difference/results.tsv")).size());
        assertEquals(0,run("undefined","bigwig-compare",first,sizes,"--runtime-home",runtime,"--input2",second.toString(),"--bin-size","10","--operation","ratio","--scale1","0","--scale2","0","--missing","error","--invalid","retain"),message);assertEquals(0,Files.size(directory.resolve("undefined/signal.bedgraph")));assertTrue(Files.readString(directory.resolve("undefined/results.tsv")).contains("NA\tMISSING_OR_NONFINITE"));assertTrue(message.contains("No zero was invented"));
    }
    @Test void actualAtacShiftReplacesGeometryAndExplicitlyDropsOriginalTagsAndSequence()throws Exception {
        String runtime=home();Path input=bam("paired.bam",true,false),sizes=file("sizes","chr1\t1000\n");String before=FollowupSupport.hash(input);
        assertEquals(0,run("shift","atac-shift",input,sizes,"--runtime-home",runtime,"--accept-sequence-tag-loss","true","--cigar-policy","match-only"),message);
        try(var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(directory.resolve("shift/shifted.bam"))){var reads=new ArrayList<SAMRecord>();reader.forEach(reads::add);assertEquals(2,reads.size());assertEquals(105,reads.get(0).getAlignmentStart());assertEquals(110,reads.get(0).getAlignmentEnd());assertEquals(31,reads.get(0).getInferredInsertSize());assertEquals(131,reads.get(1).getAlignmentStart());assertEquals(135,reads.get(1).getAlignmentEnd());assertEquals(-31,reads.get(1).getInferredInsertSize());assertEquals(105,reads.get(1).getMateAlignmentStart());for(var read:reads){assertEquals("*",read.getReadString());assertEquals("*",read.getBaseQualityString());assertTrue(read.getAttributes().isEmpty());}}
        assertEquals(before,FollowupSupport.hash(input));assertTrue(message.contains("ALL optional SAM tags"));assertTrue(message.contains("sort and index"));
    }
    @Test void actualInputFailuresAreAuditableAndPublishNoSuccessfulOutputs()throws Exception {
        String runtime=home();Path input=bam("complex.bam",true,true),sizes=file("sizes","chr1\t1000\n");assertEquals(2,run("complex","atac-shift",input,sizes,"--runtime-home",runtime,"--accept-sequence-tag-loss","true","--cigar-policy","match-only"),message);assertTrue(message.contains("requires one M CIGAR"));assertFalse(Files.exists(directory.resolve("complex/shifted.bam")));assertTrue(Files.exists(directory.resolve("complex/partial/input-audit-execution.json")));
        Path wrong=file("wrong.sizes","chr1\t999\n");assertEquals(2,run("dictionary","bam-coverage",input,wrong,"--runtime-home",runtime,"--bin-size","10","--normalization","NONE","--exact-scaling","true","--uncovered","zero"),message);assertTrue(message.contains("dictionary differs"));assertFalse(Files.exists(directory.resolve("dictionary/results.tsv")));
    }
}
