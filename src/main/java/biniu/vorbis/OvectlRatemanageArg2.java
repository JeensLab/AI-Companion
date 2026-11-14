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
public class OvectlRatemanageArg2 {

    int management_active;

    int bitrate_limit_min_kbps;
    int bitrate_limit_max_kbps;
    int bitrate_limit_reservoir_bits;
    double bitrate_limit_reservoir_bias;

    int bitrate_average_kbps;
    double bitrate_average_damping;

    public OvectlRatemanageArg2() {
    }
}
