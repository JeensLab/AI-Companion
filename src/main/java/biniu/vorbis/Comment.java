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
 * <p>
 * the comments are not part of vorbis_info so that vorbis_info can be
 * static storage
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Comment {

    private static final byte[] VORBIS = "vorbis".getBytes();

    public static final int OV_EFAULT = -129;
    public static final int OV_EIMPL = -130;

    // unlimited user comment fields.  libvorbis writes 'libvorbis'
    // whatever vendor is set to in encode
    public byte[][] user_comments;
    public int[] comment_lengths;
    public int comments;
    public byte[] vendor;

    public void init() {
        user_comments = null;
        comments = 0;
        vendor = null;
    }

    public void addComment(String comment) {
        addComment(comment.getBytes());
    }

    private void addComment(byte[] comment) {
        byte[][] foo = new byte[comments + 2][];
        if (user_comments != null) {
            System.arraycopy(user_comments, 0, foo, 0, comments);
        }
        user_comments = foo;

        int[] goo = new int[comments + 2];
        if (comment_lengths != null) {
            System.arraycopy(comment_lengths, 0, goo, 0, comments);
        }
        comment_lengths = goo;

        byte[] bar = new byte[comment.length + 1];
        System.arraycopy(comment, 0, bar, 0, comment.length);
        user_comments[comments] = bar;
        comment_lengths[comments] = comment.length;
        comments++;
        user_comments[comments] = null;
    }

    public void addTag(String tag, String contents) {
        if (contents == null) contents = "";
        addComment(tag + "=" + contents);
    }

    /**
     * This is more or less the same as strncasecmp - but that doesn't exist
     * everywhere, and this is a fairly trivial function, so we include it
     */
    private static boolean tagCompare(byte[] s1, byte[] s2, int n) {
        int c = 0;
        byte u1, u2;
        while (c < n) {
            u1 = s1[c];
            u2 = s2[c];
            if (u1 >= 'A') u1 = (byte) (u1 - 'A' + 'a');
            if (u2 >= 'A') u2 = (byte) (u2 - 'A' + 'a');
            if (u1 != u2) {
                return false;
            }
            c++;
        }
        return true;
    }

    public boolean packComment(Buffer opb) {
        byte[] temp = "Xiph.Org libVorbis I 20040629".getBytes();

        // preamble
        opb.write(0x03, 8);
        opb.write(VORBIS);

        // vendor
        opb.write(temp.length, 32);
        opb.write(temp);

        // comments

        opb.write(this.comments, 32);
        if (this.comments != 0) {
            int i;
            for (i = 0; i < this.comments; i++) {
                if (this.user_comments[i] != null) {
                    opb.write(comment_lengths[i], 32);
                    opb.write(user_comments[i]);
                } else {
                    opb.write(0, 32);
                }
            }
        } else {
            return false;
        }
        opb.write(1, 1);

        return true;
    }

    public void clear() {
        for (int i = 0; i < comments; i++)
            user_comments[i] = null;
        user_comments = null;
        vendor = null;
        comments = 0;
    }

    public String getVendor() {
        return new String(vendor, 0, vendor.length - 1);
    }

    public String getComment(int i) {
        if (comments <= i) return null;
        return new String(user_comments[i], 0, user_comments[i].length - 1);
    }

    public String toString() {
        StringBuilder foo = new StringBuilder("Vendor: " + new String(vendor, 0, vendor.length - 1));
        for (int i = 0; i < comments; i++) {
            foo.append("\nComment: ").append(new String(user_comments[i], 0, user_comments[i].length - 1));
        }
        foo.append("\n");
        return foo.toString();
    }
}
