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
public class PsyInfo {

    int athp;
    int decayp;
    int smoothp;
    int noisefitp;
    int noisefit_subblock;
    float noisefit_threshdB;

    float ath_att;

    int tonemaskp;
    float[] toneatt_125Hz = new float[5];
    float[] toneatt_250Hz = new float[5];
    float[] toneatt_500Hz = new float[5];
    float[] toneatt_1000Hz = new float[5];
    float[] toneatt_2000Hz = new float[5];
    float[] toneatt_4000Hz = new float[5];
    float[] toneatt_8000Hz = new float[5];

    int peakattp;
    float[] peakatt_125Hz = new float[5];
    float[] peakatt_250Hz = new float[5];
    float[] peakatt_500Hz = new float[5];
    float[] peakatt_1000Hz = new float[5];
    float[] peakatt_2000Hz = new float[5];
    float[] peakatt_4000Hz = new float[5];
    float[] peakatt_8000Hz = new float[5];

    int noisemaskp;
    float[] noiseatt_125Hz = new float[5];
    float[] noiseatt_250Hz = new float[5];
    float[] noiseatt_500Hz = new float[5];
    float[] noiseatt_1000Hz = new float[5];
    float[] noiseatt_2000Hz = new float[5];
    float[] noiseatt_4000Hz = new float[5];
    float[] noiseatt_8000Hz = new float[5];

    float max_curve_dB;

    float attack_coeff;
    float decay_coeff;

    //biniu
    int blockflag;

    float ath_adjatt;
    float ath_maxatt;

    float[] tone_masteratt = new float[Psy.P_NOISECURVES];
    float tone_centerboost;
    float tone_decay;
    float tone_abs_limit;
    float[] toneatt = new float[Psy.P_BANDS];

    //int noisemaskp;
    float noisemaxsupp;
    float noisewindowlo;
    float noisewindowhi;
    int noisewindowlomin;
    int noisewindowhimin;
    int noisewindowfixed;
    float[][] noiseoff = new float[Psy.P_NOISECURVES][Psy.P_BANDS];
    float[] noisecompand = new float[Psy.NOISE_COMPAND_LEVELS];

    //float max_curve_dB;
    int normal_channel_p;
    int normal_point_p;
    int normal_start;
    int normal_partition;
    double normal_thresh;

    public PsyInfo() {
    }

    public PsyInfo(
            int blockflag,
            float ath_adjatt,
            float ath_maxatt,
            float[] tone_masteratt,
            float tone_centerboost,
            float tone_decay,
            float tone_abs_limit,
            float[] toneatt,
            int noisemaskp,
            float noisemaxsupp,
            float noisewindowlo,
            float noisewindowhi,
            int noisewindowlomin,
            int noisewindowhimin,
            int noisewindowfixed,
            float[][] noiseoff,
            float[] noisecompand,
            float max_curve_dB,
            int normal_channel_p,
            int normal_point_p,
            int normal_start,
            int normal_partition,
            double normal_thresh
    ) {
        this();
        this.blockflag = blockflag;
        this.ath_adjatt = ath_adjatt;
        this.ath_maxatt = ath_maxatt;
        this.tone_masteratt = tone_masteratt;
        this.tone_centerboost = tone_centerboost;
        this.tone_decay = tone_decay;
        this.tone_abs_limit = tone_abs_limit;
        this.toneatt = toneatt;
        this.noisemaskp = noisemaskp;
        this.noisemaxsupp = noisemaxsupp;
        this.noisewindowlo = noisewindowlo;
        this.noisewindowhi = noisewindowhi;
        this.noisewindowlomin = noisewindowlomin;
        this.noisewindowhimin = noisewindowhimin;
        this.noisewindowfixed = noisewindowfixed;
        this.noiseoff = noiseoff;
        this.noisecompand = noisecompand;
        this.max_curve_dB = max_curve_dB;
        this.normal_channel_p = normal_channel_p;
        this.normal_channel_p = normal_channel_p;
        this.normal_start = normal_start;
        this.normal_partition = normal_partition;
        this.normal_thresh = normal_thresh;
    }

    void free() {
    }

    public boolean setValues(PsyInfo info) {

        if (info == null) return false;
        this.blockflag = info.blockflag;
        this.ath_adjatt = info.ath_adjatt;
        this.ath_maxatt = info.ath_maxatt;
        System.arraycopy(info.tone_masteratt, 0, this.tone_masteratt, 0, info.tone_masteratt.length);
        this.tone_centerboost = info.tone_centerboost;
        this.tone_decay = info.tone_decay;
        this.tone_abs_limit = info.tone_abs_limit;
        System.arraycopy(info.toneatt, 0, this.toneatt, 0, info.toneatt.length);
        this.noisemaskp = info.noisemaskp;
        this.noisemaxsupp = info.noisemaxsupp;
        this.noisewindowlo = info.noisewindowlo;
        this.noisewindowhi = info.noisewindowhi;
        this.noisewindowlomin = info.noisewindowlomin;
        this.noisewindowhimin = info.noisewindowhimin;
        this.noisewindowfixed = info.noisewindowfixed;
        for (int j = 0; j < info.noiseoff.length; j++) {
            System.arraycopy(info.noiseoff[j], 0, this.noiseoff[j], 0, info.noiseoff[j].length);
        }
        System.arraycopy(info.noisecompand, 0, this.noisecompand, 0, info.noisecompand.length);
        this.max_curve_dB = info.max_curve_dB;
        this.normal_channel_p = info.normal_channel_p;
        this.normal_channel_p = info.normal_channel_p;
        this.normal_start = info.normal_start;
        this.normal_partition = info.normal_partition;
        this.normal_thresh = info.normal_thresh;
        return true;
    }
}
