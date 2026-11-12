/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Shared atomic publication and failure diagnostics for Java-only tool operations. */
final class NativeToolSupport {
    private NativeToolSupport() { }
    private static final ThreadLocal<Long> DEADLINE=new ThreadLocal<>();
    static int run(Map<String,String> options,String operation,FollowupSupport.Job job,PrintStream out,PrintStream err) {
        int[] code={0};
        try {
            FollowupSupport.transaction(options,operation,(directory,manifest)->{
                manifest.put("engine","native-java");
                try {
                    DEADLINE.set(System.nanoTime()+FollowupSupport.integer(options,"timeout",3600,1,86400)*1000000000L);
                    job.execute(directory,manifest);
                    check();
                    manifest.put("analysis_status","completed");manifest.put("result_admitted",true);
                    Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\"completed\",\"result_admitted\":true,\"engine\":\"native-java\"}\n");
                }catch(Exception failure) {
                    code[0]=2;manifest.put("analysis_status","failed");manifest.put("result_admitted",false);manifest.put("error",failure.toString());
                    quarantine(directory);
                    Files.writeString(directory.resolve("WARNINGS.md"),"# Native analysis failed\n\n"+failure+"\n\nRequired action: correct the named input, option, capacity or interruption and retry into a new output directory. No partial result is admitted.\n");
                    Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\"failed\",\"result_admitted\":false,\"engine\":\"native-java\"}\n");
                }finally{DEADLINE.remove();}
            });
            if(code[0]==0)out.println("Native analysis completed: "+options.get("out"));else err.println("Native analysis failed; read "+Path.of(options.get("out"),"WARNINGS.md"));
            return code[0];
        }catch(Exception failure){err.println("Native analysis not published: "+failure.getMessage());return 2;}
    }
    static void quarantine(Path directory)throws IOException {
        List<Path> files;try(var stream=Files.list(directory)){files=stream.toList();}
        if(!files.isEmpty()) {
            Path partial=Files.createDirectory(directory.resolve("partial"));
            for(Path file:files)Files.move(file,partial.resolve(file.getFileName()));
        }
    }
    static void check()throws IOException {
        if(Thread.currentThread().isInterrupted())throw new IOException("Native analysis interrupted");
        Long deadline=DEADLINE.get();if(deadline!=null&&System.nanoTime()-deadline>=0)throw new IOException("Native analysis timeout exceeded");
    }
    static String html(String text) {
        return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
}
