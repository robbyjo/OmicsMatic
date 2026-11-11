// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Complete synthetic BCF fields and GT widths, independent of the Java reader.
#include <htslib/vcf.h>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

static float bits(uint32_t value) { float result; std::memcpy(&result, &value, sizeof(result)); return result; }
int main(int argc, char** argv) {
    if ((argc != 2 && argc != 3) || std::ifstream(argv[1]).good()) return 2;
    htsFile* file = bcf_open(argv[1], "wb"); bcf_hdr_t* header = bcf_hdr_init("w");
    if (!file || !header) return 3;
    if (bcf_hdr_set_version(header, argc == 3 ? argv[2] : "VCFv4.2") < 0) return 3;
    for (const char* line : {"##contig=<ID=chr20_α,length=1000000>",
            "##FORMAT=<ID=GT,Number=1,Type=String,Description=Genotype>",
            "##FORMAT=<ID=DP,Number=1,Type=Integer,Description=Depth>",
            "##FORMAT=<ID=FP,Number=.,Type=Float,Description=Raw_Float32>",
            "##FORMAT=<ID=TXT,Number=1,Type=String,Description=Text>",
            "##INFO=<ID=AC,Number=A,Type=Integer,Description=Allele_count>",
            "##INFO=<ID=AN,Number=1,Type=Integer,Description=Observed_alleles>",
            "##INFO=<ID=END,Number=1,Type=Integer,Description=Span_end>",
            "##INFO=<ID=VI,Number=.,Type=Integer,Description=Padded_integer>",
            "##INFO=<ID=VP,Number=.,Type=Float,Description=Padded_Float32>",
            "##INFO=<ID=VF,Number=1,Type=Float,Description=Raw_Float32>"}) if (bcf_hdr_append(header, line) < 0) return 4;
    for (const char* sample : {u8"样本_α", "second", "third", "fourth"}) if (bcf_hdr_add_sample(header, sample) < 0) return 5;
    if (bcf_hdr_add_sample(header, nullptr) < 0 || bcf_hdr_write(file, header) < 0) return 6;
    for (int variant = 0; variant < 4; variant++) {
        bcf1_t* record = bcf_init(); record->rid = 0; record->pos = 999 + 1000 * variant;
        record->qual = bits(variant == 0 ? 0x7fc01234 : variant == 1 ? 0x7f800000 : variant == 2 ? 0xff800000 : 0x7f800001);
        int largest = variant == 1 ? 128 : variant == 2 ? 17000 : 1;
        std::string alleles = "A";
        for (int i = 1; i <= largest; i++) alleles += i == 1 ? ",C" : ",<ALT" + std::to_string(i) + ">";
        std::string id = u8"变异_" + std::to_string(variant);
        if (bcf_update_alleles_str(header, record, alleles.c_str()) < 0 || bcf_update_id(header, record, id.c_str()) < 0) return 7;
        int32_t depth[] = {10, bcf_int32_missing, 300, -7};
        float floats[] = {bits(0), bits(0x7fc01234), bits(0x7f800002), 1.0f, bits(0x7f800001), bits(0x7f800002),
                          bits(0x80000000), bits(0x7f800000), bits(0xff800000), 0.125f, 0.75f, 1.0f};
        const char* strings[] = {"x", u8"αα", "abc", ""};
        std::vector<int32_t> calls = variant == 0 ? std::vector<int32_t>{3,1,0,0,4,3,5,bcf_int32_vector_end}
            : variant == 3 ? std::vector<int32_t>{2,5,5,4,2,bcf_int32_vector_end,bcf_int32_missing,bcf_int32_missing,bcf_int32_vector_end,1,4,bcf_int32_vector_end}
            : std::vector<int32_t>{bcf_gt_unphased(largest),2,2,0,0,2,2,2};
        if (bcf_update_format_int32(header, record, "DP", depth, 4) < 0 || bcf_update_format_float(header, record, "FP", floats, 12) < 0
                || bcf_update_format_string(header, record, "TXT", strings, 4) < 0 || bcf_update_genotypes(header, record, calls.data(), calls.size()) < 0) return 8;
        std::vector<int32_t> ac(largest, 0); int32_t an = 0;
        for (int32_t value : calls) if (value >= 2) { int allele = (value >> 1) - 1; an++; if (allele > 0) ac[allele - 1]++; }
        float vf = variant == 1 ? bits(0x7fc05678) : variant == 2 ? bits(0x7f800001) : variant == 3 ? bits(0x80000000) : 0.25f;
        int32_t end = record->pos + 20;
        int32_t vi[] = {variant, bcf_int32_missing, bcf_int32_vector_end};
        float vp[] = {0.25f, bits(0x7f800001), bits(0x7f800002)};
        if (variant == 0) ac = {ac[0], bcf_int32_vector_end, bcf_int32_vector_end};
        if (bcf_update_info_int32(header, record, "AC", ac.data(), ac.size()) < 0 || bcf_update_info_int32(header, record, "AN", &an, 1) < 0
                || bcf_update_info_int32(header, record, "END", &end, 1) < 0 || bcf_update_info_float(header, record, "VF", &vf, 1) < 0
                || bcf_update_info_int32(header, record, "VI", vi, 3) < 0 || bcf_update_info_float(header, record, "VP", vp, 3) < 0
                || bcf_write(file, header, record) < 0) return 9;
        bcf_destroy(record);
    }
    bcf_hdr_destroy(header);
    return bcf_close(file) == 0 ? 0 : 10;
}
