/*
 * THIS FILE IS PART OF THE OggVorbis SOFTWARE CODEC SOURCE CODE.
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.
 *
 * THE OggVorbis SOURCE CODE IS (C) COPYRIGHT 1994-2002
 * by the XIPHOPHORUS Company http://www.xiph.org/
 */

package biniu.vorbis.modes;

import biniu.vorbis.StaticCodeBook;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Util {

    static public StaticCodeBook[] codebookTab(StaticCodeBook x1, StaticCodeBook x2, StaticCodeBook x3) {
        StaticCodeBook[] tab = new StaticCodeBook[3];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        return tab;
    }

    static public StaticCodeBook[] codebookTab(StaticCodeBook x1, StaticCodeBook x2) {
        StaticCodeBook[] tab = new StaticCodeBook[2];
        tab[0] = x1;
        tab[1] = x2;
        return tab;
    }

    static public int[] intTab(int x1) {
        int[] tab = new int[1];
        tab[0] = x1;
        return tab;
    }

    static public int[] intTab(int x1, int x2) {
        int[] tab = new int[2];
        tab[0] = x1;
        tab[1] = x2;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3) {
        int[] tab = new int[3];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4) {
        int[] tab = new int[4];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5) {
        int[] tab = new int[5];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6) {
        int[] tab = new int[6];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8) {
        int[] tab = new int[8];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9) {
        int[] tab = new int[9];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9, int x10, int x11, int x12, int x13) {
        int[] tab = new int[13];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9, int x10, int x11, int x12, int x13,
                               int x14,
                               int x15) {
        int[] tab = new int[15];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        return tab;
    }

    static public float[] intfTab(int x1, int x2, int x3, int x4, int x5, int x6,
                                  int x7,
                                  int x8, int x9, int x10, int x11, int x12, int x13,
                                  int x14,
                                  int x15) {
        float[] tab = new float[15];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9, int x10, int x11, int x12, int x13,
                               int x14,
                               int x15, int x16, int x17) {
        int[] tab = new int[17];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        tab[15] = x16;
        tab[16] = x17;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9, int x10, int x11, int x12, int x13,
                               int x14,
                               int x15, int x16) {
        int[] tab = new int[16];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        tab[15] = x16;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9, int x10, int x11, int x12, int x13,
                               int x14,
                               int x15, int x16, int x17, int x18, int x19) {
        int[] tab = new int[19];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        tab[15] = x16;
        tab[16] = x17;
        tab[17] = x18;
        tab[18] = x19;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9, int x10, int x11, int x12, int x13,
                               int x14,
                               int x15, int x16, int x17, int x18, int x19, int x20,
                               int x21, int x22,
                               int x23, int x24, int x25, int x26, int x27, int x28,
                               int x29) {
        int[] tab = new int[29];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        tab[15] = x16;
        tab[16] = x17;
        tab[17] = x18;
        tab[18] = x19;
        tab[19] = x20;
        tab[20] = x21;
        tab[21] = x22;
        tab[22] = x23;
        tab[23] = x24;
        tab[24] = x25;
        tab[25] = x26;
        tab[26] = x27;
        tab[27] = x28;
        tab[28] = x29;
        return tab;
    }

    static public int[] intTab(int x1, int x2, int x3, int x4, int x5, int x6, int x7,
                               int x8, int x9, int x10, int x11, int x12, int x13,
                               int x14,
                               int x15, int x16, int x17, int x18, int x19, int x20,
                               int x21, int x22,
                               int x23, int x24, int x25, int x26, int x27, int x28,
                               int x29,
                               int x30, int x31, int x32, int x33, int x34, int x35,
                               int x36, int x37,
                               int x38, int x39, int x40) {
        int[] tab = new int[40];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        tab[15] = x16;
        tab[16] = x17;
        tab[17] = x18;
        tab[18] = x19;
        tab[19] = x20;
        tab[20] = x21;
        tab[21] = x22;
        tab[22] = x23;
        tab[23] = x24;
        tab[24] = x25;
        tab[25] = x26;
        tab[26] = x27;
        tab[27] = x28;
        tab[28] = x29;
        tab[29] = x30;
        tab[30] = x31;
        tab[31] = x32;
        tab[32] = x33;
        tab[33] = x34;
        tab[34] = x35;
        tab[35] = x36;
        tab[36] = x37;
        tab[37] = x38;
        tab[38] = x39;
        tab[39] = x40;
        return tab;
    }

    static public int[][] intTab(int[] x1, int[] x2, int[] x3, int[] x4, int[] x5) {
        int[][] tab = new int[5][];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        return tab;
    }

    static public int[][] intTab(int[] x1, int[] x2, int[] x3, int[] x4) {
        int[][] tab = new int[4][];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        return tab;
    }

    static public int[][] intTab(int[] x1, int[] x2, int[] x3) {
        int[][] tab = new int[3][];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        return tab;
    }

    static public int[][] intTab(int[] x1, int[] x2) {
        int[][] tab = new int[2][];
        tab[0] = x1;
        tab[1] = x2;
        return tab;
    }

    static public int[][] intTab(int[] x1) {
        int[][] tab = new int[1][];
        tab[0] = x1;
        return tab;
    }

    static public float[] intTab(float x1) {
        float[] tab = new float[1];
        tab[0] = x1;
        return tab;
    }

    static public float[] intTab(float x1, float x2, float x3) {
        float[] tab = new float[3];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        return tab;
    }

    static public float[] intTab(float x1, float x2, float x3, float x4,
                                 float x5, float x6, float x7) {
        float[] tab = new float[7];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        return tab;
    }

//    static public float[] intTab(float x1, float x2, float x3, float x4,
//                                 float x5, float x6, float x7, float x8, float x9) {
//        float[] tab = new float[9];
//        tab[0] = x1;
//        tab[1] = x2;
//        tab[2] = x3;
//        tab[3] = x4;
//        tab[4] = x5;
//        tab[5] = x6;
//        tab[6] = x7;
//        tab[7] = x8;
//        tab[8] = x9;
//        return tab;
//    }

    static public float[] intTab(float x1, float x2, float x3, float x4,
                                 float x5, float x6, float x7, float x8) {
        float[] tab = new float[9];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        return tab;
    }

    static public float[] intTab(float x1, float x2, float x3, float x4,
                                 float x5, float x6, float x7, float x8, float x9) {
        float[] tab = new float[9];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        return tab;
    }

    static public float[] intTab(float x1, float x2, float x3, float x4,
                                 float x5, float x6, float x7, float x8,
                                 float x9, float x10, float x11, float x12,
                                 float x13, float x14, float x15) {
        float[] tab = new float[15];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        tab[3] = x4;
        tab[4] = x5;
        tab[5] = x6;
        tab[6] = x7;
        tab[7] = x8;
        tab[8] = x9;
        tab[9] = x10;
        tab[10] = x11;
        tab[11] = x12;
        tab[12] = x13;
        tab[13] = x14;
        tab[14] = x15;
        return tab;
    }

    static public float[][] intTab(float[] x1, float[] x2, float[] x3) {
        float[][] tab = new float[3][];
        tab[0] = x1;
        tab[1] = x2;
        tab[2] = x3;
        return tab;
    }

    static public float[][] intTab(float[] x1, float[] x2) {
        float[][] tab = new float[2][];
        tab[0] = x1;
        tab[1] = x2;
        return tab;
    }
}