/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included with this distribution in  *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.batik.ext.awt.image.rendered;


import org.apache.batik.ext.awt.image.GraphicsUtil;

import java.awt.Point;
import java.awt.Transparency;

import java.awt.color.ColorSpace;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DirectColorModel;

import java.awt.image.SampleModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.SinglePixelPackedSampleModel;


/**
 * This allows you to specify the ColorModel, Alpha premult and/or
 * SampleModel to be used for output.  If the input image lacks
 * Alpha and alpha is included in output then it is filled with
 * alpha=1.  In all other cases bands are simply copied.
 *
 * @author <a href="mailto:Thomas.DeWeeese@Kodak.com">Thomas DeWeese</a>
 * @version $Id: FormatRed.java,v 1.1 2003/04/11 07:56:53 tom Exp $ */
public class FormatRed extends AbstractRed {

    public static CachableRed construct(CachableRed src, ColorModel cm) {
        ColorModel srcCM = src.getColorModel();
        if ((cm.hasAlpha() != srcCM.hasAlpha()) ||
            (cm.isAlphaPremultiplied() != srcCM.isAlphaPremultiplied()))
            return new FormatRed(src, cm);
            
        if (cm.getNumComponents() != srcCM.getNumComponents())
            throw new IllegalArgumentException
                ("Incompatible ColorModel given");
                

        if ((srcCM instanceof ComponentColorModel) &&
            (cm    instanceof ComponentColorModel)) 
            return src;

        if ((srcCM instanceof DirectColorModel) &&
            (cm    instanceof DirectColorModel)) 
            return src;

        return new FormatRed(src, cm);
    }
    
    /**
     * Construct an instance of CachableRed around a BufferedImage.
     */
    public FormatRed(CachableRed cr, SampleModel sm) {
        super(cr, cr.getBounds(),
              makeColorModel(cr, sm), sm, 
              cr.getTileGridXOffset(), 
              cr.getTileGridYOffset(), 
              null);
    }

    public FormatRed(CachableRed cr, ColorModel cm) {
        super(cr, cr.getBounds(),
              cm, makeSampleModel(cr, cm), 
              cr.getTileGridXOffset(), 
              cr.getTileGridYOffset(), 
              null);
    }

    /**
     * fetch the source image for this node.
     */
    public CachableRed getSource() {
        return (CachableRed)getSources().get(0);
    }

    public Object getProperty(String name) {
        return getSource().getProperty(name);
    }

    public String [] getPropertyNames() {
        return getSource().getPropertyNames();
    }

    public WritableRaster copyData(WritableRaster wr) {
        ColorModel   cm   = getColorModel();
        SampleModel  sm   = getSampleModel();

        CachableRed cr    = getSource();
        ColorModel  srcCM = cr.getColorModel();
        SampleModel srcSM = cr.getSampleModel();
        srcSM = srcSM.createCompatibleSampleModel(wr.getWidth(), 
                                                  wr.getHeight());
        WritableRaster srcWR;
        srcWR = Raster.createWritableRaster(srcSM, new Point(wr.getMinX(),
                                                             wr.getMinY()));
        getSource().copyData(srcWR);
        
        BufferedImage srcBI = new BufferedImage
            (srcCM, srcWR.createWritableTranslatedChild(0,0), 
             srcCM.isAlphaPremultiplied(), null);
        BufferedImage dstBI = new BufferedImage
            (cm, wr.createWritableTranslatedChild(0,0), 
             cm.isAlphaPremultiplied(), null);

        GraphicsUtil.copyData(srcBI, dstBI);

        return wr;
    }

    public static SampleModel makeSampleModel(CachableRed cr, ColorModel cm) {
        SampleModel srcSM = cr.getSampleModel();
        return cm.createCompatibleSampleModel(srcSM.getWidth(),
                                              srcSM.getHeight());
    }

    public static ColorModel makeColorModel(CachableRed cr, SampleModel sm) {
        ColorModel srcCM = cr.getColorModel();
        ColorSpace cs    = srcCM.getColorSpace();

        int bands = sm.getNumBands();

        int bits;
        int dt = sm.getDataType();
        switch (dt) {
        case DataBuffer.TYPE_BYTE:   bits=8;  break;
        case DataBuffer.TYPE_SHORT:  bits=16; break;
        case DataBuffer.TYPE_USHORT: bits=16; break;
        case DataBuffer.TYPE_INT:    bits=32; break;
        default:
            throw new IllegalArgumentException
                ("Unsupported DataBuffer type: " + dt);
        }

        boolean hasAlpha = srcCM.hasAlpha();
        if (hasAlpha){
            // if Src has Alpha then our out bands must
            // either be one less than the source (no out alpha)
            // or equal (still has alpha)
            if (bands == srcCM.getNumComponents()-1) 
                hasAlpha = false;
            else if (bands != srcCM.getNumComponents())
                throw new IllegalArgumentException
                    ("Incompatible number of bands in and out");
        } else {
            if (bands == srcCM.getNumComponents()+1)
                hasAlpha = true;
            else if (bands != srcCM.getNumComponents())
                throw new IllegalArgumentException
                    ("Incompatible number of bands in and out");
        }

        boolean preMult  = srcCM.isAlphaPremultiplied();
        if (!hasAlpha)
            preMult = false;

        if (sm instanceof ComponentSampleModel) {
            ComponentSampleModel csm = (ComponentSampleModel)sm;

            int [] bitsPer = new int[bands];
            for (int i=0; i<bands; i++)
                bitsPer[i] = bits;

            return new ComponentColorModel
                (cs, bitsPer, hasAlpha, preMult,
                 hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE,
                 dt);
        } else if (sm instanceof SinglePixelPackedSampleModel) {
            SinglePixelPackedSampleModel sppsm;
            sppsm = (SinglePixelPackedSampleModel)sm;
            int masks [] = sppsm.getBitMasks();
            if (bands == 4)
                return new DirectColorModel
                    (cs, bits, masks[0], masks[1], masks[2], masks[3],
                     preMult, dt);
            else if (bands == 3)
                return new DirectColorModel
                    (cs, bits, masks[0], masks[1], masks[2], 0x0,
                     preMult, dt);
            else
                throw new IllegalArgumentException
                    ("Incompatible number of bands out for ColorModel");
        }
        throw new IllegalArgumentException
            ("Unsupported SampleModel Type");
    }
}
