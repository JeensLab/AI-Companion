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

import biniu.vorbis.VeSetupDataTemplate;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 * <p>
 * function: toplevel settings for 44.1/48kHz
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Setup44 {

//    double rate_mapping_44_stereo_low[] = {
//            22500., 32000.
//    };

    static final double[] rate_mapping_44_stereo = {
            22500., 32000., 40000., 48000., 56000., 64000.,
            80000., 96000., 112000., 128000., 160000., 250001.
    };

//  double quality_mapping_44_stereo_low[] = {
//    -.1,.0,
//  };

    static final double[] quality_mapping_44 = {
            -.1, .0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1.0
    };

//    int blocksize_short_44_low[] = {
//            512
//    };
//
//    int blocksize_long_44_low[] = {
//            4096
//    };

    static final int[] blocksize_short_44 = {
            512, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256
    };
    static final int[] blocksize_long_44 = {
            4096, 2048, 2048, 2048, 2048, 2048, 2048, 2048, 2048, 2048, 2048
    };
    static final double[] _psy_compand_short_mapping = {
            0.5, 1., 1., 1.3, 1.6, 2., 2., 2., 2., 2., 2., 2.
    };
    static final double[] _psy_compand_long_mapping = {
            3.5, 4., 4., 4.3, 4.6, 5., 5., 5., 5., 5., 5., 5.
    };
    static final double[] _global_mapping_44 = {
            // 1., 1., 1.5, 2., 2., 2.5, 2.7, 3.0, 3.5, 4., 4.
            0., 1., 1., 1.5, 2., 2., 2.5, 2.7, 3.0, 3.7, 4., 4.
    };

    static final int[] _floor_short_mapping_44 = {
            1, 0, 0, 2, 2, 4, 5, 5, 5, 5, 5
    };
    static final int[] _floor_long_mapping_44 = {
            8, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    };

    static final int[][] Xnoise_start_44 = {Psych44._noise_start_short_44, Psych44._noise_start_long_44};
    static final int[][] Xnoise_part_44 = {Psych44._noise_part_short_44, Psych44._noise_part_long_44};

    static public final VeSetupDataTemplate ve_setup_44_stereo = new VeSetupDataTemplate(
            11,
            rate_mapping_44_stereo,
            quality_mapping_44,
            2,
            40000,
            50000,

            blocksize_short_44,
            blocksize_long_44,

            Psych44._psy_tone_masteratt_44,
            Psych44._psy_tone_0dB,
            Psych44._psy_tone_suppress,

            Psych44._vp_tonemask_adj_otherblock,
            Psych44._vp_tonemask_adj_longblock,
            Psych44._vp_tonemask_adj_otherblock,

            Psych44._psy_noiseguards_44,
            Psych44._psy_noisebias_impulse,
            Psych44._psy_noisebias_padding,
            Psych44._psy_noisebias_trans,
            Psych44._psy_noisebias_long,
            Psych44._psy_noise_suppress,

            Psych44._psy_compand_44,
            _psy_compand_short_mapping,
            _psy_compand_long_mapping,

            Xnoise_start_44,
            Xnoise_part_44,
            Psych44._noise_thresh_44,

            Psych44._psy_ath_floater,
            Psych44._psy_ath_abs,

            Psych44._psy_lowpass_44,

            Psych44._psy_global_44,
            _global_mapping_44,
            Psych44._psy_stereo_modes_44,

            FloorAll._floor_books,
            FloorAll._floor,
            _floor_short_mapping_44,
            _floor_long_mapping_44,

            Residue44._mapres_template_44_stereo
    );

}