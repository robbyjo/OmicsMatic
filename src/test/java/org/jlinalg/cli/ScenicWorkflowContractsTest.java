package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

class ScenicWorkflowContractsTest {
    static Map<String,Object> raw(){return map("schema_version",1,"mode","RUN","engine","upstream-and-native","profile",ScenicWorkflowContracts.PROFILE,"operation","NETWORK","python","python","out","out",
        "inputs",map("fit_expression","fit.tsv","activity_expression","activity.tsv","tfs","tfs.txt","annotations","motifs.tbl","databases",List.of(map("name","db","file","genes.feather"))),"options",map("seed",17));}
    static Map<String,Object> valid(Map<String,Object> raw)throws IOException {return ScenicWorkflowContracts.validate(raw,"/prepared","POSIX");}
    @Test void fittingAndActivityRemainDistinctAndSourceDefaultsExplicit()throws Exception {
        var request=valid(raw());assertNotEquals(object(request.get("inputs")).get("fit_expression"),object(request.get("inputs")).get("activity_expression"));
        var options=object(request.get("options"));assertEquals(17,number(options,"seed"));assertEquals("GRNBOOST2",options.get("method"));assertEquals(20,number(object(options.get("modules")),"min_genes"));
        assertEquals("SERIAL_UNSTACK",object(options.get("activity")).get("layout"));assertEquals(false,object(options.get("activity")).get("no_weights"));assertEquals(66,object(request.get("expected_packages")).size());
    }
    @Test void seedAndWorkerBudgetsCannotBeInferredOrSilentlyReduced()throws Exception {
        var missing=raw();missing.put("options",map());assertThrows(IOException.class,()->valid(missing));
        var workers=raw();workers.put("options",map("seed",17,"workers",2));assertThrows(IOException.class,()->valid(workers));
        workers.put("limits",map("threads",2));assertEquals(2,number(object(valid(workers).get("options")),"workers"));
    }
    @Test void individualModuleHeuristicsCanBeDisabledWithoutChangingOthers()throws Exception {
        var request=raw();request.put("options",map("seed",17,"modules",map("thresholds",List.of(),"top_n_regulators",List.of())));
        var modules=object(object(valid(request).get("options")).get("modules"));assertTrue(list(modules.get("thresholds")).isEmpty());assertEquals(List.of(50L),modules.get("top_n_targets"));
        request.put("options",map("seed",17,"modules",map("thresholds",List.of(),"top_n_targets",List.of(),"top_n_regulators",List.of())));assertThrows(IOException.class,()->valid(request));
    }
    @Test void unknownOptionsDuplicateResourcesAndProbabilityOverflowAreRefused()throws Exception {
        var unknown=raw();unknown.put("options",map("seed",17,"silently_filter",true));assertThrows(IOException.class,()->valid(unknown));
        var duplicate=raw();object(duplicate.get("inputs"));var inputs=object(duplicate.get("inputs"));inputs.put("databases",List.of(map("name","db","file","a"),map("name","db","file","b")));duplicate.put("inputs",inputs);assertThrows(IOException.class,()->valid(duplicate));
        var bounds=raw();bounds.put("options",map("seed",17,"ctx",map("motif_similarity_fdr",1.1)));assertThrows(IOException.class,()->valid(bounds));
    }
    @Test void literalIdentifiersAndExplicitSpecialNumbersKeepTheirMeaning()throws Exception {
        assertEquals("NA",ScenicWorkflowContracts.label("NA"));assertEquals(" TF ",ScenicWorkflowContracts.label(" TF "));assertEquals("\ud800\udc00",ScenicWorkflowContracts.label("\ud800\udc00"));
        assertThrows(IOException.class,()->ScenicWorkflowContracts.label("x\ty"));assertThrows(IOException.class,()->ScenicWorkflowContracts.label("\ud800"));
        assertEquals(Double.doubleToRawLongBits(-0.),Double.doubleToRawLongBits(ScenicWorkflowActivity.inputNumber("-0.0")));
        assertTrue(Double.isNaN(ScenicWorkflowActivity.inputNumber("NaN")));assertEquals(Double.POSITIVE_INFINITY,ScenicWorkflowActivity.inputNumber("+Infinity"));
        for(String value:List.of("inf","nan","1_000","0x1p2"," 1 ","1e9999"))assertThrows(IOException.class,()->ScenicWorkflowActivity.inputNumber(value));
    }
}
