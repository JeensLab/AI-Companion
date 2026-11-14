/*
 * THIS FILE IS PART OF THE OggVorbis SOFTWARE CODEC SOURCE CODE.
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.
 *
 * THE OggVorbis SOURCE CODE IS (C) COPYRIGHT 1994-2002
 * by the XIPHOPHORUS Company http://www.xiph.org/
 */

package biniu.ogg;

/**
 * Ogg Stream
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Page {

    private static final int[] crc_lookup = new int[256];

    static {
        for (int i = 0; i < crc_lookup.length; i++) {
            crc_lookup[i] = crc_entry(i);
        }
    }

    private static int crc_entry(int index) {
        int r = index << 24;
        for (int i = 0; i < 8; i++) {
            if ((r & 0x8000_0000) != 0) {
                r = (r << 1) ^ 0x04c1_1db7; // The same as the ethernet generator
                                            // polynomial, although we use an
                                            // unreflected alg and an init/final
                                            // of 0, not 0xffffffff
            } else {
                r <<= 1;
            }
        }
        return r;
    }

    public byte[] header_base;
    public int header;
    public int header_len;
    public byte[] body_base;
    public int body;
    public int body_len;

    public int version() {
        return header_base[header + 4] & 0xff;
    }

    public boolean continued() {
        return (header_base[header + 5] & 0x01) != 0;
    }

    public boolean bos() {
        return (header_base[header + 5] & 0x02) != 0;
    }

    public boolean eos() {
        return ((header_base[header + 5] & 0x04) != 0);
    }

    public long granulePos() {
        long foo = header_base[header + 13] & 0xff;
        foo = (foo << 8) | (header_base[header + 12] & 0xff);
        foo = (foo << 8) | (header_base[header + 11] & 0xff);
        foo = (foo << 8) | (header_base[header + 10] & 0xff);
        foo = (foo << 8) | (header_base[header + 9] & 0xff);
        foo = (foo << 8) | (header_base[header + 8] & 0xff);
        foo = (foo << 8) | (header_base[header + 7] & 0xff);
        foo = (foo << 8) | (header_base[header + 6] & 0xff);
        return foo;
    }

    public int serialNo() {
        return (header_base[header + 14] & 0xff) |
                ((header_base[header + 15] & 0xff) << 8) |
                ((header_base[header + 16] & 0xff) << 16) |
                ((header_base[header + 17] & 0xff) << 24);
    }

    public int pageNo() {
        return (header_base[header + 18] & 0xff) |
                ((header_base[header + 19] & 0xff) << 8) |
                ((header_base[header + 20] & 0xff) << 16) |
                ((header_base[header + 21] & 0xff) << 24);
    }

    public void checksum() {
        int crc_reg = 0;

        for (int i = 0; i < header_len; i++) {
            crc_reg = (crc_reg << 8) ^ crc_lookup[((crc_reg >>> 24) & 0xff) ^ (header_base[header + i] & 0xff)];
        }
        for (int i = 0; i < body_len; i++) {
            crc_reg = (crc_reg << 8) ^ crc_lookup[((crc_reg >>> 24) & 0xff) ^ (body_base[body + i] & 0xff)];
        }
        header_base[header + 22] = (byte) crc_reg /* & 0xff */;
        header_base[header + 23] = (byte) (crc_reg >>> 8) /* & 0xff */;
        header_base[header + 24] = (byte) (crc_reg >>> 16) /* & 0xff */;
        header_base[header + 25] = (byte) (crc_reg >>> 24) /* & 0xff */;
    }

    public Page copy() {
        return copy(new Page());
    }

    public Page copy(Page p) {
        byte[] tmp = new byte[header_len];
        System.arraycopy(header_base, header, tmp, 0, header_len);
        p.header_len = header_len;
        p.header_base = tmp;
        p.header = 0;
        tmp = new byte[body_len];
        System.arraycopy(body_base, body, tmp, 0, body_len);
        p.body_len = body_len;
        p.body_base = tmp;
        p.body = 0;
        return p;
    }

    /**
     * Calls ogg_page_packets().
     * <p>
     * NOTE:
     * If a page consists of a packet begun on a previous page, and a new
     * packet begun (but not completed) on this page, the return will be:
     * <pre>
     * ogg_page_packets(page)   ==1,
     * ogg_page_continued(page) !=0
     * </pre>
     * If a page happens to be a single packet that was begun on a
     * previous page, and spans to the next page (in the case of a three or
     * more page packet), the return will be:
     * <pre>
     * ogg_page_packets(page)   ==0,
     * ogg_page_continued(page) !=0
     * </pre>
     *
     * @return the number of packets that are completed on this page (if
     * the leading packet is begun on a previous page, but ends on this
     * page, it's counted
     */
    public int getPackets() {
        int n = this.header_base[26] & 0xFF;
        int count = 0;
        for (int i = 0; i < n; i++)
            if ((this.header_base[27 + i] & 0xFF) < 255)
                count++;
        return count;
    }
}
