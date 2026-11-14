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
public class HighlevelEncodeSetup {

    Object setup = new Object();
    int set_in_stone;

    double base_setting;
    double long_setting;
    double short_setting;
    double impulse_noisetune;

    int managed;  //biniu moze boolean
    int bitrate_min;
    int bitrate_av;
    double bitrate_av_damp;
    int bitrate_max;
    int bitrate_reservoir;
    double bitrate_reservoir_bias;

    int impulse_block_p;
    int noise_normalize_p;  //biniu moze boolean

    double stereo_point_setting;
    double lowpass_kHz;

    double ath_floating_dB;
    double ath_absolute_dB;

    double amplitude_track_dBpersec;
    double trigger_setting;

    // padding, impulse, transition, long
    final HighlevelByBlockType[] block = {
            new HighlevelByBlockType(),
            new HighlevelByBlockType(),
            new HighlevelByBlockType(),
            new HighlevelByBlockType()
    };
}

class HighlevelByBlockType {

    double tone_mask_setting;
    double tone_peaklimit_setting;
    double noise_bias_setting;
    double noise_compand_setting;
}
