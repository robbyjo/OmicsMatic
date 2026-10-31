/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FavorXsvLedgerTest {
    @TempDir Path directory;
    private static final String REQUEST="a".repeat(64),XSV="/opt/private-favor/bin/xsv";
    private static final Instant START=Instant.parse("2026-10-30T12:00:00Z"),FINISH=START.plusSeconds(10);
    private static final FavorXsvLedger.Limits LIMITS=new FavorXsvLedger.Limits(1048576,8388608,10000);
    private record Fixture(Path folder,List<Path> files,List<Map<String,Object>> records) { }
    private Fixture fixture()throws Exception {
        Path folder=directory.resolve("events-"+java.util.UUID.randomUUID());Files.createDirectory(folder);var files=new ArrayList<Path>();var records=new ArrayList<Map<String,Object>>();var plan=FavorXsvSupervisor.plan(22,2);
        for(int step=1;step<=3;step++) {
            var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("request_sha256",REQUEST);m.put("started_at",START.plusSeconds(step*2L).toString());m.put("supervisor_pid",100+step);m.put("arguments",plan.get(step-1));m.put("status","completed");m.put("planned_step",step);m.put("xsv",XSV);m.put("xsv_sha256",FavorXsvSupervisor.XSV_SHA256);var command=new ArrayList<>(List.of(XSV));command.addAll(plan.get(step-1));m.put("command",command);m.put("child_pid",200+step);m.put("child_exit_code",0);m.put("exit_code",0);m.put("finished_at",START.plusSeconds(step*2L+1).toString());m.put("owned_child_alive",false);
            Path file=folder.resolve("event-00000000-0000-4000-8000-"+String.format(java.util.Locale.ROOT,"%012d",step)+".json");files.add(file);records.add(m);
        }
        var result=new Fixture(folder,files,records);save(result);return result;
    }
    private static void save(Fixture f)throws Exception {for(int i=0;i<f.files().size();i++)Files.writeString(f.files().get(i),RegenieRuntime.json(f.records().get(i))+"\n",StandardCharsets.UTF_8);}
    private static FavorXsvLedger.Ledger read(Fixture f)throws Exception {save(f);return direct(f,LIMITS);}
    private static FavorXsvLedger.Ledger direct(Fixture f,FavorXsvLedger.Limits limits)throws Exception {return FavorXsvLedger.read(f.folder(),REQUEST,XSV,22,2,START,FINISH,limits,()->{});}
    @Test void completeExactSequentialPlanCapturesEveryTerminalEvent()throws Exception {var f=fixture();var r=read(f);assertEquals(3,r.events().size());assertEquals(List.of(1,2,3),r.events().stream().map(FavorXsvLedger.Event::step).toList());assertEquals(64,r.sha256().length());assertEquals(Files.size(f.files().get(0))+Files.size(f.files().get(1))+Files.size(f.files().get(2)),r.bytes());r.verify(()->{});}
    @Test void filenameOrderDoesNotSubstituteForTheOriginalStepOrder()throws Exception {var f=fixture();save(f);Path moved=f.folder().resolve("event-ffffffff-ffff-4fff-8fff-ffffffffffff.json");Files.move(f.files().get(0),moved);var r=direct(f,LIMITS);assertEquals(moved.getFileName().toString(),r.events().get(0).filename());assertEquals(1,r.events().get(0).step());}
    @Test void missingEventsCannotBeOverriddenByRExitZeroOrMatchingScalars()throws Exception {var f=fixture();Files.delete(f.files().get(1));assertThrows(IOException.class,()->direct(f,LIMITS));Path missing=directory.resolve("absent");assertThrows(IOException.class,()->FavorXsvLedger.read(missing,REQUEST,XSV,22,2,START,FINISH,LIMITS,()->{}));}
    @Test void repeatedPlanStepsAreRefused()throws Exception {var f=fixture();f.records().set(2,new LinkedHashMap<>(f.records().get(1)));assertThrows(IOException.class,()->read(f));}
    @Test void extraTemporaryAndNonregularFilesAreRefused()throws Exception {
        var f=fixture();Files.writeString(f.folder().resolve("extra.json"),"{}");assertThrows(IOException.class,()->direct(f,LIMITS));var second=fixture();Files.move(second.files().get(2),second.folder().resolve("event-started.tmp"));assertThrows(IOException.class,()->direct(second,LIMITS));var third=fixture();Files.delete(third.files().get(2));Files.createDirectory(third.files().get(2));assertThrows(IOException.class,()->direct(third,LIMITS));
    }
    @Test void startedFailedAndUnavailableStatusesNeverAdmitACompletedWorkflow()throws Exception {for(String status:List.of("started","failed","unavailable")){var f=fixture();f.records().get(1).put("status",status);assertTrue(assertThrows(IOException.class,()->read(f)).getMessage().contains("not completed"));}}
    @Test void actualChildAndSupervisorExitsAndLivenessMustAllBeSuccessful()throws Exception {for(String field:List.of("child_exit_code","exit_code","owned_child_alive")){var f=fixture();f.records().get(1).put(field,field.equals("owned_child_alive")?true:2);assertThrows(IOException.class,()->read(f));}}
    @Test void timeoutFlagsAndCleanupErrorsCannotBeHiddenByCompletedStatus()throws Exception {var f=fixture();f.records().get(0).put("timed_out",false);assertEquals(3,read(f).events().size());f.records().get(0).put("timed_out",true);assertThrows(IOException.class,()->read(f));var second=fixture();second.records().get(0).put("cleanup_error","retained");assertThrows(IOException.class,()->read(second));}
    @Test void requestExecutableAndBinaryPinsAreExact()throws Exception {for(String field:List.of("request_sha256","xsv","xsv_sha256")){var f=fixture();f.records().get(0).put(field,field.equals("xsv")?"/opt/other/xsv":"0".repeat(64));assertThrows(IOException.class,()->read(f));}}
    @Test void actualArgumentsAndFullCommandMustMatchTheStagedPlan()throws Exception {for(String field:List.of("arguments","command")){var f=fixture();f.records().get(2).put(field,List.of("cat","rows","foreign.csv"));assertThrows(IOException.class,()->read(f));}}
    @Test void eventTimesMustStayInsideTheActualParentRun()throws Exception {for(String field:List.of("started_at","finished_at")){var f=fixture();f.records().get(0).put(field,(field.equals("started_at")?START.minusSeconds(1):FINISH.plusSeconds(1)).toString());assertThrows(IOException.class,()->read(f));}var f=fixture();f.records().get(0).put("finished_at",START.toString());assertThrows(IOException.class,()->read(f));}
    @Test void overlappingOrOutOfOrderCommandsAreRefused()throws Exception {var f=fixture();f.records().get(1).put("started_at",START.plusSeconds(2).toString());assertThrows(IOException.class,()->read(f));var second=fixture();second.records().get(1).put("started_at",START.plusSeconds(8).toString());second.records().get(1).put("finished_at",START.plusSeconds(9).toString());assertThrows(IOException.class,()->read(second));}
    @Test void processIdentitiesAndNumericTypesAreChecked()throws Exception {for(Object value:List.of(0,-1,"123",1.5)){var f=fixture();f.records().get(0).put("child_pid",value);assertThrows(IOException.class,()->read(f));}var f=fixture();f.records().get(0).put("child_pid",f.records().get(0).get("supervisor_pid"));assertThrows(IOException.class,()->read(f));}
    @Test void kernelPidReuseAcrossNonoverlappingCommandsDoesNotTargetUnrelatedProcesses()throws Exception {var f=fixture();for(var r:f.records()){r.put("supervisor_pid",100);r.put("child_pid",200);}assertEquals(3,read(f).events().size());}
    @Test void schemaUnknownAndMissingFieldsAreRefused()throws Exception {for(String field:List.of("schema_version","unknown","finished_at")){var f=fixture();if(field.equals("finished_at"))f.records().get(0).remove(field);else f.records().get(0).put(field,2);assertThrows(IOException.class,()->read(f));}}
    @Test void duplicateJsonKeysMalformedUtf8AndNoncanonicalTimesAreRefused()throws Exception {var f=fixture();Files.writeString(f.files().get(0),"{\"status\":\"completed\",\"status\":\"failed\"}");assertThrows(IOException.class,()->direct(f,LIMITS));Files.write(f.files().get(0),new byte[]{(byte)0xff});assertThrows(IOException.class,()->direct(f,LIMITS));var second=fixture();second.records().get(0).put("started_at","bad");assertThrows(IOException.class,()->read(second));}
    @Test void completeEventByteAndCountCapacitiesAreEnforced()throws Exception {var f=fixture();for(var l:List.of(new FavorXsvLedger.Limits(10,8388608,10000),new FavorXsvLedger.Limits(1048576,10,10000),new FavorXsvLedger.Limits(1048576,8388608,2)))assertThrows(IOException.class,()->direct(f,l));}
    @Test void changingOrAddingEventsAfterAdmissionInvalidatesTheLedger()throws Exception {var f=fixture();var ledger=read(f);Files.writeString(f.files().get(0),"{}");assertThrows(IOException.class,()->ledger.verify(()->{}));var second=fixture();var another=read(second);Files.writeString(second.folder().resolve("foreign.json"),"{}");assertThrows(IOException.class,()->another.verify(()->{}));}
    @Test void cancellationReturnsNoCompletedLedger()throws Exception {var f=fixture();assertThrows(IllegalStateException.class,()->FavorXsvLedger.read(f.folder(),REQUEST,XSV,22,2,START,FINISH,LIMITS,()->{throw new IllegalStateException("cancelled");}));}
    @Test void malformedParentBindingsAndUnsupportedSingleShardPlansAreRefused()throws Exception {var f=fixture();for(String xsv:List.of("relative","/opt/../xsv","/opt//xsv","/opt/xsv/"))assertThrows(IOException.class,()->FavorXsvLedger.read(f.folder(),REQUEST,xsv,22,2,START,FINISH,LIMITS,()->{}));assertThrows(IOException.class,()->FavorXsvLedger.read(f.folder(),REQUEST,XSV,22,1,START,FINISH,LIMITS,()->{}));assertThrows(IOException.class,()->FavorXsvLedger.read(f.folder(),REQUEST,XSV,22,2,FINISH,START,LIMITS,()->{}));}
    @Test void completedEventCollectionsAndArgumentsAreImmutable()throws Exception {var r=read(fixture());assertThrows(UnsupportedOperationException.class,()->r.events().clear());assertThrows(UnsupportedOperationException.class,()->r.events().get(0).arguments().clear());}
    @Test void invalidCapacitiesAreRefused() {assertThrows(IllegalArgumentException.class,()->new FavorXsvLedger.Limits(0,1,1));assertThrows(IllegalArgumentException.class,()->new FavorXsvLedger.Limits(1,0,1));assertThrows(IllegalArgumentException.class,()->new FavorXsvLedger.Limits(1,1,10001));}
}
