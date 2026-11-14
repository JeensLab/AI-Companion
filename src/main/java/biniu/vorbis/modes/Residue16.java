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

import biniu.vorbis.StaticCodeBook;
import biniu.vorbis.VorbisMappingTemplate;
import biniu.vorbis.VorbisResidueTemplate;
import biniu.vorbis.books.coupled.ResBooksStereo16c0s;
import biniu.vorbis.books.coupled.ResBooksStereo16c1s;
import biniu.vorbis.books.coupled.ResBooksStereo16c2s;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled16u0;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled16u1;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled16u2;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 * <p>
 * toplevel residue templates 16/22kHz
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Residue16 {

    // residue backends

    static final StaticCodeBook[][] _resbook_16s_0 = {

            {null, null, null},
            {null, null, ResBooksStereo16c0s._16c0_s_p1_0},
            {null, null, ResBooksStereo16c0s._16c0_s_p2_0},
            {null, null, ResBooksStereo16c0s._16c0_s_p3_0},
            {null, null, ResBooksStereo16c0s._16c0_s_p4_0},
            {null, null, ResBooksStereo16c0s._16c0_s_p5_0},
            {null, null, ResBooksStereo16c0s._16c0_s_p6_0},
            {ResBooksStereo16c0s._16c0_s_p7_0, ResBooksStereo16c0s._16c0_s_p7_1, null},
            {ResBooksStereo16c0s._16c0_s_p8_0, ResBooksStereo16c0s._16c0_s_p8_1, null},
            {ResBooksStereo16c0s._16c0_s_p9_0, ResBooksStereo16c0s._16c0_s_p9_1, ResBooksStereo16c0s._16c0_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_16s_1 = {

            {null, null, null},
            {null, null, ResBooksStereo16c1s._16c1_s_p1_0},
            {null, null, ResBooksStereo16c1s._16c1_s_p2_0},
            {null, null, ResBooksStereo16c1s._16c1_s_p3_0},
            {null, null, ResBooksStereo16c1s._16c1_s_p4_0},
            {null, null, ResBooksStereo16c1s._16c1_s_p5_0},
            {null, null, ResBooksStereo16c1s._16c1_s_p6_0},
            {ResBooksStereo16c1s._16c1_s_p7_0, ResBooksStereo16c1s._16c1_s_p7_1, null},
            {ResBooksStereo16c1s._16c1_s_p8_0, ResBooksStereo16c1s._16c1_s_p8_1, null},
            {ResBooksStereo16c1s._16c1_s_p9_0, ResBooksStereo16c1s._16c1_s_p9_1, ResBooksStereo16c1s._16c1_s_p9_2}

    };
    static final StaticCodeBook[][] _resbook_16s_2 = {

            {null, null, null},
            {null, null, ResBooksStereo16c2s._16c2_s_p1_0},
            {null, null, ResBooksStereo16c2s._16c2_s_p2_0},
            {null, null, ResBooksStereo16c2s._16c2_s_p3_0},
            {null, null, ResBooksStereo16c2s._16c2_s_p4_0},
            {ResBooksStereo16c2s._16c2_s_p5_0, ResBooksStereo16c2s._16c2_s_p5_1, null},
            {ResBooksStereo16c2s._16c2_s_p6_0, ResBooksStereo16c2s._16c2_s_p6_1, null},
            {ResBooksStereo16c2s._16c2_s_p7_0, ResBooksStereo16c2s._16c2_s_p7_1, null},
            {ResBooksStereo16c2s._16c2_s_p8_0, ResBooksStereo16c2s._16c2_s_p8_1, null},
            {ResBooksStereo16c2s._16c2_s_p9_0, ResBooksStereo16c2s._16c2_s_p9_1, ResBooksStereo16c2s._16c2_s_p9_2}

    };

    static final VorbisResidueTemplate[] _res_16s_0 = {
            new VorbisResidueTemplate(2, 0, Residue44._residue_44_mid,
                    ResBooksStereo16c0s._huff_book__16c0_s_single, ResBooksStereo16c0s._huff_book__16c0_s_single,
                    _resbook_16s_0, _resbook_16s_0)
    };
    static final VorbisResidueTemplate[] _res_16s_1 = {
            new VorbisResidueTemplate(2, 0, Residue44._residue_44_mid,
                    ResBooksStereo16c1s._huff_book__16c1_s_short, ResBooksStereo16c1s._huff_book__16c1_s_short,
                    _resbook_16s_1, _resbook_16s_1),

            new VorbisResidueTemplate(2, 0, Residue44._residue_44_mid,
                    ResBooksStereo16c1s._huff_book__16c1_s_long, ResBooksStereo16c1s._huff_book__16c1_s_long,
                    _resbook_16s_1, _resbook_16s_1)
    };
    static final VorbisResidueTemplate[] _res_16s_2 = {
            new VorbisResidueTemplate(2, 0, Residue44._residue_44_high,
                    ResBooksStereo16c2s._huff_book__16c2_s_short, ResBooksStereo16c2s._huff_book__16c2_s_short,
                    _resbook_16s_2, _resbook_16s_2),

            new VorbisResidueTemplate(2, 0, Residue44._residue_44_high,
                    ResBooksStereo16c2s._huff_book__16c2_s_long, ResBooksStereo16c2s._huff_book__16c2_s_long,
                    _resbook_16s_2, _resbook_16s_2)
    };

    static final VorbisMappingTemplate[] _mapres_template_16_stereo = {
            new VorbisMappingTemplate(Residue44._map_nominal, _res_16s_0), /* 0 */
            new VorbisMappingTemplate(Residue44._map_nominal, _res_16s_1), /* 1 */
            new VorbisMappingTemplate(Residue44._map_nominal, _res_16s_2), /* 2 */
    };

    static final StaticCodeBook[][] _resbook_16u_0 = {

            {null, null, null},
            {null, null, ResBooksUncoupled16u0._16u0__p1_0},
            {null, null, ResBooksUncoupled16u0._16u0__p2_0},
            {null, null, ResBooksUncoupled16u0._16u0__p3_0},
            {null, null, ResBooksUncoupled16u0._16u0__p4_0},
            {null, null, ResBooksUncoupled16u0._16u0__p5_0},
            {ResBooksUncoupled16u0._16u0__p6_0, ResBooksUncoupled16u0._16u0__p6_1},
            {ResBooksUncoupled16u0._16u0__p7_0, ResBooksUncoupled16u0._16u0__p7_1, ResBooksUncoupled16u0._16u0__p7_2}

    };
    static final StaticCodeBook[][] _resbook_16u_1 = {

            {null, null, null},
            {null, null, ResBooksUncoupled16u1._16u1__p1_0},
            {null, null, ResBooksUncoupled16u1._16u1__p2_0},
            {null, null, ResBooksUncoupled16u1._16u1__p3_0},
            {null, null, ResBooksUncoupled16u1._16u1__p4_0},
            {null, null, ResBooksUncoupled16u1._16u1__p5_0},
            {null, null, ResBooksUncoupled16u1._16u1__p6_0},
            {ResBooksUncoupled16u1._16u1__p7_0, ResBooksUncoupled16u1._16u1__p7_1},
            {ResBooksUncoupled16u1._16u1__p8_0, ResBooksUncoupled16u1._16u1__p8_1},
            {ResBooksUncoupled16u1._16u1__p9_0, ResBooksUncoupled16u1._16u1__p9_1, ResBooksUncoupled16u1._16u1__p9_2}

    };
    static final StaticCodeBook[][] _resbook_16u_2 = {

            {null, null, null},
            {null, null, ResBooksUncoupled16u2._16u2_p1_0},
            {null, null, ResBooksUncoupled16u2._16u2_p2_0},
            {null, null, ResBooksUncoupled16u2._16u2_p3_0},
            {null, null, ResBooksUncoupled16u2._16u2_p4_0},
            {ResBooksUncoupled16u2._16u2_p5_0, ResBooksUncoupled16u2._16u2_p5_1},
            {ResBooksUncoupled16u2._16u2_p6_0, ResBooksUncoupled16u2._16u2_p6_1},
            {ResBooksUncoupled16u2._16u2_p7_0, ResBooksUncoupled16u2._16u2_p7_1},
            {ResBooksUncoupled16u2._16u2_p8_0, ResBooksUncoupled16u2._16u2_p8_1},
            {ResBooksUncoupled16u2._16u2_p9_0, ResBooksUncoupled16u2._16u2_p9_1, ResBooksUncoupled16u2._16u2_p9_2}

    };

    static final VorbisResidueTemplate[] _res_16u_0 = {
            new VorbisResidueTemplate(1, 0, Residu44u._residue_44_low_un,
                    ResBooksUncoupled16u0._huff_book__16u0__single, ResBooksUncoupled16u0._huff_book__16u0__single,
                    _resbook_16u_0, _resbook_16u_0),
    };
    static final VorbisResidueTemplate[] _res_16u_1 = {
            new VorbisResidueTemplate(1, 0, Residu44u._residue_44_mid_un,
                    ResBooksUncoupled16u1._huff_book__16u1__short, ResBooksUncoupled16u1._huff_book__16u1__short,
                    _resbook_16u_1, _resbook_16u_1),

            new VorbisResidueTemplate(1, 0, Residu44u._residue_44_mid_un,
                    ResBooksUncoupled16u1._huff_book__16u1__long, ResBooksUncoupled16u1._huff_book__16u1__long,
                    _resbook_16u_1, _resbook_16u_1)
    };
    static final VorbisResidueTemplate[] _res_16u_2 = {
            new VorbisResidueTemplate(1, 0, Residu44u._residue_44_hi_un,
                    ResBooksUncoupled16u2._huff_book__16u2__short, ResBooksUncoupled16u2._huff_book__16u2__short,
                    _resbook_16u_2, _resbook_16u_2),

            new VorbisResidueTemplate(1, 0, Residu44u._residue_44_hi_un,
                    ResBooksUncoupled16u2._huff_book__16u2__long, ResBooksUncoupled16u2._huff_book__16u2__long,
                    _resbook_16u_2, _resbook_16u_2)
    };

    static final VorbisMappingTemplate[] _mapres_template_16_uncoupled = {
            new VorbisMappingTemplate(Residu44u._map_nominal_u, _res_16u_0), /* 0 */
            new VorbisMappingTemplate(Residu44u._map_nominal_u, _res_16u_1), /* 1 */
            new VorbisMappingTemplate(Residu44u._map_nominal_u, _res_16u_2), /* 2 */
    };

}