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
class CodeBook {

    /** codebook dimensions (elements per vector) */
    int dim;
    /** codebook entries */
    int entries;
    int used_entries;
    StaticCodeBook c = new StaticCodeBook();

    // list of dim * entries actual entry values
    float[] valuelist;
    // list of bitstream codewords for each entry
    int[] codelist;
    DecodeAux decode_tree;

    // returns the number of bits
    int encode(int a, Buffer b) {
        b.write(codelist[a], c.lengthlist[a]);
        return (c.lengthlist[a]);
    }

    // One the encode side, our vector writers are each designed for a
    // specific purpose, and the encoder is not flexible without modification:
    //
    // The LSP vector coder uses a single stage nearest-match with no
    // interleave, so no step and no error return.  This is speced by floor0
    // and doesn't change.
    //
    // Residue0 encoding interleaves, uses multiple stages, and each stage
    // peels of a specific amount of resolution from a lattice (thus we want
    // to match by threshold, not nearest match).  Residue doesn't *have* to
    // be encoded that way, but to change it, one will need to add more
    // infrastructure on the encode side (decode side is speced and simpler)

    /**
     * floor0 LSP (single stage, non interleaved, nearest match)
     * returns entry number and *modifies a* to the quantization value
     */
    int errorv(float[] a) {
        int best = best(a, 1);
        for (int k = 0; k < dim; k++) {
            a[k] = valuelist[best * dim + k];
        }
        return best;
    }

    /** returns the number of bits and *modifies a* to the quantization value */
    int encodev(int best, float[] a, Buffer b) {
        for (int k = 0; k < dim; k++) {
            a[k] = valuelist[best * dim + k];
        }
        return (encode(best, b));
    }

    /**
     * res0 (multistage, interleave, lattice)
     * returns the number of bits and *modifies a* to the remainder value
     */
    int encodevs(float[] a, Buffer b, int step, int addmul) {
        int best = besterror(a, step, addmul);
        return (encode(best, b));
    }

    /** decodevs_add is synchronized for re-using t. */
    private int[] t = new int[15];

    synchronized int decodevs_add(float[] a, int offset, Buffer b, int n) {
        int step = n / dim;
        int entry;
        int i, j, o;

        if (t.length < step) {
            t = new int[step];
        }

        for (i = 0; i < step; i++) {
            entry = decode(b);
            if (entry == -1) return -1;
            t[i] = entry * dim;
        }
        for (i = 0, o = 0; i < dim; i++, o += step) {
            for (j = 0; j < step; j++) {
                a[offset + o + j] += valuelist[t[j] + i];
            }
        }

        return 0;
    }

    int decodev_add(float[] a, int offset, Buffer b, int n) {
        int i, j, entry;
        int t;

        if (dim > 8) {
            for (i = 0; i < n; ) {
                entry = decode(b);
                if (entry == -1) return -1;
                t = entry * dim;
                for (j = 0; j < dim; ) {
                    a[offset + (i++)] += valuelist[t + (j++)];
                }
            }
        } else {
            for (i = 0; i < n; ) {
                entry = decode(b);
                if (entry == -1) return -1;
                t = entry * dim;
                j = 0;
                switch (dim) {
                case 8:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 7:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 6:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 5:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 4:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 3:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 2:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 1:
                    a[offset + (i++)] += valuelist[t + (j++)];
                case 0:
                    break;
                }
            }
        }
        return 0;
    }

    int decodev_set(float[] a, int offset, Buffer b, int n) {
        int i, j, entry;
        int t;

        for (i = 0; i < n; ) {
            entry = decode(b);
            if (entry == -1) return -1;
            t = entry * dim;
            for (j = 0; j < dim; ) {
                a[offset + i++] = valuelist[t + (j++)];
            }
        }
        return 0;
    }

