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
public class InfoMapping0 {

    /** <= 16 */
    int submaps;
    /** up to 256 channels in a Vorbis stream */
    int[] chmuxlist = new int[256];

    /** [mux] */
    int[] timesubmap = new int[16];
    /** [mux] submap to floors */
    int[] floorsubmap = new int[16];
    /** [mux] submap to residue */
    int[] residuesubmap = new int[16];
    /** [mux]; encode only */
    int[] psysubmap = new int[16];

    int coupling_steps;
    int[] coupling_mag = new int[256];
    int[] coupling_ang = new int[256];

    void free() {
        chmuxlist = null;
        timesubmap = null;
        floorsubmap = null;
        residuesubmap = null;
        psysubmap = null;

        coupling_mag = null;
        coupling_ang = null;
    }

    public InfoMapping0() {
    }

    public InfoMapping0(
            int submaps,
            int[] chmuxlist,
            int[] floorsubmap,
            int[] residuesubmap,
            int coupling_steps,
            int[] coupling_mag,
            int[] coupling_ang
    ) {
        this();
        this.submaps = submaps;
        this.chmuxlist = chmuxlist;
        this.floorsubmap = floorsubmap;
        this.residuesubmap = residuesubmap;
        this.coupling_steps = coupling_steps;
        this.coupling_mag = coupling_mag;
        this.coupling_ang = coupling_ang;
    }

    public boolean setValues(InfoMapping0 info) {
        if (info == null)
            return false;
        this.submaps = info.submaps;
        System.arraycopy(info.chmuxlist, 0, this.chmuxlist, 0,
                info.chmuxlist.length);
        System.arraycopy(info.floorsubmap, 0, this.floorsubmap, 0,
                info.floorsubmap.length);
        System.arraycopy(info.residuesubmap, 0, this.residuesubmap, 0,
                info.residuesubmap.length);
        this.coupling_steps = info.coupling_steps;
        System.arraycopy(info.coupling_mag, 0, this.coupling_mag, 0,
                info.coupling_mag.length);
        System.arraycopy(info.coupling_ang, 0, this.coupling_ang, 0,
                info.coupling_ang.length);
        return true;
    }
}