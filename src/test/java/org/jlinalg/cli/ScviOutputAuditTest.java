package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class ScviOutputAuditTest {
    @TempDir Path root;private int serial;
    private Path text(String body)throws Exception{Path p=root.resolve("output-"+serial++);Files.writeString(p,body);return p;}
    private static final List<String> CELLS=List.of("cell_β","cell_日本"),CLASSES=List.of("type_B","type_A");
    private static final ScviOutputAudit.Limits LIMITS=ScviOutputAudit.Limits.health();
    @Test void allLatentValuesAndExactOrderIncludingSignedZeroAreAudited()throws Exception{var a=ScviOutputAudit.latent(text("cell_id\tlatent_0\tlatent_1\ncell_β\t-0.0\t-1.5\ncell_日本\t2.0\t0.25\n"),CELLS,2,LIMITS,()->{});var b=ScviOutputAudit.latent(text("cell_id\tlatent_0\tlatent_1\ncell_β\t0.0\t-1.5\ncell_日本\t2.0\t0.25\n"),CELLS,2,LIMITS,()->{});assertEquals(2,a.rows());assertEquals(4,a.values());assertNotEquals(a.orderedFloat32Sha256(),b.orderedFloat32Sha256());}
    @Test void missingExtraReorderedAndNonfiniteLatentsNeverPass()throws Exception{for(String body:List.of("cell_id\tlatent_0\ncell_β\t1.0\n","cell_id\tlatent_0\ncell_日本\t1.0\ncell_β\t2.0\n","cell_id\tlatent_0\ncell_β\tNaN\ncell_日本\t2.0\n","cell_id\tlatent_0\ncell_β\t1.0\ncell_日本\t2.0\nextra\t3.0\n"))assertThrows(IOException.class,()->ScviOutputAudit.latent(text(body),CELLS,1,LIMITS,()->{}));}
    @Test void undeclaredOutputRoundingIsRejected()throws Exception{assertThrows(IOException.class,()->ScviOutputAudit.latent(text("cell_id\tlatent_0\ncell_β\t0.1\ncell_日本\t2.0\n"),CELLS,1,LIMITS,()->{}));}
    @Test void classOrderIsLearnedNotAlphabeticalAndTiesUseFirstArgmax()throws Exception{Path p=text("cell_id\ttype_B\ttype_A\ncell_β\t0.5\t0.5\ncell_日本\t0.25\t0.75\n"),h=text("cell_id\tlabel\ncell_β\ttype_B\ncell_日本\ttype_A\n");var a=ScviOutputAudit.classification(p,h,CELLS,CLASSES,LIMITS,()->{});assertEquals(2,a.labels());assertThrows(IOException.class,()->ScviOutputAudit.classification(p,h,CELLS,List.of("type_A","type_B"),LIMITS,()->{}));}
    @Test void probabilityMassLabelsAndAllCellsAreRequired()throws Exception{Path h=text("cell_id\tlabel\ncell_β\ttype_B\ncell_日本\ttype_A\n");for(String body:List.of("cell_id\ttype_B\ttype_A\ncell_β\t0.25\t0.25\ncell_日本\t0.25\t0.75\n","cell_id\ttype_B\ttype_A\ncell_β\t0.25\t0.75\ncell_日本\t0.25\t0.75\n","cell_id\ttype_B\ttype_A\ncell_β\t1.0\t0.0\n"))assertThrows(IOException.class,()->ScviOutputAudit.classification(text(body),h,CELLS,CLASSES,LIMITS,()->{}));}
    @Test void everyTrainingCellBelongsToExactlyOneReportedPartition()throws Exception{var a=ScviOutputAudit.splits(text("partition\tcell_id\nvalidation\tcell_日本\ntrain\tcell_β\n"),CELLS,LIMITS,()->{});assertEquals(1,a.train());assertEquals(1,a.validation());for(String body:List.of("partition\tcell_id\ntrain\tcell_β\n","partition\tcell_id\ntrain\tcell_β\nvalidation\tcell_β\n","partition\tcell_id\ntrain\tcell_β\nother\tcell_日本\n"))assertThrows(IOException.class,()->ScviOutputAudit.splits(text(body),CELLS,LIMITS,()->{}));}
    @Test void finiteHistoryRetainsCompleteMetricEpochPairs()throws Exception{var a=ScviOutputAudit.history(text("metric\tepoch\tvalue\nelbo_train\t0\t3.0\nelbo_train\t1\t2.0\nkl_train\t0\t0.25\n"),2,LIMITS,()->{});assertEquals(2,a.metrics());assertEquals(3,a.observations());for(String body:List.of("metric\tepoch\tvalue\n","metric\tepoch\tvalue\nx\t0\tNaN\n","metric\tepoch\tvalue\nx\t0\t1.0\nx\t0\t2.0\n","metric\tepoch\tvalue\nx\t2\t2.0\n"))assertThrows(IOException.class,()->ScviOutputAudit.history(text(body),2,LIMITS,()->{}));}
    @Test void completeOutputBytesAndPopulationCapsAreEnforced()throws Exception{Path p=text("cell_id\tlatent_0\ncell_β\t1.0\ncell_日本\t2.0\n");assertThrows(IOException.class,()->ScviOutputAudit.latent(p,CELLS,1,new ScviOutputAudit.Limits(1,2,1,64),()->{}));assertThrows(IOException.class,()->ScviOutputAudit.latent(p,CELLS,1,new ScviOutputAudit.Limits(4096,1,1,64),()->{}));assertThrows(IOException.class,()->ScviOutputAudit.latent(p,List.of("cell_β","cell_β"),1,LIMITS,()->{}));}
}
