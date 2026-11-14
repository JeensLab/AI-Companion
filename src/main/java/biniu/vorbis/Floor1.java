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
class Floor1 extends FuncFloor {

    private static final Logger logger = System.getLogger(Floor1.class.getName());

    static final int floor1_rangedb = 140;
    static final int VIF_POSIT = 63;

    public Floor1() {
    }

    @Override
    public void pack(Object i, Buffer opb) {
        InfoFloor1 info = (InfoFloor1) i;

        int count = 0;
        int rangebits;
        int maxposit = info.postlist[1];
        int maxclass = -1;

        // save out partitions
        opb.write(info.partitions, 5);          // only 0 to 31 legal
        for (int j = 0; j < info.partitions; j++) {
            opb.write(info.partitionclass[j], 4); // only 0 to 15 legal
            if (maxclass < info.partitionclass[j])
                maxclass = info.partitionclass[j];
        }

        // save out partition classes
        for (int j = 0; j < maxclass + 1; j++) {
            opb.write(info.class_dim[j] - 1, 3); // 1 to 8
            opb.write(info.class_subs[j], 2); // 0 to 3
            if (info.class_subs[j] != 0) {
                opb.write(info.class_book[j], 8);
            }
            for (int k = 0; k < (1 << info.class_subs[j]); k++) {
                opb.write(info.class_subbook[j][k] + 1, 8);
            }
        }

        // save out the post list
        opb.write(info.mult - 1, 2);     // only 1,2,3,4 legal now
        opb.write(ilog2(maxposit), 4);
        rangebits = ilog2(maxposit);

        for (int j = 0, k = 0; j < info.partitions; j++) {
            count += info.class_dim[info.partitionclass[j]];
            for (; k < count; k++) {
                opb.write(info.postlist[k + 2], rangebits);
            }
        }
    }

    @Override
    Object unpack(Info i, Buffer opb) {
logger.log(Level.WARNING, "not implemented yet");
        return null; // TODO
    }

    @Override
    public Object look(DspState vd, InfoMode mi, Object i) {
        return this.look(vd, i);
    }

    @Override
    public Object look(DspState vd, Object mi) {
        int n = 0;

        int[] sortpointer = new int[VIF_POSIT + 2];

        InfoFloor1 info = (InfoFloor1) mi;
        LookFloor1 look = new LookFloor1();
        look.vi = info;
        look.n = info.postlist[1];

        // we drop each position value in-between already decoded values,
        // and use linear interpolation to predict each new value past the
        // edges.  The positions are read in the order of the position
        // list... we precompute the bounding positions in the lookup.  Of
        // course, the neighbors can change (if a position is declined), but
        // this is an initial mapping

        for (int j = 0; j < info.partitions; j++) {
            n += info.class_dim[info.partitionclass[j]];
        }
        n += 2;
        look.posts = n;

        // also store a sorted position index
        for (int j = 0; j < n; j++) {
            sortpointer[j] = j;
        }

        int foo;
        for (int j = 0; j < n - 1; j++) {
            for (int k = j; k < n; k++) {
                if (info.postlist[sortpointer[j]] > info.postlist[sortpointer[k]]) {
                    foo = sortpointer[k];
                    sortpointer[k] = sortpointer[j];
                    sortpointer[j] = foo;
                }
            }
        }

        // points from sort order back to range number
        for (int j = 0; j < n; j++) {
            look.forwardIndex[j] = sortpointer[j];
        }
        // points from range order to sorted position
        for (int j = 0; j < n; j++) {
            look.reverseIndex[look.forwardIndex[j]] = j;
        }
        // we actually need the post values too
        for (int j = 0; j < n; j++) {
            look.sortedIndex[j] = info.postlist[look.forwardIndex[j]];
        }

        // quantize values to multiplier spec
        switch (info.mult) {
        case 1: // 1024 -> 256
            look.quant_q = 256;
            break;
        case 2: // 1024 -> 128
            look.quant_q = 128;
            break;
        case 3: // 1024 -> 86
            look.quant_q = 86;
            break;
        case 4: // 1024 -> 64
            look.quant_q = 64;
            break;
        default:
            look.quant_q = -1;
        }

        // discover our neighbors for decode where we don't use fit flags
        // (that would push the neighbors outward)
        for (int j = 0; j < n - 2; j++) {
            int lo = 0;
            int hi = 1;
            int lx = 0;
            int hx = look.n;
            int currentx = info.postlist[j + 2];
            for (int k = 0; k < j + 2; k++) {
                int x = info.postlist[k];
                if (x > lx && x < currentx) {
                    lo = k;
                    lx = x;
                }
                if (x < hx && x > currentx) {
                    hi = k;
                    hx = x;
                }
            }
            look.loneighBor[j] = lo;
            look.hineighBor[j] = hi;
        }

        return look;
    }

