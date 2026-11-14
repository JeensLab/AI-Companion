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
public class Lpc {

    // en/decode lookups
    private final Drft fft = new Drft();

    private int ln;
    private int m;

    public void init(int mapped, int m) {
        ln = mapped;
        this.m = m;
        // we cheat decoding the LPC spectrum via FFTs
        fft.init(mapped * 2);
    }

    public void clear() {
        fft.clear();
    }

    /**
     * One can do this the long way by generating the transfer function in
     * the time domain and taking the forward FFT of the result.  The
     * results from direct calculation are cleaner and faster.
     * <p>
     * This version does a linear curve generation and then later
     * interpolates the log curve from the linear curve.
     */
    public void lpcToCurve(float[] curve, float[] lpc, float amp) {

        for (int i = 0; i < ln * 2; i++) curve[i] = 0.0f;

        if (amp == 0) return;

        for (int i = 0; i < m; i++) {
            curve[i * 2 + 1] = lpc[i] / (4 * amp);
            curve[i * 2 + 2] = -lpc[i] / (4 * amp);
        }

        fft.backward(curve); // reappropriated ;-)

        {
            int l2 = ln * 2;
            float unit = (float) (1. / amp);
            curve[0] = (float) (1. / (curve[0] * 2 + unit));
            for (int i = 1; i < ln; i++) {
                float real = (curve[i] + curve[l2 - i]);
                float imag = (curve[i] - curve[l2 - i]);

                float a = real + unit;
                curve[i] = (float) (1.0 / fastHypot(a, imag));
            }
        }
    }

    /**
     * Input : n element envelope spectral curve
     * Output: m lpc coefficients, excitation energy
     */
    public float lpcFromCurve(float[] curve, float[] lpc) {
        int n = ln;
        float[] work = new float[n + n];
        float fscale = (float) (.5 / n);
        int i, j;

        // input is a real curve. make it complex-real
        // This mixes phase, but the LPC generation doesn't care.
        for (i = 0; i < n; i++) {
            work[i * 2] = curve[i] * fscale;
            work[i * 2 + 1] = 0;
        }
        work[n * 2 - 1] = curve[n - 1] * fscale;

        n *= 2;
        fft.backward(work);

        // The autocorrelation will not be circular.  Shift, else we lose
        // most of the power in the edges.

        for (i = 0, j = n / 2; i < n / 2; ) {
            float temp = work[i];
            work[i++] = work[j];
            work[j++] = temp;
        }

        return (lpcFromData(work, 0, lpc, n, m));
    }

    /**
     * Autocorrelation LPC coeff generation algorithm invented by
     * N. Levinson in 1947, modified by J. Durbin in 1959.
     * <p>
     * Input : n elements of time doamin data
     * Output: m lpc coefficients, excitation energy
     */
    static float lpcFromData(float[] data, int ndata, float[] lpc, int n, int m) {
        double[] aut = new double[m + 1];
        double error;
        int i, j;

        // autocorrelation, p+1 lag coefficients

        j = m + 1;
        while (j-- != 0) {
            double d = 0;
            for (i = j; i < n; i++) d += data[i + ndata] * data[i - j + ndata];
            aut[j] = d;
        }

        // Generate lpc coefficients from autocorr values

        error = aut[0];

        for (i = 0; i < m; i++) {
            double r = -aut[i + 1];

            if (error == 0) {
                for (int k = 0; k < m; k++) lpc[k] = 0.0f;
                return 0;
            }

            // Sum up this iteration's reflection coefficient; note that in
            // Vorbis we don't save it.  If anyone wants to recycle this code
            // and needs reflection coefficients, save the results of 'r' from
            // each iteration.

            for (j = 0; j < i; j++) r -= lpc[j] * aut[i - j];
            r /= error;

            // Update LPC coefficients and total error

            lpc[i] = (float) r;
            for (j = 0; j < i / 2; j++) {
                float tmp = lpc[j];
                lpc[j] = (float) (lpc[j] + r * lpc[i - 1 - j]);
                lpc[i - 1 - j] = (float) (lpc[i - 1 - j] + r * tmp);
            }
            if (i % 2 != 0) lpc[j] = (float) (lpc[j] + lpc[j] * r);

            error *= 1.0 - r * r;
        }

        // we need the error value to know how big an impulse to hit the
        // filter with later

        return (float) error;
    }

    static float lpcFromDataZ(float[] data, float[] lpci, int n, int m, int current) {
        double[] aut = new double[m + 1];
        double[] lpc = new double[m];
        double error;
        int i, j;

        // autocorrelation, p+1 lag coefficients
        j = m + 1;
        int gr = current - n;
        while (j-- != 0) {
            double d = 0;
            for (i = current + 10; i-- > gr; ) d += (double) data[i - j] * data[i];
            aut[j] = d;
        }

        // Generate lpc coefficients from autocorr values
        error = aut[0];

        for (i = 0; i < m; i++) {
            double r = -aut[i + 1];

            if (error == 0) {
                for (int k = m; --k >= 0; ) lpci[k] = 0.0f;
                return 0;
            }

            // Sum up this iteration's reflection coefficient; note that in
            // Vorbis we don't save it.  If anyone wants to recycle this code
            // and needs reflection coefficients, save the results of 'r' from
            // each iteration.

            for (j = 0; j < i; j++) r -= lpc[j] * aut[i - j];
            r /= error;

            // Update LPC coefficients and total error

            lpc[i] = r;
            for (j = 0; j < i / 2; j++) {
                double tmp = lpc[j];

                lpc[j] += r * lpc[i - 1 - j];
                lpc[i - 1 - j] += r * tmp;
            }
            if (i % 2 != 0) lpc[j] += lpc[j] * r;

            error *= 1.f - r * r;
        }

        for (j = 0; j < m; j++) lpci[j] = (float) lpc[j];

        // we need the error value to know how big an impulse to hit the
        // filter with later

        return (float) error;
    }

    /**
     * in: coeff[0...m-1] LPC coefficients
     *     prime[0...m-1] initial values (allocated size of n+m-1)
     * out: data[0...n-1] data samples
     */
    static void lpcPredict(float[] coeff, float[] prime, int nprime, int m,
                           float[] data, int ndata, int n) {

        int i, j, o, p;
        float y;
        float[] work = new float[m + n];

        if (prime != null) {
            System.arraycopy(prime, nprime, work, 0, m);
        }

        for (i = 0; i < n; i++) {
            y = 0;
            o = i;
            p = m;
            for (j = 0; j < m; j++)
                y -= work[o++] * coeff[--p];

            data[i + ndata] = work[o] = y;
        }
    }

    /**
     * in: coeff[0...m-1] LPC coefficients
     *     prime[0...m-1] initial values (allocated size of n+m-1)
     * out: data[0...n-1] data samples
     */
    static void lpcPredictZ(float[] coeff, float[] prime, int nprime, int m,
                            float[] data, int ndata, int n) {

        int i, j, o, p;
        float y;
        float[] work = new float[m + n];

        for (i = m + n; i-- > n; )
            work[i] = prime[i];

        for (i = n; i-- > 0; ) {
            y = 0;
            o = i + m;
            for (j = m; --j >= 0; )
                y -= work[o--] * coeff[j];

            data[ndata--] = work[o] = y;
        }
    }

    private static float fastHypot(float a, float b) {
        return (float) Math.sqrt(a * a + b * b);
    }
}
