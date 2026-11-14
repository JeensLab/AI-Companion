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
public class VorbisResidueTemplate {

    final int res_type;
    /** 0 lowpass limited, 1 point stereo limited */
    final int limit_type;
    final InfoResidue0 res;
    final StaticCodeBook book_aux;
    final StaticCodeBook book_aux_managed;
    final StaticCodeBook[][] books_base;
    final StaticCodeBook[][] books_base_managed;

    //(int,int,InfoResidue0,StaticCodeBook,StaticCodeBook,StaticBookBlock,StaticBookBlock)
    public VorbisResidueTemplate(
            int res_type,
            int limit_type,
            InfoResidue0 res,
            StaticCodeBook book_aux,
            StaticCodeBook book_aux_managed,
            StaticCodeBook[][] books_base,
            StaticCodeBook[][] books_base_managed
    ) {
        this.res_type = res_type;
        this.limit_type = limit_type;
        this.res = res;
        this.book_aux = book_aux;
        this.book_aux_managed = book_aux_managed;
        this.books_base = books_base;
        this.books_base_managed = books_base_managed;
    }
}
