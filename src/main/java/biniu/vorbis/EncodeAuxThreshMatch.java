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
public class EncodeAuxThreshMatch {

    final float[] quantthresh;
    final int[] quantmap;
    final int quantvals;
    final int threshvals;

    public EncodeAuxThreshMatch(
            float[] quantthresh,
            int[] quantmap,
            int quantvals,
            int threshvals
    ) {
        this.quantthresh = quantthresh;
        this.quantmap = quantmap;
        this.quantvals = quantvals;
        this.threshvals = threshvals;
    }
}
