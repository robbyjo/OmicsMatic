package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerAnnotationDataTest {
    @TempDir Path root;
    @Test void bedCoordinatesRawUtf8IdentityAndMissingContigRemainComplete()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path work=root.resolve("work");var data=HomerAnnotationFixture.stage(o,work);assertEquals(2,data.peaks().size());assertEquals(1,data.missingContigs());assertEquals(10011,data.peaks().get(0).start1());assertTrue(Files.readString(work.resolve("peaks.tsv")).contains("pα"));assertEquals(Files.readString(Path.of(o.get("gtf"))),Files.readString(work.resolve("stage-genes.gtf")));
    }
    @Test void homerCoordinatesAndFocusArePreservedWithoutBedShift()throws Exception{
        var o=HomerAnnotationFixture.options(root);o.put("peak-format","homer");Files.writeString(Path.of(o.get("peaks")),"p\tchr1\t10011\t10080\t-\t2.5e-2\t0.001\n");var d=HomerAnnotationFixture.stage(o,root.resolve("work"));assertEquals(10011,d.peaks().get(0).start1());assertEquals("0.001",d.peaks().get(0).focus());assertEquals("2.5e-2",d.peaks().get(0).score());
    }
    @Test void duplicateNamesRequireExplicitOrdinalRetention()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path p=Path.of(o.get("peaks"));Files.writeString(p,Files.readString(p).replace("unknown","pα"));assertThrows(IOException.class,()->HomerAnnotationFixture.stage(o,root.resolve("fail")));o.put("duplicate-ids","retain-ordinal");var d=HomerAnnotationFixture.stage(o,root.resolve("pass"));assertEquals(1,d.duplicateIds());assertNotEquals(d.peaks().get(0).alias(),d.peaks().get(1).alias());
    }
    @Test void missingGtfContigAndDotStrandsRequireSeparateChoices()throws Exception{
        var o=HomerAnnotationFixture.options(root);o.put("missing-gtf-contig","error");assertThrows(IOException.class,()->HomerAnnotationFixture.stage(o,root.resolve("missing")));o.put("missing-gtf-contig","retain");Path p=Path.of(o.get("peaks"));Files.writeString(p,Files.readString(p).replace("\t+\n","\t.\n"));assertThrows(IOException.class,()->HomerAnnotationFixture.stage(o,root.resolve("dot")));o.put("peak-unknown-strand","original-plus");assertEquals(1,HomerAnnotationFixture.stage(o,root.resolve("plus")).plusAssignedPeaks());
    }
    @Test void crlfSourceCopiesStayByteIdentical()throws Exception{
        var o=HomerAnnotationFixture.options(root);for(String key:new String[]{"peaks","gtf","reference-lengths"}){Path p=Path.of(o.get(key));Files.writeString(p,Files.readString(p).replace("\n","\r\n"));}Path w=root.resolve("work");var d=HomerAnnotationFixture.stage(o,w);assertEquals(2,d.peaks().size());assertArrayEquals(Files.readAllBytes(Path.of(o.get("peaks"))),Files.readAllBytes(w.resolve("input-peaks")));
    }
    @Test void sourceAndStagedMutationInvalidateAnalysis()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path w=root.resolve("work");var d=HomerAnnotationFixture.stage(o,w);d.verify();Files.writeString(w.resolve("stage-genes.gtf"),"changed");assertThrows(IOException.class,d::verify);
    }
    @Test void inputResourceAndUnsupportedFieldCountsFailWithoutFiltering()throws Exception{
        var o=HomerAnnotationFixture.options(root);o.put("maximum-input-bytes","10");assertThrows(IOException.class,()->HomerAnnotationFixture.stage(o,root.resolve("bytes")));o.remove("maximum-input-bytes");Path p=Path.of(o.get("peaks"));Files.writeString(p,Files.readString(p).replace("\t+\n","\t+\textra\n"));assertThrows(IOException.class,()->HomerAnnotationFixture.stage(o,root.resolve("fields")));
    }
    @Test void completeOriginalRowsPassSeparateNativeTssAudit()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path w=root.resolve("work");var d=HomerAnnotationFixture.stage(o,w);HomerAnnotationFixture.output(w);assertEquals(2,HomerAnnotationData.audit(d,w,HomerAnnotationData.Limits.read(o)));assertTrue(Files.readString(w.resolve("results.tsv")).contains("no-GTF-contig-retained-NA"));assertTrue(Files.readString(w.resolve("native-tss-candidates.tsv")).contains("\t10001\t44\ttrue"));
    }
    @Test void alteredCoordinatesWrongGeneOrWrongDistanceFailAudit()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path w=root.resolve("work");var d=HomerAnnotationFixture.stage(o,w);
        for(String[] replace:new String[][]{{"\t10011\t","\t10012\t"},{"\t44\tA\t","\t45\tA\t"},{"\tA\tGA\tA\t","\tA\tGB\tA\t"}}){HomerAnnotationFixture.output(w);Path p=w.resolve("original.stdout");Files.writeString(p,Files.readString(p).replace(replace[0],replace[1]));assertThrows(IOException.class,()->HomerAnnotationData.audit(d,w,HomerAnnotationData.Limits.read(o)));}
    }
    @Test void omittedDuplicatedOrFabricatedUnknownRowsFailAudit()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path w=root.resolve("work");var d=HomerAnnotationFixture.stage(o,w);
        HomerAnnotationFixture.output(w);Path p=w.resolve("original.stdout");String original=Files.readString(p);Files.writeString(p,original.substring(0,original.lastIndexOf("H000000002")));assertThrows(IOException.class,()->HomerAnnotationData.audit(d,w,HomerAnnotationData.Limits.read(o)));
        Files.writeString(p,original+original.substring(original.indexOf("H000000001"),original.indexOf("H000000002")));assertThrows(IOException.class,()->HomerAnnotationData.audit(d,w,HomerAnnotationData.Limits.read(o)));
        Files.writeString(p,original.replace("\tNA\tNA\tNA\tNA\t","\tIntergenic\tIntergenic\tNA\tNA\t"));assertThrows(IOException.class,()->HomerAnnotationData.audit(d,w,HomerAnnotationData.Limits.read(o)));
    }
    @Test void incompleteInternalSubprocessDiagnosticsInvalidateExitZeroOutput()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path w=root.resolve("work");var d=HomerAnnotationFixture.stage(o,w);HomerAnnotationFixture.output(w);Files.writeString(w.resolve("original.stderr"),"helper: not found\nDone annotating peaks file\n");assertThrows(IOException.class,()->HomerAnnotationData.audit(d,w,HomerAnnotationData.Limits.read(o)));
    }
    @Test void invalidUtf8AndAmbiguousReferenceDictionaryFail()throws Exception{
        var o=HomerAnnotationFixture.options(root);Path p=Path.of(o.get("reference-lengths"));String original=Files.readString(p);Files.writeString(p,original+"chr1\t100000\n");assertThrows(IOException.class,()->HomerAnnotationFixture.stage(o,root.resolve("ref")));Files.writeString(p,original);Files.write(Path.of(o.get("peaks")),new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->HomerAnnotationFixture.stage(o,root.resolve("utf8")));
    }
}
