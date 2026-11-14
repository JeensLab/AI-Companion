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
public class Lsp {

    static final float M_PI = (float) (3.1415926539);

    static void lsp_to_curve(float[] curve,
                             int[] map, int n, int ln,
                             float[] lsp, int m,
                             float amp, float ampoffset) {
        int i;
        float wdel = M_PI / ln;
        for (i = 0; i < m; i++) lsp[i] = Lookup.coslook(lsp[i]);
        int m2 = (m / 2) * 2;

        i = 0;
        while (i < n) {
            int k = map[i];
            float p = .7071067812f;
            float q = .7071067812f;
            float w = Lookup.coslook(wdel * k);
            int ftmp = 0;
            int c = m >>> 1;

            for (int j = 0; j < m2; j += 2) {
                q *= lsp[j] - w;
                p *= lsp[j + 1] - w;
            }

            if ((m & 1) != 0) {
                // odd order filter; slightly assymetric
                // the last coefficient
                q *= lsp[m - 1] - w;
                q *= q;
                p *= p * (1.f - w * w);
            } else {
                // even order filter; still symmetric
                q *= q * (1.f + w);
                p *= p * (1.f - w);
            }

            q = p + q;
            int hx = Float.floatToIntBits(q);
            int ix = 0x7fff_ffff & hx;
            int qexp = 0;

            if (ix >= 0x7f80_0000 || (ix == 0)) {
                // 0,inf,nan
            } else {
                if (ix < 0x0080_0000) { // subnormal
                    q = (float) (q * 3.3554432000e+07); // 0x4c000000
                    hx = Float.floatToIntBits(q);
                    ix = 0x7fff_ffff & hx;
                    qexp = -25;
                }
                qexp += ((ix >>> 23) - 126);
                hx = (hx & 0x807f_ffff) | 0x3f00_0000;
                q = Float.intBitsToFloat(hx);
            }

            q = Lookup.fromdBlook(amp *
                    Lookup.invsqlook(q) *
                    Lookup.invsq2explook(qexp + m) - ampoffset);

            do {
                curve[i++] *= q;
            } while (i < n && map[i] == k);
        }
    }
}
