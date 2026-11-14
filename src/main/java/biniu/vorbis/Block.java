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
public class Block {

    private static final int BLOCKTYPE_IMPULSE = 0;
    private static final int BLOCKTYPE_PADDING = 1;
    private static final int BLOCKTYPE_TRANSITION = 0;
    private static final int BLOCKTYPE_LONG = 1;

    // necessary stream state for linking to the framing abstraction
    public float[][] pcm = new float[0][]; // this is a pointer into local storage
    public final Buffer opb = new Buffer();

    public int lW;
    public int W;
    public int nW;
    public int pcmEnd;
    public int mode;

    public int eofFlag;
    public long granulePos;
    public long sequence;
    public DspState dspState; // For read-only access of configuration

    // local storage to avoid remallocing; it's up to the mapping to
    // structure it
    private int localTop;
    private int localAlloc;
    private int totalUse;
    private AllocChain reap;

    // bitmetrics for the frame
    public int glue_bits;
    public int time_bits;
    public int floor_bits;
    public int res_bits;

    public BlockInternal blockInternal;
    private Info vi;
    private CodecSetupInfo ci;

    public Block(DspState vd) {
        this.dspState = vd;
        if (vd.analysisp != 0) {
            opb.writeInit();
        }
    }

    public void init(DspState vd) {
        this.dspState = vd;
        this.vi = dspState.vi;
        this.ci = vi.getCodecSetup();

    }

    public int blockInit(DspState vd) {
        int i;
        this.dspState = vd;
        this.vi = dspState.vi;
        this.ci = vi.getCodecSetup();
        this.localAlloc = 0;

        if (vd.analysisp != 0) {
            this.blockInternal = new BlockInternal();
            blockInternal.ampmax = -9999;
            for (i = 0; i < Const.PACKETBLOBS; i++) {
                if (i == Const.PACKETBLOBS / 2) {
                    blockInternal.packetblob[i] = this.opb;
                } else {
                    blockInternal.packetblob[i] = new Buffer();
                }
                blockInternal.packetblob[i].writeInit();
            }
        }
        return 0;
    }

    public boolean bitrateManaged() {

        BitrateManagerState bm = dspState.backEndState.bms;

        if (bm.equals(bm.managed)) return true;
        return false;
    }

