/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class Pal2NalToolsCliTest {
    @TempDir Path temporary;
    private record Run(int code,String out,String err) { }
    private static Run run(String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=Pal2NalToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    @SuppressWarnings("unchecked") private static Map<String,Object> recipe()throws IOException{return (Map<String,Object>)SimpleJson.parse(Pal2NalJson.json(Pal2NalToolsCli.example()));}
    @SuppressWarnings("unchecked") private static Map<String,Object> options(Map<String,Object> recipe){return (Map<String,Object>)recipe.get("options");}
    private Path request(Map<String,Object> recipe,String peptide,String dna)throws IOException {
        Files.writeString(temporary.resolve("protein.fasta"),peptide);Files.writeString(temporary.resolve("dna.fasta"),dna);Path path=temporary.resolve("request.json");Files.write(path,Pal2NalJson.bytes(recipe));return path;
    }
    private Path success()throws Exception {
        var recipe=recipe();Path request=request(recipe,">p1 first α\nMAK\n>p2 second\nMAK\n",">p2 second\nATGGCTAAA\n>p1 first α\nATGGCTAAA\n");Path out=temporary.resolve("complete");var run=run("--recipe",request.toString(),"--out",out.toString());assertEquals(0,run.code(),run.err());return out;
    }
    private static Map<String,byte[]> fixture()throws IOException {
        byte[] raw;try(var in=Pal2NalToolsCliTest.class.getResourceAsStream("/org/jlinalg/sequence/pal2nal-original/controls.zip")){assertNotNull(in);raw=in.readAllBytes();}
        var files=new HashMap<String,byte[]>();try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)files.put(entry.getName(),zip.readAllBytes());}return files;
    }
    private static String decode(String value){return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    private static Map<String,String> fasta(String text){var map=new LinkedHashMap<String,String>();String id=null;var sequence=new StringBuilder();for(String line:text.split("\\R")){if(line.startsWith(">")){if(id!=null)map.put(id,sequence.toString());id=line.substring(1);sequence.setLength(0);}else if(!line.isBlank()){assertNotNull(id);sequence.append(line);}}if(id!=null)map.put(id,sequence.toString());return map;}
    @TestFactory Stream<DynamicTest> allOriginalProtocolsThroughTheNativeApplication()throws Exception {
        var files=fixture();String[] cases=new String(files.get("index.tsv"),StandardCharsets.UTF_8).strip().split("\n");assertEquals(85,cases.length);
        return Stream.of(cases).map(line->{String[] f=line.split("\t",-1);return DynamicTest.dynamicTest(f[0],()->{
            Path home=temporary.resolve(f[0]);Files.createDirectory(home);Files.write(home.resolve("protein.aln"),files.get(f[0]+"/protein.aln"));Files.write(home.resolve("dna.fasta"),files.get(f[0]+"/dna.fasta"));
            var r=recipe();r.put("protein",Map.of("path","protein.aln","format",f[4].isEmpty()?"FASTA":"CLUSTAL","headers","FIRST_FIELD"));r.put("selection",Map.of("mode",f[4].isEmpty()?"NONE":"CLUSTAL_HASH"));var o=options(r);
            var flags=Set.of(f[3].split(",",-1));o.put("codon_table",Long.parseLong(f[1]));o.put("pairing",f[2]);o.put("matching","ALLOW_V14_RELAXED");o.put("placement","SOURCE_LEFTMOST");o.put("minimum_verified_residues",0);o.put("minimum_verified_fraction",0.0);o.put("gap_stop_filter",flags.contains("-nogap")?"V14_NOGAP":"KEEP");o.put("remove_mismatch_columns",flags.contains("-nomismatch"));o.put("normalize_documented_symbols",f[5].equals("documented-normalization-departure"));o.put("normalize_tfastx",f[0].startsWith("tfastx-"));
            Path request=home.resolve("request.json"),out=home.resolve("result");Files.write(request,Pal2NalJson.bytes(r));var run=run("--recipe",request.toString(),"--out",out.toString());
            if(f[5].equals("native-refusal")){assertEquals(2,run.code(),run.err());assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.exists(out.resolve("FAILURE.md")));return;}
            assertEquals(0,run.code(),run.err());var actual=fasta(Files.readString(out.resolve("alignment.fasta")));var expected=fasta(new String(files.get(f[0]+"/stdout"),StandardCharsets.UTF_8));
            if(f[5].equals("exact-rows"))assertEquals(expected,actual);else assertNotEquals(expected,actual);
            assertEquals(0,run("--verify",out.toString()).code());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));
        });});
    }
    @Test void completedBundlePreservesHeadersAndVerifiesAfterRelocation()throws Exception {
        Path root=success();assertEquals(0,run("--verify",root.toString()).code());String rows=Files.readString(root.resolve("rows.ndjson"));assertTrue(rows.contains("p1 first α"));
        Path relocated=temporary.resolve("relocated");Files.move(root,relocated);Files.delete(temporary.resolve("protein.fasta"));Files.delete(temporary.resolve("dna.fasta"));Files.delete(temporary.resolve("request.json"));assertEquals(0,run("--verify",relocated.toString()).code());
    }
    @Test void unavailableEditionCapabilityAndTableAreProminentSkips()throws Exception {
        int index=0;for(String what:List.of("profile","partial","code")){
            var r=recipe();if(what.equals("profile"))r.put("profile","native-pal2nal-v14.2");else if(what.equals("partial"))r.put("partial",true);else options(r).put("codon_table",24);
            Path request=request(r,">p1\nMAK\n",">p1\nATGGCTAAA\n");Path out=temporary.resolve("skip"+index++);var result=run("--recipe",request.toString(),"--out",out.toString());assertEquals(3,result.code(),result.err());assertTrue(result.err().contains("SKIP3"));assertTrue(result.err().contains("Required action"));assertFalse(Files.exists(out.resolve("COMPLETED.json")));
        }
    }
    @Test void unknownKeysAndLateMalformedUtf8AdmitNoPrefix()throws Exception {
        var r=recipe();r.put("discard_this",true);Path request=request(r,">p1\nMAK\n",">p1\nATGGCTAAA\n");assertEquals(2,run("--recipe",request.toString(),"--out",temporary.resolve("unknown").toString()).code());
        request=request(recipe(),">p1\nMAK\n",">p1\nATGGCTAAA\n");Files.write(temporary.resolve("dna.fasta"),new byte[]{(byte)0xc3},StandardOpenOption.APPEND);Path out=temporary.resolve("utf8");assertEquals(2,run("--recipe",request.toString(),"--out",out.toString()).code());assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.readAllBytes(out.resolve("inputs/dna-0001.fasta")).length>10);
    }
    @Test void separateDnaFilesAndCompleteMaskInputAreSupported()throws Exception {
        var r=recipe();r.put("dna",Map.of("paths",List.of("dna.fasta","dna2.fasta"),"headers","FIRST_FIELD"));r.put("selection",Map.of("mode","MASK_FILE","path","mask.txt"));
        Path request=request(r,">p1\nMAK\n>p2\nMAK\n",">p2\nATGGCTAAA\n");Files.writeString(temporary.resolve("dna2.fasta"),">p1\nATGGCTAAA\n");Files.writeString(temporary.resolve("mask.txt"),"# #\r\n");Path out=temporary.resolve("mask");var run=run("--recipe",request.toString(),"--out",out.toString());assertEquals(0,run.code(),run.err());assertEquals(Map.of("p1","ATGAAA","p2","ATGAAA"),fasta(Files.readString(out.resolve("alignment.fasta"))));
    }
    @Test void duplicateIdInLaterDnaFileAndTrailingMaskGarbageFail()throws Exception {
        var r=recipe();r.put("dna",Map.of("paths",List.of("dna.fasta","dna2.fasta"),"headers","FIRST_FIELD"));Path request=request(r,">p1\nMAK\n>p2\nMAK\n",">p1\nATGGCTAAA\n");Files.writeString(temporary.resolve("dna2.fasta"),">p1\nATGGCTAAA\n");assertEquals(2,run("--recipe",request.toString(),"--out",temporary.resolve("duplicates").toString()).code());
        r=recipe();r.put("selection",Map.of("mode","MASK_FILE","path","mask.txt"));request=request(r,">p1\nMAK\n",">p1\nATGGCTAAA\n");Files.writeString(temporary.resolve("mask.txt"),"###\nmore");assertEquals(2,run("--recipe",request.toString(),"--out",temporary.resolve("bad-mask").toString()).code());
    }
    @Test void emptyFilteredAlignmentSkipsDependentAnalysis()throws Exception {
        var r=recipe();options(r).put("gap_stop_filter","V14_NOGAP");options(r).put("minimum_verified_residues",0);Path request=request(r,">p1\n*\n",">p1\nTAA\n");Path out=temporary.resolve("empty");assertEquals(3,run("--recipe",request.toString(),"--out",out.toString()).code());assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void distinctV141MatchingProfileIsRetainedInTheResult()throws Exception {
        var r=recipe();r.put("profile","native-pal2nal-v14.1-matching");Path request=request(r,">p1\nMAK\n",">p1\nATGGCTAAA\n");Path out=temporary.resolve("v141");var result=run("--recipe",request.toString(),"--out",out.toString());assertEquals(0,result.code(),result.err());assertTrue(Files.readString(out.resolve("summary.json")).contains("native-pal2nal-v14.1-matching"));assertEquals(0,run("--verify",out.toString()).code());
    }
    @Test void maskCountsTowardTheCompleteDecodedInputBudget()throws Exception {
        var r=recipe();r.put("selection",Map.of("mode","MASK_FILE","path","mask.txt"));String protein=">p1\nMAK\n",dna=">p1\nATGGCTAAA\n";
        var p=org.jlinalg.sequence.MafftSequences.Limits.defaults();var limited=new org.jlinalg.sequence.MafftSequences.Limits(p.sequences(),p.sequenceCharacters(),p.cells(),protein.length()+dna.length()+3,p.lineCharacters(),p.headerCharacters(),p.estimatedRetainedBytes());
        r.put("limits",Map.of("input_bytes",268435456L,"parser",limited,"engine",org.jlinalg.sequence.Pal2Nal.Limits.defaults(),"bundle_bytes",1073741824L,"bundle_files",128));
        Path request=request(r,protein,dna);Files.writeString(temporary.resolve("mask.txt"),"###\n");Path out=temporary.resolve("mask-limit");var result=run("--recipe",request.toString(),"--out",out.toString());assertEquals(2,result.code(),result.err());assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void explicitOutputByteCapacityPreservesFailureEvidence()throws Exception {
        var r=recipe();r.put("limits",Map.of("input_bytes",268435456L,"parser",org.jlinalg.sequence.MafftSequences.Limits.defaults(),"engine",org.jlinalg.sequence.Pal2Nal.Limits.defaults(),"bundle_bytes",1048576L,"bundle_files",128));
        String peptide="MA".repeat(10000),dna="ATGGCT".repeat(10000);Path request=request(r,">p1\n"+peptide+"\n>p2\n"+peptide+"\n",">p1\n"+dna+"\n>p2\n"+dna+"\n");Path out=temporary.resolve("capacity");var result=run("--recipe",request.toString(),"--out",out.toString());assertEquals(2,result.code(),result.err());assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.exists(out.resolve("FAILURE.md")));assertTrue(Files.exists(out.resolve("inputs/dna-0001.fasta")));
    }
    @SuppressWarnings("unchecked") private static void rehash(Path root)throws Exception {
        var manifest=(Map<String,Object>)SimpleJson.parse(Files.readString(root.resolve("manifest.json")));var files=(Map<String,Object>)manifest.get("files");
        for(String name:new ArrayList<>(files.keySet())){Path p=root.resolve(name);byte[] raw=Files.readAllBytes(p);files.put(name,Map.of("bytes",raw.length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))));}
        Files.write(root.resolve("manifest.json"),Pal2NalJson.bytes(manifest));var marker=(Map<String,Object>)SimpleJson.parse(Files.readString(root.resolve("COMPLETED.json")));marker.put("manifest_sha256",Pal2NalBundle.pin(root.resolve("manifest.json"),16777216,()->{}).sha256());Files.write(root.resolve("COMPLETED.json"),Pal2NalJson.bytes(marker));
    }
    @Test void rehashedCellCoordinatesCannotPassSemanticVerification()throws Exception {
        Path root=success();Path cells=root.resolve("cells.tsv");String text=Files.readString(cells);assertTrue(text.contains("1\t2\t4\tGCT"));Files.writeString(cells,text.replace("1\t2\t4\tGCT","1\t2\t5\tGCT"));rehash(root);assertEquals(2,run("--verify",root.toString()).code());
    }
    @Test void rehashedTripletsAndRowCountsCannotPassSemanticVerification()throws Exception {
        Path root=success();Path triplets=root.resolve("triplets.tsv");byte[] original=Files.readAllBytes(triplets);Files.writeString(triplets,new String(original,StandardCharsets.UTF_8).replace("1\t1\t1\t1\t","1\t1\t1\t4\t"));rehash(root);assertEquals(2,run("--verify",root.toString()).code());Files.write(triplets,original);
        Path rows=root.resolve("rows.ndjson");String text=Files.readString(rows);assertTrue(text.contains("\"verified_residues\":3"));Files.writeString(rows,text.replace("\"verified_residues\":3","\"verified_residues\":2"));rehash(root);assertEquals(2,run("--verify",root.toString()).code());
    }
    @SuppressWarnings("unchecked") @Test void rehashedHeapReceiptCannotClaimLargerEffectiveCapacities()throws Exception {
        Path root=success();Path session=root.resolve("session.json");var value=(Map<String,Object>)SimpleJson.parse(Files.readString(session));value.put("runtime_heap_bytes",33554432L);Files.write(session,Pal2NalJson.bytes(value));rehash(root);assertEquals(2,run("--verify",root.toString()).code());
    }
    @Test void phylogeneticExportsPreserveEveryRowUnderExplicitOpaqueIds()throws Exception {
        Path root=success();String phy=Files.readString(root.resolve("alignment.phy"));assertEquals("2 9\nPN00000001 ATGGCTAAA\nPN00000002 ATGGCTAAA\n",phy);
        for(var names:org.jlinalg.phylogeny.PhylogeneticAlignment.NameField.values()){
            var parsed=org.jlinalg.phylogeny.PhylogeneticAlignment.readPhylip(new StringReader(phy),org.jlinalg.phylogeny.PhylogeneticAlignment.Kind.DNA,org.jlinalg.phylogeny.PhylogeneticAlignment.Layout.SEQUENTIAL,names,false,org.jlinalg.phylogeny.PhylogeneticAlignment.Limits.defaults());
            assertEquals(Map.of("PN00000001","ATGGCTAAA","PN00000002","ATGGCTAAA"),parsed.sequences());
        }
        var clustal=org.jlinalg.sequence.Pal2NalClustal.read(Files.newBufferedReader(root.resolve("alignment.aln")),org.jlinalg.sequence.MafftSequences.Limits.defaults(),()->{});assertEquals(List.of(new org.jlinalg.sequence.Pal2Nal.Sequence("PN00000001","ATGGCTAAA"),new org.jlinalg.sequence.Pal2Nal.Sequence("PN00000002","ATGGCTAAA")),clustal.rows());
        assertTrue(Files.readString(root.resolve("rows.ndjson")).contains("\"output_id\":\"PN00000001\""));
    }
    @Test void independentPhylipParsersCheckLongSequentialContinuations()throws Exception {
        var r=recipe();String protein="MA".repeat(30),dna="ATGGCT".repeat(30);Path request=request(r,">p1\n"+protein+"\n",">p1\n"+dna+"\n");Path out=temporary.resolve("long-export");var result=run("--recipe",request.toString(),"--out",out.toString());assertEquals(0,result.code(),result.err());
        for(var names:org.jlinalg.phylogeny.PhylogeneticAlignment.NameField.values()){
            var parsed=org.jlinalg.phylogeny.PhylogeneticAlignment.readPhylip(Files.newBufferedReader(out.resolve("alignment.phy")),org.jlinalg.phylogeny.PhylogeneticAlignment.Kind.DNA,org.jlinalg.phylogeny.PhylogeneticAlignment.Layout.SEQUENTIAL,names,false,org.jlinalg.phylogeny.PhylogeneticAlignment.Limits.defaults());
            assertEquals(Map.of("PN00000001",dna),parsed.sequences());
        }
    }
    @Test void rehashedMissingAlignmentRowAndChangedRecipeFail()throws Exception {
        Path root=success();Files.writeString(root.resolve("alignment.fasta"),">p1\nATGGCTAAA\n");rehash(root);assertEquals(2,run("--verify",root.toString()).code());
    }
    @SuppressWarnings("unchecked") @Test void rehashedReplayAndSessionBindingCannotBeChanged()throws Exception {
        Path root=success();var replay=(Map<String,Object>)SimpleJson.parse(Files.readString(root.resolve("recipe-replay.json")));options(replay).put("minimum_verified_residues",0);Files.write(root.resolve("recipe-replay.json"),Pal2NalJson.bytes(replay));rehash(root);assertEquals(2,run("--verify",root.toString()).code());
    }
    @Test void unexpectedEmptyDirectoryAndMissingMarkerFail()throws Exception {
        Path root=success();Files.createDirectory(root.resolve("inputs/extra"));assertEquals(2,run("--verify",root.toString()).code());Files.delete(root.resolve("COMPLETED.json"));assertEquals(2,run("--verify",root.toString()).code());
    }
    @Test void existingOutputIsPreservedAndHelpIsExplicit()throws Exception {
        Path root=success();byte[] before=Files.readAllBytes(root.resolve("manifest.json"));assertEquals(2,run("--recipe",temporary.resolve("request.json").toString(),"--out",root.toString()).code());assertArrayEquals(before,Files.readAllBytes(root.resolve("manifest.json")));assertFalse(Files.exists(root.resolve("FAILURE.md")));
        assertEquals(0,run("--help").code());assertTrue(run("--help").out().contains("SKIP3"));assertTrue(run("--example").out().contains("minimum_verified_fraction"));assertEquals(2,run("--unknown","x").code());
    }
}
