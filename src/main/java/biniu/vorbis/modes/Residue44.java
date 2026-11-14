/*
 * THIS FILE IS PART OF THE OggVorbis SOFTWARE CODEC SOURCE CODE.
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.
 *
 * THE OggVorbis SOURCE CODE IS (C) COPYRIGHT 1994-2002
 * by the XIPHOPHORUS Company http://www.xiph.org/
 */

package biniu.vorbis.modes;

import biniu.vorbis.InfoMapping0;
import biniu.vorbis.InfoResidue0;
import biniu.vorbis.StaticCodeBook;
import biniu.vorbis.VorbisMappingTemplate;
import biniu.vorbis.VorbisResidueTemplate;
import biniu.vorbis.books.coupled.ResBooksStereo44c0s;
import biniu.vorbis.books.coupled.ResBooksStereo44c0sm;
import biniu.vorbis.books.coupled.ResBooksStereo44c1s;
import biniu.vorbis.books.coupled.ResBooksStereo44c1sm;
import biniu.vorbis.books.coupled.ResBooksStereo44c2s;
import biniu.vorbis.books.coupled.ResBooksStereo44c3s;
import biniu.vorbis.books.coupled.ResBooksStereo44c4s;
import biniu.vorbis.books.coupled.ResBooksStereo44c5s;
import biniu.vorbis.books.coupled.ResBooksStereo44c6s;
import biniu.vorbis.books.coupled.ResBooksStereo44c7s;
import biniu.vorbis.books.coupled.ResBooksStereo44c8s;
import biniu.vorbis.books.coupled.ResBooksStereo44c9s;
import biniu.vorbis.books.coupled.ResBooksStereo44cn1s;
import biniu.vorbis.books.coupled.ResBooksStereo44cn1sm;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 * <p>
 * function: toplevel residue templates for 32/44.1/48kHz
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Residue44 {

    // residue backends

    static final InfoResidue0 _residue_44_low = new InfoResidue0(
            0, -1, -1, 9, -1,
            /*  0     1     2     3     4     5     6     7  */
            Util.intTab(0),
            Util.intTab(-1),
            Util.intTab(.5f, 1.5f, 2.5f, 2.5f, 4.5f, 8.5f, 16.5f, 32.5f),
            Util.intTab(.5f, .5f, .5f, 999.f, 4.5f, 8.5f, 16.5f, 32.5f)
    );

    static final InfoResidue0 _residue_44_mid = new InfoResidue0(
            0, -1, -1, 10, -1,
            /*  0     1     2     3     4     5     6     7     8  */
            Util.intTab(0),
            Util.intTab(-1),
            Util.intTab(.5f, 1.5f, 1.5f, 2.5f, 2.5f, 4.5f, 8.5f, 16.5f, 32.5f),
            Util.intTab(.5f, .5f, 999.f, .5f, 999.f, 4.5f, 8.5f, 16.5f, 32.5f)
    );

    static final InfoResidue0 _residue_44_high = new InfoResidue0(
            0, -1, -1, 10, -1,
            /*  0     1     2     3     4     5     6     7     8  */
            Util.intTab(0),
            Util.intTab(-1),
            Util.intTab(.5f, 1.5f, 2.5f, 4.5f, 8.5f, 16.5f, 32.5f, 71.5f, 157.5f),
            Util.intTab(.5f, 1.5f, 2.5f, 3.5f, 4.5f, 8.5f, 16.5f, 71.5f, 157.5f)
    );

    /* mapping conventions:
         only one submap (this would change for efficient 5.1 support for example)*/
    /* Four psychoacoustic profiles are used, one for each blocktype */
    static final InfoMapping0[] _map_nominal = {
            new InfoMapping0(1, Util.intTab(0, 0), Util.intTab(0), Util.intTab(0), 1, Util.intTab(0), Util.intTab(1)),
            new InfoMapping0(1, Util.intTab(0, 0), Util.intTab(1), Util.intTab(1), 1, Util.intTab(0), Util.intTab(1))
    };

    static final StaticCodeBook[][] _resbook_44s_n1 = {

            {null, null, null},
            {null, null, ResBooksStereo44cn1s._44cn1_s_p1_0},
            {null, null, ResBooksStereo44cn1s._44cn1_s_p2_0},
            {null, null, ResBooksStereo44cn1s._44cn1_s_p3_0},
            {null, null, ResBooksStereo44cn1s._44cn1_s_p4_0},
            {null, null, ResBooksStereo44cn1s._44cn1_s_p5_0},
            {ResBooksStereo44cn1s._44cn1_s_p6_0, ResBooksStereo44cn1s._44cn1_s_p6_1, null},
            {ResBooksStereo44cn1s._44cn1_s_p7_0, ResBooksStereo44cn1s._44cn1_s_p7_1, null},
            {ResBooksStereo44cn1s._44cn1_s_p8_0, ResBooksStereo44cn1s._44cn1_s_p8_1, ResBooksStereo44cn1s._44cn1_s_p8_2}

    };
    static final StaticCodeBook[][] _resbook_44sm_n1 = {

            {null, null, null},
            {null, null, ResBooksStereo44cn1sm._44cn1_sm_p1_0},
            {null, null, ResBooksStereo44cn1sm._44cn1_sm_p2_0},
            {null, null, ResBooksStereo44cn1sm._44cn1_sm_p3_0},
            {null, null, ResBooksStereo44cn1sm._44cn1_sm_p4_0},
            {null, null, ResBooksStereo44cn1sm._44cn1_sm_p5_0},
            {ResBooksStereo44cn1sm._44cn1_sm_p6_0, ResBooksStereo44cn1sm._44cn1_sm_p6_1, null},
            {ResBooksStereo44cn1sm._44cn1_sm_p7_0, ResBooksStereo44cn1sm._44cn1_sm_p7_1, null},
            {ResBooksStereo44cn1sm._44cn1_sm_p8_0, ResBooksStereo44cn1sm._44cn1_sm_p8_1, ResBooksStereo44cn1sm._44cn1_sm_p8_2}

    };

    static final StaticCodeBook[][] _resbook_44s_0 = {

            {null, null, null},
            {null, null, ResBooksStereo44c0s._44c0_s_p1_0},
            {null, null, ResBooksStereo44c0s._44c0_s_p2_0},
            {null, null, ResBooksStereo44c0s._44c0_s_p3_0},
            {null, null, ResBooksStereo44c0s._44c0_s_p4_0},
            {null, null, ResBooksStereo44c0s._44c0_s_p5_0},
            {ResBooksStereo44c0s._44c0_s_p6_0, ResBooksStereo44c0s._44c0_s_p6_1, null},
            {ResBooksStereo44c0s._44c0_s_p7_0, ResBooksStereo44c0s._44c0_s_p7_1, null},
            {ResBooksStereo44c0s._44c0_s_p8_0, ResBooksStereo44c0s._44c0_s_p8_1, ResBooksStereo44c0s._44c0_s_p8_2}

    };
    static final StaticCodeBook[][] _resbook_44sm_0 = {

            {null, null, null},
            {null, null, ResBooksStereo44c0sm._44c0_sm_p1_0},
            {null, null, ResBooksStereo44c0sm._44c0_sm_p2_0},
            {null, null, ResBooksStereo44c0sm._44c0_sm_p3_0},
            {null, null, ResBooksStereo44c0sm._44c0_sm_p4_0},
            {null, null, ResBooksStereo44c0sm._44c0_sm_p5_0},
            {ResBooksStereo44c0sm._44c0_sm_p6_0, ResBooksStereo44c0sm._44c0_sm_p6_1, null},
            {ResBooksStereo44c0sm._44c0_sm_p7_0, ResBooksStereo44c0sm._44c0_sm_p7_1, null},
            {ResBooksStereo44c0sm._44c0_sm_p8_0, ResBooksStereo44c0sm._44c0_sm_p8_1, ResBooksStereo44c0sm._44c0_sm_p8_2}

    };

    static final StaticCodeBook[][] _resbook_44s_1 = {

            {null, null, null},
            {null, null, ResBooksStereo44c1s._44c1_s_p1_0},
            {null, null, ResBooksStereo44c1s._44c1_s_p2_0},
            {null, null, ResBooksStereo44c1s._44c1_s_p3_0},
            {null, null, ResBooksStereo44c1s._44c1_s_p4_0},
            {null, null, ResBooksStereo44c1s._44c1_s_p5_0},
            {ResBooksStereo44c1s._44c1_s_p6_0, ResBooksStereo44c1s._44c1_s_p6_1, null},
            {ResBooksStereo44c1s._44c1_s_p7_0, ResBooksStereo44c1s._44c1_s_p7_1, null},
            {ResBooksStereo44c1s._44c1_s_p8_0, ResBooksStereo44c1s._44c1_s_p8_1, ResBooksStereo44c1s._44c1_s_p8_2}

    };
    static final StaticCodeBook[][] _resbook_44sm_1 = {

            {null, null, null},
            {null, null, ResBooksStereo44c1sm._44c1_sm_p1_0},
            {null, null, ResBooksStereo44c1sm._44c1_sm_p2_0},
            {null, null, ResBooksStereo44c1sm._44c1_sm_p3_0},
            {null, null, ResBooksStereo44c1sm._44c1_sm_p4_0},
            {null, null, ResBooksStereo44c1sm._44c1_sm_p5_0},
            {ResBooksStereo44c1sm._44c1_sm_p6_0, ResBooksStereo44c1sm._44c1_sm_p6_1, null},
            {ResBooksStereo44c1sm._44c1_sm_p7_0, ResBooksStereo44c1sm._44c1_sm_p7_1, null},
            {ResBooksStereo44c1sm._44c1_sm_p8_0, ResBooksStereo44c1sm._44c1_sm_p8_1, ResBooksStereo44c1sm._44c1_sm_p8_2}

    };

    static final StaticCodeBook[][] _resbook_44s_2 = {

            {null, null, null},
            {null, null, ResBooksStereo44c2s._44c2_s_p1_0},
            {null, null, ResBooksStereo44c2s._44c2_s_p2_0},
            {null, null, ResBooksStereo44c2s._44c2_s_p3_0},
            {null, null, ResBooksStereo44c2s._44c2_s_p4_0},
            {null, null, ResBooksStereo44c2s._44c2_s_p5_0},
            {null, null, ResBooksStereo44c2s._44c2_s_p6_0},
            {ResBooksStereo44c2s._44c2_s_p7_0, ResBooksStereo44c2s._44c2_s_p7_1, null},
            {ResBooksStereo44c2s._44c2_s_p8_0, ResBooksStereo44c2s._44c2_s_p8_1, null},
            {ResBooksStereo44c2s._44c2_s_p9_0, ResBooksStereo44c2s._44c2_s_p9_1, ResBooksStereo44c2s._44c2_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44s_3 = {

            {null, null, null},
            {null, null, ResBooksStereo44c3s._44c3_s_p1_0},
            {null, null, ResBooksStereo44c3s._44c3_s_p2_0},
            {null, null, ResBooksStereo44c3s._44c3_s_p3_0},
            {null, null, ResBooksStereo44c3s._44c3_s_p4_0},
            {null, null, ResBooksStereo44c3s._44c3_s_p5_0},
            {null, null, ResBooksStereo44c3s._44c3_s_p6_0},
            {ResBooksStereo44c3s._44c3_s_p7_0, ResBooksStereo44c3s._44c3_s_p7_1, null},
            {ResBooksStereo44c3s._44c3_s_p8_0, ResBooksStereo44c3s._44c3_s_p8_1, null},
            {ResBooksStereo44c3s._44c3_s_p9_0, ResBooksStereo44c3s._44c3_s_p9_1, ResBooksStereo44c3s._44c3_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44s_4 = {

            {null, null, null},
            {null, null, ResBooksStereo44c4s._44c4_s_p1_0},
            {null, null, ResBooksStereo44c4s._44c4_s_p2_0},
            {null, null, ResBooksStereo44c4s._44c4_s_p3_0},
            {null, null, ResBooksStereo44c4s._44c4_s_p4_0},
            {null, null, ResBooksStereo44c4s._44c4_s_p5_0},
            {null, null, ResBooksStereo44c4s._44c4_s_p6_0},
            {ResBooksStereo44c4s._44c4_s_p7_0, ResBooksStereo44c4s._44c4_s_p7_1, null},
            {ResBooksStereo44c4s._44c4_s_p8_0, ResBooksStereo44c4s._44c4_s_p8_1, null},
            {ResBooksStereo44c4s._44c4_s_p9_0, ResBooksStereo44c4s._44c4_s_p9_1, ResBooksStereo44c4s._44c4_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44s_5 = {

            {null, null, null},
            {null, null, ResBooksStereo44c5s._44c5_s_p1_0},
            {null, null, ResBooksStereo44c5s._44c5_s_p2_0},
            {null, null, ResBooksStereo44c5s._44c5_s_p3_0},
            {null, null, ResBooksStereo44c5s._44c5_s_p4_0},
            {null, null, ResBooksStereo44c5s._44c5_s_p5_0},
            {null, null, ResBooksStereo44c5s._44c5_s_p6_0},
            {ResBooksStereo44c5s._44c5_s_p7_0, ResBooksStereo44c5s._44c5_s_p7_1, null},
            {ResBooksStereo44c5s._44c5_s_p8_0, ResBooksStereo44c5s._44c5_s_p8_1, null},
            {ResBooksStereo44c5s._44c5_s_p9_0, ResBooksStereo44c5s._44c5_s_p9_1, ResBooksStereo44c5s._44c5_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44s_6 = {

            {null, null, null},
            {null, null, ResBooksStereo44c6s._44c6_s_p1_0},
            {null, null, ResBooksStereo44c6s._44c6_s_p2_0},
            {null, null, ResBooksStereo44c6s._44c6_s_p3_0},
            {null, null, ResBooksStereo44c6s._44c6_s_p4_0},
            {ResBooksStereo44c6s._44c6_s_p5_0, ResBooksStereo44c6s._44c6_s_p5_1, null},
            {ResBooksStereo44c6s._44c6_s_p6_0, ResBooksStereo44c6s._44c6_s_p6_1, null},
            {ResBooksStereo44c6s._44c6_s_p7_0, ResBooksStereo44c6s._44c6_s_p7_1, null},
            {ResBooksStereo44c6s._44c6_s_p8_0, ResBooksStereo44c6s._44c6_s_p8_1, null},
            {ResBooksStereo44c6s._44c6_s_p9_0, ResBooksStereo44c6s._44c6_s_p9_1, ResBooksStereo44c6s._44c6_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44s_7 = {

            {null, null, null},
            {null, null, ResBooksStereo44c7s._44c7_s_p1_0},
            {null, null, ResBooksStereo44c7s._44c7_s_p2_0},
            {null, null, ResBooksStereo44c7s._44c7_s_p3_0},
            {null, null, ResBooksStereo44c7s._44c7_s_p4_0},
            {ResBooksStereo44c7s._44c7_s_p5_0, ResBooksStereo44c7s._44c7_s_p5_1, null},
            {ResBooksStereo44c7s._44c7_s_p6_0, ResBooksStereo44c7s._44c7_s_p6_1, null},
            {ResBooksStereo44c7s._44c7_s_p7_0, ResBooksStereo44c7s._44c7_s_p7_1, null},
            {ResBooksStereo44c7s._44c7_s_p8_0, ResBooksStereo44c7s._44c7_s_p8_1, null},
            {ResBooksStereo44c7s._44c7_s_p9_0, ResBooksStereo44c7s._44c7_s_p9_1, ResBooksStereo44c7s._44c7_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44s_8 = {

            {null, null, null},
            {null, null, ResBooksStereo44c8s._44c8_s_p1_0},
            {null, null, ResBooksStereo44c8s._44c8_s_p2_0},
            {null, null, ResBooksStereo44c8s._44c8_s_p3_0},
            {null, null, ResBooksStereo44c8s._44c8_s_p4_0},
            {ResBooksStereo44c8s._44c8_s_p5_0, ResBooksStereo44c8s._44c8_s_p5_1, null},
            {ResBooksStereo44c8s._44c8_s_p6_0, ResBooksStereo44c8s._44c8_s_p6_1, null},
            {ResBooksStereo44c8s._44c8_s_p7_0, ResBooksStereo44c8s._44c8_s_p7_1, null},
            {ResBooksStereo44c8s._44c8_s_p8_0, ResBooksStereo44c8s._44c8_s_p8_1, null},
            {ResBooksStereo44c8s._44c8_s_p9_0, ResBooksStereo44c8s._44c8_s_p9_1, ResBooksStereo44c8s._44c8_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44s_9 = {

            {null, null, null},
            {null, null, ResBooksStereo44c9s._44c9_s_p1_0},
            {null, null, ResBooksStereo44c9s._44c9_s_p2_0},
            {null, null, ResBooksStereo44c9s._44c9_s_p3_0},
            {null, null, ResBooksStereo44c9s._44c9_s_p4_0},
            {ResBooksStereo44c9s._44c9_s_p5_0, ResBooksStereo44c9s._44c9_s_p5_1, null},
            {ResBooksStereo44c9s._44c9_s_p6_0, ResBooksStereo44c9s._44c9_s_p6_1, null},
            {ResBooksStereo44c9s._44c9_s_p7_0, ResBooksStereo44c9s._44c9_s_p7_1, null},
            {ResBooksStereo44c9s._44c9_s_p8_0, ResBooksStereo44c9s._44c9_s_p8_1, null},
            {ResBooksStereo44c9s._44c9_s_p9_0, ResBooksStereo44c9s._44c9_s_p9_1, ResBooksStereo44c9s._44c9_s_p9_2}

    };

    static final VorbisResidueTemplate[] _res_44s_n1 = {
            new VorbisResidueTemplate(2, 0, _residue_44_low,
                    ResBooksStereo44cn1s._huff_book__44cn1_s_short, ResBooksStereo44cn1sm._huff_book__44cn1_sm_short,
                    _resbook_44s_n1, _resbook_44sm_n1),

            new VorbisResidueTemplate(2, 0, _residue_44_low,
                    ResBooksStereo44cn1s._huff_book__44cn1_s_long, ResBooksStereo44cn1sm._huff_book__44cn1_sm_long,
                    _resbook_44s_n1, _resbook_44sm_n1)
    };
    static final VorbisResidueTemplate[] _res_44s_0 = {
            new VorbisResidueTemplate(2, 0, _residue_44_low,
                    ResBooksStereo44c0s._huff_book__44c0_s_short, ResBooksStereo44c0sm._huff_book__44c0_sm_short,
                    _resbook_44s_0, _resbook_44sm_0),

            new VorbisResidueTemplate(2, 0, _residue_44_low,
                    ResBooksStereo44c0s._huff_book__44c0_s_long, ResBooksStereo44c0sm._huff_book__44c0_sm_long,
                    _resbook_44s_0, _resbook_44sm_0)
    };
    static final VorbisResidueTemplate[] _res_44s_1 = {
            new VorbisResidueTemplate(2, 0, _residue_44_low,
                    ResBooksStereo44c1s._huff_book__44c1_s_short, ResBooksStereo44c1sm._huff_book__44c1_sm_short,
                    _resbook_44s_1, _resbook_44sm_1),

            new VorbisResidueTemplate(2, 0, _residue_44_low,
                    ResBooksStereo44c1s._huff_book__44c1_s_long, ResBooksStereo44c1sm._huff_book__44c1_sm_long,
                    _resbook_44s_1, _resbook_44sm_1)
    };

    static final VorbisResidueTemplate[] _res_44s_2 = {
            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c2s._huff_book__44c2_s_short, ResBooksStereo44c2s._huff_book__44c2_s_short,
                    _resbook_44s_2, _resbook_44s_2),

            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c2s._huff_book__44c2_s_long, ResBooksStereo44c2s._huff_book__44c2_s_long,
                    _resbook_44s_2, _resbook_44s_2)
    };
    static final VorbisResidueTemplate[] _res_44s_3 = {
            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c3s._huff_book__44c3_s_short, ResBooksStereo44c3s._huff_book__44c3_s_short,
                    _resbook_44s_3, _resbook_44s_3),

            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c3s._huff_book__44c3_s_long, ResBooksStereo44c3s._huff_book__44c3_s_long,
                    _resbook_44s_3, _resbook_44s_3)
    };
    static final VorbisResidueTemplate[] _res_44s_4 = {
            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c4s._huff_book__44c4_s_short, ResBooksStereo44c4s._huff_book__44c4_s_short,
                    _resbook_44s_4, _resbook_44s_4),

            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c4s._huff_book__44c4_s_long, ResBooksStereo44c4s._huff_book__44c4_s_long,
                    _resbook_44s_4, _resbook_44s_4)
    };
    static final VorbisResidueTemplate[] _res_44s_5 = {
            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c5s._huff_book__44c5_s_short, ResBooksStereo44c5s._huff_book__44c5_s_short,
                    _resbook_44s_5, _resbook_44s_5),

            new VorbisResidueTemplate(2, 0, _residue_44_mid,
                    ResBooksStereo44c5s._huff_book__44c5_s_long, ResBooksStereo44c5s._huff_book__44c5_s_long,
                    _resbook_44s_5, _resbook_44s_5)
    };
    static final VorbisResidueTemplate[] _res_44s_6 = {
            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c6s._huff_book__44c6_s_short, ResBooksStereo44c6s._huff_book__44c6_s_short,
                    _resbook_44s_6, _resbook_44s_6),

            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c6s._huff_book__44c6_s_long, ResBooksStereo44c6s._huff_book__44c6_s_long,
                    _resbook_44s_6, _resbook_44s_6)
    };
    static final VorbisResidueTemplate[] _res_44s_7 = {
            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c7s._huff_book__44c7_s_short, ResBooksStereo44c7s._huff_book__44c7_s_short,
                    _resbook_44s_7, _resbook_44s_7),

            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c7s._huff_book__44c7_s_long, ResBooksStereo44c7s._huff_book__44c7_s_long,
                    _resbook_44s_7, _resbook_44s_7)
    };
    static final VorbisResidueTemplate[] _res_44s_8 = {
            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c8s._huff_book__44c8_s_short, ResBooksStereo44c8s._huff_book__44c8_s_short,
                    _resbook_44s_8, _resbook_44s_8),

            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c8s._huff_book__44c8_s_long, ResBooksStereo44c8s._huff_book__44c8_s_long,
                    _resbook_44s_8, _resbook_44s_8)
    };
    static final VorbisResidueTemplate[] _res_44s_9 = {
            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c9s._huff_book__44c9_s_short, ResBooksStereo44c9s._huff_book__44c9_s_short,
                    _resbook_44s_9, _resbook_44s_9),

            new VorbisResidueTemplate(2, 0, _residue_44_high,
                    ResBooksStereo44c9s._huff_book__44c9_s_long, ResBooksStereo44c9s._huff_book__44c9_s_long,
                    _resbook_44s_9, _resbook_44s_9)
    };

    static final VorbisMappingTemplate[] _mapres_template_44_stereo = {
            new VorbisMappingTemplate(_map_nominal, _res_44s_n1), /* -1 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_0), /* 0 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_1), /* 1 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_2), /* 2 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_3), /* 3 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_4), /* 4 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_5), /* 5 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_6), /* 6 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_7), /* 7 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_8), /* 8 */
            new VorbisMappingTemplate(_map_nominal, _res_44s_9), /* 9 */
    };

}