    @Override
    void clear(Object obj) {
        obj = null;
    }

    @Override
    int forward(Block vb, Object i, float[] in, float[] out, Object vs) {
        return 0;
    }

    @Override
    public Object inverse1(Block vb, Object ii, Object memo) {
        LookFloor1 look = (LookFloor1) ii;
        InfoFloor1 info = look.vi;
        CodeBook[] books = vb.dspState.fullbooks;

        // unpack wrapped/predicted values from stream
        if (vb.opb.read(1) == 1) {
            int[] fit_value = null;
            if (memo instanceof int[]) {
                fit_value = (int[]) memo;
            }
            if (fit_value == null || fit_value.length < look.posts) {
                fit_value = new int[look.posts];
            } else {
                Arrays.fill(fit_value, 0);
            }

            fit_value[0] = vb.opb.read(ilog(look.quant_q - 1));
            fit_value[1] = vb.opb.read(ilog(look.quant_q - 1));

            // partition by partition
            for (int i = 0, j = 2; i < info.partitions; i++) {
                int clss = info.partitionclass[i];
                int cdim = info.class_dim[clss];
                int csubbits = info.class_subs[clss];
                int csub = 1 << csubbits;
                int cval = 0;

                // decode the partition's first stage cascade value
                if (csubbits != 0) {
                    cval = books[info.class_book[clss]].decode(vb.opb);

                    if (cval == -1) {
                        return null;
                    }
                }

                for (int k = 0; k < cdim; k++) {
                    int book = info.class_subbook[clss][cval & (csub - 1)];
                    cval >>>= csubbits;
                    if (book >= 0) {
                        if ((fit_value[j + k] = books[book].decode(vb.opb)) == -1) {
                            return null;
                        }
                    } else {
                        fit_value[j + k] = 0;
                    }
                }
                j += cdim;
            }

            // unwrap positive values and reconsitute via linear interpolation
            for (int i = 2; i < look.posts; i++) {
                int predicted = renderPoint(info.postlist[look.loneighBor[i - 2]],
                        info.postlist[look.hineighBor[i - 2]],
                        fit_value[look.loneighBor[i - 2]],
                        fit_value[look.hineighBor[i - 2]],
                        info.postlist[i]);
                int hiroom = look.quant_q - predicted;
                int loroom = predicted;
                int room = (hiroom < loroom ? hiroom : loroom) << 1;
                int val = fit_value[i];

                if (val != 0) {
                    if (val >= room) {
                        if (hiroom > loroom) {
                            val = val - loroom;
                        } else {
                            val = -1 - (val - hiroom);
                        }
                    } else {
                        if ((val & 1) != 0) {
                            val = -((val + 1) >>> 1);
                        } else {
                            val >>= 1;
                        }
                    }

                    fit_value[i] = val + predicted;
                    fit_value[look.loneighBor[i - 2]] &= 0x7fff;
                    fit_value[look.hineighBor[i - 2]] &= 0x7fff;
                } else {
                    fit_value[i] = predicted | 0x8000;
                }
            }
            return fit_value;
        }

        return null;
    }

    private static int renderPoint(int x0, int x1, int y0, int y1, int x) {
        y0 &= 0x7fff; // mask off flag
        y1 &= 0x7fff;

        {
            int dy = y1 - y0;
            int adx = x1 - x0;
            int ady = Math.abs(dy);
            int err = ady * (x - x0);

            int off = err / adx;
            if (dy < 0) return (y0 - off);
            return (y0 + off);
        }
    }

    @Override
    public int inverse2(Block vb, Object i, Object memo, float[] out) {
        LookFloor1 look = (LookFloor1) i;
        InfoFloor1 info = look.vi;
        int n = vb.dspState.vi.blocksizes[vb.mode] / 2;

        if (memo != null) {
            // render the lines
            int[] fit_value = (int[]) memo;
            int hx = 0;
            int lx = 0;
            int ly = fit_value[0] * info.mult;
            for (int j = 1; j < look.posts; j++) {
                int current = look.forwardIndex[j];
                int hy = fit_value[current] & 0x7fff;
                if (hy == fit_value[current]) {
                    hy *= info.mult;
                    hx = info.postlist[current];

                    renderLine(lx, hx, ly, hy, out);

                    lx = hx;
                    ly = hy;
                }
            }
            for (int j = hx; j < n; j++) {
                out[j] *= out[j - 1]; // be certain
            }
            return 1;
        }
        for (int j = 0; j < n; j++) {
            out[j] = 0.f;
        }
        return 0;
    }

