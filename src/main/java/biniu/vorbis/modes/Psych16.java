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
import biniu.vorbis.Noise3;
import biniu.vorbis.VpAdjBlock;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 * <p>
 * function: 16kHz settings
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Psych16 {

    /* stereo mode by base quality level */
    /* stereo mode by base quality level */
    static final AdjStereo[] _psy_stereo_modes_16 = {
            /*  0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  */
            new AdjStereo(Util.intTab(4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
                    Util.intTab(6, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                    Util.intfTab(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 4, 4),
                    Util.intfTab(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99)),
            new AdjStereo(Util.intTab(4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
                    Util.intTab(6, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                    Util.intfTab(2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 4, 4, 4, 4),
                    Util.intfTab(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99)),
            new AdjStereo(Util.intTab(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
                    Util.intTab(5, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3),
                    Util.intfTab(4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
                    Util.intfTab(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99)),
            new AdjStereo(Util.intTab(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                    Util.intTab(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                    Util.intfTab(8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8),
                    Util.intfTab(99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99)),
    };

    static final double[] _psy_lowpass_16 = {6.5, 8, 30., 99.};

    static final Att3[] _psy_tone_masteratt_16 = {
            new Att3(Util.intTab(30, 25, 12), 0, 0),  /* 0 */
            new Att3(Util.intTab(25, 22, 12), 0, 0),  /* 0 */
            new Att3(Util.intTab(20, 12, 0), 0, 0),  /* 0 */
            new Att3(Util.intTab(15, 0, -14), 0, 0), /* 0 */
    };

    static final VpAdjBlock[] _vp_tonemask_adj_16 = {
            /* adjust for mode zero */
            /* 63     125     250     500       1     2     4     8    16 */
            new VpAdjBlock(Util.intTab(-20, -20, -20, -20, -20, -16, -10, 0, 0, 0, 0, 10, 0, 0, 0, 0, 0)), /* 0 */
            new VpAdjBlock(Util.intTab(-20, -20, -20, -20, -20, -16, -10, 0, 0, 0, 0, 10, 0, 0, 0, 0, 0)), /* 1 */
            new VpAdjBlock(Util.intTab(-20, -20, -20, -20, -20, -16, -10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)), /* 2 */
            new VpAdjBlock(Util.intTab(-30, -30, -30, -30, -30, -26, -20, -10, -5, 0, 0, 0, 0, 0, 0, 0, 0)), /* 2 */
    };

    static final Noise3[] _psy_noisebias_16_short = {
            /*  63     125     250     500      1k       2k      4k      8k     16k*/
            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -10, -10, -5, 4, 10, 10, 10, 10, 12, 12, 14, 20),
                    Util.intTab(-15, -15, -15, -15, -15, -10, -10, -5, 0, 0, 4, 5, 5, 6, 8, 8, 15),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, -6, -6, -6))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -10, -10, -5, 4, 6, 6, 6, 6, 8, 10, 12, 20),
                    Util.intTab(-15, -15, -15, -15, -15, -15, -15, -10, -5, -5, -5, 4, 5, 6, 8, 8, 15),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -10, -10, -10, -10, -10, -10, -10, -10))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, 0, 2, 4, 4, 5, 5, 5, 8, 12),
                    Util.intTab(-20, -20, -20, -20, -16, -12, -20, -14, -10, -10, -8, 0, 0, 0, 0, 2, 5),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, -20, -20, -20))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, -5, -5, -5, -5, -5, 0, 0, 0, 6),
                    Util.intTab(-30, -30, -30, -30, -26, -22, -20, -14, -12, -12, -10, -10, -10, -10, -10, -10, -6),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, -20, -20, -20))),
    };

    static final Noise3[] _psy_noisebias_16_impulse = {
            /*  63     125     250     500      1k       2k      4k      8k     16k*/
            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -10, -10, -5, 4, 10, 10, 10, 10, 12, 12, 14, 20),
                    Util.intTab(-15, -15, -15, -15, -15, -10, -10, -5, 0, 0, 4, 5, 5, 6, 8, 8, 15),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, -6, -6, -6))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -10, -10, -5, 4, 4, 4, 4, 5, 5, 6, 8, 15),
                    Util.intTab(-15, -15, -15, -15, -15, -15, -15, -10, -5, -5, -5, 0, 0, 0, 0, 4, 10),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -10, -10, -10, -10, -10, -10, -10, -10))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, 0, 0, 0, 0, 0, 0, 0, 4, 10),
                    Util.intTab(-20, -20, -20, -20, -16, -12, -20, -14, -10, -10, -10, -10, -10, -10, -10, -7, -5),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, -20, -20, -20))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, -5, -5, -5, -5, -5, 0, 0, 0, 6),
                    Util.intTab(-30, -30, -30, -30, -26, -22, -20, -18, -18, -18, -20, -20, -20, -20, -20, -20, -16),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, -20, -20, -20))),
    };

    static final Noise3[] _psy_noisebias_16 = {
            /*  63     125     250     500      1k       2k      4k      8k     16k*/
            new Noise3(Util.intTab(Util.intTab(-10, -10, -10, -10, -5, -5, -5, 0, 4, 6, 8, 8, 10, 10, 10, 14, 20),
                    Util.intTab(-10, -10, -10, -10, -10, -5, -2, -2, 0, 0, 0, 4, 5, 6, 8, 8, 15),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, -6, -6, -6))),

            new Noise3(Util.intTab(Util.intTab(-10, -10, -10, -10, -5, -5, -5, 0, 4, 6, 6, 6, 6, 8, 10, 12, 20),
                    Util.intTab(-15, -15, -15, -15, -15, -10, -5, -5, 0, 0, 0, 4, 5, 6, 8, 8, 15),
                    Util.intTab(-30, -30, -30, -30, -30, -24, -20, -14, -10, -6, -8, -8, -6, -6, -6, -6, -6))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, 0, 2, 4, 4, 5, 5, 5, 8, 12),
                    Util.intTab(-20, -20, -20, -20, -16, -12, -20, -10, -5, -5, 0, 0, 0, 0, 0, 2, 5),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, -20, -20, -20))),

            new Noise3(Util.intTab(Util.intTab(-15, -15, -15, -15, -15, -12, -10, -8, -5, -5, -5, -5, -5, 0, 0, 0, 6),
                    Util.intTab(-30, -30, -30, -30, -26, -22, -20, -14, -12, -12, -10, -10, -10, -10, -10, -10, -6),
                    Util.intTab(-30, -30, -30, -30, -26, -26, -26, -26, -26, -26, -26, -26, -26, -24, -20, -20, -20))),
    };

    static final double[] _noise_thresh_16 = {.3, .5, .5, .5};

    static final int[] _noise_start_16 = {256, 256, 9999};
    static final int[] _noise_part_16 = {8, 8, 8, 8};

    static final int[] _psy_ath_floater_16 = {
            -100, -100, -100, -105,
    };

    static final int[] _psy_ath_abs_16 = {
            -130, -130, -130, -140,
    };

}