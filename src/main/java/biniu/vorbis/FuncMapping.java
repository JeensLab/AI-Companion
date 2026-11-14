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
abstract class FuncMapping {

    public static final FuncMapping[] mapping_P = {new Mapping0()};

    abstract void pack(Info info, Object imap, Buffer buffer);

    abstract Object unpack(Info info, Buffer buffer);

    abstract Object look(DspState vd, InfoMode vm, Object m);

    abstract void clear(Object imap);

    abstract public int mapping0Forward(Block vb);

    abstract int inverse(Block vd, Object lm);
}
