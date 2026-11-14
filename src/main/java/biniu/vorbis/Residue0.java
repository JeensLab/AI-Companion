package biniu.vorbis;

import biniu.ogg.Buffer;


class Residue0 extends FuncResidue {

    @Override
    void pack(Object vr, Buffer opb) {
        InfoResidue0 info = (InfoResidue0) vr;
        int acc = 0;
        opb.write(info.begin, 24);
        opb.write(info.end, 24);

        opb.write(info.grouping - 1, 24); // residue vectors to group and code with a partitioned book
        opb.write(info.partitions - 1, 6); // possible partition choices
        opb.write(info.groupbook, 8); // group huffman book

        // secondstages is a bitmask; as encoding progresses pass by pass, a
        // bitmask of one indicates this partition class has bits to write
        // this pass
        for (int j = 0; j < info.partitions; j++) {
            if (iLog(info.secondstages[j]) > 3) {
                // yes, this is a minor hack due to not thinking ahead
                opb.write(info.secondstages[j], 3);
                opb.write(1, 1);
                opb.write(info.secondstages[j] >>> 3, 5);
            } else {
                opb.write(info.secondstages[j], 4); // trailing zero
            }
            acc += iCount(info.secondstages[j]);
        }
        for (int j = 0; j < acc; j++) {
            opb.write(info.booklist[j], 8);
        }
    }

    @Override
    LookResidue unpack(Info vi, Buffer opb) {
        int acc = 0;
        InfoResidue0 info = new InfoResidue0();

        info.begin = opb.read(24);
        info.end = opb.read(24);
        info.grouping = opb.read(24) + 1;
        info.partitions = opb.read(6) + 1;
        info.groupbook = opb.read(8);

        for (int j = 0; j < info.partitions; j++) {
            int cascade = opb.read(3);
            if (opb.read(1) != 0) {
                cascade |= (opb.read(5) << 3);
            }
            info.secondstages[j] = cascade;
            acc += iCount(cascade);
        }

        for (int j = 0; j < acc; j++) {
            info.booklist[j] = opb.read(8);
//    if(info.booklist[j]==255)info.booklist[j]=-1;
        }

        if (info.groupbook >= vi.books) {
            clear(info);
            return null;
        }

        for (int j = 0; j < acc; j++) {
            if (info.booklist[j] >= vi.books) {
                clear(info);
                return null;
            }
        }
        return null;
    }

    @Override
    public LookResidue look(DspState vd, Object vr) {
        InfoResidue0 info = (InfoResidue0) vr;
        LookResidue look = new LookResidue();
        CodecSetupInfo ci = vd.vi.getCodecSetup();

        int j, k, acc = 0;
        int dim;
        int maxstage = 0;
        look.info = info;

        look.parts = info.partitions;
        look.fullbooks = ci.fullBooks;
        look.phrasebook = ci.fullBooks[info.groupbook];
        dim = look.phrasebook.dim;

        look.partbooks = new CodeBook[look.parts][];
        for (j = 0; j < look.parts; j++) {
            int stages = iLog(info.secondstages[j]);
            if (stages != 0) {
                if (stages > maxstage) maxstage = stages;
                look.partbooks[j] = new CodeBook[stages];
                for (k = 0; k < stages; k++)
                    if ((info.secondstages[j] & (1 << k)) != 0) {
                        look.partbooks[j][k] = ci.fullBooks[info.booklist[acc++]];
                    }
            }
        }

        look.partvals = (int) Math.rint(Math.pow((float) look.parts, (float) dim));
        look.stages = maxstage;
        look.decodemap = new int[look.partvals][];
        for (j = 0; j < look.partvals; j++) {
            int val = j;
            int mult = look.partvals / look.parts;
            look.decodemap[j] = new int[dim];
            for (k = 0; k < dim; k++) {
                int deco = val / mult;
                val -= deco * mult;
                mult /= look.parts;
                look.decodemap[j][k] = deco;
            }
        }
        return look;
    }

