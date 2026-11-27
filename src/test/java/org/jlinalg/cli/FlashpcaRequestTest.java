/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashpcaRequestTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> request(String mode)throws Exception{var r=(Map<String,Object>)SimpleJson.parse(NextflowJson.json(FlashpcaToolsCli.example(mode)));var base=java.nio.file.Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();r.put("runtime",base.resolve("private-runtime").toString());r.put("bed_prefix",base.resolve("data").toString());return r;}
    @SuppressWarnings("unchecked") private static Map<String,Object> settings(Map<String,Object> request){return (Map<String,Object>)request.get("settings");}
    @Test void explicitModernAndOriginalLegacyDefaultsAreDistinct()throws Exception{
        var modern=FlashpcaRequest.parse(request("pca"));var legacy=FlashpcaRequest.parse(request("legacy"));assertNotEquals(modern.profile(),legacy.profile());assertEquals("HIGH",legacy.settings().get("legacy_memory"));assertEquals("EIGEN",legacy.settings().get("legacy_method"));assertNull(modern.settings().get("legacy_memory"));
    }
    @Test void legacyParametersCannotLeakIntoModernOnlineMode()throws Exception{var r=request("pca");settings(r).put("legacy_force_transpose",false);assertThrows(Exception.class,()->FlashpcaRequest.parse(r));}
    @Test void unknownFieldsAndUnreportedModeAreRefused()throws Exception{var r=request("legacy");settings(r).put("legacy_method","AUTOMATIC_REPAIR");assertThrows(Exception.class,()->FlashpcaRequest.parse(r));var changed=request("pca");changed.put("ignore_failed_qc",true);assertThrows(Exception.class,()->FlashpcaRequest.parse(changed));}
    @Test void originalCategoricalOrNonlinearModelsCannotBeSubstituted()throws Exception{var r=request("legacy");settings(r).put("standardization","BINOM2");assertThrows(Exception.class,()->FlashpcaRequest.parse(r));}
}
