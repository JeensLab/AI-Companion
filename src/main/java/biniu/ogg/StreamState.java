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

import java.util.Arrays;


/**
 * Ogg Stream
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class StreamState {

    /** bytes from packet bodies */
    private byte[] bodyData;
    /** storage elements allocated */
    private int bodyStorage;
    /** elements stored; fill mark */
    private int bodyFill;
    /** elements of fill returned */
    private int bodyReturned;

    /** The values that will go to the segment table */
    int[] lacing_vals;
    /**
     * pcm_pos values for headers. Not compact
     * this way, but it is simply coupled to the
     * lacing fifo
     */
    long[] granule_vals;
    int lacing_storage;
    int lacing_fill;
    int lacing_packet;
    int lacing_returned;

    /** working space for header encode */
    final byte[] header = new byte[282];
    int header_fill;

    /**
     * set when we have buffered the last packet in the
     * logical bitstream
     */
    public boolean e_o_s;
    /**
     * set after we've written the initial page
     * of a logical bitstream
     */
    boolean b_o_s;
    public int serialNo;
    private int pageNo;
    /**
     * sequence number for decode; the framing
     * knows where there's a hole in the data,
     * but we need coupling so that the codec
     * (which is in a seperate abstraction
     * layer) also knows about the gap
     */
    private long packetNo;
    private long granulePos;

    public StreamState() {
        init();
    }

    StreamState(int serialno) {
        this();
        init(serialno);
    }

    void init() {
        bodyStorage = 16 * 1024;
        bodyData = new byte[bodyStorage];
        lacing_storage = 1024;
        lacing_vals = new int[lacing_storage];
        granule_vals = new long[lacing_storage];
    }

    public void init(int serialno) {
        if (bodyData == null) {
            init();
        } else {
            Arrays.fill(bodyData, (byte) 0);
            Arrays.fill(lacing_vals, 0);
            Arrays.fill(granule_vals, 0);
        }
        this.serialNo = serialno;
    }

    public void clear() {
        bodyData = null;
        lacing_vals = null;
        granule_vals = null;
        //memset(os,0,sizeof(ogg_stream_state));
    }

    void destroy() {
        clear();
    }

    void bodyExpand(int needed) {
        if (bodyStorage <= bodyFill + needed) {
            bodyStorage += (needed + 1024);
            byte[] foo = new byte[bodyStorage];
            System.arraycopy(bodyData, 0, foo, 0, bodyData.length);
            bodyData = foo;
        }
    }

    void lacingExpand(int needed) {
        if (lacing_storage <= lacing_fill + needed) {
            lacing_storage += (needed + 32);
            int[] foo = new int[lacing_storage];
            System.arraycopy(lacing_vals, 0, foo, 0, lacing_vals.length);
            lacing_vals = foo;

            long[] bar = new long[lacing_storage];
            System.arraycopy(granule_vals, 0, bar, 0, granule_vals.length);
            granule_vals = bar;
        }
    }

    /* submit data to the internal buffer of the framing engine */
    public int packetIn(Packet op) {
        int lacing_val = op.bytes / 255 + 1;

        if (bodyReturned != 0) {
            // advance packet data according to the bodyReturned pointer. We
            // had to keep it around to return a pointer into the buffer last
            // call

            bodyFill -= bodyReturned;
            if (bodyFill != 0) {
                System.arraycopy(bodyData, bodyReturned, bodyData, 0, bodyFill);
            }
            bodyReturned = 0;
        }

        // make sure we have the buffer storage
        bodyExpand(op.bytes);
        lacingExpand(lacing_val);

        // Copy in the submitted packet.  Yes, the copy is a waste; this is
        // the liability of overly clean abstraction for the time being.  It
        // will actually be fairly easy to eliminate the extra copy in the
        // future

        System.arraycopy(op.packetByte, 0, bodyData, bodyFill, op.bytes);
        bodyFill += op.bytes;

        // Store lacing vals for this packet
        int j;
        for (j = 0; j < lacing_val - 1; j++) {
            lacing_vals[lacing_fill + j] = 255;
            granule_vals[lacing_fill + j] = granulePos;
        }
        lacing_vals[lacing_fill + j] = (op.bytes) % 255;
        granulePos = granule_vals[lacing_fill + j] = op.granulePos;

        /* flag the first segment as the beginning of the packet */
        lacing_vals[lacing_fill] |= 0x100;

        lacing_fill += lacing_val;

        /* for the sake of completeness */
        packetNo++;

        if (op.e_o_s) e_o_s = true;
        return 0;
    }

    /**
     * The last part of decode. We have the stream broken into packet
     * segments.  Now we need to group them into packets (or return the
     * out of sync markers)
     */
    public int packetOut(Packet op) {

        int ptr = lacing_returned;

        if (lacing_packet <= ptr) {
            return 0;
        }

        if ((lacing_vals[ptr] & 0x400) != 0) {
            // We lost sync here; let the app know
            lacing_returned++;

            // we need to tell the codec there's a gap; it might need to
            // handle previous packet dependencies.
            packetNo++;
            return -1;
        }

        // Gather the whole packet. We'll have no holes or a partial packet
        {
            int size = lacing_vals[ptr] & 0xff;
            int bytes = 0;

            op.packetByte = bodyData;
//            op.packet = bodyReturned;
            op.e_o_s = (lacing_vals[ptr] & 0x200) != 0; // last packet of the stream?
            op.b_o_s = (lacing_vals[ptr] & 0x100) != 0; // first packet of the stream?
            bytes += size;

            while (size == 255) {
                int val = lacing_vals[++ptr];
                size = val & 0xff;
                if ((val & 0x200) != 0) op.e_o_s = true;
                bytes += size;
            }

            op.packetNo = packetNo;
            op.granulePos = granule_vals[ptr];
            op.bytes = bytes;

            bodyReturned += bytes;

            lacing_returned = ptr + 1;
        }
        packetNo++;
        return 1;
    }

    /**
     * add the incoming page to the stream state; we decompose the page
     * into packet segments here as well.
     */
    public int pageIn(Page og) {
        byte[] header_base = og.header_base;
        int header = og.header;
        byte[] body_base = og.body_base;
        int body = og.body;
        int bodysize = og.body_len;
        int segptr = 0;

        int version = og.version();
        boolean continued = og.continued();
        boolean bos = og.bos();
        boolean eos = og.eos();
        long granulepos = og.granulePos();
        int _serialno = og.serialNo();
        int _pageno = og.pageNo();
        int segments = header_base[header + 26] & 0xff;

        // clean up 'returned data'
        {
            int lr = lacing_returned;
            int br = bodyReturned;

            // body data
            if (br != 0) {
                bodyFill -= br;
                if (bodyFill != 0) {
                    System.arraycopy(bodyData, br, bodyData, 0, bodyFill);
                }
                bodyReturned = 0;
            }

            if (lr != 0) {
                // segment table
                if ((lacing_fill - lr) != 0) {
                    System.arraycopy(lacing_vals, lr, lacing_vals, 0, lacing_fill - lr);
                    System.arraycopy(granule_vals, lr, granule_vals, 0, lacing_fill - lr);
                }
                lacing_fill -= lr;
                lacing_packet -= lr;
                lacing_returned = 0;
            }
        }

        // check the serial number
        if (_serialno != serialNo) return -1;
        if (version > 0) return -1;

        lacingExpand(segments + 1);

        // are we in sequence?
        if (_pageno != pageNo) {
            int i;

            // unroll previous partial packet (if any)
            for (i = lacing_packet; i < lacing_fill; i++) {
                bodyFill -= lacing_vals[i] & 0xff;
            }
            lacing_fill = lacing_packet;

            // make a note of dropped data in segment table
            if (pageNo != -1) {
                lacing_vals[lacing_fill++] = 0x400;
                lacing_packet++;
            }

            // are we a 'continued packet' page?  If so, we'll need to skip
            // some segments
            if (continued) {
                bos = false;
                for (; segptr < segments; segptr++) {
                    int val = (header_base[header + 27 + segptr] & 0xff);
                    body += val;
                    bodysize -= val;
                    if (val < 255) {
                        segptr++;
                        break;
                    }
                }
            }
        }

        if (bodysize != 0) {
            bodyExpand(bodysize);
            System.arraycopy(body_base, body, bodyData, bodyFill, bodysize);
            bodyFill += bodysize;
        }

        {
            int saved = -1;
            while (segptr < segments) {
                int val = (header_base[header + 27 + segptr] & 0xff);
                lacing_vals[lacing_fill] = val;
                granule_vals[lacing_fill] = -1;

                if (bos) {
                    lacing_vals[lacing_fill] |= 0x100;
                    bos = false;
                }

                if (val < 255) saved = lacing_fill;

                lacing_fill++;
                segptr++;

                if (val < 255) lacing_packet = lacing_fill;
            }

            /* set the granulePos on the last pcmval of the last full packet */
            if (saved != -1) {
                granule_vals[saved] = granulepos;
            }
        }

        if (eos) {
            e_o_s = true;
            if (lacing_fill > 0)
                lacing_vals[lacing_fill - 1] |= 0x200;
        }

        pageNo = _pageno + 1;
        return 0;
    }

    /**
     * This will flush remaining packets into a page (returning nonzero),
     * even if there is not enough data to trigger a flush normally
     * (undersized page). If there are no packets or partial packets to
     * flush, ogg_stream_flush returns 0.  Note that ogg_stream_flush will
     * try to flush a normal sized page like ogg_stream_pageout; a call to
     * ogg_stream_flush does not gurantee that all packets have flushed.
     * Only a return value of 0 from ogg_stream_flush indicates all packet
     * data is flushed into pages.
     * <p>
     * ogg_stream_page will flush the last page in a stream even if it's
     * undersized; you almost certainly want to use ogg_stream_pageout
     * (and *not* ogg_stream_flush) unless you need to flush an undersized
     * page in the middle of a stream for some reason.
     */
    public boolean flush(Page og) {

        int i;
        int vals = 0;
        int maxvals = (lacing_fill > 255 ? 255 : lacing_fill);
        int bytes = 0;
        int acc = 0;
        long granule_pos = granule_vals[0];

        if (maxvals == 0) return false;

        // construct a page
        // decide how many segments to include

        // If this is the initial header case, the first page must only include
        // the initial header packet
        if (!b_o_s) {  // 'initial header page' case
            granule_pos = 0;
            for (vals = 0; vals < maxvals; vals++) {
                if ((lacing_vals[vals] & 0x0ff) < 255) {
                    vals++;
                    break;
                }
            }
        } else {
            for (vals = 0; vals < maxvals; vals++) {
                if (acc > 4096) break;
                acc += (lacing_vals[vals] & 0x0ff);
                granule_pos = granule_vals[vals];
            }
        }

        // construct the header in temp storage
        System.arraycopy("OggS".getBytes(), 0, header, 0, 4);

        // stream structure version
        header[4] = 0x00;

        // continued packet flag?
        header[5] = 0x00;
        if ((lacing_vals[0] & 0x100) == 0) header[5] |= 0x01;
        // first page flag?
        if (!b_o_s) header[5] |= 0x02;
        // last page flag?
        if (e_o_s && lacing_fill == vals) header[5] |= 0x04;
        b_o_s = true;

        // 64 bits of PCM position
        for (i = 6; i < 14; i++) {
            header[i] = (byte) (granule_pos & 0xFF);
            granule_pos >>>= 8;
        }

        // 32 bits of stream serial number
        {
            int _serialno = serialNo;
            for (i = 14; i < 18; i++) {
                header[i] = (byte) (_serialno & 0xFF);
                _serialno >>>= 8;

            }
        }

        // 32 bits of page counter (we have both counter and page header
        // because this val can roll over)
        if (pageNo == -1) pageNo = 0; // because someone called
        // stream_reset; this would be a
        // strange thing to do in an
        // encode stream, but it has
        // plausible uses
        {
            int _pageno = pageNo++;
            for (i = 18; i < 22; i++) {
                header[i] = (byte) (_pageno & 0xFF);
                _pageno >>>= 8;
            }
        }

        // zero for computation; filled in later
        header[22] = 0;
        header[23] = 0;
        header[24] = 0;
        header[25] = 0;

        // segment table
        header[26] = (byte) vals;
        for (i = 0; i < vals; i++) {
            bytes += (lacing_vals[i] & 0xff);
            header[i + 27] = (byte) (lacing_vals[i] & 0xff);
        }

        // set pointers in the ogg_page struct
        og.header_base = header;
        og.header = 0;
        og.header_len = header_fill = vals + 27;
        og.body_base = bodyData;
        og.body = bodyReturned;
        og.body_len = bytes;

        // advance the lacing data and set the bodyReturned pointer

        lacing_fill -= vals;
        System.arraycopy(lacing_vals, vals, lacing_vals, 0, lacing_fill);
        System.arraycopy(granule_vals, vals, granule_vals, 0, lacing_fill);
        bodyReturned += bytes;

        // calculate the checksum
        og.checksum();
        // done
        return true;
    }

    /**
     * This constructs pages from buffered packet segments.  The pointers
     * returned are to static buffers; do not free. The returned buffers are
     * good only until the next call (using the same ogg_stream_state)
     */
    public boolean pageOut(Page og) {
        if ((e_o_s && lacing_fill != 0) ||     // 'were done, now flush' case
                bodyFill - bodyReturned > 4096 ||   // 'page nominal size' case
                lacing_fill >= 255 ||               // 'segment table full' case
                (lacing_fill != 0 && !b_o_s)) { // 'initial header page' case
            return flush(og);
        }
        return false;
    }

    public boolean eof() {
        return e_o_s;
    }

    public int reset() {
        bodyFill = 0;
        bodyReturned = 0;

        lacing_fill = 0;
        lacing_packet = 0;
        lacing_returned = 0;

        header_fill = 0;

        e_o_s = false;
        b_o_s = false;
        pageNo = -1;
        packetNo = 0;
        granulePos = 0;
        return 0;
    }

    /**
     * Calls ogg_stream_packetpeek().
     */
    public int packetPeek(Packet packet) {
        return packetOutInternal(packet, false);
    }

    /**
     * Retrieves a packet from the internal storage for emission.
     * This method is called by packetOut and packetPeek.
     *
     * @param packet   the Packet object to store the retrieved packet
     *                 data in. May be null if bAdvance is false.
     * @param bAdvance should the internal pointers to the packet
     *                 data storage be advanced to the next packet after retrieving
     *                 this one? Called with a value of true for ordinary packet out
     *                 and with a value of false for packet peek.
     * @return 0: success, -1: failure
     */
    private int packetOutInternal(Packet packet, boolean bAdvance) {
        // The last part of decode. We have the stream broken into
        // packet segments.  Now we need to group them into packets
        // (or return the out of sync markers)

        int ptr = lacing_returned;

        if (lacing_packet <= ptr)
            return 0;

        if ((lacing_vals[ptr] & 0x400) != 0) {
            // we need to tell the codec there's a gap; it might need
            // to handle previous packet dependencies.
            lacing_returned++;
            packetNo++;
            return -1;
        }

        if (packet == null && !bAdvance)
            // just using peek as an inexpensive way
            // to ask if there's a whole packet
            // waiting
            return 1;
        // Gather the whole packet. We'll have no holes or a partial
        // packet
        int size = lacing_vals[ptr] & 0xFF;
        int bytes = size;
        // last packet of the stream?
        boolean eos = (lacing_vals[ptr] & 0x200) != 0;
        // first packet of the stream?
        boolean bos = (lacing_vals[ptr] & 0x100) != 0;

        while (size == 255) {
            int val = lacing_vals[++ptr];
            size = val & 0xff;
            if ((val & 0x200) != 0)
                eos = true;
            bytes += size;
        }

        if (packet != null) {
            packet.packetByte = Arrays.copyOfRange(bodyData, bodyReturned, bytes);
            packet.b_o_s = bos;
            packet.e_o_s = eos;
            packet.granulePos = granule_vals[ptr];
            packet.packetNo = packetNo;
        }

        if (bAdvance) {
            bodyReturned += bytes;
            lacing_returned = ptr + 1;
            packetNo++;
        }
        return 1;
    }
}
