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
import biniu.vorbis.books.coupled.ResBooksStereo8c0s;
import biniu.vorbis.books.coupled.ResBooksStereo8c1s;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled8u0;
import biniu.vorbis.books.uncoupled.ResBooksUncoupled8u1;


/**
 * EncoderVorbis</p>
 * <p>Description: Codec for encoding music files (wav -> ogg) </p>
 * <p>
 * function: toplevel residue templates 8/11kHz
 *
 * @author Zbigniew Sudnik
 * @version 1.1.0j 2005-05-01 07:00:00
 */
public class Residue8 {

    // residue backends

    static final StaticCodeBook[][] _resbook_8s_0 = {
            {null, null, null},
            {null, null, ResBooksStereo8c0s._8c0_s_p1_0},
            {null, null, ResBooksStereo8c0s._8c0_s_p2_0},
            {null, null, ResBooksStereo8c0s._8c0_s_p3_0},
            {null, null, ResBooksStereo8c0s._8c0_s_p4_0},
            {null, null, ResBooksStereo8c0s._8c0_s_p5_0},
            {null, null, ResBooksStereo8c0s._8c0_s_p6_0},
            {ResBooksStereo8c0s._8c0_s_p7_0, ResBooksStereo8c0s._8c0_s_p7_1, null},
            {ResBooksStereo8c0s._8c0_s_p8_0, ResBooksStereo8c0s._8c0_s_p8_1, null},
            {ResBooksStereo8c0s._8c0_s_p9_0, ResBooksStereo8c0s._8c0_s_p9_1, ResBooksStereo8c0s._8c0_s_p9_2}
    };
    static final StaticCodeBook[][] _resbook_8s_1 = {
            {null, null, null},
            {null, null, ResBooksStereo8c1s._8c1_s_p1_0},
            {null, null, ResBooksStereo8c1s._8c1_s_p2_0},
            {null, null, ResBooksStereo8c1s._8c1_s_p3_0},
            {null, null, ResBooksStereo8c1s._8c1_s_p4_0},
            {null, null, ResBooksStereo8c1s._8c1_s_p5_0},
            {null, null, ResBooksStereo8c1s._8c1_s_p6_0},
            {ResBooksStereo8c1s._8c1_s_p7_0, ResBooksStereo8c1s._8c1_s_p7_1, null},
            {ResBooksStereo8c1s._8c1_s_p8_0, ResBooksStereo8c1s._8c1_s_p8_1, null},
            {ResBooksStereo8c1s._8c1_s_p9_0, ResBooksStereo8c1s._8c1_s_p9_1, ResBooksStereo8c1s._8c1_s_p9_2}
    };

    static final VorbisResidueTemplate[] _res_8s_0 = {
            new VorbisResidueTemplate(2, 0, Residue44._residue_44_mid,
                    ResBooksStereo8c0s._huff_book__8c0_s_single, ResBooksStereo8c0s._huff_book__8c0_s_single,
                    _resbook_8s_0, _resbook_8s_0),
    };
    static final VorbisResidueTemplate[] _res_8s_1 = {
            new VorbisResidueTemplate(2, 0, Residue44._residue_44_mid,
                    ResBooksStereo8c1s._huff_book__8c1_s_single, ResBooksStereo8c1s._huff_book__8c1_s_single,
                    _resbook_8s_1, _resbook_8s_1),
    };

    static final VorbisMappingTemplate[] _mapres_template_8_stereo = {
            new VorbisMappingTemplate(Residue44._map_nominal, _res_8s_0), /* 0 */
            new VorbisMappingTemplate(Residue44._map_nominal, _res_8s_1), /* 1 */
    };

    static final StaticCodeBook[][] _resbook_8u_0 = {
            {null, null, null},
            {null, null, ResBooksUncoupled8u0._8u0__p1_0},
            {null, null, ResBooksUncoupled8u0._8u0__p2_0},
            {null, null, ResBooksUncoupled8u0._8u0__p3_0},
            {null, null, ResBooksUncoupled8u0._8u0__p4_0},
            {null, null, ResBooksUncoupled8u0._8u0__p5_0},
            {ResBooksUncoupled8u0._8u0__p6_0, ResBooksUncoupled8u0._8u0__p6_1, null},
            {ResBooksUncoupled8u0._8u0__p7_0, ResBooksUncoupled8u0._8u0__p7_1, ResBooksUncoupled8u0._8u0__p7_2}
    };
    static final StaticCodeBook[][] _resbook_8u_1 = {
            {null, null, null},
            {null, null, ResBooksUncoupled8u1._8u1__p1_0},
            {null, null, ResBooksUncoupled8u1._8u1__p2_0},
            {null, null, ResBooksUncoupled8u1._8u1__p3_0},
            {null, null, ResBooksUncoupled8u1._8u1__p4_0},
            {null, null, ResBooksUncoupled8u1._8u1__p5_0},
            {null, null, ResBooksUncoupled8u1._8u1__p6_0},
            {ResBooksUncoupled8u1._8u1__p7_0, ResBooksUncoupled8u1._8u1__p7_1, null},
            {ResBooksUncoupled8u1._8u1__p8_0, ResBooksUncoupled8u1._8u1__p8_1, null},
            {ResBooksUncoupled8u1._8u1__p9_0, ResBooksUncoupled8u1._8u1__p9_1, ResBooksUncoupled8u1._8u1__p9_2}
    };

    static final VorbisResidueTemplate[] _res_8u_0 = {
            new VorbisResidueTemplate(1, 0, Residu44u._residue_44_low_un,
                    ResBooksUncoupled8u0._huff_book__8u0__single, ResBooksUncoupled8u0._huff_book__8u0__single,
                    _resbook_8u_0, _resbook_8u_0),
    };
    static final VorbisResidueTemplate[] _res_8u_1 = {
            new VorbisResidueTemplate(1, 0, Residu44u._residue_44_mid_un,
                    ResBooksUncoupled8u1._huff_book__8u1__single, ResBooksUncoupled8u1._huff_book__8u1__single,
                    _resbook_8u_1, _resbook_8u_1),
    };

    static final VorbisMappingTemplate[] _mapres_template_8_uncoupled = {
            new VorbisMappingTemplate(Residu44u._map_nominal_u, _res_8u_0), /* 0 */
            new VorbisMappingTemplate(Residu44u._map_nominal_u, _res_8u_1), /* 1 */
    };

}