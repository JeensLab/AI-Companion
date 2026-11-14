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
public class CodecSetupInfo {

    // Vorbis supports only short and long blocks, but allows the
    // encoder to choose the sizes

    public int[] blocksizes = new int[2];

    // modes are the primary means of supporting on-the-fly different
    // blocksizes, different channel mappings (LR or M/A),
    // different residue backends, etc.  Each mode consists of a
    // blocksize flag and a mapping (along with the mapping setup */

    public int modes;
    public int maps;
    public int floors;
    public int residues;
    public int books;
    /** encode only */
    public int psys;

    public InfoMode[] modeParam = new InfoMode[64];
    public int[] mapType = new int[64];
    public InfoMapping0[] mapParam = new InfoMapping0[64];
    public int[] floorType = new int[64];
    public InfoFloor1[] floorParam = new InfoFloor1[64];
    public int[] residueType = new int[64];
    public InfoResidue0[] residueParam = new InfoResidue0[64];
    public StaticCodeBook[] bookParam = new StaticCodeBook[256];
    public CodeBook[] fullBooks;

    /** encode only */
    public PsyInfo[] psyParam = new PsyInfo[64];
    public final InfoPsyGlobal psyGlobParam;

    public final BitrateManagerInfo biManInfo;
    /**
     * used only by vorbisenc.c.  It's a
     * highly redundant structure, but
     * improves clarity of program flow.
     */
    public final HighlevelEncodeSetup hiEncSet;
    /** painless downsample for decode */
    public int halfrateFlag;

    public CodecSetupInfo() {
        this.hiEncSet = new HighlevelEncodeSetup();
        this.psyGlobParam = new InfoPsyGlobal();
        for (int i = 0; i < mapParam.length; i++) {
            mapParam[i] = new InfoMapping0();
        }
        for (int i = 0; i < floorParam.length; i++) {
            floorParam[i] = new InfoFloor1();
        }
        for (int i = 0; i < residueParam.length; i++) {
            residueParam[i] = new InfoResidue0();
        }
        for (int i = 0; i < psyParam.length; i++) {
            psyParam[i] = new PsyInfo();
        }
        this.biManInfo = new BitrateManagerInfo();
    }

    public CodecSetupInfo(
            int[] blocksizes,
            int modes,
            int maps,
            int floors,
            int residues,
            int books,
            int psys,
            InfoMode[] mode_param,
            int[] map_type,
            InfoMapping0[] map_param,//
            int[] floor_type,
            InfoFloor1[] floor_param,//
            int[] residue_type,
            InfoResidue0[] residue_param,//
            StaticCodeBook[] book_param,
            CodeBook[] fullbooks,
            PsyInfo[] psy_param,
            InfoPsyGlobal psy_g_param,
            BitrateManagerInfo bi,
            HighlevelEncodeSetup hi,
            int halfrate_flag

    ) {
        this.blocksizes = blocksizes;
        this.modes = modes;
        this.maps = maps;
        this.floors = floors;
        this.residues = residues;
        this.books = books;
        this.psys = psys;
        this.modeParam = mode_param;
        this.mapType = map_type;
        this.mapParam = map_param;
        this.floorType = floor_type;
        this.floorParam = floor_param;
        this.residueType = residue_type;
        this.residueParam = residue_param;
        this.bookParam = book_param;
        this.fullBooks = fullbooks;
        this.psyParam = psy_param;
        this.psyGlobParam = psy_g_param;
        this.biManInfo = bi;
        this.hiEncSet = hi;
        this.halfrateFlag = halfrate_flag;
    }
}
