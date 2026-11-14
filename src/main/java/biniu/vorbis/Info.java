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

import java.util.Arrays;

import biniu.ogg.Buffer;
import biniu.ogg.Packet;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Info {

    private static final int OV_EBADPACKET = -136;
    private static final int OV_ENOTAUDIO = -135;

    private static final byte[] _vorbis = "vorbis".getBytes();
    private static final int VI_TIMEB = 1;
    private static final int VI_FLOORB = 2;
    private static final int VI_RESB = 3;
    private static final int VI_MAPB = 1;
    private static final int VI_WINDOWB = 1;

    public int version;
    public int channels;
    public int rate;

    // The below bitrate declarations are *hints*.
    // Combinations of the three values carry the following implications:
    //
    // all three set to the same value:
    // implies a fixed rate bitstream
    // only nominal set:
    // implies a VBR stream that averages the nominal bitrate.  No hard
    // upper/lower limit
    // upper and or lower set:
    // implies a VBR bitstream that obeys the bitrate limits. nominal
    // may also be set to give a nominal rate.
    // none set:
    //  the coder does not care to speculate.

    int bitrate_upper;
    int bitrate_nominal;
    int bitrate_lower;
    int bitrate_window;

    // Vorbis supports only short and long blocks, but allows the
    // encoder to choose the sizes

    private CodecSetupInfo codecSetup;

    final int[] blocksizes = new int[2];

    // modes are the primary means of supporting on-the-fly different
    // blocksizes, different channel mappings (LR or mid-side),
    // different residue backends, etc.  Each mode consists of a
    // blocksize flag and a mapping (along with the mapping setup

    int modes;
    int maps;
    int times;
    int floors;
    int residues;
    int books;
    int psys; // encode only

    InfoMode[] mode_param = null;

    final int[] map_type = null;
    Object[] map_param = null;

    final int[] time_type = null;
    Object[] time_param = null;

    final int[] floor_type = null;
    Object[] floor_param = null;

    final int[] residue_type = null;
    LookResidue[] residue_param = null;

    StaticCodeBook[] book_param = null;

    CodeBook[] fullbooks;

    InfoPsyGlobal psy_g_param;

    BitrateManagerInfo bi;
    HighlevelEncodeSetup hi; /* used only by vorbisenc.c.  It's a
       highly redundant structure, but
       improves clarity of program flow. */
    int halfrate_flag; /* painless downsample for decode */

    final PsyInfo[] psy_param = new PsyInfo[64]; // encode only

    // for block long/sort tuning; encode only
    int envelopesa;
    float preecho_thresh;
    float preecho_clamp;

    public Info() {
    }

    // used by synthesis, which has a full, alloced vi
    public void init() {
        rate = 0;
        this.codecSetup = new CodecSetupInfo();
    }

    public LookPsyGlobal getLookPsyGlobal() {

        InfoPsyGlobal gi = codecSetup.psyGlobParam;
        LookPsyGlobal look = new LookPsyGlobal();

        look.channels = this.channels;

        look.ampmax = -9999.f;
        look.gi = gi;
        return look;
    }

    public void clear() {
        for (int i = 0; i < modes; i++) {
            mode_param[i] = null;
        }
        mode_param = null;

        for (int i = 0; i < maps; i++) { // unpack does the range checking
            FuncMapping.mapping_P[map_type[i]].clear(map_param[i]);
        }
        map_param = null;

        for (int i = 0; i < times; i++) { // unpack does the range checking
            FuncTime.time_P[time_type[i]].free_info(time_param[i]);
        }
        time_param = null;

        for (int i = 0; i < floors; i++) { // unpack does the range checking
            FuncFloor.floor_P[floor_type[i]].clear(floor_param[i]);
        }
        floor_param = null;

        for (int i = 0; i < residues; i++) { // unpack does the range checking
            FuncResidue.residue_P[residue_type[i]].clear(residue_param[i]);
        }
        residue_param = null;

        // the static codebooks *are* freed if you call info_clear, because
        // decode side does alloc a 'static' codebook. Calling clear on the
        // full codebook does not clear the static codebook (that's our
        // responsibility)
        for (int i = 0; i < books; i++) {
            // just in case the decoder pre-cleared to save space
            if (book_param[i] != null) {
                book_param[i].clear();
                book_param[i] = null;
            }
        }
        book_param = null;

        for (int i = 0; i < psys; i++) {
            psy_param[i].free();
        }
    }

    // Header packing/unpacking
    int unpack_info(Buffer opb) {

        version = opb.read(32);
        if (version != 0)
            return -1;

        channels = opb.read(8);
        rate = opb.read(32);

        bitrate_upper = opb.read(32);
        bitrate_nominal = opb.read(32);
        bitrate_lower = opb.read(32);

        codecSetup.blocksizes[0] = 1 << opb.read(4);
        codecSetup.blocksizes[1] = 1 << opb.read(4);

        if ((rate < 1) ||
                (channels < 1) ||
                (codecSetup.blocksizes[0] < 8) ||
                (codecSetup.blocksizes[1] < blocksizes[0]) ||
                (opb.read(1) != 1)) {
            clear();
            return -1;
        }
        return 0;
    }

    private static int vorbis_unpack_comment(Comment vc, Buffer opb) {
        int vendorlen = opb.read(32);
        if (vendorlen < 0) {
            vc.clear();
            return -133;
        }
        if (vendorlen > opb.storage - 8) {
            vc.clear();
            return -133;
        }
        vc.vendor = new byte[vendorlen + 1];
        opb.read(vc.vendor, vendorlen);
        int i = opb.read(32);
        if (i < 0) {
            vc.clear();
            return -133;
        }
        if (i > ((opb.storage - opb.getBytes()) >> 2)) {
            vc.clear();
            return -133;
        }
        vc.comments = i;
        vc.user_comments = new byte[vc.comments + 1][vc.user_comments[0].length];
        vc.comment_lengths = new int[vc.comments + 1];

        for (i = 0; i < vc.comments; i++) {
            int len = opb.read(32);
            if (len < 0) {
                vc.clear();
                return -133;
            }
            if (len > opb.storage - opb.getBytes()) {
                vc.clear();
                return -133;
            }
            vc.comment_lengths[i] = len;
            vc.user_comments[i] = new byte[len + 1];
            opb.read(vc.user_comments[i], len);
        }
        if (opb.read(1) != 1) { // EOP check
            vc.clear();
            return -133;
        }

        return 0;
    }

    /**
     * all of the real encoding details are here.  The modes, books,
     * everything
     */
    private static int vorbis_unpack_books(Info vi, Buffer opb) {
        CodecSetupInfo ci = vi.getCodecSetup();
        int i;

        // codebooks
        ci.books = opb.read(8) + 1;
        if (ci.books <= 0) {
            vi.clear();
            return -133;
        }
        for (i = 0; i < ci.books; i++) {
            StaticCodeBook scb = new StaticCodeBook();
            int r = scb.unpack(opb);
            if (r < 0) {
                vi.clear();
                return -133;
            }
            ci.bookParam[i] = scb;
        }

        // time backend settings; hooks are unused
        {
            int times = opb.read(6) + 1;
            if (times <= 0) {
                vi.clear();
                return -133;
            }
            for (i = 0; i < times; i++) {
                int test = opb.read(16);
                if (test < 0 || test >= VI_TIMEB) {
                    vi.clear();
                    return -133;
                }
            }
        }

        // floor backend settings
        ci.floors = opb.read(6) + 1;
        if (ci.floors <= 0) {
            vi.clear();
            return -133;
        }
        for (i = 0; i < ci.floors; i++) {
            ci.floorType[i] = opb.read(16);
            if (ci.floorType[i] < 0 || ci.floorType[i] >= VI_FLOORB) {
                vi.clear();
                return -133;
            }
            ci.floorParam[i] = (InfoFloor1) FuncFloor.floor_P[ci.floorType[i]].unpack(vi, opb);
            if (ci.floorParam[i] == null) {
                vi.clear();
                return -133;
            }
        }

        // residue backend settings
        ci.residues = opb.read(6) + 1;
        if (ci.residues <= 0) {
            vi.clear();
            return -133;
        }
        for (i = 0; i < ci.residues; i++) {
            ci.residueType[i] = opb.read(16);
            if (ci.residueType[i] < 0 || ci.residueType[i] >= VI_RESB) {
                vi.clear();
                return -133;
            }
            ci.residueParam[i] = (InfoResidue0) FuncResidue.residue_P[ci.residueType[i]].unpack(vi, opb);
            if (ci.residueParam[i] == null) {
                vi.clear();
                return -133;
            }
        }

        // map backend settings
        ci.maps = opb.read(6) + 1;
        if (ci.maps <= 0) {
            vi.clear();
            return -133;
        }
        for (i = 0; i < ci.maps; i++) {
            ci.mapType[i] = opb.read(16);
            if (ci.mapType[i] < 0 || ci.mapType[i] >= VI_MAPB) {
                vi.clear();
                return -133;
            }
            ci.mapParam[i] = (InfoMapping0) FuncMapping.mapping_P[ci.mapType[i]].unpack(vi, opb);
            if (ci.mapParam[i] == null) {
                vi.clear();
                return -133;
            }
        }

        // mode settings
        ci.modes = opb.read(6) + 1;
        if (ci.modes <= 0) {
            vi.clear();
            return -133;
        }
        for (i = 0; i < ci.modes; i++) {
            ci.modeParam[i] = new InfoMode();
            ci.modeParam[i].blockflag = opb.read(1);
            ci.modeParam[i].windowtype = opb.read(16);
            ci.modeParam[i].transformtype = opb.read(16);
            ci.modeParam[i].mapping = opb.read(8);

            if (ci.modeParam[i].windowtype >= VI_WINDOWB) {
                vi.clear();
                return -133;
            }
            if (ci.modeParam[i].transformtype >= VI_WINDOWB) {
                vi.clear();
                return -133;
            }
            if (ci.modeParam[i].mapping >= ci.maps) {
                vi.clear();
                return -133;
            }
            if (ci.modeParam[i].mapping < 0) {
                vi.clear();
                return -133;
            }
        }

        if (opb.read(1) != 1) { // top level EOP check
            vi.clear();
            return -133;
        }

        return 0;
    }

    /**
     * The Vorbis header is in three packets; the initial small packet in
     * the first page that identifies basic parameters, a second packet
     * with bitstream comments and a third packet that holds the
     * codebook.
     */
    public int headerIn(Comment vc, Packet op) {
        Buffer opb = new Buffer();

        if (op != null) {
            opb.readInit(op.packetByte, 0, op.bytes);

            // Which of the three types of header is this?
            // Also verify header-ness, vorbis
            {
                byte[] buffer = new byte[6];
                int packtype = opb.read(8);
                Arrays.fill(buffer, 0, 6, (byte) 0);
                opb.read(buffer, 6);
                if (!Arrays.equals(buffer, "vorbis".getBytes())) {
                    // not a vorbis header
                    return -132;
                }
                switch (packtype) {
                case 0x01: // least significant *bit* is read first
                    if (!op.b_o_s) {
                        // Not the initial packet
                        return -133;
                    }
                    if (this.rate != 0) {
                        // previously initialized info header
                        return -133;
                    }

                    return this.unpack_info(opb);

                case 0x03: // least significant *bit* is read first
                    if (this.rate == 0) {
                        // um... we didn't get the initial header
                        return -133;
                    }
                    if (vc.vendor != null) {
                        // previously initialized comment header
                        return -133;
                    }

                    return vorbis_unpack_comment(vc, opb);

                case 0x05: // least significant *bit* is read first
                    if (this.rate == 0 || vc.vendor == null) {
                        // um... we didn;t get the initial header or comments yet
                        return -133;
                    }
                    if (this.getCodecSetup() == null) {
                        // improperly initialized vorbis_info
                        return -129;
                    }
                    if (this.getCodecSetup().books > 0) {
                        // previously initialized setup header
                        return -133;
                    }

                    return vorbis_unpack_books(this, opb);

                default:
                    // Not a valid vorbis header type
                    return -133;
                }
            }
        }
        return -133;
    }

    public boolean packBooks(Buffer opb) {

        if (codecSetup == null)
            return false;

        // preamble
        opb.write(0x05, 8);
        opb.write(_vorbis);
        // books
        opb.write(codecSetup.books - 1, 8);
        for (int i = 0; i < codecSetup.books; i++) {
            if (codecSetup.bookParam[i].pack(opb) != 0) {
                return false;
            }
        }

        // times; hook placeholders
        opb.write(0, 6);
        opb.write(0, 16);

        // floors
        opb.write(codecSetup.floors - 1, 6);
        for (int i = 0; i < codecSetup.floors; i++) {
            opb.write(codecSetup.floorType[i], 16);
//	          if (FuncFloor.floor_P[codecSetup.floorType[i]].pack) {
            if (codecSetup.floorType[i] != 0) {
                FuncFloor.floor_P[codecSetup.floorType[i]].pack(codecSetup.floorParam[i], opb);
            } else {
                return false;
            }
        }

        // residues
        opb.write(codecSetup.residues - 1, 6);
        for (int i = 0; i < codecSetup.residues; i++) {
            opb.write(codecSetup.residueType[i], 16);
            FuncResidue.residue_P[codecSetup.residueType[i]].pack(codecSetup.residueParam[i], opb);
        }

        // maps
        opb.write(codecSetup.maps - 1, 6);
        for (int i = 0; i < codecSetup.maps; i++) {
            opb.write(codecSetup.mapType[i], 16);
            FuncMapping.mapping_P[codecSetup.mapType[i]].pack(this, codecSetup.mapParam[i], opb);
        }

        // modes
        opb.write(codecSetup.modes - 1, 6);
        for (int i = 0; i < codecSetup.modes; i++) {
            opb.write(codecSetup.modeParam[i].blockflag, 1);
            opb.write(codecSetup.modeParam[i].windowtype, 16);
            opb.write(codecSetup.modeParam[i].transformtype, 16);
            opb.write(codecSetup.modeParam[i].mapping, 8);
        }
        opb.write(1, 1);

        return true;
    }

    // pack side
    public boolean packInfo(Buffer opb) {
        if (codecSetup == null)
            return false;

        // preamble
        opb.write(0x01, 8);
        opb.write(_vorbis);

        // basic information about the stream
        opb.write(0x00, 32);
        opb.write(this.channels, 8);
        opb.write(this.rate, 32);

        opb.write(this.bitrate_upper, 32);
        opb.write(this.bitrate_nominal, 32);
        opb.write(this.bitrate_lower, 32);

        opb.write(ilog2(codecSetup.blocksizes[0]), 4);
        opb.write(ilog2(codecSetup.blocksizes[1]), 4);
        opb.write(1, 1);
        return true;
    }

    public CodecSetupInfo getCodecSetup() {
        return this.codecSetup;
    }

    private static int ilog2(int v) {
        int ret = 0;
        while (v > 1) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    public String toString() {
        return "version:" + version +
                ", channels:" + channels +
                ", rate:" + rate +
                ", bitrate:" + bitrate_upper + "," +
                bitrate_nominal + "," +
                bitrate_lower;
    }
}
