package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerAtacTagsTest {
    @TempDir Path root;
    private static final String TAGS="\tchr1\t100\t0\t0.5\t100\n\tchr1\t199\t1\t0.5\t100\n";
    private HomerAtacInputs.Data data(boolean doubled)throws Exception{
        String pair="pair\t99\tchr1\t100\t60\t10M\t*\t0\t100\tACGTACGTAC\tIIIIIIIIII\n"+"pair\t147\tchr1\t190\t60\t10M\t*\t0\t-100\tACGTACGTAC\tIIIIIIIIII\n";Files.writeString(root.resolve("reads.sam"),"@HD\tVN:1.6\n@SQ\tSN:chr1\tLN:10000\n"+pair+(doubled?pair:""));Files.writeString(root.resolve("reference.tsv"),"chr1\t10000\n");Files.writeString(root.resolve("list.tsv"),"replicate_id\tsam_path\nRep\treads.sam\n");var o=new LinkedHashMap<String,String>();o.put("sam-list",root.resolve("list.tsv").toString());o.put("reference-lengths",root.resolve("reference.tsv").toString());o.put("maximum-records","1000");o.put("maximum-ledger-keys","10");for(String key:new String[]{"duplicate-alignments","supplementary-alignments","unpaired-alignments","improper-pairs"})o.put(key,"error");o.put("zero-template-length","error");o.put("identical-replicates","error");Path home=Files.createDirectory(root.resolve("runtime"));Files.writeString(home.resolve(HomerAtacRuntime.MARKER),"{}\n");var context=new HomerAtacRuntime.Context(home,"unit-test-intake-only",FollowupSupport.hash(home.resolve(HomerAtacRuntime.MARKER)),Map.of(),Map.of(),Map.of());return HomerAtacInputs.stage(o,Files.createDirectory(root.resolve("work")),HomerAtacInputs.Limits.read(o),context);
    }
    private Map<String,Path> roles(HomerAtacInputs.Data d,String text,int rows,String mass)throws Exception{
        var result=new LinkedHashMap<String,Path>();for(String name:new String[]{"R0001","MERGED"}){Path dir=Files.createDirectory(d.work().resolve(name));Files.writeString(dir.resolve("chr1.tags.tsv"),text);String info="name\tUnique Positions\tTotal Tags\ngenome\t"+rows+"\t"+mass+"\nfragmentLengthEstimate=-123456\t\t\npeakSizeEstimate=150\t\t\ntagsPerBP=0.1\t\t\naverageTagsPerPosition=0.5\t\t\nmedianTagsPerPosition=1\t\t\naverageTagLength=100.0\t\t\ngsizeEstimate=199\t\t\naverageFragmentGCcontent=-1.000\t\t\nchr1\t"+rows+"\t"+mass+"\ncmd=unit-fixture\t\t\n";Files.writeString(dir.resolve("tagInfo.txt"),info);for(String file:new String[]{"tagAutocorrelation.txt","tagCountDistribution.txt","tagLengthDistribution.txt"})Files.writeString(dir.resolve(file),"unit-fixture diagnostic\n");result.put(name,dir);}return result;
    }
    @Test void everyIndividualAndMergedNativeFullKeyPopulationMustMatch()throws Exception{var d=data(false);var a=HomerAtacTags.audit(d,roles(d,TAGS,2,"1.0"));assertEquals(4,a.actual().fullKeys());assertEquals(2,a.roles().get("MERGED").serializedRows());assertEquals(2,HomerAtacTags.chromosome(d,a.roles().get("MERGED"),"chr1").size());a.verify();}
    @Test void serializedPositionsNeedNotBeDistinctFullKeys()throws Exception{var d=data(true);String split="\tchr1\t100\t0\t0.5\t100\n\tchr1\t100\t0\t0.5\t100\n\tchr1\t199\t1\t0.5\t100\n\tchr1\t199\t1\t0.5\t100\n";var a=HomerAtacTags.audit(d,roles(d,split,4,"2.0"));assertEquals(4,a.actual().fullKeys());assertEquals(4,a.roles().get("MERGED").serializedRows());}
    @Test void fullIntegerLengthAliasCannotPassOnlyPositionStrandMassChecks()throws Exception{var d=data(false);var roles=roles(d,TAGS,2,"1.0");Files.writeString(roles.get("MERGED").resolve("chr1.tags.tsv"),TAGS.replace("\t100\n","\t356\n"));assertTrue(assertThrows(IOException.class,()->HomerAtacTags.audit(d,roles)).getMessage().contains("Complete original tag population"));}
    @Test void totalMassAndSerializedRowMetadataCannotReplaceActualTagRecords()throws Exception{var d=data(false);var roles=roles(d,TAGS,3,"1.0");assertTrue(assertThrows(IOException.class,()->HomerAtacTags.audit(d,roles)).getMessage().contains("serialized-row"));}
    @Test void unknownFilesAndMissingIndividualRolesFailCompleteInventoryAudit()throws Exception{var d=data(false);var roles=roles(d,TAGS,2,"1.0");Files.writeString(roles.get("MERGED").resolve("extra.txt"),"extra");assertThrows(IOException.class,()->HomerAtacTags.audit(d,roles));assertThrows(IOException.class,()->HomerAtacTags.audit(d,Map.of("MERGED",roles.get("MERGED"))));}
    @Test void unsortedSourceRowsCannotBeSilentlyReorderedBeforeCoverage()throws Exception{var d=data(false);String reverse="\tchr1\t199\t1\t0.5\t100\n\tchr1\t100\t0\t0.5\t100\n";assertTrue(assertThrows(IOException.class,()->HomerAtacTags.audit(d,roles(d,reverse,2,"1.0"))).getMessage().contains("position ordered"));}
    @Test void changedSourceQCDiagnosticInvalidatesTheAdmittedTagFamily()throws Exception{var d=data(false);var a=HomerAtacTags.audit(d,roles(d,TAGS,2,"1.0"));Files.writeString(a.roles().get("MERGED").directory().resolve("tagCountDistribution.txt"),"changed");assertThrows(IOException.class,a::verify);}
    @Test void nativeActualLedgerIsBoundThroughFinalVerification()throws Exception{var d=data(false);var a=HomerAtacTags.audit(d,roles(d,TAGS,2,"1.0"));Files.writeString(a.ledger(),"changed");assertThrows(IOException.class,a::verify);}
}
