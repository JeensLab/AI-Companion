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
 * function: toplevel settings for 44.1/48kHz uncoupled modes
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Setup44u {

    static final double[] rate_mapping_44_un = {
            32000., 48000., 60000., 70000., 80000., 86000.,
            96000., 110000., 120000., 140000., 160000., 240001.
    };

    static public final VeSetupDataTemplate ve_setup_44_uncoupled = new VeSetupDataTemplate(
            11,
            rate_mapping_44_un,
            Setup44.quality_mapping_44,
            -1,
            40000,
            50000,

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

}