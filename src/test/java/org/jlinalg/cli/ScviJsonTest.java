package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
class ScviJsonTest {
    @Test void processLimitsSerializeAsStructuredOwnedRecordsOnEveryHost()throws Exception{Object parsed=SimpleJson.parse(ScviJson.json(Map.of("limits",ScviProcess.Limits.health())));var limits=(Map<?,?>)((Map<?,?>)parsed).get("limits");assertEquals(180000L,limits.get("wallMillis"));assertEquals(17179869184L,limits.get("addressSpaceBytes"));}
    @Test void nestedMapsAndSetsHaveCanonicalOrdering()throws Exception{var a=new LinkedHashMap<String,Object>();a.put("z",Set.of("b","a"));a.put("a",Map.of("y",2,"x",1));var b=new LinkedHashMap<String,Object>();b.put("a",Map.of("x",1,"y",2));b.put("z",Set.of("a","b"));assertEquals(ScviJson.json(a),ScviJson.json(b));}
    @Test void unownedRecordsAndNonfiniteNumbersAreRejected(){assertThrows(IOException.class,()->ScviJson.json(Thread.State.RUNNABLE.getClass()));assertThrows(IOException.class,()->ScviJson.json(Double.NaN));assertThrows(IOException.class,()->ScviJson.json(Float.POSITIVE_INFINITY));}
    @Test void objectKeysCannotCoerceDifferentValuesIntoOneTextKey(){assertThrows(IOException.class,()->ScviJson.json(Map.of(1,"integer","1","text")));}
    @Test void nullAndLiteralTextRemainDistinctAndEscaped()throws Exception{var m=new HashMap<String,Object>();m.put("literal","null\n\"β\"");m.put("missing",null);var parsed=(Map<?,?>)SimpleJson.parse(ScviJson.json(m));assertNull(parsed.get("missing"));assertEquals("null\n\"β\"",parsed.get("literal"));}
}
