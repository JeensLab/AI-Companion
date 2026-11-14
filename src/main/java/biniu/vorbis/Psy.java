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
public class Psy {

    public static class LookPsyGlobal {

    }

    // psychoacoustic setup

    /** 62Hz to 16kHz */
    public static final int P_BANDS = 17;
    /** 30dB to 100dB */
    public static final int P_LEVELS = 8;
    /** 30 dB */
    public static final float P_LEVEL_0 = 30;
    public static final int P_NOISECURVES = 3;
    public static final int NOISE_COMPAND_LEVELS = 40;
}

class InfoPsy {

    int blockflag;

    float ath_adjatt;
    float ath_maxatt;

    float[] tone_masteratt = new float[Psy.P_NOISECURVES];
    float tone_centerboost;
    float tone_decay;
    float tone_abs_limit;
    float[] toneatt = new float[Psy.P_BANDS];

    int noisemaskp;
    float noisemaxsupp;
    float noisewindowlo;
    float noisewindowhi;
    int noisewindowlomin;
    int noisewindowhimin;
    int noisewindowfixed;
    float[][] noiseoff = new float[Psy.P_NOISECURVES][Psy.P_BANDS];
    float[] noisecompand = new float[Psy.NOISE_COMPAND_LEVELS];

    float max_curve_dB;

    int normal_channel_p;
    int normal_point_p;
    int normal_start;
    int normal_partition;
    double normal_thresh;
}

class LookPsy {

    static final float[] FLOOR1_fromdB_INV_LOOKUP = {
            0.F, 8.81683e+06F, 8.27882e+06F, 7.77365e+06F,
            7.29930e+06F, 6.85389e+06F, 6.43567e+06F, 6.04296e+06F,
            5.67422e+06F, 5.32798e+06F, 5.00286e+06F, 4.69759e+06F,
            4.41094e+06F, 4.14178e+06F, 3.88905e+06F, 3.65174e+06F,
            3.42891e+06F, 3.21968e+06F, 3.02321e+06F, 2.83873e+06F,
            2.66551e+06F, 2.50286e+06F, 2.35014e+06F, 2.20673e+06F,
            2.07208e+06F, 1.94564e+06F, 1.82692e+06F, 1.71544e+06F,
            1.61076e+06F, 1.51247e+06F, 1.42018e+06F, 1.33352e+06F,
            1.25215e+06F, 1.17574e+06F, 1.10400e+06F, 1.03663e+06F,
            973377.F, 913981.F, 858210.F, 805842.F,
            756669.F, 710497.F, 667142.F, 626433.F,
            588208.F, 552316.F, 518613.F, 486967.F,
            457252.F, 429351.F, 403152.F, 378551.F,
            355452.F, 333762.F, 313396.F, 294273.F,
            276316.F, 259455.F, 243623.F, 228757.F,
            214798.F, 201691.F, 189384.F, 177828.F,
            166977.F, 156788.F, 147221.F, 138237.F,
            129802.F, 121881.F, 114444.F, 107461.F,
            100903.F, 94746.3F, 88964.9F, 83536.2F,
            78438.8F, 73652.5F, 69158.2F, 64938.1F,
            60975.6F, 57254.9F, 53761.2F, 50480.6F,
            47400.3F, 44507.9F, 41792.0F, 39241.9F,
            36847.3F, 34598.9F, 32487.7F, 30505.3F,
            28643.8F, 26896.0F, 25254.8F, 23713.7F,
            22266.7F, 20908.0F, 19632.2F, 18434.2F,
            17309.4F, 16253.1F, 15261.4F, 14330.1F,
            13455.7F, 12634.6F, 11863.7F, 11139.7F,
            10460.0F, 9821.72F, 9222.39F, 8659.64F,
            8131.23F, 7635.06F, 7169.17F, 6731.70F,
            6320.93F, 5935.23F, 5573.06F, 5232.99F,
            4913.67F, 4613.84F, 4332.30F, 4067.94F,
            3819.72F, 3586.64F, 3367.78F, 3162.28F,
            2969.31F, 2788.13F, 2617.99F, 2458.24F,
            2308.24F, 2167.39F, 2035.14F, 1910.95F,
            1794.35F, 1684.85F, 1582.04F, 1485.51F,
            1394.86F, 1309.75F, 1229.83F, 1154.78F,
            1084.32F, 1018.15F, 956.024F, 897.687F,
            842.910F, 791.475F, 743.179F, 697.830F,
            655.249F, 615.265F, 577.722F, 542.469F,
            509.367F, 478.286F, 449.101F, 421.696F,
            395.964F, 371.803F, 349.115F, 327.812F,
            307.809F, 289.026F, 271.390F, 254.830F,
            239.280F, 224.679F, 210.969F, 198.096F,
            186.008F, 174.658F, 164.000F, 153.993F,
            144.596F, 135.773F, 127.488F, 119.708F,
            112.404F, 105.545F, 99.1046F, 93.0572F,
            87.3788F, 82.0469F, 77.0404F, 72.3394F,
            67.9252F, 63.7804F, 59.8885F, 56.2341F,
            52.8027F, 49.5807F, 46.5553F, 43.7144F,
            41.0470F, 38.5423F, 36.1904F, 33.9821F,
            31.9085F, 29.9614F, 28.1332F, 26.4165F,
            24.8045F, 23.2910F, 21.8697F, 20.5352F,
            19.2822F, 18.1056F, 17.0008F, 15.9634F,
            14.9893F, 14.0746F, 13.2158F, 12.4094F,
            11.6522F, 10.9411F, 10.2735F, 9.64662F,
            9.05798F, 8.50526F, 7.98626F, 7.49894F,
            7.04135F, 6.61169F, 6.20824F, 5.82941F,
            5.47370F, 5.13970F, 4.82607F, 4.53158F,
            4.25507F, 3.99542F, 3.75162F, 3.52269F,
            3.30774F, 3.10590F, 2.91638F, 2.73842F,
            2.57132F, 2.41442F, 2.26709F, 2.12875F,
            1.99885F, 1.87688F, 1.76236F, 1.65482F,
            1.55384F, 1.45902F, 1.36999F, 1.28640F,
            1.20790F, 1.13419F, 1.06499F, 1.F
    };

