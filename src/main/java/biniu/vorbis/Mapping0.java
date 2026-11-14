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
public class Mapping0 extends FuncMapping {

    static int seq = 0;
    private final Floor1 floor1 = new Floor1();
//    private final Window window = new Window();

    @Override
    public void clear(Object imap) {
        imap = null;
    }

    @Override
    public Object look(DspState vd, InfoMode vm, Object m) {

        Info vi = vd.vi;
        LookMapping0 look = new LookMapping0();
        InfoMapping0 info = look.map = (InfoMapping0) m;
        look.mode = vm;

        look.time_look = new Object[info.submaps];
        look.floor_look = new Object[info.submaps];
        look.residue_look = new LookResidue[info.submaps];

//        if (vd.analysisp != 0) {
//            look.floor_state = new Object[vi.channels];
//        }
//        if (vi.psys != 0) {
//            look.psy_look = new PsyLook[info.submaps];
//            for (int i = 0; i < info.submaps; i++) {
//                look.psy_look[i] = new PsyLook();
//            }
//        }

        look.time_func = new FuncTime[info.submaps];
        look.floor_func = new FuncFloor[info.submaps];
        look.residue_func = new FuncResidue[info.submaps];

        for (int i = 0; i < info.submaps; i++) {
            int timenum = info.timesubmap[i];
            int floornum = info.floorsubmap[i];
            int resnum = info.residuesubmap[i];

            look.time_func[i] = FuncTime.time_P[vi.time_type[timenum]];
            look.time_look[i] = look.time_func[i].look(vd, vm, vi.time_param[timenum]);
            look.floor_func[i] = FuncFloor.floor_P[vi.floor_type[floornum]];
            look.floor_look[i] = look.floor_func[i].
                    look(vd, vm, vi.floor_param[floornum]);
            look.residue_func[i] = FuncResidue.residue_P[vi.residue_type[resnum]];
            look.residue_look[i] = look.residue_func[i].look(vd, vm, vi.residue_param[resnum]);

//            if (vi.psys != 0 && vd.analysisp != 0) {
//                int psynum = info.psysubmap[i];
//                look.psy_look[i].init(vi.psy_param[psynum],
//                        vi.blocksizes[vm.blockflag] / 2, vi.rate);
//            }
        }

        if (vi.psys != 0 && vd.analysisp != 0) {
//            if (info -> psy[0] != info -> psy[1]) {
//                int psynum = info -> psy[0];
//                look -> psy_look[0] = _ogg_calloc(1, sizeof(vorbis_look_psy));
//                _vp_psy_init(look -> psy_look[0], ci -> psy_param[psynum],
//                        ci -> psy_g_param,
//                        ci -> blocksizes[vm -> blockflag] / 2, vi -> rate);
//                psynum = info -> psy[1];
//                look -> psy_look[1] = _ogg_calloc(1, sizeof(vorbis_look_psy));
//                _vp_psy_init(look -> psy_look[1], ci -> psy_param[psynum],
//                        ci -> psy_g_param,
//                        ci -> blocksizes[vm -> blockflag] / 2, vi -> rate);
//            } else {
//                int psynum = info -> psy[0];
//                look -> psy_look[0] = _ogg_calloc(1, sizeof(vorbis_look_psy));
//                look -> psy_look[1] = look -> psy_look[0];
//                _vp_psy_init(look -> psy_look[0], ci -> psy_param[psynum],
//                        ci -> psy_g_param,
//                        ci -> blocksizes[vm -> blockflag] / 2, vi -> rate);
//            }
        }

        look.ch = vi.channels;
//  if(vd->analysisp)drft_init(&look->fft_look,ci->blocksizes[vm->blockflag]);

        return look;
    }

