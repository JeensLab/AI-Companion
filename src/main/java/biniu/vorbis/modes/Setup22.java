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
 * function: 22kHz settings
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Setup22 {

    static final double[] rate_mapping_22 = {
            15000., 20000., 44000., 86000.
    };

    static final double[] rate_mapping_22_uncoupled = {
            16000., 28000., 50000., 90000.
    };

    static final double[] _psy_lowpass_22 = {9.5, 11., 30., 99.};

    static final int[][] Xnoise_start_16 = {Psych16._noise_start_16, Psych16._noise_start_16};
    static final int[][] Xnoise_part_16 = {Psych16._noise_part_16, Psych16._noise_part_16};

    static public final VeSetupDataTemplate ve_setup_22_stereo = new VeSetupDataTemplate(
            3,
            rate_mapping_22,
            Setup16.quality_mapping_16,
            2,
            19000,
            26000,

            Setup16.blocksize_16_short,
            Setup16.blocksize_16_long,

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
            Setup8._psy_compand_8_mapping,
            Setup8._psy_compand_8_mapping,

            Xnoise_start_16,
            Xnoise_part_16,
            Psych16._noise_thresh_16,

            Psych16._psy_ath_floater_16,
            Psych16._psy_ath_abs_16,

            Setup22._psy_lowpass_22,

            Psych44._psy_global_44,
            Setup16._global_mapping_16,
            Psych16._psy_stereo_modes_16,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup16._floor_mapping_16_short,
            Setup16._floor_mapping_16,

            Residue16._mapres_template_16_stereo
    );

    static public final VeSetupDataTemplate ve_setup_22_uncoupled = new VeSetupDataTemplate(
            3,
            rate_mapping_22_uncoupled,
            Setup16.quality_mapping_16,
            -1,
            19000,
            26000,

            Setup16.blocksize_16_short,
            Setup16.blocksize_16_long,

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
            Setup8._psy_compand_8_mapping,
            Setup8._psy_compand_8_mapping,

            Xnoise_start_16,
            Xnoise_part_16,
            Psych16._noise_thresh_16,

            Psych16._psy_ath_floater_16,
            Psych16._psy_ath_abs_16,

            Setup22._psy_lowpass_22,

            Psych44._psy_global_44,
            Setup16._global_mapping_16,
            Psych16._psy_stereo_modes_16,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup16._floor_mapping_16_short,
            Setup16._floor_mapping_16,

            Residue16._mapres_template_16_uncoupled
    );

}