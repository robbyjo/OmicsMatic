/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Disjoint directory scopes account for all sibling stages during an owned model process. */
final class ScviAnalysisWorkspace {
    private ScviAnalysisWorkspace() { }
    record Scopes(Path root,Path changing,List<Path> directories,int ancestorDirectories) {
        Scopes{directories=List.copyOf(directories);}
        ScviProcess.Limits process(ScviProcess.Limits requested)throws IOException{
            int remaining=requested.fileCount()-ancestorDirectories;if(remaining<4)throw new IOException("Whole scVI workspace file capacity cannot cover containers and owned process records");
            return new ScviProcess.Limits(requested.wallMillis(),requested.addressSpaceBytes(),requested.cpuSeconds(),requested.fileBytes(),requested.openFiles(),requested.workspaceBytes(),remaining,requested.processes(),requested.threads(),requested.sampledRssBytes(),requested.monitorMillis());
        }
    }
    static Scopes scopes(Path suppliedRoot,Path suppliedChanging)throws IOException{
        Path root=suppliedRoot.toAbsolutePath().normalize(),changing=suppliedChanging.toAbsolutePath().normalize();ordinaryDirectory(root);
        if(!changing.startsWith(root)||changing.equals(root))throw new IOException("Owned model process must remain below its complete canonical analysis workspace");
        var scopes=new ArrayList<Path>();Path cursor=root;
        for(Path part:root.relativize(changing)){
            Path next=cursor.resolve(part);ordinaryDirectory(cursor);
            try(var entries=Files.list(cursor)){
                for(Path child:entries.sorted().toList())if(!child.equals(next)){
                    if(!Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS)||!child.toRealPath().equals(child))throw new IOException("Managed analysis files must remain inside disjoint stage directories during original execution: "+child);
                    scopes.add(child);
                }
            }
            cursor=next;
        }
        // The changed root is counted by ScviProcess; charge all excluded ancestors separately.
        return new Scopes(root,changing,scopes,root.relativize(changing).getNameCount());
    }
    private static void ordinaryDirectory(Path p)throws IOException{if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||!p.toRealPath().equals(p))throw new IOException("Canonical existing scVI workspace directory required: "+p);}
}
