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
 * function: 16kHz settings
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Setup16 {

    static final int[] blocksize_16_short = {
            1024, 512, 512
    };
    static final int[] blocksize_16_long = {
            1024, 1024, 1024
    };

    static final int[] _floor_mapping_16_short = {
            9, 3, 3
    };
    static final int[] _floor_mapping_16 = {
            9, 9, 9
    };

    static final double[] rate_mapping_16 = {
            12000., 20000., 44000., 86000.
    };

    static final double[] rate_mapping_16_uncoupled = {
            16000., 28000., 64000., 100000.
    };

    static final double[] _global_mapping_16 = {1., 2., 3., 4.};

    static final double[] quality_mapping_16 = {-.1, .05, .5, 1.};

    static final double[] _psy_compand_16_mapping = {0., .8, 1., 1.};

    static final int[][] Xnoise_start_16 = {Psych16._noise_start_16, Psych16._noise_start_16};
    static final int[][] Xnoise_part_16 = {Psych16._noise_part_16, Psych16._noise_part_16};

    static public final VeSetupDataTemplate ve_setup_16_stereo = new VeSetupDataTemplate(
            3,
            rate_mapping_16,
            quality_mapping_16,
            2,
            15000,
            19000,

            blocksize_16_short,
            blocksize_16_long,

            Psych16._psy_tone_masteratt_16,
            Psych44._psy_tone_0dB,
            Psych44._psy_tone_suppress,

            Psych16._vp_tonemask_adj_16,
            Psych16._vp_tonemask_adj_16,
            Psych16._vp_tonemask_adj_16,

            Psych8._psy_noiseguards_8,
            Psych16._psy_noisebias_16_impulse,
            Psych16._psy_noisebias_16_short,
            Psych16._psy_noisebias_16_short,
            Psych16._psy_noisebias_16,
            Psych44._psy_noise_suppress,

            Psych8._psy_compand_8,
            _psy_compand_16_mapping,
            _psy_compand_16_mapping,

            Xnoise_start_16,
            Xnoise_part_16,
            Psych16._noise_thresh_16,

            Psych16._psy_ath_floater_16,
            Psych16._psy_ath_abs_16,

            Psych16._psy_lowpass_16,

            Psych44._psy_global_44,
            _global_mapping_16,
            Psych16._psy_stereo_modes_16,

            FloorAll._floor_books,
            FloorAll._floor,
            _floor_mapping_16_short,
            _floor_mapping_16,

            Residue16._mapres_template_16_stereo
    );

    static public final VeSetupDataTemplate ve_setup_16_uncoupled = new VeSetupDataTemplate(
            3,
            rate_mapping_16_uncoupled,
            quality_mapping_16,
            -1,
            15000,
            19000,

            blocksize_16_short,
            blocksize_16_long,

            Psych16._psy_tone_masteratt_16,
            Psych44._psy_tone_0dB,
            Psych44._psy_tone_suppress,

            Psych16._vp_tonemask_adj_16,
            Psych16._vp_tonemask_adj_16,
            Psych16._vp_tonemask_adj_16,

            Psych8._psy_noiseguards_8,
            Psych16._psy_noisebias_16_impulse,
            Psych16._psy_noisebias_16_short,
            Psych16._psy_noisebias_16_short,
            Psych16._psy_noisebias_16,
            Psych44._psy_noise_suppress,

            Psych8._psy_compand_8,
            _psy_compand_16_mapping,
            _psy_compand_16_mapping,

            Xnoise_start_16,
            Xnoise_part_16,
            Psych16._noise_thresh_16,

            Psych16._psy_ath_floater_16,
            Psych16._psy_ath_abs_16,

            Psych16._psy_lowpass_16,

            Psych44._psy_global_44,
            _global_mapping_16,
            Psych16._psy_stereo_modes_16,

            FloorAll._floor_books,
            FloorAll._floor,
            _floor_mapping_16_short,
            _floor_mapping_16,

            Residue16._mapres_template_16_uncoupled
    );

}