    private static final int EHMER_MAX = 56;
    private static final int EHMER_OFFSET = 16;

    // psychoacoustic setup

    /** 62Hz to 16kHz */
    private static final int P_BANDS = 17;
    /** 30dB to 100dB */
    private static final int P_LEVELS = 8;
    /** 30 dB */
    private static final float P_LEVEL_0 = 30.f;
    private static final int P_NOISECURVES = 3;

    private static final int NOISE_COMPAND_LEVELS = 40;
    private static final float NEGINF = -9999.f;

    int n;
    PsyInfo vi;

    float[][][] tonecurves;
    float[][] noiseoffset;

    float[] ath;
    /** in n.ocshift format */
    int[] octave;
    int[] bark;

    int firstoc;
    int shiftoc;
    /** power of two, please */
    int eighth_octave_lines;
    int total_octave_lines;
    /** cache it */
    int rate;

    /** Masking compensation value */
    float m_val;

    public void noiseNormalizeSort(float[] magnitudes, int[] sortedindex) {
        int i, j, n = this.n;
        PsyInfo vi = this.vi;
        int partition = vi.normal_partition;
        float[][] work = new float[partition][];
        int start = vi.normal_start;

        for (j = start; j < n; j += partition) {
            if (j + partition > n) partition = n - j;
            for (i = 0; i < partition; i++) {
                work[i] = new float[magnitudes.length - i - j];
                System.arraycopy(magnitudes, i + j, work[i], 0, work[i].length);
            }
            for (i = 0; i < partition; i++) {
                sortedindex[i + j - start] = (int) (work[i][0] - magnitudes[0]);
            }
        }
    }

