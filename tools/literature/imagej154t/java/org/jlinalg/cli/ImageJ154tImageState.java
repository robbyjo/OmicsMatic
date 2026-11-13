/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import ij.ImagePlus;
import ij.io.Opener;
import ij.io.RoiEncoder;
import ij.io.TiffDecoder;
import ij.measure.Calibration;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static org.jlinalg.cli.ImageJ154tRequest.require;

/** Every decoded raw pixel, stack axis and explicit ImageJ calibration field; original files retained. */
final class ImageJ154tImageState {
    private ImageJ154tImageState() { }
    static Map<String,Object> read(Path path,long maximumPixels)throws IOException {
        var headers=new TiffDecoder(path.getParent().toString()+java.io.File.separator,path.getFileName().toString()).getTiffInfo();require(headers!=null&&headers.length>0,"Original TIFF header is unavailable");long declaredPixels=0;
        for(var header:headers){require(header.width>0&&header.height>0,"Invalid original TIFF dimensions");declaredPixels=Math.addExact(declaredPixels,Math.multiplyExact(Math.multiplyExact((long)header.width,header.height),Math.max(1,header.nImages)));require(declaredPixels<=maximumPixels,"Complete TIFF header pixel capacity exceeded before decoding");}
        ImagePlus image=new Opener().openImage(path.toString());require(image!=null,"Original ImageJ could not decode complete TIFF output: "+path);
        try{
            long pixels=Math.multiplyExact(Math.multiplyExact((long)image.getWidth(),image.getHeight()),image.getStackSize());require(pixels<=maximumPixels,"Complete TIFF decoded-pixel capacity exceeded; no prefix accepted");var rows=new ArrayList<Object>();MessageDigest complete=digest();
            for(int i=1;i<=image.getStackSize();i++){
                var ip=image.getStack().getProcessor(i);Object raw=ip.getPixels();MessageDigest sha=digest();int count=0;
                if(raw instanceof byte[] values){sha.update(values);complete.update(values);count=values.length;}
                else if(raw instanceof short[] values){for(short v:values){put(sha,complete,v&65535,2);}count=values.length;}
                else if(raw instanceof float[] values){for(float v:values){put(sha,complete,Float.floatToRawIntBits(v),4);}count=values.length;}
                else if(raw instanceof int[] values){for(int v:values){put(sha,complete,v,4);}count=values.length;}
                else throw new IOException("Unsupported original decoded pixel representation");
                require(count==(long)image.getWidth()*image.getHeight(),"Original decoded plane is incomplete");var row=new LinkedHashMap<String,Object>();row.put("slice",i);row.put("label",image.getStack().getSliceLabel(i));row.put("pixel_class",raw.getClass().getName());row.put("complete_pixels",count);row.put("raw_pixel_sha256",HexFormat.of().formatHex(sha.digest()));row.put("display_min",Double.toHexString(ip.getMin()));row.put("display_max",Double.toHexString(ip.getMax()));
                if(ip.getColorModel() instanceof IndexColorModel lut){int size=lut.getMapSize();byte[] r=new byte[size],g=new byte[size],b=new byte[size],a=new byte[size];lut.getReds(r);lut.getGreens(g);lut.getBlues(b);lut.getAlphas(a);row.put("lut_rgba",List.of(Base64.getEncoder().encodeToString(r),Base64.getEncoder().encodeToString(g),Base64.getEncoder().encodeToString(b),Base64.getEncoder().encodeToString(a)));}rows.add(row);
            }
            var state=new LinkedHashMap<String,Object>();state.put("dimensions_xyczt",image.getDimensions());state.put("bit_depth",image.getBitDepth());state.put("image_type",image.getType());state.put("stack_size",image.getStackSize());state.put("complete_pixels",pixels);state.put("complete_raw_pixel_sha256",HexFormat.of().formatHex(complete.digest()));state.put("planes",rows);state.put("calibration",calibration(image.getCalibration()));state.put("properties",image.getPropertiesAsArray());state.put("info",image.getInfoProperty());state.put("roi",image.getRoi()==null?null:Base64.getEncoder().encodeToString(RoiEncoder.saveAsByteArray(image.getRoi())));var overlay=new ArrayList<String>();if(image.getOverlay()!=null)for(var roi:image.getOverlay().toArray())overlay.add(Base64.getEncoder().encodeToString(RoiEncoder.saveAsByteArray(roi)));state.put("overlay_rois",overlay);return state;
        }finally{image.changes=false;image.close();}
    }
    private static Map<String,Object> calibration(Calibration c){
        var m=new LinkedHashMap<String,Object>();m.put("pixel_width",Double.toHexString(c.pixelWidth));m.put("pixel_height",Double.toHexString(c.pixelHeight));m.put("pixel_depth",Double.toHexString(c.pixelDepth));m.put("frame_interval",Double.toHexString(c.frameInterval));m.put("fps",Double.toHexString(c.fps));m.put("origins_xyz",List.of(Double.toHexString(c.xOrigin),Double.toHexString(c.yOrigin),Double.toHexString(c.zOrigin)));m.put("units_xyzt",Arrays.asList(c.getXUnit(),c.getYUnit(),c.getZUnit(),c.getTimeUnit()));m.put("value_unit",c.getValueUnit());m.put("function",c.getFunction());m.put("coefficients",c.getCoefficients()==null?null:Arrays.stream(c.getCoefficients()).mapToObj(Double::toHexString).toList());m.put("loop",c.loop);m.put("info",c.info);return m;
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static void put(MessageDigest plane,MessageDigest all,int value,int bytes){for(int i=0;i<bytes;i++){byte v=(byte)(value>>>(8*i));plane.update(v);all.update(v);}}
}
