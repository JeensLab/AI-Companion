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
public class Buffer {

    protected static final int BUFFER_INCREMENT = 256;

    protected static final int[] mask = {
            0x00000000, 0x00000001, 0x00000003, 0x00000007, 0x0000000f,
            0x0000001f, 0x0000003f, 0x0000007f, 0x000000ff, 0x000001ff,
            0x000003ff, 0x000007ff, 0x00000fff, 0x00001fff, 0x00003fff,
            0x00007fff, 0x0000ffff, 0x0001ffff, 0x0003ffff, 0x0007ffff,
            0x000fffff, 0x001fffff, 0x003fffff, 0x007fffff, 0x00ffffff,
            0x01ffffff, 0x03ffffff, 0x07ffffff, 0x0fffffff, 0x1fffffff,
            0x3fffffff, 0x7fffffff, 0xffffffff
    };

    public int endByte = 0;
    public int endBit = 0;

    public byte[] buffer;
    private int ptr = 0;
    public int storage = 0;

    public Buffer() {
    }

    public void writeInit() {
        this.ptr = 0;
        this.buffer = new byte[BUFFER_INCREMENT];
        this.buffer[0] = '\0';
        this.storage = BUFFER_INCREMENT;
    }

    public void reset() {
        this.ptr = 0;
        this.buffer = new byte[BUFFER_INCREMENT];
        this.buffer[0] = 0;
        this.endBit = this.endByte = 0;
        this.storage = BUFFER_INCREMENT;
    }

    public void writeClear() {
        this.buffer = null;
        this.endByte = 0;
        this.endBit = 0;
        this.ptr = 0;
        this.storage = 0;
    }

    public int getBytes() {
        return (this.endByte + (this.endBit + 7) / 8);
    }

    public void write(byte[] s) {
        for (byte b : s) {
            if (b == 0) break;
            write(b, 8);
        }
    }

    public void writetrUnc(int bits) {
        int bytes = bits >> 3;
        bits -= bytes * 8;
        endBit = bits;
        endByte = bytes;
        ptr = mask[bits];
    }

    /* Takes only up to 32 bits. */
    public void write(int value, int bits) {
        long czas1 = System.currentTimeMillis();
        if ((this.endByte + 4) >= (this.storage)) {
            byte[] tmp = new byte[this.storage + BUFFER_INCREMENT];
            System.arraycopy(this.buffer, 0, tmp, 0, this.storage);
            this.buffer = tmp;
            this.storage += BUFFER_INCREMENT;
        }

        value &= mask[bits];
        bits += endBit;
        this.buffer[ptr] |= (byte) (value << endBit);
        if (bits >= 8) {
            buffer[ptr + 1] = (byte) (value >>> (8 - endBit));
            if (bits >= 16) {
                buffer[ptr + 2] = (byte) (value >>> (16 - endBit));
                if (bits >= 24) {
                    buffer[ptr + 3] = (byte) (value >>> (24 - endBit));
                    if (bits >= 32) {
                        if (endBit > 0)
                            buffer[ptr + 4] = (byte) (value >>> (32 - endBit));
                        else
                            buffer[ptr + 4] = 0;
                    }
                }
            }
        }

        endByte += bits / 8;
        ptr += bits / 8;
        endBit = bits & 7;

    }

    public void read(byte[] s, int bytes) {
        int i = 0;
        while (bytes-- != 0) {
            s[i++] = (byte) (read(8));
        }
    }

    public void readInit(byte[] buf, int bytes) {
        readInit(buf, 0, bytes);
    }

    public void readInit(byte[] buf, int start, int bytes) {
        ptr = start;
        buffer = buf;
        endBit = endByte = 0;
        storage = bytes;
    }

    public int look(int bits) {
        int ret;
        int m = mask[bits];

        bits += endBit;

        if (endByte + 4 >= storage) {
            if (endByte + (bits - 1) / 8 >= storage) return -1;
        }

        ret = ((buffer[ptr]) & 0xff) >>> endBit;
        if (bits > 8) {
            ret |= ((buffer[ptr + 1]) & 0xff) << (8 - endBit);
            if (bits > 16) {
                ret |= ((buffer[ptr + 2]) & 0xff) << (16 - endBit);
                if (bits > 24) {
                    ret |= ((buffer[ptr + 3]) & 0xff) << (24 - endBit);
                    if (bits > 32 && endBit != 0) {
                        ret |= ((buffer[ptr + 4]) & 0xff) << (32 - endBit);
                    }
                }
            }
        }
        return (m & ret);
    }

    public int look1() {
        if (endByte >= storage) return -1;
        return ((buffer[ptr] >> endBit) & 1);
    }

    public void adv(int bits) {
        bits += endBit;
        ptr += bits / 8;
        endByte += bits / 8;
        endBit = bits & 7;
    }

    public void adv1() {
        ++endBit;
        if (endBit > 7) {
            endBit = 0;
            ptr++;
            endByte++;
        }
    }

    public int read(int bits) {

        int ret;
        int m = mask[bits];
        bits += endBit;
        if (endByte + 4 >= storage) {
            ret = -1;
            if (endByte + (bits - 1) / 8 >= storage) {
                ptr += bits / 8;
                endByte += bits / 8;
                endBit = bits & 7;
                return ret;
            }
        }

        ret = ((buffer[ptr]) & 0xff) >>> endBit;
        if (bits > 8) {
            ret |= ((buffer[ptr + 1]) & 0xff) << (8 - endBit);
            if (bits > 16) {
                ret |= ((buffer[ptr + 2]) & 0xff) << (16 - endBit);
                if (bits > 24) {
                    ret |= ((buffer[ptr + 3]) & 0xff) << (24 - endBit);
                    if (bits > 32 && endBit != 0) {
                        ret |= ((buffer[ptr + 4]) & 0xff) << (32 - endBit);
                    }
                }
            }
        }

        ret &= m;

        ptr += bits / 8;
        endByte += bits / 8;
        endBit = bits & 7;
        return ret;
    }

    public int readB(int bits) {
        int ret;
        int m = 32 - bits;

        bits += endBit;

        if (endByte + 4 >= storage) {
            /* not the main path */
            ret = -1;
            if (endByte * 8 + bits > storage * 8) {
                ptr += bits / 8;
                endByte += bits / 8;
                endBit = bits & 7;
                return ret;
            }
        }

        ret = (buffer[ptr] & 0xff) << (24 + endBit);
        if (bits > 8) {
            ret |= (buffer[ptr + 1] & 0xff) << (16 + endBit);
            if (bits > 16) {
                ret |= (buffer[ptr + 2] & 0xff) << (8 + endBit);
                if (bits > 24) {
                    ret |= (buffer[ptr + 3] & 0xff) << (endBit);
                    if (bits > 32 && (endBit != 0))
                        ret |= (buffer[ptr + 4] & 0xff) >> (8 - endBit);
                }
            }
        }
        ret = (ret >>> (m >> 1)) >>> ((m + 1) >> 1);

        ptr += bits / 8;
        endByte += bits / 8;
        endBit = bits & 7;
        return ret;
    }

    public int read1() {
        int ret;
        if (endByte >= storage) {
            ret = -1;
            endBit++;
            if (endBit > 7) {
                endBit = 0;
                ptr++;
                endByte++;
            }
            return ret;
        }

        ret = (buffer[ptr] >> endBit) & 1;

        endBit++;
        if (endBit > 7) {
            endBit = 0;
            ptr++;
            endByte++;
        }
        return ret;
    }

    public int getBits() {
        return (endByte * 8 + endBit);
    }
}