    private static final float[] FLOOR_fromdB_LOOKUP = {
            1.0649863e-07F, 1.1341951e-07F, 1.2079015e-07F, 1.2863978e-07F,
            1.3699951e-07F, 1.4590251e-07F, 1.5538408e-07F, 1.6548181e-07F,
            1.7623575e-07F, 1.8768855e-07F, 1.9988561e-07F, 2.128753e-07F,
            2.2670913e-07F, 2.4144197e-07F, 2.5713223e-07F, 2.7384213e-07F,
            2.9163793e-07F, 3.1059021e-07F, 3.3077411e-07F, 3.5226968e-07F,
            3.7516214e-07F, 3.9954229e-07F, 4.2550680e-07F, 4.5315863e-07F,
            4.8260743e-07F, 5.1396998e-07F, 5.4737065e-07F, 5.8294187e-07F,
            6.2082472e-07F, 6.6116941e-07F, 7.0413592e-07F, 7.4989464e-07F,
            7.9862701e-07F, 8.5052630e-07F, 9.0579828e-07F, 9.6466216e-07F,
            1.0273513e-06F, 1.0941144e-06F, 1.1652161e-06F, 1.2409384e-06F,
            1.3215816e-06F, 1.4074654e-06F, 1.4989305e-06F, 1.5963394e-06F,
            1.7000785e-06F, 1.8105592e-06F, 1.9282195e-06F, 2.0535261e-06F,
            2.1869758e-06F, 2.3290978e-06F, 2.4804557e-06F, 2.6416497e-06F,
            2.8133190e-06F, 2.9961443e-06F, 3.1908506e-06F, 3.3982101e-06F,
            3.6190449e-06F, 3.8542308e-06F, 4.1047004e-06F, 4.3714470e-06F,
            4.6555282e-06F, 4.9580707e-06F, 5.2802740e-06F, 5.6234160e-06F,
            5.9888572e-06F, 6.3780469e-06F, 6.7925283e-06F, 7.2339451e-06F,
            7.7040476e-06F, 8.2047000e-06F, 8.7378876e-06F, 9.3057248e-06F,
            9.9104632e-06F, 1.0554501e-05F, 1.1240392e-05F, 1.1970856e-05F,
            1.2748789e-05F, 1.3577278e-05F, 1.4459606e-05F, 1.5399272e-05F,
            1.6400004e-05F, 1.7465768e-05F, 1.8600792e-05F, 1.9809576e-05F,
            2.1096914e-05F, 2.2467911e-05F, 2.3928002e-05F, 2.5482978e-05F,
            2.7139006e-05F, 2.8902651e-05F, 3.0780908e-05F, 3.2781225e-05F,
            3.4911534e-05F, 3.7180282e-05F, 3.9596466e-05F, 4.2169667e-05F,
            4.4910090e-05F, 4.7828601e-05F, 5.0936773e-05F, 5.4246931e-05F,
            5.7772202e-05F, 6.1526565e-05F, 6.5524908e-05F, 6.9783085e-05F,
            7.4317983e-05F, 7.9147585e-05F, 8.4291040e-05F, 8.9768747e-05F,
            9.5602426e-05F, 0.00010181521F, 0.00010843174F, 0.00011547824F,
            0.00012298267F, 0.00013097477F, 0.00013948625F, 0.00014855085F,
            0.00015820453F, 0.00016848555F, 0.00017943469F, 0.00019109536F,
            0.00020351382F, 0.00021673929F, 0.00023082423F, 0.00024582449F,
            0.00026179955F, 0.00027881276F, 0.00029693158F, 0.00031622787F,
            0.00033677814F, 0.00035866388F, 0.00038197188F, 0.00040679456F,
            0.00043323036F, 0.00046138411F, 0.00049136745F, 0.00052329927F,
            0.00055730621F, 0.00059352311F, 0.00063209358F, 0.00067317058F,
            0.00071691700F, 0.00076350630F, 0.00081312324F, 0.00086596457F,
            0.00092223983F, 0.00098217216F, 0.0010459992F, 0.0011139742F,
            0.0011863665F, 0.0012634633F, 0.0013455702F, 0.0014330129F,
            0.0015261382F, 0.0016253153F, 0.0017309374F, 0.0018434235F,
            0.0019632195F, 0.0020908006F, 0.0022266726F, 0.0023713743F,
            0.0025254795F, 0.0026895994F, 0.0028643847F, 0.0030505286F,
            0.0032487691F, 0.0034598925F, 0.0036847358F, 0.0039241906F,
            0.0041792066F, 0.0044507950F, 0.0047400328F, 0.0050480668F,
            0.0053761186F, 0.0057254891F, 0.0060975636F, 0.0064938176F,
            0.0069158225F, 0.0073652516F, 0.0078438871F, 0.0083536271F,
            0.0088964928F, 0.009474637F, 0.010090352F, 0.010746080F,
            0.011444421F, 0.012188144F, 0.012980198F, 0.013823725F,
            0.014722068F, 0.015678791F, 0.016697687F, 0.017782797F,
            0.018938423F, 0.020169149F, 0.021479854F, 0.022875735F,
            0.024362330F, 0.025945531F, 0.027631618F, 0.029427276F,
            0.031339626F, 0.033376252F, 0.035545228F, 0.037855157F,
            0.040315199F, 0.042935108F, 0.045725273F, 0.048696758F,
            0.051861348F, 0.055231591F, 0.058820850F, 0.062643361F,
            0.066714279F, 0.071049749F, 0.075666962F, 0.080584227F,
            0.085821044F, 0.091398179F, 0.097337747F, 0.10366330F,
            0.11039993F, 0.11757434F, 0.12521498F, 0.13335215F,
            0.14201813F, 0.15124727F, 0.16107617F, 0.17154380F,
            0.18269168F, 0.19456402F, 0.20720788F, 0.22067342F,
            0.23501402F, 0.25028656F, 0.26655159F, 0.28387361F,
            0.30232132F, 0.32196786F, 0.34289114F, 0.36517414F,
            0.38890521F, 0.41417847F, 0.44109412F, 0.46975890F,
            0.50028648F, 0.53279791F, 0.56742212F, 0.60429640F,
            0.64356699F, 0.68538959F, 0.72993007F, 0.77736504F,
            0.82788260F, 0.88168307F, 0.9389798F, 1.F
    };

