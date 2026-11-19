/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.MetalWorkflowFiles.*;
import org.jlinalg.cli.MetalWorkflowRequest.Config;
import org.jlinalg.cli.MetalWorkflowInputs.Population;

/** Typed original interpreter input, direct process launch, and retained process records. */
final class MetalWorkflowRuntime {
    private MetalWorkflowRuntime(){ }
    static void prerequisite(Config c)throws IOException {
        if(!c.get("execution").equals("allow"))throw new Prerequisite("Original execution is forbidden by request; dependent METAL analysis skipped");
        if(!c.get("release").equals("2020-05-05"))throw new Prerequisite("This profile supports original 2020-05-05 only; do not substitute it for a named 2011 author run. Supply/qualify the required edition or leave that analysis skipped");
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Prerequisite("Original execution needs permitted Linux x86_64/WSL with its system loader/libraries; native bundle verification works on other hosts");
    }
    static String script(Config c,Population p,boolean post){
        StringBuilder text=new StringBuilder("SCHEME "+c.get("scheme")+"\nSEPARATOR TAB\nCOLUMNCOUNTING STRICT\nMINWEIGHT 0\nMAXWARNINGS 2147483647\nEFFECT_PRINT_PRECISION 12\nSTDERR_PRINT_PRECISION 12\n");
        text.append("MARKERLABEL ").append(post?"MarkerName":"MARKER").append('\n');
        text.append("ALLELELABELS ").append(c.get("allele_mode").equals("none")?"UNUSED1 UNUSED2":post?"Allele1 Allele2":"ALLELE1 ALLELE2").append('\n');
        text.append("EFFECTLABEL ").append(post?(c.stderr()?"Effect":"Zscore"):"EFFECT").append('\n');
        text.append("STDERRLABEL ").append(post?"StdErr":"STDERR").append('\n');
        text.append("PVALUELABEL ").append(post?"P-value":"PVALUE").append('\n');
        text.append("WEIGHTLABEL ").append(post?(c.stderr()?"DeclaredN":c.on("overlap")?"N":"Weight"):"N").append('\n');
        text.append("CUSTOMVARIABLE DeclaredN\nLABEL DeclaredN AS ").append(post?"DeclaredN":"N").append('\n');
        text.append("AVERAGEFREQ ").append(c.get("frequency")).append("\nMINMAXFREQ ").append(c.get("frequency")).append("\nFREQLABEL ").append(post?"Freq1":"FREQ").append('\n');
        text.append("USESTRAND ").append(post?"OFF":c.get("strand")).append("\nSTRANDLABEL STRAND\n");
        text.append("OVERLAP ").append(post?"OFF":c.get("overlap")).append('\n');
        if(!post&&c.on("overlap"))text.append("ZCUTOFF ").append(c.get("z_cutoff")).append('\n');
        text.append("LOGPVALUE ").append((!post&&c.post())||c.get("output_probability").equals("ordinary")?"OFF":"ON").append('\n');
        if(post)text.append("GENOMICCONTROL ").append(c.get("post_genomic_control")).append("\nPROCESSFILE ../primary/result1.tbl\n");
        else for(int i=0;i<p.studies().size();i++)text.append("GENOMICCONTROL ").append(p.studies().get(i).gc()).append("\nPROCESSFILE ../../inputs/").append(String.format(Locale.ROOT,"%04d.tsv",i)).append('\n');
        return text.append("OUTFILE result .tbl\nANALYZE").append(!post&&c.on("heterogeneity")?" HETEROGENEITY":"").append("\nQUIT\n").toString();
    }
    static void execute(Path root,Config c,Population population,boolean post)throws IOException,InterruptedException {
        String name=post?"post-gc":"primary";Path folder=root.resolve("stages/"+name);Files.createDirectory(folder);
        Files.writeString(folder.resolve("analysis.metal"),script(c,population,post),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Path executable=root.resolve("runtime/metal");
        try{Files.setPosixFilePermissions(executable,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_EXECUTE));}catch(IOException|UnsupportedOperationException e){throw new Prerequisite("Cannot execute original runtime on this filesystem; supply a permitted Linux filesystem: "+e.getMessage());}
        List<String> command=List.of(executable.toString(),"analysis.metal");
        map(folder.resolve("PROCESS-STARTED.tsv"),Map.of("argument.0",command.get(0),"argument.1","analysis.metal","arguments","2","executable_relative","runtime/metal","executable_sha256",pin(executable).sha256(),"environment","PATH=/usr/bin:/bin;LC_ALL=C;LANG=C;remove-LD_PRELOAD-LD_LIBRARY_PATH-OMP_*-MKL_*"));
        var builder=new ProcessBuilder(command).directory(folder.toFile()).redirectOutput(folder.resolve("original.stdout").toFile()).redirectError(folder.resolve("original.stderr").toFile());
        builder.environment().keySet().removeIf(k->k.startsWith("OMP_")||k.startsWith("MKL_")||Set.of("LD_PRELOAD","LD_LIBRARY_PATH").contains(k));builder.environment().put("LC_ALL","C");builder.environment().put("LANG","C");builder.environment().put("PATH","/usr/bin:/bin");
        Process child;long began=System.nanoTime();try{child=builder.start();}catch(IOException e){throw new Prerequisite("Original runtime or its system loader/libraries unavailable/forbidden; supply permitted prerequisites or leave dependent analysis skipped: "+e.getMessage());}
        boolean timeout=false,budget=false,interrupted=false;
        try{
            child.getOutputStream().close();
            while(!child.waitFor(200,TimeUnit.MILLISECONDS)){
                if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)>c.number("wall_ms")){timeout=true;break;}
                try{inventory(root,c.number("max_bytes"),c.number("max_files"),false);}catch(IOException e){budget=true;break;}
            }
            if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)>c.number("wall_ms"))timeout=true;
            try{inventory(root,c.number("max_bytes"),c.number("max_files"),false);}catch(IOException e){budget=true;}
        }catch(InterruptedException e){interrupted=true;throw e;}
        finally{
            if(child.isAlive()){child.descendants().forEach(h->{if(h.isAlive())h.destroyForcibly();});child.destroyForcibly();if(!interrupted)child.waitFor(10,TimeUnit.SECONDS);}
            map(folder.resolve("PROCESS-FINISHED.tsv"),Map.of("exit_code",child.isAlive()?"unknown":Integer.toString(child.exitValue()),"timed_out",Boolean.toString(timeout),"budget_exceeded",Boolean.toString(budget),"interrupted",Boolean.toString(interrupted),"pid",Long.toString(child.pid()),"elapsed_ms",Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began))));
        }
        require(!timeout&&!budget,"Original stage exceeded "+(timeout?"wall time":"workspace budget")+"; preserve partials, no complete result");inventory(root,c.number("max_bytes"),c.number("max_files"),false);
    }
    static void validateProcess(Path root,Config c,Population p,boolean post)throws IOException {
        Path folder=root.resolve("stages/"+(post?"post-gc":"primary"));var start=map(folder.resolve("PROCESS-STARTED.tsv"));var end=map(folder.resolve("PROCESS-FINISHED.tsv"));
        require("0".equals(end.get("exit_code"))&&"false".equals(end.get("timed_out"))&&"false".equals(end.get("budget_exceeded"))&&"false".equals(end.get("interrupted")),"Original process failed, timed out, exceeded budget or was interrupted; read both consoles");
        require(start.get("executable_sha256").equals(c.get("executable_sha256"))&&"runtime/metal".equals(start.get("executable_relative"))&&"2".equals(start.get("arguments"))&&"analysis.metal".equals(start.get("argument.1")),"Original process identity/arguments differ");
        require(Files.readString(folder.resolve("analysis.metal"),StandardCharsets.UTF_8).equals(script(c,p,post)),"Captured interpreter script differs from the typed request");
        regular(folder.resolve("result1.tbl"));regular(folder.resolve("result1.tbl.info"));
    }
}
