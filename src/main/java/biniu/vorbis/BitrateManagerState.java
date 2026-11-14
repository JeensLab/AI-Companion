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
public class BitrateManagerState {

    Object managed;

    int avg_reservoir;
    int minmax_reservoir;
    int avg_bitsper;
    int min_bitsper;
    int max_bitsper;

    int short_per_long;
    double avgfloat;

    Block vb;
    int choice;

    /** compute bitrate tracking setup */
    void vorbis_bitrate_init(Info vi) {
        CodecSetupInfo ci = vi.getCodecSetup();
        BitrateManagerInfo bi = ci.biManInfo;

        if (bi != null && (bi.reservoir_bits > 0)) {
            long ratesamples = vi.rate;
            int halfsamples = ci.blocksizes[0] >> 1;

            this.short_per_long = ci.blocksizes[1] / ci.blocksizes[0];
            this.managed = new Object();

            this.avg_bitsper = (int) Math.rint(1. * bi.avg_rate * halfsamples / ratesamples);
            this.min_bitsper = (int) Math.rint(1. * bi.min_rate * halfsamples / ratesamples);
            this.max_bitsper = (int) Math.rint(1. * bi.max_rate * halfsamples / ratesamples);

            this.avgfloat = Const.PACKETBLOBS / 2d;

            // not a necessary fix, but one that leads to a more balanced
            // typical initialization
            {
                int desired_fill = (int) (bi.reservoir_bits * bi.reservoir_bias);
                this.minmax_reservoir = desired_fill;
                this.avg_reservoir = desired_fill;
            }
        }
    }
}
