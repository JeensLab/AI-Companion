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
public class InfoPsyGlobal {

    int eighth_octave_lines;

    // for block long/short tuning; encode only
    float[] preecho_thresh = new float[EnvelopeInt.VE_BANDS];
    float[] postecho_thresh = new float[EnvelopeInt.VE_BANDS];
    float stretch_penalty;
    float preecho_minenergy;

    float ampmax_att_per_sec;

    // channel coupling config
    int[] coupling_pkHz = new int[Const.PACKETBLOBS];
    int[][] coupling_pointlimit = new int[2][Const.PACKETBLOBS];
    int[] coupling_prepointamp = new int[Const.PACKETBLOBS];
    int[] coupling_postpointamp = new int[Const.PACKETBLOBS];
    int[][] sliding_lowpass = new int[2][Const.PACKETBLOBS];

    public InfoPsyGlobal() {
    }

    public InfoPsyGlobal(
            int eighth_octave_lines,
            float[] preecho_thresh,
            float[] postecho_thresh,
            float stretch_penalty,
            float preecho_minenergy,
            float ampmax_att_per_sec,
            int[] coupling_pkHz,
            int[][] coupling_pointlimit,
            int[] coupling_prepointamp,
            int[] coupling_postpointamp,
            int[][] sliding_lowpass
    ) {
        this();
        this.eighth_octave_lines = eighth_octave_lines;
        this.preecho_thresh = preecho_thresh;
        this.postecho_thresh = postecho_thresh;
        this.stretch_penalty = stretch_penalty;
        this.preecho_minenergy = preecho_minenergy;
        this.ampmax_att_per_sec = ampmax_att_per_sec;
        this.coupling_pkHz = coupling_pkHz;
        this.coupling_pointlimit = coupling_pointlimit;
        this.coupling_prepointamp = coupling_prepointamp;
        this.coupling_postpointamp = coupling_postpointamp;
        this.sliding_lowpass = sliding_lowpass;

    }

    public boolean setValue(InfoPsyGlobal info) {

        if (info == null) return false;

        this.eighth_octave_lines = info.eighth_octave_lines;
        System.arraycopy(info.preecho_thresh, 0, this.preecho_thresh, 0, info.preecho_thresh.length);
        System.arraycopy(info.postecho_thresh, 0, this.postecho_thresh, 0, info.postecho_thresh.length);
        this.stretch_penalty = info.stretch_penalty;
        this.preecho_minenergy = info.preecho_minenergy;
        this.ampmax_att_per_sec = info.ampmax_att_per_sec;
        System.arraycopy(info.coupling_pkHz, 0, this.coupling_pkHz, 0, info.coupling_pkHz.length);
        for (int j = 0; j < info.coupling_pointlimit.length; j++) {
            System.arraycopy(info.coupling_pointlimit[j], 0, this.coupling_pointlimit[j], 0,
                    info.coupling_pointlimit[j].length);
        }
        System.arraycopy(info.coupling_prepointamp, 0, this.coupling_prepointamp, 0, info.coupling_prepointamp.length);
        System.arraycopy(info.coupling_postpointamp, 0, this.coupling_postpointamp, 0, info.coupling_postpointamp.length);
        for (int j = 0; j < info.sliding_lowpass.length; j++) {
            System.arraycopy(info.sliding_lowpass[j], 0, this.sliding_lowpass[j], 0,
                    info.sliding_lowpass[j].length);
        }
        return true;
    }
}
