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
public class OvectlRatemanageArg {

    public static final int OV_ECTL_RATEMANAGE_GET = 0x10;

    public static final int OV_ECTL_RATEMANAGE_SET = 0x11;
    public static final int OV_ECTL_RATEMANAGE_AVG = 0x12;
    public static final int OV_ECTL_RATEMANAGE_HARD = 0x13;

    public static final int OV_ECTL_RATEMANAGE2_GET = 0x14;
    public static final int OV_ECTL_RATEMANAGE2_SET = 0x15;

    public static final int OV_ECTL_LOWPASS_GET = 0x20;
    public static final int OV_ECTL_LOWPASS_SET = 0x21;

    public static final int OV_ECTL_IBLOCK_GET = 0x30;
    public static final int OV_ECTL_IBLOCK_SET = 0x31;

    int management_active;

    int bitrate_hard_min;
    int bitrate_hard_max;
    double bitrate_hard_window;

    int bitrate_av_lo;
    int bitrate_av_hi;
    double bitrate_av_window;
    double bitrate_av_window_center;
}
