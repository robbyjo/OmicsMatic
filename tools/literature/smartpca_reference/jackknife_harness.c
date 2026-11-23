/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Caller only: wjackest and all numerical functions are linked unchanged from EIG. */
#include <qpsubs.h>
static void value(double x) {
    if(isnan(x)) printf("\"NaN\"");
    else if(isinf(x)) printf(x>0?"\"Inf\"":"\"-Inf\"");
    else printf("%.17g",x);
}
int main(void) {
    int n,i;double mean,estimate,sd,*deleted,*weights;
    if(scanf("%d %lf",&n,&mean)!=2||n<1||n>10000)return 11;
    deleted=calloc((size_t)n,sizeof(double));weights=calloc((size_t)n,sizeof(double));
    if(deleted==NULL||weights==NULL)return 12;
    for(i=0;i<n;i++)if(scanf("%lf",deleted+i)!=1)return 13;
    for(i=0;i<n;i++)if(scanf("%lf",weights+i)!=1)return 14;
    wjackest(&estimate,&sd,mean,deleted,weights,n);
    printf("{\"estimate\":");value(estimate);printf(",\"standard_error\":");value(sd);printf("}\n");
    free(deleted);free(weights);return 0;
}
