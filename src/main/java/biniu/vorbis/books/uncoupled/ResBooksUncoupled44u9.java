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
public class ResBooksUncoupled44u9 {

    private static final int[] _huff_lengthlist__44u9__long = {
            3, 9, 13, 13, 14, 15, 14, 14, 15, 15, 5, 5, 9, 10, 12, 12,
            13, 14, 16, 15, 10, 6, 6, 6, 8, 11, 12, 13, 16, 15, 11, 7,
            5, 3, 5, 8, 10, 12, 15, 15, 10, 10, 7, 4, 3, 5, 8, 10,
            12, 12, 12, 12, 9, 7, 5, 4, 6, 8, 10, 13, 13, 12, 11, 9,
            7, 5, 5, 6, 9, 12, 14, 12, 12, 10, 8, 6, 6, 6, 7, 11,
            13, 12, 14, 13, 10, 8, 7, 7, 7, 10, 11, 11, 12, 13, 12, 11,
            10, 8, 8, 9,
    };

    public static final StaticCodeBook _huff_book__44u9__long = new StaticCodeBook(
            2, 100,
            _huff_lengthlist__44u9__long,
            0, 0, 0, 0, 0,
            null,
            null,
            null,
            null,
            0
    );

    private static final int[] _huff_lengthlist__44u9__short = {
            9, 16, 18, 18, 17, 17, 17, 17, 17, 17, 5, 8, 11, 12, 11, 12,
            17, 17, 16, 16, 6, 6, 8, 8, 9, 10, 14, 15, 16, 16, 6, 7,
            7, 4, 6, 9, 13, 16, 16, 16, 6, 6, 7, 4, 5, 8, 11, 15,
            17, 16, 7, 6, 7, 6, 6, 8, 9, 10, 14, 16, 11, 8, 8, 7,
            6, 6, 3, 4, 10, 15, 14, 12, 12, 10, 5, 6, 3, 3, 8, 13,
            15, 17, 15, 11, 6, 8, 6, 6, 9, 14, 17, 15, 15, 12, 8, 10,
            9, 9, 12, 15,
    };