    @Override
    public void pack(Info vi, Object imap, Buffer opb) {
        InfoMapping0 info = (InfoMapping0) imap;

        // another 'we meant to do it this way' hack...  up to beta 4, we
        // packed 4 binary zeros here to signify one submapping in use.  We
        // now redefine that to mean four bitflags that indicate use of
        // deeper features; bit0:submappings, bit1:coupling,
        // bit2,3:reserved. This is backward compatable with all actual uses
        // of the beta code.

        if (info.submaps > 1) {
            opb.write(1, 1);
            opb.write(info.submaps - 1, 4);
        } else {
            opb.write(0, 1);
        }

        if (info.coupling_steps > 0) {
            opb.write(1, 1);
            opb.write(info.coupling_steps - 1, 8);
            for (int i = 0; i < info.coupling_steps; i++) {
                opb.write(info.coupling_mag[i], ilog2(vi.channels));
                opb.write(info.coupling_ang[i], ilog2(vi.channels));
            }
        } else {
            opb.write(0, 1);
        }

        opb.write(0, 2); // 2,3:reserved

        // we don't write the channel submappings if we only have one...
        if (info.submaps > 1) {
            for (int i = 0; i < vi.channels; i++)
                opb.write(info.chmuxlist[i], 4);
        }
        for (int i = 0; i < info.submaps; i++) {
            opb.write(info.timesubmap[i], 8);
            opb.write(info.floorsubmap[i], 8);
            opb.write(info.residuesubmap[i], 8);
        }
    }

    /** also responsible for range checking */
    @Override
    public Object unpack(Info vi, Buffer opb) {
        InfoMapping0 info = new InfoMapping0();

        // !!!!
        if (opb.read(1) != 0) {
            info.submaps = opb.read(4) + 1;
        } else {
            info.submaps = 1;
        }

        if (opb.read(1) != 0) {
            info.coupling_steps = opb.read(8) + 1;

            for (int i = 0; i < info.coupling_steps; i++) {
                int testM = info.coupling_mag[i] = opb.read(ilog2(vi.channels));
                int testA = info.coupling_ang[i] = opb.read(ilog2(vi.channels));

                if (testM < 0 ||
                        testA < 0 ||
                        testM == testA ||
                        testM >= vi.channels ||
                        testA >= vi.channels) {
                    info.free();
                    return null;
                }
            }
        }

        if (opb.read(2) > 0) { // 2,3:reserved
            info.free();
            return null;
        }

        if (info.submaps > 1) {
            for (int i = 0; i < vi.channels; i++) {
                info.chmuxlist[i] = opb.read(4);
                if (info.chmuxlist[i] >= info.submaps) {
                    info.free();
                    return null;
                }
            }
        }

        for (int i = 0; i < info.submaps; i++) {
            info.timesubmap[i] = opb.read(8);
            if (info.timesubmap[i] >= vi.times) {
                info.free();
                return null;
            }
            info.floorsubmap[i] = opb.read(8);
            if (info.floorsubmap[i] >= vi.floors) {
                info.free();
                return null;
            }
            info.residuesubmap[i] = opb.read(8);
            if (info.residuesubmap[i] >= vi.residues) {
                info.free();
                return null;
            }
        }
        return info;
    }

    float[][] pcmbundle = null;
    int[] zerobundle = null;
    int[] nonzero = null;
    Object[] floormemo = null;

