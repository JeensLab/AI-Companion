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
public class ResBooksUncoupled44u6 {

    private static final int[] _huff_lengthlist__44u6__long = {
            3, 9, 14, 13, 14, 13, 16, 12, 13, 14, 5, 4, 6, 6, 8, 9,
            11, 10, 12, 15, 10, 5, 5, 6, 6, 8, 10, 10, 13, 16, 10, 6,
            6, 6, 6, 8, 9, 9, 12, 14, 13, 7, 6, 6, 4, 6, 6, 7,
            11, 14, 10, 7, 7, 7, 6, 6, 6, 7, 10, 13, 15, 10, 9, 8,
            5, 6, 5, 6, 10, 14, 10, 9, 8, 8, 6, 6, 5, 4, 6, 11,
            11, 11, 12, 11, 10, 9, 9, 5, 5, 9, 10, 12, 15, 13, 13, 13,
            13, 8, 7, 7,
    };

    public static final StaticCodeBook _huff_book__44u6__long = new StaticCodeBook(
            2, 100,
            _huff_lengthlist__44u6__long,
            0, 0, 0, 0, 0,
            null,
            null,
            null,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p1_0 = {
            1,
            0,
            2,
    };

    private static final int[] _vq_lengthlist__44u6__p1_0 = {
            1, 4, 4, 4, 8, 7, 5, 7, 7, 5, 8, 8, 8, 10, 10, 7,
            9, 10, 5, 8, 8, 7, 10, 9, 8, 10, 10, 5, 8, 8, 8, 10,
            10, 8, 10, 10, 8, 10, 10, 10, 12, 13, 10, 13, 13, 7, 10, 10,
            10, 13, 11, 10, 13, 13, 5, 8, 8, 8, 11, 10, 8, 10, 10, 7,
            10, 10, 10, 13, 13, 10, 11, 13, 8, 10, 11, 10, 13, 13, 10, 13,
            12,
    };

    private static final float[] _vq_quantthresh__44u6__p1_0 = {
            -0.5f, 0.5f,
    };

    private static final int[] _vq_quantmap__44u6__p1_0 = {
            1, 0, 2,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p1_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p1_0,
            _vq_quantmap__44u6__p1_0,
            3,
            3
    );

    public static final StaticCodeBook _44u6__p1_0 = new StaticCodeBook(
            4, 81,
            _vq_lengthlist__44u6__p1_0,
            1, -535822336, 1611661312, 2, 0,
            _vq_quantlist__44u6__p1_0,
            null,
            _vq_auxt__44u6__p1_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p2_0 = {
            1,
            0,
            2,
    };

    private static final int[] _vq_lengthlist__44u6__p2_0 = {
            3, 4, 4, 5, 6, 6, 5, 6, 6, 5, 6, 6, 6, 8, 8, 6,
            7, 8, 5, 6, 6, 6, 8, 7, 6, 8, 8, 5, 6, 6, 6, 8,
            8, 6, 8, 8, 6, 8, 8, 8, 9, 9, 8, 9, 9, 6, 7, 7,
            7, 9, 8, 8, 9, 9, 5, 6, 6, 6, 8, 7, 6, 8, 8, 6,
            8, 8, 8, 9, 9, 7, 8, 9, 6, 8, 8, 8, 9, 9, 8, 9,
            9,
    };

    private static final float[] _vq_quantthresh__44u6__p2_0 = {
            -0.5f, 0.5f,
    };

    private static final int[] _vq_quantmap__44u6__p2_0 = {
            1, 0, 2,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p2_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p2_0,
            _vq_quantmap__44u6__p2_0,
            3,
            3
    );

    public static final StaticCodeBook _44u6__p2_0 = new StaticCodeBook(
            4, 81,
            _vq_lengthlist__44u6__p2_0,
            1, -535822336, 1611661312, 2, 0,
            _vq_quantlist__44u6__p2_0,
            null,
            _vq_auxt__44u6__p2_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p3_0 = {
            2,
            1,
            3,
            0,
            4,
    };

    private static final int[] _vq_lengthlist__44u6__p3_0 = {
            2, 5, 4, 8, 8, 5, 7, 6, 9, 9, 5, 6, 7, 9, 9, 8,
            9, 9, 13, 12, 8, 9, 10, 12, 13, 5, 7, 7, 10, 9, 7, 9,
            9, 11, 11, 7, 8, 9, 11, 11, 10, 11, 11, 14, 14, 9, 10, 11,
            13, 14, 5, 7, 7, 9, 10, 6, 9, 8, 11, 11, 7, 9, 9, 11,
            11, 9, 11, 10, 14, 13, 10, 11, 11, 14, 13, 8, 10, 10, 13, 13,
            10, 11, 11, 15, 15, 9, 11, 11, 14, 14, 13, 14, 14, 17, 16, 12,
            13, 14, 16, 16, 8, 10, 10, 13, 14, 9, 11, 11, 14, 15, 10, 11,
            12, 14, 15, 12, 14, 13, 16, 15, 13, 14, 14, 15, 17, 5, 7, 7,
            10, 10, 7, 9, 9, 11, 11, 7, 9, 9, 11, 11, 10, 12, 11, 14,
            14, 10, 11, 11, 14, 14, 7, 9, 9, 12, 11, 9, 11, 11, 13, 13,
            9, 11, 11, 13, 13, 11, 13, 13, 14, 15, 11, 12, 13, 15, 16, 6,
            9, 9, 11, 12, 8, 11, 10, 13, 12, 9, 11, 11, 13, 14, 11, 13,
            12, 16, 14, 11, 13, 13, 15, 16, 10, 12, 11, 14, 15, 11, 13, 13,
            15, 17, 11, 13, 13, 17, 16, 15, 15, 16, 17, 16, 14, 15, 16, 18,
            0, 9, 11, 11, 14, 15, 10, 12, 12, 16, 15, 11, 13, 13, 16, 16,
            13, 15, 14, 18, 15, 14, 16, 16, 0, 0, 5, 7, 7, 10, 10, 7,
            9, 9, 11, 11, 7, 9, 9, 11, 11, 10, 11, 11, 14, 14, 10, 11,
            12, 14, 14, 6, 9, 9, 11, 11, 9, 11, 11, 13, 13, 8, 10, 11,
            12, 13, 11, 13, 13, 16, 15, 11, 12, 13, 14, 16, 7, 9, 9, 11,
            12, 9, 11, 11, 13, 13, 9, 11, 11, 13, 13, 11, 13, 13, 16, 15,
            11, 13, 12, 15, 15, 9, 11, 11, 15, 14, 11, 13, 13, 17, 16, 10,
            12, 13, 15, 16, 14, 16, 16, 0, 18, 14, 14, 15, 15, 17, 10, 11,
            12, 15, 15, 11, 13, 13, 16, 16, 11, 13, 13, 16, 16, 14, 16, 16,
            19, 17, 14, 15, 15, 17, 17, 8, 10, 10, 14, 14, 10, 12, 11, 15,
            15, 10, 11, 12, 16, 15, 14, 15, 15, 18, 20, 13, 14, 16, 17, 18,
            9, 11, 11, 15, 16, 11, 13, 13, 17, 17, 11, 13, 13, 17, 16, 15,
            16, 16, 0, 0, 15, 16, 16, 0, 0, 9, 11, 11, 15, 15, 10, 13,
            12, 17, 15, 11, 13, 13, 17, 16, 15, 17, 15, 20, 19, 15, 16, 16,
            19, 0, 13, 15, 14, 0, 17, 14, 15, 16, 0, 20, 15, 16, 16, 0,
            19, 17, 18, 0, 0, 0, 16, 17, 18, 0, 0, 12, 14, 14, 19, 18,
            13, 15, 14, 0, 17, 14, 15, 16, 19, 19, 16, 18, 16, 0, 19, 19,
            20, 17, 20, 0, 8, 10, 10, 13, 14, 10, 11, 11, 15, 15, 10, 12,
            12, 15, 16, 14, 15, 14, 19, 16, 14, 15, 15, 0, 18, 9, 11, 11,
            16, 15, 11, 13, 13, 0, 16, 11, 12, 13, 16, 17, 14, 16, 17, 0,
            19, 15, 16, 16, 18, 0, 9, 11, 11, 15, 16, 11, 13, 13, 16, 16,
            11, 14, 13, 18, 17, 15, 16, 16, 18, 20, 15, 17, 19, 0, 0, 12,
            14, 14, 17, 17, 14, 16, 15, 0, 0, 13, 14, 15, 19, 0, 16, 18,
            20, 0, 0, 16, 16, 18, 18, 0, 12, 14, 14, 17, 20, 14, 16, 16,
            19, 0, 14, 16, 14, 0, 20, 16, 20, 17, 0, 0, 17, 0, 15, 0,
            19,
    };

    private static final float[] _vq_quantthresh__44u6__p3_0 = {
            -1.5f, -0.5f, 0.5f, 1.5f,
    };

    private static final int[] _vq_quantmap__44u6__p3_0 = {
            3, 1, 0, 2, 4,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p3_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p3_0,
            _vq_quantmap__44u6__p3_0,
            5,
            5
    );

    public static final StaticCodeBook _44u6__p3_0 = new StaticCodeBook(
            4, 625,
            _vq_lengthlist__44u6__p3_0,
            1, -533725184, 1611661312, 3, 0,
            _vq_quantlist__44u6__p3_0,
            null,
            _vq_auxt__44u6__p3_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p4_0 = {
            2,
            1,
            3,
            0,
            4,
    };

    private static final int[] _vq_lengthlist__44u6__p4_0 = {
            4, 5, 5, 8, 8, 6, 7, 6, 9, 9, 6, 6, 7, 9, 9, 8,
            9, 9, 11, 11, 8, 9, 9, 11, 11, 6, 7, 7, 9, 9, 7, 8,
            8, 10, 10, 7, 7, 8, 9, 10, 9, 10, 10, 11, 11, 9, 9, 10,
            11, 12, 6, 7, 7, 9, 9, 7, 8, 7, 10, 9, 7, 8, 8, 10,
            10, 9, 10, 9, 12, 11, 9, 10, 10, 12, 11, 8, 9, 9, 11, 11,
            9, 10, 10, 12, 12, 9, 10, 10, 12, 12, 11, 12, 12, 14, 13, 11,
            11, 12, 13, 13, 8, 9, 9, 11, 11, 9, 10, 10, 12, 12, 9, 10,
            10, 12, 12, 11, 12, 11, 13, 12, 11, 12, 12, 13, 13, 5, 7, 7,
            9, 9, 7, 8, 7, 10, 10, 7, 7, 8, 10, 10, 9, 10, 10, 12,
            11, 9, 10, 10, 11, 12, 7, 8, 8, 10, 10, 8, 8, 9, 11, 11,
            8, 9, 9, 11, 11, 10, 10, 11, 12, 13, 10, 10, 11, 12, 12, 6,
            7, 7, 10, 10, 7, 9, 8, 11, 10, 8, 8, 9, 10, 11, 10, 11,
            10, 13, 11, 10, 11, 11, 12, 12, 9, 10, 10, 12, 12, 10, 10, 11,
            13, 13, 10, 11, 11, 12, 13, 12, 12, 12, 13, 14, 12, 12, 13, 14,
            14, 9, 10, 10, 12, 12, 9, 10, 10, 13, 12, 10, 11, 11, 13, 13,
            11, 12, 11, 14, 12, 12, 13, 13, 14, 14, 6, 7, 7, 9, 9, 7,
            8, 7, 10, 10, 7, 8, 8, 10, 10, 9, 10, 10, 12, 11, 9, 10,
            10, 11, 12, 6, 7, 7, 10, 10, 8, 9, 8, 11, 10, 7, 8, 9,
            10, 11, 10, 11, 11, 12, 12, 10, 10, 11, 11, 13, 7, 8, 8, 10,
            10, 8, 9, 9, 11, 11, 8, 9, 8, 11, 11, 10, 11, 10, 13, 12,
            10, 11, 11, 13, 12, 9, 10, 10, 12, 12, 10, 11, 11, 13, 12, 9,
            10, 10, 12, 13, 12, 13, 12, 14, 14, 11, 11, 12, 12, 14, 9, 10,
            10, 12, 12, 10, 11, 11, 13, 13, 10, 11, 10, 13, 12, 12, 12, 12,
            14, 14, 12, 13, 12, 14, 13, 8, 9, 9, 11, 11, 9, 10, 10, 12,
            12, 9, 10, 10, 12, 12, 11, 12, 12, 14, 13, 11, 12, 12, 13, 14,
            9, 10, 10, 12, 12, 10, 11, 11, 13, 13, 10, 11, 11, 13, 13, 12,
            12, 13, 14, 15, 12, 12, 13, 14, 14, 9, 10, 10, 12, 12, 9, 11,
            10, 13, 12, 10, 10, 11, 12, 13, 12, 13, 12, 14, 13, 12, 12, 13,
            14, 15, 11, 12, 12, 14, 13, 11, 12, 12, 14, 14, 12, 13, 13, 14,
            14, 13, 13, 14, 14, 16, 13, 14, 14, 15, 15, 11, 12, 11, 13, 13,
            11, 12, 11, 14, 13, 12, 12, 13, 14, 15, 12, 14, 12, 15, 12, 13,
            14, 15, 15, 16, 8, 9, 9, 11, 11, 9, 10, 10, 12, 12, 9, 10,
            10, 12, 12, 11, 12, 12, 14, 13, 11, 12, 12, 13, 13, 9, 10, 10,
            12, 12, 10, 11, 10, 13, 12, 9, 10, 11, 12, 13, 12, 13, 12, 14,
            14, 12, 12, 13, 13, 14, 9, 10, 10, 12, 12, 10, 11, 11, 13, 13,
            10, 11, 11, 13, 13, 12, 13, 12, 14, 14, 12, 13, 13, 14, 14, 11,
            11, 11, 13, 13, 12, 13, 12, 14, 14, 11, 11, 12, 13, 14, 14, 14,
            14, 16, 15, 12, 12, 14, 12, 15, 11, 12, 12, 13, 14, 12, 13, 13,
            14, 15, 11, 12, 12, 14, 14, 13, 14, 14, 16, 16, 13, 14, 13, 16,
            13,
    };

    private static final float[] _vq_quantthresh__44u6__p4_0 = {
            -1.5f, -0.5f, 0.5f, 1.5f,
    };

    private static final int[] _vq_quantmap__44u6__p4_0 = {
            3, 1, 0, 2, 4,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p4_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p4_0,
            _vq_quantmap__44u6__p4_0,
            5,
            5
    );

    public static final StaticCodeBook _44u6__p4_0 = new StaticCodeBook(
            4, 625,
            _vq_lengthlist__44u6__p4_0,
            1, -533725184, 1611661312, 3, 0,
            _vq_quantlist__44u6__p4_0,
            null,
            _vq_auxt__44u6__p4_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p5_0 = {
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

    private static final int[] _vq_lengthlist__44u6__p5_0 = {
            2, 3, 3, 6, 6, 8, 8, 10, 10, 4, 5, 5, 8, 7, 8, 8,
            11, 11, 3, 5, 5, 7, 8, 8, 8, 11, 11, 6, 8, 7, 9, 9,
            10, 9, 12, 11, 6, 7, 8, 9, 9, 9, 10, 11, 12, 8, 8, 8,
            10, 9, 12, 11, 13, 13, 8, 8, 9, 9, 10, 11, 12, 13, 13, 10,
            11, 11, 12, 12, 13, 13, 14, 14, 10, 10, 11, 11, 12, 13, 13, 14,
            14,
    };

    private static final float[] _vq_quantthresh__44u6__p5_0 = {
            -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f,
    };

    private static final int[] _vq_quantmap__44u6__p5_0 = {
            7, 5, 3, 1, 0, 2, 4, 6,
            8,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p5_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p5_0,
            _vq_quantmap__44u6__p5_0,
            9,
            9
    );

    public static final StaticCodeBook _44u6__p5_0 = new StaticCodeBook(
            2, 81,
            _vq_lengthlist__44u6__p5_0,
            1, -531628032, 1611661312, 4, 0,
            _vq_quantlist__44u6__p5_0,
            null,
            _vq_auxt__44u6__p5_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p6_0 = {
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

    private static final int[] _vq_lengthlist__44u6__p6_0 = {
            3, 4, 4, 5, 5, 7, 7, 9, 9, 4, 5, 4, 6, 6, 7, 7,
            9, 9, 4, 4, 5, 6, 6, 7, 8, 9, 9, 5, 6, 6, 7, 7,
            8, 8, 10, 10, 5, 6, 6, 7, 7, 8, 8, 10, 10, 7, 8, 7,
            8, 8, 10, 9, 11, 11, 7, 7, 8, 8, 8, 9, 10, 10, 11, 9,
            9, 9, 10, 10, 11, 11, 12, 11, 9, 9, 9, 10, 10, 11, 11, 11,
            12,
    };

    private static final float[] _vq_quantthresh__44u6__p6_0 = {
            -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f,
    };

    private static final int[] _vq_quantmap__44u6__p6_0 = {
            7, 5, 3, 1, 0, 2, 4, 6,
            8,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p6_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p6_0,
            _vq_quantmap__44u6__p6_0,
            9,
            9
    );

    public static final StaticCodeBook _44u6__p6_0 = new StaticCodeBook(
            2, 81,
            _vq_lengthlist__44u6__p6_0,
            1, -531628032, 1611661312, 4, 0,
            _vq_quantlist__44u6__p6_0,
            null,
            _vq_auxt__44u6__p6_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p7_0 = {
            1,
            0,
            2,
    };

    private static final int[] _vq_lengthlist__44u6__p7_0 = {
            1, 4, 4, 5, 7, 7, 5, 7, 7, 5, 9, 8, 7, 10, 10, 8,
            10, 10, 5, 8, 9, 7, 10, 10, 7, 10, 9, 4, 8, 8, 9, 11,
            11, 8, 11, 11, 7, 11, 11, 10, 10, 13, 10, 13, 13, 7, 11, 11,
            10, 13, 12, 10, 13, 13, 5, 9, 8, 8, 11, 11, 9, 11, 11, 7,
            11, 11, 10, 13, 13, 10, 12, 13, 7, 11, 11, 10, 13, 13, 9, 13,
            10,
    };

    private static final float[] _vq_quantthresh__44u6__p7_0 = {
            -5.5f, 5.5f,
    };

    private static final int[] _vq_quantmap__44u6__p7_0 = {
            1, 0, 2,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p7_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p7_0,
            _vq_quantmap__44u6__p7_0,
            3,
            3
    );

    public static final StaticCodeBook _44u6__p7_0 = new StaticCodeBook(
            4, 81,
            _vq_lengthlist__44u6__p7_0,
            1, -529137664, 1618345984, 2, 0,
            _vq_quantlist__44u6__p7_0,
            null,
            _vq_auxt__44u6__p7_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p7_1 = {
            5,
            4,
            6,
            3,
            7,
            2,
            8,
            1,
            9,
            0,
            10,
    };

    private static final int[] _vq_lengthlist__44u6__p7_1 = {
            3, 4, 4, 6, 6, 7, 7, 8, 8, 8, 8, 4, 5, 5, 7, 6,
            8, 8, 8, 8, 8, 8, 4, 5, 5, 6, 7, 8, 8, 8, 8, 8,
            8, 6, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 6, 7, 7, 7,
            7, 8, 8, 8, 8, 8, 8, 7, 8, 8, 8, 8, 8, 8, 9, 9,
            9, 9, 7, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 8, 8, 8,
            8, 8, 9, 9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 9, 9, 9,
            9, 9, 9, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 8, 8,
            8, 8, 8, 9, 9, 9, 9, 9, 9,
    };

    private static final float[] _vq_quantthresh__44u6__p7_1 = {
            -4.5f, -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f,
            3.5f, 4.5f,
    };

    private static final int[] _vq_quantmap__44u6__p7_1 = {
            9, 7, 5, 3, 1, 0, 2, 4,
            6, 8, 10,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p7_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p7_1,
            _vq_quantmap__44u6__p7_1,
            11,
            11
    );

    public static final StaticCodeBook _44u6__p7_1 = new StaticCodeBook(
            2, 121,
            _vq_lengthlist__44u6__p7_1,
            1, -531365888, 1611661312, 4, 0,
            _vq_quantlist__44u6__p7_1,
            null,
            _vq_auxt__44u6__p7_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p8_0 = {
            5,
            4,
            6,
            3,
            7,
            2,
            8,
            1,
            9,
            0,
            10,
    };

    private static final int[] _vq_lengthlist__44u6__p8_0 = {
            1, 4, 4, 6, 6, 8, 8, 9, 9, 10, 10, 4, 6, 6, 7, 7,
            9, 9, 10, 10, 11, 11, 4, 6, 6, 7, 7, 9, 9, 10, 10, 11,
            11, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 6, 8, 8, 9,
            9, 10, 10, 11, 11, 12, 12, 8, 9, 9, 10, 10, 11, 11, 12, 12,
            13, 13, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 10, 10, 10,
            11, 11, 13, 13, 13, 13, 15, 14, 9, 10, 10, 12, 11, 12, 13, 13,
            13, 14, 15, 11, 12, 12, 13, 13, 13, 13, 15, 14, 15, 15, 11, 11,
            12, 13, 13, 14, 14, 14, 15, 15, 15,
    };

    private static final float[] _vq_quantthresh__44u6__p8_0 = {
            -49.5f, -38.5f, -27.5f, -16.5f, -5.5f, 5.5f, 16.5f, 27.5f,
            38.5f, 49.5f,
    };

    private static final int[] _vq_quantmap__44u6__p8_0 = {
            9, 7, 5, 3, 1, 0, 2, 4,
            6, 8, 10,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p8_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p8_0,
            _vq_quantmap__44u6__p8_0,
            11,
            11
    );

    public static final StaticCodeBook _44u6__p8_0 = new StaticCodeBook(
            2, 121,
            _vq_lengthlist__44u6__p8_0,
            1, -524582912, 1618345984, 4, 0,
            _vq_quantlist__44u6__p8_0,
            null,
            _vq_auxt__44u6__p8_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p8_1 = {
            5,
            4,
            6,
            3,
            7,
            2,
            8,
            1,
            9,
            0,
            10,
    };

    private static final int[] _vq_lengthlist__44u6__p8_1 = {
            3, 5, 5, 6, 6, 7, 7, 7, 7, 7, 7, 5, 6, 5, 7, 7,
            7, 7, 8, 7, 8, 8, 5, 5, 6, 6, 7, 7, 7, 7, 7, 8,
            8, 6, 7, 7, 7, 7, 8, 7, 8, 8, 8, 8, 6, 6, 7, 7,
            7, 7, 8, 8, 8, 8, 8, 7, 7, 7, 8, 8, 8, 8, 8, 8,
            8, 8, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 8, 7, 7, 7,
            8, 8, 8, 8, 8, 8, 8, 8, 7, 8, 8, 8, 8, 8, 8, 8,
            8, 8, 8, 7, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 7, 8,
            8, 8, 8, 8, 8, 8, 8, 8, 8,
    };

    private static final float[] _vq_quantthresh__44u6__p8_1 = {
            -4.5f, -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f,
            3.5f, 4.5f,
    };

    private static final int[] _vq_quantmap__44u6__p8_1 = {
            9, 7, 5, 3, 1, 0, 2, 4,
            6, 8, 10,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p8_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p8_1,
            _vq_quantmap__44u6__p8_1,
            11,
            11
    );

    public static final StaticCodeBook _44u6__p8_1 = new StaticCodeBook(
            2, 121,
            _vq_lengthlist__44u6__p8_1,
            1, -531365888, 1611661312, 4, 0,
            _vq_quantlist__44u6__p8_1,
            null,
            _vq_auxt__44u6__p8_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p9_0 = {
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

    private static final int[] _vq_lengthlist__44u6__p9_0 = {
            1, 3, 2, 9, 8, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 4,
            8, 9, 13, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 5, 8,
            9, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 11, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 11, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14,
    };

    private static final float[] _vq_quantthresh__44u6__p9_0 = {
            -1657.5f, -1402.5f, -1147.5f, -892.5f, -637.5f, -382.5f, -127.5f, 127.5f,
            382.5f, 637.5f, 892.5f, 1147.5f, 1402.5f, 1657.5f,
    };

    private static final int[] _vq_quantmap__44u6__p9_0 = {
            13, 11, 9, 7, 5, 3, 1, 0,
            2, 4, 6, 8, 10, 12, 14,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p9_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p9_0,
            _vq_quantmap__44u6__p9_0,
            15,
            15
    );

    public static final StaticCodeBook _44u6__p9_0 = new StaticCodeBook(
            2, 225,
            _vq_lengthlist__44u6__p9_0,
            1, -514071552, 1627381760, 4, 0,
            _vq_quantlist__44u6__p9_0,
            null,
            _vq_auxt__44u6__p9_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p9_1 = {
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

    private static final int[] _vq_lengthlist__44u6__p9_1 = {
            1, 4, 4, 7, 7, 8, 9, 8, 8, 9, 8, 9, 8, 9, 9, 4,
            7, 6, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 4, 7,
            6, 9, 9, 10, 10, 9, 9, 10, 10, 10, 10, 11, 11, 7, 9, 8,
            10, 10, 11, 11, 10, 10, 11, 11, 11, 11, 11, 11, 7, 8, 9, 10,
            10, 11, 11, 10, 10, 11, 11, 11, 11, 11, 12, 8, 10, 10, 11, 11,
            12, 12, 11, 11, 12, 12, 12, 12, 13, 12, 8, 10, 10, 11, 11, 12,
            11, 11, 11, 11, 12, 12, 12, 12, 13, 8, 9, 9, 11, 10, 11, 11,
            12, 12, 12, 12, 13, 12, 13, 12, 8, 9, 9, 11, 11, 11, 11, 12,
            12, 12, 12, 12, 13, 13, 13, 9, 10, 10, 11, 12, 12, 12, 12, 12,
            13, 13, 13, 13, 13, 13, 9, 10, 10, 11, 11, 12, 12, 12, 12, 13,
            13, 13, 13, 14, 13, 10, 10, 10, 12, 11, 12, 12, 13, 13, 13, 13,
            13, 13, 13, 13, 10, 10, 11, 11, 11, 12, 12, 13, 13, 13, 13, 13,
            13, 13, 13, 10, 11, 11, 12, 12, 13, 12, 12, 13, 13, 13, 13, 13,
            13, 14, 10, 11, 11, 12, 12, 13, 12, 13, 13, 13, 14, 13, 13, 14,
            13,
    };

    private static final float[] _vq_quantthresh__44u6__p9_1 = {
            -110.5f, -93.5f, -76.5f, -59.5f, -42.5f, -25.5f, -8.5f, 8.5f,
            25.5f, 42.5f, 59.5f, 76.5f, 93.5f, 110.5f,
    };

    private static final int[] _vq_quantmap__44u6__p9_1 = {
            13, 11, 9, 7, 5, 3, 1, 0,
            2, 4, 6, 8, 10, 12, 14,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p9_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p9_1,
            _vq_quantmap__44u6__p9_1,
            15,
            15
    );

    public static final StaticCodeBook _44u6__p9_1 = new StaticCodeBook(
            2, 225,
            _vq_lengthlist__44u6__p9_1,
            1, -522338304, 1620115456, 4, 0,
            _vq_quantlist__44u6__p9_1,
            null,
            _vq_auxt__44u6__p9_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u6__p9_2 = {
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

    private static final int[] _vq_lengthlist__44u6__p9_2 = {
            3, 5, 5, 7, 7, 8, 8, 8, 8, 8, 8, 9, 8, 8, 9, 9,
            9, 5, 6, 6, 7, 7, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9,
            9, 9, 5, 6, 6, 7, 7, 8, 8, 8, 8, 8, 8, 9, 9, 9,
            9, 9, 9, 7, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 7, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 8, 8, 8, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 8, 8, 8, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 8, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 10, 9, 8, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 10, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 10, 9, 9, 9, 10, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 10, 9, 9, 9, 10, 9, 9, 10, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 10, 9, 10, 9, 10, 10,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 9, 10, 10, 9, 9,
            10,
    };

    private static final float[] _vq_quantthresh__44u6__p9_2 = {
            -7.5f, -6.5f, -5.5f, -4.5f, -3.5f, -2.5f, -1.5f, -0.5f,
            0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f,
    };

    private static final int[] _vq_quantmap__44u6__p9_2 = {
            15, 13, 11, 9, 7, 5, 3, 1,
            0, 2, 4, 6, 8, 10, 12, 14,
            16,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u6__p9_2 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u6__p9_2,
            _vq_quantmap__44u6__p9_2,
            17,
            17
    );

    public static final StaticCodeBook _44u6__p9_2 = new StaticCodeBook(
            2, 289,
            _vq_lengthlist__44u6__p9_2,
            1, -529530880, 1611661312, 5, 0,
            _vq_quantlist__44u6__p9_2,
            null,
            _vq_auxt__44u6__p9_2,
            null,
            0
    );

    private static final int[] _huff_lengthlist__44u6__short = {
            4, 11, 16, 13, 17, 13, 17, 16, 17, 17, 4, 7, 9, 9, 13, 10,
            16, 12, 16, 17, 7, 6, 5, 7, 8, 9, 12, 12, 16, 17, 6, 9,
            7, 9, 10, 10, 15, 15, 17, 17, 6, 7, 5, 7, 5, 7, 7, 10,
            16, 17, 7, 9, 8, 9, 8, 10, 11, 11, 15, 17, 7, 7, 7, 8,
            5, 8, 8, 9, 15, 17, 8, 7, 9, 9, 7, 8, 7, 2, 7, 15,
            14, 13, 13, 15, 5, 10, 4, 3, 6, 17, 17, 15, 13, 17, 7, 11,
            7, 6, 9, 16,
    };

    public static final StaticCodeBook _huff_book__44u6__short = new StaticCodeBook(
            2, 100,
            _huff_lengthlist__44u6__short,
            0, 0, 0, 0, 0,
            null,
            null,
            null,
            null,
            0
    );
}
