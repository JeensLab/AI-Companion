/*
 * THIS FILE IS PART OF THE OggVorbis SOFTWARE CODEC SOURCE CODE.
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.
 *
 * THE OggVorbis SOURCE CODE IS (C) COPYRIGHT 1994-2002
 * by the XIPHOPHORUS Company http://www.xiph.org/
 */

package biniu.vorbis;

/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class VeSetupDataTemplate {

    final int mappings;
    final double[] rate_mapping;
    final double[] quality_mapping;
    final int coupling_restriction;
    final int samplerate_min_restriction;
    final int samplerate_max_restriction;

    final int[] blocksize_short;
    final int[] blocksize_long;

    final Att3[] psy_tone_masteratt;
    final int[] psy_tone_0dB;
    final int[] psy_tone_dBsuppress;

    final VpAdjBlock[] psy_tone_adj_impulse;
    final VpAdjBlock[] psy_tone_adj_long;
    final VpAdjBlock[] psy_tone_adj_other;

    final NoiseGuard[] psy_noiseguards;
    final Noise3[] psy_noise_bias_impulse;
    final Noise3[] psy_noise_bias_padding;
    final Noise3[] psy_noise_bias_trans;
    final Noise3[] psy_noise_bias_long;
    final int[] psy_noise_dBsuppress;

    final CompandBlock[] psy_noise_compand;
    final double[] psy_noise_compand_short_mapping;
    final double[] psy_noise_compand_long_mapping;

    final int[][] psy_noise_normal_start;
    final int[][] psy_noise_normal_partition;
    final double[] psy_noise_normal_thresh;

    final int[] psy_ath_float;
    final int[] psy_ath_abs;

    final double[] psy_lowpass;

    final InfoPsyGlobal[] global_params;
    final double[] global_mapping;
    final AdjStereo[] stereo_modes;

    final StaticCodeBook[][] floor_books;
    final InfoFloor1[] floor_params;
    final int[] floor_short_mapping;
    final int[] floor_long_mapping;

    final VorbisMappingTemplate[] maps;

    public VeSetupDataTemplate(
            int mappings,
            double[] rate_mapping,
            double[] quality_mapping,
            int coupling_restriction,
            int samplerate_min_restriction,
            int samplerate_max_restriction,
            int[] blocksize_short,
            int[] blocksize_long,
            Att3[] psy_tone_masteratt,
            int[] psy_tone_0dB,
            int[] psy_tone_dBsuppress,
            VpAdjBlock[] psy_tone_adj_impulse,
            VpAdjBlock[] psy_tone_adj_long,
            VpAdjBlock[] psy_tone_adj_other,
            NoiseGuard[] psy_noiseguards,
            Noise3[] psy_noise_bias_impulse,
            Noise3[] psy_noise_bias_padding,
            Noise3[] psy_noise_bias_trans,
            Noise3[] psy_noise_bias_long,
            int[] psy_noise_dBsuppress,
            CompandBlock[] psy_noise_compand,
            double[] psy_noise_compand_short_mapping,
            double[] psy_noise_compand_long_mapping,
            int[][] psy_noise_normal_start,
            int[][] psy_noise_normal_partition,
            double[] psy_noise_normal_thresh,
            int[] psy_ath_float,
            int[] psy_ath_abs,
            double[] psy_lowpass,
            InfoPsyGlobal[] global_params,
            double[] global_mapping,
            AdjStereo[] stereo_modes,
            StaticCodeBook[][] floor_books,
            InfoFloor1[] floor_params,
            int[] floor_short_mapping,
            int[] floor_long_mapping,
            VorbisMappingTemplate[] maps
    ) {
        this.mappings = mappings;
        this.rate_mapping = rate_mapping;
        this.quality_mapping = quality_mapping;
        this.coupling_restriction = coupling_restriction;
        this.samplerate_min_restriction = samplerate_min_restriction;
        this.samplerate_max_restriction = samplerate_max_restriction;
        this.blocksize_short = blocksize_short;
        this.blocksize_long = blocksize_long;
        this.psy_tone_masteratt = psy_tone_masteratt;
        this.psy_tone_0dB = psy_tone_0dB;
        this.psy_tone_dBsuppress = psy_tone_dBsuppress;
        this.psy_tone_adj_impulse = psy_tone_adj_impulse;
        this.psy_tone_adj_long = psy_tone_adj_long;
        this.psy_tone_adj_other = psy_tone_adj_other;
        this.psy_noiseguards = psy_noiseguards;
        this.psy_noise_bias_impulse = psy_noise_bias_impulse;
        this.psy_noise_bias_padding = psy_noise_bias_padding;
        this.psy_noise_bias_trans = psy_noise_bias_trans;
        this.psy_noise_bias_long = psy_noise_bias_long;
        this.psy_noise_dBsuppress = psy_noise_dBsuppress;
        this.psy_noise_compand = psy_noise_compand;
        this.psy_noise_compand_short_mapping = psy_noise_compand_short_mapping;
        this.psy_noise_compand_long_mapping = psy_noise_compand_long_mapping;
        this.psy_noise_normal_start = psy_noise_normal_start;
        this.psy_noise_normal_partition = psy_noise_normal_partition;
        this.psy_noise_normal_thresh = psy_noise_normal_thresh;
        this.psy_ath_float = psy_ath_float;
        this.psy_ath_abs = psy_ath_abs;
        this.psy_lowpass = psy_lowpass;
        this.global_params = global_params;
        this.global_mapping = global_mapping;
        this.stereo_modes = stereo_modes;
        this.floor_books = floor_books;
        this.floor_params = floor_params;
        this.floor_short_mapping = floor_short_mapping;
        this.floor_long_mapping = floor_long_mapping;
        this.maps = maps;
    }
}
