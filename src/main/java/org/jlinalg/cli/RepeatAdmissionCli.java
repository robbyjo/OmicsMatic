/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Explicit fixed-health admission; installation/download remain separate authorized actions. */
final class RepeatAdmissionCli {
    private RepeatAdmissionCli() { }
    static int run(Path supplied,PrintStream out,PrintStream err,Runnable check){
        try{
            Path source=supplied.toAbsolutePath().normalize();
            if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||!source.toRealPath().equals(source))throw new IOException("Admission options require a canonical regular UTF8 file");
            byte[] raw=AdmixtureInputs.raw(source,1048576);Map<?,?> values=RepeatBundle.object(raw);
            if(!values.keySet().equals(Set.of("profile","payload_home","python","out")))throw new IOException("Admission options require exactly profile,payload_home,python,out;python must be explicit null for the current profile");
            String profile=text(values,"profile");Path payload=Path.of(text(values,"payload_home")),target=Path.of(text(values,"out"));
            Path python=values.get("python")==null?null:Path.of(text(values,"python"));
            RepeatProcess.Check integrity=()->{if(!RepeatInputs.hash(source,1048576,check).equals(AdmixtureInputs.digest(raw)))throw new IOException("Admission options changed;retain partials and use a NEW runtime home");};
            integrity.verify();var context=RepeatRuntime.admit(payload,profile,python,target,check,integrity);
            emit(out,"Original runtime admitted: "+RepeatJson.json(Map.of("home",context.home(),"profile",context.payload().profile(),"manifest_sha256",context.manifestSha256(),"study_search_completed",false)));
            emit(err,"RUNTIME WARNINGS: "+context.home().resolve("WARNINGS.md")+"\n"+RepeatRuntime.recovery());return 0;
        }catch(RepeatRuntime.Unavailable|RepeatPayload.Unavailable|RepeatProcess.UnavailableDependency missing){emit(err,"SKIP3 ORIGINAL ADMISSION UNAVAILABLE: "+missing+"\n"+RepeatRuntime.recovery());return 3;}
        catch(Exception failed){emit(err,"FAIL2 ORIGINAL ADMISSION FAILED: "+failed+"\n"+RepeatRuntime.recovery());return 2;}
    }
    private static String text(Map<?,?> values,String key)throws IOException{
        if(!(values.get(key) instanceof String text)||text.isBlank()||text.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require nonblank admission text: "+key);return text;
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
