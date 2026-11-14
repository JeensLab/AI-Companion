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
public class BlockInternal {

    /** this is a pointer into local storage */
    float[][] pcmdelay;
    float ampmax;
    int blocktype;

    /**
     * initialized, must be freed;
     * blob [PACKETBLOBS/2] points to
     * the oggpack_buffer in the
     * main vorbis_block
     */
    final Buffer[] packetblob = new Buffer[Const.PACKETBLOBS];

    public BlockInternal() {
    }
}
