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
public class EnvelopeLookUp {

    int ch;
    int winlength;
    int searchstep;
    float minenergy;

    final Mdct mdct;
    float[] mdct_win;

    final EnvelopeBand[] band = new EnvelopeBand[EnvelopeInt.VE_BANDS];
    EnvelopeFilterState[] filter;
    int stretch;

    int[] mark;

    int storage;
    int current;
    int curmark;
    int cursor;

    public EnvelopeLookUp() {
        this.mdct = new Mdct();
        for (int i = 0; i < this.band.length; i++) {
            this.band[i] = new EnvelopeBand();
        }

    }

    public void envelopeInit(Info vi) {
        CodecSetupInfo ci = vi.getCodecSetup();
        InfoPsyGlobal gi = ci.psyGlobParam;
        int ch = vi.channels;
        int i, j;
        int n = this.winlength = 128;
        this.searchstep = 64; // not random

        this.minenergy = gi.preecho_minenergy;
        this.ch = ch;
        this.storage = 128;
        this.cursor = ci.blocksizes[1] / 2;
        this.mdct_win = new float[n];
        this.mdct.init(n);

        for (i = 0; i < n; i++) {
            this.mdct_win[i] = (float) (Math.sin(i / (n - 1.) * EnvelopeInt.M_PI));
            this.mdct_win[i] *= this.mdct_win[i];
        }

        // magic follows
        this.band[0].begin = 2;
        this.band[0].end = 4;
        this.band[1].begin = 4;
        this.band[1].end = 5;
        this.band[2].begin = 6;
        this.band[2].end = 6;
        this.band[3].begin = 9;
        this.band[3].end = 8;
        this.band[4].begin = 13;
        this.band[4].end = 8;
        this.band[5].begin = 17;
        this.band[5].end = 8;
        this.band[6].begin = 22;
        this.band[6].end = 8;

        for (j = 0; j < EnvelopeInt.VE_BANDS; j++) {
            n = this.band[j].end;
            this.band[j].window = new float[n];
            for (i = 0; i < n; i++) {
                this.band[j].window[i] = (float) (Math.sin((i + .5) / n * EnvelopeInt.M_PI));
                this.band[j].total += this.band[j].window[i];
            }
            this.band[j].total = 1.f / this.band[j].total;
        }

        this.filter = new EnvelopeFilterState[EnvelopeInt.VE_BANDS * ch];
        for (i = 0; i < EnvelopeInt.VE_BANDS * ch; i++) {
            this.filter[i] = new EnvelopeFilterState();
        }
        this.mark = new int[this.storage];

    }

    public void envelopeClear() {
        int i;
        this.mdct.clear();
        for (i = 0; i < EnvelopeInt.VE_BANDS; i++) {
            this.band[i].window = null;
        }
    }

    public void envelopeShift(int shift) {
        int smallsize = this.current / this.searchstep + EnvelopeInt.VE_POST; // adjust for placing marks ahead of ve.current
        int smallshift = shift / this.searchstep;

        System.arraycopy(this.mark, smallshift, this.mark, 0, smallsize - smallshift);

        this.current -= shift;
        if (this.curmark >= 0)
            this.curmark -= shift;
        this.cursor -= shift;
    }

    // fairly straight threshhold-by-band based until we find something
    // that works better and isn't patented.