    public int[][] quantizeCoupleSort(Block vb, InfoMapping0 vi, float[][] mags) {

        if (this.vi.normal_point_p != 0) {
            int i, j, k, n = this.n;
            int[][] ret = new int[vi.coupling_steps][];
            int partition = this.vi.normal_partition;
            float[][] work = new float[partition][];

            for (i = 0; i < vi.coupling_steps; i++) {
                ret[i] = new int[n];

                for (j = 0; j < n; j += partition) {
                    for (k = 0; k < partition; k++) {
                        work[k] = new float[mags[i].length - k - j];
                        System.arraycopy(mags[i], k + j, work[k], 0, work[k].length);
                    }
                    for (k = 0; k < partition; k++) {
                        ret[i][k + j] = (int) (work[k][0] - mags[i][0]);
                    }
                }
            }
            return ret;
        }
        return null;
    }

    /**
     * doing the real circular magnitude calculation is audibly superior
     * to (A+B)/sqrt(2)
     */
    static float dipole_hypot(float a, float b) {
        if (a > 0.) {
            if (b > 0.) return (float) Math.sqrt(a * a + b * b);
            if (a > -b) return (float) Math.sqrt(a * a - b * b);
            return -(float) Math.sqrt(b * b - a * a);
        }
        if (b < 0.) return -(float) Math.sqrt(a * a + b * b);
        if (-a > b) return -(float) Math.sqrt(a * a - b * b);
        return (float) Math.sqrt(b * b - a * a);
    }

    static float round_hypot(float a, float b) {
        if (a > 0.) {
            if (b > 0.) return (float) Math.sqrt(a * a + b * b);
            if (a > -b) return (float) Math.sqrt(a * a + b * b);
            return -(float) Math.sqrt(b * b + a * a);
        }
        if (b < 0.) return -(float) Math.sqrt(a * a + b * b);
        if (-a > b) return -(float) Math.sqrt(a * a + b * b);
        return (float) Math.sqrt(b * b + a * a);
    }

    /** revert to round hypot for now */
    public float[][] quantizeCoupleMemo(Block vb,
                                        InfoPsyGlobal g,
                                        InfoMapping0 vi,
                                        float[][] mdct) {

        int i, j, n = this.n;
        float[][] ret = new float[vi.coupling_steps][];
        int limit = g.coupling_pointlimit[this.vi.blockflag][Const.PACKETBLOBS / 2];

        for (i = 0; i < vi.coupling_steps; i++) {
            float[] mdctM = mdct[vi.coupling_mag[i]];
            float[] mdctA = mdct[vi.coupling_ang[i]];
            ret[i] = new float[n];
            for (j = 0; j < limit; j++)
                ret[i][j] = dipole_hypot(mdctM[j], mdctA[j]);
            for (; j < n; j++)
                ret[i][j] = round_hypot(mdctM[j], mdctA[j]);
        }

        return ret;
    }

    // AoTuV

    /**
     * @ M2 **
     * The boost problem by the combination of noise normalization and point stereo is eased.
     * However, this is a temporary patch.
     * by Aoyumi @ 2004/04/18
     */
    void hf_reduction(InfoPsyGlobal g,
                      InfoMapping0 vi,
                      float[][] mdct) {

        int i, j, n = this.n, de = (int) (0.3 * this.m_val);
        int limit = g.coupling_pointlimit[this.vi.blockflag][Const.PACKETBLOBS / 2];
        int start = this.vi.normal_start;

        for (i = 0; i < vi.coupling_steps; i++) {
            // for(j=start; j<limit; j++){} // ???
            for (j = limit; j < n; j++)
                mdct[i][j] = (float) (mdct[i][j] * (1.0 - de * ((float) (j - limit) / (float) (n - limit))));
        }
    }

    static float unitnorm(float x) {
        long ix = (long) x;
        ix = (ix & 0x80000000L) | (0x3f800000);
        return x;
    }

