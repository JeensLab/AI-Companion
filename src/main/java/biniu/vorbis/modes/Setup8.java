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
 * function: 8kHz settings
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Setup8 {

    static final int[] blocksize_8 = {
            512, 512
    };

    static final int[] _floor_mapping_8 = {
            6, 6,
    };

    static final double[] rate_mapping_8 = {
            6000., 9000., 32000.,
    };

    static final double[] rate_mapping_8_uncoupled = {
            8000., 14000., 42000.,
    };

    static final double[] quality_mapping_8 = {
            -.1, .0, 1.
    };

    static final double[] _psy_compand_8_mapping = {0., 1., 1.};

    static final double[] _global_mapping_8 = {1., 2., 3.};

    static final int[][] Xnoise_start_8 = {Psych8._noise_start_8, Psych8._noise_start_8};
    static final int[][] Xnoise_part_8 = {Psych8._noise_part_8, Psych8._noise_part_8};

    static public final VeSetupDataTemplate ve_setup_8_stereo = new VeSetupDataTemplate(
            2,
            rate_mapping_8,
            quality_mapping_8,
            2,
            8000,
            9000,

            blocksize_8,
            blocksize_8,

            Psych8._psy_tone_masteratt_8,
            Psych44._psy_tone_0dB,
            Psych44._psy_tone_suppress,

            Psych8._vp_tonemask_adj_8,
            null,
            Psych8._vp_tonemask_adj_8,

            Psych8._psy_noiseguards_8,
            Psych8._psy_noisebias_8,
            Psych8._psy_noisebias_8,
            null,
            null,
            Psych44._psy_noise_suppress,

            Psych8._psy_compand_8,
            _psy_compand_8_mapping,
            null,

            Xnoise_start_8,
            Xnoise_part_8,
            Psych44._noise_thresh_5only,

            Psych8._psy_ath_floater_8,
            Psych8._psy_ath_abs_8,

            Psych8._psy_lowpass_8,

            Psych44._psy_global_44,
            _global_mapping_8,
            Psych8._psy_stereo_modes_8,

            FloorAll._floor_books,
            FloorAll._floor,
            _floor_mapping_8,
            null,

            Residue8._mapres_template_8_stereo
    );

    static public final VeSetupDataTemplate ve_setup_8_uncoupled = new VeSetupDataTemplate(
            2,
            rate_mapping_8_uncoupled,
            quality_mapping_8,
            -1,
            8000,
            9000,

            blocksize_8,
            blocksize_8,

            Psych8._psy_tone_masteratt_8,
            Psych44._psy_tone_0dB,
            Psych44._psy_tone_suppress,

            Psych8._vp_tonemask_adj_8,
            null,
            Psych8._vp_tonemask_adj_8,

            Psych8._psy_noiseguards_8,
            Psych8._psy_noisebias_8,
            Psych8._psy_noisebias_8,
            null,
            null,
            Psych44._psy_noise_suppress,

            Psych8._psy_compand_8,
            _psy_compand_8_mapping,
            null,

            Xnoise_start_8,
            Xnoise_part_8,
            Psych44._noise_thresh_5only,

            Psych8._psy_ath_floater_8,
            Psych8._psy_ath_abs_8,

            Psych8._psy_lowpass_8,

            Psych44._psy_global_44,
            _global_mapping_8,
            Psych8._psy_stereo_modes_8,

            FloorAll._floor_books,
            FloorAll._floor,
            _floor_mapping_8,
            null,

            Residue8._mapres_template_8_uncoupled
    );

}