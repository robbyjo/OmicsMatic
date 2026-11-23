from pathlib import Path
from handover_inventory_20261122_111 import pin,save
T=Path('D:/projects/JLinAlg/build/fu-1');O=Path('D:/projects/JLinAlg/build/git-publish/20261123-mrpresso-qualification-162')
source=T/'src/test/java/org/jlinalg/mrpresso/MrPressoOriginalTest.java'
assert pin(source)['sha256']=='d6c42d6545423938bec47ccd4d8ebddd85ae79844fbc9cf0bdeed38fde5286cf'
raw=source.read_text();(O/'MrPressoOriginalTest-before.java').write_bytes(source.read_bytes())
start=raw.index('    private static void compareFit');helpers=raw[start:raw.rindex('}')].replace('private static','static')
old='''        if(Double.isNaN(expected))assertTrue(Double.isNaN(actual),name);
        else assertEquals(expected,actual,Math.max(3e-13,Math.abs(expected)*3e-11),name);'''
new='''        if(Double.isNaN(expected))assertTrue(Double.isNaN(actual),name);
        else if(Double.isInfinite(expected))assertEquals(expected,actual,name);
        else {
            assertTrue(Double.isFinite(actual),name+" must stay finite");
            if(expected==0||actual==0)assertEquals(expected,actual,0,name+" numeric zero");
            else assertEquals(expected,actual,Math.abs(expected)*3e-11+32*Math.max(Math.ulp(expected),Math.ulp(actual)),name);
        }'''
assert old in helpers;helpers=helpers.replace(old,new)
header='''/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mrpresso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.junit.jupiter.api.Assertions.*;

/** Typed original-R reference assertions; finite comparisons have no absolute floor. */
final class MrPressoReferenceAssertions {
    private MrPressoReferenceAssertions() { }
'''
target=source.with_name('MrPressoReferenceAssertions.java');target.write_text(header+helpers+'}\n',encoding='utf-8')
raw=raw[:start]+'}\n';raw=raw.replace('import static org.junit.jupiter.api.Assertions.*;','import static org.junit.jupiter.api.Assertions.*;\nimport static org.jlinalg.mrpresso.MrPressoReferenceAssertions.*;')
source.write_text(raw,encoding='utf-8')
save(O/'TEST-ASSERTION-REFINEMENT.json',dict(previous_test=pin(O/'MrPressoOriginalTest-before.java'),
 replacement_test=pin(source),shared_assertions=pin(target),finite_relative_tolerance=3e-11,finite_ulp_tolerance=32,numeric_zero_exact=True,
 purpose='Remove a fixed absolute floor from original-reference comparisons, including tiny-unit coefficients and standard errors.',new_family_credit=0,new_reviews=0))
print('Prepared shared original-reference assertions with relative/ULP finite comparisons and exact numeric zeros.')
