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
public class InfoMode {

    int blockflag;
    int windowtype;
    int transformtype;
    int mapping;

    public InfoMode() {
    }

    public InfoMode(
            int blockflag,
            int windowtype,
            int transformtype,
            int mapping
    ) {
        this();
        this.blockflag = blockflag;
        this.windowtype = windowtype;
        this.transformtype = transformtype;
        this.mapping = mapping;
    }

    public boolean setValues(InfoMode info) {
        if (info == null) return false;
        this.blockflag = info.blockflag;
        this.windowtype = info.windowtype;
        this.transformtype = info.transformtype;
        this.mapping = info.mapping;
        return true;
    }
}
