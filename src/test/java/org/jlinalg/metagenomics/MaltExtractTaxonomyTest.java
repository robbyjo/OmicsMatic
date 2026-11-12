/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaltExtractTaxonomyTest {
 private static MaltExtractTaxonomy read(String text)throws IOException {return MaltExtractTaxonomy.read(new StringReader(text),new MaltExtractTaxonomy.Limits(10000,100,20));}
 @Test void retainsEveryCanonicalNameParentDepthChildCountAndExplicitBranchBit()throws Exception {
  var tree=read("((2:1.25e1,3:-0.0)4:0.5,5:1)1:2;");assertArrayEquals(new int[]{1,4,2,3,5},tree.ids());assertEquals(5,tree.size());assertEquals(5,tree.explicitLengths());assertEquals(1,tree.root());assertEquals(4,tree.parent(2));assertEquals(1,tree.parent(4));assertEquals(0,tree.parent(1));assertEquals(2,tree.depth(3));assertEquals(2,tree.childCount(1));assertEquals(0,tree.childCount(2));assertEquals(12.5,tree.length(2));assertEquals(Double.doubleToRawLongBits(-0.0),Double.doubleToRawLongBits(tree.length(3)));assertFalse(tree.contains(-1));
 }
 @Test void absenceOfLengthRemainsAbsentAndNoSyntheticTaxaAppear()throws Exception {var tree=read("((2,3)4,5)1;");assertEquals(5,tree.size());assertEquals(0,tree.explicitLengths());assertFalse(tree.hasLength(2));assertThrows(IllegalArgumentException.class,()->tree.length(2));assertFalse(tree.contains(-2));}
 @Test void malformedTruncatedDuplicateAndNoncanonicalTreesRefuseAsCompleteInputs() {
  for(String text:new String[]{"","(2:1,3:1)1:","(2:1,3:1)1:;","(2:1,2:1)1:1;","(02:1,3:1)1:1;","(2:NaN,3:1)1:1;","(2:1e999,3:1)1:1;","(2:1,3:1)1:1;garbage","(2:1,3:1)1:1","(2:1,)1:1;","(2[&&NHX:S=x]:1,3:1)1:1;"})assertThrows(IOException.class,()->read(text),text);
 }
 @Test void completeCharacterNodeAndDepthCapacitiesRefuseRatherThanTakingAPrefix() {
  String text="((2:1,3:1)4:1,5:1)1:1;";for(var caps:new MaltExtractTaxonomy.Limits[]{new MaltExtractTaxonomy.Limits(10,100,20),new MaltExtractTaxonomy.Limits(10000,4,20),new MaltExtractTaxonomy.Limits(10000,100,1)})assertThrows(IOException.class,()->MaltExtractTaxonomy.read(new StringReader(text),caps));
 }
}
