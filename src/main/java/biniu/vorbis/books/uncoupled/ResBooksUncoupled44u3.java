/*
 * THIS FILE IS PART OF THE OggVorbis SOFTWARE CODEC SOURCE CODE.
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.
 *
 * THE OggVorbis SOURCE CODE IS (C) COPYRIGHT 1994-2002
 * by the XIPHOPHORUS Company http://www.xiph.org/
 */

package biniu.vorbis.books.uncoupled;

import biniu.vorbis.EncodeAuxThreshMatch;
import biniu.vorbis.StaticCodeBook;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class ResBooksUncoupled44u3 {

    private static final int[] _huff_lengthlist__44u3__long = {
            6, 9, 13, 12, 14, 11, 10, 13, 8, 4, 5, 7, 8, 7, 8, 12,
            11, 4, 3, 5, 5, 7, 9, 14, 11, 6, 5, 6, 6, 6, 7, 13,
            13, 7, 5, 6, 4, 5, 7, 14, 11, 7, 6, 6, 5, 5, 6, 13,
            9, 7, 8, 6, 7, 5, 3, 9, 9, 12, 13, 12, 14, 10, 6, 7,
    };

    public static final StaticCodeBook _huff_book__44u3__long = new StaticCodeBook(
            2, 64,
            _huff_lengthlist__44u3__long,
            0, 0, 0, 0, 0,
            null,
            null,
            null,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p1_0 = {
            1,
            0,
            2,
    };

    private static final int[] _vq_lengthlist__44u3__p1_0 = {
            1, 4, 4, 5, 8, 7, 5, 7, 8, 5, 8, 8, 8, 10, 11, 8,
            10, 11, 5, 8, 8, 8, 11, 10, 8, 11, 11, 4, 8, 8, 8, 11,
            11, 8, 11, 11, 8, 11, 11, 11, 13, 14, 11, 14, 14, 8, 11, 11,
            10, 14, 12, 11, 14, 14, 4, 8, 8, 8, 11, 11, 8, 11, 11, 7,
            11, 11, 11, 14, 14, 10, 12, 14, 8, 11, 11, 11, 14, 14, 11, 14,
            13,
    };

    private static final float[] _vq_quantthresh__44u3__p1_0 = {
            -0.5f, 0.5f,
    };

    private static final int[] _vq_quantmap__44u3__p1_0 = {
            1, 0, 2,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p1_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p1_0,
            _vq_quantmap__44u3__p1_0,
            3,
            3
    );

    public static final StaticCodeBook _44u3__p1_0 = new StaticCodeBook(
            4, 81,
            _vq_lengthlist__44u3__p1_0,
            1, -535822336, 1611661312, 2, 0,
            _vq_quantlist__44u3__p1_0,
            null,
            _vq_auxt__44u3__p1_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p2_0 = {
            1,
            0,
            2,
    };

    private static final int[] _vq_lengthlist__44u3__p2_0 = {
            2, 5, 4, 5, 6, 6, 5, 6, 6, 5, 6, 6, 7, 8, 8, 6,
            8, 8, 5, 6, 6, 6, 8, 8, 7, 8, 8, 5, 7, 6, 7, 8,
            8, 6, 8, 8, 7, 8, 8, 8, 9, 10, 8, 10, 10, 6, 8, 8,
            8, 10, 8, 8, 10, 10, 5, 6, 6, 6, 8, 8, 7, 8, 8, 6,
            8, 8, 8, 10, 10, 8, 8, 10, 7, 8, 8, 8, 10, 10, 8, 10,
            9,
    };

    private static final float[] _vq_quantthresh__44u3__p2_0 = {
            -0.5f, 0.5f,
    };

    private static final int[] _vq_quantmap__44u3__p2_0 = {
            1, 0, 2,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p2_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p2_0,
            _vq_quantmap__44u3__p2_0,
            3,
            3
    );

    public static final StaticCodeBook _44u3__p2_0 = new StaticCodeBook(
            4, 81,
            _vq_lengthlist__44u3__p2_0,
            1, -535822336, 1611661312, 2, 0,
            _vq_quantlist__44u3__p2_0,
            null,
            _vq_auxt__44u3__p2_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p3_0 = {
            2,
            1,
            3,
            0,
            4,
    };

    private static final int[] _vq_lengthlist__44u3__p3_0 = {
            2, 4, 4, 7, 7, 5, 7, 7, 9, 9, 5, 7, 7, 9, 9, 8,
            9, 9, 12, 12, 8, 9, 9, 11, 12, 5, 7, 7, 10, 10, 7, 9,
            9, 11, 11, 7, 9, 9, 10, 11, 10, 11, 11, 13, 13, 9, 10, 11,
            13, 13, 5, 7, 7, 10, 10, 7, 9, 9, 11, 10, 7, 9, 9, 11,
            11, 9, 11, 10, 13, 13, 10, 11, 11, 14, 13, 8, 10, 10, 14, 13,
            10, 11, 11, 15, 14, 9, 11, 11, 14, 14, 13, 14, 13, 16, 16, 12,
            13, 13, 15, 15, 8, 10, 10, 13, 14, 9, 11, 11, 14, 14, 10, 11,
            11, 14, 15, 12, 13, 13, 15, 15, 13, 14, 14, 15, 16, 5, 7, 7,
            10, 10, 7, 9, 9, 11, 11, 7, 9, 9, 11, 12, 10, 11, 11, 14,
            14, 10, 11, 11, 14, 14, 7, 9, 9, 12, 12, 9, 11, 11, 13, 13,
            9, 11, 11, 13, 13, 12, 12, 13, 15, 15, 11, 12, 13, 15, 16, 7,
            9, 9, 11, 11, 8, 11, 10, 13, 12, 9, 11, 11, 13, 13, 11, 13,
            12, 15, 13, 11, 13, 13, 15, 16, 9, 12, 11, 15, 14, 11, 12, 13,
            16, 15, 11, 13, 13, 15, 16, 14, 14, 15, 17, 16, 13, 15, 16, 0,
            17, 9, 11, 11, 15, 15, 10, 13, 12, 15, 15, 11, 13, 13, 15, 16,
            13, 15, 13, 16, 15, 14, 16, 15, 0, 19, 5, 7, 7, 10, 10, 7,
            9, 9, 11, 11, 7, 9, 9, 11, 11, 10, 12, 11, 14, 14, 10, 11,
            12, 14, 14, 7, 9, 9, 12, 12, 9, 11, 11, 14, 13, 9, 10, 11,
            12, 13, 11, 13, 13, 16, 16, 11, 12, 13, 13, 16, 7, 9, 9, 12,
            12, 9, 11, 11, 13, 13, 9, 11, 11, 13, 13, 11, 13, 13, 15, 15,
            12, 13, 12, 15, 14, 9, 11, 11, 15, 14, 11, 13, 12, 16, 16, 10,
            12, 12, 15, 15, 13, 15, 15, 17, 19, 13, 14, 15, 16, 17, 10, 12,
            12, 15, 15, 11, 13, 13, 16, 16, 11, 13, 13, 15, 16, 13, 15, 15,
            0, 0, 14, 15, 15, 16, 16, 8, 10, 10, 14, 14, 10, 12, 12, 15,
            15, 10, 12, 11, 15, 16, 14, 15, 15, 19, 20, 13, 14, 14, 18, 16,
            9, 11, 11, 15, 15, 11, 13, 13, 17, 16, 11, 13, 13, 16, 16, 15,
            17, 17, 20, 20, 14, 15, 16, 17, 20, 9, 11, 11, 15, 15, 10, 13,
            12, 16, 15, 11, 13, 13, 15, 17, 14, 16, 15, 18, 0, 14, 16, 15,
            18, 20, 12, 14, 14, 0, 0, 14, 14, 16, 0, 0, 13, 16, 15, 0,
            0, 17, 17, 18, 0, 0, 16, 17, 19, 19, 0, 12, 14, 14, 18, 0,
            12, 16, 14, 0, 17, 13, 15, 15, 18, 0, 16, 18, 17, 0, 17, 16,
            18, 17, 0, 0, 7, 10, 10, 14, 14, 10, 12, 11, 15, 15, 10, 12,
            12, 16, 15, 13, 15, 15, 18, 0, 14, 15, 15, 17, 0, 9, 11, 11,
            15, 15, 11, 13, 13, 16, 16, 11, 12, 13, 16, 16, 14, 15, 16, 17,
            17, 14, 16, 16, 16, 18, 9, 11, 12, 16, 16, 11, 13, 13, 17, 17,
            11, 14, 13, 20, 17, 15, 16, 16, 19, 0, 15, 16, 17, 0, 19, 11,
            13, 14, 17, 16, 14, 15, 15, 20, 18, 13, 14, 15, 17, 19, 16, 18,
            18, 0, 20, 16, 16, 19, 17, 0, 12, 15, 14, 17, 0, 14, 15, 15,
            18, 19, 13, 16, 15, 19, 20, 15, 18, 18, 0, 20, 17, 0, 16, 0,
            0,
    };

    private static final float[] _vq_quantthresh__44u3__p3_0 = {
            -1.5f, -0.5f, 0.5f, 1.5f,
    };

    private static final int[] _vq_quantmap__44u3__p3_0 = {
            3, 1, 0, 2, 4,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p3_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p3_0,
            _vq_quantmap__44u3__p3_0,
            5,
            5
    );

    public static final StaticCodeBook _44u3__p3_0 = new StaticCodeBook(
            4, 625,
            _vq_lengthlist__44u3__p3_0,
            1, -533725184, 1611661312, 3, 0,
            _vq_quantlist__44u3__p3_0,
            null,
            _vq_auxt__44u3__p3_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p4_0 = {
            2,
            1,
            3,
            0,
            4,
    };

    private static final int[] _vq_lengthlist__44u3__p4_0 = {
            4, 5, 5, 8, 8, 5, 7, 6, 9, 9, 5, 6, 7, 9, 9, 9,
            9, 9, 11, 11, 9, 9, 9, 11, 11, 5, 7, 7, 9, 9, 7, 8,
            8, 10, 10, 7, 7, 8, 10, 10, 9, 10, 10, 11, 12, 9, 10, 10,
            11, 12, 5, 7, 7, 9, 9, 7, 8, 7, 10, 10, 7, 8, 8, 10,
            10, 9, 10, 9, 12, 11, 9, 10, 10, 12, 11, 9, 10, 9, 12, 12,
            9, 10, 10, 13, 12, 9, 10, 10, 12, 13, 12, 12, 12, 14, 14, 11,
            12, 12, 13, 14, 9, 9, 10, 12, 12, 9, 10, 10, 12, 12, 9, 10,
            10, 12, 13, 11, 12, 11, 14, 13, 12, 12, 12, 14, 13, 5, 7, 7,
            9, 9, 7, 8, 8, 10, 10, 7, 8, 8, 10, 10, 10, 10, 10, 12,
            12, 9, 10, 10, 12, 12, 7, 8, 8, 11, 10, 8, 8, 9, 11, 11,
            8, 9, 9, 11, 11, 11, 11, 11, 12, 13, 10, 11, 11, 13, 13, 6,
            8, 8, 10, 10, 7, 9, 8, 11, 10, 8, 9, 9, 11, 11, 10, 11,
            10, 13, 11, 10, 11, 11, 13, 13, 9, 11, 10, 13, 12, 10, 11, 11,
            13, 13, 10, 11, 11, 13, 13, 12, 12, 13, 12, 15, 12, 13, 13, 15,
            15, 9, 10, 10, 12, 13, 10, 11, 10, 13, 12, 10, 11, 11, 13, 14,
            12, 13, 11, 15, 13, 12, 13, 13, 15, 15, 5, 7, 7, 9, 9, 7,
            8, 8, 10, 10, 7, 8, 8, 10, 10, 9, 10, 10, 12, 12, 10, 10,
            11, 12, 12, 6, 8, 8, 10, 10, 8, 9, 9, 11, 11, 7, 8, 9,
            10, 11, 10, 11, 11, 13, 13, 10, 10, 11, 11, 13, 7, 8, 8, 10,
            10, 8, 9, 9, 11, 11, 8, 9, 9, 11, 11, 10, 11, 11, 13, 13,
            11, 11, 11, 13, 12, 9, 10, 10, 13, 12, 10, 11, 11, 14, 13, 10,
            10, 11, 12, 13, 12, 13, 13, 15, 15, 12, 11, 13, 13, 14, 9, 10,
            11, 12, 13, 10, 11, 11, 13, 13, 10, 11, 11, 13, 13, 12, 13, 13,
            15, 15, 12, 13, 12, 15, 12, 8, 9, 9, 12, 12, 9, 11, 10, 13,
            13, 9, 10, 10, 13, 13, 12, 13, 13, 15, 14, 12, 12, 12, 14, 13,
            9, 10, 10, 13, 12, 10, 11, 11, 13, 13, 10, 11, 11, 14, 12, 13,
            13, 14, 14, 16, 12, 13, 13, 15, 15, 9, 10, 10, 13, 13, 10, 11,
            10, 14, 13, 10, 11, 11, 13, 14, 12, 14, 13, 15, 14, 13, 13, 13,
            15, 15, 11, 13, 12, 15, 14, 11, 12, 13, 14, 15, 12, 13, 13, 16,
            14, 14, 12, 15, 12, 16, 14, 15, 15, 17, 15, 11, 12, 12, 14, 14,
            11, 13, 11, 15, 14, 12, 13, 13, 15, 15, 13, 15, 12, 17, 13, 14,
            15, 15, 16, 16, 8, 9, 9, 12, 12, 9, 10, 10, 12, 13, 9, 10,
            10, 13, 13, 12, 12, 12, 14, 14, 12, 13, 13, 15, 15, 9, 10, 10,
            13, 12, 10, 11, 11, 14, 13, 10, 10, 11, 13, 14, 12, 13, 13, 15,
            15, 12, 12, 13, 14, 16, 9, 10, 10, 13, 13, 10, 11, 11, 13, 14,
            10, 11, 11, 14, 13, 12, 13, 13, 14, 15, 13, 14, 13, 16, 14, 11,
            12, 12, 14, 14, 12, 13, 13, 15, 14, 11, 12, 13, 14, 15, 14, 15,
            15, 16, 16, 13, 13, 15, 13, 16, 11, 12, 12, 14, 15, 12, 13, 13,
            14, 15, 11, 13, 12, 15, 14, 14, 15, 15, 16, 16, 14, 15, 12, 16,
            13,
    };

    private static final float[] _vq_quantthresh__44u3__p4_0 = {
            -1.5f, -0.5f, 0.5f, 1.5f,
    };

    private static final int[] _vq_quantmap__44u3__p4_0 = {
            3, 1, 0, 2, 4,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p4_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p4_0,
            _vq_quantmap__44u3__p4_0,
            5,
            5
    );

    public static final StaticCodeBook _44u3__p4_0 = new StaticCodeBook(
            4, 625,
            _vq_lengthlist__44u3__p4_0,
            1, -533725184, 1611661312, 3, 0,
            _vq_quantlist__44u3__p4_0,
            null,
            _vq_auxt__44u3__p4_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p5_0 = {
            4,
            3,
            5,
            2,
            6,
            1,
            7,
            0,
            8,
    };

    private static final int[] _vq_lengthlist__44u3__p5_0 = {
            2, 3, 3, 6, 6, 7, 7, 9, 9, 4, 5, 5, 7, 7, 8, 8,
            10, 10, 4, 5, 5, 7, 7, 8, 8, 10, 10, 6, 7, 7, 8, 8,
            9, 9, 11, 10, 6, 7, 7, 8, 8, 9, 9, 10, 10, 7, 8, 8,
            9, 9, 10, 10, 11, 11, 7, 8, 8, 9, 9, 10, 10, 11, 11, 9,
            10, 10, 11, 10, 11, 11, 12, 12, 9, 10, 10, 10, 10, 11, 11, 12,
            12,
    };

    private static final float[] _vq_quantthresh__44u3__p5_0 = {
            -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f,
    };

    private static final int[] _vq_quantmap__44u3__p5_0 = {
            7, 5, 3, 1, 0, 2, 4, 6,
            8,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p5_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p5_0,
            _vq_quantmap__44u3__p5_0,
            9,
            9
    );

    public static final StaticCodeBook _44u3__p5_0 = new StaticCodeBook(
            2, 81,
            _vq_lengthlist__44u3__p5_0,
            1, -531628032, 1611661312, 4, 0,
            _vq_quantlist__44u3__p5_0,
            null,
            _vq_auxt__44u3__p5_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p6_0 = {
            6,
            5,
            7,
            4,
            8,
            3,
            9,
            2,
            10,
            1,
            11,
            0,
            12,
    };

    private static final int[] _vq_lengthlist__44u3__p6_0 = {
            1, 4, 4, 6, 6, 8, 8, 9, 9, 10, 11, 13, 14, 4, 6, 5,
            8, 8, 9, 9, 10, 10, 11, 11, 14, 14, 4, 6, 6, 8, 8, 9,
            9, 10, 10, 11, 11, 14, 14, 6, 8, 8, 9, 9, 10, 10, 11, 11,
            12, 12, 15, 15, 6, 8, 8, 9, 9, 10, 11, 11, 11, 12, 12, 15,
            15, 8, 9, 9, 11, 10, 11, 11, 12, 12, 13, 13, 15, 16, 8, 9,
            9, 10, 11, 11, 11, 12, 12, 13, 13, 16, 16, 10, 10, 11, 11, 11,
            12, 12, 13, 13, 13, 14, 17, 16, 9, 10, 11, 12, 11, 12, 12, 13,
            13, 13, 13, 16, 18, 11, 12, 11, 12, 12, 13, 13, 13, 14, 15, 14,
            17, 17, 11, 11, 12, 12, 12, 13, 13, 13, 14, 14, 15, 18, 17, 14,
            15, 15, 15, 15, 16, 16, 17, 17, 19, 18, 0, 20, 14, 15, 14, 15,
            15, 16, 16, 16, 17, 18, 16, 20, 18,
    };

    private static final float[] _vq_quantthresh__44u3__p6_0 = {
            -27.5f, -22.5f, -17.5f, -12.5f, -7.5f, -2.5f, 2.5f, 7.5f,
            12.5f, 17.5f, 22.5f, 27.5f,
    };

    private static final int[] _vq_quantmap__44u3__p6_0 = {
            11, 9, 7, 5, 3, 1, 0, 2,
            4, 6, 8, 10, 12,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p6_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p6_0,
            _vq_quantmap__44u3__p6_0,
            13,
            13
    );

    public static final StaticCodeBook _44u3__p6_0 = new StaticCodeBook(
            2, 169,
            _vq_lengthlist__44u3__p6_0,
            1, -526516224, 1616117760, 4, 0,
            _vq_quantlist__44u3__p6_0,
            null,
            _vq_auxt__44u3__p6_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p6_1 = {
            2,
            1,
            3,
            0,
            4,
    };

    private static final int[] _vq_lengthlist__44u3__p6_1 = {
            2, 4, 4, 5, 5, 4, 5, 5, 6, 5, 4, 5, 5, 5, 6, 5,
            6, 5, 6, 6, 5, 5, 6, 6, 6,
    };

    private static final float[] _vq_quantthresh__44u3__p6_1 = {
            -1.5f, -0.5f, 0.5f, 1.5f,
    };

    private static final int[] _vq_quantmap__44u3__p6_1 = {
            3, 1, 0, 2, 4,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p6_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p6_1,
            _vq_quantmap__44u3__p6_1,
            5,
            5
    );

    public static final StaticCodeBook _44u3__p6_1 = new StaticCodeBook(
            2, 25,
            _vq_lengthlist__44u3__p6_1,
            1, -533725184, 1611661312, 3, 0,
            _vq_quantlist__44u3__p6_1,
            null,
            _vq_auxt__44u3__p6_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p7_0 = {
            4,
            3,
            5,
            2,
            6,
            1,
            7,
            0,
            8,
    };

    private static final int[] _vq_lengthlist__44u3__p7_0 = {
            1, 3, 3, 10, 10, 10, 10, 10, 10, 4, 10, 10, 10, 10, 10, 10,
            10, 10, 4, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            9,
    };

    private static final float[] _vq_quantthresh__44u3__p7_0 = {
            -892.5f, -637.5f, -382.5f, -127.5f, 127.5f, 382.5f, 637.5f, 892.5f,
    };

    private static final int[] _vq_quantmap__44u3__p7_0 = {
            7, 5, 3, 1, 0, 2, 4, 6,
            8,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p7_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p7_0,
            _vq_quantmap__44u3__p7_0,
            9,
            9
    );

    public static final StaticCodeBook _44u3__p7_0 = new StaticCodeBook(
            2, 81,
            _vq_lengthlist__44u3__p7_0,
            1, -515907584, 1627381760, 4, 0,
            _vq_quantlist__44u3__p7_0,
            null,
            _vq_auxt__44u3__p7_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p7_1 = {
            7,
            6,
            8,
            5,
            9,
            4,
            10,
            3,
            11,
            2,
            12,
            1,
            13,
            0,
            14,
    };

    private static final int[] _vq_lengthlist__44u3__p7_1 = {
            1, 4, 4, 6, 6, 7, 6, 8, 7, 9, 8, 10, 9, 11, 11, 4,
            7, 7, 8, 7, 9, 9, 10, 10, 11, 11, 11, 11, 12, 12, 4, 7,
            7, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 12, 11, 6, 8, 8,
            9, 9, 10, 10, 11, 11, 12, 12, 13, 12, 13, 13, 6, 8, 8, 9,
            9, 10, 11, 11, 11, 12, 12, 13, 14, 13, 13, 8, 9, 9, 11, 11,
            12, 12, 12, 13, 14, 13, 14, 14, 14, 15, 8, 9, 9, 11, 11, 11,
            12, 13, 14, 13, 14, 15, 17, 14, 15, 9, 10, 10, 12, 12, 13, 13,
            13, 14, 15, 15, 15, 16, 16, 16, 9, 11, 11, 12, 12, 13, 13, 14,
            14, 14, 15, 16, 16, 16, 16, 10, 12, 12, 13, 13, 14, 14, 15, 15,
            15, 16, 17, 17, 17, 17, 10, 12, 11, 13, 13, 15, 14, 15, 14, 16,
            17, 16, 16, 16, 16, 11, 13, 12, 14, 14, 14, 14, 15, 16, 17, 16,
            17, 17, 17, 17, 11, 13, 12, 14, 14, 14, 15, 17, 16, 17, 17, 17,
            17, 17, 17, 12, 13, 13, 15, 16, 15, 16, 17, 17, 16, 16, 17, 17,
            17, 17, 12, 13, 13, 15, 15, 15, 16, 17, 17, 17, 16, 17, 16, 17,
            17,
    };

    private static final float[] _vq_quantthresh__44u3__p7_1 = {
            -110.5f, -93.5f, -76.5f, -59.5f, -42.5f, -25.5f, -8.5f, 8.5f,
            25.5f, 42.5f, 59.5f, 76.5f, 93.5f, 110.5f,
    };

    private static final int[] _vq_quantmap__44u3__p7_1 = {
            13, 11, 9, 7, 5, 3, 1, 0,
            2, 4, 6, 8, 10, 12, 14,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p7_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p7_1,
            _vq_quantmap__44u3__p7_1,
            15,
            15
    );

    public static final StaticCodeBook _44u3__p7_1 = new StaticCodeBook(
            2, 225,
            _vq_lengthlist__44u3__p7_1,
            1, -522338304, 1620115456, 4, 0,
            _vq_quantlist__44u3__p7_1,
            null,
            _vq_auxt__44u3__p7_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u3__p7_2 = {
            8,
            7,
            9,
            6,
            10,
            5,
            11,
            4,
            12,
            3,
            13,
            2,
            14,
            1,
            15,
            0,
            16,
    };

    private static final int[] _vq_lengthlist__44u3__p7_2 = {
            2, 5, 5, 7, 6, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9,
            9, 5, 6, 6, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9,
            10, 10, 5, 6, 6, 7, 7, 8, 8, 8, 8, 9, 8, 9, 9, 9,
            9, 10, 9, 7, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9,
            10, 10, 10, 10, 7, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 10,
            9, 10, 10, 10, 10, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9,
            10, 10, 10, 10, 10, 10, 7, 8, 8, 9, 8, 9, 9, 9, 9, 10,
            9, 10, 10, 10, 10, 10, 10, 8, 8, 8, 9, 9, 9, 9, 9, 9,
            9, 10, 10, 10, 10, 10, 10, 10, 8, 9, 8, 9, 9, 9, 9, 10,
            9, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 9, 9, 9, 10,
            9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 9, 9, 10,
            9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 10, 9,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 11, 9, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 11, 9,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 10,
            11,
    };

    private static final float[] _vq_quantthresh__44u3__p7_2 = {
            -7.5f, -6.5f, -5.5f, -4.5f, -3.5f, -2.5f, -1.5f, -0.5f,
            0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f,
    };

    private static final int[] _vq_quantmap__44u3__p7_2 = {
            15, 13, 11, 9, 7, 5, 3, 1,
            0, 2, 4, 6, 8, 10, 12, 14,
            16,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u3__p7_2 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u3__p7_2,
            _vq_quantmap__44u3__p7_2,
            17,
            17
    );

    public static final StaticCodeBook _44u3__p7_2 = new StaticCodeBook(
            2, 289,
            _vq_lengthlist__44u3__p7_2,
            1, -529530880, 1611661312, 5, 0,
            _vq_quantlist__44u3__p7_2,
            null,
            _vq_auxt__44u3__p7_2,
            null,
            0
    );

    private static final int[] _huff_lengthlist__44u3__short = {
            14, 14, 14, 15, 13, 15, 12, 16, 10, 8, 7, 9, 9, 8, 12, 16,
            10, 5, 4, 6, 5, 6, 9, 16, 14, 8, 6, 8, 7, 8, 10, 16,
            14, 7, 4, 6, 3, 5, 8, 16, 15, 9, 5, 7, 4, 4, 7, 16,
            13, 10, 6, 7, 4, 3, 4, 13, 13, 12, 7, 9, 5, 5, 6, 12,
    };

    public static final StaticCodeBook _huff_book__44u3__short = new StaticCodeBook(
            2, 64,
            _huff_lengthlist__44u3__short,
            0, 0, 0, 0, 0,
            null,
            null,
            null,
            null,
            0
    );
}
