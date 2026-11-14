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
 * function: toplevel settings for 32kHz
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Setup32 {

    static final double[] rate_mapping_32 = {
            18000., 28000., 35000., 45000., 56000., 60000.,
            75000., 90000., 100000., 115000., 150000., 190000.,
    };

    static final double[] rate_mapping_32_un = {
            30000., 42000., 52000., 64000., 72000., 78000.,
            86000., 92000., 110000., 120000., 140000., 190000.,
    };

//    static double rate_mapping_32_low[] = {
//            20000., 28000.
//    };
//
//    static double rate_mapping_32_un_low[] = {
//            24000., 42000.,
//    };
//
//    static double _psy_lowpass_32_low[] = {
//            13., 13.,
//    };

    static final double[] _psy_lowpass_32 = {
            12.3, 13., 13., 14., 15., 99., 99., 99., 99., 99., 99., 99.
    };

    static public final VeSetupDataTemplate ve_setup_32_stereo = new VeSetupDataTemplate(
            11,
            rate_mapping_32,
            Setup44.quality_mapping_44,
            2,
            26000,
            40000,

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

            _psy_lowpass_32,

            Psych44._psy_global_44,
            Setup44._global_mapping_44,
            Psych44._psy_stereo_modes_44,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup44._floor_short_mapping_44,
            Setup44._floor_long_mapping_44,

            Residue44._mapres_template_44_stereo
    );

    static public final VeSetupDataTemplate ve_setup_32_uncoupled = new VeSetupDataTemplate(
            11,
            rate_mapping_32_un,
            Setup44.quality_mapping_44,
            -1,
            26000,
            40000,

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

            _psy_lowpass_32,

            Psych44._psy_global_44,
            Setup44._global_mapping_44,
            null,

            FloorAll._floor_books,
            FloorAll._floor,
            Setup44._floor_short_mapping_44,
            Setup44._floor_long_mapping_44,

            Residu44u._mapres_template_44_uncoupled
    );

}