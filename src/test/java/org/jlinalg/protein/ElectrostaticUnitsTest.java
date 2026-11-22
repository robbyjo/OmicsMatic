/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ElectrostaticUnitsTest {
    @Test void thermalVoltageMatchesIndependentSiRatio() {assertEquals(0.02569257912108585,ElectrostaticUnits.ktPerEToVolts(1,298.15),1e-17);assertEquals(-0.12846289560542925,ElectrostaticUnits.ktPerEToVolts(-5,298.15),1e-16);assertEquals(0,ElectrostaticUnits.ktPerEToVolts(0,310));}
    @Test void signedRoundTripsAndTemperatureScalingAreExplicit() {for(double temperature:new double[]{180,298.15,310,1000})for(double value:new double[]{-100,-.5,0,.5,100})assertEquals(value,ElectrostaticUnits.voltsToKtPerE(ElectrostaticUnits.ktPerEToVolts(value,temperature),temperature),1e-12);assertEquals(2*ElectrostaticUnits.ktPerEToVolts(1,200),ElectrostaticUnits.ktPerEToVolts(1,400));}
    @Test void invalidPotentialAndKelvinValuesFail() {for(double temperature:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,1e7})assertThrows(IllegalArgumentException.class,()->ElectrostaticUnits.ktPerEToVolts(1,temperature));assertThrows(IllegalArgumentException.class,()->ElectrostaticUnits.ktPerEToVolts(Double.NaN,298));assertThrows(IllegalArgumentException.class,()->ElectrostaticUnits.voltsToKtPerE(Double.POSITIVE_INFINITY,298));}
}