    public void noiseNormalize(float[] in,
                               float[] out,
                               int pout,
                               int[] sortedindex) {
        int flag = 0, i, j = 0, n = this.n;
        PsyInfo vi = this.vi;
        int partition = vi.normal_partition;
        int start = vi.normal_start;

        if (start > n) start = n;

        if (vi.normal_channel_p != 0) {
            for (; j < start; j++)
                out[j + pout] = (float) Math.rint(in[j]);

            for (; j + partition <= n; j += partition) {
                float acc = 0.f;
                int k;

                for (i = j; i < j + partition; i++)
                    acc += in[i] * in[i];

                for (i = 0; i < partition; i++) {
                    k = sortedindex[i + j - start];

                    if (in[k] * in[k] >= .25f) {
                        out[k + pout] = (float) Math.rint(in[k]);
                        acc -= in[k] * in[k];
                        flag = 1;
                    } else {
                        if (acc < vi.normal_thresh) break;
                        out[k + pout] = unitnorm(in[k]);
                        acc = (float) (acc - 1.);
                    }
                }

                for (; i < partition; i++) {
                    k = sortedindex[i + j - start];
                    out[k + pout] = 0.f;
                }
            }
        }

        for (; j < n; j++)
            out[j + pout] = (float) Math.rint(in[j]);
    }

    void removeFloor(float[] mdct,
                     int[] codedflr,
                     float[] residue,
                     int sliding_lowpass) {

        int i, n = this.n;

        if (sliding_lowpass > n) sliding_lowpass = n;

        for (i = 0; i < sliding_lowpass; i++) {
            residue[i] =
                    mdct[i] * FLOOR1_fromdB_INV_LOOKUP[codedflr[i]];
        }

        for (; i < n; i++)
            residue[i] = 0.f;
    }

    public void offsetAndMix(float[] noise,
                             float[] tone,
                             int offset_select,
                             float[] logmask,
                             float[] mdct,
                             float[] logmdct) {
        int i, n = this.n;
        float de, coeffi, cx; // AoTuV
        float toneatt = this.vi.tone_masteratt[offset_select];

        cx = this.m_val;

        for (i = 0; i < n; i++) {
            float val = noise[i] + this.noiseoffset[offset_select][i];
            if (val > this.vi.noisemaxsupp) val = this.vi.noisemaxsupp;
            logmask[i] = Math.max(val, tone[i] + toneatt);

            // AoTuV
            // @ M1
            // The following codes improve a noise problem.
            // A fundamental idea uses the value of masking and carries out
            // the relative compensation of the MDCT.
            // However, this code is not perfect and all noise problems cannot be solved.
            // by Aoyumi @ 2004/04/18

            if (offset_select == 1) {
                coeffi = -17.2f; // coeffi is a -17.2dB threshold
                val = val - logmdct[i]; // val == mdct line value relative to floor in dB

                if (val > coeffi) {
                    // mdct value is > -17.2 dB below floor

                    de = 1.0f - ((val - coeffi) * 0.005f * cx);
                    // pro-rated attenuation:
                    // -0.00 dB boost if mdct value is -17.2dB (relative to floor)
                    // -0.77 dB boost if mdct value is 0dB (relative to floor)
                    // -1.64 dB boost if mdct value is +17.2dB (relative to floor)
                    // etc...

                    if (de < 0) de = 0.0001f;
                } else
                    // mdct value is <= -17.2 dB below floor

                    de = 1.0f - ((val - coeffi) * 0.0003f * cx);
                // pro-rated attenuation:
                // +0.00 dB atten if mdct value is -17.2dB (relative to floor)
                // +0.45 dB atten if mdct value is -34.4dB (relative to floor)
                // etc...

                mdct[i] *= de;
            }
        }
    }

    public void noiseMask(float[] logmdct,
                          float[] logmask) {

        int i, n = this.n;
        float[] work = new float[n];
        bark_noise_hybridmp(n, this.bark, logmdct, logmask,
                140.f, -1);

        for (i = 0; i < n; i++) work[i] = logmdct[i] - logmask[i];

        bark_noise_hybridmp(n, this.bark, work, logmask, 0.0f,
                this.vi.noisewindowfixed);

        for (i = 0; i < n; i++) work[i] = logmdct[i] - work[i];

        for (i = 0; i < n; i++) {
            int dB = (int) (logmask[i] + .5f);
            if (dB >= NOISE_COMPAND_LEVELS) dB = NOISE_COMPAND_LEVELS - 1;
            if (dB < 0) dB = 0;
            logmask[i] = work[i] + this.vi.noisecompand[dB];
        }
    }

