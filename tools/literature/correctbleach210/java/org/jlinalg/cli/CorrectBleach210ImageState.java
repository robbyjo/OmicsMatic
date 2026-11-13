/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import ij.ImagePlus;
import ij.io.Opener;
import ij.io.RoiEncoder;
import ij.io.TiffDecoder;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static org.jlinalg.cli.CorrectBleach210Request.require;

/** Complete decoded pixels and explicit original image axes/calibration/ROI diagnostics. */
final class CorrectBleach210ImageState {
    private CorrectBleach210ImageState() { }
    static ImagePlus open(Path file,long maximum)throws IOException {
        var headers=new TiffDecoder(file.getParent().toString()+java.io.File.separator,file.getFileName().toString()).getTiffInfo();require(headers!=null&&headers.length>0,"Original TIFF header unavailable");long count=0;
        for(var h:headers){require(h.width>0&&h.height>0,"Invalid original TIFF dimensions");count=Math.addExact(count,Math.multiplyExact(Math.multiplyExact((long)h.width,h.height),Math.max(1,h.nImages)));require(count<=maximum,"Complete TIFF header pixel capacity exceeded before decoding");}
        ImagePlus image=new Opener().openImage(file.toString());require(image!=null,"Original complete TIFF decoding failed");require(Math.multiplyExact(Math.multiplyExact((long)image.getWidth(),image.getHeight()),image.getStackSize())<=maximum,"Complete decoded TIFF pixel capacity exceeded");return image;
    }
    static Map<String,Object> state(ImagePlus image)throws IOException {
        var result=new LinkedHashMap<String,Object>();var planes=new ArrayList<Object>();MessageDigest complete=digest();
        for(int i=1;i<=image.getStackSize();i++){
            var ip=image.getStack().getProcessor(i);Object pixels=ip.getPixels();MessageDigest plane=digest();int count=0;
            if(pixels instanceof byte[] a){plane.update(a);complete.update(a);count=a.length;}else if(pixels instanceof short[] a){for(short v:a)put(plane,complete,v&65535,2);count=a.length;}else if(pixels instanceof float[] a){for(float v:a)put(plane,complete,Float.floatToRawIntBits(v),4);count=a.length;}else if(pixels instanceof int[] a){for(int v:a)put(plane,complete,v,4);count=a.length;}else throw new IOException("Unknown original pixel array");
            require(count==(long)image.getWidth()*image.getHeight(),"Incomplete original decoded plane");var row=new LinkedHashMap<String,Object>();row.put("slice",i);row.put("label",image.getStack().getSliceLabel(i));row.put("complete_pixels",count);row.put("pixel_class",pixels.getClass().getName());row.put("raw_pixel_sha256",HexFormat.of().formatHex(plane.digest()));row.put("display_min",Double.toHexString(ip.getMin()));row.put("display_max",Double.toHexString(ip.getMax()));
            if(ip.getColorModel() instanceof IndexColorModel lut){int n=lut.getMapSize();byte[] r=new byte[n],g=new byte[n],b=new byte[n],a=new byte[n];lut.getReds(r);lut.getGreens(g);lut.getBlues(b);lut.getAlphas(a);row.put("lut_rgba",List.of(Base64.getEncoder().encodeToString(r),Base64.getEncoder().encodeToString(g),Base64.getEncoder().encodeToString(b),Base64.getEncoder().encodeToString(a)));}planes.add(row);
        }
        result.put("dimensions_xyczt",image.getDimensions());result.put("bit_depth",image.getBitDepth());result.put("complete_pixels",(long)image.getWidth()*image.getHeight()*image.getStackSize());result.put("complete_raw_pixel_sha256",HexFormat.of().formatHex(complete.digest()));result.put("planes",planes);var c=image.getCalibration();var cal=new LinkedHashMap<String,Object>();cal.put("pixel_dimensions",List.of(Double.toHexString(c.pixelWidth),Double.toHexString(c.pixelHeight),Double.toHexString(c.pixelDepth)));cal.put("origins_xyz",List.of(Double.toHexString(c.xOrigin),Double.toHexString(c.yOrigin),Double.toHexString(c.zOrigin)));cal.put("frame_interval",Double.toHexString(c.frameInterval));cal.put("fps",Double.toHexString(c.fps));cal.put("units_xyzt",Arrays.asList(c.getXUnit(),c.getYUnit(),c.getZUnit(),c.getTimeUnit()));cal.put("value_unit",c.getValueUnit());cal.put("function",c.getFunction());cal.put("coefficients",c.getCoefficients()==null?null:Arrays.stream(c.getCoefficients()).mapToObj(Double::toHexString).toList());cal.put("loop",c.loop);cal.put("info",c.info);result.put("calibration",cal);result.put("roi",image.getRoi()==null?null:Base64.getEncoder().encodeToString(RoiEncoder.saveAsByteArray(image.getRoi())));var overlay=new ArrayList<String>();if(image.getOverlay()!=null)for(var roi:image.getOverlay().toArray())overlay.add(Base64.getEncoder().encodeToString(RoiEncoder.saveAsByteArray(roi)));result.put("overlay_rois",overlay);result.put("info",image.getInfoProperty());result.put("properties",image.getPropertiesAsArray());return result;
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void put(MessageDigest plane,MessageDigest all,int value,int bytes){for(int i=0;i<bytes;i++){byte v=(byte)(value>>>(8*i));plane.update(v);all.update(v);}}
}
