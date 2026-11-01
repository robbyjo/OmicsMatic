// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Independent HTSlib getters: no Java decoder or SHAPEIT port is used here.
#include <htslib/vcf.h>
#include <cstring>
#include <cstdlib>
#include <fstream>
#include <iomanip>
#include <string>

static void integers(std::ofstream& out, int32_t* values, int length) {
    for (int i = 0; i < length; i++) { if (i) out << ','; out << values[i]; }
}
static void floating(std::ofstream& out, float* values, int length) {
    for (int i = 0; i < length; i++) {
        uint32_t bits; std::memcpy(&bits, &values[i], sizeof(bits));
        if (i) out << ',';
        out << std::hex << std::setw(8) << std::setfill('0') << bits << std::dec;
    }
}
static void bytes(std::ofstream& out, const uint8_t* data, int length) {
    for (int i = 0; i < length; i++) out << std::hex << std::setw(2) << std::setfill('0') << unsigned(data[i]);
    out << std::dec;
}
int main(int argc, char** argv) {
    if (argc != 3 || std::ifstream(argv[2]).good()) return 2;
    htsFile* file = bcf_open(argv[1], "r");
    if (!file) return 3;
    bcf_hdr_t* header = bcf_hdr_read(file);
    bcf1_t* record = bcf_init();
    std::ofstream out(argv[2]);
    if (!header || !record || !out) return 4;
    out << "H\t" << bcf_hdr_nsamples(header) << '\n';
    out << "V\t" << bcf_hdr_get_version(header) << '\n';
    for (int sample = 0; sample < bcf_hdr_nsamples(header); sample++) out << "S\t" << sample << '\t' << header->samples[sample] << '\n';
    int32_t *iv = nullptr; int ni = 0;
    float *fv = nullptr; int nf = 0;
    int variant = 0, status;
    while ((status = bcf_read(file, header, record)) == 0) {
        bcf_unpack(record, BCF_UN_ALL);
        uint32_t quality; std::memcpy(&quality, &record->qual, sizeof(quality));
        out << "R\t" << variant << '\t' << bcf_hdr_id2name(header, record->rid) << '\t' << record->pos + 1 << '\t' << record->rlen << '\t' << record->d.id << '\t' << record->d.allele[0] << '\t';
        for (int i = 1; i < record->n_allele; i++) { if (i > 1) out << ','; out << record->d.allele[i]; }
        out << '\t' << std::hex << std::setw(8) << std::setfill('0') << quality << std::dec << '\n';
        for (int i = 0; i < record->n_fmt; i++) {
            bcf_fmt_t* format = &record->d.fmt[i];
            const char* name = bcf_hdr_int2id(header, BCF_DT_ID, format->id);
            out << "T\t" << variant << '\t' << name << '\t' << format->type << '\t' << format->n << '\n';
            out << "B\t" << variant << "\tF\t" << name << '\t' << format->type << '\t' << format->n << '\t' << bcf_hdr_nsamples(header) << '\t';
            bytes(out, format->p, format->size * bcf_hdr_nsamples(header)); out << '\n';
            int kind = bcf_hdr_id2type(header, BCF_HL_FMT, format->id);
            if (std::string(name) == "GT" || kind == BCF_HT_INT) {
                int n = std::string(name) == "GT" ? bcf_get_genotypes(header, record, &iv, &ni) : bcf_get_format_int32(header, record, name, &iv, &ni);
                if (n < 0) return 5;
                out << "G\t" << variant << '\t' << name << '\t'; integers(out, iv, n); out << '\n';
            } else if (kind == BCF_HT_REAL) {
                int n = bcf_get_format_float(header, record, name, &fv, &nf);
                if (n < 0) return 6;
                out << "F\t" << variant << '\t' << name << '\t'; floating(out, fv, n); out << '\n';
            }
        }
        for (int i = 0; i < record->n_info; i++) {
            bcf_info_t* info = &record->d.info[i];
            const char* name = bcf_hdr_int2id(header, BCF_DT_ID, info->key);
            int kind = bcf_hdr_id2type(header, BCF_HL_INFO, info->key);
            out << "B\t" << variant << "\tI\t" << name << '\t' << info->type << '\t' << info->len << "\t1\t";
            bytes(out, info->vptr, info->vptr_len); out << '\n';
            if (kind == BCF_HT_INT) {
                int n = bcf_get_info_int32(header, record, name, &iv, &ni); if (n < 0) return 7;
                out << "I\t" << variant << '\t' << name << '\t'; integers(out, iv, n); out << '\n';
            } else if (kind == BCF_HT_REAL) {
                int n = bcf_get_info_float(header, record, name, &fv, &nf); if (n < 0) return 8;
                out << "P\t" << variant << '\t' << name << '\t'; floating(out, fv, n); out << '\n';
            }
        }
        variant++;
    }
    out << "E\t" << variant << '\n'; out.flush();
    bool complete = status == -1 && out.good();
    free(iv); free(fv); bcf_destroy(record); bcf_hdr_destroy(header);
    if (bcf_close(file) != 0) complete = false;
    return complete ? 0 : 9;
}