    public void toneMask(float[] logfft,
                         float[] logmask,
                         float global_specmax,
                         float local_specmax) {

        int i, n = this.n;

        float[] seed = new float[this.total_octave_lines];
        float att = local_specmax + this.vi.ath_adjatt;
        for (i = 0; i < this.total_octave_lines; i++) seed[i] = NEGINF;

        // set the ATH (floating below localmax, not global max by a
        // specified att)
        if (att < this.vi.ath_maxatt) att = this.vi.ath_maxatt;

        for (i = 0; i < n; i++)
            logmask[i] = this.ath[i] + att;

        // tone masking
        seed_loop(this.tonecurves, logfft, logmask, seed, global_specmax);
        max_seeds(seed, logmask);
    }

    /** bleaugh, this is more complicated than it needs to be */
    void max_seeds(float[] seed,
                   float[] flr) {
        int n = this.total_octave_lines;
        int linesper = this.eighth_octave_lines;
        int linpos = 0;
        int pos;

        seed_chase(seed, linesper, n); // for masking

        pos = this.octave[0] - this.firstoc - (linesper >> 1);

        while (linpos + 1 < this.n) {
            float minV = seed[pos];
            long end = ((this.octave[linpos] + this.octave[linpos + 1]) >> 1) - this.firstoc;
            if (minV > this.vi.tone_abs_limit) minV = this.vi.tone_abs_limit;
            while (pos + 1 <= end) {
                pos++;
                if ((seed[pos] > NEGINF && seed[pos] < minV) || minV == NEGINF)
                    minV = seed[pos];
            }

            end = pos + this.firstoc;
            for (; linpos < this.n && this.octave[linpos] <= end; linpos++)
                if (flr[linpos] < minV) flr[linpos] = minV;
        }

        {
            float minV = seed[this.total_octave_lines - 1];
            for (; linpos < this.n; linpos++)
                if (flr[linpos] < minV) flr[linpos] = minV;
        }
    }

