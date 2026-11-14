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
class Time0 extends FuncTime {

    @Override
    void pack(Object i, Buffer opb) {
    }

    @Override
    Object unpack(Info vi, Buffer opb) {
        return "";
    }

    @Override
    Object look(DspState vd, InfoMode mi, Object i) {
        return "";
    }

    @Override
    void free_info(Object i) {
    }

    @Override
    void free_look(Object i) {
    }

    @Override
    int forward(Block vb, Object i) {
        return 0;
    }

    @Override
    int inverse(Block vb, Object i, float[] in, float[] out) {
        return 0;
    }
}
