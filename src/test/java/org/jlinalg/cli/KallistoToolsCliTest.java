package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KallistoToolsCliTest {
    @TempDir Path directory;private String console;
    private Path resource(String name)throws Exception{Path path=directory.resolve(name);if(!Files.exists(path))try(var input=getClass().getResourceAsStream("/kallisto/"+name)){assertNotNull(input);Files.copy(input,path);}return path;}
    private Path file(String name,String text)throws Exception{Path path=directory.resolve(name);Files.writeString(path,text);return path;}
    private int call(String name,String...options){var args=new ArrayList<>(List.of("kallisto-tools","--out",directory.resolve(name).toString(),"--no-config"));args.addAll(List.of(options));var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);console=bytes.toString();return code;}
    private String home(){String value=System.getenv("JLINALG_KALLISTO_HOME");Assumptions.assumeTrue(value!=null&&Files.isRegularFile(Path.of(value,"kallisto.exe")),"Set JLINALG_KALLISTO_HOME to the permitted verified original0.48.0 runtime");return value;}
    private Path index()throws Exception{assertEquals(0,call("index","--method","index","--home",home(),"--reference",resource("transcripts.fa").toString(),"--reference-id","synthetic-v1","--reference-kind","transcriptome","--reference-processing","strict","--kmer-size","31"),console);return directory.resolve("index/model.json");}
    private Path lanes(boolean paired)throws Exception{resource("read1.fastq.gz");resource("read2.fastq.gz");return file(paired?"paired.tsv":"single.tsv",paired?"lane\tread1\tread2\r\n001\tread1.fastq.gz\tread2.fastq.gz\r\n":"lane\tread1\n001\tread1.fastq.gz\n");}
    private int quant(String name,Path model,boolean paired,String strand,String bias,String...extra)throws Exception{var options=new ArrayList<>(List.of("--method","quant","--home",home(),"--index-model",model.toString(),"--lanes",lanes(paired).toString(),"--sample-id","001","--library",paired?"paired":"single","--strand",strand,"--fragment-length-mode","supplied","--fragment-mean","200","--fragment-sd","20","--bias",bias,"--bootstraps","0"));for(int i=0;i<extra.length;i+=2){int at=options.indexOf(extra[i]);if(at>=0)options.set(at+1,extra[i+1]);else{options.add(extra[i]);options.add(extra[i+1]);}}return call(name,options.toArray(String[]::new));}
    @Test void missingRuntimeSkipsWithExactRecoveryAndNoSuccessfulOutput()throws Exception {
        assertEquals(3,call("missing","--method","index","--home",directory.resolve("absent").toString(),"--reference",resource("transcripts.fa").toString(),"--reference-id","fixture","--reference-kind","transcriptome","--reference-processing","strict","--kmer-size","31"),console);assertFalse(Files.exists(directory.resolve("missing/results.tsv")));assertTrue(console.contains("ANALYSIS SKIPPED"));String warnings=Files.readString(directory.resolve("missing/WARNINGS.md"));assertTrue(warnings.contains("install_kallisto.py --allow-download"));assertTrue(warnings.contains("Required action"));
    }
    @Test void optionsRejectIgnoredOrInapplicableChoicesAndHelpNamesFormatLimitation() {
        assertEquals(2,call("bad","--method","index","--bias","false"),console);assertTrue(console.contains("does not apply"));
        assertEquals(0,call("help","--help"),console);assertTrue(console.contains("does not emit HDF5"));assertTrue(console.contains("no automatic installation"));
    }
    @Test void actualIndexPairedCountsAndEverySeededBootstrapMatchOriginal()throws Exception {
        Path model=index();assertEquals(0,quant("quant",model,true,"unstranded","false","--bootstraps","3","--seed","17"),console);var table=DelimitedData.read(directory.resolve("quant/results.tsv"));assertEquals("001",table.rows().get(0)[0]);assertEquals("001",table.rows().get(0)[5]);assertEquals(40,Double.parseDouble(table.rows().get(0)[3]));assertEquals(120,Double.parseDouble(table.rows().get(1)[3]));
        for(int i=0;i<3;i++)assertEquals(Files.readString(resource("bootstrap17-draw"+i+".tsv")),Files.readString(directory.resolve("quant/upstream/bs_abundance_"+i+".tsv")));
        assertEquals(4,Files.readAllLines(directory.resolve("quant/bootstrap-inventory.tsv")).size());assertTrue(Files.readString(directory.resolve("quant/session.yaml")).contains("input_records_or_pairs: 170"));assertFalse(Files.exists(directory.resolve("quant/upstream/abundance.h5")));assertTrue(Files.readString(directory.resolve("quant/WARNINGS.md")).contains("does not produce HDF5"));
        assertThrows(IOException.class,()->SimpleJson.parse(Files.readString(directory.resolve("quant/upstream/run_info.json"))));assertEquals(170L,RegenieRuntime.object(directory.resolve("quant/run-info-audited.json")).get("n_processed"));assertTrue(Files.readString(directory.resolve("quant/WARNINGS.md")).contains("original upstream/run_info.json"));
    }
    @Test void singleAndBiasModesRetainActualOriginalEffectiveLengthsAndCounters()throws Exception {
        Path model=index();assertEquals(0,quant("single",model,false,"fr","false","--single-overhang","true"),console);assertEquals(0,quant("bias",model,true,"unstranded","true"),console);assertEquals(Files.readString(resource("bias.tsv")),Files.readString(directory.resolve("bias/upstream/abundance.tsv")));assertTrue(Files.readString(directory.resolve("single/WARNINGS.md")).contains("one biological sample"));
    }
    @Test void wrongStrandAndChangedIndexFailAndPreserveUpstreamDiagnostics()throws Exception {
        Path model=index();assertEquals(2,quant("wrong-strand",model,true,"rf","false"),console);assertFalse(Files.exists(directory.resolve("wrong-strand/results.tsv")));assertTrue(Files.exists(directory.resolve("wrong-strand/partial/upstream/run_info.json")));assertTrue(Files.readString(directory.resolve("wrong-strand/quant.log")).contains("0 reads pseudoaligned"));
        Files.writeString(directory.resolve("index/targets.tsv"),"changed");assertEquals(2,quant("changed",model,true,"unstranded","false"),console);assertTrue(Files.readString(directory.resolve("changed/WARNINGS.md")).contains("member changed"));
    }
    @Test void pairMismatchAndResourceBoundsFailBeforePublishingCounts()throws Exception {
        Path model=index();assertEquals(2,quant("cells",model,true,"unstranded","false","--max-cells","2"),console);assertTrue(Files.readString(directory.resolve("cells/WARNINGS.md")).contains("max-cells"));assertEquals(2,quant("records",model,true,"unstranded","false","--max-records","2"),console);
        Path mate=file("bad-mate.fastq","@different/2\nACGT\n+\nIIII\n");Path lane=file("mismatch.tsv","lane\tread1\tread2\n1\tread1.fastq.gz\t"+mate.getFileName()+"\n");assertEquals(2,quant("pairs",model,true,"unstranded","false","--lanes",lane.toString()),console);assertTrue(Files.readString(directory.resolve("pairs/WARNINGS.md")).contains("mate identity"));
    }
    @Test void referenceProcessingIsAnExplicitAuditedChoice()throws Exception {
        Path reference=file("ambiguous.fa",">001 desc\nACGTNNNUGTC\n>tail\nCGTACGTAAAAAAAAAA\n");String[] args={"--method","index","--home",home(),"--reference",reference.toString(),"--reference-id","ambiguous-v1","--reference-kind","contigs","--reference-processing","strict","--kmer-size","3"};assertEquals(2,call("strict",args),console);args[11]="upstream";assertEquals(0,call("upstream",args),console);String warnings=Files.readString(directory.resolve("upstream/WARNINGS.md"));assertTrue(warnings.contains("pseudorandom"));assertTrue(warnings.contains("RAW lengths"));
    }
}
