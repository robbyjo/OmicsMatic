/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Driver/observer for unmodified PMBio PEER, not a replacement inference engine.
 */
#include "vbfa.h"
#include <cerrno>
#include <climits>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iomanip>
#include <limits>
#include <map>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

static const char* SOURCE_COMMIT="40bc4b2cd92459ce42f44dfe279717436395f3f6";

struct BoundObservation { double bound; double residual_variance; };
class ObservedDensePeer : public PEER::cVBFA {
public:
    std::vector<BoundObservation> bound_observations;
    double calcBound() override {
        double value=PEER::cVBFA::calcBound();
        // Observe the exact double returned to the unchanged original update loop.
        double variance=calc_residuals().array().pow(2).mean();
        BoundObservation observation={value,variance};bound_observations.push_back(observation);return value;
    }
};

static void write_new(const std::string& path,const std::string& text) {
    FILE* file=std::fopen(path.c_str(),"wx");
    if(!file) throw std::runtime_error("Cannot create NEW output: "+path);
    bool ok=std::fwrite(text.data(),1,text.size(),file)==text.size();
    if(std::fclose(file)!=0) ok=false;
    if(!ok) throw std::runtime_error("Output write failed: "+path);
}

static PMatrix read_matrix(const std::string& path) {
    std::ifstream file(path.c_str());if(!file)throw std::runtime_error("Cannot read matrix: "+path);
    std::vector<std::vector<float> > rows;std::string line;size_t columns=0;
    while(std::getline(file,line)) {
        if(!line.empty()&&line[line.size()-1]=='\r')line.erase(line.size()-1);
        if(line.empty())throw std::runtime_error("Empty input matrix row: "+path);
        std::vector<float> row;std::istringstream fields(line);std::string value;
        while(std::getline(fields,value,'\t')) {
            char* end=0;errno=0;double parsed=std::strtod(value.c_str(),&end);
            if(value.empty()||*end||!std::isfinite(parsed)||!std::isfinite(static_cast<float>(parsed)))throw std::runtime_error("Invalid/nonfinite FP32 input: "+path);
            row.push_back(static_cast<float>(parsed));
        }
        if(!line.empty()&&line[line.size()-1]=='\t')throw std::runtime_error("Trailing empty matrix cell: "+path);
        if(rows.empty())columns=row.size();
        if(columns==0||row.size()!=columns)throw std::runtime_error("Nonrectangular input matrix: "+path);
        rows.push_back(row);if(rows.size()*columns>50000000)throw std::runtime_error("Driver capacity exceeds 50 million cells: "+path);
    }
    if(rows.empty())throw std::runtime_error("Empty input matrix: "+path);
    PMatrix result(rows.size(),columns);
    for(size_t i=0;i<rows.size();++i)for(size_t j=0;j<columns;++j)result(i,j)=rows[i][j];
    return result;
}

static void matrix(std::ostream& out,const std::string& name,const PMatrix& values) {
    for(int i=0;i<values.rows();++i)for(int j=0;j<values.cols();++j)
        out<<name<<'\t'<<i<<'\t'<<j<<'\t'<<static_cast<double>(values(i,j))<<'\n';
}

static bool all_finite(const PMatrix& values) {
    for(int i=0;i<values.rows();++i)for(int j=0;j<values.cols();++j)
        if(!std::isfinite(static_cast<double>(values(i,j))))return false;
    return true;
}

static void state(std::ostream& out,PEER::cVBFA& model,bool final) {
    matrix(out,"Y",model.pheno->E1);matrix(out,"Y2",model.pheno->E2);matrix(out,"measurementVariance",model.pheno_var);
    matrix(out,"covariates",model.covs);matrix(out,"X",model.X->E1);matrix(out,"W",model.W->E1);
    matrix(out,"X2",model.X->E2S);matrix(out,"W2",model.W->E2S);matrix(out,"WXPrecision",model.W->Xprec);
    matrix(out,"XPriorMean",model.X->prior_offset);matrix(out,"XPriorPrecision",model.X->prior_prec);
    matrix(out,"alpha",model.Alpha->E1);matrix(out,"alphaLn",model.Alpha->lnE);matrix(out,"alphaA",model.Alpha->a);matrix(out,"alphaB",model.Alpha->b);
    matrix(out,"epsilon",model.Eps->E1);matrix(out,"epsilonLn",model.Eps->lnE);matrix(out,"epsilonA",model.Eps->a);matrix(out,"epsilonB",model.Eps->b);
    matrix(out,"residual",model.getResiduals());
    if(final) {
        matrix(out,"XCovariance",model.X->cov);matrix(out,"WAlphaLast",model.W->A_last);matrix(out,"WEpsilonLast",model.W->E_last);matrix(out,"WX2Last",model.W->XE2S_last);
        out<<"WLogDetCovarianceSum\t0\t0\t"<<model.W->lndetcovS<<'\n';
    }
}

