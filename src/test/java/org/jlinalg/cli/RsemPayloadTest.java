package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RsemPayloadTest {
    @Test void fixed_original_controls_retain_every_golden_scientific_file_and_the_historical_failures()throws Exception{
        byte[] raw;try(var input=RsemPayloadTest.class.getResourceAsStream("/org/jlinalg/omics/rsem-health/inputs.zip")){
            assertNotNull(input);raw=input.readNBytes(1000001);
        }
        assertEquals("742c4d5feb9118d5bed6b9c6e2b6f5ae89b7924f083be490823f6045fe123338",AdmixtureInputs.digest(raw));
        var files=new HashMap<String,byte[]>();
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(raw))){
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){assertNull(files.put(entry.getName(),zip.readNBytes(1000001)));}
        }
        assertEquals(8,files.size());int count=0;
        for(String line:new String(files.get("SHA256SUMS"),StandardCharsets.UTF_8).split("\n")){
            String[] fields=line.split("  ",2);assertEquals(fields[0],AdmixtureInputs.digest(files.get(fields[1])));count++;
        }
        assertEquals(7,count);Map<?,?> golden=RepeatBundle.object(files.get("expected-science.json"));
        Map<?,?> cases=(Map<?,?>)golden.get("cases"),references=(Map<?,?>)golden.get("references");assertEquals(22,cases.size());assertEquals(5,references.size());
        assertEquals(364,cases.values().stream().mapToInt(value->((Map<?,?>)value).size()).sum());
        assertEquals(30,references.values().stream().mapToInt(value->((Map<?,?>)value).size()).sum());
        assertEquals(List.of("1.2.28","1.3.0","1.3.1"),golden.get("expected_old_no_alignment_failures"));
        for(String older:List.of("1.2.28","1.3.0","1.3.1"))assertFalse(cases.containsKey(older+"/noise"));
        for(String fixed:List.of("1.3.2","1.3.3"))assertTrue(cases.containsKey(fixed+"/noise"));
    }
    @Test void everySupportedSourceProfileHasItsOwnPinnedArchiveAndExactScriptPopulation()throws Exception{
        var profiles=RsemPayload.profiles();assertEquals(Set.of("1.2.28","1.3.0","1.3.1","1.3.2","1.3.3"),profiles.keySet());
        assertEquals(5,profiles.values().stream().map(RsemPayload.Profile::commit).distinct().count());
        assertEquals(5,profiles.values().stream().map(RsemPayload.Profile::archiveSha256).distinct().count());
        for(var profile:profiles.values())assertEquals(Set.of("rsem-calculate-expression","rsem-prepare-reference","rsem_perl_utils.pm"),profile.scripts().keySet());
        assertThrows(UnsupportedOperationException.class,()->profiles.clear());assertThrows(UnsupportedOperationException.class,()->profiles.get("1.3.3").scripts().clear());
    }
    @Test void the_original_shared_version_diagnostic_cannot_replace_source_release_selection()throws Exception{
        var profiles=RsemPayload.profiles();assertEquals("RSEM v1.2.28",profiles.get("1.2.28").reportedVersion());
        for(String version:List.of("1.3.1","1.3.2","1.3.3"))assertEquals("RSEM v1.3.1",profiles.get(version).reportedVersion());
        assertNotEquals(profiles.get("1.3.1").scripts().get("rsem-calculate-expression"),profiles.get("1.3.3").scripts().get("rsem-calculate-expression"));
    }
    @Test void audit_records_preserve_original_profile_and_refuse_nonfinite_or_unsupported_values()throws Exception{
        var profile=RsemPayload.profiles().get("1.3.3");Map<?,?> value=RepeatBundle.object(RsemJson.bytes(Map.of("source",profile,"scientific_runtime_admitted",false)));
        assertEquals(Boolean.FALSE,value.get("scientific_runtime_admitted"));Map<?,?> source=(Map<?,?>)value.get("source");
        assertEquals("1.3.3",source.get("version"));assertEquals("RSEM v1.3.1",source.get("reportedVersion"));
        assertThrows(IOException.class,()->RsemJson.json(Double.NaN));assertThrows(IOException.class,()->RsemJson.json(new byte[]{1,2}));
    }
}
