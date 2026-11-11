// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Calls unchanged upstream public regression objects; this is only a test probe.
#include <cmath>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <string>
#include "regression.h"
#include "tools.h"

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    std::ifstream input(argv[1]); std::string id; int n, axes;
    std::cout << std::setprecision(17);
    while (input >> id >> n >> axes) {
        if (n < 3 || n > 1000 || axes < 0 || axes > 20) return 2;
        arrayD y(n), w(n); matrixD x(n, axes + 1);
        double weighted = 0, sumw = 0;
        for (int i = 0; i < n; ++i) {
            double effect, weight; if (!(input >> effect >> weight)) return 2;
            y.put(i,effect); w.put(i,weight); x.put(i,0,1);
            weighted += effect * weight; sumw += weight;
            for (int j = 0; j < axes; ++j) { double value; if (!(input >> value)) return 2; x.put(i,j + 1,value); }
        }
        lr fit; if (!fit.lr_w(&y,&x,&w)) return 2;
        std::cout << id << '\t' << n << '\t' << axes;
        for (int field = 0; field < 3; ++field) {
            std::cout << '\t';
            if (field < 2) for (int j = 0; j <= axes; ++j) {
                if (j) std::cout << ',';
                std::cout << (field == 0 ? fit.Cstat->get(j) : fit.SECstat->get(j));
            }
            else for (int j = 0; j <= axes; ++j) for (int k = 0; k <= axes; ++k) {
                if (j || k) std::cout << ','; std::cout << fit.covariance->get(j,k);
            }
        }
        const int df = n - axes - 1;
        const double association = std::abs(fit.TSS0 - fit.RSS), ancestry = std::abs(fit.TSS - fit.RSS), residual = std::abs(fit.RSS);
        std::cout << '\t' << weighted / sumw << '\t' << fit.TSS << '\t' << fit.TSS0 << '\t' << fit.RSS << '\t' << fit.RSS / df
            << '\t' << association << '\t' << ancestry << '\t' << residual << '\t' << axes + 1 << '\t' << axes << '\t' << df
            << '\t' << (association > 0 ? chisqr(axes + 1,association) : NAN)
            << '\t' << (ancestry > 0 ? chisqr(axes,ancestry) : NAN)
            << '\t' << (residual > 0 ? chisqr(df,residual) : NAN)
            << '\t' << (association - (axes + 1) * std::log(n)) / 2 << '\n';
    }
    return input.eof() ? 0 : 2;
}
