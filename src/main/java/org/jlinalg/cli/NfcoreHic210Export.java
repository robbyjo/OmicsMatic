/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete selected original Cooler arrays using the exact original Cooler image; no fitting. */
final class NfcoreHic210Export {
    private NfcoreHic210Export(){ }
    static final String URI="https://depot.galaxyproject.org/singularity/cooler:0.8.11--pyh3252c3a_0";
    static Map<String,Object> request(NfcoreHic210Files files,NfcoreHic210Request.Request r)throws IOException{
        var maps=new ArrayList<Map<String,Object>>();for(var sample:r.samples())for(long size:NfcoreHic210Request.mapResolutions(r)){Path blob=files.output("contact_maps/cool/"+sample.id()+"."+size+"_balanced.cool");maps.add(Map.of("sample",sample.id(),"bin_size",size,"file",files.producer+"/attempts/"+files.attempt.getFileName()+"/objects/"+blob.getFileName()));}
        return Map.of("schema_version",1,"maps",maps,"maximum_bins",r.auditLimits().maximumBins(),"maximum_pixels",r.auditLimits().maximumPixels());
    }
    static void run(Path root,Path attempt,NfcoreHic210Request.Request r,NfcoreHic210Files files,NextflowRecipe.Request recipe,NextflowProcess.Check integrity)throws IOException{
        Files.write(attempt.resolve("export_cooler.py"),NfcoreHic210Profile.resource("export_cooler.py",NfcoreHic210Profile.EXPORTER),StandardOpenOption.CREATE_NEW);writeJson(attempt.resolve("export-request.json"),request(files,r));Path admission=Files.createDirectory(attempt.resolve("export-runtime"));var runtime=new NfcoreContainerSet(Path.of(r.containerHome()),root.resolve("image-set.json"),admission,recipe.process(),NfcoreHic210Profile.RESOURCE+"IMAGES.json",NfcoreHic210Profile.IMAGES);var images=object(object(json(root.resolve("image-set.json"),1048576)).get("images"));String image=text(images.get(URI));
        var command=List.of(r.containerHome()+"/bin/apptainer","exec","--userns","--no-home","--pid","--cleanenv","-B",root+":"+root,image,"python",attempt.resolve("export_cooler.py").toString(),attempt.resolve("export-request.json").toString(),attempt.resolve("native-export").toString());NfcoreProcess.executeSession(command,runtime.environment(),attempt.resolve("export-process"),attempt,root,recipe.process(),()->{runtime.verify();integrity.verify();},()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Hi-C full array export interrupted");});runtime.verify();
    }
    static void verifyRequest(Path attempt,NfcoreHic210Files files,NfcoreHic210Request.Request r)throws IOException{
        if(!pin(attempt.resolve("export_cooler.py"),1048576,()->{}).equals(NfcoreHic210Profile.EXPORTER)||!utf8(bytes(attempt.resolve("export-request.json"),4194304)).equals(NextflowJson.json(request(files,r))+"\n"))throw new IOException("Original source map/whole-array export code or bindings differ");var process=object(json(attempt.resolve("export-process/process.json"),67108864));if(!"completed-process-only".equals(process.get("status"))||integer(process.get("exit_code"),0,255)!=0||!list(process.get("owned_live_after_cleanup")).isEmpty())throw new IOException("Original whole-array export process was not successfully complete");
        if(!files.producer.endsWith("/engine"))throw new IOException("Invalid original Hi-C producer binding");String producer=files.producer.substring(0,files.producer.length()-7),originalAttempt=producer+"/attempts/"+attempt.getFileName();Pin image=fingerprint(object(NfcoreHic210Profile.images().get(URI)).get("pin"),5000000000L);var wanted=List.of(r.containerHome()+"/bin/apptainer","exec","--userns","--no-home","--pid","--cleanenv","-B",producer+":"+producer,producer+"/materialized/images/"+image.sha256()+".sif","python",originalAttempt+"/export_cooler.py",originalAttempt+"/export-request.json",originalAttempt+"/native-export");var command=strings(process.get("command"));if(command.size()<wanted.size()||!command.subList(command.size()-wanted.size(),command.size()).equals(wanted))throw new IOException("Original complete array-export command/image/source binding differs");
    }
}
