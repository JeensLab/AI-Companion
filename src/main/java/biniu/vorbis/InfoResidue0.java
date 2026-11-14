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
public class InfoResidue0 {

    // block-partitioned VQ coded straight residue

    int begin;
    int end;

    // first stage (lossless partitioning)

    /** group n vectors per partition */
    int grouping;
    /** possible codebooks for a partition */
    int partitions;
    /** huffbook for partitioning */
    int groupbook;
    /** expanded out to pointers in lookup */
    int[] secondstages = new int[64];
    /** list of second stage books */
    int[] booklist = new int[256];

    // encode-only heuristic settings
    /** book entropy threshholds */
    float[] entmax = new float[64];
    /** book amp threshholds */
    float[] ampmax = new float[64];
    /** book heuristic subgroup size */
    int[] subgrp = new int[64];
    /** subgroup position limits */
    int[] blimit = new int[64];

    float[] classmetric1 = new float[64];
    float[] classmetric2 = new float[64];

    public InfoResidue0() {
    }

    public InfoResidue0(
            int begin,
            int end,
            int grouping,
            int partitions,
            int groupbook,
            int[] secondstages,
            int[] booklist,
            float[] classmetric1,
            float[] classmetric2
    ) {
        this();
        this.begin = begin;
        this.end = end;
        this.grouping = grouping;
        this.partitions = partitions;
        this.groupbook = groupbook;
        this.secondstages = secondstages;
        this.booklist = booklist;
        this.classmetric1 = classmetric1;
        this.classmetric2 = classmetric2;
    }

    public boolean setValues(InfoResidue0 info) {
        if (info == null) return false;
        this.begin = info.begin;
        this.end = info.end;
        this.grouping = info.grouping;
        this.partitions = info.partitions;
        this.groupbook = info.groupbook;
        System.arraycopy(info.secondstages, 0, this.secondstages, 0, info.secondstages.length);
        System.arraycopy(info.booklist, 0, this.booklist, 0, info.booklist.length);
        System.arraycopy(info.classmetric1, 0, this.classmetric1, 0, info.classmetric1.length);
        System.arraycopy(info.classmetric2, 0, this.classmetric2, 0, info.classmetric2.length);
        return true;
    }
}
