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
public class Mdct {

    static private final float cPI3_8 = 0.38268343236508977175f;
    static private final float cPI2_8 = 0.70710678118654752441f;
    static private final float cPI1_8 = 0.92387953251128675613f;
    static private final float M_PI = 3.1415926536f;

    static private final int TRIGBITS = 14;

    static private float FLOAT_CONV(double x) {
        return ((((float) (x)) * 2) / 2);
    }

    static private float MULT_NORM(float x) {
        return x;
    }

    static private float HALVE(float x) {
        if (x != 0) {
            int yy = 0;
        }
        return (x / 2);
    }

    int n;
    int log2n;

    float[] trig;
    int[] bitrev;

    float scale;

    void init(int n) {
        this.bitrev = new int[n / 4];
        this.trig = new float[n + n / 4];

        int n2 = n >>> 1;
        this.log2n = (int) Math.rint(Math.log(n) / Math.log(2));
        this.n = n;

        int AE = 0;
        int AO = 1;
        int BE = AE + n / 2;
        int BO = BE + 1;
        int CE = BE + n / 2;
        int CO = CE + 1;
        // trig lookups...
        for (int i = 0; i < n / 4; i++) {
            this.trig[i * 2] = FLOAT_CONV(Math.cos((M_PI / n) * (4 * i)));
            this.trig[i * 2 + 1] = FLOAT_CONV(-Math.sin((M_PI / n) * (4 * i)));
            this.trig[n2 + i * 2] = FLOAT_CONV(Math.cos((M_PI / (2 * n)) * (2 * i + 1)));
            this.trig[n2 + i * 2 + 1] = FLOAT_CONV(Math.sin((M_PI / (2 * n)) * (2 * i + 1)));
        }
        for (int i = 0; i < n / 8; i++) {
            this.trig[n + i * 2] = FLOAT_CONV(Math.cos((M_PI / n) * (4 * i + 2)) * 0.5);
            this.trig[n + i * 2 + 1] = FLOAT_CONV(-Math.sin((M_PI / n) * (4 * i + 2)) * 0.5);
        }

        {
            int mask = (1 << (log2n - 1)) - 1;
            int msb = 1 << (log2n - 2);
            for (int i = 0; i < n / 8; i++) {
                int acc = 0;
                for (int j = 0; msb >>> j != 0; j++)
                    if (((msb >>> j) & i) != 0) acc |= 1 << j;
                this.bitrev[i * 2] = ((~acc) & mask) - 1;
                this.bitrev[i * 2 + 1] = acc;
            }
        }
        this.scale = 4.f / n;
    }

    void clear() {
    }

    private void mdct_butterflies(float[] x,
                                  int px, //pointer in x
                                  int points) {

        float[] T = this.trig;
        int stages = this.log2n - 5;
        int i, j;

        if (--stages > 0) {
            mdct_butterfly_first(T, x, px, points);
        }

        for (i = 1; --stages > 0; i++) {
            for (j = 0; j < (1 << i); j++)
                mdct_butterfly_generic(T, x, px + (points >> i) * j, points >> i, 4 << i);
        }

        for (j = 0; j < points; j += 32) {
            mdct_butterfly_32(x, px + j);
        }
    }

    /** 8 point butterfly (in place, 4 register) */
    static void mdct_butterfly_8(float[] x, int px) {
        float r0 = x[6 + px] + x[2 + px];
        float r1 = x[6 + px] - x[2 + px];
        float r2 = x[4 + px] + x[0 + px];
        float r3 = x[4 + px] - x[0 + px];

        x[6 + px] = r0 + r2;
        x[4 + px] = r0 - r2;

        r0 = x[5 + px] - x[1 + px];
        r2 = x[7 + px] - x[3 + px];
        x[0 + px] = r1 + r0;
        x[2 + px] = r1 - r0;

        r0 = x[5 + px] + x[1 + px];
        r1 = x[7 + px] + x[3 + px];
        x[3 + px] = r2 + r3;
        x[1 + px] = r2 - r3;
        x[7 + px] = r1 + r0;
        x[5 + px] = r1 - r0;

    }