    @Override
    synchronized int inverse(Block vb, Object l) {
        DspState vd = vb.dspState;
        Info vi = vd.vi;
        LookMapping0 look = (LookMapping0) l;
        InfoMapping0 info = look.map;
        InfoMode mode = look.mode;
        int n = vb.pcmEnd = vi.blocksizes[vb.W];

        float[] window = vd.window[vb.W][vb.lW][vb.nW][mode.windowtype];
        if (pcmbundle == null || pcmbundle.length < vi.channels) {
            pcmbundle = new float[vi.channels][];
            nonzero = new int[vi.channels];
            zerobundle = new int[vi.channels];
            floormemo = new Object[vi.channels];
        }

        // recover the spectral envelope; store it in the PCM vector for now
        for (int i = 0; i < vi.channels; i++) {
            float[] pcm = vb.pcm[i];
            int submap = info.chmuxlist[i];

            floormemo[i] = look.floor_func[submap].inverse1(vb, look.
                            floor_look[submap],
                    floormemo[i]
            );
            if (floormemo[i] != null) {
                nonzero[i] = 1;
            } else {
                nonzero[i] = 0;
            }
            for (int j = 0; j < n / 2; j++) {
                pcm[j] = 0;
            }
        }

        for (int i = 0; i < info.coupling_steps; i++) {
            if (nonzero[info.coupling_mag[i]] != 0 ||
                    nonzero[info.coupling_ang[i]] != 0) {
                nonzero[info.coupling_mag[i]] = 1;
                nonzero[info.coupling_ang[i]] = 1;
            }
        }

        // recover the residue, apply directly to the spectral envelope

        for (int i = 0; i < info.submaps; i++) {
            int ch_in_bundle = 0;
            for (int j = 0; j < vi.channels; j++) {
                if (info.chmuxlist[j] == i) {
                    if (nonzero[j] != 0) {
                        zerobundle[ch_in_bundle] = 1;
                    } else {
                        zerobundle[ch_in_bundle] = 0;
                    }
                    pcmbundle[ch_in_bundle++] = vb.pcm[j];
                }
            }

            look.residue_func[i].inverse(vb, look.residue_look[i],
                    pcmbundle, zerobundle, ch_in_bundle);
        }

        for (int i = info.coupling_steps - 1; i >= 0; i--) {
            float[] pcmM = vb.pcm[info.coupling_mag[i]];
            float[] pcmA = vb.pcm[info.coupling_ang[i]];

            for (int j = 0; j < n / 2; j++) {
                float mag = pcmM[j];
                float ang = pcmA[j];

                if (mag > 0) {
                    if (ang > 0) {
                        pcmM[j] = mag;
                        pcmA[j] = mag - ang;
                    } else {
                        pcmA[j] = mag;
                        pcmM[j] = mag + ang;
                    }
                } else {
                    if (ang > 0) {
                        pcmM[j] = mag;
                        pcmA[j] = mag + ang;
                    } else {
                        pcmA[j] = mag;
                        pcmM[j] = mag - ang;
                    }
                }
            }
        }

        // compute and apply spectral envelope

        for (int i = 0; i < vi.channels; i++) {
            float[] pcm = vb.pcm[i];
            int submap = info.chmuxlist[i];
            look.floor_func[submap].inverse2(vb, look.floor_look[submap], floormemo[i],
                    pcm);
        }

        // transform the PCM data; takes PCM vector, vb; modifies PCM vector
        // only MDCT right now....

        for (int i = 0; i < vi.channels; i++) {
            float[] pcm = vb.pcm[i];
            ((Mdct) vd.transform[vb.W][0]).backward(pcm, pcm);
        }

        // window the data
        for (int i = 0; i < vi.channels; i++) {
            float[] pcm = vb.pcm[i];
            if (nonzero[i] != 0) {
                for (int j = 0; j < n; j++) {
                    pcm[j] *= window[j];
                }
            } else {
                for (int j = 0; j < n; j++) {
                    pcm[j] = 0.f;
                }
            }
        }

        return 0;
    }

    private static int ilog2(int v) {
        int ret = 0;
        while (v > 1) {
            ret++;
            v >>>= 1;
        }
        return ret;
    }

    public static float todB(float x) {
        return ((Float.floatToIntBits(x) & 0x7fffffff) * 7.17711438e-7f -
                764.6161886f);
    }

    private static float todB345(float x) {
        return (((Float.floatToIntBits(x) & 0x7fffffff) * 7.17711438e-7f) -
                764.2711886f); //764.6161886f);
    }

    private static float todB05(float x) {
        return (((Float.floatToIntBits(x) & 0x7fffffff) * 3.58855719e-7f) -
                381.9630943f); //764.6161886f);
    }

