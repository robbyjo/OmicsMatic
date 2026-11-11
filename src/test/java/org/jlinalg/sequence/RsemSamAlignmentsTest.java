package org.jlinalg.sequence;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.junit.jupiter.api.Assertions.*;

class RsemSamAlignmentsTest {
    @TempDir static Path fixtures;
    private static List<?> cases,low;
    private static final Map<String,RsemReference.Reference> REFERENCES=new HashMap<>();
    private static final RsemReference.Limits REF=new RsemReference.Limits(1000,1000,1000000,10000,5000000,1000000);
    private static final RsemSamAlignments.Limits LIMITS=new RsemSamAlignments.Limits(10000,10000,100000,10000,1000,100000,1000000,10000000,10000,1000000,10000,10000000);
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void unpackVerifiedSourceCorpus()throws Exception{
        byte[] raw;try(var stream=RsemSamAlignmentsTest.class.getResourceAsStream("/org/jlinalg/sequence/rsem-reference/alignment-fixtures.zip")){assertNotNull(stream);raw=stream.readAllBytes();}
        assertEquals("c22c99f8e414d864df86367b01a825237810c4c9a744eb8f1c100dbfb901f72c",sha(raw));int files=0;long total=0;
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){assertFalse(entry.isDirectory());Path p=fixtures.resolve(entry.getName()).normalize();assertTrue(p.startsWith(fixtures));byte[] bytes=zip.readNBytes(5000001);assertTrue(bytes.length<=5000000);total+=bytes.length;assertTrue(total<40000000);assertTrue(++files<6000);Files.createDirectories(p.getParent());Files.write(p,bytes);}}
        assertEquals(3993,files);int hashes=0;for(String line:Files.readAllLines(fixtures.resolve("SHA256SUMS"))){String[] f=line.split("  ",2);assertEquals(f[0],sha(Files.readAllBytes(fixtures.resolve(f[1]))));hashes++;}assertEquals(3992,hashes);
        cases=readJson("cases.json");low=readJson("low-quality.json");assertEquals(580,cases.size());assertEquals(2640,low.size());
    }
    private static List<?> readJson(String name)throws IOException{
        var options=new LoaderOptions();options.setCodePointLimit(8000000);options.setMaxAliasesForCollections(0);
        Object value;try(var reader=Files.newBufferedReader(fixtures.resolve(name))){value=new Yaml(new SafeConstructor(options)).load(reader);}assertInstanceOf(List.class,value);return(List<?>)value;
    }
    private static Map<?,?> row(Object value){assertInstanceOf(Map.class,value);return(Map<?,?>)value;}
    private static String text(Map<?,?> m,String key){return(String)m.get(key);}
    private static int integer(Map<?,?> m,String key){return((Number)m.get(key)).intValue();}
    private static RsemReference.Reference reference(String version,String kind)throws IOException{
        String key=version+'/'+kind;var ref=REFERENCES.get(key);if(ref==null){ref=RsemReference.read(fixtures.resolve("references/"+key+"/reference"),REF);REFERENCES.put(key,ref);}return ref;
    }
    private static RsemSamAlignments.Options options(Map<?,?> c){return new RsemSamAlignments.Options(RsemAbundance.Release.valueOf("V"+text(c,"version").replace('.','_')),integer(c,"readtype"),text(c,"tag"),25);}
    private static Map<?,?> find(String version,String name,String format){return cases.stream().map(RsemSamAlignmentsTest::row).filter(m->text(m,"version").equals(version)&&text(m,"case").equals(name)&&text(m,"format").equals(format)).findFirst().orElseThrow();}
    private static RsemSamAlignments.Summary read(Map<?,?> c,List<RsemSamAlignments.Group> groups)throws IOException{
        var records=new ArrayList<RsemSamAlignments.Record>();var sink=new RsemSamAlignments.Sink(){
            @Override public void record(RsemSamAlignments.Record r){records.add(r);}
            @Override public void group(RsemSamAlignments.Group g){groups.add(g);}
            @Override public void finding(RsemSamAlignments.Finding f,long record,String detail){ }
        };
        RsemSamAlignments.Summary result;
        try(var reader=Files.newBufferedReader(fixtures.resolve(text(c,"decoded_sam")));
            var metadata=Files.newBufferedReader(fixtures.resolve(text(c,"decoded_metadata")))){
            result=RsemSamAlignments.read(reader,reference(text(c,"version"),text(c,"reference")),options(c),LIMITS,sink,()->{},RsemSamAlignments.decodedRecordMetadata(metadata,LIMITS,()->{}));
        }
        assertEquals(result.records(),records.size());assertEquals(result.groups(),groups.size());return result;
    }
    private static void compareOutputs(Map<?,?> c,RsemSamAlignments.Summary s,List<RsemSamAlignments.Group> groups)throws IOException{
        Map<?,?> outputs=row(c.get("outputs"));Path cnt=fixtures.resolve((String)outputs.get("sample.cnt"));
        assertEquals(Files.readString(cnt),s.accounting().originalText());s.accounting().requireMatch(RsemCounts.read(cnt,1000000,1000000,1000000,1000000,1000000));
        var wanted=new ArrayList<String>();wanted.add(Long.toString(s.accounting().alignable()));wanted.add(Long.toString(s.accounting().hits()));wanted.add(Integer.toString(integer(c,"readtype")));
        for(var g:groups)if(g.category()==1){wanted.add(Integer.toString(g.hits().size()));for(var h:g.hits()){wanted.add(Integer.toString(h.signedSid()));wanted.add(Integer.toString(h.position()));if(integer(c,"readtype")>=2)wanted.add(Integer.toString(h.insertLength()));}}
        String data=Files.readString(fixtures.resolve((String)outputs.get("sample.dat"))).trim();assertEquals(wanted,List.of(data.split("\\s+")),text(c,"version")+'/'+text(c,"format")+'/'+text(c,"case")+" .dat");
        String omissions=Files.readString(fixtures.resolve((String)outputs.get("sample.omit")));assertEquals(String.join("",s.omittedSids().stream().map(x->x+"\n").toList()),omissions);
        String[] names={"un","alignable","max"};boolean paired=integer(c,"readtype")>=2,quality=(integer(c,"readtype")&1)!=0;
        for(int category=0;category<3;category++)for(int mate=0;mate<(paired?2:1);mate++){
            StringBuilder expected=new StringBuilder();for(var g:groups)if(g.category()==category){String name=mate==0?g.name():g.mate2Name(),seq=mate==0?g.sequence():g.mate2Sequence(),q=mate==0?g.quality():g.mate2Quality();expected.append(quality?'@':'>').append(name).append('\n').append(seq).append('\n');if(quality)expected.append("+\n").append(q).append('\n');}
            String name="sample_"+names[category]+(paired?"_"+(mate+1):"")+(quality?".fq":".fa");Object path=outputs.get(name);
            if(expected.length()==0)assertNull(path,name);else{assertNotNull(path,name);assertEquals(expected.toString(),Files.readString(fixtures.resolve((String)path)),text(c,"case")+" "+name);}
        }
    }
    @Test void allFiveReleaseSamBamCramProjectionsMatchEveryOriginalOutput()throws Exception{
        int decoded=0,matched=0,failedSource=0;
        for(Object value:cases){Map<?,?> c=row(value);if(integer(c,"decoder_exit")!=0)continue;decoded++;var groups=new ArrayList<RsemSamAlignments.Group>();
            if(integer(c,"parser_exit")!=0){assertThrows(IOException.class,()->read(c,groups),text(c,"version")+'/'+text(c,"case")+'/'+text(c,"format"));failedSource++;continue;}
            var s=read(c,groups);compareOutputs(c,s,groups);if(text(c,"validator_verdict").equals("INVALID"))assertFalse(s.safeForFitting(),text(c,"case"));matched++;
        }
        assertEquals(575,decoded);assertEquals(478,matched);assertEquals(97,failedSource);
    }
    @Test void originalLowQualityFlagsMatchAll2640SourceVectors()throws Exception{
        int compared=0;
        for(Object value:low){var c=row(value);int type=integer(c,"type"),seed=integer(c,"seed"),poly=integer(c,"poly");String a=text(c,"a"),b=text(c,"b");var ref=reference(text(c,"version"),poly==0?"transcripts":"polyA");
            StringBuilder input=new StringBuilder();for(var t:ref.transcripts())input.append("@SQ\tSN:").append(t.id()).append("\tLN:").append(t.length()).append('\n');
            input.append(sam("read",type>=2?77:4,a));if(type>=2)input.append(sam("read",141,b));var groups=new ArrayList<RsemSamAlignments.Group>();
            RsemSamAlignments.read(new StringReader(input.toString()),ref,new RsemSamAlignments.Options(RsemAbundance.Release.valueOf("V"+text(c,"version").replace('.','_')),type,"",seed),LIMITS,new RsemSamAlignments.Sink(){
                @Override public void record(RsemSamAlignments.Record r){ }
                @Override public void group(RsemSamAlignments.Group g){groups.add(g);}
                @Override public void finding(RsemSamAlignments.Finding f,long i,String detail){ }
            },()->{});
            assertEquals(1,groups.size());assertEquals(c.get("low"),groups.get(0).lowQuality(),text(c,"version")+'/'+text(c,"id"));compared++;
        }
        assertEquals(2640,compared);
    }
    private static String sam(String name,int flag,String seq){return name+'\t'+flag+"\t*\t0\t0\t*\t*\t0\t0\t"+seq+'\t'+"I".repeat(seq.length())+'\n';}
    @Test void crossGeneMultiAlignmentsAreNotAUniqueGeneSubset()throws Exception{
        var c=find("1.3.3","single-cross-gene","sam");var s=read(c,new ArrayList<>());assertEquals(0,s.accounting().uniqueGenes());assertEquals(1,s.accounting().multipleGenes());assertEquals(1,s.accounting().multipleAlignments());
    }
    @Test void originalExitZeroValidatorInvalidIsNotSafeFitting()throws Exception{
        var c=find("1.3.3","paired-same-strand","sam");assertEquals(0,integer(c,"validator_exit"));assertEquals("INVALID",text(c,"validator_verdict"));assertFalse(read(c,new ArrayList<>()).safeForFitting());
    }
    @Test void validatorValidityDoesNotOverrideOriginalSingleCigarConstraint()throws Exception{
        var c=find("1.3.3","single-two-m-operations","sam");assertEquals("VALID",text(c,"validator_verdict"));assertEquals(255,integer(c,"parser_exit"));assertThrows(IOException.class,()->read(c,new ArrayList<>()));
    }
    @Test void ignoredDanglingMateRemainsInCompletePhysicalCensus()throws Exception{
        var s=read(find("1.3.3","paired-odd-tail","sam"),new ArrayList<>());assertEquals(3,s.records());assertEquals(1,s.groups());assertEquals(1,s.ignoredTrailingRecords());assertFalse(s.safeForFitting());
    }
    @Test void ignoredSecondarySequenceIsRetainedWithoutChangingFirstSourceRead()throws Exception{
        var groups=new ArrayList<RsemSamAlignments.Group>();var s=read(find("1.3.3","single-secondary-different-sequence","sam"),groups);assertEquals(1,s.findings().get(RsemSamAlignments.Finding.IGNORED_SECONDARY_SEQUENCE));assertEquals(2,groups.get(0).hits().size());assertTrue(groups.get(0).sequence().startsWith("ACGT"));
    }
    @Test void historicalLengthBehaviorIsNotReplacedByNewerFailure()throws Exception{
        var old=find("1.2.28","single-secondary-different-length","sam");var now=find("1.3.3","single-secondary-different-length","sam");var s=read(old,new ArrayList<>());assertFalse(s.safeForFitting());assertEquals(1,s.findings().get(RsemSamAlignments.Finding.HISTORICAL_LENGTH_MISMATCH));assertThrows(IOException.class,()->read(now,new ArrayList<>()));
    }
    @Test void missingQualitiesRemainSourceSpacesAndRequireAction()throws Exception{
        var groups=new ArrayList<RsemSamAlignments.Group>();var s=read(find("1.3.3","single-missing-quality","sam"),groups);assertEquals(" ".repeat(50),groups.get(0).quality());assertFalse(s.safeForFitting());
        assertTrue(read(find("1.3.3","single-missing-quality-ignored","sam"),new ArrayList<>()).safeForFitting());
    }
    @Test void secondarySupplementaryDuplicateAndQcFlagsAreNotFilters()throws Exception{
        var s=read(find("1.3.3","single-flags-retained","sam"),new ArrayList<>());assertEquals(1,s.records());assertEquals(1,s.accounting().alignable());assertEquals(1,s.accounting().hits());
    }
    @Test void readTypeTagClassifiesSuppressedGroupsOnlyWhenDeclared()throws Exception{
        var s=read(find("1.3.3","single-mixed-categories","sam"),new ArrayList<>());assertEquals(1,s.accounting().unalignable());assertEquals(1,s.accounting().excluded());
        assertEquals(1,read(find("1.3.3","single-tag-disabled","sam"),new ArrayList<>()).accounting().unalignable());
    }
    @Test void referenceOmissionsStayExplicitAndDictionaryLengthsAreBound()throws Exception{
        var s=read(find("1.3.3","single-dictionary-subset","sam"),new ArrayList<>());assertEquals(List.of(2,4,5,6),s.omittedSids());assertTrue(s.safeForFitting());
        assertFalse(read(find("1.3.3","single-dictionary-length-mismatch","sam"),new ArrayList<>()).safeForFitting());
    }
    @Test void cramEffectiveLengthIsNotInferredFromRetainedSamHeaderText()throws Exception{
        var groups=new ArrayList<RsemSamAlignments.Group>();var s=read(find("1.3.3","single-dictionary-length-mismatch","cram"),groups);
        assertEquals(799,s.targets().get(0).declaredLength());assertEquals(680,groups.get(0).hits().get(0).position());assertFalse(s.safeForFitting());
        assertEquals(1,s.findings().get(RsemSamAlignments.Finding.DECODED_HEADER_LENGTH_DISAGREEMENT));
    }
    @Test void metadataMustMatchEveryPhysicalRecordAndItsCompletionPopulation()throws Exception{
        var c=find("1.3.3","single-basic","sam");String sam=Files.readString(fixtures.resolve(text(c,"decoded_sam"))),original=Files.readString(fixtures.resolve(text(c,"decoded_metadata")));
        for(String bad:List.of(original.replace("\tone\t","\tother\t"),original.replace("END\t2","END\t3"),original.substring(0,original.lastIndexOf("END\t")),original+"extra\n")){
            assertNotEquals(original,bad);var metadata=RsemSamAlignments.decodedRecordMetadata(new StringReader(bad),LIMITS,()->{});
            assertThrows(IOException.class,()->RsemSamAlignments.read(new StringReader(sam),reference("1.3.3","transcripts"),options(c),LIMITS,new RsemSamAlignments.Sink(){
                @Override public void record(RsemSamAlignments.Record r){ }
                @Override public void group(RsemSamAlignments.Group g){ }
                @Override public void finding(RsemSamAlignments.Finding f,long i,String detail){ }
            },()->{},metadata));
        }
    }
    @Test void qnameSuffixesAreNotChoppedAndWhitespaceUsesOriginalRule()throws Exception{
        var groups=new ArrayList<RsemSamAlignments.Group>();read(find("1.3.3","single-qname-suffixes","sam"),groups);assertEquals(List.of("read/1","read/2"),groups.stream().map(RsemSamAlignments.Group::name).toList());
        groups.clear();read(find("1.3.3","single-names-whitespace","sam"),groups);assertEquals(1,groups.size());assertEquals("same",groups.get(0).name());
    }
    @Test void pathAuditRetainsAllBytesAndAcceptsCrLfFraming(@TempDir Path dir)throws Exception{
        var c=find("1.3.3","single-basic","sam");Path p=dir.resolve("sample.sam");Files.writeString(p,Files.readString(fixtures.resolve(text(c,"decoded_sam"))).replace("\n","\r\n"),StandardCharsets.UTF_8);
        var r=RsemSamAlignments.read(p,reference("1.3.3","transcripts"),options(c),LIMITS);assertEquals(sha(Files.readAllBytes(p)),r.evidence().sha256());assertEquals(Files.size(p),r.evidence().bytes());
    }
    @Test void sourceRecordsGroupsAndFindingsAreImmutable()throws Exception{
        var groups=new ArrayList<RsemSamAlignments.Group>();var s=read(find("1.3.3","single-cross-gene","sam"),groups);assertThrows(UnsupportedOperationException.class,()->s.targets().clear());assertThrows(UnsupportedOperationException.class,()->s.findings().clear());assertThrows(UnsupportedOperationException.class,()->groups.get(0).hits().clear());
    }
    @Test void completeRecordAndRetainedNameBudgetsCannotProducePartialSuccess()throws Exception{
        var c=find("1.3.3","single-basic","sam");String input=Files.readString(fixtures.resolve(text(c,"decoded_sam")));
        for(var limits:List.of(new RsemSamAlignments.Limits(1,100,100,100,100,1000,10000,1000000,100,10000,100,1000000),
                new RsemSamAlignments.Limits(100,100,100,100,100,1000,10000,1000000,1,10000,100,1000000),
                new RsemSamAlignments.Limits(100,100,100,100,100,1000,10000,50,100,10000,100,1000000)))
            assertThrows(IOException.class,()->boundedRead(c,input,limits));
    }
    @Test void completeHitAndReferenceBudgetsCannotDropMembers()throws Exception{
        var c=find("1.3.3","single-cross-gene","sam");String input=Files.readString(fixtures.resolve(text(c,"decoded_sam")));
        for(var limits:List.of(new RsemSamAlignments.Limits(100,100,100,1,100,1000,10000,1000000,100,10000,100,1000000),
                new RsemSamAlignments.Limits(100,100,100,100,5,1000,10000,1000000,100,10000,100,1000000)))
            assertThrows(IOException.class,()->boundedRead(c,input,limits));
    }
    @Test void wholeFileByteBudgetIsCheckedBeforePathAudit()throws Exception{
        var c=find("1.3.3","single-basic","sam");var tiny=new RsemSamAlignments.Limits(100,100,100,100,100,1000,10000,1000000,100,10000,100,1);
        assertThrows(IOException.class,()->RsemSamAlignments.read(fixtures.resolve(text(c,"decoded_sam")),reference("1.3.3","transcripts"),options(c),tiny));
    }
    private static RsemSamAlignments.Summary boundedRead(Map<?,?> c,String input,RsemSamAlignments.Limits limits)throws IOException{
        return RsemSamAlignments.read(new StringReader(input),reference(text(c,"version"),text(c,"reference")),options(c),limits,new RsemSamAlignments.Sink(){
            @Override public void record(RsemSamAlignments.Record r){ }
            @Override public void group(RsemSamAlignments.Group g){ }
            @Override public void finding(RsemSamAlignments.Finding f,long i,String detail){ }
        },()->{});
    }
    @Test void callerCancellationCannotReturnACompletedScan()throws Exception{
        var c=find("1.3.3","single-basic","sam");assertThrows(IOException.class,()->RsemSamAlignments.read(new StringReader(Files.readString(fixtures.resolve(text(c,"decoded_sam")))),reference("1.3.3","transcripts"),options(c),LIMITS,new RsemSamAlignments.Sink(){
            @Override public void record(RsemSamAlignments.Record r){ }
            @Override public void group(RsemSamAlignments.Group g){ }
            @Override public void finding(RsemSamAlignments.Finding f,long i,String detail){ }
        },()->{throw new IOException("cancelled");}));
    }
}