    /** 16 point butterfly (in place, 4 register) */
    static void mdct_butterfly_16(float[] x, int px) {
        float r0 = x[1 + px] - x[9 + px];
        float r1 = x[0 + px] - x[8 + px];

        x[8 + px] += x[0 + px];
        x[9 + px] += x[1 + px];
        x[0 + px] = MULT_NORM((r0 + r1) * cPI2_8);
        x[1 + px] = MULT_NORM((r0 - r1) * cPI2_8);

        r0 = x[3 + px] - x[11 + px];
        r1 = x[10 + px] - x[2 + px];
        x[10 + px] += x[2 + px];
        x[11 + px] += x[3 + px];
        x[2 + px] = r0;
        x[3 + px] = r1;

        r0 = x[12 + px] - x[4 + px];
        r1 = x[13 + px] - x[5 + px];
        x[12 + px] += x[4 + px];
        x[13 + px] += x[5 + px];
        x[4 + px] = MULT_NORM((r0 - r1) * cPI2_8);
        x[5 + px] = MULT_NORM((r0 + r1) * cPI2_8);

        r0 = x[14 + px] - x[6 + px];
        r1 = x[15 + px] - x[7 + px];
        x[14 + px] += x[6 + px];
        x[15 + px] += x[7 + px];
        x[6 + px] = r0;
        x[7 + px] = r1;

        mdct_butterfly_8(x, px);
        mdct_butterfly_8(x, px + 8);
    }

    /** 32 point butterfly (in place, 4 register) */
    static void mdct_butterfly_32(float[] x, int px) {
        float r0 = x[30 + px] - x[14 + px];
        float r1 = x[31 + px] - x[15 + px];

        x[30 + px] += x[14 + px];
        x[31 + px] += x[15 + px];
        x[14 + px] = r0;
        x[15 + px] = r1;

        r0 = x[28 + px] - x[12 + px];
        r1 = x[29 + px] - x[13 + px];
        x[28 + px] += x[12 + px];
        x[29 + px] += x[13 + px];
        x[12 + px] = MULT_NORM(r0 * cPI1_8 - r1 * cPI3_8);
        x[13 + px] = MULT_NORM(r0 * cPI3_8 + r1 * cPI1_8);

        r0 = x[26 + px] - x[10 + px];
        r1 = x[27 + px] - x[11 + px];
        x[26 + px] += x[10 + px];
        x[27 + px] += x[11 + px];
        x[10 + px] = MULT_NORM((r0 - r1) * cPI2_8);
        x[11 + px] = MULT_NORM((r0 + r1) * cPI2_8);

        r0 = x[24 + px] - x[8 + px];
        r1 = x[25 + px] - x[9 + px];
        x[24 + px] += x[8 + px];
        x[25 + px] += x[9 + px];
        x[8 + px] = MULT_NORM(r0 * cPI3_8 - r1 * cPI1_8);
        x[9 + px] = MULT_NORM(r1 * cPI3_8 + r0 * cPI1_8);

        r0 = x[22 + px] - x[6 + px];
        r1 = x[7 + px] - x[23 + px];
        x[22 + px] += x[6 + px];
        x[23 + px] += x[7 + px];
        x[6 + px] = r1;
        x[7 + px] = r0;

        r0 = x[4 + px] - x[20 + px];
        r1 = x[5 + px] - x[21 + px];
        x[20 + px] += x[4 + px];
        x[21 + px] += x[5 + px];
        x[4 + px] = MULT_NORM(r1 * cPI1_8 + r0 * cPI3_8);
        x[5 + px] = MULT_NORM(r1 * cPI3_8 - r0 * cPI1_8);

        r0 = x[2 + px] - x[18 + px];
        r1 = x[3 + px] - x[19 + px];
        x[18 + px] += x[2 + px];
        x[19 + px] += x[3 + px];
        x[2 + px] = MULT_NORM((r1 + r0) * cPI2_8);
        x[3 + px] = MULT_NORM((r1 - r0) * cPI2_8);

        r0 = x[0 + px] - x[16 + px];
        r1 = x[1 + px] - x[17 + px];
        x[16 + px] += x[0 + px];
        x[17 + px] += x[1 + px];
        x[0 + px] = MULT_NORM(r1 * cPI3_8 + r0 * cPI1_8);
        x[1 + px] = MULT_NORM(r1 * cPI1_8 - r0 * cPI3_8);

        mdct_butterfly_16(x, px);
        mdct_butterfly_16(x, px + 16);

    }

