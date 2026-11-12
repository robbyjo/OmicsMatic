/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Malt052AssignmentTest {
 @Test void zeroRequestedIdentityDoesNotAdmitNanOrNegativeHitIdentity() {
  var matches=List.of(new Malt052Assignment.Match(9606,Float.POSITIVE_INFINITY,1,Float.NaN,false),new Malt052Assignment.Match(8574,100,0,99,false),new Malt052Assignment.Match(37349,100,0,-1,false));
  assertEquals(BitSet.valueOf(new long[]{2}),Malt052Assignment.activeMatches(matches,new Malt052Assignment.Filter(50,1,1,0),Set.of()));
 }
 @Test void unknownHitIdentityBypassesBothTheInitialIdentityGateAndConditionalTopFilter() {
  var matches=List.of(new Malt052Assignment.Match(9606,50,0,0,false),new Malt052Assignment.Match(8574,100,0,100,false));
  assertEquals(BitSet.valueOf(new long[]{3}),Malt052Assignment.activeMatches(matches,new Malt052Assignment.Filter(50,1,1,99),Set.of()));
 }
 @Test void nanRequestedIdentityRetainsOnlyUnknownIdentityAndPreservesDisabledFiltering() {
  var matches=List.of(new Malt052Assignment.Match(9606,50,0,0,false),new Malt052Assignment.Match(8574,100,0,Float.POSITIVE_INFINITY,false));var filter=new Malt052Assignment.Filter(50,1,1,Float.NaN);
  assertEquals(BitSet.valueOf(new long[]{1}),Malt052Assignment.activeMatches(matches,filter,Set.of()));assertTrue(Malt052Assignment.activeMatches(matches,filter,Set.of(9606)).isEmpty());
 }
}
