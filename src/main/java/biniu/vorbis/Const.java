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
public interface Const {

    int PACKETBLOBS = 15;
    int CHUNKSIZE = 8500;
    int SEEK_SET = 0;
    int SEEK_CUR = 1;
    int SEEK_END = 2;

    int OV_FALSE = -1;
    int OV_EOF = -2;
    int OV_HOLE = -3;

    int OV_EREAD = -128;
    int OV_EFAULT = -129;
    int OV_EIMPL = -130;
    int OV_EINVAL = -131;
    int OV_ENOTVORBIS = -132;
    int OV_EBADHEADER = -133;
    int OV_EVERSION = -134;
    int OV_ENOTAUDIO = -135;
    int OV_EBADPACKET = -136;
    int OV_EBADLINK = -137;
    int OV_ENOSEEK = -138;
}