    /** N point first stage butterfly (in place, 2 register) */
    static void mdct_butterfly_first(float[] T,
                                     float[] x,
                                     int px, //pointer in x
                                     int points) {

//float[] x1 = x + points - 8;
//float[] x2 = x + (points >> 1) - 8;
        int px1 = points - 8 + px;
        int px2 = (points >> 1) - 8 + px;
        int pT = 0;
        float r0;
        float r1;

        do {
            r0 = x[6 + px1] - x[6 + px2];
            r1 = x[7 + px1] - x[7 + px2];
            x[6 + px1] += x[6 + px2];
            x[7 + px1] += x[7 + px2];
            x[6 + px2] = MULT_NORM(r1 * T[1 + pT] + r0 * T[0 + pT]);
            x[7 + px2] = MULT_NORM(r1 * T[0 + pT] - r0 * T[1 + pT]);

            r0 = x[4 + px1] - x[4 + px2];
            r1 = x[5 + px1] - x[5 + px2];
            x[4 + px1] += x[4 + px2];
            x[5 + px1] += x[5 + px2];
            x[4 + px2] = MULT_NORM(r1 * T[5 + pT] + r0 * T[4 + pT]);
            x[5 + px2] = MULT_NORM(r1 * T[4 + pT] - r0 * T[5 + pT]);

            r0 = x[2 + px1] - x[2 + px2];
            r1 = x[3 + px1] - x[3 + px2];
            x[2 + px1] += x[2 + px2];
            x[3 + px1] += x[3 + px2];
            x[2 + px2] = MULT_NORM(r1 * T[9 + pT] + r0 * T[8 + pT]);
            x[3 + px2] = MULT_NORM(r1 * T[8 + pT] - r0 * T[9 + pT]);

            r0 = x[0 + px1] - x[0 + px2];
            r1 = x[1 + px1] - x[1 + px2];
            x[0 + px1] += x[0 + px2];
            x[1 + px1] += x[1 + px2];
            x[0 + px2] = MULT_NORM(r1 * T[13 + pT] + r0 * T[12 + pT]);
            x[1 + px2] = MULT_NORM(r1 * T[12 + pT] - r0 * T[13 + pT]);

            px1 -= 8;
            px2 -= 8;
            pT += 16;

        } while (px2 >= (x.length - px));
    }

    /** N/stage point generic N stage butterfly (in place, 2 register) */
    static void mdct_butterfly_generic(float[] T,
                                       float[] x,
                                       int px,
                                       int points,
                                       int trigint) {

//float[] x1 = x + points - 8;
//float[] x2 = x + (points>>1) - 8;
        int px1 = points - 8 + px;
        int px2 = (points >> 1) - 8 + px;
        int pT = 0;
        float r0;
        float r1;

        do {
            r0 = x[6 + px1] - x[6 + px2];
            r1 = x[7 + px1] - x[7 + px2];
            x[6 + px1] += x[6 + px2];
            x[7 + px1] += x[7 + px2];
            x[6 + px2] = MULT_NORM(r1 * T[1 + pT] + r0 * T[0 + pT]);
            x[7 + px2] = MULT_NORM(r1 * T[0 + pT] - r0 * T[1 + pT]);

            pT += trigint;

            r0 = x[4 + px1] - x[4 + px2];
            r1 = x[5 + px1] - x[5 + px2];
            x[4 + px1] += x[4 + px2];
            x[5 + px1] += x[5 + px2];
            x[4 + px2] = MULT_NORM(r1 * T[1 + pT] + r0 * T[0 + pT]);
            x[5 + px2] = MULT_NORM(r1 * T[0 + pT] - r0 * T[1 + pT]);

            pT += trigint;

            r0 = x[2 + px1] - x[2 + px2];
            r1 = x[3 + px1] - x[3 + px2];
            x[2 + px1] += x[2 + px2];
            x[3 + px1] += x[3 + px2];
            x[2 + px2] = MULT_NORM(r1 * T[1 + pT] + r0 * T[0 + pT]);
            x[3 + px2] = MULT_NORM(r1 * T[0 + pT] - r0 * T[1 + pT]);

            pT += trigint;

            r0 = x[0 + px1] - x[0 + px2];
            r1 = x[1 + px1] - x[1 + px2];
            x[0 + px1] += x[0 + px2];
            x[1 + px1] += x[1 + px2];
            x[0 + px2] = MULT_NORM(r1 * T[1 + pT] + r0 * T[0 + pT]);
            x[1 + px2] = MULT_NORM(r1 * T[0 + pT] - r0 * T[1 + pT]);

            pT += trigint;
            px1 -= 8;
            px2 -= 8;

        } while (px2 >= px);
//} while (px2 >= (x.length - px));
    }

