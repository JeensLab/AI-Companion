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
 * function: catch-all toplevel settings for q modes only
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class SetupX {

    static final double[] rate_mapping_X = {
            -1., -1., -1., -1., -1., -1.,
            -1., -1., -1., -1., -1., -1.
    };

    static public final VeSetupDataTemplate ve_setup_X_stereo = new VeSetupDataTemplate(
            11,
            rate_mapping_X,
            Setup44.quality_mapping_44,
            2,
            50000,
            200000,
            Setup44.blocksize_short_44,
            Setup44.blocksize_long_44,
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
            Setup44._psy_compand_short_mapping,
            Setup44._psy_compand_long_mapping,

            Setup44.Xnoise_start_44,
            Setup44.Xnoise_part_44,
            Psych44._noise_thresh_44,

            Psych44._psy_ath_floater,
            Psych44._psy_ath_abs,

            Psych44._psy_lowpass_44,

            Psych44._psy_global_44,
            Setup44._global_mapping_44,
            Psych44._psy_stereo_modes_44,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup44._floor_short_mapping_44,
            Setup44._floor_long_mapping_44,

            Residue44._mapres_template_44_stereo
    );

    static public final VeSetupDataTemplate ve_setup_X_uncoupled = new VeSetupDataTemplate(
            11,
            rate_mapping_X,
            Setup44.quality_mapping_44,
            -1,
            50000,
            200000,

            Setup44.blocksize_short_44,
            Setup44.blocksize_long_44,

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
            Setup44._psy_compand_short_mapping,
            Setup44._psy_compand_long_mapping,

            Setup44.Xnoise_start_44,
            Setup44.Xnoise_part_44,
            Psych44._noise_thresh_44,

            Psych44._psy_ath_floater,
            Psych44._psy_ath_abs,

            Psych44._psy_lowpass_44,

            Psych44._psy_global_44,
            Setup44._global_mapping_44,
            null,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup44._floor_short_mapping_44,
            Setup44._floor_long_mapping_44,

            Residu44u._mapres_template_44_uncoupled
    );

    static public final VeSetupDataTemplate ve_setup_XX_stereo = new VeSetupDataTemplate(
            2,
            rate_mapping_X,
            Setup8.quality_mapping_8,
            2,
            0,
            8000,

            Setup8.blocksize_8,
            Setup8.blocksize_8,

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
            Setup8._psy_compand_8_mapping,
            null,

            Setup8.Xnoise_start_8,
            Setup8.Xnoise_part_8,
            Psych44._noise_thresh_5only,

            Psych8._psy_ath_floater_8,
            Psych8._psy_ath_abs_8,

            Psych8._psy_lowpass_8,

            Psych44._psy_global_44,
            Setup8._global_mapping_8,
            Psych8._psy_stereo_modes_8,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup8._floor_mapping_8,
            null,

            Residue8._mapres_template_8_stereo
    );

    static public final VeSetupDataTemplate ve_setup_XX_uncoupled = new VeSetupDataTemplate(
            2,
            rate_mapping_X,
            Setup8.quality_mapping_8,
            -1,
            0,
            8000,

            Setup8.blocksize_8,
            Setup8.blocksize_8,

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
            Setup8._psy_compand_8_mapping,
            null,

            Setup8.Xnoise_start_8,
            Setup8.Xnoise_part_8,
            Psych44._noise_thresh_5only,

            Psych8._psy_ath_floater_8,
            Psych8._psy_ath_abs_8,

            Psych8._psy_lowpass_8,

            Psych44._psy_global_44,
            Setup8._global_mapping_8,
            Psych8._psy_stereo_modes_8,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup8._floor_mapping_8,
            null,

            Residue8._mapres_template_8_uncoupled
    );

}