    private static void renderLine(int x0, int x1, int y0, int y1, float[] d) {
        int dy = y1 - y0;
        int adx = x1 - x0;
        int ady = Math.abs(dy);
        int base = dy / adx;
        int sy = (dy < 0 ? base - 1 : base + 1);
        int x = x0;
        int y = y0;
        int err = 0;

        ady -= Math.abs(base * adx);

        d[x] *= FLOOR_fromdB_LOOKUP[y];
        while (++x < x1) {
            err = err + ady;
            if (err >= adx) {
                err -= adx;
                y += sy;
            } else {
                y += base;
            }
            d[x] *= FLOOR_fromdB_LOOKUP[y];
        }
    }

    private static int inspectError(int x0, int x1, int y0, int y1, float[] mask,
                                    float[] mdct, int pmdct,
                                    InfoFloor1 info) {
        int dy = y1 - y0;
        int adx = x1 - x0;
        int ady = Math.abs(dy);
        int base = dy / adx;
        int sy = (dy < 0 ? base - 1 : base + 1);
        int x = x0;
        int y = y0;
        int err = 0;
        int val = dBquant(mask, x);
        int mse = 0;
        int n = 0;

        ady -= Math.abs(base * adx);

        mse = (y - val);
        mse *= mse;
        n++;
        if (mdct[x + pmdct] + info.twofitatten >= mask[x]) {
            if (y + info.maxover < val) return 1;
            if (y - info.maxunder > val) return 1;
        }

        while (++x < x1) {
            err = err + ady;
            if (err >= adx) {
                err -= adx;
                y += sy;
            } else {
                y += base;
            }

            val = dBquant(mask, x);
            mse += ((y - val) * (y - val));
            n++;
            if (mdct[x + pmdct] + info.twofitatten >= mask[x]) {
                if (val != 0) {
                    if (y + info.maxover < val) return 1;
                    if (y - info.maxunder > val) return 1;
                }
            }
        }

        if (info.maxover * info.maxover / n > info.maxerr) return 0;
        if (info.maxunder * info.maxunder / n > info.maxerr) return 0;
        if ((float) mse / n > info.maxerr) return 1;
        return 0;
    }

    static int dBquant(float[] x, int px) {
        int i = (int) (x[px] * 7.3142857f + 1023.5f);
        if (i > 1023) return 1023;
        if (i < 0) return 0;
        return i;
    }

