/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package ij;

/** Access the original package-private initialization without an ImageJ window/startup runner. */
public final class OmicsMaticImageJMenus {
    private OmicsMaticImageJMenus() { }
    public static void initialize(){IJ.init();}
}