    public int amp(InfoPsyGlobal gi,
                   float[] data,
                   EnvelopeBand[] bands,
                   EnvelopeFilterState[] filters,
                   int pfilters,
                   int pos) {
        int n = this.winlength;
        int ret = 0;
        int i, j;
        float decay;

        // we want to have a 'minimum bar' for energy, else we're just
        // basing blocks on quantization noise that outweighs the signal
        // itself (for low power signals)

        float minV = this.minenergy;
        float[] vec = new float[n];

        // stretch is used to gradually lengthen the number of windows
        //   considered prevoius-to-potential-trigger
        int stretch = Math.max(EnvelopeInt.VE_MINSTRETCH, this.stretch / 2);
        float penalty = gi.stretch_penalty - (this.stretch / 2f - EnvelopeInt.VE_MINSTRETCH);
        if (penalty < 0.f) penalty = 0.f;
        if (penalty > gi.stretch_penalty) penalty = gi.stretch_penalty;

        //_analysis_output_always("lpcm",seq2,data,n,0,0,
        //  totalshift+pos*ve.searchstep);

        // window and transform
        for (i = 0; i < n; i++) {
//      vec[i]=(float)i;
            vec[i] = data[i] * this.mdct_win[i];
        }

        this.mdct.mdct_forward(vec, vec);

        // near-DC spreading function; this has nothing to do with
        //   psychoacoustics, just sidelobe leakage and window size
        {
            float temp = vec[0] * vec[0] + .7f * vec[1] * vec[1] + .2f * vec[2] * vec[2];
            int ptr = filters[pfilters].nearptr;

            // the accumulation is regularly refreshed from scratch to avoid
            //   floating point creep
            if (ptr == 0) {
                decay = filters[pfilters].nearDC_acc = filters[pfilters].nearDC_partialacc + temp;
                filters[pfilters].nearDC_partialacc = temp;
            } else {
                decay = filters[pfilters].nearDC_acc += temp;
                filters[pfilters].nearDC_partialacc += temp;
            }
            filters[pfilters].nearDC_acc -= filters[pfilters].nearDC[ptr];
            filters[pfilters].nearDC[ptr] = temp;

            decay = (float) (decay * (1. / (EnvelopeInt.VE_NEARDC + 1)));
            filters[pfilters].nearptr++;
            if (filters[pfilters].nearptr >= EnvelopeInt.VE_NEARDC) filters[pfilters].nearptr = 0;
            decay = Mapping0.todB(decay) * .5f - 15.f;
        }

        // perform spreading and limiting, also smooth the spectrum.  yes,
        //   the MDCT results in all real coefficients, but it still *behaves*
        //   like real/imaginary pairs
        for (i = 0; i < n / 2; i += 2) {
            float val = vec[i] * vec[i] + vec[i + 1] * vec[i + 1];
            val = Mapping0.todB(val) * 0.5f;
            if (val < decay) val = decay;
            if (val < minV) val = minV;
            vec[i >> 1] = val;
            decay = (float) (decay - 8.);
        }

        // perform preecho/postecho triggering by band
        for (j = 0; j < EnvelopeInt.VE_BANDS; j++) {
            float acc = 0.f;
            float valmax = 0, valmin = 0;

            // accumulate amplitude
            for (i = 0; i < bands[j].end; i++)
                acc += vec[i + bands[j].begin] * bands[j].window[i];

            acc *= bands[j].total;

            // convert amplitude to delta
            {
                int p, thisx = filters[j + pfilters].ampptr;
                float postmax, postmin, premax = -99999.f, premin = 99999.f;

                p = thisx;
                p--;
                if (p < 0) p += EnvelopeInt.VE_AMP;
                postmax = Math.max(acc, filters[j + pfilters].ampbuf[p]);
                postmin = Math.min(acc, filters[j + pfilters].ampbuf[p]);

                for (i = 0; i < stretch; i++) {
                    p--;
                    if (p < 0) p += EnvelopeInt.VE_AMP;
                    premax = Math.max(premax, filters[j + pfilters].ampbuf[p]);
                    premin = Math.min(premin, filters[j + pfilters].ampbuf[p]);
                }

                valmin = postmin - premin;
                valmax = postmax - premax;

                //filters[j].markers[pos]=valmax;
                filters[j + pfilters].ampbuf[thisx] = acc;
                filters[j + pfilters].ampptr++;
                if (filters[j + pfilters].ampptr >= EnvelopeInt.VE_AMP) filters[j + pfilters].ampptr = 0;
            }

            // look at min/max, decide trigger
            if (valmax > gi.preecho_thresh[j] + penalty) {
                ret |= 1;
                ret |= 4;
            }
            if (valmin < gi.postecho_thresh[j] - penalty) ret |= 2;
        }

        return ret;
    }
}

class EnvelopeFilterState {

    final float[] ampbuf = new float[EnvelopeInt.VE_AMP];
    int ampptr;

    final float[] nearDC = new float[EnvelopeInt.VE_NEARDC];
    float nearDC_acc;
    float nearDC_partialacc;
    int nearptr;
}

class EnvelopeBand {

    int begin;
    int end;
    float[] window;
    float total;
}