    // the floor has already been filtered to only include relevant sections
    private static int accumulateFit(float[] flr, float[] mdct, int pmdct,
                                     int x0, int x1, LsfitAcc a,
                                     int n, InfoFloor1 info) {
        int i;
        int quantized = dBquant(flr, x0);

        int xa = 0, ya = 0, x2a = 0, y2a = 0, xya = 0, na = 0, xb = 0, yb = 0, x2b = 0, y2b = 0, xyb = 0, nb = 0;

        a.x0 = x0;
        a.x1 = x1;
        if (x1 >= n) x1 = n - 1;

        for (i = x0; i <= x1; i++) {
            quantized = dBquant(flr, i);
            if (quantized != 0) {
                if (mdct[i + pmdct] + info.twofitatten >= flr[i]) {
                    xa += i;
                    ya += quantized;
                    x2a += i * i;
                    y2a += quantized * quantized;
                    xya += i * quantized;
                    na++;
                } else {
                    xb += i;
                    yb += quantized;
                    x2b += i * i;
                    y2b += quantized * quantized;
                    xyb += i * quantized;
                    nb++;
                }
            }
        }

        xb += xa;
        yb += ya;
        x2b += x2a;
        y2b += y2a;
        xyb += xya;
        nb += na;

        // weight toward the actually used frequencies if we meet the threshhold
        {
            int weight = (int) (nb * info.twofitweight / (na + 1));

            a.xa = xa * weight + xb;
            a.ya = ya * weight + yb;
            a.x2a = x2a * weight + x2b;
            a.y2a = y2a * weight + y2b;
            a.xya = xya * weight + xyb;
            a.an = na * weight + nb;
        }

        return na;
    }

    private static void fitLine(LsfitAcc[] ac, int pfits, int fits, para yy) {
        int x = 0, y = 0, x2 = 0, y2 = 0, xy = 0, an = 0, i;
        int x0 = ac[pfits].x0;
        int x1 = ac[fits - 1 + pfits].x1;

        for (i = 0; i < fits; i++) {
            x += ac[i + pfits].xa;
            y += ac[i + pfits].ya;
            x2 += ac[i + pfits].x2a;
            y2 += ac[i + pfits].y2a;
            xy += ac[i + pfits].xya;
            an += ac[i + pfits].an;
        }

        if (yy.y0 >= 0) {
            x += x0;
            y += yy.y0;
            x2 += x0 * x0;
            y2 += yy.y0 * yy.y0;
            xy += yy.y0 * x0;
            an++;
        }

        if (yy.y1 >= 0) {
            x += x1;
            y += yy.y1;
            x2 += x1 * x1;
            y2 += yy.y1 * yy.y1;
            xy += yy.y1 * x1;
            an++;
        }

        if (an != 0) {
            // need 64 bit multiplies, which C doesn't give portably as int
            double fx = x;
            double fy = y;
            double fx2 = x2;
            double fxy = xy;
            double denom = 1. / (an * fx2 - fx * fx);
            double a = (fy * fx2 - fxy * fx) * denom;
            double b = (an * fxy - fx * fy) * denom;
            yy.y0 = (int) Math.rint(a + b * x0);
            yy.y1 = (int) Math.rint(a + b * x1);

            // limit to our range!
            if (yy.y0 > 1023) yy.y0 = 1023;
            if (yy.y1 > 1023) yy.y1 = 1023;
            if (yy.y0 < 0) yy.y0 = 0;
            if (yy.y1 < 0) yy.y1 = 0;

        } else {
            yy.y0 = 0;
            yy.y1 = 0;
        }
    }

    static int post_Y(int[] A, int[] B, int pos) {
        if (A[pos] < 0)
            return B[pos];
        if (B[pos] < 0)
            return A[pos];

        return (A[pos] + B[pos]) >> 1;
    }

    boolean TRAIN_FLOOR1 = false;

    private static void renderLine0(int x0, int x1, int y0, int y1, int[] d) {
        int dy = y1 - y0;
        int adx = x1 - x0;
        int ady = Math.abs(dy);
        int base = dy / adx;
        int sy = (dy < 0 ? base - 1 : base + 1);
        int x = x0;
        int y = y0;
        int err = 0;

        ady -= Math.abs(base * adx);

        d[x] = y;
        while (++x < x1) {
            err = err + ady;
            if (err >= adx) {
                err -= adx;
                y += sy;
            } else {
                y += base;
            }
            d[x] = y;
        }
    }