    @Override
    public LookResidue look(DspState vd, InfoMode vm, Object vr) {
        InfoResidue0 info = (InfoResidue0) vr;
        LookResidue look = new LookResidue();
        int acc = 0;
        int dim;
        int maxstage = 0;
        look.info = info;
        look.map = vm.mapping;

        look.parts = info.partitions;
        look.fullbooks = vd.fullbooks;
        look.phrasebook = vd.fullbooks[info.groupbook];

        dim = look.phrasebook.dim;

        look.partbooks = new CodeBook[look.parts][];

        for (int j = 0; j < look.parts; j++) {
            int stages = iLog(info.secondstages[j]);
            if (stages != 0) {
                if (stages > maxstage) maxstage = stages;
                look.partbooks[j] = new CodeBook[stages];
                for (int k = 0; k < stages; k++) {
                    if ((info.secondstages[j] & (1 << k)) != 0) {
                        look.partbooks[j][k] = look.fullbooks[info.booklist[acc++]];
                    }
                }
            }
        }

        look.partvals = (int) Math.rint(Math.pow(look.parts, dim));
        look.stages = maxstage;
        look.decodemap = new int[look.partvals][];
        for (int j = 0; j < look.partvals; j++) {
            int val = j;
            int mult = look.partvals / look.parts;
            look.decodemap[j] = new int[dim];

            for (int k = 0; k < dim; k++) {
                int deco = val / mult;
                val -= deco * mult;
                mult /= look.parts;
                look.decodemap[j][k] = deco;
            }
        }
        return look;
    }

    @Override
    void clear(Object i) {
    }

    private int[][][] partword = new int[2][][]; // _01inverse is synchronized for

    // re-using partword
    protected int _01inverse(Block vb, LookResidue look,
                             float[][] in, int ch, int decodepart) {
        int i, j, k, l, s;
        InfoResidue0 info = look.info;

        // move all this setup out later
        int samples_per_partition = info.grouping;
        int partitions_per_word = look.phrasebook.dim;
        int n = info.end - info.begin;

        int partvals = n / samples_per_partition;
        int partwords = (partvals + partitions_per_word - 1) / partitions_per_word;

        if (partword.length < ch) {
            partword = new int[ch][][];
            for (j = 0; j < ch; j++) {
                partword[j] = new int[partwords][];
            }
        } else {
            for (j = 0; j < ch; j++) {
                if (partword[j] == null || partword[j].length < partwords)
                    partword[j] = new int[partwords][];
            }
        }

        for (s = 0; s < look.stages; s++) {
            // each loop decodes on partition codeword containing
            // partitions_pre_word partitions
            for (i = 0, l = 0; i < partvals; l++) {
                if (s == 0) {
                    // fetch the partition word for each channel
                    for (j = 0; j < ch; j++) {
                        int temp = look.phrasebook.decode(vb.opb);
                        if (temp == -1) {
                            return 0;
                        }
                        partword[j][l] = look.decodemap[temp];
                        if (partword[j][l] == null) {
                            return 0;
                        }
                    }
                }

                // now we decode residual values for the partitions
                for (k = 0; k < partitions_per_word && i < partvals; k++, i++)
                    for (j = 0; j < ch; j++) {
                        int offset = info.begin + i * samples_per_partition;
                        if ((info.secondstages[partword[j][l][k]] & (1 << s)) != 0) {
                            CodeBook stagebook = look.partbooks[partword[j][l][k]][s];
                            if (stagebook != null) {
                                if (decodepart == 0) {
                                    if (stagebook.decodevs_add(in[j], offset, vb.opb, samples_per_partition) == -1) {
                                        return 0;
                                    }
                                } else if (decodepart == 1) {
                                    if (stagebook.decodev_add(in[j], offset, vb.opb, samples_per_partition) == -1) {
                                        return 0;
                                    }
                                }
                            }
                        }
                    }
            }
        }
        return 0;
    }

