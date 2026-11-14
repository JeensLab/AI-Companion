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
 * </p>
 * DECODING PRIMITIVES: packet streaming layer
 * <p>
 * This has two layers to place more of the multi-serialNo and paging
 * control in the application's hands.  First, we expose a data buffer
 * using ogg_decode_buffer().  The app either copies into the
 * buffer, or passes it directly to read(), etc.  We then call
 * ogg_decode_wrote() to tell how many bytes we just added.
 * <p>
 * Pages are returned (pointers into the buffer in ogg_sync_state)
 * by ogg_decode_stream().  The page is then submitted to
 * ogg_decode_page() along with the appropriate
 * ogg_stream_state* (ie, matching serialNo).  We then get raw
 * packets out calling ogg_stream_packet() with an
 * ogg_stream_state.  See the 'frame-prog.txt' docs for details and
 * example code.
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class SyncState {

    public byte[] data;
    private int storage;
    private int fill;
    private int returned;

    private int unsynced;
    private int headerBytes;
    private int bodyBytes;

    public int clear() {
        data = null;
        return 0;
    }

    // !!!!!!!!!!!!
//  byte[] buffer(int size) {

    public int buffer(int size) {
        // first, clear out any space that has been previously returned
        if (returned != 0) {
            fill -= returned;
            if (fill > 0) {
                System.arraycopy(data, returned, data, 0, fill);
            }
            returned = 0;
        }

        if (size > storage - fill) {
            // We need to extend the internal buffer
            int newsize = size + fill + 4096; // an extra page to be nice
            if (data != null) {
                byte[] foo = new byte[newsize];
                System.arraycopy(data, 0, foo, 0, data.length);
                data = foo;
            } else {
                data = new byte[newsize];
            }
            storage = newsize;
        }

        // expose a segment at least as large as requested at the fill mark
//        return((char *)oy->data+oy->fill);
//        return(data);
        return fill;
    }

    public int wrote(int bytes) {
        if (fill + bytes > storage) return -1;
        fill += bytes;
        return 0;
    }

    // sync the stream.  This is meant to be useful for finding page
    // boundaries.
    //
    // return values for this:
    // -n) skipped n bytes
    //  0) page not ready; more data (no bytes skipped)
    //  n) page synced at current location; page length n bytes

    private final Page pageseek = new Page();
    private final byte[] chksum = new byte[4];

    public int pageSeek(Page og) {
        int page = returned;
        int next;
        int bytes = fill - returned;

        if (headerBytes == 0) {
            int _headerbytes, i;
            if (bytes < 27) return 0; // not enough for a header

            // verify capture pattern
            if (data[page] != 'O' ||
                    data[page + 1] != 'g' ||
                    data[page + 2] != 'g' ||
                    data[page + 3] != 'S') {
                headerBytes = 0;
                bodyBytes = 0;

                // search for possible capture
                next = 0;
                for (int ii = 0; ii < bytes - 1; ii++) {
                    if (data[page + 1 + ii] == 'O') {
                        next = page + 1 + ii;
                        break;
                    }
                }
                //next=memchr(page+1,'O',bytes-1);
                if (next == 0) next = fill;

                returned = next;
                return -(next - page);
            }
            _headerbytes = (data[page + 26] & 0xff) + 27;
            if (bytes < _headerbytes) return 0; // not enough for header + seg table

            // count up body length in the segment table

            for (i = 0; i < (data[page + 26] & 0xff); i++) {
                bodyBytes += (data[page + 27 + i] & 0xff);
            }
            headerBytes = _headerbytes;
        }

        if (bodyBytes + headerBytes > bytes) return 0;

        // The whole test page is buffered.  Verify the checksum
        synchronized (chksum) {
            // Grab the checksum bytes, set the header field to zero

            System.arraycopy(data, page + 22, chksum, 0, 4);
            data[page + 22] = 0;
            data[page + 23] = 0;
            data[page + 24] = 0;
            data[page + 25] = 0;

            // set up a temp page struct and recompute the checksum
            Page log = pageseek;
            log.header_base = data;
            log.header = page;
            log.header_len = headerBytes;

            log.body_base = data;
            log.body = page + headerBytes;
            log.body_len = bodyBytes;
            log.checksum();

            // Compare
            if (chksum[0] != data[page + 22] ||
                    chksum[1] != data[page + 23] ||
                    chksum[2] != data[page + 24] ||
                    chksum[3] != data[page + 25]) {
                // D'oh.  Mismatch! Corrupt page (or miscapture and not a page at all)
                // replace the computed checksum with the one actually read in
                System.arraycopy(chksum, 0, data, page + 22, 4);
                // Bad checksum. Lose sync

                headerBytes = 0;
                bodyBytes = 0;
                // search for possible capture
                next = 0;
                for (int ii = 0; ii < bytes - 1; ii++) {
                    if (data[page + 1 + ii] == 'O') {
                        next = page + 1 + ii;
                        break;
                    }
                }
                //next=memchr(page+1,'O',bytes-1);
                if (next == 0) next = fill;
                returned = next;
                return (-(next - page));
            }
        }

        // yes, have a whole page all ready to go
        {
            page = returned;

            if (og != null) {
                og.header_base = data;
                og.header = page;
                og.header_len = headerBytes;
                og.body_base = data;
                og.body = page + headerBytes;
                og.body_len = bodyBytes;
            }

            unsynced = 0;
            returned += (bytes = headerBytes + bodyBytes);
            headerBytes = 0;
            bodyBytes = 0;
            return bytes;
        }
//        headerBytes = 0;
//        bodyBytes = 0;
//        next = 0;
//        for (int ii = 0; ii < bytes - 1; ii++) {
//            if (data[page + 1 + ii] == 'O') {
//                next = page + 1 + ii;
//            }
//        }
//        //next = memchr(page + 1, 'O', bytes - 1);
//        if (next == 0) next = fill;
//        returned = next;
//        return (-(next - page));
    }

    /**
     * sync the stream and get a page.  Keep trying until we find a page.
     * Supress 'sync errors' after reporting the first.
     *
     * @return values:
     * -1) recapture (hole in data)
     * 0) need more data
     * 1) page returned
     * <p>
     * Returns pointers into buffered data; invalidated by next call to
     * _stream, _clear, _init, or _buffer
     */
    public int pageOut(Page og) {
        // all we need to do is verify a page at the head of the stream
        // buffer.  If it doesn't verify, we look for the next potential
        // frame

        while (true) {
            int ret = pageSeek(og);
            if (ret > 0) {
                // have a page
                return 1;
            }
            if (ret == 0) {
                // need more data
                return 0;
            }

            // head did not start a synced page... skipped some bytes
            if (unsynced == 0) {
                unsynced = 1;
                return -1;
            }
            // loop. keep looking
        }
    }

    /** clear things to an initial state.  Good to call, eg, before seeking */
    public int reset() {
        fill = 0;
        returned = 0;
        unsynced = 0;
        headerBytes = 0;
        bodyBytes = 0;
        return 0;
    }

    public void init() {
    }

    public int getDataOffset() {
        return returned;
    }

    public int getBufferOffset() {
        return fill;
    }

    /**
     * Writes to the stream buffer.
     */
    public int write(byte[] abBuffer, int nBytes) {
        // Clear out space that has been previously returned.
        if (returned > 0) {
            fill -= returned;
            if (fill > 0) {
                System.arraycopy(data, returned, data, 0, fill);
            }
            returned = 0;
        }

        // Check for enough space in the stream buffer and if it is
        // allocated at all. If there isn't sufficient space, extend
        // the buffer.
        if (data == null || nBytes > data.length - fill) {
            int nNewSize = nBytes + fill + 4096;
            byte[] abOldBuffer = data;
            data = new byte[nNewSize];
            if (abOldBuffer != null) {
                System.arraycopy(abOldBuffer, 0, data, 0, fill);
            }
        }

        // Now finally fill with the new data.
        System.arraycopy(abBuffer, 0, data, fill, nBytes);
        fill += nBytes;
        return 0;
    }
}