    public static int encode(Block vb, Buffer opb, int inlook, int[] post, int[] ilogmask) {

        int i, j;
        CodecSetupInfo ci = vb.dspState.vi.getCodecSetup();
        StaticCodeBook[] sbooks = ci.bookParam;
        CodeBook[] books = ci.fullBooks;
        PrivateState backEndState = vb.dspState.backEndState;
        LookFloor1 lookFloor1 = (LookFloor1) vb.dspState.backEndState.flr[inlook];
        InfoFloor1 info = lookFloor1.vi;
        int n = lookFloor1.n;
        int posts = lookFloor1.posts;
        int[] out = new int[InfoFloor1.VIF_POSIT + 2];
        int seq = 0;

        // quantize values to multiplier spec
        if (post != null) {
            for (i = 0; i < posts; i++) {
                int val = post[i] & 0x7fff;
                switch (info.mult) {
                case 1: // 1024 -> 256
                    val >>= 2;
                    break;
                case 2: // 1024 -> 128
                    val >>= 3;
                    break;
                case 3: // 1024 -> 86
                    val /= 12;
                    break;
                case 4: // 1024 -> 64
                    val >>= 4;
                    break;
                }
                post[i] = val | (post[i] & 0x8000);
            }

            out[0] = post[0];
            out[1] = post[1];

            // find prediction values for each post and subtract them
            for (i = 2; i < posts; i++) {
                int ln = lookFloor1.loneighBor[i - 2];
                int hn = lookFloor1.hineighBor[i - 2];
                int x0 = info.postlist[ln];
                int x1 = info.postlist[hn];
                int y0 = post[ln];
                int y1 = post[hn];

                int predicted = renderPoint(x0, x1, y0, y1, info.postlist[i]);

                if (((post[i] & 0x8000) != 0) || (predicted == post[i])) {
                    post[i] = predicted | 0x8000; /* in case there was roundoff jitter
                                       in interpolation */
                    out[i] = 0;
                } else {
                    int headroom = (lookFloor1.quant_q - predicted < predicted ?
                            lookFloor1.quant_q - predicted : predicted);

                    int val = post[i] - predicted;

          /* at this point the 'deviation' value is in the range +/- max
             range, but the real, unique range can always be mapped to
             only [0-maxrange).  So we want to wrap the deviation into
             this limited range, but do it in the way that least screws
             an essentially gaussian probability distribution. */

                    if (val < 0)
                        if (val < -headroom)
                            val = headroom - val - 1;
                        else
                            val = -1 - (val << 1);
                    else if (val >= headroom)
                        val = val + headroom;
                    else
                        val <<= 1;

                    out[i] = val;
                    post[ln] &= 0x7fff;
                    post[hn] &= 0x7fff;
                }
            }

            // we have everything we need. pack it out
            // mark nontrivial floor
            opb.write(1, 1);

            // beginning/end post
            lookFloor1.frames++;
            lookFloor1.postbits += ilog(lookFloor1.quant_q - 1) * 2;
            opb.write(out[0], ilog(lookFloor1.quant_q - 1));
            opb.write(out[1], ilog(lookFloor1.quant_q - 1));

            // partition by partition
            for (i = 0, j = 2; i < info.partitions; i++) {
                int clas = info.partitionclass[i];
                int cdim = info.class_dim[clas];
                int csubbits = info.class_subs[clas];
                int csub = 1 << csubbits;
                int[] bookas = {0, 0, 0, 0, 0, 0, 0, 0};
                int cval = 0;
                int cshift = 0;
                int k, l;

                // generate the partition's first stage cascade value
                if (csubbits != 0) {
                    int[] maxval = new int[8];
                    for (k = 0; k < csub; k++) {
                        int booknum = info.class_subbook[clas][k];
                        if (booknum < 0) {
                            maxval[k] = 1;
                        } else {
                            maxval[k] = sbooks[info.class_subbook[clas][k]].entries;
                        }
                    }
                    for (k = 0; k < cdim; k++) {
                        for (l = 0; l < csub; l++) {
                            int val = out[j + k];
                            if (val < maxval[l]) {
                                bookas[k] = l;
                                break;
                            }
                        }
                        cval |= bookas[k] << cshift;
                        cshift += csubbits;
                    }
                    // write it
                    lookFloor1.phrasebits += books[info.class_book[clas]].bookEncode(cval, opb);

                }

                // write post values
                for (k = 0; k < cdim; k++) {
                    int book = info.class_subbook[clas][bookas[k]];
                    if (book >= 0) {
                        // hack to allow training with 'bad' books
                        if (out[j + k] < (books[book]).entries)
                            lookFloor1.postbits += books[book].bookEncode(out[j + k], opb);
                    }
                }
                j += cdim;
            }

            {
                // generate quantized floor equivalent to what we'd unpack in decode
                // render the lines
                int hx = 0;
                int lx = 0;
                int ly = post[0] * info.mult;
                for (j = 1; j < lookFloor1.posts; j++) {
                    int current = lookFloor1.forwardIndex[j];
                    int hy = post[current] & 0x7fff;
                    if (hy == post[current]) {

                        hy *= info.mult;
                        hx = info.postlist[current];

                        renderLine0(lx, hx, ly, hy, ilogmask);

                        lx = hx;
                        ly = hy;
                    }
                }
                for (j = hx; j < vb.pcmEnd / 2; j++) ilogmask[j] = ly; // be certain
                seq++;
                return 1;
            }
        } else {
            opb.write(0, 1);
            ilogmask = new int[vb.pcmEnd / 2];
            seq++;
            return 0;
        }
    }