    static void bark_noise_hybridmp(int n, int[] b,
                                    float[] f,
                                    float[] noise,
                                    float offset,
                                    int fixed) {

//float[] N=alloca(n*sizeof(*N));
//float[] X=alloca(n*sizeof(*N));
//float[] XX=alloca(n*sizeof(*N));
//float[] Y=alloca(n*sizeof(*N));
//float[] XY=alloca(n*sizeof(*N));
        float[] N = new float[n];
        float[] X = new float[n];
        float[] XX = new float[n];
        float[] Y = new float[n];
        float[] XY = new float[n];

        float tN, tX, tXX, tY, tXY;
        int i;

        int lo, hi;
        float R, A = 0.0f, B = 0.0f, D = 0.0f;
        float w, x, y;

        tN = tX = tXX = tY = tXY = 0.f;

        y = f[0] + offset;
        if (y < 1.f) y = 1.f;

        w = y * y * .5f;

        tN += w;
        tX += w;
        tY += w * y;

        N[0] = tN;
        X[0] = tX;
        XX[0] = tXX;
        Y[0] = tY;
        XY[0] = tXY;

        for (i = 1, x = 1.f; i < n; i++, x += 1.f) {

            y = f[i] + offset;
            if (y < 1.f) y = 1.f;

            w = y * y;

            tN += w;
            tX += w * x;
            tXX += w * x * x;
            tY += w * y;
            tXY += w * x * y;

            N[i] = tN;
            X[i] = tX;
            XX[i] = tXX;
            Y[i] = tY;
            XY[i] = tXY;
        }

        for (i = 0, x = 0.f; ; i++, x += 1.f) {

            lo = b[i] >> 16;
            if (lo >= 0) break;
            hi = b[i] & 0xffff;

            tN = N[hi] + N[-lo];
            tX = X[hi] - X[-lo];
            tXX = XX[hi] + XX[-lo];
            tY = Y[hi] + Y[-lo];
            tXY = XY[hi] - XY[-lo];

            A = tY * tXX - tX * tXY;
            B = tN * tXY - tX * tY;
            D = tN * tXX - tX * tX;
            R = (A + x * B) / D;
            if (R < 0.f)
                R = 0.f;

            noise[i] = R - offset;
        }

        for (; ; i++, x += 1.f) {

            lo = b[i] >> 16;
            hi = b[i] & 0xffff;
            if (hi >= n) break;

            tN = N[hi] - N[lo];
            tX = X[hi] - X[lo];
            tXX = XX[hi] - XX[lo];
            tY = Y[hi] - Y[lo];
            tXY = XY[hi] - XY[lo];

            A = tY * tXX - tX * tXY;
            B = tN * tXY - tX * tY;
            D = tN * tXX - tX * tX;
            R = (A + x * B) / D;
            if (R < 0.f) R = 0.f;

            noise[i] = R - offset;
        }
        for (; i < n; i++, x += 1.f) {

            R = (A + x * B) / D;
            if (R < 0.f) R = 0.f;

            noise[i] = R - offset;
        }

        if (fixed <= 0) return;

        for (i = 0, x = 0.f; ; i++, x += 1.f) {
            hi = i + fixed / 2;
            lo = hi - fixed;
            if (lo >= 0) break;

            tN = N[hi] + N[-lo];
            tX = X[hi] - X[-lo];
            tXX = XX[hi] + XX[-lo];
            tY = Y[hi] + Y[-lo];
            tXY = XY[hi] - XY[-lo];

            A = tY * tXX - tX * tXY;
            B = tN * tXY - tX * tY;
            D = tN * tXX - tX * tX;
            R = (A + x * B) / D;

            if (R - offset < noise[i]) noise[i] = R - offset;
        }
        for (; ; i++, x += 1.f) {

            hi = i + fixed / 2;
            lo = hi - fixed;
            if (hi >= n) break;

            tN = N[hi] - N[lo];
            tX = X[hi] - X[lo];
            tXX = XX[hi] - XX[lo];
            tY = Y[hi] - Y[lo];
            tXY = XY[hi] - XY[lo];

            A = tY * tXX - tX * tXY;
            B = tN * tXY - tX * tY;
            D = tN * tXX - tX * tX;
            R = (A + x * B) / D;

            if (R - offset < noise[i]) noise[i] = R - offset;
        }
        for (; i < n; i++, x += 1.f) {
            R = (A + x * B) / D;
            if (R - offset < noise[i]) noise[i] = R - offset;
        }
    }

    /** octave/(8*eighth_octave_lines) x scale and dB y scale */
    static void seed_curve(float[] seed,
                           float[][] curves,
                           float amp,
                           int oc, int n,
                           int linesper, float dBoffset) {
        int i, post1;
        int seedptr;
        float[] posts;
        float[] curve;

        int choice = (int) ((amp + dBoffset - P_LEVEL_0) * .1f);
//choice = max(choice, 0);
        choice = Math.max(choice, 0);
//choice = min(choice, P_LEVELS-1);
        choice = Math.min(choice, P_LEVELS - 1);
        posts = curves[choice];
//curve = posts + 2;
        curve = new float[posts.length - 2];
        System.arraycopy(posts, 2, curve, 0, curve.length);
        post1 = (int) posts[1];
        seedptr = (int) (oc + (posts[0] - EHMER_OFFSET) * linesper - (linesper >> 1));

        for (i = (int) posts[0]; i < post1; i++) {
            if (seedptr > 0) {
                float lin = amp + curve[i];
                if (seed[seedptr] < lin) seed[seedptr] = lin;
            }
            seedptr += linesper;
            if (seedptr >= n) break;
        }
    }

    private void seed_loop(float[][][] curves,
                           float[] f,
                           float[] flr,
                           float[] seed,
                           float specmax) {
        PsyInfo vi = this.vi;
        int n = this.n, i;
        float dBoffset = vi.max_curve_dB - specmax;

        // prime the working vector with peak values

        for (i = 0; i < n; i++) {
            float max = f[i];
            int oc = this.octave[i];
            while (i + 1 < n && this.octave[i + 1] == oc) {
                i++;
                if (f[i] > max) max = f[i];
            }

            if (max + 6.f > flr[i]) {
                oc = oc >> this.shiftoc;

                if (oc >= P_BANDS) oc = P_BANDS - 1;
                if (oc < 0) oc = 0;

                seed_curve(seed,
                        curves[oc],
                        max,
                        this.octave[i] - this.firstoc,
                        this.total_octave_lines,
                        this.eighth_octave_lines,
                        dBoffset);
            }
        }
    }

