/*
 * THIS FILE IS PART OF THE OggVorbis SOFTWARE CODEC SOURCE CODE.
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.
 *
 * THE OggVorbis SOURCE CODE IS (C) COPYRIGHT 1994-2002
 * by the XIPHOPHORUS Company http://www.xiph.org/
 */

package biniu.vorbis.modes;

import biniu.vorbis.Att3;
import biniu.vorbis.Noise3;
import biniu.vorbis.VpAdjBlock;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Psych11 {

    static final double[] _psy_lowpass_11 = {4.5, 5.5, 30.,};

    static final Att3[] _psy_tone_masteratt_11 = {
            new Att3(Util.intTab(30, 25, 12), 0, 0),  /* 0 */
            new Att3(Util.intTab(30, 25, 12), 0, 0),  /* 0 */
            new Att3(Util.intTab(20, 0, -14), 0, 0), /* 0 */
    };

    static final VpAdjBlock[] _vp_tonemask_adj_11 = {
            /* adjust for mode zero */
            /* 63     125     250     500     1     2     4     8    16 */
            new VpAdjBlock(Util.intTab(-20, -20, -20, -20, -20, -16, -10, 0, 0, 0, 0, 10, 2, 0, 99, 99, 99)), /* 0 */
            new VpAdjBlock(Util.intTab(-20, -20, -20, -20, -20, -16, -10, 0, 0, 0, 0, 5, 0, 0, 99, 99, 99)), /* 1 */
            new VpAdjBlock(Util.intTab(-20, -20, -20, -20, -20, -16, -10, 0, 0, 0, 0, 0, 0, 0, 99, 99, 99)), /* 2 */
    };

    static final Noise3[] _psy_noisebias_11 = {
            /*  63     125     250     500      1k       2k      4k      8k     16k*/
            new Noise3(Util.intTab(Util.intTab(-10, -10, -10, -10, -5, -5, -5, 0, 4, 10, 10, 12, 12, 12, 99, 99, 99),
                    Util.intTab(-15, -15, -15, -15, -10, -10, -5, 0, 0, 4, 4, 5, 5, 10, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, 99, 99, 99))),

            new Noise3(Util.intTab(Util.intTab(-10, -10, -10, -10, -5, -5, -5, 0, 4, 10, 10, 12, 12, 12, 99, 99, 99),
                    Util.intTab(-15, -15, -15, -15, -10, -10, -5, -5, -5, 0, 0, 0, 0, 0, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, 99, 99, 99))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, 0, 2, 4, 4, 5, 5, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -26, -22, -20, -14, -12, -12, -10, -10, -10, -10, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, 99, 99, 99))),
    };

    static final double[] _noise_thresh_11 = {.3, .5, .5};

}