    private static int[] val200n = {-200};
    private static int[] val1 = {1};
    private static int[] val1n = {-1};

    public static int[] fit(Block vb, int inlook,
                            float[] logmdct,   // in
                            int plogmdct,
                            float[] logmask) {
        int i, j;
        int y0, y1;
        LookFloor1 _look = (LookFloor1) vb.dspState.backEndState.flr[inlook];
        InfoFloor1 info = _look.vi;
        int n = _look.n;
        int posts = _look.posts;
        int nonzero = 0;
        LsfitAcc[] fits = new LsfitAcc[InfoFloor1.VIF_POSIT + 1];
        int[] fit_valueA = new int[InfoFloor1.VIF_POSIT + 2]; // index by range list position
        int[] fit_valueB = new int[InfoFloor1.VIF_POSIT + 2]; // index by range list position

        int[] loneighbor = new int[InfoFloor1.VIF_POSIT + 2]; // sorted index of range list position (+2)
        int[] hineighbor = new int[InfoFloor1.VIF_POSIT + 2];
        int[] output = null;
        int[] memo = new int[InfoFloor1.VIF_POSIT + 2];

        if (val1.length < posts) {
            val200n = new int[posts];
            val1 = new int[posts];
            val1n = new int[posts];
            for (i = 0; i < posts; i++) {
                val200n[i] = -200;
                val1[i] = 1;
                val1n[i] = -1;
            }
        }
        System.arraycopy(val200n, 0, fit_valueA, 0, posts);
        System.arraycopy(val200n, 0, fit_valueB, 0, posts);
        System.arraycopy(val1, 0, hineighbor, 0, posts);
        System.arraycopy(val1n, 0, memo, 0, posts);

        // quantize the relevant floor points and collect them into line fit
        // structures (one per minimal division) at the same time
        if (posts == 0) {
            fits[0] = new LsfitAcc();
            nonzero += accumulateFit(logmask, logmdct, plogmdct, 0, n, fits[0], n, info);
        } else {
            for (i = 0; i < posts - 1; i++) {
                fits[i] = new LsfitAcc();
                nonzero += accumulateFit(logmask, logmdct, plogmdct, _look.sortedIndex[i],
                        _look.sortedIndex[i + 1], fits[i], n, info);
            }
        }

        if (nonzero != 0) {
            // start by fitting the implicit base case....
            y0 = -200;
            y1 = -200;
            para yy = new para(y0, y1);
            fitLine(fits, 0, posts - 1, yy);

            fit_valueA[0] = yy.y0;
            fit_valueB[0] = yy.y0;
            fit_valueB[1] = yy.y1;
            fit_valueA[1] = yy.y1;

            // Non degenerate case
            // start progressive splitting.  This is a greedy, non-optimal
            // algorithm, but simple and close enough to the best
            // answer.
            for (i = 2; i < posts; i++) {
                int sortpos = _look.reverseIndex[i];
                int ln = loneighbor[sortpos];
                int hn = hineighbor[sortpos];

                // eliminate repeat searches of a particular range with a memo
                if (memo[ln] != hn) {
                    // haven't performed this error search yet
                    int lsortpos = _look.reverseIndex[ln];
                    int hsortpos = _look.reverseIndex[hn];
                    memo[ln] = hn;

                    {
                        // A note: we want to bound/minimize *local*, not global, error
                        int lx = info.postlist[ln];
                        int hx = info.postlist[hn];
                        int ly = post_Y(fit_valueA, fit_valueB, ln);
                        int hy = post_Y(fit_valueA, fit_valueB, hn);

                        if (ly == -1 || hy == -1) {
                            System.exit(1);
                        }

                        if (inspectError(lx, hx, ly, hy, logmask, logmdct, plogmdct, info) != 0) {
                            // outside error bounds/begin search area.  Split it.
                            int ly0 = -200;
                            int ly1 = -200;
                            int hy0 = -200;
                            int hy1 = -200;
                            para lyy = new para(ly0, ly1);
                            fitLine(fits, lsortpos, sortpos - lsortpos, lyy);
                            para hyy = new para(hy0, hy1);
                            fitLine(fits, sortpos, hsortpos - sortpos, hyy);

                            // store new edge values
                            fit_valueB[ln] = lyy.y0;
                            if (ln == 0) fit_valueA[ln] = lyy.y0;
                            fit_valueA[i] = lyy.y1;
                            fit_valueB[i] = hyy.y0;
                            fit_valueA[hn] = hyy.y1;
                            if (hn == 1) fit_valueB[hn] = hyy.y1;

                            if (lyy.y1 >= 0 || hyy.y0 >= 0) {
                                // store new neighbor values
                                for (j = sortpos - 1; j >= 0; j--)
                                    if (hineighbor[j] == hn)
                                        hineighbor[j] = i;
                                    else
                                        break;
                                for (j = sortpos + 1; j < posts; j++)
                                    if (loneighbor[j] == ln)
                                        loneighbor[j] = i;
                                    else
                                        break;

                            }
                        } else {

                            fit_valueA[i] = -200;
                            fit_valueB[i] = -200;
                        }
                    }
                }
            }

            output = new int[posts];

            output[0] = post_Y(fit_valueA, fit_valueB, 0);
            output[1] = post_Y(fit_valueA, fit_valueB, 1);

            // fill in posts marked as not using a fit; we will zero
            // back out to 'unused' when encoding them so long as curve
            // interpolation doesn't force them into use
            for (i = 2; i < posts; i++) {
                int ln = _look.loneighBor[i - 2];
                int hn = _look.hineighBor[i - 2];
                int x0 = info.postlist[ln];
                int x1 = info.postlist[hn];
                y0 = output[ln];
                y1 = output[hn];

                int predicted = renderPoint(x0, x1, y0, y1, info.postlist[i]);
                int vx = post_Y(fit_valueA, fit_valueB, i);

                if (vx >= 0 && predicted != vx) {
                    output[i] = vx;
                } else {
                    output[i] = predicted | 0x8000;
                }
            }
        }

        return output;

    }