    /**
     * do the deltas, envelope shaping, pre-echo and determine the size of
     * the next block on which to continue analysis
     */
    public boolean analysisBlockOut() {
        int i;
        Info vi = dspState.vi;
        CodecSetupInfo ci = vi.getCodecSetup();
        PrivateState b = dspState.backEndState;
        LookPsyGlobal g = b.psyGlobLook;
        int beginW = dspState.centerW - ci.blocksizes[dspState.W] / 2, centerNext;

        /* check to see if we're started... */
        if (dspState.preextrapolate == 0) return false;

        /* check to see if we're done... */
        if (dspState.eofflag == -1) return false;

    /* By our invariant, we have lW, W and centerW set.  Search for
       the next boundary so we can determine nW (the next window size)
       which lets us compute the shape of the current block's window */

    /* we do an envelope search even on a single blocksize; we may still
       be throwing more bits at impulses, and envelope search handles
       marking impulses too. */
        {
            int bp = dspState.envelopeSearch();
            if (bp == -1) {

                if (dspState.eofflag == 0) return false; /* not enough data currently to search for a
                                       full long block */
                dspState.nW = 0;
            } else {

                if (ci.blocksizes[0] == ci.blocksizes[1])
                    dspState.nW = 0;
                else
                    dspState.nW = bp;
            }
        }

        centerNext = dspState.centerW + (ci.blocksizes[dspState.W] / 4) + (ci.blocksizes[dspState.nW] / 4);

        {
            // center of next block + next block maximum right side.

            long blockbound = centerNext + ci.blocksizes[dspState.nW] / 2;
            if (dspState.pcm_current < blockbound)
                // not enough data yet;
                // although this check is less strict that the _ve_envelope_search,
                // the search is not run if we only use one block size
                return false;

        }

        // fill in the block.  Note that for a short window, lW and nW are *short*
        // regardless of actual settings in the stream

        blockRipcord();
        this.lW = dspState.lW;
        this.W = dspState.W;
        this.nW = dspState.nW;

        if (dspState.W != 0) {
            if (dspState.lW == 0 || dspState.nW == 0) {
                blockInternal.blocktype = BLOCKTYPE_TRANSITION;
            } else {
                blockInternal.blocktype = BLOCKTYPE_LONG;
            }
        } else {
            if (dspState.envelopeMark() != 0) {
                blockInternal.blocktype = BLOCKTYPE_IMPULSE;
            } else {
                blockInternal.blocktype = BLOCKTYPE_PADDING;
            }
        }

//    this.dspState=dspState;
        this.sequence = dspState.sequence++;
        this.granulePos = dspState.granulepos;
        this.pcmEnd = ci.blocksizes[dspState.W];

        /* copy the vectors; this uses the local storage in vb */

        /* this tracks 'strongest peak' for later psychoacoustics */
        /* moved to the global psy state; clean this mess up */
        if (blockInternal.ampmax > g.ampmax) g.ampmax = blockInternal.ampmax;
        g.ampmax = dspState.ampMaxDecay(g.ampmax);
        blockInternal.ampmax = g.ampmax;

        this.pcm = new float[vi.channels][];
        blockInternal.pcmdelay = new float[vi.channels][];
        for (i = 0; i < vi.channels; i++) {
            blockInternal.pcmdelay[i] = new float[this.pcmEnd + beginW];
            System.arraycopy(dspState.pcm[i], 0, blockInternal.pcmdelay[i], 0, (this.pcmEnd + beginW));
            this.pcm[i] = new float[blockInternal.pcmdelay[i].length - beginW];
            System.arraycopy(blockInternal.pcmdelay[i], beginW, this.pcm[i], 0, this.pcm[i].length);

        }

    /* handle eof detection: eof==0 means that we've not yet received EOF
                             eof>0  marks the last 'real' sample in pcm[]
                             eof<0  'no more to do'; doesn't get here */

        if (dspState.eofflag != 0) {
            if (dspState.centerW >= dspState.eofflag) {
                dspState.eofflag = -1;
                this.eofFlag = 1;
                return true;
            }
        }

        /* advance storage vectors and clean up */
        {
            int new_centerNext = ci.blocksizes[1] / 2;
            int movementW = centerNext - new_centerNext;

            if (movementW > 0) {

                b.enveLook.envelopeShift(movementW);
                dspState.pcm_current -= movementW;

                for (i = 0; i < vi.channels; i++) {
                    System.arraycopy(dspState.pcm[i], movementW, dspState.pcm[i], 0, dspState.pcm_current);
                }

                dspState.lW = dspState.W;
                dspState.W = dspState.nW;
                dspState.centerW = new_centerNext;

                if (dspState.eofflag != 0) {
                    dspState.eofflag -= movementW;
                    if (dspState.eofflag <= 0) dspState.eofflag = -1;
                    /* do not add padding to end of stream! */
                    if (dspState.centerW >= dspState.eofflag) {
                        dspState.granulepos += movementW - (dspState.centerW - dspState.eofflag);
                    } else {
                        dspState.granulepos += movementW;
                    }
                } else {
                    dspState.granulepos += movementW;
                }
            }
        }

        /* done */
        return true;
    }

    public int blockClear() {
        int i;

        blockRipcord();

        if (blockInternal != null) {
            for (i = 0; i < Const.PACKETBLOBS; i++) {
                blockInternal.packetblob[i].writeClear();
                if (i != Const.PACKETBLOBS / 2) {
                    blockInternal.packetblob[i] = null;
                }
            }
            blockInternal = null;
        }
        return 0;
    }

    public int clear() {
        if (dspState != null) {
            if (dspState.analysisp != 0) {
                opb.writeClear();
            }
        }
        return 0;
    }