    private static void seed_chase(float[] seeds, int linesper, int n) {
        int[] posstack = new int[n];
        float[] ampstack = new float[n];
        int stack = 0;
        int pos = 0;
        int i;

        for (i = 0; i < n; i++) {
            if (stack < 2) {
                posstack[stack] = i;
                ampstack[stack++] = seeds[i];
            } else {
                while (true) {
                    if (seeds[i] < ampstack[stack - 1]) {
                        posstack[stack] = i;
                        ampstack[stack++] = seeds[i];
                        break;
                    } else {
                        if (i < posstack[stack - 1] + linesper) {
                            if (stack > 1 && ampstack[stack - 1] <= ampstack[stack - 2] &&
                                    i < posstack[stack - 2] + linesper) {
                                // we completely overlap, making stack-1 irrelevant.  pop it
                                stack--;
                                continue;
                            }
                        }
                        posstack[stack] = i;
                        ampstack[stack++] = seeds[i];
                        break;

                    }
                }
            }
        }

        // the stack now contains only the positions that are relevant. Scan
        // 'em straight through

        for (i = 0; i < stack; i++) {
            long endpos;
            if (i < stack - 1 && ampstack[i + 1] > ampstack[i]) {
                endpos = posstack[i + 1];
            } else {
                // +1 is important, else bin 0 is
                // discarded in short frames
                endpos = posstack[i] + linesper + 1;
            }
            if (endpos > n) endpos = n;
            for (; pos < endpos; pos++)
                seeds[pos] = ampstack[i];
        }

        // there.  Linear time.  I now remember this was on a problem set I
        // had in Grad Skool... I didn't solve it at the time ;-)
    }

    static final float[] stereo_threshholds = {0.0f, .5f, 1.0f, 1.5f, 2.5f, 4.5f, 8.5f, 16.5f, 9e10f};
    static final float[] stereo_threshholds_limited = {0.0f, .5f, 1.0f, 1.5f, 2.0f, 2.5f, 4.5f, 8.5f, 9e10f};

