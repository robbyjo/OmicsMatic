/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import ij.IJ;
import java.awt.GraphicsEnvironment;

/** Original version loading only; constructs no image and performs no analysis. */
public final class ImageJReadiness {
    private ImageJReadiness() { }
    public static void main(String[] args) {
        if (!IJ.getVersion().equals("1.54t")) throw new IllegalStateException("Original ImageJ1.54t required");
        System.out.println("READY_ORIGINAL_IMAGEJ154T_NOT_ANALYSIS version=" + IJ.getVersion()
                + " full=" + IJ.getFullVersion() + " headless=" + GraphicsEnvironment.isHeadless());
    }
}