static int integer(const std::string& value,int minimum,int maximum) {
    size_t end=0;long parsed=std::stol(value,&end);
    if(end!=value.size()||parsed<minimum||parsed>maximum)throw std::runtime_error("Invalid integer argument: "+value);
    return static_cast<int>(parsed);
}
static double number(const std::string& value,bool positive) {
    size_t end=0;double parsed=std::stod(value,&end);
    if(end!=value.size()||!std::isfinite(parsed)||(positive?parsed<=0:parsed<0))throw std::runtime_error("Invalid numeric argument: "+value);
    return parsed;
}

int main(int argc,char** argv) {
    std::string output;
    try {
        std::map<std::string,std::string> args;
        const std::string allowed=" data covariates measurement-variance factors out seed max-iterations tolerance variance-tolerance add-mean alpha-a alpha-b epsilon-a epsilon-b covariate-precision initialization verbose ";
        for(int i=1;i<argc;i+=2) {
            std::string key=argv[i];if(key.size()<3||key.substr(0,2)!="--"||i+1>=argc)throw std::runtime_error("Use --name value pairs");key=key.substr(2);
            if(allowed.find(" "+key+" ")==std::string::npos||args.count(key))throw std::runtime_error("Unknown/duplicate argument: "+key);args[key]=argv[i+1];
        }
        if(!args.count("data")||!args.count("factors")||!args.count("out"))throw std::runtime_error("Required: --data, --factors, --out (NEW empty directory)");
        output=args["out"];
        const auto option=[&args](const std::string& key,const std::string& fallback){return args.count(key)?args[key]:fallback;};
        const std::string seedText=option("seed","1");size_t seedEnd=0;unsigned long seed=std::stoul(seedText,&seedEnd);
        if(seedText.empty()||seedText[0]=='-'||seedEnd!=seedText.size()||seed>UINT_MAX)throw std::runtime_error("Seed must fit unsigned C srand");
        PEER::setVerbose(integer(option("verbose","1"),0,3));std::srand(static_cast<unsigned int>(seed));
        ObservedDensePeer model;model.pheno_mean=read_matrix(args["data"]);
        if(args.count("covariates"))model.covs=read_matrix(args["covariates"]);
        if(args.count("measurement-variance")) {
            model.pheno_var=read_matrix(args["measurement-variance"]);
            if(model.pheno_var.rows()!=model.pheno_mean.rows()||model.pheno_var.cols()!=model.pheno_mean.cols()||(model.pheno_var.array()<0).any())throw std::runtime_error("Invalid measurement-variance shape/value");
        }
        model.Nk=integer(args["factors"],1,1000000);model.add_mean=integer(option("add-mean","0"),0,1)!=0;
        if(model.covs.size()&&model.covs.rows()!=model.pheno_mean.rows())throw std::runtime_error("Covariate sample count differs");
        int total=model.Nk+model.covs.cols()+(model.add_mean?1:0);
        if(total>=model.pheno_mean.rows()||total>=model.pheno_mean.cols())throw std::runtime_error("Total factors must be less than samples and features");
        model.Nmax_iterations=integer(option("max-iterations","1000"),1,1000000);
        model.tolerance=number(option("tolerance","0.001"),false);model.var_tolerance=number(option("variance-tolerance","0.00000001"),false);
        model.Alpha_pa=number(option("alpha-a","0.001"),true);model.Alpha_pb=number(option("alpha-b","0.1"),true);
        model.Eps_pa=number(option("epsilon-a","0.1"),true);model.Eps_pb=number(option("epsilon-b","10"),true);
        model.covariate_prec=number(option("covariate-precision","100"),true);
        std::string initialization=option("initialization","randn");
        if(initialization!="randn"&&initialization!="pca")throw std::runtime_error("Initialization must be randn or pca");
        model.initialisation=initialization=="randn"?RANDN:PCA;
        std::ostringstream metadata;metadata<<std::setprecision(17)<<"source_commit\t"<<SOURCE_COMMIT<<"\nPMatrix_scalar_bytes\t"<<sizeof(PMatrix::Scalar)<<"\nC_rand_max\t"<<RAND_MAX<<"\nseed\t"<<seed<<"\ninitialization\t"<<initialization<<"\nverbose\t"<<PEER::getVerbose()<<"\nmax_iterations\t"<<model.Nmax_iterations<<"\ntolerance\t"<<model.tolerance<<"\nvariance_tolerance\t"<<model.var_tolerance<<"\n";
        write_new(output+"/parameters.tsv",metadata.str());
        model.init_net();
        std::ostringstream initial;initial<<std::setprecision(17)<<"field\trow\tcolumn\tvalue\n";state(initial,model,false);write_new(output+"/initial-state.tsv",initial.str());
        model.update();
        std::ostringstream final;final<<std::setprecision(17)<<"field\trow\tcolumn\tvalue\n";state(final,model,true);write_new(output+"/final-state.tsv",final.str());
        bool converged=model.Niterations<model.Nmax_iterations;
        int executed=model.Niterations+(converged?1:0);
        bool monitored=PEER::getVerbose()>=2||model.tolerance>0;
        std::ostringstream history;history<<std::setprecision(17)<<"iteration\tbound_computed\tbound\tresidual_variance\tin_original_getter_history\n";
        for(int i=0;i<executed;++i){history<<i+1<<'\t'<<(monitored?1:0)<<'\t';if(monitored)history<<model.Tbound(i);else history<<"NA";history<<'\t'<<model.Tresidual_varaince(i)<<'\t'<<(i<model.Niterations?1:0)<<'\n';}
        write_new(output+"/history.tsv",history.str());
        std::ostringstream observed;observed<<std::setprecision(17)<<"call\tbound_double\tresidual_variance\n";
        for(size_t i=0;i<model.bound_observations.size();++i)observed<<i<<'\t'<<model.bound_observations[i].bound<<'\t'<<model.bound_observations[i].residual_variance<<'\n';
        write_new(output+"/bound-observations.tsv",observed.str());
        std::ostringstream monitor;monitor<<std::setprecision(17)<<"iteration\tbound_double\tresidual_variance\n";
        size_t cursor=0;
        if(monitored)for(int i=0;i<executed;++i) {
            cursor+=PEER::getVerbose()>=3?(i==0?3:5):1;
            if(cursor>model.bound_observations.size())throw std::runtime_error("Incomplete owned bound observer events");
            const BoundObservation& observation=model.bound_observations[cursor-1];
            monitor<<i+1<<'\t'<<observation.bound<<'\t'<<observation.residual_variance<<'\n';
        }
        if(cursor!=model.bound_observations.size())throw std::runtime_error("Unexpected owned bound observer event population");
        write_new(output+"/monitor.tsv",monitor.str());
        bool finite=all_finite(model.X->E1)&&all_finite(model.W->E1)&&all_finite(model.Alpha->E1)&&all_finite(model.Eps->E1)&&all_finite(model.getResiduals())
            &&all_finite(model.X->E2S)&&all_finite(model.W->E2S)&&all_finite(model.W->Xprec)&&all_finite(model.X->cov)
            &&all_finite(model.Alpha->lnE)&&all_finite(model.Eps->lnE)&&all_finite(model.Alpha->a)&&all_finite(model.Alpha->b)&&all_finite(model.Eps->a)&&all_finite(model.Eps->b);
        for(int i=0;i<executed;++i)finite=finite&&std::isfinite(model.Tresidual_varaince(i))&&(!monitored||std::isfinite(model.Tbound(i)));
        std::ostringstream status;status<<"status\t"<<(finite&&converged?"PASS_DENSE_ORIGINAL_RUN":"FAIL2_NO_COMPLETE_RESULT")<<"\noriginal_reported_iterations\t"<<model.Niterations<<"\nexecuted_updates\t"<<executed<<"\nconverged\t"<<(converged?1:0)<<"\nfinite_exported_means\t"<<(finite?1:0)<<"\nknown_covariates\t"<<model.Nc<<"\ntotal_factors\t"<<model.Nk<<"\n";
        write_new(output+"/status.tsv",status.str());
        if(!finite||!converged){std::cerr<<"FAIL2: NO COMPLETE RESULT; original model/state/history retained. Resolve numerical failure or iteration limit and retry NEW output.\n";return 2;}
        return 0;
    } catch(const std::exception& error) {
        std::cerr<<"FAIL2: NO COMPLETE RESULT; "<<error.what()<<". Preserve inputs and partial output; correct the named cause and retry NEW output.\n";return 2;
    }
}