    /** decides between modes, dispatches to the appropriate mapping. */
    public int analysis(Packet op) {
        int ret, i;

        this.glue_bits = 0;
        this.time_bits = 0;
        this.floor_bits = 0;
        this.res_bits = 0;

        // first things first.  Make sure encode is ready
        for (i = 0; i < Const.PACKETBLOBS; i++)
            blockInternal.packetblob[i].reset();

        // we only have one mapping type (0), and we let the mapping code
        // itself figure out what soft mode to use.  This allows easier
        // bitrate management
        if ((ret = FuncMapping.mapping_P[0].mapping0Forward(this)) != 0)
            return ret;

        if (op != null) {
            if (bitrateManaged()) {
                // The app is using a bitmanaged mode... but not using the
                // bitrate management interface.
                return Const.OV_EINVAL;
            }

            op.b_o_s = false;
            op.e_o_s = this.eofFlag != 0;
            op.granulePos = this.granulePos;
            op.packetNo = this.sequence; // for sake of completeness
        }
        return 0;
    }

    /** finish taking in the block we just processed */
    public int bitrateAddBlock() {
        BitrateManagerState bm = dspState.backEndState.bms;
        BitrateManagerInfo bi = ci.biManInfo;

        int choice = (int) Math.rint(bm.avgfloat);
        long this_bits = blockInternal.packetblob[choice].getBytes() * 8L;
        long min_target_bits = (this.W != 0 ? (long) bm.min_bitsper * bm.short_per_long : bm.min_bitsper);
        int max_target_bits = (this.W != 0 ? bm.max_bitsper * bm.short_per_long : bm.max_bitsper);
        int samples = ci.blocksizes[this.W] >> 1;
        int desired_fill = (int) (bi.reservoir_bits * bi.reservoir_bias);
        if (bm.managed == null) {
            // not a bitrate managed stream, but for API simplicity, we'll
            // buffer the packet to keep the code path clean

            if (bm.vb != null)
                // one has been submitted without
                // being claimed
                return -1;
            bm.vb = this;
            return 0;
        }

        bm.vb = this;

        // look ahead for avg floater
        if (bm.avg_bitsper > 0) {
            double slew = 0.;
            long avg_target_bits = (this.W != 0 ? (long) bm.avg_bitsper * bm.short_per_long : bm.avg_bitsper);
            double slewlimit = 15. / bi.slew_damp;

            // choosing a new floater:
            // if we're over target, we slew down
            // if we're under target, we slew up
            //
            // choose slew as follows: look through packetblobs of this frame
            // and set slew as the first in the appropriate direction that
            // gives us the slew we want.  This may mean no slew if delta is
            // already favorable.
            //
            // Then limit slew to slew max

            if (bm.avg_reservoir + (this_bits - avg_target_bits) > desired_fill) {
                while (choice > 0 && this_bits > avg_target_bits &&
                        bm.avg_reservoir + (this_bits - avg_target_bits) > desired_fill) {
                    choice--;
                    this_bits = blockInternal.packetblob[choice].getBytes() * 8L;
                }
            } else if (bm.avg_reservoir + (this_bits - avg_target_bits) < desired_fill) {
                while (choice + 1 < Const.PACKETBLOBS && this_bits < avg_target_bits &&
                        bm.avg_reservoir + (this_bits - avg_target_bits) < desired_fill) {
                    choice++;
                    this_bits = blockInternal.packetblob[choice].getBytes() * 8L;
                }
            }

            slew = Math.rint(choice - bm.avgfloat) / samples * vi.rate;
            if (slew < -slewlimit) slew = -slewlimit;
            if (slew > slewlimit) slew = slewlimit;
            choice = (int) Math.rint(bm.avgfloat += slew / vi.rate * samples);
            this_bits = blockInternal.packetblob[choice].getBytes() * 8L;
        }


        // enforce min(if used) on the current floater (if used)
        if (bm.min_bitsper > 0) {
            // do we need to force the bitrate up?
            if (this_bits < min_target_bits) {
                while (bm.minmax_reservoir - (min_target_bits - this_bits) < 0) {
                    choice++;
                    if (choice >= Const.PACKETBLOBS) break;
                    this_bits = blockInternal.packetblob[choice].getBytes() * 8L;
                }
            }
        }

        // enforce max (if used) on the current floater (if used)
        if (bm.max_bitsper > 0) {
            // do we need to force the bitrate down?
            if (this_bits > max_target_bits) {
                while (bm.minmax_reservoir + (this_bits - max_target_bits) > bi.reservoir_bits) {
                    choice--;
                    if (choice < 0) break;
                    this_bits = blockInternal.packetblob[choice].getBytes() * 8L;
                }
            }
        }

        // Choice of packetblobs now made based on floater, and min/max
        // requirements. Now boundary check extreme choices

        if (choice < 0) {
            // choosing a smaller packetblob is insufficient to trim bitrate.
            // frame will need to be truncated
            int maxsize = (max_target_bits + (bi.reservoir_bits - bm.minmax_reservoir)) / 8;
            bm.choice = choice = 0;

            if (blockInternal.packetblob[choice].getBytes() > maxsize) {
                blockInternal.packetblob[choice].writetrUnc(maxsize * 8);
                this_bits = blockInternal.packetblob[choice].getBytes() * 8L;
            }
        } else {
            long minsize = (min_target_bits - bm.minmax_reservoir + 7) / 8;
            if (choice >= Const.PACKETBLOBS)
                choice = Const.PACKETBLOBS - 1;

            bm.choice = choice;

            // prop up bitrate according to demand. pad this frame out with zeroes
            minsize -= blockInternal.packetblob[choice].getBytes();
            while ((minsize - .0) != 0) blockInternal.packetblob[choice].write(0, 8);
            this_bits = blockInternal.packetblob[choice].getBytes() * 8L;

        }

        // now we have the final packet and the final packet size.  Update statistics
        // min and max reservoir
        if (bm.min_bitsper > 0 || bm.max_bitsper > 0) {

            if (max_target_bits > 0 && this_bits > max_target_bits) {
                bm.minmax_reservoir = (int) (bm.minmax_reservoir + (this_bits - max_target_bits));
            } else if (min_target_bits > 0 && this_bits < min_target_bits) {
                bm.minmax_reservoir = (int) (bm.minmax_reservoir + (this_bits - min_target_bits));
            } else {
                // inbetween; we want to take reservoir toward but not past desired_fill
                if (bm.minmax_reservoir > desired_fill) {
                    if (max_target_bits > 0) { // logical bulletproofing against initialization state
                        bm.minmax_reservoir = (int) (bm.minmax_reservoir + (this_bits - max_target_bits));
                        if (bm.minmax_reservoir < desired_fill) bm.minmax_reservoir = desired_fill;
                    } else {
                        bm.minmax_reservoir = desired_fill;
                    }
                } else {
                    if (min_target_bits > 0) { // logical bulletproofing against initialization state
                        bm.minmax_reservoir = (int) (bm.minmax_reservoir + (this_bits - min_target_bits));
                        if (bm.minmax_reservoir > desired_fill) bm.minmax_reservoir = desired_fill;
                    } else {
                        bm.minmax_reservoir = desired_fill;
                    }
                }
            }
        }

        // avg reservoir
        if (bm.avg_bitsper > 0) {
            long avg_target_bits = (this.W != 0 ? (long) bm.avg_bitsper * bm.short_per_long : bm.avg_bitsper);
            bm.avg_reservoir = (int) (bm.avg_reservoir + this_bits - avg_target_bits);
        }

        return 0;
    }

    /** reap the chain, pull the ripcord */
    private void blockRipcord() {
        // reap the chain
        while (this.reap != null) {
            AllocChain next = reap.next;
            reap.ptr = null;
            reap = next;
        }
        // consolidate storage
        if (this.totalUse != 0) {
            this.localAlloc += this.totalUse;
            this.totalUse = 0;
        }

        // pull the ripcord
        this.localTop = 0;
        this.reap = null;
    }
}
