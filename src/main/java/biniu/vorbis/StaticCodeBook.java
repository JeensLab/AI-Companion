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

import biniu.ogg.Buffer;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class StaticCodeBook {

    /** codebook dimensions (elements per vector) */
    int dim;
    /** codebook entries */
    int entries;
    /** codeword lengths in bits */
    int[] lengthlist;

    // mapping

    /**
     * 0=none
     * 1=implicitly populated values from map column
     * 2=listed arbitrary values
     */
    int maptype;

    // The below does a linear, single monotonic sequence mapping.

    /** packed 32 bit float; quant value 0 maps to minval */
    int q_min;
    /** packed 32 bit float; val 1 - val 0 == delta */
    int q_delta;
    /** bits: 0 < quant <= 16 */
    int q_quant;
    /** bitflag */
    int q_sequencep;

    // additional information for log (dB) mapping; the linear mapping
    // is assumed to actually be values in dB.  encodebias is used to
    // assign an error weight to 0 dB. We have two additional flags:
    // zeroflag indicates if entry zero is to represent -Inf dB; negflag
    // indicates if we're to represent negative linear values in a
    // mirror of the positive mapping.

    // map == 1: (int)(entries/dim) element column map
    // map == 2: list of dim*entries quantized entry vals
    int[] quantlist;

    // encode helpers

    EncodeAuxNearestMatch nearest_tree;
    EncodeAuxThreshMatch thresh_tree;
    EncodeAuxPigeonHole pigeon_tree;

    int allocedp;

    public StaticCodeBook() {
    }

    /**
     * @param nearest_tree Object nearest_tree
     * @param thresh_tree  Object thresh_tree
     * @param pigeon_tree  Object pigeon_tree
     */
    public StaticCodeBook(int dim, int entries, int[] lengthlist,
                          int maptype, int q_min, int q_delta,
                          int q_quant, int q_sequencep, int[] quantlist,
                          EncodeAuxNearestMatch nearest_tree,
                          EncodeAuxThreshMatch thresh_tree,
                          EncodeAuxPigeonHole pigeon_tree,
                          int allocedp
    ) {
        this();
        this.dim = dim;
        this.entries = entries;
        this.lengthlist = lengthlist;
        this.maptype = maptype;
        this.q_min = q_min;
        this.q_delta = q_delta;
        this.q_quant = q_quant;
        this.q_sequencep = q_sequencep;
        this.quantlist = quantlist;
        this.nearest_tree = nearest_tree;
        this.thresh_tree = thresh_tree;
        this.pigeon_tree = pigeon_tree;
        this.allocedp = allocedp;
    }

    public int pack(Buffer opb) {
        int i;
        boolean ordered = false;

        opb.write(0x564342, 24);
        opb.write(dim, 16);
        opb.write(entries, 24);

        // pack the codewords.  There are two packings; length ordered and
        // length random.  Decide between the two now.

        for (i = 1; i < entries; i++) {
            if (lengthlist[i] < lengthlist[i - 1]) break;
        }
        if (i == entries) ordered = true;

        if (ordered) {
            // length ordered.  We only need to say how many codewords of
            // each length.  The actual codewords are generated
            // deterministically

            int count = 0;
            opb.write(1, 1);                 // ordered
            opb.write(lengthlist[0] - 1, 5); // 1 to 32

            for (i = 1; i < entries; i++) {
                int _this = lengthlist[i];
                int _last = lengthlist[i - 1];
                if (_this > _last) {
                    for (int j = _last; j < _this; j++) {
                        opb.write(i - count, ilog(entries - count));
                        count = i;
                    }
                }
            }
            opb.write(i - count, ilog(entries - count));
        } else {
            // length random.  Again, we don't code the codeword itself, just
            // the length.  This time, though, we have to encode each length
            opb.write(0, 1); // unordered

            // algortihmic mapping has use for 'unused entries', which we tag
            // here.  The algorithmic mapping happens as usual, but the unused
            // entry has no codeword.
            for (i = 0; i < entries; i++) {
                if (lengthlist[i] == 0) break;
            }

            if (i == entries) {
                opb.write(0, 1); // no unused entries
                for (i = 0; i < entries; i++) {
                    opb.write(lengthlist[i] - 1, 5);
                }
            } else {
                opb.write(1, 1); // we have unused entries; thus we tag
                for (i = 0; i < entries; i++) {
                    if (lengthlist[i] == 0) {
                        opb.write(0, 1);
                    } else {
                        opb.write(1, 1);
                        opb.write(lengthlist[i] - 1, 5);
                    }
                }
            }
        }

        // is the entry number the desired return value, or do we have a
        // mapping? If we have a mapping, what type?
        opb.write(maptype, 4);
        switch (maptype) {
        case 0:
            // no mapping
            break;
        case 1:
        case 2:
            // implicitly populated value mapping
            // explicitly populated value mapping
            if (quantlist == null) {
                // no quantlist?  error
                return -1;
            }

            // values that define the dequantization
            opb.write(q_min, 32);
            opb.write(q_delta, 32);
            opb.write(q_quant - 1, 4);
            opb.write(q_sequencep, 1);

        {
            int quantvals = switch (maptype) {
                case 1 ->
                    // a single column of (c->entries/c->dim) quantized values for
                    // building a full value list algorithmically (square lattice)
                        maptype1_quantvals();
                case 2 ->
                    // every value (c->entries*c->dim total) specified explicitly
                        entries * dim;
                default -> 0;
            };

            // quantized values
            for (i = 0; i < quantvals; i++) {
                opb.write(Math.abs(quantlist[i]), q_quant);
            }
        }
        break;
        default:
            // error case; we don't have any other map types now
            return -1;
        }
        return 0;
    }

    /**
     * unpacks a codebook from the packet buffer into the codebook struct,
     * readies the codebook auxiliary structures for decode
     */
    public int unpack(Buffer opb) {
        int i;

        // make sure alignment is correct
        if (opb.read(24) != 0x564342) {
            clear();
            return -1;
        }
        // first the basic parameters
        dim = opb.read(16);
        entries = opb.read(24);
        if (entries == -1) {
            clear();
            return -1;
        }

        // codeword ordering.... length ordered or unordered?
        switch (opb.read(1)) {
        case 0:
            // unordered
            lengthlist = new int[entries];

            // allocated but unused entries?
            if (opb.read(1) != 0) {
                // yes, unused entries

                for (i = 0; i < entries; i++) {
                    if (opb.read(1) != 0) {
                        int num = opb.read(5);
                        if (num == -1) {
                            clear();
                            return -1;
                        }
                        lengthlist[i] = num + 1;
                    } else {
                        lengthlist[i] = 0;
                    }
                }
            } else {
                // all entries used; no tagging
                for (i = 0; i < entries; i++) {
                    int num = opb.read(5);
                    if (num == -1) {
                        clear();
                        return -1;
                    }
                    lengthlist[i] = num + 1;
                }
            }
            break;
        case 1:
            // ordered
        {
            int length = opb.read(5) + 1;
            lengthlist = new int[entries];

            for (i = 0; i < entries; ) {
                int num = opb.read(ilog(entries - i));
                if (num == -1) {
                    clear();
                    return -1;
                }
                for (int j = 0; j < num; j++, i++) {
                    lengthlist[i] = length;
                }
                length++;
            }
        }
        break;
        default:
            // EOF
            return -1;
        }

        // Do we have a mapping to unpack?
        switch ((maptype = opb.read(4))) {
        case 0:
            // no mapping
            break;
        case 1:
        case 2:
            // implicitly populated value mapping
            // explicitly populated value mapping
            q_min = opb.read(32);
            q_delta = opb.read(32);
            q_quant = opb.read(4) + 1;
            q_sequencep = opb.read(1);

        {
            int quantvals = switch (maptype) {
                case 1 -> maptype1_quantvals();
                case 2 -> entries * dim;
                default -> 0;
            };
            // quantized values
            quantlist = new int[quantvals];
            for (i = 0; i < quantvals; i++) {
                quantlist[i] = opb.read(q_quant);
            }
            if (quantlist[quantvals - 1] == -1) {
                clear();
                return -1;
            }
        }
        break;
        default:
            clear();
            return -1;
        }
        // all set
        return 0;
    }

    /**
     * there might be a straightforward one-line way to do the below
     * that's portable and totally safe against roundoff, but I haven't
     * thought of it.  Therefore, we opt on the side of caution
     */
    private int maptype1_quantvals() {
        int vals = (int) (Math.floor(Math.pow(entries, 1. / dim)));

        // the above *should* be reliable, but we'll not assume that FP is
        // ever reliable when bitstream sync is at stake; verify via integer
        // means that vals really is the greatest value of dim for which
        // vals^b->bim <= b->entries
        // treat the above as an initial guess
        while (true) {
            int acc = 1;
            int acc1 = 1;
            for (int i = 0; i < dim; i++) {
                acc *= vals;
                acc1 *= vals + 1;
            }
            if (acc <= entries && acc1 > entries) {
                return vals;
            } else {
                if (acc > entries) {
                    vals--;
                } else {
                    vals++;
                }
            }
        }
    }

    void clear() {
//        if (quantlist != null) free(b -> quantlist);
//        if (lengthlist != null) free(b -> lengthlist);
//        if (nearest_tree != null) {
//            free(b -> nearest_tree -> ptr0);
//            free(b -> nearest_tree -> ptr1);
//            free(b -> nearest_tree -> p);
//            free(b -> nearest_tree -> q);
//            memset(b -> nearest_tree, 0, sizeof(encode_aux_nearestmatch));
//            free(b -> nearest_tree);
//        }
//        if (thresh_tree != null) {
//            free(b -> thresh_tree -> quantthresh);
//            free(b -> thresh_tree -> quantmap);
//            memset(b -> thresh_tree, 0, sizeof(encode_aux_threshmatch));
//            free(b -> thresh_tree);
//        }
//        memset(b, 0, sizeof(static_codebook));
    }

    private static int ilog(int v) {
        int ret = 0;
        while (v != 0) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    // 32 bit float (not IEEE; nonnormalized mantissa +
    // biased exponent) : neeeeeee eeemmmmm mmmmmmmm mmmmmmmm
    // Why not IEEE?  It's just not that important here.

    static final int VQ_FEXP = 10;
    static final int VQ_FMAN = 21;
    static final int VQ_FEXP_BIAS = 768; // bias toward values smaller than 1.

    // doesn't currently guard under/overflow
    private static long float32_pack(float val) {
        int sign = 0;
        int exp;
        int mant;
        if (val < 0) {
            sign = 0x80000000;
            val = -val;
        }
        exp = (int) Math.floor(Math.log(val) / Math.log(2));
        mant = (int) Math.rint(Math.pow(val, (VQ_FMAN - 1) - exp));
        exp = (exp + VQ_FEXP_BIAS) << VQ_FMAN;
        return (sign | exp | mant);
    }

    public float float32_unpack(int val) {
        float mant = val & 0x1fffff;
        float sign = val & 0x80000000;
        float exp = (val & 0x7fe00000) >>> VQ_FMAN;
        //if (sign != 0.0) mant= -mant;
        if ((val & 0x80000000) != 0) mant = -mant;
        return (ldexp(mant, ((int) exp) - (VQ_FMAN - 1) - VQ_FEXP_BIAS));
    }

    private static float ldexp(float foo, int e) {
        return (float) (foo * Math.pow(2, e));
    }
}