    public static int[] interpolate(Block vb, int inlook,
                                    int[] A, int[] B,
                                    int del) {

        int i;
        LookFloor1 _look = (LookFloor1) vb.dspState.backEndState.flr[inlook];
        int posts = _look.posts;
        int[] output = null;

        if (A != null && B != null) {
            output = new int[posts];

            for (i = 0; i < posts; i++) {
                output[i] = ((65536 - del) * (A[i] & 0x7fff) + del * (B[i] & 0x7fff) + 32768) >> 16;
                if (((A[i] & 0x8000) != 0) && ((B[i] & 0x8000) != 0)) output[i] |= 0x8000;
            }
        }

        return output;
    }

    private static int ilog(int v) {
        int ret = 0;
        while (v != 0) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    private static int ilog2(int v) {
        int ret = 0;
        while (v > 1) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }
}

class Lsfit_acc {

    long x0;
    long x1;

    long xa;
    long ya;
    long x2a;
    long y2a;
    long xya;
    long n;
    long an;
    long un;
    long edgey0;
    long edgey1;
}

class EchstateFloor1 {

    int[] codewords;
    float[] curve;
    long frameNo;
    long codes;
}

class LookFloor1 {

    public int[] sortedIndex = new int[InfoFloor1.VIF_POSIT + 2];
    public int[] forwardIndex = new int[InfoFloor1.VIF_POSIT + 2];
    public int[] reverseIndex = new int[InfoFloor1.VIF_POSIT + 2];
    public int[] hineighBor = new int[InfoFloor1.VIF_POSIT];
    public int[] loneighBor = new int[InfoFloor1.VIF_POSIT];
    int posts;

    int n;
    int quant_q;
    InfoFloor1 vi;

    int phrasebits;
    int postbits;
    int frames;

    void free() {
        sortedIndex = null;
        forwardIndex = null;
        reverseIndex = null;
        hineighBor = null;
        loneighBor = null;
    }
}

class LsfitAcc {

    int x0;
    int x1;

    int xa;
    int ya;
    int x2a;
    int y2a;
    int xya;
    int an;
}

class para {

    int y0;
    int y1;

    para(int y0, int y1) {
        this.y0 = y0;
        this.y1 = y1;
    }

    int getY0() {
        return this.y0;
    }

    int getY1() {
        return this.y1;
    }
}
