/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PymolPotentialFigureTest {
    @TempDir Path root;
    @Test void actualOriginalRasterIsEmbeddedUnchangedWithUnitsAndTemperature()throws Exception {
        var f=PymolPotentialAuditTest.fixture(root.resolve("figure"));byte[] png=Files.readAllBytes(f.directory().resolve("surface.png"));var result=PymolPotentialFigure.write(f.directory(),f.recipe(),"kt-per-e",298.15);String svg=Files.readString(f.directory().resolve("figure.svg"));assertTrue(svg.contains("Electrostatic potential (kT/e)"));assertTrue(svg.contains("298.1500 K"));assertTrue(svg.contains(Base64.getEncoder().encodeToString(png)));assertEquals(1180,result.get("figure_height"));assertTrue(svg.contains("source PQR"));assertTrue(svg.contains("surface vertices"));
    }
    @Test void actualBodyPixelBoundsAndOpaqueRasterDimensionsAreRetained()throws Exception {
        var f=PymolPotentialAuditTest.fixture(root.resolve("pixels"));var result=PymolPotentialFigure.write(f.directory(),f.recipe(),"volt",0);assertEquals(1200,result.get("raw_width"));assertEquals(1000,result.get("raw_height"));assertEquals(List.of(229,193,968,821),result.get("foreground_pixel_bounds"));assertEquals(282417L,result.get("foreground_pixels"));
    }
    @Test void asymmetricRampPlacesItsMiddleColorAtThePhysicalScalarFraction()throws Exception {
        var f=PymolPotentialAuditTest.fixture(root.resolve("asymmetric"));f.recipe().put("levels",List.of(-10.,0.,5.));var result=PymolPotentialFigure.write(f.directory(),f.recipe(),"arbitrary",0);assertEquals(2./3.,(double)result.get("legend_middle_fraction"),1e-16);String svg=Files.readString(f.directory().resolve("figure.svg"));assertTrue(svg.contains("66.66666666666666%"));assertTrue(svg.contains("arbitrary units"));assertFalse(svg.contains("Declared temperature"));
    }
    @Test void probeOffsetAndDisplayRadiusPolicyAreExplicitInStandaloneLegend()throws Exception {
        var f=PymolPotentialAuditTest.fixture(root.resolve("offset"));f.recipe().put("ramp_above_mode",1);f.recipe().put("surface_radii","pymol");f.recipe().put("surface_solvent","accessible");PymolPotentialFigure.write(f.directory(),f.recipe(),"volt",0);String svg=Files.readString(f.directory().resolve("figure.svg"));assertTrue(svg.contains("original PyMOL display radii"));assertTrue(svg.contains("accessible surface"));assertTrue(svg.contains("one probe radius"));assertTrue(svg.contains("potential (V)"));
    }
    @Test void EmptyClippedTransparentAndWrongSizedImagesAreRejected()throws Exception {
        int i=0;for(String kind:List.of("empty","clipped","transparent","dimensions")){var f=PymolPotentialAuditTest.fixture(root.resolve("bad"+i++));int width=kind.equals("dimensions")?100:1200;var image=new BufferedImage(width,1000,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<1000;y++)for(int x=0;x<width;x++)image.setRGB(x,y,kind.equals("transparent")?0x00ffffff:kind.equals("clipped")?0xff000000:0xffffffff);ImageIO.write(image,"png",f.directory().resolve("surface.png").toFile());assertThrows(java.io.IOException.class,()->PymolPotentialFigure.write(f.directory(),f.recipe(),"volt",0));}
    }
    @Test void svgHasNoExternalImageDependencyAndCorrectCompleteViewbox()throws Exception {
        var f=PymolPotentialAuditTest.fixture(root.resolve("standalone"));PymolPotentialFigure.write(f.directory(),f.recipe(),"kt-per-e",298.15);String svg=Files.readString(f.directory().resolve("figure.svg"));assertTrue(svg.contains("viewBox=\"0 0 1200 1180\""));assertTrue(svg.contains("xlink:href=\"data:image/png;base64,"));assertFalse(svg.contains("<script"));assertFalse(svg.contains("file://"));assertFalse(svg.contains("href=\"https://"));
    }
}
