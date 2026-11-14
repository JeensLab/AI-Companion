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

import biniu.vorbis.AdjStereo;
import biniu.vorbis.Att3;
import biniu.vorbis.CompandBlock;
import biniu.vorbis.Noise3;
import biniu.vorbis.NoiseGuard;
import biniu.vorbis.VpAdjBlock;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 * <p>
 * function: 8kHz psychoacoustic settings
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Psych8 {

    static final Att3[] _psy_tone_masteratt_8 = {
            new Att3(Util.intTab(32, 25, 12), 0, 0),  /* 0 */
            new Att3(Util.intTab(30, 25, 12), 0, 0),  /* 0 */
            new Att3(Util.intTab(20, 0, -14), 0, 0), /* 0 */
    };

    static final VpAdjBlock[] _vp_tonemask_adj_8 = {
            /* adjust for mode zero */
            /* 63     125     250     500     1     2     4     8    16 */
            new VpAdjBlock(Util.intTab(-15, -15, -15, -15, -10, -10, -6, 0, 0, 0, 0, 10, 0, 0, 99, 99, 99)), /* 1 */
            new VpAdjBlock(Util.intTab(-15, -15, -15, -15, -10, -10, -6, 0, 0, 0, 0, 10, 0, 0, 99, 99, 99)), /* 1 */
            new VpAdjBlock(Util.intTab(-15, -15, -15, -15, -10, -10, -6, 0, 0, 0, 0, 0, 0, 0, 99, 99, 99)), /* 1 */
    };

    static final Noise3[] _psy_noisebias_8 = {
            /*  63     125     250     500      1k       2k      4k      8k     16k*/
            new Noise3(Util.intTab(Util.intTab(-10, -10, -10, -10, -5, -5, -5, 0, 4, 8, 8, 8, 10, 10, 99, 99, 99),
                    Util.intTab(-10, -10, -10, -10, -5, -5, -5, 0, 0, 4, 4, 4, 4, 4, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, 99, 99, 99))),

            new Noise3(Util.intTab(Util.intTab(-10, -10, -10, -10, -5, -5, -5, 0, 4, 8, 8, 8, 10, 10, 99, 99, 99),
                    Util.intTab(-10, -10, -10, -10, -10, -10, -5, -5, -5, 0, 0, 0, 0, 0, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, 99, 99, 99))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, 0, 2, 4, 4, 5, 5, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -26, -22, -20, -14, -12, -12, -10, -10, -10, -10, 99, 99, 99),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, 99, 99, 99))),
    };

    /* stereo mode by base quality level */
    static final AdjStereo[] _psy_stereo_modes_8 = {
            /*  0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  */
            new AdjStereo(Util.intTab(4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
                    Util.intTab(6, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                    Util.intfTab(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                    Util.intfTab(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99)),
            new AdjStereo(Util.intTab(4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
                    Util.intTab(6, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                    Util.intfTab(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                    Util.intfTab(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99)),
            new AdjStereo(Util.intTab(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
                    Util.intTab(4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                    Util.intfTab(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                    Util.intfTab(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99)),
    };

    static final NoiseGuard[] _psy_noiseguards_8 = {
            new NoiseGuard(10, 10, -1),
            new NoiseGuard(10, 10, -1),
    };

    static final CompandBlock[] _psy_compand_8 = {
            new CompandBlock(Util.intTab(
                    0, 1, 2, 3, 4, 5, 6, 7,     /* 7dB */
                    8, 8, 9, 9, 10, 10, 11, 11,     /* 15dB */
                    12, 12, 13, 13, 14, 14, 15, 15,     /* 23dB */
                    16, 16, 17, 17, 17, 18, 18, 19,     /* 31dB */
                    19, 19, 20, 21, 22, 23, 24, 25     /* 39dB */
            )),
            new CompandBlock(Util.intTab(
                    0, 1, 2, 3, 4, 5, 6, 6,     /* 7dB */
                    7, 7, 6, 6, 5, 5, 4, 4,     /* 15dB */
                    3, 3, 3, 4, 5, 6, 7, 8,     /* 23dB */
                    9, 10, 11, 12, 13, 14, 15, 16,     /* 31dB */
                    17, 18, 19, 20, 21, 22, 23, 24     /* 39dB */
            )),
    };

    static final double[] _psy_lowpass_8 = {3., 4., 4.};
    static final int[] _noise_start_8 = {
            64, 64,
    };
    static final int[] _noise_part_8 = {
            8, 8,
    };

    static final int[] _psy_ath_floater_8 = {
            -100, -100, -105,
    };

    static final int[] _psy_ath_abs_8 = {
            -130, -130, -140,
    };

}