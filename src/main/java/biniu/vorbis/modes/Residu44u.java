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
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u0;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u1;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u2;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u3;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u4;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u5;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u6;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u7;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u8;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44u9;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled44un1;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Residu44u {

    // residue backends

    static final InfoResidue0 _residue_44_low_un = new InfoResidue0(
            0, -1, -1, 8, -1,
            Util.intTab(0),
            Util.intTab(-1),
            Util.intTab(.5f, 1.5f, 1.5f, 2.5f, 2.5f, 4.5f, 28.5f),
            Util.intTab(-1, 25, -1, 45, -1, -1, -1.f)
    );

    static final InfoResidue0 _residue_44_mid_un = new InfoResidue0(
            0, -1, -1, 10, -1,
            /*  0     1     2     3     4     5     6     7     8     9 */
            Util.intTab(0),
            Util.intTab(-1),
            Util.intTab(.5f, 1.5f, 1.5f, 2.5f, 2.5f, 4.5f, 4.5f, 16.5f, 60.5f),
            Util.intTab(-1, 30, -1, 50, -1, 80, -1, -1, -1.f)
    );

    static final InfoResidue0 _residue_44_hi_un = new InfoResidue0(
            0, -1, -1, 10, -1,
            /*  0     1     2     3     4     5     6     7     8     9 */
            Util.intTab(0),
            Util.intTab(-1),
            Util.intTab(.5f, 1.5f, 2.5f, 4.5f, 8.5f, 16.5f, 32.5f, 71.5f, 157.5f),
            Util.intTab(-1.f, -1, -1, -1, -1, -1, -1, -1, -1)
    );

    /* mapping conventions:
       only one submap (this would change for efficient 5.1 support for example)*/
    /* Four psychoacoustic profiles are used, one for each blocktype */
    static final InfoMapping0[] _map_nominal_u = {
            new InfoMapping0(1, Util.intTab(0, 0), Util.intTab(0), Util.intTab(0), 0, Util.intTab(0), Util.intTab(0)),
            new InfoMapping0(1, Util.intTab(0, 0), Util.intTab(1), Util.intTab(1), 0, Util.intTab(0), Util.intTab(0))
    };

    static final StaticCodeBook[][] _resbook_44u_n1 = {
            {null, null, null},
            {null, null, ResBooksUncoupled44un1._44un1__p1_0},
            {null, null, ResBooksUncoupled44un1._44un1__p2_0},
            {null, null, ResBooksUncoupled44un1._44un1__p3_0},
            {null, null, ResBooksUncoupled44un1._44un1__p4_0},
            {null, null, ResBooksUncoupled44un1._44un1__p5_0},
            {ResBooksUncoupled44un1._44un1__p6_0, ResBooksUncoupled44un1._44un1__p6_1, null},
            {ResBooksUncoupled44un1._44un1__p7_0, ResBooksUncoupled44un1._44un1__p7_1, ResBooksUncoupled44un1._44un1__p7_2}
    };
    static final StaticCodeBook[][] _resbook_44u_0 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u0._44u0__p1_0},
            {null, null, ResBooksUncoupled44u0._44u0__p2_0},
            {null, null, ResBooksUncoupled44u0._44u0__p3_0},
            {null, null, ResBooksUncoupled44u0._44u0__p4_0},
            {null, null, ResBooksUncoupled44u0._44u0__p5_0},
            {ResBooksUncoupled44u0._44u0__p6_0, ResBooksUncoupled44u0._44u0__p6_1, null},
            {ResBooksUncoupled44u0._44u0__p7_0, ResBooksUncoupled44u0._44u0__p7_1, ResBooksUncoupled44u0._44u0__p7_2}

    };
    static final StaticCodeBook[][] _resbook_44u_1 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u1._44u1__p1_0},
            {null, null, ResBooksUncoupled44u1._44u1__p2_0},
            {null, null, ResBooksUncoupled44u1._44u1__p3_0},
            {null, null, ResBooksUncoupled44u1._44u1__p4_0},
            {null, null, ResBooksUncoupled44u1._44u1__p5_0},
            {ResBooksUncoupled44u1._44u1__p6_0, ResBooksUncoupled44u1._44u1__p6_1, null},
            {ResBooksUncoupled44u1._44u1__p7_0, ResBooksUncoupled44u1._44u1__p7_1, ResBooksUncoupled44u1._44u1__p7_2}

    };
    static final StaticCodeBook[][] _resbook_44u_2 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u2._44u2__p1_0},
            {null, null, ResBooksUncoupled44u2._44u2__p2_0},
            {null, null, ResBooksUncoupled44u2._44u2__p3_0},
            {null, null, ResBooksUncoupled44u2._44u2__p4_0},
            {null, null, ResBooksUncoupled44u2._44u2__p5_0},
            {ResBooksUncoupled44u2._44u2__p6_0, ResBooksUncoupled44u2._44u2__p6_1, null},
            {ResBooksUncoupled44u2._44u2__p7_0, ResBooksUncoupled44u2._44u2__p7_1, ResBooksUncoupled44u2._44u2__p7_2}

    };
    static final StaticCodeBook[][] _resbook_44u_3 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u3._44u3__p1_0},
            {null, null, ResBooksUncoupled44u3._44u3__p2_0},
            {null, null, ResBooksUncoupled44u3._44u3__p3_0},
            {null, null, ResBooksUncoupled44u3._44u3__p4_0},
            {null, null, ResBooksUncoupled44u3._44u3__p5_0},
            {ResBooksUncoupled44u3._44u3__p6_0, ResBooksUncoupled44u3._44u3__p6_1, null},
            {ResBooksUncoupled44u3._44u3__p7_0, ResBooksUncoupled44u3._44u3__p7_1, ResBooksUncoupled44u3._44u3__p7_2}

    };
    static final StaticCodeBook[][] _resbook_44u_4 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u4._44u4__p1_0},
            {null, null, ResBooksUncoupled44u4._44u4__p2_0},
            {null, null, ResBooksUncoupled44u4._44u4__p3_0},
            {null, null, ResBooksUncoupled44u4._44u4__p4_0},
            {null, null, ResBooksUncoupled44u4._44u4__p5_0},
            {ResBooksUncoupled44u4._44u4__p6_0, ResBooksUncoupled44u4._44u4__p6_1, null},
            {ResBooksUncoupled44u4._44u4__p7_0, ResBooksUncoupled44u4._44u4__p7_1, ResBooksUncoupled44u4._44u4__p7_2}

    };
    static final StaticCodeBook[][] _resbook_44u_5 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u5._44u5__p1_0},
            {null, null, ResBooksUncoupled44u5._44u5__p2_0},
            {null, null, ResBooksUncoupled44u5._44u5__p3_0},
            {null, null, ResBooksUncoupled44u5._44u5__p4_0},
            {null, null, ResBooksUncoupled44u5._44u5__p5_0},
            {null, null, ResBooksUncoupled44u5._44u5__p6_0},
            {ResBooksUncoupled44u5._44u5__p7_0, ResBooksUncoupled44u5._44u5__p7_1, null},
            {ResBooksUncoupled44u5._44u5__p8_0, ResBooksUncoupled44u5._44u5__p8_1, null},
            {ResBooksUncoupled44u5._44u5__p9_0, ResBooksUncoupled44u5._44u5__p9_1, ResBooksUncoupled44u5._44u5__p9_2}

    };
    static final StaticCodeBook[][] _resbook_44u_6 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u6._44u6__p1_0},
            {null, null, ResBooksUncoupled44u6._44u6__p2_0},
            {null, null, ResBooksUncoupled44u6._44u6__p3_0},
            {null, null, ResBooksUncoupled44u6._44u6__p4_0},
            {null, null, ResBooksUncoupled44u6._44u6__p5_0},
            {null, null, ResBooksUncoupled44u6._44u6__p6_0},
            {ResBooksUncoupled44u6._44u6__p7_0, ResBooksUncoupled44u6._44u6__p7_1, null},
            {ResBooksUncoupled44u6._44u6__p8_0, ResBooksUncoupled44u6._44u6__p8_1, null},
            {ResBooksUncoupled44u6._44u6__p9_0, ResBooksUncoupled44u6._44u6__p9_1, ResBooksUncoupled44u6._44u6__p9_2}

    };
    static final StaticCodeBook[][] _resbook_44u_7 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u7._44u7__p1_0},
            {null, null, ResBooksUncoupled44u7._44u7__p2_0},
            {null, null, ResBooksUncoupled44u7._44u7__p3_0},
            {null, null, ResBooksUncoupled44u7._44u7__p4_0},
            {null, null, ResBooksUncoupled44u7._44u7__p5_0},
            {null, null, ResBooksUncoupled44u7._44u7__p6_0},
            {ResBooksUncoupled44u7._44u7__p7_0, ResBooksUncoupled44u7._44u7__p7_1, null},
            {ResBooksUncoupled44u7._44u7__p8_0, ResBooksUncoupled44u7._44u7__p8_1, null},
            {ResBooksUncoupled44u7._44u7__p9_0, ResBooksUncoupled44u7._44u7__p9_1, ResBooksUncoupled44u7._44u7__p9_2}

    };
    static final StaticCodeBook[][] _resbook_44u_8 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u8._44u8_p1_0},
            {null, null, ResBooksUncoupled44u8._44u8_p2_0},
            {null, null, ResBooksUncoupled44u8._44u8_p3_0},
            {null, null, ResBooksUncoupled44u8._44u8_p4_0},
            {ResBooksUncoupled44u8._44u8_p5_0, ResBooksUncoupled44u8._44u8_p5_1, null},
            {ResBooksUncoupled44u8._44u8_p6_0, ResBooksUncoupled44u8._44u8_p6_1, null},
            {ResBooksUncoupled44u8._44u8_p7_0, ResBooksUncoupled44u8._44u8_p7_1, null},
            {ResBooksUncoupled44u8._44u8_p8_0, ResBooksUncoupled44u8._44u8_p8_1, null},
            {ResBooksUncoupled44u8._44u8_p9_0, ResBooksUncoupled44u8._44u8_p9_1, ResBooksUncoupled44u8._44u8_p9_2}

    };
    static final StaticCodeBook[][] _resbook_44u_9 = {

            {null, null, null},
            {null, null, ResBooksUncoupled44u9._44u9_p1_0},
            {null, null, ResBooksUncoupled44u9._44u9_p2_0},
            {null, null, ResBooksUncoupled44u9._44u9_p3_0},
            {null, null, ResBooksUncoupled44u9._44u9_p4_0},
            {ResBooksUncoupled44u9._44u9_p5_0, ResBooksUncoupled44u9._44u9_p5_1, null},
            {ResBooksUncoupled44u9._44u9_p6_0, ResBooksUncoupled44u9._44u9_p6_1, null},
            {ResBooksUncoupled44u9._44u9_p7_0, ResBooksUncoupled44u9._44u9_p7_1, null},
            {ResBooksUncoupled44u9._44u9_p8_0, ResBooksUncoupled44u9._44u9_p8_1, null},
            {ResBooksUncoupled44u9._44u9_p9_0, ResBooksUncoupled44u9._44u9_p9_1, ResBooksUncoupled44u9._44u9_p9_2}

    };

    static final VorbisResidueTemplate[] _res_44u_n1 = {
            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44un1._huff_book__44un1__short, ResBooksUncoupled44un1._huff_book__44un1__short,
                    _resbook_44u_n1, _resbook_44u_n1),

            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44un1._huff_book__44un1__long, ResBooksUncoupled44un1._huff_book__44un1__long,
                    _resbook_44u_n1, _resbook_44u_n1)
    };
    static final VorbisResidueTemplate[] _res_44u_0 = {
            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u0._huff_book__44u0__short, ResBooksUncoupled44u0._huff_book__44u0__short,
                    _resbook_44u_0, _resbook_44u_0),

            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u0._huff_book__44u0__long, ResBooksUncoupled44u0._huff_book__44u0__long,
                    _resbook_44u_0, _resbook_44u_0)
    };
    static final VorbisResidueTemplate[] _res_44u_1 = {
            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u1._huff_book__44u1__short, ResBooksUncoupled44u1._huff_book__44u1__short,
                    _resbook_44u_1, _resbook_44u_1),

            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u1._huff_book__44u1__long, ResBooksUncoupled44u1._huff_book__44u1__long,
                    _resbook_44u_1, _resbook_44u_1)
    };
    static final VorbisResidueTemplate[] _res_44u_2 = {
            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u2._huff_book__44u2__short, ResBooksUncoupled44u2._huff_book__44u2__short,
                    _resbook_44u_2, _resbook_44u_2),

            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u2._huff_book__44u2__long, ResBooksUncoupled44u2._huff_book__44u2__long,
                    _resbook_44u_2, _resbook_44u_2)
    };
    static final VorbisResidueTemplate[] _res_44u_3 = {
            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u3._huff_book__44u3__short, ResBooksUncoupled44u3._huff_book__44u3__short,
                    _resbook_44u_3, _resbook_44u_3),

            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u3._huff_book__44u3__long, ResBooksUncoupled44u3._huff_book__44u3__long,
                    _resbook_44u_3, _resbook_44u_3)
    };
    static final VorbisResidueTemplate[] _res_44u_4 = {
            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u4._huff_book__44u4__short, ResBooksUncoupled44u4._huff_book__44u4__short,
                    _resbook_44u_4, _resbook_44u_4),

            new VorbisResidueTemplate(1, 0, _residue_44_low_un,
                    ResBooksUncoupled44u4._huff_book__44u4__long, ResBooksUncoupled44u4._huff_book__44u4__long,
                    _resbook_44u_4, _resbook_44u_4)
    };

    static final VorbisResidueTemplate[] _res_44u_5 = {
            new VorbisResidueTemplate(1, 0, _residue_44_mid_un,
                    ResBooksUncoupled44u5._huff_book__44u5__short, ResBooksUncoupled44u5._huff_book__44u5__short,
                    _resbook_44u_5, _resbook_44u_5),

            new VorbisResidueTemplate(1, 0, _residue_44_mid_un,
                    ResBooksUncoupled44u5._huff_book__44u5__long, ResBooksUncoupled44u5._huff_book__44u5__long,
                    _resbook_44u_5, _resbook_44u_5)
    };

    static final VorbisResidueTemplate[] _res_44u_6 = {
            new VorbisResidueTemplate(1, 0, _residue_44_mid_un,
                    ResBooksUncoupled44u6._huff_book__44u6__short, ResBooksUncoupled44u6._huff_book__44u6__short,
                    _resbook_44u_6, _resbook_44u_6),

            new VorbisResidueTemplate(1, 0, _residue_44_mid_un,
                    ResBooksUncoupled44u6._huff_book__44u6__long, ResBooksUncoupled44u6._huff_book__44u6__long,
                    _resbook_44u_6, _resbook_44u_6)
    };

    static final VorbisResidueTemplate[] _res_44u_7 = {
            new VorbisResidueTemplate(1, 0, _residue_44_mid_un,
                    ResBooksUncoupled44u7._huff_book__44u7__short, ResBooksUncoupled44u7._huff_book__44u7__short,
                    _resbook_44u_7, _resbook_44u_7),

            new VorbisResidueTemplate(1, 0, _residue_44_mid_un,
                    ResBooksUncoupled44u7._huff_book__44u7__long, ResBooksUncoupled44u7._huff_book__44u7__long,
                    _resbook_44u_7, _resbook_44u_7)
    };

    static final VorbisResidueTemplate[] _res_44u_8 = {
            new VorbisResidueTemplate(1, 0, _residue_44_hi_un,
                    ResBooksUncoupled44u8._huff_book__44u8__short, ResBooksUncoupled44u8._huff_book__44u8__short,
                    _resbook_44u_8, _resbook_44u_8),

            new VorbisResidueTemplate(1, 0, _residue_44_hi_un,
                    ResBooksUncoupled44u8._huff_book__44u8__long, ResBooksUncoupled44u8._huff_book__44u8__long,
                    _resbook_44u_8, _resbook_44u_8)
    };
    static final VorbisResidueTemplate[] _res_44u_9 = {
            new VorbisResidueTemplate(1, 0, _residue_44_hi_un,
                    ResBooksUncoupled44u9._huff_book__44u9__short, ResBooksUncoupled44u9._huff_book__44u9__short,
                    _resbook_44u_9, _resbook_44u_9),

            new VorbisResidueTemplate(1, 0, _residue_44_hi_un,
                    ResBooksUncoupled44u9._huff_book__44u9__long, ResBooksUncoupled44u9._huff_book__44u9__long,
                    _resbook_44u_9, _resbook_44u_9)
    };

    static final VorbisMappingTemplate[] _mapres_template_44_uncoupled = {
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_n1), /* -1 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_0), /* 0 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_1), /* 1 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_2), /* 2 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_3), /* 3 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_4), /* 4 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_5), /* 5 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_6), /* 6 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_7), /* 7 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_8), /* 8 */
            new VorbisMappingTemplate(_map_nominal_u, _res_44u_9), /* 9 */
    };

}