    int decodevv_add(float[][] a, int offset, int ch, Buffer b, int n) {
        int i, j, k, entry;
        int chptr = 0;
        //System.out.println("decodevv_add: a="+a+",b="+b+",valuelist="+valuelist);

        for (i = offset / ch; i < (offset + n) / ch; ) {
            entry = decode(b);
            if (entry == -1) return -1;

            int t = entry * dim;
            for (j = 0; j < dim; j++) {
                a[chptr++][i] += valuelist[t + j];
                if (chptr == ch) {
                    chptr = 0;
                    i++;
                }
            }
        }
        return 0;
    }

    /**
     * Decode side is specced and easier, because we don't need to find
     * matches using different criteria; we simply read and map.  There are
     * two things we need to do 'depending':
     * <p>
     * We may need to support interleave.  We don't really, but it's
     * convenient to do it here rather than rebuild the vector later.
     * <p>
     * Cascades may be additive or multiplicative; this is not inherent in
     * the codebook, but set in the code using the codebook.  Like
     * interleaving, it's easiest to do it here.
     * <pre>
     * stage==0 -> declarative (set the value)
     * stage==1 -> additive
     * stage==2 -> multiplicative
     * </pre>
     *
     * @return the entry number or -1 on eof
     */
    int decode(Buffer b) {
        int ptr = 0;
        DecodeAux t = decode_tree;
        int lok = b.look(t.tabn);
        //System.err.println(this+" "+t+" lok="+lok+", tabn="+t.tabn);

        if (lok >= 0) {
            ptr = t.tab[lok];
            b.adv(t.tabl[lok]);
            if (ptr <= 0) {
                return -ptr;
            }
        }
        do {
            switch (b.read1()) {
            case 0:
                ptr = t.ptr0[ptr];
                break;
            case 1:
                ptr = t.ptr1[ptr];
                break;
            case -1:
            default:
                return -1;
            }
        }
        while (ptr > 0);
        return -ptr;
    }

    /** returns the entry number or -1 on eof */
    int decodevs(float[] a, int index, Buffer b, int step, int addmul) {
        int entry = decode(b);
        if (entry == -1) return -1;
        switch (addmul) {
        case -1:
            for (int i = 0, o = 0; i < dim; i++, o += step)
                a[index + o] = valuelist[entry * dim + i];
            break;
        case 0:
            for (int i = 0, o = 0; i < dim; i++, o += step)
                a[index + o] += valuelist[entry * dim + i];
            break;
        case 1:
            for (int i = 0, o = 0; i < dim; i++, o += step)
                a[index + o] *= valuelist[entry * dim + i];
            break;
        default:
            //System.err.println("CodeBook.decodeves: addmul="+addmul);
        }
        return entry;
    }

    int best(float[] a, int step) {
        EncodeAuxNearestMatch nt = c.nearest_tree;
        EncodeAuxThreshMatch tt = c.thresh_tree;
        int ptr = 0;

        // we assume for now that a thresh tree is the only other possibility
        if (tt != null) {
            int index = 0;
            // find the quant val of each scalar
            for (int k = 0, o = step * (dim - 1); k < dim; k++, o -= step) {
                int i;
                // linear search the quant list for now; it's small and although
                // with > 8 entries, it would be faster to bisect, this would be
                // a misplaced optimization for now
                for (i = 0; i < tt.threshvals - 1; i++) {
                    if (a[o] < tt.quantthresh[i]) {
                        break;
                    }
                }
                index = (index * tt.quantvals) + tt.quantmap[i];
            }
            // regular lattices are easy :-)
            if (c.lengthlist[index] > 0) {
                // is this unused?  If so, we'll
                // use a decision tree after all
                // and fall through
                return index;
            }
        }
        if (nt != null) {
            // optimized using the decision tree
            while (true) {
                float c = 0.f;
                int p = nt.p[ptr];
                int q = nt.q[ptr];
                for (int k = 0, o = 0; k < dim; k++, o += step) {
                    c = (float) (c + (valuelist[p + k] - valuelist[q + k]) *
                            (a[o] - (valuelist[p + k] + valuelist[q + k]) * .5));
                }
                if (c > 0.) { // in A
                    ptr = -nt.ptr0[ptr];
                } else {     // in B
                    ptr = -nt.ptr1[ptr];
                }
                if (ptr <= 0) break;
            }
            return -ptr;
        }

        // brute force it!
        {
            int besti = -1;
            float best = 0.f;
            int e = 0;
            for (int i = 0; i < entries; i++) {
                if (c.lengthlist[i] > 0) {
                    float _this = dist(dim, valuelist, e, a, step);
                    if (besti == -1 || _this < best) {
                        best = _this;
                        besti = i;
                    }
                }
                e += dim;
            }
            return besti;
        }
    }

