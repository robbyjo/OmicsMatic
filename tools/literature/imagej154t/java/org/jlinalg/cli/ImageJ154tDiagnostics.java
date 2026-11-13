/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import ij.IJ;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Observe original caught command exceptions by their actual call site, not log-text matching. */
final class ImageJ154tDiagnostics extends PrintStream {
    private final PrintStream original;
    private final List<String> failures=new ArrayList<>();
    ImageJ154tDiagnostics(PrintStream stream){super(stream,true,StandardCharsets.UTF_8);original=stream;}
    @Override public void println(String text){
        var callers=StackWalker.getInstance().walk(s->s.skip(1).limit(2).map(f->f.getClassName()+"#"+f.getMethodName()).toList());
        if(callers.equals(List.of("ij.IJ#log","ij.Executer#run")))record("Original Executer caught command exception:\n"+text);
        original.println(text);
    }
    synchronized void record(String message){failures.add(message);}
    synchronized List<String> failures(){return List.copyOf(failures);}
    void install(){
        System.setOut(this);IJ.setExceptionHandler(e->{record("Original IJ.handleException: "+e);e.printStackTrace(System.err);});
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{record("Uncaught original thread "+t.getName()+": "+e);e.printStackTrace(System.err);});
    }
}
