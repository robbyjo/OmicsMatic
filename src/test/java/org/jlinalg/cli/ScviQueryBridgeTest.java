/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScviQueryBridgeTest {
    @TempDir Path temp;
    private static ScviModelMetadata.Scalar text(String x){return new ScviModelMetadata.Scalar("string",x);}
    private static ScviModelMetadata.Scalar integer(int x){return new ScviModelMetadata.Scalar("integer",Integer.toString(x));}
    private ScviCovariates.Prepared query()throws IOException{
        var recipe=ScviRecipe.read(Path.of("examples/scvi-workflow/transfer-scanvi-shared.json"));var input=recipe.query();var matrix=ScviMatrix.prepare(input.matrix(),temp.resolve("matrix-"+UUID.randomUUID()),()->{});return ScviCovariates.prepare(matrix,input.metadata(),input.covariates(),temp.resolve("metadata-"+UUID.randomUUID()),()->{});
    }
    private static ScviModelMetadata.Info reference(boolean numeric,boolean renamed){
        var labels=numeric?List.of(integer(2),integer(1),integer(9)):List.of(text("type_B"),text("type_A"),text("Unlabeled"));String batch=renamed?"reference_batch":"batch",site=renamed?"reference_site":"site",age=renamed?"reference_age":"age",label=renamed?"reference_label":"label";
        return new ScviModelMetadata.Info(null,null,ScviModelRequest.Family.SCANVI,"1.3.0",true,List.of("g0","g1"),32,"layers","counts",batch,numeric?List.of(integer(0),integer(1)):List.of(text("b0"),text("b1")),label,labels,labels.get(2),labels.subList(0,2),List.of(new ScviModelMetadata.Category(site,numeric?List.of(integer(1),integer(2),integer(3)):List.of(text("s0"),text("s1"),text("s2")))),List.of(age),Map.of("batch_key",batch,"labels_key",label),Map.of("n_latent",3),List.of(),Set.of("F32"),Map.of());
    }
    private static ScviQueryBridge.Request identity(){return new ScviQueryBridge.Request("counts",Map.of(),Map.of(),"Reviewed identical source roles and exact string categories;no units conversion");}
    @Test void exactStringRolesRetainEveryObservedSourceValue()throws Exception{
        var prepared=ScviQueryBridge.prepare(query(),"counts",reference(false,false),identity());assertEquals("batch",prepared.batch());assertEquals(List.of("site"),prepared.categorical());assertEquals(List.of("age"),prepared.continuous());assertTrue(ScviJson.json(prepared.protocol()).contains("query_batch"));assertTrue(ScviJson.json(prepared.protocol()).contains("Unlabeled"));
    }
    @Test void numericReferenceCategoriesNeverBecomeImplicitStringCasts()throws Exception{
        var failure=assertThrows(IOException.class,()->ScviQueryBridge.prepare(query(),"counts",reference(true,false),identity()));assertTrue(failure.getMessage().contains("typed value mapping"));
    }
    @Test void explicitTypedMappingsCoverActualQueryBatchLabelAndCovariates()throws Exception{
        var values=Map.of("batch",List.of(new ScviQueryBridge.Value("query_batch",integer(2))),"label",List.of(new ScviQueryBridge.Value("Unlabeled",integer(9))),"site",List.of(new ScviQueryBridge.Value("s0",integer(1)),new ScviQueryBridge.Value("s1",integer(2)),new ScviQueryBridge.Value("s2",integer(3))));var p=ScviQueryBridge.prepare(query(),"counts",reference(true,false),new ScviQueryBridge.Request("counts",Map.of(),values,"Reviewed explicit typed categories against the genuine reference registry"));assertTrue(ScviJson.json(p.protocol()).contains("\"kind\":\"integer\""));
    }
    @Test void differingRoleNamesRequireAnExplicitCompleteRename()throws Exception{
        assertThrows(IOException.class,()->ScviQueryBridge.prepare(query(),"counts",reference(false,true),identity()));var rename=Map.of("batch","reference_batch","site","reference_site","age","reference_age","label","reference_label");var p=ScviQueryBridge.prepare(query(),"counts",reference(false,true),new ScviQueryBridge.Request("counts",rename,Map.of(),"Reviewed source-to-reference field mapping;continuous units agree"));assertEquals("reference_batch",p.batch());assertEquals(List.of("reference_age"),p.continuous());
    }
    @Test void wrongTargetLayerAndIncompleteValueCensusFailBeforeLoading()throws Exception{
        assertThrows(IOException.class,()->ScviQueryBridge.prepare(query(),"counts",reference(false,false),new ScviQueryBridge.Request("X",Map.of(),Map.of(),"Reviewed but incorrect target layer")));var incomplete=Map.of("site",List.of(new ScviQueryBridge.Value("s0",text("s0"))));assertThrows(IOException.class,()->ScviQueryBridge.prepare(query(),"counts",reference(false,false),new ScviQueryBridge.Request("counts",Map.of(),incomplete,"Incomplete explicit category mapping")));
    }
    @Test void relabelingUnlabeledCellsAsAKnownClassCannotPassTheBridge()throws Exception{
        var values=Map.of("label",List.of(new ScviQueryBridge.Value("Unlabeled",text("type_A"))));assertThrows(IOException.class,()->ScviQueryBridge.prepare(query(),"counts",reference(false,false),new ScviQueryBridge.Request("counts",Map.of(),values,"Incorrect unlabeled mapping must fail")));
    }
    @Test void learnedReferenceCategoryOrderAndTypeMustSurviveTransfer()throws Exception{
        var reference=reference(false,false);ScviQueryBridge.verifyTransferredRegistry(reference,reference);var numeric=reference(true,false);assertThrows(IOException.class,()->ScviQueryBridge.verifyTransferredRegistry(reference,numeric));
    }
    @Test void preparedMappingsCannotBeMutatedThroughNestedProtocolCollections()throws Exception{
        var prepared=ScviQueryBridge.prepare(query(),"counts",reference(false,false),identity());Object values=prepared.protocol().get("values");assertInstanceOf(Map.class,values);assertThrows(UnsupportedOperationException.class,()->((Map<?,?>)values).clear());Object rows=((Map<?,?>)values).get("batch");assertInstanceOf(List.class,rows);assertThrows(UnsupportedOperationException.class,()->((List<?>)rows).clear());
    }
    @Test void originalCategoryCoercionOrMissingExtensionInvalidatesTransfer()throws Exception{
        var reference=reference(false,false);var bridge=ScviQueryBridge.prepare(query(),"counts",reference,identity());var failure=assertThrows(IOException.class,()->ScviQueryBridge.verifyTransferredRegistry(reference,reference,bridge));assertTrue(failure.getMessage().contains("coerced or omitted"));
    }
}