    // returns the entry number and *modifies a* to the remainder value
    int besterror(float[] a, int step, int addmul) {
        int best = best(a, step);
        switch (addmul) {
        case 0:
            for (int i = 0, o = 0; i < dim; i++, o += step)
                a[o] -= valuelist[best * dim + i];
            break;
        case 1:
            for (int i = 0, o = 0; i < dim; i++, o += step) {
                float val = valuelist[best * dim + i];
                if (val == 0) {
                    a[o] = 0;
                } else {
                    a[o] /= val;
                }
            }
            break;
        }
        return best;
    }

    void clear() {
    }

    private static float dist(int el, float[] ref, int index, float[] b, int step) {
        float acc = (float) 0.;
        for (int i = 0; i < el; i++) {
            float val = (ref[index + i] - b[i * step]);
            acc += val * val;
        }
        return acc;
    }

//    int init_encode(StaticCodeBook s) {
//        //memset(c,0,sizeof(codebook));
//        c = s;
//        entries = s.entries;
//        dim = s.dim;
//        codelist = make_words(s.lengthlist, s.entries);
//        valuelist = s.unquantize();
//        return 0;
//    }

    private static int ilog(int v) {
        int ret = 0;
        while (v != 0) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    /** returns the number of bits */
    public int bookEncode(int a, Buffer b) {
        b.write(this.codelist[a], this.c.lengthlist[a]);
        return (this.c.lengthlist[a]);
    }

    public int bookInitEncode(StaticCodeBook s) {

        this.c = s;
        this.entries = s.entries;
        this.used_entries = s.entries;
        this.dim = s.dim;
        this.codelist = makeWords(s.lengthlist, s.entries, 0);
        this.valuelist = bookUnquantize(s, s.entries, null);

        return 0;
    }

    /**
     * given a list of word lengths, generate a list of codewords.  Works
     * for length ordered or unordered, always assigns the lowest valued
     * codewords first.  Extended to handle unused entries (length 0)
     */
    private static int[] makeWords(int[] l, int n, int sparsecount) {
        int i, j, count = 0;
        int[] marker = new int[33];
        int[] r = new int[sparsecount != 0 ? sparsecount : n];

        for (i = 0; i < n; i++) {
            int length = l[i];
            if (length > 0) {
                int entry = marker[length];

                // when we claim a node for an entry, we also claim the nodes
                // below it (pruning off the imagined tree that may have dangled
                // from it) as well as blocking the use of any nodes directly
                // above for leaves

                // update ourself
                if (length < 32 && (entry >> length) != 0) {
                    // error condition; the lengths must specify an overpopulated tree
                    return null;
                }
                r[count++] = entry;

                // Look to see if the next shorter marker points to the node
                // above. if so, update it and repeat.
                {
                    for (j = length; j > 0; j--) {

                        if ((marker[j] & 1) != 0) {
                            // have to jump branches
                            if (j == 1)
                                marker[1]++;
                            else
                                marker[j] = marker[j - 1] << 1;
                            break; // invariant says next upper marker would already
                            // have been moved if it was on the same path
                        }
                        marker[j]++;
                    }
                }

                // prune the tree; the implicit invariant says all the longer
                // markers were dangling from our just-taken node.  Dangle them
                // from our *new* node.
                for (j = length + 1; j < 33; j++)
                    if ((marker[j] >> 1) == entry) {
                        entry = marker[j];
                        marker[j] = marker[j - 1] << 1;
                    } else
                        break;
            } else if (sparsecount == 0) count++;
        }

        // bitreverse the words because our bitwise packer/unpacker is LSb
        // endian
        for (i = 0, count = 0; i < n; i++) {
            int temp = 0;
            for (j = 0; j < l[i]; j++) {
                temp <<= 1;
                temp |= (r[count] >> j) & 1;
            }

            if (sparsecount != 0) {
                if (l[i] != 0)
                    r[count++] = temp;
            } else
                r[count++] = temp;
        }

        return r;
    }

    // unpack the quantized list of values for encode/decode

    /**
     * we need to deal with two map types: in map type 1, the values are
     * generated algorithmically (each column of the vector counts through
     * the values in the quant vector). in map type 2, all the values came
     * in an explicit list.  Both value lists must be unpacked
     */
    private float[] bookUnquantize(StaticCodeBook b, int n, int[] sparsemap) {
        int j, k, count = 0;
        if (b.maptype == 1 || b.maptype == 2) {
            int quantvals;
            float mindel = b.float32_unpack(b.q_min);
            float delta = b.float32_unpack(b.q_delta);
            float[] r = new float[n * b.dim];//_ogg_calloc(n*b->dim,sizeof(*r));

            // maptype 1 and 2 both use a quantized value vector, but
            // different sizes
            switch (b.maptype) {
            case 1:
                // most of the time, entries % dimensions == 0, but we need to be
                // well defined.  We define that the possible vales at each
                // scalar is values == entries / dim.  If entries % dim != 0, we'll
                // have 'too few' values (values * dim < entries), which means that
                // we'll have 'left over' entries; left over entries use zeroed
                // values (and are wasted).  So don't generate codebooks like
                // that
                quantvals = bookMaptype1Quantvals(b);
                for (j = 0; j < b.entries; j++) {
                    if ((sparsemap != null && b.lengthlist[j] != 0) || sparsemap == null) {
                        float last = 0.f;
                        int indexdiv = 1;
                        for (k = 0; k < b.dim; k++) {
                            int index = (j / indexdiv) % quantvals;
                            float val = b.quantlist[index];
                            val = Math.abs(val) * delta + mindel + last;
                            if (b.q_sequencep != 0) last = val;
                            if (sparsemap != null)
                                r[sparsemap[count] * b.dim + k] = val;
                            else
                                r[count * b.dim + k] = val;
                            indexdiv *= quantvals;
                        }
                        count++;
                    }

                }
                break;
            case 2:
                for (j = 0; j < b.entries; j++) {
                    if ((sparsemap != null && b.lengthlist[j] != 0) || sparsemap != null) {
                        float last = 0.f;

                        for (k = 0; k < b.dim; k++) {
                            float val = b.quantlist[j * b.dim + k];
                            val = Math.abs(val) * delta + mindel + last;
                            if (b.q_sequencep != 0) last = val;
                            if (sparsemap != null)
                                r[sparsemap[count] * b.dim + k] = val;
                            else
                                r[count * b.dim + k] = val;
                        }
                        count++;
                    }
                }
                break;
            }

            return r;
        }
        return null;
    }

    /**
     * there might be a straightforward one-line way to do the below
     * that's portable and totally safe against roundoff, but I haven't
     * thought of it.  Therefore, we opt on the side of caution
     */
    private static int bookMaptype1Quantvals(StaticCodeBook b) {
        int vals = (int) Math.floor(Math.pow(b.entries, (double) 1.f / b.dim));

        // the above *should* be reliable, but we'll not assume that FP is
        // ever reliable when bitstream sync is at stake; verify via integer
        // means that vals really is the greatest value of dim for which
        // vals^b.bim <= b.entries
        // treat the above as an initial guess
        while (true) {
            long acc = 1;
            long acc1 = 1;
            int i;
            for (i = 0; i < b.dim; i++) {
                acc *= vals;
                acc1 *= vals + 1;
            }
            if (acc <= b.entries && acc1 > b.entries) {
                return vals;
            } else {
                if (acc > b.entries) {
                    vals--;
                } else {
                    vals++;
                }
            }
        }
    }
}

class DecodeAux {

    int[] tab;
    int[] tabl;
    int tabn;

    int[] ptr0;
    int[] ptr1;
    /** number of tree entries */
    int aux;
}
