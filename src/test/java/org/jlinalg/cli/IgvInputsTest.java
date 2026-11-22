package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.BedGraphSignals.Segment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IgvInputsTest {
    @TempDir Path root;
    private Map<String,Object> recipe()throws Exception {
        Path fa=root.resolve("ref.fa"),bg=root.resolve("signal.bg");Files.writeString(fa,">chr1\n"+"ACGT".repeat(250)+"\n");Files.writeString(bg,"chr1\t0\t100\t1.23456789\nchr1\t200\t300\t-2\n");var track=track("bedgraph",bg);track.put("units","arbitrary signed units");track.put("range",List.of(-3,4));track.put("window","none");var recipe=new LinkedHashMap<String,Object>();recipe.put("assembly","synthetic");recipe.put("reference",fa.toString());recipe.put("reference_index","generate");recipe.put("width",1200);recipe.put("height",850);recipe.put("sequence",true);recipe.put("views",List.of(Map.of("id","locus","contig","chr1","start",0,"end",600)));recipe.put("tracks",List.of(track));return recipe;
    }
    private static LinkedHashMap<String,Object> track(String kind,Path file){var row=new LinkedHashMap<String,Object>();row.put("id","data");row.put("kind",kind);row.put("file",file.toString());row.put("height",80);row.put("display","expanded");row.put("color",List.of(1,2,3));row.put("negative_color",List.of(4,5,6));return row;}
    private IgvInputs.Model read(Map<String,Object> recipe)throws Exception {Path file=root.resolve("recipe-"+UUID.randomUUID()+".json");Files.writeString(file,RegenieRuntime.json(recipe));return IgvInputs.read(file,Files.createDirectory(root.resolve("out-"+UUID.randomUUID())),10000000,1000);}
    @Test void unicodeStagingPreservesRawSourceAndAuditsFloat32Rounding()throws Exception {
        var r=recipe();r.put("assembly","合成 reference");var model=read(r);assertEquals(Map.of("chr1",1000L),model.dictionary());assertEquals((float)1.23456789,model.tracks().get(0).signal().get(0).value());assertTrue(model.warnings().stream().anyMatch(s->s.contains("FLOAT32")));model.verify();Files.writeString(model.tracks().get(0).file(),"changed");assertThrows(java.io.IOException.class,model::verify);
    }
    @Test void eachRequiredRootFieldMustBeExplicitAndUnknownFieldsFail()throws Exception {
        var r=recipe();for(String key:r.keySet()){var missing=new LinkedHashMap<>(r);missing.remove(key);assertThrows(Exception.class,()->read(missing),key);}r.put("infer_assembly",true);assertThrows(java.io.IOException.class,()->read(r));
    }
    @Test void duplicateIdsExactContigsAndHalfOpenBoundsAreEnforced()throws Exception {
        var r=recipe();for(var view:List.of(Map.of("id","v","contig","1","start",0,"end",600),Map.of("id","v","contig","chr1","start",600,"end",500),Map.of("id","v","contig","chr1","start",0,"end",1001),Map.of("id","v","contig","chr1","start",0,"end",10))){r.put("views",List.of(view));assertThrows(java.io.IOException.class,()->read(r));}var view=Map.of("id","v","contig","chr1","start",0,"end",600);r.put("views",List.of(view,view));assertThrows(java.io.IOException.class,()->read(r));
    }
    @Test void rawSignalRejectsOverlapsNonfiniteValuesAndUnsupportedReduction()throws Exception {
        var r=recipe();for(String text:List.of("chr1\t0\t100\tNaN\n","chr1\t0\t100\t1e99\n","chr1\t0\t100\t1\nchr1\t50\t150\t2\n","chr2\t0\t100\t2\n")){Files.writeString(root.resolve("signal.bg"),text);assertThrows(Exception.class,()->read(r));}Files.writeString(root.resolve("signal.bg"),"chr1\t0\t100\t2\n");var row=track("bedgraph",root.resolve("signal.bg"));row.put("units","counts");row.put("range",List.of(0,5));row.put("window","mean");r.put("tracks",List.of(row));assertThrows(java.io.IOException.class,()->read(r));
    }
    @Test void segOneBasedConversionRawModeAndPerSampleOverlapAreExplicit()throws Exception {
        var r=recipe();Path file=root.resolve("copy.seg");Files.writeString(file,"Sample\tChromosome\tStart\tEnd\tNum_Probes\tSegment_Mean\nS1\tchr1\t1\t100\t2\t2\nS2\tchr1\t1\t100\t2\t-1\n");var row=track("seg",file);row.put("units","already supplied log2 ratio");row.put("range",List.of(-3,3));row.put("coordinates","one-based-closed");row.put("mode","raw-supplied-units");r.put("tracks",List.of(row));var model=read(r);assertEquals(0,model.tracks().get(0).segments().get(0).start());assertEquals(100,model.tracks().get(0).segments().get(0).end());assertTrue(Files.readString(model.tracks().get(0).file()).startsWith("#type=OTHER\n"));assertFalse(model.warnings().isEmpty());row.put("coordinates","infer");assertThrows(java.io.IOException.class,()->read(r));row.put("coordinates","one-based-closed");Files.writeString(file,Files.readString(file)+"S1\tchr1\t50\t110\t1\t0\n");assertThrows(Exception.class,()->read(r));
    }
    @Test void bedNeedsSixColumnsValidScoresAndExplicitStrand()throws Exception {
        var r=recipe();Path file=root.resolve("features.bed");var row=track("bed6",file);r.put("tracks",List.of(row));Files.writeString(file,"chr1\t0\t100\tname\t1000\t.\n");assertEquals(1,read(r).tracks().get(0).features().size());for(String text:List.of("chr1\t0\t100\tname\n","chr1\t0\t100\tname\t1001\t+\n","chr1\t0\t100\tname\t10\tunknown\n")){Files.writeString(file,text);assertThrows(java.io.IOException.class,()->read(r));}
    }
    @Test void suppliedFastaIndexMustMatchActualOffsetsAndLineWidths()throws Exception {
        var r=recipe();r.put("reference_index","verify-supplied");Files.writeString(root.resolve("ref.fa.fai"),"chr1\t1000\t6\t1000\t1001\n");read(r);Files.writeString(root.resolve("ref.fa.fai"),"chr1\t1000\t5\t1000\t1001\n");assertThrows(java.io.IOException.class,()->read(r));
    }
    @Test void remoteSourcesAndInapplicableReadPoliciesFailBeforeRuntime()throws Exception {
        var r=recipe();r.put("reference","https://example.invalid/genome.fa");assertThrows(java.io.IOException.class,()->read(r));var second=recipe();second.put("reads",Map.of());assertThrows(java.io.IOException.class,()->read(second));
    }
    @Test void exactSignalFunctionsAcceptCacheDuplicatesAndSplitsButRejectZerosForGapsAndChangedValues()throws Exception {
        var view=new IgvInputs.View("v","chr1",0,100);var expected=List.of(new Segment("chr1",10,30,2));var observed=List.of(new Segment("chr1",10,20,2),new Segment("chr1",20,30,2),new Segment("chr1",20,30,2));assertEquals(80L,IgvAudit.signal(expected,observed,view).get("missing_bases_compared"));assertThrows(java.io.IOException.class,()->IgvAudit.signal(expected,List.of(new Segment("chr1",0,10,0),new Segment("chr1",10,30,2)),view));assertThrows(java.io.IOException.class,()->IgvAudit.signal(expected,List.of(new Segment("chr1",10,30,2.001)),view));assertThrows(java.io.IOException.class,()->IgvAudit.signal(expected,List.of(new Segment("chr1",10,25,2),new Segment("chr1",20,30,2)),view));
    }
    private Map<String,Object> bamRecipe()throws Exception {
        var recipe=recipe();Path bam=root.resolve("reads.bam");var header=new htsjdk.samtools.SAMFileHeader();header.addSequence(new htsjdk.samtools.SAMSequenceRecord("chr1",1000));header.setSortOrder(htsjdk.samtools.SAMFileHeader.SortOrder.coordinate);try(var writer=new htsjdk.samtools.SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header,true,bam)){var read=new htsjdk.samtools.SAMRecord(header);read.setReadName("fixture");read.setReferenceName("chr1");read.setAlignmentStart(1);read.setCigarString("20M");read.setReadString("ACGT".repeat(5));read.setBaseQualityString("I".repeat(20));read.setMappingQuality(60);writer.addAlignment(read);}var track=track("bam",bam);track.put("index",root.resolve("reads.bai").toString());track.put("range",List.of(0,20));recipe.put("tracks",List.of(track));var reads=new LinkedHashMap<String,Object>();reads.put("minimum_mapq",10);for(String flag:List.of("duplicates","failed","secondary","supplementary","soft_clips","pairs","junctions","deletions_in_coverage"))reads.put(flag,false);reads.put("color_by","none");reads.put("base_quality_policy","all-bases;quality-shading-only");reads.put("downsample",false);reads.put("overlapping_mates","count-each-alignment");recipe.put("reads",reads);return recipe;
    }
    @Test void everyBamSelectionChoiceMustBeExplicitIncludingOverlappingMates()throws Exception {
        var recipe=bamRecipe();var policies=new LinkedHashMap<String,Object>();for(var entry:((Map<?,?>)recipe.get("reads")).entrySet())policies.put(String.valueOf(entry.getKey()),entry.getValue());assertNotNull(read(recipe).reads());for(String key:policies.keySet()){var missing=new LinkedHashMap<>(policies);missing.remove(key);recipe.put("reads",missing);assertThrows(Exception.class,()->read(recipe),key);}var changed=new LinkedHashMap<>(policies);changed.put("downsample",true);recipe.put("reads",changed);assertThrows(java.io.IOException.class,()->read(recipe));changed.put("downsample",false);changed.put("overlapping_mates","infer");assertThrows(java.io.IOException.class,()->read(recipe));
    }
    @Test void bamDictionaryAndExplicitIndexMustMatchStagedReference()throws Exception {
        var recipe=bamRecipe();recipe.put("reference_index","generate");Files.writeString(root.resolve("ref.fa"),">other\n"+"ACGT".repeat(250)+"\n");recipe.put("views",List.of(Map.of("id","v","contig","other","start",0,"end",100)));assertThrows(java.io.IOException.class,()->read(recipe));
    }
}