    @Override
    public int mapping0Forward(Block vb) {

        CodecSetupInfo ci = vb.dspState.vi.getCodecSetup();
        BlockInternal vbi = vb.blockInternal;
        int n = vb.pcmEnd;
        int i, j, k;
        int channels = vb.dspState.vi.channels;

        int[] nonzero = new int[channels];
        float[][] gmdct = new float[channels][];
        int[][] ilogmaskch = new int[channels][];
        int[][][] floor_posts = new int[channels][][];

        float global_ampmax = vbi.ampmax;
        float[] local_ampmax = new float[channels];
        int blocktype = vbi.blocktype;

        int modenumber = vb.W;
        InfoMapping0 info = ci.mapParam[modenumber];
        PsyLook psyLook = vb.dspState.backEndState.psy[blocktype + (vb.W != 0 ? 2 : 0)];

        vb.mode = modenumber;

        for (i = 0; i < channels; i++) {
            float scale = 4.f / n;
            float scale_dB;

            float[] pcm = vb.pcm[i];
            float[] logfft = pcm;

            gmdct[i] = new float[n / 2];

            // + .345 is a hack; the original
            // todB estimation used on IEEE 754
            // compliant machines had a bug that
            // returned dB values about a third
            // of a decibel too high.  The bug
            // was harmless because tunings
            // implicitly took that into
            // account.  However, fixing the bug
            // in the estimator requires
            // changing all the tunings as well.
            // For now, it's easier to sync
            // things back up here, and
            // recalibrate the tunings in the
            // next major model upgrade.
            scale_dB = todB345(scale);

            // window the PCM data
            Window.applyWindow(pcm, vb.dspState.backEndState.window,
                    ci.blocksizes, vb.lW, vb.W, vb.nW);

            // transform the PCM data
            // only MDCT right now....
            vb.dspState.backEndState.transform[vb.W][0].mdct_forward(pcm, gmdct[i]);

            // FFT yields more accurate tonal estimation (not phase sensitive)
            vb.dspState.backEndState.fftLook[vb.W].drft_forward(pcm);

            // + .345 is a hack; the
            // original todB estimation used on
            // IEEE 754 compliant machines had a
            // bug that returned dB values about
            // a third of a decibel too high.
            // The bug was harmless because
            // tunings implicitly took that into
            // account.  However, fixing the bug
            // in the estimator requires
            // changing all the tunings as well.
            // For now, it's easier to sync
            // things back up here, and
            // recalibrate the tunings in the
            // next major model upgrade.
            logfft[0] = scale_dB + todB345(pcm[0]);
            local_ampmax[i] = logfft[0];
            for (j = 1; j < n - 1; j += 2) {
                float temp = pcm[j] * pcm[j] + pcm[j + 1] * pcm[j + 1];
                // + .345 is a hack; the original todB
                // estimation used on IEEE 754
                // compliant machines had a bug that
                // returned dB values about a third
                // of a decibel too high.  The bug
                // was harmless because tunings
                // implicitly took that into
                // account.  However, fixing the bug
                // in the estimator requires
                // changing all the tunings as well.
                // For now, it's easier to sync
                // things back up here, and
                // recalibrate the tunings in the
                // next major model upgrade.
                temp = logfft[(j + 1) >> 1] = scale_dB + todB05(temp);

                if (temp > local_ampmax[i])
                    local_ampmax[i] = temp;
            }

            if (local_ampmax[i] > 0.f) local_ampmax[i] = 0.f;
            if (local_ampmax[i] > global_ampmax) global_ampmax = local_ampmax[i];
        }

        {
            float[] noise = new float[n / 2];
            float[] tone = new float[n / 2];

            for (i = 0; i < channels; i++) {
                // the encoder setup assumes that all the modes used by any
                // specific bitrate tweaking use the same floor

                int submap = info.chmuxlist[i];

                // the following makes things clearer to *me* anyway
                float[] mdct = gmdct[i];
                float[] logfft = vb.pcm[i];

                float[] logmask = logfft;

                vb.mode = modenumber;

                floor_posts[i] = new int[Const.PACKETBLOBS][];

                for (j = 0; j < n / 2; j++)
                    // + .345 is a hack; the original todB estimation used on IEEE 754
                    // compliant machines had a bug that returned dB values about a third
                    // of a decibel too high.  The bug was harmless because tunings
                    // implicitly took that into account.  However, fixing the bug in the
                    // estimator requires changing all the tunings as well. For now, it's
                    // easier to sync things back up here, and recalibrate the tunings in
                    // the next major model upgrade.
                    logfft[j + n / 2] = todB345(mdct[j]);

                // first step; noise masking.  Not only does 'noise masking' give us
                // curves from which we can decide how much resolution to give noise
                // parts of the spectrum, it also implicitly hands us a tonality
                // estimate (the larger the value in the 'noise_depth' vector, the more
                // tonal that area is)
                psyLook.noiseMask(logfft, n / 2, noise);
                // noise does not have by-frequency offset bias applied yet

                // second step: 'all the other crap'; all the stuff that isn't
                // computed/fit for bitrate management goes in the second psy
                // vector. This includes tone masking, peak limiting and ATH

                psyLook.toneMask(logfft,
                        tone,
                        global_ampmax,
                        local_ampmax[i]);

                // third step; we offset the noise vectors, overlay tone
                // masking.  We then do a floor1-specific line fit.  If we're
                // performing bitrate management, the line fit is performed
                // multiple times for up/down tweakage on demand.

                psyLook.offsetAndMix(noise,
                        tone,
                        1,
                        logmask,
                        mdct,
                        logfft,
                        n / 2);

                // this algorithm is hardwired to floor 1 for now; abort out if
                // we're *not* floor1.  This won't happen unless someone has
                // broken the encode setup lib. Guard it anyway.
                if (ci.floorType[info.floorsubmap[submap]] != 1)
                    return -1;

                floor_posts[i][Const.PACKETBLOBS / 2] =
                        Floor1.fit(vb, info.floorsubmap[submap],
                                logfft,
                                n / 2,
                                logmask);

                // are we managing bitrate?  If so, perform two more fits for
                // later rate tweaking (fits represent hi/lo) */
                if ((vb.bitrateManaged()) &&
                        (floor_posts[i][Const.PACKETBLOBS / 2] != null)) {
                    // higher rate by way of lower noise curve

                    psyLook.offsetAndMix(noise,
                            tone,
                            2,
                            logmask,
                            mdct,
                            logfft,
                            n / 2);

                    floor_posts[i][Const.PACKETBLOBS - 1] =
                            Floor1.fit(vb, info.floorsubmap[submap],
                                    logfft,
                                    n / 2,
                                    logmask);

                    // lower rate by way of higher noise curve
                    psyLook.offsetAndMix(noise,
                            tone,
                            0,
                            logmask,
                            mdct,
                            logfft,
                            n / 2);

                    floor_posts[i][0] =
                            Floor1.fit(vb, info.floorsubmap[submap],
                                    logfft,
                                    n / 2,
                                    logmask);

                    // we also interpolate a range of intermediate curves for
                    // intermediate rates
                    for (k = 1; k < Const.PACKETBLOBS / 2; k++)
                        floor_posts[i][k] =
                                Floor1.interpolate(vb, info.floorsubmap[submap],
                                        floor_posts[i][0],
                                        floor_posts[i][Const.PACKETBLOBS / 2],
                                        k * 65536 / (Const.PACKETBLOBS / 2));

                    for (k = Const.PACKETBLOBS / 2 + 1; k < Const.PACKETBLOBS - 1; k++)
                        floor_posts[i][k] =
                                Floor1.interpolate(vb, info.floorsubmap[submap],
                                        floor_posts[i][Const.PACKETBLOBS / 2],
                                        floor_posts[i][Const.PACKETBLOBS - 1],
                                        (k - Const.PACKETBLOBS / 2) * 65536 / (Const.PACKETBLOBS / 2));
                }
            }
        }

        vbi.ampmax = global_ampmax;

        // the next phases are performed once for vbr-only and PACKETBLOB
        // times for bitrate managed modes.
        // 1) encode actual mode being used
        // 2) encode the floor for each channel, compute coded mask curve/res
        // 3) normalize and couple.
        // 4) encode residue
        // 5) save packet bytes to the packetblob vector

        // iterate over the many masking curve fits we've created

        {
            float[][] couple_bundle = new float[channels][];
            int[] zerobundle = new int[channels];
            int[][] sortindex = new int[channels][];
            float[][] mag_memo = new float[1][1];
            int[][] mag_sort = new int[1][1];

            if (info.coupling_steps != 0) {
                mag_memo = psyLook.quantizeCoupleMemo(
                        ci.psyGlobParam,
                        info,
                        gmdct);

                mag_sort = psyLook.quantizeCoupleSort(
                        info,
                        mag_memo);

                psyLook.hfReduction(ci.psyGlobParam,
                        info,
                        mag_memo);
            }

            if (psyLook.vi.normal_channel_p != 0) {
                for (i = 0; i < channels; i++) {
                    float[] mdct = gmdct[i];
                    sortindex[i] = new int[n / 2];
                    sortindex[i] = psyLook.noisenNormalizeSort(mdct);
                }
            }

            for (k = ((vb.bitrateManaged()) ? 0 :
                    Const.PACKETBLOBS / 2);
                 k <=
                         ((vb.bitrateManaged()) ? Const.PACKETBLOBS - 1 :
                                 Const.PACKETBLOBS / 2);
                 k++) {
                Buffer opb = vbi.packetblob[k];

                // start out our new packet blob with packet type and mode
                // Encode the packet type
                opb.write(0, 1);
                // Encode the modenumber
                // Encode frame mode, pre,post windowsize, then dispatch
                opb.write(modenumber, vb.dspState.backEndState.modeBits);
                if (vb.W != 0) {
                    opb.write(vb.lW, 1);
                    opb.write(vb.nW, 1);
                }

                // encode floor, compute masking curve, sep out residue
                for (i = 0; i < channels; i++) {
                    int submap = info.chmuxlist[i];
                    float[] mdct = gmdct[i];
                    float[] res = vb.pcm[i];
                    int[] ilogmask = ilogmaskch[i] = new int[n / 2];

                    nonzero[i] = Floor1.encode(vb, opb, info.floorsubmap[submap],
                            floor_posts[i][k],
                            ilogmask);

                    psyLook.removeFloor(mdct,
                            ilogmask,
                            res,
                            ci.psyGlobParam.sliding_lowpass[vb.W][k]);

                    psyLook.noiseNormalize(res, res, (n / 2), sortindex[i]);

                }

                // our iteration is now based on masking curve, not prequant and
                // coupling. Only one prequant/coupling step

                // quantize/couple
                // incomplete implementation that assumes the tree is all depth
                // one, or no tree at all
                if (info.coupling_steps != 0) {
                    psyLook.couple(k,
                            ci.psyGlobParam,
                            info,
                            vb.pcm,
                            mag_memo,
                            mag_sort,
                            ilogmaskch,
                            nonzero,
                            ci.psyGlobParam.sliding_lowpass[vb.W][k]);
                }

                // classify and encode by submap
                for (i = 0; i < info.submaps; i++) {
                    int ch_in_bundle = 0;
                    int[][] classifications;
                    int resnum = info.residuesubmap[i];

                    for (j = 0; j < channels; j++) {
                        if (info.chmuxlist[j] == i) {
                            zerobundle[ch_in_bundle] = 0;
                            if (nonzero[j] != 0)
                                zerobundle[ch_in_bundle] = 1;
                            couple_bundle[ch_in_bundle] = new float[vb.pcm[j].length - (n / 2)];
                            System.arraycopy(vb.pcm[j], n / 2, couple_bundle[ch_in_bundle], 0,
                                    couple_bundle[ch_in_bundle].length);
                            ch_in_bundle++;
                        }
                    }

                    classifications = FuncResidue.residue_P[ci.residueType[resnum]].
                            clas(vb.dspState.backEndState.residue[resnum],
                                    vb.pcm, n / 2, zerobundle, channels);
                    FuncResidue.residue_P[ci.residueType[resnum]].
                            forward(opb, vb, vb.dspState.backEndState.residue[resnum],
                                    couple_bundle, null, zerobundle, ch_in_bundle,
                                    classifications);
                }

                // ok, done encoding.  Next protopacket.
            }
        }

        return 0;
    }
}

class LookMapping0 {

    InfoMode mode;
    InfoMapping0 map;
    Object[] time_look;
    Object[] floor_look;
    Object[] floor_state;
    LookResidue[] residue_look;
    PsyLook[] psy_look;

    FuncTime[] time_func;
    FuncFloor[] floor_func;
    FuncResidue[] residue_func;

    int ch;
    float[][] decay;
    /**
     * if a different mode is called, we need to
     * invalidate decay and floor state
     */
    int lastframe;
}