    public static final StaticCodeBook _huff_book__44u9__short = new StaticCodeBook(
            2, 100,
            _huff_lengthlist__44u9__short,
            0, 0, 0, 0, 0,
            null,
            null,
            null,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p1_0 = {
            1,
            0,
            2,
    };

    private static final int[] _vq_lengthlist__44u9_p1_0 = {
            1, 5, 5, 5, 7, 7, 5, 7, 7, 5, 7, 7, 7, 9, 9, 7,
            9, 9, 5, 7, 7, 7, 9, 9, 7, 9, 9, 5, 7, 7, 7, 9,
            9, 7, 9, 9, 8, 9, 9, 9, 10, 11, 9, 11, 11, 7, 9, 9,
            9, 11, 10, 9, 11, 11, 5, 7, 7, 7, 9, 9, 8, 9, 10, 7,
            9, 9, 9, 11, 11, 9, 10, 11, 7, 9, 10, 9, 11, 11, 9, 11,
            10,
    };

    private static final float[] _vq_quantthresh__44u9_p1_0 = {
            -0.5f, 0.5f,
    };

    private static final int[] _vq_quantmap__44u9_p1_0 = {
            1, 0, 2,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p1_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p1_0,
            _vq_quantmap__44u9_p1_0,
            3,
            3
    );

    public static final StaticCodeBook _44u9_p1_0 = new StaticCodeBook(
            4, 81,
            _vq_lengthlist__44u9_p1_0,
            1, -535822336, 1611661312, 2, 0,
            _vq_quantlist__44u9_p1_0,
            null,
            _vq_auxt__44u9_p1_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p2_0 = {
            2,
            1,
            3,
            0,
            4,
    };

    private static final int[] _vq_lengthlist__44u9_p2_0 = {
            3, 5, 5, 8, 8, 5, 7, 7, 9, 9, 6, 7, 7, 9, 9, 8,
            9, 9, 11, 10, 8, 9, 9, 11, 11, 6, 7, 7, 9, 9, 7, 8,
            8, 10, 10, 7, 8, 8, 9, 10, 9, 10, 10, 11, 11, 9, 9, 10,
            11, 11, 6, 7, 7, 9, 9, 7, 8, 8, 10, 9, 7, 8, 8, 10,
            10, 9, 10, 9, 11, 11, 9, 10, 10, 11, 11, 8, 9, 9, 11, 11,
            9, 10, 10, 12, 11, 9, 10, 10, 11, 12, 11, 11, 11, 13, 13, 11,
            11, 11, 12, 13, 8, 9, 9, 11, 11, 9, 10, 10, 11, 11, 9, 10,
            10, 12, 11, 11, 12, 11, 13, 12, 11, 11, 12, 13, 13, 6, 7, 7,
            9, 9, 7, 8, 8, 10, 10, 7, 8, 8, 10, 10, 9, 10, 10, 12,
            11, 9, 10, 10, 11, 12, 7, 8, 8, 10, 10, 8, 9, 9, 11, 11,
            8, 9, 9, 10, 10, 10, 11, 11, 12, 12, 10, 10, 11, 12, 12, 7,
            8, 8, 10, 10, 8, 9, 8, 10, 10, 8, 9, 9, 10, 10, 10, 11,
            10, 12, 11, 10, 10, 11, 12, 12, 9, 10, 10, 11, 12, 10, 11, 11,
            12, 12, 10, 11, 10, 12, 12, 12, 12, 12, 13, 13, 11, 12, 12, 13,
            13, 9, 10, 10, 11, 11, 9, 10, 10, 12, 12, 10, 11, 11, 12, 13,
            11, 12, 11, 13, 12, 12, 12, 12, 13, 14, 6, 7, 7, 9, 9, 7,
            8, 8, 10, 10, 7, 8, 8, 10, 10, 9, 10, 10, 11, 11, 9, 10,
            10, 11, 12, 7, 8, 8, 10, 10, 8, 9, 9, 11, 10, 8, 8, 9,
            10, 10, 10, 11, 10, 12, 12, 10, 10, 11, 11, 12, 7, 8, 8, 10,
            10, 8, 9, 9, 10, 10, 8, 9, 9, 10, 10, 10, 11, 10, 12, 12,
            10, 11, 10, 12, 12, 9, 10, 10, 12, 11, 10, 11, 11, 12, 12, 9,
            10, 10, 12, 12, 12, 12, 12, 13, 13, 11, 11, 12, 12, 14, 9, 10,
            10, 11, 12, 10, 11, 11, 12, 12, 10, 11, 11, 12, 12, 11, 12, 12,
            14, 14, 12, 12, 12, 13, 13, 8, 9, 9, 11, 11, 9, 10, 10, 12,
            11, 9, 10, 10, 12, 12, 11, 12, 11, 13, 13, 11, 11, 12, 13, 13,
            9, 10, 10, 12, 12, 10, 11, 11, 12, 12, 10, 11, 11, 12, 12, 12,
            12, 12, 14, 14, 12, 12, 12, 13, 13, 9, 10, 10, 12, 11, 10, 11,
            10, 12, 12, 10, 11, 11, 12, 12, 11, 12, 12, 14, 13, 12, 12, 12,
            13, 14, 11, 12, 11, 13, 13, 11, 12, 12, 13, 13, 12, 12, 12, 14,
            14, 13, 13, 13, 13, 15, 13, 13, 14, 15, 15, 11, 11, 11, 13, 13,
            11, 12, 11, 13, 13, 11, 12, 12, 13, 13, 12, 13, 12, 15, 13, 13,
            13, 14, 14, 15, 8, 9, 9, 11, 11, 9, 10, 10, 11, 12, 9, 10,
            10, 11, 12, 11, 12, 11, 13, 13, 11, 12, 12, 13, 13, 9, 10, 10,
            11, 12, 10, 11, 10, 12, 12, 10, 10, 11, 12, 13, 12, 12, 12, 14,
            13, 11, 12, 12, 13, 14, 9, 10, 10, 12, 12, 10, 11, 11, 12, 12,
            10, 11, 11, 12, 12, 12, 12, 12, 14, 13, 12, 12, 12, 14, 13, 11,
            11, 11, 13, 13, 11, 12, 12, 14, 13, 11, 11, 12, 13, 13, 13, 13,
            13, 15, 14, 12, 12, 13, 13, 15, 11, 12, 12, 13, 13, 12, 12, 12,
            13, 14, 11, 12, 12, 13, 13, 13, 13, 14, 14, 15, 13, 13, 13, 14,
            14,
    };

    private static final float[] _vq_quantthresh__44u9_p2_0 = {
            -1.5f, -0.5f, 0.5f, 1.5f,
    };

    private static final int[] _vq_quantmap__44u9_p2_0 = {
            3, 1, 0, 2, 4,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p2_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p2_0,
            _vq_quantmap__44u9_p2_0,
            5,
            5
    );

    public static final StaticCodeBook _44u9_p2_0 = new StaticCodeBook(
            4, 625,
            _vq_lengthlist__44u9_p2_0,
            1, -533725184, 1611661312, 3, 0,
            _vq_quantlist__44u9_p2_0,
            null,
            _vq_auxt__44u9_p2_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p3_0 = {
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

    private static final int[] _vq_lengthlist__44u9_p3_0 = {
            3, 4, 4, 5, 5, 7, 7, 8, 8, 4, 5, 5, 6, 6, 7, 7,
            9, 9, 4, 4, 5, 6, 6, 7, 7, 9, 9, 5, 6, 6, 7, 7,
            8, 8, 9, 9, 5, 6, 6, 7, 7, 8, 8, 9, 9, 7, 7, 7,
            8, 8, 9, 9, 10, 10, 7, 7, 7, 8, 8, 9, 9, 10, 10, 8,
            9, 9, 10, 9, 10, 10, 11, 11, 8, 9, 9, 9, 10, 10, 10, 11,
            11,
    };

    private static final float[] _vq_quantthresh__44u9_p3_0 = {
            -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f,
    };

    private static final int[] _vq_quantmap__44u9_p3_0 = {
            7, 5, 3, 1, 0, 2, 4, 6,
            8,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p3_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p3_0,
            _vq_quantmap__44u9_p3_0,
            9,
            9
    );

    public static final StaticCodeBook _44u9_p3_0 = new StaticCodeBook(
            2, 81,
            _vq_lengthlist__44u9_p3_0,
            1, -531628032, 1611661312, 4, 0,
            _vq_quantlist__44u9_p3_0,
            null,
            _vq_auxt__44u9_p3_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p4_0 = {
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

    private static final int[] _vq_lengthlist__44u9_p4_0 = {
            4, 5, 5, 6, 6, 7, 7, 8, 8, 8, 8, 9, 9, 10, 10, 11,
            11, 5, 5, 5, 6, 6, 7, 7, 8, 8, 8, 8, 9, 9, 10, 10,
            11, 11, 5, 5, 5, 6, 6, 7, 7, 8, 8, 8, 8, 9, 9, 10,
            10, 11, 11, 6, 6, 6, 7, 6, 7, 7, 8, 8, 9, 9, 10, 10,
            11, 11, 12, 11, 6, 6, 6, 6, 7, 7, 7, 8, 8, 9, 9, 10,
            10, 11, 11, 11, 12, 7, 7, 7, 7, 7, 8, 8, 9, 9, 9, 9,
            10, 10, 11, 11, 12, 12, 7, 7, 7, 7, 7, 8, 8, 9, 9, 9,
            9, 10, 10, 11, 11, 12, 12, 8, 8, 8, 8, 8, 9, 8, 10, 9,
            10, 10, 11, 10, 12, 11, 13, 12, 8, 8, 8, 8, 8, 9, 9, 9,
            10, 10, 10, 10, 11, 11, 12, 12, 12, 8, 8, 8, 9, 9, 9, 9,
            10, 10, 11, 10, 12, 11, 12, 12, 13, 12, 8, 8, 8, 9, 9, 9,
            9, 10, 10, 10, 11, 11, 11, 12, 12, 12, 13, 9, 9, 9, 10, 10,
            10, 10, 11, 10, 11, 11, 12, 11, 13, 12, 13, 13, 9, 9, 10, 10,
            10, 10, 10, 10, 11, 11, 11, 11, 12, 12, 13, 13, 13, 10, 11, 10,
            11, 11, 11, 11, 12, 11, 12, 12, 13, 12, 13, 13, 14, 13, 10, 10,
            10, 11, 11, 11, 11, 11, 12, 12, 12, 12, 13, 13, 13, 13, 14, 11,
            11, 11, 12, 11, 12, 12, 12, 12, 13, 13, 13, 13, 14, 13, 14, 14,
            11, 11, 11, 11, 12, 12, 12, 12, 12, 12, 13, 13, 13, 13, 14, 14,
            14,
    };

    private static final float[] _vq_quantthresh__44u9_p4_0 = {
            -7.5f, -6.5f, -5.5f, -4.5f, -3.5f, -2.5f, -1.5f, -0.5f,
            0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f,
    };

    private static final int[] _vq_quantmap__44u9_p4_0 = {
            15, 13, 11, 9, 7, 5, 3, 1,
            0, 2, 4, 6, 8, 10, 12, 14,
            16,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p4_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p4_0,
            _vq_quantmap__44u9_p4_0,
            17,
            17
    );

    public static final StaticCodeBook _44u9_p4_0 = new StaticCodeBook(
            2, 289,
            _vq_lengthlist__44u9_p4_0,
            1, -529530880, 1611661312, 5, 0,
            _vq_quantlist__44u9_p4_0,
            null,
            _vq_auxt__44u9_p4_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p5_0 = {
            1,
            0,
            2,
    };

    private static final int[] _vq_lengthlist__44u9_p5_0 = {
            1, 4, 4, 5, 7, 7, 5, 7, 7, 5, 8, 8, 8, 9, 9, 7,
            9, 9, 5, 8, 8, 7, 9, 9, 8, 9, 9, 5, 8, 8, 8, 10,
            10, 8, 10, 10, 7, 10, 10, 9, 10, 12, 9, 11, 11, 7, 10, 10,
            9, 11, 10, 9, 11, 12, 5, 8, 8, 8, 10, 10, 8, 10, 10, 7,
            10, 10, 9, 12, 11, 9, 10, 11, 7, 10, 10, 9, 11, 11, 10, 12,
            10,
    };

    private static final float[] _vq_quantthresh__44u9_p5_0 = {
            -5.5f, 5.5f,
    };

    private static final int[] _vq_quantmap__44u9_p5_0 = {
            1, 0, 2,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p5_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p5_0,
            _vq_quantmap__44u9_p5_0,
            3,
            3
    );

    public static final StaticCodeBook _44u9_p5_0 = new StaticCodeBook(
            4, 81,
            _vq_lengthlist__44u9_p5_0,
            1, -529137664, 1618345984, 2, 0,
            _vq_quantlist__44u9_p5_0,
            null,
            _vq_auxt__44u9_p5_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p5_1 = {
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

    private static final int[] _vq_lengthlist__44u9_p5_1 = {
            5, 5, 5, 6, 6, 7, 7, 7, 7, 7, 7, 5, 6, 6, 6, 6,
            7, 7, 7, 7, 8, 7, 5, 6, 6, 6, 6, 7, 7, 7, 7, 7,
            7, 6, 6, 6, 7, 7, 7, 7, 7, 7, 8, 8, 6, 6, 6, 7,
            7, 7, 7, 7, 7, 8, 8, 7, 7, 7, 7, 7, 8, 7, 8, 8,
            8, 8, 7, 7, 7, 7, 7, 7, 8, 8, 8, 8, 8, 7, 7, 7,
            8, 7, 8, 8, 8, 8, 8, 8, 7, 7, 7, 7, 8, 8, 8, 8,
            8, 8, 8, 7, 8, 7, 8, 8, 8, 8, 8, 8, 8, 8, 7, 8,
            8, 8, 8, 8, 8, 8, 8, 8, 8,
    };

    private static final float[] _vq_quantthresh__44u9_p5_1 = {
            -4.5f, -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f,
            3.5f, 4.5f,
    };

    private static final int[] _vq_quantmap__44u9_p5_1 = {
            9, 7, 5, 3, 1, 0, 2, 4,
            6, 8, 10,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p5_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p5_1,
            _vq_quantmap__44u9_p5_1,
            11,
            11
    );

    public static final StaticCodeBook _44u9_p5_1 = new StaticCodeBook(
            2, 121,
            _vq_lengthlist__44u9_p5_1,
            1, -531365888, 1611661312, 4, 0,
            _vq_quantlist__44u9_p5_1,
            null,
            _vq_auxt__44u9_p5_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p6_0 = {
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

    private static final int[] _vq_lengthlist__44u9_p6_0 = {
            2, 4, 4, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 4, 6, 5,
            7, 7, 8, 8, 8, 8, 9, 9, 10, 10, 4, 5, 6, 7, 7, 8,
            8, 8, 8, 9, 9, 10, 10, 6, 7, 7, 8, 8, 8, 8, 9, 9,
            10, 10, 10, 10, 6, 7, 7, 8, 8, 8, 8, 9, 9, 10, 10, 10,
            10, 7, 8, 8, 8, 8, 9, 9, 9, 9, 10, 10, 11, 11, 7, 8,
            8, 8, 8, 9, 9, 9, 9, 10, 10, 11, 11, 8, 8, 8, 9, 9,
            9, 9, 9, 10, 10, 10, 11, 11, 8, 8, 8, 9, 9, 9, 9, 10,
            9, 10, 10, 11, 11, 9, 9, 9, 10, 10, 10, 10, 10, 11, 11, 11,
            11, 12, 9, 9, 9, 10, 10, 10, 10, 10, 10, 11, 10, 12, 11, 10,
            10, 10, 10, 10, 11, 11, 11, 11, 11, 12, 12, 12, 10, 10, 10, 10,
            10, 11, 11, 11, 11, 12, 11, 12, 12,
    };

    private static final float[] _vq_quantthresh__44u9_p6_0 = {
            -27.5f, -22.5f, -17.5f, -12.5f, -7.5f, -2.5f, 2.5f, 7.5f,
            12.5f, 17.5f, 22.5f, 27.5f,
    };

    private static final int[] _vq_quantmap__44u9_p6_0 = {
            11, 9, 7, 5, 3, 1, 0, 2,
            4, 6, 8, 10, 12,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p6_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p6_0,
            _vq_quantmap__44u9_p6_0,
            13,
            13
    );

    public static final StaticCodeBook _44u9_p6_0 = new StaticCodeBook(
            2, 169,
            _vq_lengthlist__44u9_p6_0,
            1, -526516224, 1616117760, 4, 0,
            _vq_quantlist__44u9_p6_0,
            null,
            _vq_auxt__44u9_p6_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p6_1 = {
            2,
            1,
            3,
            0,
            4,
    };

    private static final int[] _vq_lengthlist__44u9_p6_1 = {
            4, 4, 4, 5, 5, 4, 5, 4, 5, 5, 4, 4, 5, 5, 5, 5,
            5, 5, 5, 5, 5, 5, 5, 5, 5,
    };

    private static final float[] _vq_quantthresh__44u9_p6_1 = {
            -1.5f, -0.5f, 0.5f, 1.5f,
    };

    private static final int[] _vq_quantmap__44u9_p6_1 = {
            3, 1, 0, 2, 4,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p6_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p6_1,
            _vq_quantmap__44u9_p6_1,
            5,
            5
    );

    public static final StaticCodeBook _44u9_p6_1 = new StaticCodeBook(
            2, 25,
            _vq_lengthlist__44u9_p6_1,
            1, -533725184, 1611661312, 3, 0,
            _vq_quantlist__44u9_p6_1,
            null,
            _vq_auxt__44u9_p6_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p7_0 = {
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

    private static final int[] _vq_lengthlist__44u9_p7_0 = {
            1, 4, 5, 6, 6, 7, 7, 8, 9, 10, 10, 11, 11, 5, 6, 6,
            7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 5, 6, 6, 7, 7, 8,
            8, 9, 9, 10, 10, 11, 11, 6, 7, 7, 8, 8, 9, 9, 10, 10,
            11, 11, 12, 12, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12,
            12, 8, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 8, 8,
            8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 9, 9, 9, 10, 10,
            11, 11, 12, 12, 13, 13, 13, 13, 9, 9, 9, 10, 10, 11, 11, 12,
            12, 13, 13, 14, 14, 10, 10, 10, 11, 11, 12, 12, 13, 13, 14, 13,
            15, 14, 10, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 14, 14, 11,
            11, 12, 12, 12, 13, 13, 14, 14, 14, 14, 15, 15, 11, 11, 12, 12,
            12, 13, 13, 14, 14, 14, 15, 15, 15,
    };

    private static final float[] _vq_quantthresh__44u9_p7_0 = {
            -60.5f, -49.5f, -38.5f, -27.5f, -16.5f, -5.5f, 5.5f, 16.5f,
            27.5f, 38.5f, 49.5f, 60.5f,
    };

    private static final int[] _vq_quantmap__44u9_p7_0 = {
            11, 9, 7, 5, 3, 1, 0, 2,
            4, 6, 8, 10, 12,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p7_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p7_0,
            _vq_quantmap__44u9_p7_0,
            13,
            13
    );

    public static final StaticCodeBook _44u9_p7_0 = new StaticCodeBook(
            2, 169,
            _vq_lengthlist__44u9_p7_0,
            1, -523206656, 1618345984, 4, 0,
            _vq_quantlist__44u9_p7_0,
            null,
            _vq_auxt__44u9_p7_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p7_1 = {
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

    private static final int[] _vq_lengthlist__44u9_p7_1 = {
            5, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 6, 6, 6, 7, 7,
            7, 7, 7, 7, 7, 7, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, 7, 7, 7, 8, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, 8, 8, 7, 7, 7, 7, 7, 7, 7, 8, 7, 8, 8, 7, 7,
            7, 7, 7, 7, 7, 8, 8, 8, 8,
    };

    private static final float[] _vq_quantthresh__44u9_p7_1 = {
            -4.5f, -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f,
            3.5f, 4.5f,
    };

    private static final int[] _vq_quantmap__44u9_p7_1 = {
            9, 7, 5, 3, 1, 0, 2, 4,
            6, 8, 10,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p7_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p7_1,
            _vq_quantmap__44u9_p7_1,
            11,
            11
    );

    public static final StaticCodeBook _44u9_p7_1 = new StaticCodeBook(
            2, 121,
            _vq_lengthlist__44u9_p7_1,
            1, -531365888, 1611661312, 4, 0,
            _vq_quantlist__44u9_p7_1,
            null,
            _vq_auxt__44u9_p7_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p8_0 = {
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

    private static final int[] _vq_lengthlist__44u9_p8_0 = {
            1, 4, 4, 7, 7, 8, 8, 8, 8, 9, 9, 10, 9, 11, 10, 4,
            6, 6, 8, 8, 9, 9, 9, 9, 10, 10, 11, 10, 12, 10, 4, 6,
            6, 8, 8, 9, 10, 9, 9, 10, 10, 11, 11, 12, 12, 7, 8, 8,
            10, 10, 11, 11, 10, 10, 11, 11, 12, 12, 13, 12, 7, 8, 8, 10,
            10, 11, 11, 10, 10, 11, 11, 12, 12, 12, 13, 8, 10, 9, 11, 11,
            12, 12, 11, 11, 12, 12, 13, 13, 14, 13, 8, 9, 9, 11, 11, 12,
            12, 11, 12, 12, 12, 13, 13, 14, 13, 8, 9, 9, 10, 10, 12, 11,
            13, 12, 13, 13, 14, 13, 15, 14, 8, 9, 9, 10, 10, 11, 12, 12,
            12, 13, 13, 13, 14, 14, 14, 9, 10, 10, 12, 11, 13, 12, 13, 13,
            14, 13, 14, 14, 14, 15, 9, 10, 10, 11, 12, 12, 12, 13, 13, 14,
            14, 14, 15, 15, 15, 10, 11, 11, 12, 12, 13, 13, 14, 14, 14, 14,
            15, 14, 16, 15, 10, 11, 11, 12, 12, 13, 13, 13, 14, 14, 14, 14,
            14, 15, 16, 11, 12, 12, 13, 13, 14, 13, 14, 14, 15, 14, 15, 16,
            16, 16, 11, 12, 12, 13, 13, 14, 13, 14, 14, 15, 15, 15, 16, 15,
            15,
    };

    private static final float[] _vq_quantthresh__44u9_p8_0 = {
            -136.5f, -115.5f, -94.5f, -73.5f, -52.5f, -31.5f, -10.5f, 10.5f,
            31.5f, 52.5f, 73.5f, 94.5f, 115.5f, 136.5f,
    };

    private static final int[] _vq_quantmap__44u9_p8_0 = {
            13, 11, 9, 7, 5, 3, 1, 0,
            2, 4, 6, 8, 10, 12, 14,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p8_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p8_0,
            _vq_quantmap__44u9_p8_0,
            15,
            15
    );

    public static final StaticCodeBook _44u9_p8_0 = new StaticCodeBook(
            2, 225,
            _vq_lengthlist__44u9_p8_0,
            1, -520986624, 1620377600, 4, 0,
            _vq_quantlist__44u9_p8_0,
            null,
            _vq_auxt__44u9_p8_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p8_1 = {
            10,
            9,
            11,
            8,
            12,
            7,
            13,
            6,
            14,
            5,
            15,
            4,
            16,
            3,
            17,
            2,
            18,
            1,
            19,
            0,
            20,
    };

    private static final int[] _vq_lengthlist__44u9_p8_1 = {
            4, 6, 6, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 6, 6, 6, 7, 7, 8, 8, 8, 8, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 6, 6, 6, 7, 7, 8,
            8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7,
            7, 7, 8, 8, 8, 8, 9, 8, 9, 9, 9, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 7, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 10, 9, 10, 10, 10, 8, 8,
            8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            9, 10, 10, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            10, 9, 10, 9, 10, 10, 10, 10, 8, 8, 8, 9, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 10, 10, 9, 10, 10, 10, 10, 10, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 9, 10, 9, 10, 10, 10, 10, 10, 10,
            10, 10, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 10,
            10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            9, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 9,
            9, 9, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            10, 9, 9, 9, 9, 9, 9, 9, 10, 9, 10, 10, 10, 10, 10, 10,
            10, 10, 10, 10, 10, 10, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 9, 9,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            9, 9, 9, 9, 10, 9, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            10, 10, 10, 10, 10, 9, 9, 9, 10, 9, 10, 9, 10, 10, 10, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9, 9, 9, 10, 9, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 9,
            9, 9, 9, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            10, 10, 10, 10, 9, 9, 9, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10,
    };

    private static final float[] _vq_quantthresh__44u9_p8_1 = {
            -9.5f, -8.5f, -7.5f, -6.5f, -5.5f, -4.5f, -3.5f, -2.5f,
            -1.5f, -0.5f, 0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f,
            6.5f, 7.5f, 8.5f, 9.5f,
    };

    private static final int[] _vq_quantmap__44u9_p8_1 = {
            19, 17, 15, 13, 11, 9, 7, 5,
            3, 1, 0, 2, 4, 6, 8, 10,
            12, 14, 16, 18, 20,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p8_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p8_1,
            _vq_quantmap__44u9_p8_1,
            21,
            21
    );

    public static final StaticCodeBook _44u9_p8_1 = new StaticCodeBook(
            2, 441,
            _vq_lengthlist__44u9_p8_1,
            1, -529268736, 1611661312, 5, 0,
            _vq_quantlist__44u9_p8_1,
            null,
            _vq_auxt__44u9_p8_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p9_0 = {
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

    private static final int[] _vq_lengthlist__44u9_p9_0 = {
            1, 3, 3, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 4,
            10, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 4, 10,
            10, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10,
            10,
    };

    private static final float[] _vq_quantthresh__44u9_p9_0 = {
            -6051.5f, -5120.5f, -4189.5f, -3258.5f, -2327.5f, -1396.5f, -465.5f, 465.5f,
            1396.5f, 2327.5f, 3258.5f, 4189.5f, 5120.5f, 6051.5f,
    };

    private static final int[] _vq_quantmap__44u9_p9_0 = {
            13, 11, 9, 7, 5, 3, 1, 0,
            2, 4, 6, 8, 10, 12, 14,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p9_0 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p9_0,
            _vq_quantmap__44u9_p9_0,
            15,
            15
    );

    public static final StaticCodeBook _44u9_p9_0 = new StaticCodeBook(
            2, 225,
            _vq_lengthlist__44u9_p9_0,
            1, -510036736, 1631393792, 4, 0,
            _vq_quantlist__44u9_p9_0,
            null,
            _vq_auxt__44u9_p9_0,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p9_1 = {
            9,
            8,
            10,
            7,
            11,
            6,
            12,
            5,
            13,
            4,
            14,
            3,
            15,
            2,
            16,
            1,
            17,
            0,
            18,
    };

    private static final int[] _vq_lengthlist__44u9_p9_1 = {
            1, 4, 4, 7, 7, 8, 7, 8, 7, 9, 8, 10, 9, 10, 10, 11,
            11, 12, 12, 4, 7, 6, 9, 9, 10, 9, 9, 8, 10, 10, 11, 10,
            12, 10, 13, 12, 13, 12, 4, 6, 6, 9, 9, 9, 9, 9, 9, 10,
            10, 11, 11, 11, 12, 12, 12, 12, 12, 7, 9, 8, 11, 10, 10, 10,
            11, 10, 11, 11, 12, 12, 13, 12, 13, 13, 13, 13, 7, 8, 9, 10,
            10, 11, 11, 10, 10, 11, 11, 11, 12, 13, 13, 13, 13, 14, 14, 8,
            9, 9, 11, 11, 12, 11, 12, 12, 13, 12, 12, 13, 13, 14, 15, 14,
            14, 14, 8, 9, 9, 10, 11, 11, 11, 12, 12, 13, 12, 13, 13, 14,
            14, 14, 15, 14, 16, 8, 9, 9, 11, 10, 12, 12, 12, 12, 15, 13,
            13, 13, 17, 14, 15, 15, 15, 14, 8, 9, 9, 10, 11, 11, 12, 13,
            12, 13, 13, 13, 14, 15, 14, 14, 14, 16, 15, 9, 11, 10, 12, 12,
            13, 13, 13, 13, 14, 14, 16, 15, 14, 14, 14, 15, 15, 17, 9, 10,
            10, 11, 11, 13, 13, 13, 14, 14, 13, 15, 14, 15, 14, 15, 16, 15,
            16, 10, 11, 11, 12, 12, 13, 14, 15, 14, 15, 14, 14, 15, 17, 16,
            15, 15, 17, 17, 10, 12, 11, 13, 12, 14, 14, 13, 14, 15, 15, 15,
            15, 16, 17, 17, 15, 17, 16, 11, 12, 12, 14, 13, 15, 14, 15, 16,
            17, 15, 17, 15, 17, 15, 15, 16, 17, 15, 11, 11, 12, 14, 14, 14,
            14, 14, 15, 15, 16, 15, 17, 17, 17, 16, 17, 16, 15, 12, 12, 13,
            14, 14, 14, 15, 14, 15, 15, 16, 16, 17, 16, 17, 15, 17, 17, 16,
            12, 14, 12, 14, 14, 15, 15, 15, 14, 14, 16, 16, 16, 15, 16, 16,
            15, 17, 15, 12, 13, 13, 14, 15, 14, 15, 17, 15, 17, 16, 17, 17,
            17, 16, 17, 16, 17, 17, 12, 13, 13, 14, 16, 15, 15, 15, 16, 15,
            17, 17, 15, 17, 15, 17, 16, 16, 17,
    };

    private static final float[] _vq_quantthresh__44u9_p9_1 = {
            -416.5f, -367.5f, -318.5f, -269.5f, -220.5f, -171.5f, -122.5f, -73.5f,
            -24.5f, 24.5f, 73.5f, 122.5f, 171.5f, 220.5f, 269.5f, 318.5f,
            367.5f, 416.5f,
    };

    private static final int[] _vq_quantmap__44u9_p9_1 = {
            17, 15, 13, 11, 9, 7, 5, 3,
            1, 0, 2, 4, 6, 8, 10, 12,
            14, 16, 18,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p9_1 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p9_1,
            _vq_quantmap__44u9_p9_1,
            19,
            19
    );

    public static final StaticCodeBook _44u9_p9_1 = new StaticCodeBook(
            2, 361,
            _vq_lengthlist__44u9_p9_1,
            1, -518287360, 1622704128, 5, 0,
            _vq_quantlist__44u9_p9_1,
            null,
            _vq_auxt__44u9_p9_1,
            null,
            0
    );

    private static final int[] _vq_quantlist__44u9_p9_2 = {
            24,
            23,
            25,
            22,
            26,
            21,
            27,
            20,
            28,
            19,
            29,
            18,
            30,
            17,
            31,
            16,
            32,
            15,
            33,
            14,
            34,
            13,
            35,
            12,
            36,
            11,
            37,
            10,
            38,
            9,
            39,
            8,
            40,
            7,
            41,
            6,
            42,
            5,
            43,
            4,
            44,
            3,
            45,
            2,
            46,
            1,
            47,
            0,
            48,
    };

    private static final int[] _vq_lengthlist__44u9_p9_2 = {
            2, 4, 4, 5, 4, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 6, 7, 6, 7, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            7,
    };

    private static final float[] _vq_quantthresh__44u9_p9_2 = {
            -23.5f, -22.5f, -21.5f, -20.5f, -19.5f, -18.5f, -17.5f, -16.5f,
            -15.5f, -14.5f, -13.5f, -12.5f, -11.5f, -10.5f, -9.5f, -8.5f,
            -7.5f, -6.5f, -5.5f, -4.5f, -3.5f, -2.5f, -1.5f, -0.5f,
            0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f,
            8.5f, 9.5f, 10.5f, 11.5f, 12.5f, 13.5f, 14.5f, 15.5f,
            16.5f, 17.5f, 18.5f, 19.5f, 20.5f, 21.5f, 22.5f, 23.5f,
    };

    private static final int[] _vq_quantmap__44u9_p9_2 = {
            47, 45, 43, 41, 39, 37, 35, 33,
            31, 29, 27, 25, 23, 21, 19, 17,
            15, 13, 11, 9, 7, 5, 3, 1,
            0, 2, 4, 6, 8, 10, 12, 14,
            16, 18, 20, 22, 24, 26, 28, 30,
            32, 34, 36, 38, 40, 42, 44, 46,
            48,
    };

    public static final EncodeAuxThreshMatch _vq_auxt__44u9_p9_2 = new EncodeAuxThreshMatch(
            _vq_quantthresh__44u9_p9_2,
            _vq_quantmap__44u9_p9_2,
            49,
            49
    );

    public static final StaticCodeBook _44u9_p9_2 = new StaticCodeBook(
            1, 49,
            _vq_lengthlist__44u9_p9_2,
            1, -526909440, 1611661312, 6, 0,
            _vq_quantlist__44u9_p9_2,
            null,
            _vq_auxt__44u9_p9_2,
            null,
            0
    );
}
