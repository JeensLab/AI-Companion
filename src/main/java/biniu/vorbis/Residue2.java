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
class Residue2 extends Residue0 {

    public static int forward(Block vb, Object vl, float[][] in, int ch) {
        return 0;
    }

    public int inverse(Block vb, Object vl, float[][] in, int[] nonzero, int ch) {
        int i = 0;
        for (i = 0; i < ch; i++) if (nonzero[i] != 0) break;
        if (i == ch) return 0; // no nonzero vectors

        return (_2inverse(vb, vl, in, ch));
    }

    @Override
    public int[][] clas(LookResidue vl, float[][] in, int pin, int[] nonzero, int ch) {
        int i, used = 0;
        for (i = 0; i < ch; i++)
            if (nonzero[i] != 0) used++;
        if (used != 0)
            return (_2class(vl, in, pin, ch));
        else
            return null;
    }

    /**
     * designed for stereo or other modes where the partition size is an
     * integer multiple of the number of channels encoded in the current
     * submap
     */
    private static int[][] _2class(LookResidue vl, float[][] in, int pin, int ch) {
        int i, j, k, l;
        LookResidue look = vl;
        InfoResidue0 info = look.info;

        // move all this setup out later
        int samples_per_partition = info.grouping;
        int possible_partitions = info.partitions;
        int n = info.end - info.begin;

        int partvals = n / samples_per_partition;
        int[][] partword = new int[1][];

        partword[0] = new int[n * ch / samples_per_partition];

        for (i = 0, l = (info.begin / ch) + pin; i < partvals; i++) {
            float magmax = 0.f;
            float angmax = 0.f;
            for (j = 0; j < samples_per_partition; j += ch) {
                if (Math.abs(in[0][l]) > magmax) magmax = Math.abs(in[0][l]);
                for (k = 1; k < ch; k++)
                    if (Math.abs(in[k][l]) > angmax) angmax = Math.abs(in[k][l]);
                l++;
            }

            for (j = 0; j < possible_partitions - 1; j++)
                if (magmax <= info.classmetric1[j] && angmax <= info.classmetric2[j]) {
                    break;
                }

            partword[0][i] = j;

        }
        look.frames++;

        return partword;
    }

    /**
     * res2 is slightly more different; all the channels are interleaved
     * into a single vector and encoded.
     */
    @Override
    public int forward(Buffer opb, Block vb, LookResidue vl,
                       float[][] in, float[][] out, int[] nonzero, int ch,
                       int[][] partword) {
        int i, j, k, n = vb.pcmEnd / 2, used = 0;

        // don't duplicate the code; use a working vector hack for now and
        // reshape ourselves into a single channel res1
        // ugly; reallocs for each coupling pass :-(
        float[][] work = new float[1][ch * n];

        for (i = 0; i < ch; i++) {
            if (nonzero[i] != 0) used++;
            for (j = 0, k = i; j < n; j++, k += ch) {
                work[0][k] = in[i][j];
            }
        }

        if (used != 0) {
            int ret = _01forward(opb, vb, vl, work, 1, partword);
            // update the so far vector
            if (out != null) {
                for (i = 0; i < ch; i++) {
                    for (j = 0, k = i; j < n; j++, k += ch)
                        out[i][j] += in[i][j] - work[0][k];

                }
            }
            return ret;
        } else {
            return 0;
        }
    }
}
