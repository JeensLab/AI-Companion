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
abstract class FuncFloor {

    public static final FuncFloor[] floor_P = {new Floor0(), new Floor1()};

    abstract void pack(Object i, Buffer opb);

    abstract Object unpack(Info i, Buffer opb);

    abstract Object look(DspState vd, Object i);

    abstract Object look(DspState vd, InfoMode mi, Object i);

    abstract void clear(Object obj);

    abstract int forward(Block vb, Object i, float[] in, float[] out, Object vs);

    abstract Object inverse1(Block vb, Object i, Object memo);

    abstract int inverse2(Block vb, Object i, Object memo, float[] out);
}
