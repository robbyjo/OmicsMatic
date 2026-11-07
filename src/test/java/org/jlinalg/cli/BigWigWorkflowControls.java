/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Complete packaged public-dispatch controls; reference provenance is checked by the owning validator. */
public final class BigWigWorkflowControls {
    private BigWigWorkflowControls() { }
    /** @param args produce|verify, NEW producer/complete control root, parent native protocol root, exact JAR */
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[1]).toAbsolutePath(),source=Path.of(args[2]).toAbsolutePath(),artifact=Path.of(args[3]).toAbsolutePath();for(Class<?> type:List.of(JLinAlgCli.class,BigWigToolsCli.class,BigWigWorkflow.class,org.jlinalg.genetics.BigWigWriter.class))if(!type.getProtectionDomain().getCodeSource().getLocation().toURI().equals(artifact.toUri()))throw new AssertionError("Wrong actual workflow JAR: "+type);
        if(args[0].equals("verify")){var cases=NextflowFiles.list(BigWigFiles.json(root.resolve("CONTROLS.json"),2097152).get("cases"));for(Object value:cases){var row=NextflowFiles.object(value);Path bundle=root.resolve(NextflowFiles.member(NextflowFiles.text(row.get("bundle"))));var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();if(JLinAlgCli.run(new String[]{"bigwig-tools","--verify",bundle.toString()},new PrintStream(out),new PrintStream(err))!=0)throw new AssertionError("Complete portable workflow failed: "+row.get("id")+"\n"+err.toString(StandardCharsets.UTF_8));}System.out.println("COMPLETE_PORTABLE_NATIVE_BIGWIG_WORKFLOWS "+cases.size());return;}
        if(!args[0].equals("produce")||Files.exists(root))throw new AssertionError("Require NEW packaged native workflow producer root");Files.createDirectory(root);Files.createDirectory(root.resolve("cases"));Files.createDirectory(root.resolve("sources with spaces α"));var controls=BigWigFiles.json(source.resolve("CONTROLS.json"),2097152);var records=new ArrayList<Map<String,Object>>();int intervals=0;
        for(Object value:NextflowFiles.list(controls.get("cases"))){var row=NextflowFiles.object(value);String id=NextflowFiles.text(row.get("id"));var options=NextflowFiles.object(row.get("options"));Path original=source.resolve(id);for(boolean gzip:List.of(false,true)){
            String label=id+(gzip?"-gzip":"-plain");Path inputs=root.resolve("sources with spaces α").resolve(label);Files.createDirectory(inputs);Path signal=inputs.resolve(gzip?"signal input α.gz":"signal input α.bedGraph"),sizes=inputs.resolve("reference input α.sizes");if(gzip)try(var out=new GZIPOutputStream(Files.newOutputStream(signal))){Files.copy(original.resolve("signal.bedGraph"),out);}else Files.copy(original.resolve("signal.bedGraph"),signal);Files.copy(original.resolve("reference.sizes"),sizes);Path bundle=root.resolve("cases").resolve(label);
            var command=new ArrayList<>(List.of("bigwig-tools","--input",signal.toString(),"--sizes",sizes.toString(),"--genome-build","synthetic-reference-control","--units","supplied synthetic float64","--float32",Boolean.TRUE.equals(options.get("requireExactFloat32"))?"exact":"round","--dictionary",Boolean.TRUE.equals(options.get("includeUnusedChromosomes"))?"all":"used","--compression",Boolean.TRUE.equals(options.get("compressed"))?"zlib":"none","--input-format",gzip?"gzip":"plain","--items-per-section",Long.toString(NextflowFiles.integer(options.get("itemsPerSection"),1,65535)),"--tree-fanout",Long.toString(NextflowFiles.integer(options.get("treeFanout"),2,1024)),"--output-bytes",Long.toString(NextflowFiles.integer(options.get("maximumBytes"),256,1099511627776L)),"--out",bundle.toString()));
            var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int exit=JLinAlgCli.run(command.toArray(String[]::new),new PrintStream(out),new PrintStream(err));Files.write(root.resolve(label+".stdout"),out.toByteArray());Files.write(root.resolve(label+".stderr"),err.toByteArray());if(exit!=0)throw new AssertionError("Packaged public workflow failed: "+label+"\n"+err.toString(StandardCharsets.UTF_8));var expected=NextflowFiles.pin(original.resolve("native.bw"),50000000,()->{});if(!expected.equals(NextflowFiles.pin(bundle.resolve("track.bw"),50000000,()->{})))throw new AssertionError("Public output differs from complete independently read native prototype: "+label);var result=BigWigWorkflow.verify(bundle,()->{});int count=(int)NextflowFiles.integer(row.get("records"),0,10000000);if(result.report().records()!=count)throw new AssertionError("Complete public interval family differs");intervals+=count;records.add(Map.of("id",label,"bundle","cases/"+label,"complete_intervals",count,"byte_identical_to_independently_read_native_reference",true,"track",BigWigFiles.pin(expected),"input_format",gzip?"gzip":"plain","public_dispatch_exit",exit));
        }}
        if(records.size()!=44||intervals!=159896)throw new AssertionError("Incomplete packaged native workflow protocols");BigWigFiles.json(root.resolve("CONTROLS.json"),Map.of("status","COMPLETE_PACKAGED_PUBLIC_NATIVE_BIGWIG_WORKFLOWS","cases",records,"workflows",records.size(),"complete_intervals",intervals,"implementation_credit",false,"biological_accuracy_certified",false));System.out.println("COMPLETE_PUBLIC_NATIVE_BIGWIG_WORKFLOWS44 INTERVALS159896");
    }
}