    protected static int _2inverse(Block vb, Object vl, float[][] in, int ch) {
        int i, j, k, l, s;
        LookResidue look = (LookResidue) vl;
        InfoResidue0 info = look.info;

        // move all this setup out later
        int samples_per_partition = info.grouping;
        int partitions_per_word = look.phrasebook.dim;
        int n = info.end - info.begin;

        int partvals = n / samples_per_partition;
        int partwords = (partvals + partitions_per_word - 1) / partitions_per_word;

        int[][] partword = new int[partwords][];
        for (s = 0; s < look.stages; s++) {
            for (i = 0, l = 0; i < partvals; l++) {
                if (s == 0) {
                    // fetch the partition word for each channel
                    int temp = look.phrasebook.decode(vb.opb);
                    if (temp == -1) {
                        return 0;
                    }
                    partword[l] = look.decodemap[temp];
                    if (partword[l] == null) {
                        return 0;
                    }
                }

                // now we decode residual values for the partitions
                for (k = 0; k < partitions_per_word && i < partvals; k++, i++) {
                    int offset = info.begin + i * samples_per_partition;
                    if ((info.secondstages[partword[l][k]] & (1 << s)) != 0) {
                        CodeBook stagebook = look.partbooks[partword[l][k]][s];
                        if (stagebook != null) {
                            if (stagebook.decodevv_add(in, offset, ch, vb.opb, samples_per_partition) == -1) {
                                return 0;
                            }
                        }
                    }
                }
            }
        }
        return 0;
    }

    @Override
    public int inverse(Block vb, LookResidue vl, float[][] in, int[] nonzero, int ch) {
        int used = 0;
        for (int i = 0; i < ch; i++) {
            if (nonzero[i] != 0) {
                in[used++] = in[i];
            }
        }
        if (used != 0)
            return (_01inverse(vb, vl, in, used, 0));
        else
            return 0;
    }

