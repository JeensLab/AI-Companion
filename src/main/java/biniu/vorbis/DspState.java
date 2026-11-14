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
import biniu.ogg.Packet;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class DspState {

    static final float M_PI = 3.1415926539f;
    static final int VI_TRANSFORMB = 1;
    static final int VI_WINDOWB = 1;

    int analysisp;
    public Info vi;
    private CodecSetupInfo ci;
    private InfoPsyGlobal gi;
    int modebits;

    float[][] pcm;

    int pcm_storage;
    public int pcm_current;
    int pcm_returned;

    float[] multipliers;

    int preextrapolate;
    int eofflag;

    public int lW;
    public int W;
    int nW;
    int centerW;

    long granulepos;
    long sequence;

    long glue_bits;
    long time_bits;
    long floor_bits;
    long res_bits;

    PrivateState backEndState;

    // local lookup storage
    final float[][][][][] window; // block, leadin, leadout, type
    //vorbis_look_transform **transform[2]; // block, type
    final Object[][] transform;
    CodeBook[] fullbooks;
    // backend lookups are tied to the mode, not the backend or naked mapping
    Object[] mode;

    // local storage, only used on the encoding side.  This way the
    // application does not need to worry about freeing some packets'
    // memory and not others'; packet storage is always tracked.
    // Cleared next call to a _dsp_ function
    byte[] header;
    byte[] header1;
    byte[] header2;

//    private final Window _w = new Window();

    public DspState() {
        transform = new Object[2][];
        window = new float[2][][][][];
        window[0] = new float[2][][][];
        window[0][0] = new float[2][][];
        window[0][1] = new float[2][][];
        window[0][0][0] = new float[2][];
        window[0][0][1] = new float[2][];
        window[0][1][0] = new float[2][];
        window[0][1][1] = new float[2][];
        window[1] = new float[2][][][];
        window[1][0] = new float[2][][];
        window[1][1] = new float[2][][];
        window[1][0][0] = new float[2][];
        window[1][0][1] = new float[2][];
        window[1][1][0] = new float[2][];
        window[1][1][1] = new float[2][];
    }

    private static int ilog2(int v) {
        int ret = 0;
        while (v > 1) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    private PrivateState getPrivateState() {
        return this.backEndState;
    }

    private static float[] window(int type, int window, int left, int right) {
        float[] ret = new float[window];
        switch (type) {
        case 0:
            // The 'vorbis window' (window 0) is sin(sin(x)*sin(x)*2pi)
        {
            int leftbegin = window / 4 - left / 2;
            int rightbegin = window - window / 4 - right / 2;

            for (int i = 0; i < left; i++) {
                float x = (float) ((i + .5) / left * M_PI / 2.);
                x = (float) Math.sin(x);
                x *= x;
                x = (float) (x * M_PI / 2.);
                x = (float) Math.sin(x);
                ret[i + leftbegin] = x;
            }

            for (int i = leftbegin + left; i < rightbegin; i++) {
                ret[i] = 1.f;
            }

            for (int i = 0; i < right; i++) {
                float x = (float) ((right - i - .5) / right * M_PI / 2.);
                x = (float) Math.sin(x);
                x *= x;
                x = (float) (x * M_PI / 2.);
                x = (float) Math.sin(x);
                ret[i + rightbegin] = x;
            }
        }
        break;
        default:
            //free(ret);
            return null;
        }
        return ret;
    }

    private int sharedInit(Info vi, int encp) {
        int i;
        this.vi = vi;
        this.ci = vi.getCodecSetup();
        this.gi = ci.psyGlobParam;
        this.backEndState = new PrivateState();
        int hs;

        if (ci == null) return 1;
        hs = ci.halfrateFlag;

        backEndState.modeBits = ilog2(ci.modes);

        backEndState.transform[0] = new Mdct[VI_TRANSFORMB];
        backEndState.transform[1] = new Mdct[VI_TRANSFORMB];

        // MDCT is tranform 0
        backEndState.transform[0][0] = new Mdct();
        backEndState.transform[1][0] = new Mdct();
        backEndState.transform[0][0].init(ci.blocksizes[0] >> hs);
        backEndState.transform[1][0].init(ci.blocksizes[1] >> hs);

        // Vorbis I use only window type 0
        backEndState.window[0] = ilog2(ci.blocksizes[0]) - 6;
        backEndState.window[1] = ilog2(ci.blocksizes[1]) - 6;

        if (encp != 0) { // encode/decode differ here

            // analysis always needs a fft
            drftInit(backEndState.fftLook[0], ci.blocksizes[0]);
            drftInit(backEndState.fftLook[1], ci.blocksizes[1]);

            // finish the codebooks
            if (ci.fullBooks == null) {
                ci.fullBooks = new CodeBook[ci.books];
                for (i = 0; i < ci.books; i++) {
                    ci.fullBooks[i] = new CodeBook();
                    ci.fullBooks[i].bookInitEncode(ci.bookParam[i]);
                }
            }

            backEndState.psy = new PsyLook[ci.psys];
            for (i = 0; i < ci.psys; i++) {
                backEndState.psy[i] = new PsyLook();
                backEndState.psy[i].psyInit(ci.psyParam[i], ci.psyGlobParam, ci.blocksizes[ci.psyParam[i].blockflag] / 2, vi.rate);
            }

            this.analysisp = 1;
        } else {
            // finish the codebooks
            if (ci.fullBooks != null) {
                for (i = 0; i < ci.books; i++) {
                    // decode codebooks are now standalone after init
                    ci.bookParam[i] = null;
                }
            }
        }

        // initialize the storage vectors. blocksize[1] is small for encode,
        // but the correct size for decode
        this.pcm_storage = ci.blocksizes[1];
        this.pcm = new float[vi.channels][];
        {
            for (i = 0; i < vi.channels; i++)
                this.pcm[i] = new float[this.pcm_storage];
        }

        // all 1 (large block) or 0 (small block)
        // explicitly set for the sake of clarity
        this.lW = 0; // previous window size
        this.W = 0;  // current window size

        // all vector indexes
        this.centerW = ci.blocksizes[1] / 2;

        this.pcm_current = this.centerW;

        // initialize all the backend lookups
        backEndState.flr = new Object[ci.floors];
        backEndState.residue = new LookResidue[ci.residues];
        for (i = 0; i < ci.floors; i++) {
            backEndState.flr[i] = FuncFloor.floor_P[ci.floorType[i]].look(this, ci.floorParam[i]);
        }
        for (i = 0; i < ci.residues; i++) {
            backEndState.residue[i] = FuncResidue.residue_P[ci.residueType[i]].look(this, ci.residueParam[i]);
        }

        return 0;
    }

    private static void drftInit(DrftLookup l, int n) {
        l.n = n;
        l.trigcache = new float[3 * n];
        l.splitcache = new int[32];
        if (n == 1) return;
        l.drfti1(n, l.trigcache, n, l.splitcache);
    }

    /* arbitrary settings and spec-mandated numbers get filled in here */
    public int analysisInit(Info vi) {

        if (sharedInit(vi, 1) != 0) return 1;
        backEndState.psyGlobLook = vi.getLookPsyGlobal();

        /* Initialize the envelope state storage */
        backEndState.enveLook = new EnvelopeLookUp();
        backEndState.enveLook.envelopeInit(vi);

        backEndState.bms.vorbis_bitrate_init(vi);

        return 0;
    }

    // Unike in analysis, the window is only partially applied for each
    // block.  The time domain envelope is not yet handled at the point of
    // calling (as it relies on the previous block).

    public void clear() {

    }

    public int analysisWrote(int vals) {

        if (vals <= 0) {
            int order = 32;
            int i;
            float[] lpc = new float[order];

            // if it wasn't done earlier (very short sample)
            if (this.preextrapolate == 0)
                preExtrapolateHelper();

            // We're encoding the end of the stream.  Just make sure we have
            // [at least] a few full blocks of zeroes at the end.
            // actually, we don't want zeroes; that could drop a large
            // amplitude off a cliff, creating spread spectrum noise that will
            // suck to encode.  Extrapolate for the sake of cleanliness.

            analysisBuffer(ci.blocksizes[1] * 3);
            this.eofflag = this.pcm_current;
            this.pcm_current += ci.blocksizes[1] * 3;

            for (i = 0; i < vi.channels; i++) {
                if (this.eofflag > order * 2) {
                    // extrapolate with LPC to fill in
                    int n;

                    // make a predictor filter
                    n = this.eofflag;
                    if (n > ci.blocksizes[1]) n = ci.blocksizes[1];
                    Lpc.lpcFromData(this.pcm[i], this.eofflag - n,
                            lpc,
                            n,
                            order);

                    // run the predictor filter
                    Lpc.lpcPredict(lpc,
                            this.pcm[i], this.eofflag - order,
                            order,
                            this.pcm[i], this.eofflag,
                            this.pcm_current - this.eofflag);
                } else {
                    // not enough data to extrapolate (unlikely to happen due to
                    // guarding the overlap, but bulletproof in case that
                    // assumtion goes away). zeroes will do.
                }
            }
        } else {

            if (this.pcm_current + vals > this.pcm_storage)
                return Const.OV_EINVAL;

            this.pcm_current += vals;

            // we may want to reverse extrapolate the beginning of a stream
            // too... in case we're beginning on a cliff!
            // clumsy, but simple.  It only runs once, so simple is good.
            if ((this.preextrapolate == 0) && this.pcm_current - this.centerW > ci.blocksizes[1])
                preExtrapolateHelper();

        }
        return 0;
    }

    private void preExtrapolateHelper() {
        int i;
        int order = 32;
        float[] lpc = new float[order];
        float[] work = new float[pcm_current];
        int j;
        preextrapolate = 1;

        if (pcm_current - centerW > order * 2) { // safety
            for (i = 0; i < vi.channels; i++) {
                // need to run the extrapolation in reverse!
                // prime as above
                Lpc.lpcFromDataZ(this.pcm[i], lpc, pcm_current - centerW, order, pcm_current - 1);

                // run the predictor filter
                Lpc.lpcPredictZ(lpc,
                        this.pcm[i], centerW + order - 1,
                        order,
                        this.pcm[i], centerW - 1,
                        centerW);
            }
        }
    }

    public float[][] analysisBuffer(int vals) {
        int i;

        // free header, header1, header2
        if (backEndState.header != null) backEndState.header = null;
        if (backEndState.header1 != null) backEndState.header1 = null;
        if (backEndState.header2 != null) backEndState.header2 = null;

        // Do we have enough storage space for the requested buffer? If not,
        // expand the PCM (and envelope) storage

        if (this.pcm_current + vals >= this.pcm_storage) {
            this.pcm_storage = this.pcm_current + vals * 2;
            float[][] temp = new float[this.pcm.length][];
            for (i = 0; i < vi.channels; i++) {
                if (this.pcm[i] != null) {
                    temp[i] = new float[this.pcm[i].length];
                    System.arraycopy(this.pcm[i], 0, temp[i], 0, temp[i].length);
                    this.pcm[i] = new float[this.pcm_storage];
                    System.arraycopy(temp[i], 0, this.pcm[i], 0, temp[i].length);
                } else {
                    this.pcm[i] = new float[this.pcm_storage];
                }
            }
        }

        return this.pcm;
    }

    public float ampMaxDecay(float amp) {

        int n = ci.blocksizes[this.W] / 2;
        float secs = (float) n / vi.rate;

        amp += secs * gi.ampmax_att_per_sec;
        if (amp < -9999) amp = -9999;
        return amp;
    }

    public int envelopeMark() {
        EnvelopeLookUp ve = this.backEndState.enveLook;
        int centerW = this.centerW;
        int beginW = centerW - ci.blocksizes[this.W] / 4;
        int endW = centerW + ci.blocksizes[this.W] / 4;
        if (this.W != 0) {
            beginW -= ci.blocksizes[this.lW] / 4;
            endW += ci.blocksizes[this.nW] / 4;
        } else {
            beginW -= ci.blocksizes[0] / 4;
            endW += ci.blocksizes[0] / 4;
        }

        if (ve.curmark >= beginW && ve.curmark < endW) return 1;
        {
            int first = beginW / ve.searchstep;
            int last = endW / ve.searchstep;
            int i;
            for (i = first; i < last; i++)
                if (ve.mark[i] != 0) return 1;
        }
        return 0;
    }

    public int envelopeSearch() {
        EnvelopeLookUp ve = this.backEndState.enveLook;
        int i, j;

        int first = ve.current / ve.searchstep;
        int last = this.pcm_current / ve.searchstep - EnvelopeInt.VE_WIN;

        if (first < 0) first = 0;

        // make sure we have enough storage to match the PCM
        if (last + EnvelopeInt.VE_WIN + EnvelopeInt.VE_POST > ve.storage) {
            ve.storage = last + EnvelopeInt.VE_WIN + EnvelopeInt.VE_POST; // be sure
            int[] temp = new int[ve.mark.length];
            System.arraycopy(ve.mark, 0, temp, 0, temp.length);
            ve.mark = new int[ve.storage];
            System.arraycopy(temp, 0, ve.mark, 0, temp.length);
        }

        for (j = first; j < last; j++) {
            int ret = 0;

            ve.stretch++;
            if (ve.stretch > EnvelopeInt.VE_MAXSTRETCH * 2)
                ve.stretch = EnvelopeInt.VE_MAXSTRETCH * 2;
            for (i = 0; i < ve.ch; i++) {
                float[] pcm = new float[this.pcm[i].length - (ve.searchstep * (j))];
                System.arraycopy(this.pcm[i], ve.searchstep * (j), pcm, 0, pcm.length);
                ret |= ve.amp(gi, pcm, ve.band, ve.filter, i * EnvelopeInt.VE_BANDS, j);
            }

            ve.mark[j + EnvelopeInt.VE_POST] = 0;
            if ((ret & 1) != 0) {
                ve.mark[j] = 1;
                ve.mark[j + 1] = 1;
            }

            if ((ret & 2) != 0) {
                ve.mark[j] = 1;
                if (j > 0) ve.mark[j - 1] = 1;
            }

            if ((ret & 4) != 0) ve.stretch = -1;
        }

        ve.current = last * ve.searchstep;

        {
            long centerW = this.centerW;
            long testW = centerW +
                            ci.blocksizes[this.W] / 4 +
                            ci.blocksizes[1] / 2 +
                            ci.blocksizes[0] / 4;

            j = ve.cursor;

            while (j < ve.current - (ve.searchstep)) { // account for postecho working back one window
                if (j >= testW) return 1;

                ve.cursor = j;

                if (ve.mark[j / ve.searchstep] != 0) {
                    if (j > centerW) {
                        ve.curmark = j;
                        if (j >= testW) return 1;
                        return 0;
                    }
                }
                j += ve.searchstep;
            }
        }

        return -1;
    }

    public int analysisHeaderOut(Comment vc,
                                 Packet op,
                                 Packet op_comm,
                                 Packet op_code) {
        int ret = Comment.OV_EIMPL;
        Buffer opb = new Buffer();

        if (backEndState == null) {
            ret = Comment.OV_EFAULT;
            opb.writeClear();
            op = new Packet();
            op_comm = new Packet();
            op_code = new Packet();

            backEndState.header = null;
            backEndState.header1 = null;
            backEndState.header2 = null;
            return ret;
        }

        // first header packet

        opb.writeInit();
        if (vi.packInfo(opb)) {

            // build the packet
            if (backEndState.header != null) backEndState.header = null;
            backEndState.header = new byte[opb.getBytes()];
            System.arraycopy(opb.buffer, 0, backEndState.header, 0, backEndState.header.length);
            op.packetByte = backEndState.header;
            op.bytes = opb.getBytes();
            op.b_o_s = true;
            op.e_o_s = false;
            op.granulePos = 0;

            // second header packet (comments)

            opb.reset();
            if (vc.packComment(opb)) {

                if (backEndState.header1 != null) backEndState.header1 = null;
                backEndState.header1 = new byte[opb.getBytes()];
                System.arraycopy(opb.buffer, 0, backEndState.header1, 0, backEndState.header1.length);
                op_comm.packetByte = backEndState.header1;
                op_comm.bytes = opb.getBytes();
                op_comm.b_o_s = false;
                op_comm.e_o_s = false;
                op_comm.granulePos = 0;

                // third header packet (modes/codebooks)

                opb.reset();
                if (vi.packBooks(opb)) {
                    if (backEndState.header2 != null) backEndState.header2 = null;
                    backEndState.header2 = new byte[opb.getBytes()];
                    System.arraycopy(opb.buffer, 0, backEndState.header2, 0, backEndState.header2.length);
                    op_code.packetByte = backEndState.header2;
                    op_code.bytes = opb.getBytes();
                    op_code.b_o_s = false;
                    op_code.e_o_s = false;
                    op_code.granulePos = 0;

                    opb.writeClear();
                    return 0;
                }
            }
        }
        opb.writeClear();
        op = new Packet();
        op_comm = new Packet();
        op_code = new Packet();

        backEndState.header = null;
        backEndState.header1 = null;
        backEndState.header2 = null;
        return ret;
    }

    public boolean bitrateFlushPacket(Packet op) {

        BitrateManagerState bm = backEndState.bms;
        Block vb = bm.vb;
        int choice = Const.PACKETBLOBS / 2;
        if (vb == null) return false;

        if (op != null) {
            BlockInternal vbi = vb.blockInternal;

            if (vb.bitrateManaged()) {
                choice = bm.choice;
            }

            op.packetByte = vbi.packetblob[choice].buffer;
            op.bytes = vbi.packetblob[choice].getBytes();
            op.b_o_s = false;
            op.e_o_s = vb.eofFlag != 0;
            op.granulePos = vb.granulePos;
            op.packetNo = vb.sequence; // for sake of completeness
        }

        bm.vb = null;
        return true;
    }

    /**
     * Unlike in analysis, the window is only partially applied for each
     * block.  The time domain envelope is not yet handled at the point of
     * calling (as it relies on the previous block).
     */
    public int blockIn(Block vb) {
        Info vi = this.vi;
        CodecSetupInfo ci = vi.getCodecSetup();
        PrivateState b = this.backEndState;
        int hs = ci.halfrateFlag;
        int i, j;

        if (vb == null) return -131;
        if (this.pcm_current > this.pcm_returned && this.pcm_returned != -1) return -131;

        this.lW = this.W;
        this.W = vb.W;
        this.nW = -1;

        if ((this.sequence == -1) || (this.sequence + 1 != vb.sequence)) {
            this.granulepos = -1; // out of sequence; lose count
            b.sample_count = -1;
        }

        this.sequence = vb.sequence;

        if (vb.pcm != null) {
            // no pcm to process if vorbis_synthesis_trackonly
            // was called on block
            int n = ci.blocksizes[this.W] >> (hs + 1);
            int n0 = ci.blocksizes[0] >> (hs + 1);
            int n1 = ci.blocksizes[1] >> (hs + 1);

            int thisCenter;
            int prevCenter;

            this.glue_bits += vb.glue_bits;
            this.time_bits += vb.time_bits;
            this.floor_bits += vb.floor_bits;
            this.res_bits += vb.res_bits;

            if (this.centerW != 0) {
                thisCenter = n1;
                prevCenter = 0;
            } else {
                thisCenter = 0;
                prevCenter = n1;
            }

            // this.pcm is now used like a two-stage double buffer.  We don't want
            // to have to constantly shift *or* adjust memory usage.  Don't
            // accept a new block until the old is shifted out */

            for (j = 0; j < vi.channels; j++) {
                // the overlap/add section
                if (this.lW != 0) {
                    if (this.W != 0) {
                        // large/large
                        float[] w = Window.getWindow(b.window[1] - hs);
                        int pcm = prevCenter; // this.pcm[j]
                        float[] p = vb.pcm[j];
                        for (i = 0; i < n1; i++)
                            this.pcm[j][pcm + i] = this.pcm[j][pcm + i] * w[n1 - i - 1] + p[i] * w[i];
                    } else {
                        // large/small
                        float[] w = Window.getWindow(b.window[0] - hs);
                        int pcm = prevCenter + n1 / 2 - n0 / 2; // this.pcm[j]
                        float[] p = vb.pcm[j];
                        for (i = 0; i < n0; i++)
                            this.pcm[j][pcm + i] = this.pcm[j][pcm + i] * w[n0 - i - 1] + p[i] * w[i];
                    }
                } else {
                    if (this.W != 0) {
                        // small/large
                        float[] w = Window.getWindow(b.window[0] - hs);
                        int pcm = prevCenter; // this.pcm[j]
                        int p = n1 / 2 - n0 / 2; // vb.pcm[j]
                        for (i = 0; i < n0; i++)
                            this.pcm[j][pcm + i] = this.pcm[j][pcm + i] * w[n0 - i - 1] + vb.pcm[j][p + i] * w[i];
                        for (; i < n1 / 2 + n0 / 2; i++)
                            this.pcm[j][pcm + i] = vb.pcm[j][p + i];
                    } else {
                        // small/small
                        float[] w = Window.getWindow(b.window[0] - hs);
                        int pcm = prevCenter; // this.pcm[j]
                        float[] p = vb.pcm[j];
                        for (i = 0; i < n0; i++)
                            this.pcm[j][pcm + i] = this.pcm[j][pcm + i] * w[n0 - i - 1] + p[i] * w[i];
                    }
                }

                // the copy section
                {
                    int pcm = thisCenter; // this.pcm[j]
                    int p = n; // vb.pcm[j]
                    for (i = 0; i < n; i++)
                        this.pcm[j][pcm + i] = vb.pcm[j][p + i];
                }
            }

            if (this.centerW != 0)
                this.centerW = 0;
            else
                this.centerW = n1;

            // deal with initial packet state; we do this using the explicit
            // pcm_returned==-1 flag otherwise we're sensitive to first block
            // being short or long

            if (this.pcm_returned == -1) {
                this.pcm_returned = thisCenter;
                this.pcm_current = thisCenter;
            } else {
                this.pcm_returned = prevCenter;
                this.pcm_current = prevCenter +
                        ((ci.blocksizes[this.lW] / 4 +
                                ci.blocksizes[this.W] / 4) >> hs);
            }
        }

        // track the frame number... This is for convenience, but also
        // making sure our last packet doesn't end with added padding.  If
        // the last packet is partial, the number of samples we'll have to
        // return will be past the vb.granulepos.
        //
        // This is not foolproof!  It will be confused if we begin
        // decoding at the last page after a seek or hole.  In that case,
        // we don't have a starting point to judge where the last frame
        // is.  For this reason, vorbisfile will always try to make sure
        // it reads the last two marked pages in proper sequence

        if (b.sample_count == -1) {
            b.sample_count = 0;
        } else {
            b.sample_count += ci.blocksizes[this.lW] / 4 + ci.blocksizes[this.W] / 4;
        }

        if (this.granulepos == -1) {
            if (vb.granulePos != -1) { /* only set if we have a position to set to */

                this.granulepos = vb.granulePos;

                // is this a short page?
                if (b.sample_count > this.granulepos) {
                    // corner case; if this is both the first and last audio page,
                    // then spec says the end is cut, not beginning
                    long extra = b.sample_count - vb.granulePos;

                    // we use ogg_int64_t for granule positions because a
                    // uint64 isn't universally available.  Unfortunately,
                    // that means granposes can be 'negative' and result in
                    // extra being negative
                    if (extra < 0)
                        extra = 0;

                    if (vb.eofFlag != 0) {
                        // trim the end
                        // no preceding granulepos; assume we started at zero (we'd
                        // have to in a short single-page stream)
                        // granulepos could be -1 due to a seek, but that would result
                        // in a long count, not short count

                        // Guard against corrupt/malicious frames that set EOP and
                        // a backdated granpos; don't rewind more samples than we
                        // actually have
                        if (extra > (long) (this.pcm_current - this.pcm_returned) << hs)
                            extra = (long) (this.pcm_current - this.pcm_returned) << hs;

                        this.pcm_current = (int) (this.pcm_current - extra >> hs);
                    } else {
                        // trim the beginning
                        this.pcm_returned = (int) (this.pcm_returned + extra >> hs);
                        if (this.pcm_returned > this.pcm_current)
                            this.pcm_returned = this.pcm_current;
                    }
                }
            }
        } else {
            this.granulepos += ci.blocksizes[this.lW] / 4 + ci.blocksizes[this.W] / 4;
            if (vb.granulePos != -1 && this.granulepos != vb.granulePos) {

                if (this.granulepos > vb.granulePos) {
                    long extra = this.granulepos - vb.granulePos;

                    if (extra != 0)
                        if (vb.eofFlag != 0) {
                            // partial last frame.  Strip the extra samples off

                            // Guard against corrupt/malicious frames that set EOP and
                            // a backdated granpos; don't rewind more samples than we
                            // actually have
                            if (extra > (long) (this.pcm_current - this.pcm_returned) << hs)
                                extra = (long) (this.pcm_current - this.pcm_returned) << hs;

                            // we use ogg_int64_t for granule positions because a
                            // uint64 isn't universally available.  Unfortunately,
                            // that means granposes can be 'negative' and result in
                            // extra being negative
                            if (extra < 0)
                                extra = 0;

                            this.pcm_current -= (int) (extra >> hs);
                        }
                    // else {Shouldn't happen *unless* the bitstream is out of
                    // spec.  Either way, believe the bitstream }
                }
                // else {Shouldn't happen *unless* the bitstream is out of
                // spec.  Either way, believe the bitstream }
                this.granulepos = vb.granulePos;
            }
        }

        // Update, cleanup

        if (vb.eofFlag != 0) this.eofflag = 1;
        return 0;
    }

    /** pcm==NULL indicates we just want the pending samples, no more */
    public int pcmOut(float[][][] pcm) {
        Info vi = this.vi;

        float[][] pcmret = new float[vi.channels][];
        if (this.pcm_returned > -1 && this.pcm_returned < this.pcm_current) {
            if (pcm[0] != null) {
                int i;
                for (i = 0; i < vi.channels; i++) {
                    int l = pcmret[i].length - this.pcm_returned;
                    pcmret[i] = new float[l];
                    System.arraycopy(this.pcm[i], this.pcm_returned, pcmret[i], 0, l);
                }
                pcm[0] = pcmret;
            }
            return this.pcm_current - this.pcm_returned;
        }
        return 0;
    }

    public int read(int n) {
        if (n != 0 && this.pcm_returned + n > this.pcm_current) return -131;
        this.pcm_returned += n;
        return 0;
    }

    /**
     * intended for use with a specific vorbisfile feature; we want access
     * to the [usually synthetic/postextrapolated] buffer and lapping at
     * the end of a decode cycle, specifically, a half-short-block worth.
     * This funtion works like pcmout above, except it will also expose
     * this implicit buffer data not normally decoded.
     */
    public int lapout(float[][][] pcm) {
        Info vi = this.vi;
        CodecSetupInfo ci = vi.getCodecSetup();
        int hs = ci.halfrateFlag;

        int n = ci.blocksizes[this.W] >> (hs + 1);
        int n0 = ci.blocksizes[0] >> (hs + 1);
        int n1 = ci.blocksizes[1] >> (hs + 1);
        int i, j;

        if (this.pcm_returned < 0) return 0;

        // our returned data ends at pcm_returned; because the synthesis pcm
        // buffer is a two-fragment ring, that means our data block may be
        // fragmented by buffering, wrapping or a short block not filling
        // out a buffer.  To simplify things, we unfragment if it's at all
        // possibly needed. Otherwise, we'd need to call lapout more than
        // once as well as hold additional dsp state.  Opt for
        // simplicity.

        // centerW was advanced by blockin; it would be the center of the
        // *next* block
        if (this.centerW == n1) {
            // the data buffer wraps; swap the halves
            // slow, sure, small
            for (j = 0; j < vi.channels; j++) {
                float[] p = this.pcm[j];
                for (i = 0; i < n1; i++) {
                    float temp = p[i];
                    p[i] = p[i + n1];
                    p[i + n1] = temp;
                }
            }

            this.pcm_current -= n1;
            this.pcm_returned -= n1;
            this.centerW = 0;
        }

        // solidify buffer into contiguous space
        if ((this.lW ^ this.W) == 1) {
            // long/short or short/long
            for (j = 0; j < vi.channels; j++) {
                float[] s = this.pcm[j];
                int d = (n1 - n0) / 2; // this.pcm[j]
                for (i = (n1 + n0) / 2 - 1; i >= 0; --i)
                    s[d + i] = s[i];
            }
            this.pcm_returned += (n1 - n0) / 2;
            this.pcm_current += (n1 - n0) / 2;
        } else {
            if (this.lW == 0) {
                // short/short
                for (j = 0; j < vi.channels; j++) {
                    float[] s = this.pcm[j];
                    int d = n1 - n0; // this.pcm[j]
                    for (i = n0 - 1; i >= 0; --i)
                        s[d + i] = s[i];
                }
                this.pcm_returned += n1 - n0;
                this.pcm_current += n1 - n0;
            }
        }

        float[][] pcmret = new float[vi.channels][];
        if (pcm[0] != null) {
            for (i = 0; i < vi.channels; i++) {
                int l = pcmret[i].length - this.pcm_returned;
                pcmret[i] = new float[l];
                System.arraycopy(this.pcm[i], this.pcm_returned, pcmret[i], 0, l);
            }
            pcm[0] = pcmret;
        }

        return (n1 + n - this.pcm_returned);
    }
}
