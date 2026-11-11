// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-only
// Separate original-source reference executable, never linked into the Java JAR.
// Only the original CLI entry symbol is renamed; original scientific functions are called unchanged.
#define main smr_original_entrypoint_not_invoked
#include "SMR.cpp"
#undef main
#include "StatFunc.hpp"
#include <iomanip>
#include <iostream>
#include <cmath>

// The published header declares an unused float overload; declare the actual original double overload.
namespace SMRDATA {
void est_cov_bxy(Eigen::MatrixXd&, Eigen::VectorXd&, Eigen::VectorXd&,
                 Eigen::VectorXd&, Eigen::VectorXd&, Eigen::MatrixXd&);
}
static void numeric(double x) {
    if (std::isnan(x)) std::cout << "\"NaN\"";
    else if (std::isinf(x)) std::cout << (x > 0 ? "\"Inf\"" : "\"-Inf\"");
    else std::cout << x;
}
static void matrix(const Eigen::MatrixXd& x) {
    std::cout << '[';
    for (int i=0;i<x.rows();++i) {
        if(i)std::cout<<',';std::cout<<'[';
        for(int j=0;j<x.cols();++j){if(j)std::cout<<',';numeric(x(i,j));}
        std::cout<<']';
    }
    std::cout<<']';
}
int main() {
    std::cout << std::setprecision(17);
    std::string id;int n;
    while(std::cin>>id>>n) {
        if(n<2||n>500)return 2;
        Eigen::VectorXd bx(n),sx(n),by(n),sy(n),z(n),ratios(n);
        Eigen::MatrixXd ld(n,n),cov(n,n);
        for(int i=0;i<n;++i)if(!(std::cin>>bx[i]>>sx[i]>>by[i]>>sy[i]))return 2;
        for(int i=0;i<n;++i)for(int j=0;j<n;++j)if(!(std::cin>>ld(i,j)))return 2;
        z=bx.array()/sx.array();ratios=by.array()/bx.array();
        int top=CommFunc::max_abs_id(z);
        SMRDATA::est_cov_bxy(cov,z,ratios,sy,bx,ld);
        long used=n;
        double originalP=SMRDATA::bxy_hetero3(by,bx,sy,sx,z,ld,&used);
        std::cout<<"{\"id\":\""<<id<<"\",\"instruments\":"<<n<<",\"original_top_index\":"<<top<<",\"original_covariance\":";
        matrix(cov);std::cout<<",\"original_HEIDI_P\":";numeric(originalP);
        std::cout<<",\"original_degrees_count\":"<<used<<",\"original_chisq_survival_for_ratio_diagnostics\":[";
        for(int i=0;i<n;++i){if(i)std::cout<<',';double beta=by[i]/bx[i];double variance=(sy[i]*sy[i]*bx[i]*bx[i]+sx[i]*sx[i]*by[i]*by[i])/(bx[i]*bx[i]*bx[i]*bx[i]);numeric(StatFunc::pchisq(beta*beta/variance,1));}
        std::cout<<"]}"<<std::endl;
    }
    return 0;
}
