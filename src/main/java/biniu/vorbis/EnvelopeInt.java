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
public interface EnvelopeInt {

    int VE_PRE = 16;
    int VE_WIN = 4;
    int VE_POST = 2;
    int VE_AMP = (VE_PRE + VE_POST - 1);

    float M_PI = 3.1415926536f;

    int VE_BANDS = 7;
    int VE_NEARDC = 15;

    int VE_MINSTRETCH = 2;   // a bit less than short block
    int VE_MAXSTRETCH = 12;  // one-third full block
}
