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
public class InfoFloor1 {

    static final int VIF_POSIT = 63;
    static final int VIF_CLASS = 16;
    static final int VIF_PARTS = 31;

    /** 0 to 31 */
    int partitions;
    /** 0 to 15 */
    int[] partitionclass = new int[VIF_PARTS];

    /** 1 to 8 */
    int[] class_dim = new int[VIF_CLASS];
    /** 0,1,2,3 (bits: 1<<n poss) */
    int[] class_subs = new int[VIF_CLASS];
    /** subs ^ dim entries */
    int[] class_book = new int[VIF_CLASS];
    /** [VIF_CLASS][subs] */
    int[][] class_subbook = new int[VIF_CLASS][8];

    /** 1 2 3 or 4 */
    int mult;
    /** first two implicit */
    int[] postlist = new int[VIF_POSIT + 2];

    // encode side analysis parameters
    float maxover;
    float maxunder;
    float maxerr;

    int twofitminsize;
    int twofitminused;
    float twofitweight;
    float twofitatten;
    int unusedminsize;
    int unusedmin_n;

    int n;

    public InfoFloor1() {
    }

    public InfoFloor1(
            int partitions,
            int[] partitionclass,
            int[] class_dim,
            int[] class_subs,
            int[] class_book,
            int[][] class_subbook,
            int mult,
            int[] postlist,
            float maxover,
            float maxunder,
            float maxerr,
            int twofitminsize,
            int twofitminused,
            float twofitweight,
            float twofitatten,
            int unusedminsize,
            int unusedmin_n,
            int n
    ) {
        this.partitionclass = partitionclass;
        this.class_dim = class_dim;
        this.class_subs = class_subs;
        this.class_book = class_book;
        this.class_subbook = class_subbook;
        this.mult = mult;
        this.postlist = postlist;
        this.maxover = maxover;
        this.maxunder = maxunder;
        this.maxerr = maxerr;
        this.twofitminsize = twofitminsize;
        this.twofitminused = twofitminused;
        this.twofitweight = twofitweight;
        this.twofitatten = twofitatten;
        this.unusedminsize = unusedminsize;
        this.unusedmin_n = unusedmin_n;
        this.n = n;
    }

    public InfoFloor1(
            int partitions,
            int[] partitionclass,
            int[] class_dim,
            int[] class_subs,
            int[] class_book,
            int[][] class_subbook,
            int mult,
            int[] postlist,
            int maxover,
            int maxunder,
            int maxerr,
            double twofitweight,
            double twofitatten,
            int n
    ) {
        this.partitions = partitions;
        this.partitionclass = partitionclass;
        this.class_dim = class_dim;
        this.class_subs = class_subs;
        this.class_book = class_book;
        this.class_subbook = class_subbook;
        this.mult = mult;
        this.postlist = postlist;
        this.maxover = maxover;
        this.maxunder = maxunder;
        this.maxerr = maxerr;
        this.twofitweight = (float) twofitweight;
        this.twofitatten = (float) twofitatten;
        this.n = n;
    }

    void free() {
        partitionclass = null;
        class_dim = null;
        class_subs = null;
        class_book = null;
        class_subbook = null;
        postlist = null;
    }

    Object copy_info() {
        InfoFloor1 info = this;
        InfoFloor1 ret = new InfoFloor1();

        ret.partitions = info.partitions;
        System.arraycopy(info.partitionclass, 0, ret.partitionclass, 0, VIF_PARTS);
        System.arraycopy(info.class_dim, 0, ret.class_dim, 0, VIF_CLASS);
        System.arraycopy(info.class_subs, 0, ret.class_subs, 0, VIF_CLASS);
        System.arraycopy(info.class_book, 0, ret.class_book, 0, VIF_CLASS);

        for (int j = 0; j < VIF_CLASS; j++) {
            System.arraycopy(info.class_subbook[j], 0,
                    ret.class_subbook[j], 0, 8);
        }

        ret.mult = info.mult;
        System.arraycopy(info.postlist, 0, ret.postlist, 0, VIF_POSIT + 2);

        ret.maxover = info.maxover;
        ret.maxunder = info.maxunder;
        ret.maxerr = info.maxerr;

        ret.twofitminsize = info.twofitminsize;
        ret.twofitminused = info.twofitminused;
        ret.twofitweight = info.twofitweight;
        ret.twofitatten = info.twofitatten;
        ret.unusedminsize = info.unusedminsize;
        ret.unusedmin_n = info.unusedmin_n;

        ret.n = info.n;

        return ret;
    }

    public boolean setValue(InfoFloor1 info) {

        if (info == null) return false;
        this.partitions = info.partitions;
        System.arraycopy(info.partitionclass, 0, this.partitionclass, 0, info.partitionclass.length);
        System.arraycopy(info.class_dim, 0, this.class_dim, 0, info.class_dim.length);
        System.arraycopy(info.class_subs, 0, this.class_subs, 0, info.class_subs.length);
        System.arraycopy(info.class_book, 0, this.class_book, 0, info.class_book.length);

        for (int j = 0; j < info.class_subbook.length; j++) {
            System.arraycopy(info.class_subbook[j], 0,
                    this.class_subbook[j], 0, info.class_subbook[j].length);
        }

        this.mult = info.mult;
        System.arraycopy(info.postlist, 0, this.postlist, 0, info.postlist.length);

        this.maxover = info.maxover;
        this.maxunder = info.maxunder;
        this.maxerr = info.maxerr;

        this.twofitminsize = info.twofitminsize;
        this.twofitminused = info.twofitminused;
        this.twofitweight = info.twofitweight;
        this.twofitatten = info.twofitatten;
        this.unusedminsize = info.unusedminsize;
        this.unusedmin_n = info.unusedmin_n;

        this.n = info.n;
        return true;
    }
}
