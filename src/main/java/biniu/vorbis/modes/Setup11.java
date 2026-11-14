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
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Setup11 {

    static final int[] blocksize_11 = {
            512, 512
    };

    static final int[] _floor_mapping_11 = {
            6, 6,
    };

    static final double[] rate_mapping_11 = {
            8000., 13000., 44000.,
    };

    static final double[] rate_mapping_11_uncoupled = {
            12000., 20000., 50000.,
    };

    static final double[] quality_mapping_11 = {
            -.1, .0, 1.
    };

    static public final VeSetupDataTemplate ve_setup_11_stereo = new VeSetupDataTemplate(
            2,
            rate_mapping_11,
            quality_mapping_11,
            2,
            9000,
            15000,
            blocksize_11,
            blocksize_11,
            Psych11._psy_tone_masteratt_11,
            Psych44._psy_tone_0dB,
            Psych44._psy_tone_suppress,
            Psych11._vp_tonemask_adj_11,
            null,
            Psych11._vp_tonemask_adj_11,
            Psych8._psy_noiseguards_8,
            Psych11._psy_noisebias_11,
            Psych11._psy_noisebias_11,
            null,
            null,
            Psych44._psy_noise_suppress,
            Psych8._psy_compand_8,
            Setup8._psy_compand_8_mapping,
            null,
            Setup8.Xnoise_start_8,
            Setup8.Xnoise_part_8,
            Psych11._noise_thresh_11,
            Psych8._psy_ath_floater_8,
            Psych8._psy_ath_abs_8,
            Psych11._psy_lowpass_11,
            Psych44._psy_global_44,
            Setup8._global_mapping_8,
            Psych8._psy_stereo_modes_8,
            FloorAll._floor_books,
            FloorAll._floor,
            _floor_mapping_11,
            null,
            Residue8._mapres_template_8_stereo
    );

    static public final VeSetupDataTemplate ve_setup_11_uncoupled = new VeSetupDataTemplate(
            2,
            rate_mapping_11_uncoupled,
            quality_mapping_11,
            -1,
            9000,
            15000,

            blocksize_11,
            blocksize_11,

            Psych11._psy_tone_masteratt_11,
            Psych44._psy_tone_0dB,
            Psych44._psy_tone_suppress,

            Psych11._vp_tonemask_adj_11,
            null,
            Psych11._vp_tonemask_adj_11,

            Psych8._psy_noiseguards_8,
            Psych11._psy_noisebias_11,
            Psych11._psy_noisebias_11,
            null,
            null,
            Psych44._psy_noise_suppress,

            Psych8._psy_compand_8,
            Setup8._psy_compand_8_mapping,
            null,

            Setup8.Xnoise_start_8,
            Setup8.Xnoise_part_8,
            Psych11._noise_thresh_11,

            Psych8._psy_ath_floater_8,
            Psych8._psy_ath_abs_8,

            Psych11._psy_lowpass_11,

            Psych44._psy_global_44,
            Setup8._global_mapping_8,
            Psych8._psy_stereo_modes_8,

            FloorAll._floor_books,
            FloorAll._floor,
            _floor_mapping_11,
            null,

            Residue8._mapres_template_8_uncoupled
    );

}