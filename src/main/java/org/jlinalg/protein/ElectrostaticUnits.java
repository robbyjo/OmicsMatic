/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

/** Explicit electrostatic-potential conversion using exact SI2019 k_B and elementary charge.
 * Conversion does not infer a calculation temperature or reproduce historical CODATA rounding.
 */
public final class ElectrostaticUnits {
    private ElectrostaticUnits() { }
    /** Exact SI Boltzmann constant, joules per kelvin. */
    public static final double BOLTZMANN_JOULE_PER_KELVIN=1.380649e-23;
    /** Exact SI elementary charge, coulombs. */
    public static final double ELEMENTARY_CHARGE_COULOMB=1.602176634e-19;
    /** Converts one dimensionless kT/e potential to volts, with an explicit kelvin temperature. */
    public static double ktPerEToVolts(double potential,double temperatureKelvin) {
        check(potential,temperatureKelvin);return potential*(BOLTZMANN_JOULE_PER_KELVIN/ELEMENTARY_CHARGE_COULOMB)*temperatureKelvin;
    }
    /** Converts volts to dimensionless kT/e, with an explicit kelvin temperature. */
    public static double voltsToKtPerE(double volts,double temperatureKelvin) {
        check(volts,temperatureKelvin);double result=volts/(BOLTZMANN_JOULE_PER_KELVIN/ELEMENTARY_CHARGE_COULOMB)/temperatureKelvin;
        if(!Double.isFinite(result))throw new IllegalArgumentException("Potential/temperature conversion exceeds finite numeric range");return result;
    }
    private static void check(double value,double temperature){if(!Double.isFinite(value) || Math.abs(value)>1e100 || !Double.isFinite(temperature) || temperature<=0 || temperature>1e6)throw new IllegalArgumentException("Require finite abs(potential)<=1e100 and explicit temperature in(0,1e6]kelvin");}
}
