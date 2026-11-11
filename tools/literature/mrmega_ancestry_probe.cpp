// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Test-only calls to unchanged original distance, MDS and regression functions.
#include <cmath>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <string>
#include <sys/stat.h>
#include "mds.h"
#include "regression.h"

void matrix(std::ofstream &out,const char *name,matrixD &value) {
    out << name << '\t'; bool first=true;
    for (int i=0;i<value.getRows();++i) for (int j=0;j<value.getCols();++j) {
        if(!first)out<<','; first=false;out<<value.get(i,j);
    }
    out << '\n';
}
int main(int argc,char **argv) {
    if(argc!=3)return 2;std::ifstream input(argv[1]);std::string id;int n,p,m;
    while(input>>id>>n>>p>>m) {
        if(n<2||n>512||p<1||p>=n||m<1||m>10000||id.find('/')!=std::string::npos)return 2;
        matrixD frequencies(m,n);for(int i=0;i<m;++i)for(int j=0;j<n;++j){double x;if(!(input>>x))return 2;frequencies.put(i,j,x);}
        arrayD y(n),w(n);for(int i=0;i<n;++i){double a,b;if(!(input>>a>>b))return 2;y.put(i,a);w.put(i,b);}
        matrixD distance=frequencies.distance();matrixD coordinates=calculateMDS(distance,p);
        matrixD design(n,p+1);for(int i=0;i<n;++i){design.put(i,0,1);for(int j=0;j<p;++j)design.put(i,j+1,coordinates.get(i,j));}
        lr model;if(!model.lr_w(&y,&design,&w))return 2;
        std::ofstream out((std::string(argv[2])+"/"+id+".tsv").c_str());if(!out)return 2;out<<std::setprecision(17);
        matrix(out,"distances",distance);matrix(out,"coordinates",coordinates);
        out<<"coefficients\t";for(int i=0;i<=p;++i){if(i)out<<',';out<<model.Cstat->get(i);}out<<'\n';
        out<<"errors\t";for(int i=0;i<=p;++i){if(i)out<<',';out<<model.SECstat->get(i);}out<<'\n';
        matrix(out,"covariance",*model.covariance);
        double association=std::abs(model.TSS0-model.RSS),ancestry=std::abs(model.TSS-model.RSS),residual=std::abs(model.RSS);
        out<<"statistics\t"<<model.TSS<<','<<model.TSS0<<','<<model.RSS<<','<<association<<','<<ancestry<<','<<residual<<','<<(association-(p+1)*std::log(n))/2<<'\n';
    }
    return input.eof()?0:2;
}
