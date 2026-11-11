package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RsemRecipeTest {
    @TempDir Path temporary;
    private Map<String,Object> request() {
        var value = new LinkedHashMap<String,Object>(); value.put("schema_version", 1); value.put("source_version", "1.3.3"); value.put("build_home", "build"); value.put("build_receipt_sha256", "a".repeat(64)); value.put("library_directories", List.of("lib")); value.put("reference_prefix", "reference/sample"); value.put("alignment", "original.bam"); value.put("read_type", 3); value.put("read_type_tag", "");
        value.put("settings", new LinkedHashMap<String,Object>(Map.of("seed", 4294967295L, "worker_threads", 1, "strand", "NONE", "posterior", "NONE", "estimate_read_start", false, "append_names", false)));
        return value;
    }
    private RsemRecipe.Spec read(Map<String,Object> value) throws IOException { Path recipe = temporary.resolve("request.json"); Files.write(recipe, RsemJson.bytes(value)); return RsemRecipe.read(recipe); }
    @Test void explicitScientificSettingsAndRelativePathsAreRetained() throws Exception {
        var spec = read(request()); assertEquals(4294967295L, spec.settings().seed()); assertEquals("", spec.input().options().readTypeTag()); assertEquals(temporary.resolve("original.bam"), spec.input().alignment()); assertEquals(temporary.resolve("build"), spec.build()); assertEquals(16777216, spec.limits().inputs().fileBytes()); spec.verify();
        Files.writeString(spec.recipe(), "\n", java.nio.file.StandardOpenOption.APPEND); assertThrows(IOException.class, spec::verify);
    }
    @Test void allCapacityLevelsCanBeRaisedWithoutChangingOtherDefaults() throws Exception {
        var m = request(); m.put("limits", Map.of("inputs", Map.of("fileBytes", 5000000000L, "reference", Map.of("maxTranscripts", 200000)), "fit", Map.of("output", Map.of("model", Map.of("maxScalars", 500000L)))));
        var spec = read(m); assertEquals(5000000000L, spec.limits().inputs().fileBytes()); assertEquals(200000, spec.limits().inputs().reference().maxTranscripts()); assertEquals(500000, spec.limits().fit().output().model().maxScalars()); assertEquals(10000, spec.limits().audit().alignment().maxDistinctNames());
    }
    @Test void unknownFieldsAtEveryCapacityDepthAreRefused() throws Exception {
        for (Map<String,Object> limits : List.of(Map.<String,Object>of("ignored", 1), Map.<String,Object>of("fit", Map.of("output", Map.of("model", Map.of("ignored", 1)))))) { var m = request(); m.put("limits", limits); assertThrows(IOException.class, () -> read(m)); }
        var m = request(); m.put("ignored", true); assertThrows(IOException.class, () -> read(m));
    }
    @Test void zeroNegativeFractionalOverflowAndWrongTypeCapacitiesAreRefused() throws Exception {
        for (Object value : List.of(0, -1, 1.5, 2147483648L, "1000", true)) { var m = request(); m.put("limits", Map.of("inputs", Map.of("members", value))); assertThrows(IOException.class, () -> read(m), String.valueOf(value)); }
    }
    @Test void pairedFragmentSettingsCannotBeSilentlyIgnored() throws Exception {
        var m = request(); @SuppressWarnings("unchecked") var settings = (Map<String,Object>)m.get("settings"); settings.put("single_end_fragment", Map.of("mean", 200, "standard_deviation", 0)); assertThrows(IOException.class, () -> read(m));
        m.put("read_type", 1); var spec = read(m); assertEquals(0, spec.settings().singleEndFragment().standardDeviation());
    }
    @Test void wrongSourceMissingSettingsDuplicateRootsAndUnknownSettingsAreRefused() throws Exception {
        var m = request(); m.put("source_version", "1.3.4"); var wrongSource = m; assertThrows(IOException.class, () -> read(wrongSource));
        m = request(); m.remove("settings"); var absent = m; assertThrows(IOException.class, () -> read(absent));
        m = request(); m.put("library_directories", List.of("lib", "lib")); var duplicate = m; assertThrows(IOException.class, () -> read(duplicate));
        m = request(); @SuppressWarnings("unchecked") var settings = (Map<String,Object>)m.get("settings"); settings.put("ignored", true); var unknown = m; assertThrows(IOException.class, () -> read(unknown));
    }
    @Test void inadequateThreadCapacityAndMalformedReceiptAreRefused() throws Exception {
        var m = request(); m.put("limits", Map.of("fit", Map.of("process", Map.of("threads", 3)))); assertThrows(IOException.class, () -> read(m));
        var wrong = request(); wrong.put("build_receipt_sha256", "unreviewed"); assertThrows(IOException.class, () -> read(wrong));
    }
    @Test void exportedCompleteLimitsCanBeCopiedIntoARecipeUnchanged() throws Exception {
        var m = request(); m.put("limits", RepeatBundle.object(RsemJson.bytes(RsemRecipe.limitValues()))); assertEquals(RsemRecipe.defaults(), read(m).limits());
    }
}
