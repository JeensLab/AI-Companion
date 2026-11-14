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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;

import biniu.ogg.Buffer;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Floor0 extends FuncFloor {

    private static final Logger logger = System.getLogger(Floor0.class.getName());

    @Override
    public void pack(Object i, Buffer opb) {
        InfoFloor0 info = (InfoFloor0) i;
        opb.write(info.order, 8);
        opb.write(info.rate, 16);
        opb.write(info.barkmap, 16);
        opb.write(info.ampbits, 6);
        opb.write(info.ampdB, 8);
        opb.write(info.numbooks - 1, 4);
        for (int j = 0; j < info.numbooks; j++)
            opb.write(info.books[j], 8);
    }

    @Override
    Object unpack(Info i, Buffer opb) {
logger.log(Level.WARNING, "not implemented yet");
        return null; // TODO
    }

    @Override
    public Object look(DspState vd, Object mi) {
        InfoFloor0 info = (InfoFloor0) mi;
        LookFloor0 look = new LookFloor0();
        look.m = info.order;
        look.ln = info.barkmap;
        look.vi = info;
//    look.n=info.postlist[1];
        look.linearmap = new int[2];

        return look;
    }

    @Override
    public Object look(DspState vd, InfoMode mi, Object i) {
        float scale;
        Info vi = vd.vi;
        InfoFloor0 info = (InfoFloor0) i;
        LookFloor0 look = new LookFloor0();
        look.m = info.order;
        look.n = vi.blocksizes[mi.blockflag] / 2;
        look.ln = info.barkmap;
        look.vi = info;
        look.lpcLook.init(look.ln, look.m);

        // we choose a scaling constant so that:
        //  floor(bark(rate/2-1)*C)=mapped-1
        // floor(bark(rate/2)*C)=mapped
        scale = look.ln / toBARK((float) (info.rate / 2.));

        // the mapping from a linear scale to a smaller bark scale is
        // straightforward.  We do *not* make sure that the linear mapping
        // does not skip bark-scale bins; the decoder simply skips them and
        // the encoder may do what it wishes in filling them.  They're
        // necessary in some mapping combinations to keep the scale spacing
        // accurate
        look.linearmap = new int[look.n];
        for (int j = 0; j < look.n; j++) {
            int val = (int) Math.floor(toBARK((float) ((info.rate / 2.) / look.n * j))
                    * scale); // bark numbers represent band edges
            if (val >= look.ln) val = look.ln; // guard against the approximation
            look.linearmap[j] = val;
        }
        return look;
    }

    private static float toBARK(float f) {
        return (float) (13.1 * Math.atan(.00074 * (f)) + 2.24 * Math.atan((f) * (f) * 1.85e-8) + 1e-4 * (f));
    }

    public static Object state(Object i) {
        EchstateFloor0 state = new EchstateFloor0();
        InfoFloor0 info = (InfoFloor0) i;

        // a safe size if usually too big (dim==1)
        state.codewords = new int[info.order];
        state.curve = new float[info.barkmap];
        state.frameno = -1;
        return state;
    }

    @Override
    public void clear(Object obj) {
        obj = null;
    }

    @Override
    int forward(Block vb, Object i, float[] in, float[] out, Object vs) {
        return 0;
    }

    float[] lsp = null;

    public int inverse(Block vb, Object i, float[] out) {
        LookFloor0 look = (LookFloor0) i;
        InfoFloor0 info = look.vi;
        int ampraw = vb.opb.read(info.ampbits);
        if (ampraw > 0) { // also handles the -1 out of data case
            int maxval = (1 << info.ampbits) - 1;
            float amp = (float) ampraw / maxval * info.ampdB;
            int booknum = vb.opb.read(ilog(info.numbooks));

            if (booknum != -1 && booknum < info.numbooks) {

                synchronized (this) {
                    if (lsp == null || lsp.length < look.m) {
                        lsp = new float[look.m];
                    } else {
                        for (int j = 0; j < look.m; j++) lsp[j] = 0.f;
                    }

                    CodeBook b = vb.dspState.fullbooks[info.books[booknum]];
                    float last = 0.f;

                    //memset(out,0,sizeof(float)*look->m);
                    for (int j = 0; j < look.m; j++) out[j] = 0.0f;

                    for (int j = 0; j < look.m; j += b.dim) {
                        if (b.decodevs(lsp, j, vb.opb, 1, -1) == -1) {
                            //goto eop;
                            // memset(out,0,sizeof(float)*look->n);
                            for (int k = 0; k < look.n; k++) out[k] = 0.0f;
                            return 0;
                        }
                    }
                    for (int j = 0; j < look.m; ) {
                        for (int k = 0; k < b.dim; k++, j++) lsp[j] += last;
                        last = lsp[j - 1];
                    }
                    // take the coefficients back to a spectral envelope curve
//lsp_to_lpc(out, out, look.m);
//lpc_to_curve(out, out, amp, look, "", 0);
//for (int j = 0; j < look.n; j++) {
//    out[j] = fromdB(out[j] - info.ampdB);
//}
                    Lsp.lsp_to_curve(out, look.linearmap, look.n, look.ln,
                            lsp, look.m, amp, info.ampdB);

                    return 1;
                }
            }
        }
        return 0;
    }

    @Override
    public Object inverse1(Block vb, Object i, Object memo) {
        LookFloor0 look = (LookFloor0) i;
        InfoFloor0 info = look.vi;
        float[] lsp = null;
        if (memo instanceof float[]) {
            lsp = (float[]) memo;
        }

        int ampraw = vb.opb.read(info.ampbits);
        if (ampraw > 0) { // also handles the -1 out of data case
            int maxval = (1 << info.ampbits) - 1;
            float amp = (float) ampraw / maxval * info.ampdB;
            int booknum = vb.opb.read(ilog(info.numbooks));

            if (booknum != -1 && booknum < info.numbooks) {
                CodeBook b = vb.dspState.fullbooks[info.books[booknum]];
                float last = 0.f;

                if (lsp == null || lsp.length < look.m + 1) {
                    lsp = new float[look.m + 1];
                } else {
                    Arrays.fill(lsp, 0.f);
                }

                for (int j = 0; j < look.m; j += b.dim) {
                    if (b.decodev_set(lsp, j, vb.opb, b.dim) == -1) {
                        return null;
                    }
                }

                for (int j = 0; j < look.m; ) {
                    for (int k = 0; k < b.dim; k++, j++) lsp[j] += last;
                    last = lsp[j - 1];
                }
                lsp[look.m] = amp;
                return lsp;
            }
        }
        return null;
    }

    @Override
    public int inverse2(Block vb, Object i, Object memo, float[] out) {
        LookFloor0 look = (LookFloor0) i;
        InfoFloor0 info = look.vi;

        if (memo != null) {
            float[] lsp = (float[]) memo;
            float amp = lsp[look.m];

            Lsp.lsp_to_curve(out, look.linearmap, look.n, look.ln,
                    lsp, look.m, amp, info.ampdB);
            return 1;
        }
        for (int j = 0; j < look.n; j++) {
            out[j] = 0.f;
        }
        return 0;
    }

    private static float fromdB(float x) {
        return (float) (Math.exp((x) * .11512925));
    }

    private static int ilog(int v) {
        int ret = 0;
        while (v != 0) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    private static void lsp_to_lpc(float[] lsp, float[] lpc, int m) {
        int i, j, m2 = m / 2;
        float[] O = new float[m2];
        float[] E = new float[m2];
        float A;
        float[] Ae = new float[m2 + 1];
        float[] Ao = new float[m2 + 1];
        float B;
        float[] Be = new float[m2];
        float[] Bo = new float[m2];
        float temp;

        // even/odd roots setup
        for (i = 0; i < m2; i++) {
            O[i] = (float) (-2. * Math.cos(lsp[i * 2]));
            E[i] = (float) (-2. * Math.cos(lsp[i * 2 + 1]));
        }

        // set up impulse response
        for (j = 0; j < m2; j++) {
            Ae[j] = 0.f;
            Ao[j] = 1.f;
            Be[j] = 0.f;
            Bo[j] = 1.f;
        }
        Ao[j] = 1.f;
        Ae[j] = 1.f;

        // run impulse response
        for (i = 1; i < m + 1; i++) {
            A = B = 0.f;
            for (j = 0; j < m2; j++) {
                temp = O[j] * Ao[j] + Ae[j];
                Ae[j] = Ao[j];
                Ao[j] = A;
                A += temp;

                temp = E[j] * Bo[j] + Be[j];
                Be[j] = Bo[j];
                Bo[j] = B;
                B += temp;
            }
            lpc[i - 1] = (A + Ao[j] + B - Ae[j]) / 2;
            Ao[j] = A;
            Ae[j] = B;
        }
    }

    private static void lpc_to_curve(float[] curve, float[] lpc, float amp,
                                     LookFloor0 l, String name, int frameno) {
        // l->m+1 must be less than l->ln, but guard in case we get a bad stream
        float[] lcurve = new float[Math.max(l.ln * 2, l.m * 2 + 2)];

        if (amp == 0) {
            for (int j = 0; j < l.n; j++) curve[j] = 0.0f;
            return;
        }
        l.lpcLook.lpcToCurve(lcurve, lpc, amp);

        for (int i = 0; i < l.n; i++) curve[i] = lcurve[l.linearmap[i]];
    }
}

class InfoFloor0 {

    int order;
    int rate;
    int barkmap;

    int ampbits;
    int ampdB;

    int numbooks; // <= 16
    final int[] books = new int[16];
}

class LookFloor0 {

    int n;
    int ln;
    int m;
    int[] linearmap;

    InfoFloor0 vi;
    final Lpc lpcLook = new Lpc();
}

class EchstateFloor0 {

    int[] codewords;
    float[] curve;
    long frameno;
    long codes;
}
