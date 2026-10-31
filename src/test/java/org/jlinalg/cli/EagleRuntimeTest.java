/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class EagleRuntimeTest {
    @TempDir Path temporary;
    private byte[] resource(String name) throws IOException {
        try (var stream=getClass().getResourceAsStream("/org/jlinalg/omics/"+name)) {
            if(stream==null)throw new IOException("Missing test resource "+name);return stream.readAllBytes();
        }
    }
    private Map<?,?> lock()throws IOException { return (Map<?,?>)SimpleJson.parse(new String(resource("eagle-runtime-lock.json"),StandardCharsets.UTF_8)); }
    private Map<?,?> contract(String label)throws IOException { return (Map<?,?>)((Map<?,?>)lock().get("health_contracts")).get(label); }
    private Path home()throws IOException {
        Path home=temporary.resolve("runtime-α-测");Files.createDirectory(home);Files.createDirectories(home.resolve("health/input"));
        for(String name:new String[]{"cohort-target.vcf","target.vcf.gz"})Files.write(home.resolve("health/input/"+name),resource("eagle-runtime-setup/eagle-runtime/health/"+name));
        return home;
    }
    private Path output(Path home,String label)throws IOException {
        var lines=Files.readAllLines(home.resolve("health/input/cohort-target.vcf"),StandardCharsets.UTF_8);var result=new ArrayList<String>();
        for(String line:lines){
            if(line.startsWith("#")){result.add(line);continue;}
            String[] fields=line.split("\t",-1);
            for(int i=9;i<fields.length;i++){
                String call=fields[i];
                if(call.contains("."))fields[i]=label.endsWith("impute")?"1|0":label.startsWith("reference")?"./.":call;
                else{String[] a=call.split("[/|]");fields[i]=a[1]+"|"+a[0];}
            }
            result.add(String.join("\t",fields));
        }
        Path output=home.resolve(label+".vcf");Files.write(output,result,StandardCharsets.UTF_8);return output;
    }
    @Test void packagedSetupAndLockAreByteExactAndComplete()throws Exception {
        assertArrayEquals(resource("eagle-runtime-lock.json"),resource("eagle-runtime-setup/eagle-runtime-lock.json"));
        for(String group:new String[]{"health_fixtures","bundled_notices"}){
            Map<?,?> entries=(Map<?,?>)lock().get(group);
            for(var entry:entries.entrySet()){
                String directory=group.equals("health_fixtures")?"health/":"notices/";
                byte[] bytes=resource("eagle-runtime-setup/eagle-runtime/"+directory+entry.getKey());
                Map<?,?> pin=(Map<?,?>)entry.getValue();assertEquals(((Number)pin.get("bytes")).longValue(),bytes.length);
                assertEquals(pin.get("sha256"),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
        }
        assertTrue(resource("eagle-runtime-setup/install_eagle.py").length>10000);
    }
    @Test void independentNativeHealthAcceptsFourExplicitPoliciesAndReverseOrientation()throws Exception {
        Path home=home();
        for(String label:new String[]{"cohort-keep","cohort-impute","reference-keep","reference-impute"})
            assertEquals(contract(label).get("normalized_sha256"),EagleRuntime.health(home,contract(label),output(home,label)));
    }
    @Test void nativeHealthRejectsCompleteButDosageChangedOutput()throws Exception {
        Path home=home(), output=output(home,"cohort-keep");
        String text=Files.readString(output);text=text.replaceFirst("1\\|0","1|1");Files.writeString(output,text);
        assertThrows(IOException.class,()->EagleRuntime.health(home,contract("cohort-keep"),output));
    }
    @Test void nativeHealthRejectsChangedPartialMissingCall()throws Exception {
        Path home=home(), output=output(home,"cohort-keep");
        Files.writeString(output,Files.readString(output).replace("0/.","./."));
        assertThrows(IOException.class,()->EagleRuntime.health(home,contract("cohort-keep"),output));
    }
    @Test void nativeHealthRejectsTruncatedAndExtraOutputRows()throws Exception {
        Path home=home(), output=output(home,"reference-impute");var lines=Files.readAllLines(output);String tail=lines.remove(lines.size()-1);Files.write(output,lines);
        assertThrows(IOException.class,()->EagleRuntime.health(home,contract("reference-impute"),output));
        lines.add(tail);lines.add(tail);Files.write(output,lines);
        assertThrows(IOException.class,()->EagleRuntime.health(home,contract("reference-impute"),output));
    }
    @Test void nativeHealthRejectsUnknownModeOrPolicy()throws Exception {
        Path home=home(), output=output(home,"cohort-keep");var invalid=new java.util.HashMap<Object,Object>();invalid.putAll(contract("cohort-keep"));invalid.put("mode","automatic");
        assertThrows(IOException.class,()->EagleRuntime.health(home,invalid,output));
        invalid.put("mode","cohort");invalid.put("missing","automatic");
        assertThrows(IOException.class,()->EagleRuntime.health(home,invalid,output));
    }
    @Test void unavailableHostOrMissingHomeIsDistinctFromAnIntegrityFailure()throws Exception {
        assertThrows(EagleRuntime.Unavailable.class,()->EagleRuntime.open(temporary.resolve("absent"),"linux-x86_64-eagle-2.4.1"));
    }
}