    private void mdct_bitreverse(float[] x) {
        int n = this.n;
        int[] bit = this.bitrev;
        int pbit = 0;
//float[] w0 = x;
        int pw0 = 0;
//float[] w1 = x = w0+(n>>1);
        int pw1 = (n >> 1);
//float[] T = init.trig+n;
        float[] T = this.trig;
        int pT = n;

        do {
//float[] x0 = x + bit[0];
            int px0 = bit[0 + pbit] + (n >> 1);
//float[] x1 = x + bit[1];
            int px1 = bit[1 + pbit] + (n >> 1);

            float r0 = x[1 + px0] - x[1 + px1];
            float r1 = x[0 + px0] + x[0 + px1];
            float r2 = MULT_NORM(r1 * T[0 + pT] + r0 * T[1 + pT]);
            float r3 = MULT_NORM(r1 * T[1 + pT] - r0 * T[0 + pT]);

            pw1 -= 4;

            r0 = HALVE(x[1 + px0] + x[1 + px1]);
            r1 = HALVE(x[0 + px0] - x[0 + px1]);

            x[0 + pw0] = r0 + r2;
            x[2 + pw1] = r0 - r2;
            x[1 + pw0] = r1 + r3;
            x[3 + pw1] = r3 - r1;

//x0 = x + bit[2];
            px0 = bit[2 + pbit] + (n >> 1);
//x1 = x + bit[3];
            px1 = bit[3 + pbit] + (n >> 1);

            r0 = x[1 + px0] - x[1 + px1];
            r1 = x[0 + px0] + x[0 + px1];
            r2 = MULT_NORM(r1 * T[2 + pT] + r0 * T[3 + pT]);
            r3 = MULT_NORM(r1 * T[3 + pT] - r0 * T[2 + pT]);

            r0 = HALVE(x[1 + px0] + x[1 + px1]);
            r1 = HALVE(x[0 + px0] - x[0 + px1]);

            x[2 + pw0] = r0 + r2;
            x[0 + pw1] = r0 - r2;
            x[3 + pw0] = r1 + r3;
            x[1 + pw1] = r3 - r1;

            pT += 4;
            pbit += 4;
            pw0 += 4;

        } while (pw0 < pw1);
    }

    public void mdct_forward(float[] in, float[] out) {
        int n = this.n;
        int n2 = n >> 1;
        int n4 = n >> 2;
        int n8 = n >> 3;
        float[] w = new float[n]; // alloca(n * sizeof(*w)); // forward needs working space
//float[] w2 = w + n2;
        int pw2 = n2;
        // rotate

        // window + rotate + step 1

        float r0;
        float r1;
//float[] x0 = in + n2 + n4;
        int px0 = n2 + n4;
//float[] x1 = x0 + 1;
        int px1 = px0 + 1;
//float[] T = init->trig + n2;
        float[] T = this.trig;
        int pT = n2;

        int i = 0;

        for (i = 0; i < n8; i += 2) {
            px0 -= 4;
            pT -= 2;
            r0 = in[2 + px0] + in[0 + px1];
            r1 = in[0 + px0] + in[2 + px1];
            w[i + pw2] = MULT_NORM((r1 * T[1 + pT]) + (r0 * T[0 + pT]));
            w[i + 1 + pw2] = MULT_NORM((r1 * T[0 + pT]) - (r0 * T[1 + pT]));
            px1 += 4;
        }

//x1 = in + 1;
        px1 = 1;

        for (; i < n2 - n8; i += 2) {
            pT -= 2;
            px0 -= 4;
            r0 = in[2 + px0] - in[0 + px1];
            r1 = in[0 + px0] - in[2 + px1];
            w[i + pw2] = MULT_NORM(r1 * T[1 + pT] + r0 * T[0 + pT]);
            w[i + 1 + pw2] = MULT_NORM(r1 * T[0 + pT] - r0 * T[1 + pT]);
            px1 += 4;
        }

//x0=in+n;
        px0 = n;

        for (; i < n2; i += 2) {
            pT -= 2;
            px0 -= 4;
            r0 = -in[2 + px0] - in[0 + px1];
            r1 = -in[0 + px0] - in[2 + px1];
            w[i + pw2] = MULT_NORM(r1 * T[1 + pT] + r0 * T[0 + pT]);
            w[i + 1 + pw2] = MULT_NORM(r1 * T[0 + pT] - r0 * T[1 + pT]);
            px1 += 4;
        }

        mdct_butterflies(w, n2, n2);
        mdct_bitreverse(w);

        // roatate + window

//T=init->trig+n2;
        pT = n2;
//x0=out+n2;
        px0 = n2;
        int pw = 0;
        for (i = 0; i < n4; i++) {
            px0--;
            out[i] = MULT_NORM((w[0 + pw] * T[0 + pT] + w[1 + pw] * T[1 + pT]) * this.scale);
            out[0 + px0] = MULT_NORM((w[0 + pw] * T[1 + pT] - w[1 + pw] * T[0 + pT]) * this.scale);
            pw += 2;
            pT += 2;
        }
    }

    float[] _x = new float[1024];
    float[] _w = new float[1024];