    private static int iLog(int v) {
        int ret = 0;
        while (v != 0) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    private static int iCount(int v) {
        int ret = 0;
        while (v != 0) {
            ret += (v & 1);
            v >>>= 1;
        }
        return ret;
    }

    @Override
    public int[][] clas(LookResidue vl, float[][] in, int pin, int[] nonzero, int ch) {
        int i, used = 0;
        for (i = 0; i < ch; i++)
            if (nonzero[i] != 0)
                in[used++] = in[i];
        if (used != 0)
            return (_01class(vl, in, pin, used));
        else
            return null;

    }

    protected static int[][] _01class(LookResidue vl, float[][] in, int pin, int ch) {
        int i, j, k;
        LookResidue look = vl;
        InfoResidue0 info = look.info;

        // move all this setup out later
        int samples_per_partition = info.grouping;
        int possible_partitions = info.partitions;
        int n = info.end - info.begin;

        int partvals = n / samples_per_partition;
        int[][] partword = new int[ch][];
        float scale = 100.f / samples_per_partition;

        // we find the partition type for each partition of each
        // channel.  We'll go back and do the interleaved encoding in a
        // bit.  For now, clarity */

        for (i = 0; i < ch; i++) {
            partword[i] = new int[n / samples_per_partition];
        }

        for (i = 0; i < partvals; i++) {
            int offset = (i * samples_per_partition) + info.begin + pin;
            for (j = 0; j < ch; j++) {
                float max = 0.f;
                float ent = 0.f;
                for (k = 0; k < samples_per_partition; k++) {
                    if (Math.abs(in[j][offset + k]) > max) max = Math.abs(in[j][offset + k]);
                    ent = (float) (ent + Math.abs(Math.rint(in[j][offset + k])));
                }
                ent *= scale;

                for (k = 0; k < possible_partitions - 1; k++)
                    if (max <= info.classmetric1[k] &&
                            (info.classmetric2[k] < 0 || (int) ent < info.classmetric2[k]))
                        break;

                partword[j][i] = k;
            }
        }
        look.frames++;

        return partword;
    }

    @Override
    public int forward(Buffer opb, Block vb, LookResidue vl,
                       float[][] in, float[][] out, int[] nonzero, int ch,
                       int[][] partword) {
        // we encode only the nonzero parts of a bundle
        int i, j, used = 0, n = vb.pcmEnd / 2;
        for (i = 0; i < ch; i++)
            if (nonzero[i] != 0) {
                if (out != null)
                    for (j = 0; j < n; j++)
                        out[i][j] += in[i][j];
                in[used++] = in[i];
            }
        if (used != 0) {
            int ret = _01forward(opb, vb, vl, in, used, partword);
            if (out != null) {
                used = 0;
                for (i = 0; i < ch; i++)
                    if (nonzero[i] != 0) {
                        for (j = 0; j < n; j++)
                            out[i][j] -= in[used][j];
                        used++;
                    }
            }
            return ret;
        } else {
            return 0;
        }
    }

    public int _01forward(Buffer opb,
                          Block vb, LookResidue look,
                          float[][] in, int ch,
                          int[][] partword) {

        int i, j, k, s;
        DspState vd = vb.dspState;

        // move all this setup out later
        int samples_per_partition = look.info.grouping;
        int possible_partitions = look.info.partitions;
        int partitions_per_word = look.phrasebook.dim;
        int n = look.info.end - look.info.begin;

        int partvals = n / samples_per_partition;
        int[] resbits = new int[128];
        int[] resvals = new int[128];

        // we code the partition words for each channel, then the residual
        // words for a partition per channel until we've written all the
        // residual words for that partition word.  Then write the next
        // partition channel words...

        for (s = 0; s < look.stages; s++) {

            for (i = 0; i < partvals; ) {

                // first we encode a partition codeword for each channel
                if (s == 0) {
                    for (j = 0; j < ch; j++) {
                        int val = partword[j][i];
                        for (k = 1; k < partitions_per_word; k++) {
                            val *= possible_partitions;
                            if (i + k < partvals)
                                val += partword[j][i + k];
                        }

                        // training hack
                        if (val < look.phrasebook.entries)
                            look.phrasebits += look.phrasebook.bookEncode(val, opb);
                    }
                }

                // now we encode interleaved residual values for the partitions
                for (k = 0; k < partitions_per_word && i < partvals; k++, i++) {
                    int offset = i * samples_per_partition + look.info.begin;

                    for (j = 0; j < ch; j++) {
                        if (s == 0) resvals[partword[j][i]] += samples_per_partition;
                        if ((look.info.secondstages[partword[j][i]] & (1 << s)) != 0) {
                            CodeBook statebook = look.partbooks[partword[j][i]][s];
                            if (statebook != null) {
                                int ret;
                                int[] accumulator = null;
                                ret = _encodepart(opb, in[j], offset, samples_per_partition, statebook, accumulator);

                                look.postbits += ret;
                                resbits[partword[j][i]] += ret;
                            }
                        }
                    }
                }
            }
        }

        return 0;
    }

    private static int _encodepart(Buffer opb, float[] vec, int pvec, int n,
                                   CodeBook book, int[] acc) {
        int i, bits = 0;
        int dim = book.dim;
        int step = n / dim;

        for (i = 0; i < step; i++) {
            int entry = bookBestError(book, vec, (i * dim) + pvec);
            bits += book.bookEncode(entry, opb);
        }

        return bits;
    }

    /** break an abstraction and copy some code for performance purposes */
    private static int bookBestError(CodeBook book, float[] a, int pa) {

        int dim = book.dim, i, k, o;
        int best = 0;
        EncodeAuxThreshMatch tt = book.c.thresh_tree;

        // find the quant val of each scalar
        for (k = 0, o = dim; k < dim; ++k) {
            float val = a[--o + pa];
            i = tt.threshvals >> 1;

            if (val < tt.quantthresh[i]) {
                if (val < tt.quantthresh[i - 1]) {
                    for (--i; i > 0; --i)
                        if (val >= tt.quantthresh[i - 1])
                            break;
                }
            } else {

                for (++i; i < tt.threshvals - 1; ++i)
                    if (val < tt.quantthresh[i]) break;

            }

            best = (best * tt.quantvals) + tt.quantmap[i];
        }
        // regular lattices are easy :-)

        if (book.c.lengthlist[best] <= 0) {
            StaticCodeBook c = book.c;
            i = 0;
            float bestf = 0.f;
            float[] e = book.valuelist;
            best = -1;
            int g = 0;
            for (i = 0; i < book.entries; i++) {
                if (c.lengthlist[i] > 0) {
                    float thisx = 0.f;
                    for (int j = 0; j < dim; j++) {
                        float val = (e[j + g] - a[j + pa]);
                        thisx += val * val;
                    }
                    if (best == -1 || thisx < bestf) {
                        bestf = thisx;
                        best = i;
                    }
                }
                g += dim;
            }
        }

        {
            for (i = 0; i < dim; i++) {
                a[i + pa] -= book.valuelist[i + (best * dim)];
            }
        }

        return best;
    }
}
