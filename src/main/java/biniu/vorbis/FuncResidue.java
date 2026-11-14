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
abstract class FuncResidue {

    public static final FuncResidue[] residue_P = {new Residue0(),
            new Residue1(),
            new Residue2()};

    abstract void pack(Object vr, Buffer opb);

    abstract Object unpack(Info vi, Buffer opb);

    abstract LookResidue look(DspState vd, InfoMode vm, Object vr);

    abstract LookResidue look(DspState vd, Object vr);

    abstract void clear(Object i);

    //  abstract void freeLook(Object i);
    abstract public int forward(Buffer opb, Block vb, LookResidue vl,
                                float[][] in, float[][] out, int[] nonzero, int ch,
                                int[][] partword);

    abstract public int[][] clas(LookResidue vl, float[][] in, int pin, int[] nonzero, int ch);

    //  abstract int inverse(Block vb, Object vl, float[][] in, int ch);
    abstract int inverse(Block vb, LookResidue vl, float[][] in, int[] nonzero, int ch);
}