    synchronized void backward(float[] in, float[] out) {
        if (_x.length < n / 2) {
            _x = new float[n / 2];
        }
        if (_w.length < n / 2) {
            _w = new float[n / 2];
        }
        float[] x = _x;
        float[] w = _w;
        int n2 = n >>> 1;
        int n4 = n >>> 2;
        int n8 = n >>> 3;

        // rotate + step 1
        {
            int inO = 1;
            int xO = 0;
            int A = n2;

            int i;
            for (i = 0; i < n8; i++) {
                A -= 2;
                x[xO++] = -in[inO + 2] * trig[A + 1] - in[inO] * trig[A];
                x[xO++] = in[inO] * trig[A + 1] - in[inO + 2] * trig[A];
                inO += 4;
            }

            inO = n2 - 4;

            for (i = 0; i < n8; i++) {
                A -= 2;
                x[xO++] = in[inO] * trig[A + 1] + in[inO + 2] * trig[A];
                x[xO++] = in[inO] * trig[A] - in[inO + 2] * trig[A + 1];
                inO -= 4;
            }
        }

        float[] xxx = mdct_kernel(x, w, n, n2, n4, n8);
        int xx = 0;

        // step 8

        {
            int B = n2;
            int o1 = n4, o2 = o1 - 1;
            int o3 = n4 + n2, o4 = o3 - 1;

            for (int i = 0; i < n4; i++) {
                float temp1 = (xxx[xx] * trig[B + 1] - xxx[xx + 1] * trig[B]);
                float temp2 = -(xxx[xx] * trig[B] + xxx[xx + 1] * trig[B + 1]);

                out[o1] = -temp1;
                out[o2] = temp1;
                out[o3] = temp2;
                out[o4] = temp2;

                o1++;
                o2--;
                o3++;
                o4--;
                xx += 2;
                B += 2;
            }
        }
    }

    private float[] mdct_kernel(float[] x, float[] w,
                                int n, int n2, int n4, int n8) {
        // step 2

        int xA = n4;
        int xB = 0;
        int w2 = n4;
        int A = n2;

        for (int i = 0; i < n4; ) {
            float x0 = x[xA] - x[xB];
            float x1;
            w[w2 + i] = x[xA++] + x[xB++];

            x1 = x[xA] - x[xB];
            A -= 4;

            w[i++] = x0 * trig[A] + x1 * trig[A + 1];
            w[i] = x1 * trig[A] - x0 * trig[A + 1];

            w[w2 + i] = x[xA++] + x[xB++];
            i++;
        }

        // step 3

        {
            for (int i = 0; i < log2n - 3; i++) {
                int k0 = n >>> (i + 2);
                int k1 = 1 << (i + 3);
                int wbase = n2 - 2;

                A = 0;
                float[] temp;

                for (int r = 0; r < (k0 >>> 2); r++) {
                    int w1 = wbase;
                    w2 = w1 - (k0 >> 1);
                    float AEv = trig[A], wA;
                    float AOv = trig[A + 1], wB;
                    wbase -= 2;

                    k0++;
                    for (int s = 0; s < (2 << i); s++) {
                        wB = w[w1] - w[w2];
                        x[w1] = w[w1] + w[w2];

                        wA = w[++w1] - w[++w2];
                        x[w1] = w[w1] + w[w2];

                        x[w2] = wA * AEv - wB * AOv;
                        x[w2 - 1] = wB * AEv + wA * AOv;

                        w1 -= k0;
                        w2 -= k0;
                    }
                    k0--;
                    A += k1;
                }

                temp = w;
                w = x;
                x = temp;
            }
        }

        // step 4, 5, 6, 7
        {
            int C = n;
            int bit = 0;
            int x1 = 0;
            int x2 = n2 - 1;

            for (int i = 0; i < n8; i++) {
                int t1 = bitrev[bit++];
                int t2 = bitrev[bit++];

                float wA = w[t1] - w[t2 + 1];
                float wB = w[t1 - 1] + w[t2];
                float wC = w[t1] + w[t2 + 1];
                float wD = w[t1 - 1] - w[t2];

                float wACE = wA * trig[C];
                float wBCE = wB * trig[C++];
                float wACO = wA * trig[C];
                float wBCO = wB * trig[C++];

                x[x1++] = (wC + wACO + wBCE) * .5f;
                x[x2--] = (-wD + wBCO - wACE) * .5f;
                x[x1++] = (wD + wBCO - wACE) * .5f;
                x[x2--] = (wC - wACO - wBCE) * .5f;
            }
        }
        return x;
    }
}

class mdct_lookup {

    int n;
    int log2n;

    float[] trig;
    int[] bitrev;

    float scale;
}
