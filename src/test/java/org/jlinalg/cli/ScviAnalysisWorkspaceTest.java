/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScviAnalysisWorkspaceTest {
    @TempDir Path temp;
    @Test void everySiblingStageIsCountedOnceAndNoAncestorOverlaps()throws Exception{
        Path source=Files.createDirectory(temp.resolve("source")),inputs=Files.createDirectory(temp.resolve("inputs")),runtime=Files.createDirectory(temp.resolve("runtime")),prior=Files.createDirectory(temp.resolve("prior-analysis")),active=Files.createDirectory(temp.resolve("query-analysis")),prepared=Files.createDirectory(active.resolve("prepared"));
        var scopes=ScviAnalysisWorkspace.scopes(temp,active.resolve("run"));assertEquals(Set.of(source,inputs,runtime,prior,prepared),Set.copyOf(scopes.directories()));assertEquals(2,scopes.ancestorDirectories());assertEquals(scopes.directories(),ScviProcess.accountingRoots(active.resolve("run"),scopes.directories()));
    }
    @Test void nestedInspectionCountsOriginalResultsModelsAndPreparation()throws Exception{
        Path runtime=Files.createDirectory(temp.resolve("runtime")),analysis=Files.createDirectory(temp.resolve("analysis")),original=Files.createDirectory(analysis.resolve("run")),models=Files.createDirectory(analysis.resolve("reviewed-models")),inspection=Files.createDirectory(analysis.resolve("inspection")),prepared=Files.createDirectory(inspection.resolve("prepared"));
        var scopes=ScviAnalysisWorkspace.scopes(temp,inspection.resolve("run"));assertEquals(Set.of(runtime,original,models,prepared),Set.copyOf(scopes.directories()));assertEquals(3,scopes.ancestorDirectories());
    }
    @Test void looseControlFilesCannotEscapeCombinedProcessAccounting()throws Exception{
        Files.writeString(temp.resolve("unscoped.tmp"),"must be charged");Path analysis=Files.createDirectory(temp.resolve("analysis"));assertThrows(IOException.class,()->ScviAnalysisWorkspace.scopes(temp,analysis.resolve("run")));
    }
    @Test void processCannotPointOutsideOrReplaceTheWholeWorkspace()throws Exception{
        assertThrows(IOException.class,()->ScviAnalysisWorkspace.scopes(temp,temp));assertThrows(IOException.class,()->ScviAnalysisWorkspace.scopes(temp,temp.getParent().resolve("foreign/run")));
    }
    @Test void ancestorContainersAreChargedInAdditionToAllScopedMembers()throws Exception{
        Path analysis=Files.createDirectory(temp.resolve("analysis"));var scopes=ScviAnalysisWorkspace.scopes(temp,analysis.resolve("run"));var requested=ScviProcess.Limits.health();var actual=scopes.process(requested);assertEquals(requested.fileCount()-2,actual.fileCount());assertEquals(requested.workspaceBytes(),actual.workspaceBytes());assertEquals(requested.sampledRssBytes(),actual.sampledRssBytes());assertEquals(requested.wallMillis(),actual.wallMillis());
    }
    @Test void insufficientContainerCapacityStopsBeforeAChildCanStart()throws Exception{
        Path analysis=Files.createDirectory(temp.resolve("analysis"));var scopes=ScviAnalysisWorkspace.scopes(temp,analysis.resolve("run"));var p=ScviProcess.Limits.health();var small=new ScviProcess.Limits(p.wallMillis(),p.addressSpaceBytes(),p.cpuSeconds(),p.fileBytes(),p.openFiles(),p.workspaceBytes(),4,p.processes(),p.threads(),p.sampledRssBytes(),p.monitorMillis());assertThrows(IOException.class,()->scopes.process(small));
    }
}