    public void couple(int blobno,
                       InfoPsyGlobal g,
                       InfoMapping0 vi,
                       float[][] res,
                       float[][] mag_memo,
                       int[][] mag_sort,
                       int[][] ifloor,
                       int[] nonzero,
                       int sliding_lowpass) {

        int i, j, k, n = this.n;

        // perform any requested channel coupling
        // point stereo can only be used in a first stage (in this encoder)
        // because of the dependency on floor lookups
        for (i = 0; i < vi.coupling_steps; i++) {

            // once we're doing multistage coupling in which a channel goes
            // through more than one coupling step, the floor vector
            // magnitudes will also have to be recalculated a propagated
            // along with PCM.  Right now, we're not (that will wait until 5.1
            // most likely), so the code isn't here yet. The memory management
            // here is all assuming single depth couplings anyway.

            // make sure coupling a zero and a nonzero channel results in two
            // nonzero channels.
            if ((nonzero[vi.coupling_mag[i]] != 0) ||
                    (nonzero[vi.coupling_ang[i]] != 0)) {

                float[] rM = res[vi.coupling_mag[i]];
                float[] rA = res[vi.coupling_ang[i]];
                int[] floorM = ifloor[vi.coupling_mag[i]];
                int[] floorA = ifloor[vi.coupling_ang[i]];
                float prepoint = stereo_threshholds[g.coupling_prepointamp[blobno]];
                float postpoint = stereo_threshholds[g.coupling_postpointamp[blobno]];
                int partition = ((this.vi.normal_point_p != 0) ? this.vi.normal_partition : this.n);
                int limit = g.coupling_pointlimit[this.vi.blockflag][blobno];
                int pointlimit = limit;

                nonzero[vi.coupling_mag[i]] = 1;
                nonzero[vi.coupling_ang[i]] = 1;

                // The threshold of a stereo is changed with the size of n
                if (n > 1000)
                    postpoint = stereo_threshholds_limited[g.coupling_postpointamp[blobno]];

                for (j = 0; j < this.n; j += partition) {
                    float acc = 0.f;

                    for (k = 0; k < partition; k++) {
                        int l = k + j;

                        if (l < sliding_lowpass) {
                            if ((l >= limit && Math.abs(rM[l]) < postpoint && Math.abs(rA[l]) < postpoint) ||
                                    (Math.abs(rM[l]) < prepoint && Math.abs(rA[l]) < prepoint)) {

                                rM[n + l] = precomputed_couple_point(mag_memo[i][l],
                                        floorM[l], floorA[l],
                                        rM[n + l]);
                                rA[n + l] = 0.0f;

                                if (Math.rint(rM[l + n]) == 0.) acc += rM[l + n] * rM[l + n];
                            } else {
                                Parax para = new Parax(rM[l + n], rA[l + n]);
                                couple_lossless(rM[l], rA[l], para);
                                rM[l + n] = para.A;
                                rA[l + n] = para.B;
                            }
                        } else {
                            rM[l + n] = 0.f;
                            rA[l + n] = 0.f;
                        }
                    }

                    if (this.vi.normal_point_p != 0) {
                        for (k = 0; k < partition && acc >= this.vi.normal_thresh; k++) {
                            int l = mag_sort[i][j + k];
                            if (l < sliding_lowpass && l >= pointlimit && Math.rint(rM[l + n]) == 0.f) {
                                rM[l + n] = unitnorm(rM[l + n]);
                                acc -= 1.f;
                            }
                        }
                    }
                }
            }
        }
    }

    static final float[] hypot_lookup = {
            -0.009935f, -0.011245f, -0.012726f, -0.014397f,
            -0.016282f, -0.018407f, -0.020800f, -0.023494f,
            -0.026522f, -0.029923f, -0.033737f, -0.038010f,
            -0.042787f, -0.048121f, -0.054064f, -0.060671f,
            -0.068000f, -0.076109f, -0.085054f, -0.094892f,
            -0.105675f, -0.117451f, -0.130260f, -0.144134f,
            -0.159093f, -0.175146f, -0.192286f, -0.210490f,
            -0.229718f, -0.249913f, -0.271001f, -0.292893f};

    static float precomputed_couple_point(float premag, int floorA, int floorB, float mag) {

        int test = (floorA > floorB) ? -1 : 0;
        int offset = 31 - Math.abs(floorA - floorB);
        float floormag = hypot_lookup[((offset < 0) ? -1 : 0) & offset] + 1.f;

        floormag *= FLOOR1_fromdB_INV_LOOKUP[(floorB & test) | (floorA & (~test))];

        mag = premag * floormag;
        return mag;
    }

    private static void couple_lossless(float A, float B, Parax q) {
        int test1 = (Math.abs(q.A) > Math.abs(q.B)) ? 1 : 0;
        test1 -= (Math.abs(q.A) < Math.abs(q.B)) ? 1 : 0;

        if (test1 == 0) test1 = ((Math.abs(A) > Math.abs(B)) ? (1 << 1) - 1 : 0);
        if (test1 == 1) {
            q.B = (q.A > 0.f ? q.A - q.B : q.B - q.A);
        } else {
            float temp = q.B;
            q.B = (q.B > 0.f ? q.A - q.B : q.B - q.A);
            q.A = temp;
        }

        if (q.B > Math.abs(q.A) * 1.9999f) {
            q.B = -Math.abs(q.A) * 2.f;
            q.A = -q.A;
        }
    }
}

class Parax {

    float A = 0.f;
    float B = 0.f;

    Parax() {
    }

    Parax(float x, float y) {
        this.A = x;
        this.B = y;
    }

    void setMagAng(float m, float a) {
        this.A = m;
        this.B = a;
    }
}
