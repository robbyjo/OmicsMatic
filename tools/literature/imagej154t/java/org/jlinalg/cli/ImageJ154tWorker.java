/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import ij.IJ;
import ij.Menus;
import ij.OmicsMaticImageJMenus;
import ij.Prefs;
import ij.macro.OmicsMaticBatchInterpreter;
import java.awt.AWTError;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.ImageJ154tRequest.*;

/** Private child entry point: the complete original interpreter executes the exact captured macro. */
public final class ImageJ154tWorker {
    private ImageJ154tWorker() { }
    public static void main(String[] args){
        int code=2;String cause="FAIL2_IMAGEJ154T_WORKER";Path out=null;OmicsMaticBatchInterpreter interpreter=null;ImageJ154tDiagnostics diagnostics=null;
        try{
            require(args.length==1,"Require owned work directory");out=Path.of(args[0]).toAbsolutePath().normalize();NextflowFiles.directory(out);var request=object(NextflowFiles.json(out.resolve("worker-request.json"),1048576));
            try{require(IJ.getVersion().equals("1.54t"),"Original core version differs");if(GraphicsEnvironment.isHeadless())throw new Prerequisite("AWT headless environment cannot initialize the original command registry");OmicsMaticImageJMenus.initialize();}catch(HeadlessException|AWTError|UnsatisfiedLinkError e){throw new Prerequisite("Original full AWT runtime/display is unavailable: "+e);}
            Prefs.setThreads((int)NextflowFiles.integer(request.get("threads"),1,256));IJ.redirectErrorMessages(true);diagnostics=new ImageJ154tDiagnostics(System.out);diagnostics.install();var registry=new TreeMap<String,String>();Map<?,?> original=Menus.getCommands();require(original!=null,"Original command registry is unavailable");for(var e:original.entrySet())registry.put(e.getKey().toString(),e.getValue().toString());NextflowFiles.writeJson(out.resolve("original-command-registry.json"),registry);
            for(String command:NextflowFiles.strings(request.get("required_commands")))if(!registry.containsKey(command))throw new Prerequisite("Required original/plugin command is unavailable: "+command+". Supply its permitted complete provider; no command is substituted");
            String macro=NextflowFiles.utf8(NextflowFiles.bytes(out.resolve("macro.ijm"),16777216));interpreter=new OmicsMaticBatchInterpreter();String returned=interpreter.run(macro,out.toString().replace('\\','/'));require(!interpreter.wasError()&&interpreter.getErrorMessage()==null,"Original macro error: "+interpreter.getErrorMessage());String originalError=IJ.getErrorMessage();require(originalError==null,"Original ImageJ reported an error: "+originalError);require(diagnostics.failures().isEmpty(),"Original command/thread exception was retained; no result admitted");
            if(request.get("expected_return")!=null)require(Objects.equals(text(request.get("expected_return")),returned),"Original macro returned a different completion value");
            var outputs=new LinkedHashMap<String,Object>();long remaining=NextflowFiles.integer(request.get("maximum_decoded_pixels"),1,1000000000000L);
            for(var e:object(request.get("expected_outputs")).entrySet()){
                Path file=out.resolve("results").resolve(member(e.getKey()));require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Missing expected output: "+e.getKey());var spec=object(e.getValue());var pin=NextflowFiles.pin(file,137438953472L,()->{});require(pin.bytes()>=NextflowFiles.integer(spec.get("minimum_bytes"),1,137438953472L),"Incomplete expected output: "+e.getKey());var row=new LinkedHashMap<String,Object>();row.put("file",pin);row.put("kind",spec.get("kind"));
                if(text(spec.get("kind")).equals("TIFF")){var state=ImageJ154tImageState.read(file,remaining);remaining-=((Number)state.get("complete_pixels")).longValue();row.put("decoded_image",state);}outputs.put(e.getKey(),row);
            }
            var result=new LinkedHashMap<String,Object>();result.put("status","PASS_ORIGINAL_IMAGEJ154T_MACRO");result.put("version",IJ.getVersion());result.put("macro_error",false);result.put("return_value",returned);result.put("outputs",outputs);result.put("command_registry_size",registry.size());result.put("original_source_patched",false);result.put("error_presentation_redirected",true);NextflowFiles.writeJson(out.resolve("worker-status.json"),result);code=0;cause="PASS_ORIGINAL_IMAGEJ154T_MACRO";
        }catch(Prerequisite e){cause="SKIP3: "+e.getMessage();code=3;}catch(Throwable e){cause="FAIL2: "+e;code=2;e.printStackTrace(System.err);}
        try{if(out!=null&&code!=0){var result=new LinkedHashMap<String,Object>();result.put("status",cause);result.put("exit_code",code);result.put("macro_error",interpreter!=null&&interpreter.wasError());result.put("original_error",interpreter==null?null:interpreter.getErrorMessage());result.put("original_diagnostic",interpreter==null?null:interpreter.diagnostic());result.put("recorded_original_failures",diagnostics==null?List.of():diagnostics.failures());result.put("result_admitted",false);NextflowFiles.writeJson(out.resolve("worker-status.json"),result);}}catch(IOException e){e.printStackTrace(System.err);code=2;}
        System.err.println(cause);System.exit(code);
    }
    private static final class Prerequisite extends IOException {private static final long serialVersionUID=1L;Prerequisite(String s){super(s);}}
}
