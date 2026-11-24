# Copyright (C) 2026 JLinAlg contributors; GPL-3.0-or-later.
# Primitive R values only. No eval(), arbitrary RDS loading or class hooks.
prep_hex<-function(x)paste(sprintf('%02x',as.integer(charToRaw(enc2utf8(x)))),collapse='')
prep_unhex<-function(x){stopifnot(length(x)==1L,grepl('^(?:[0-9a-f]{2})*$',x,perl=TRUE));if(!nzchar(x))return('');out<-rawToChar(as.raw(strtoi(substring(x,seq(1,nchar(x),2),seq(2,nchar(x),2)),16L)));Encoding(out)<-'UTF-8';out}
prep_token<-function(v,i){
 x<-v[i]
 if(is.numeric(x)&&is.nan(x))return('Q')
 if(is.na(x))return('N')
 if(is.numeric(x)&&is.infinite(x))return(if(x>0)'P'else'M')
 if(is.character(x))return(paste0('S:',prep_hex(x)))
 if(is.logical(x))return(if(x)'B:TRUE'else'B:FALSE')
 if(is.integer(x))return(paste0('I:',as.character(x)))
 if(is.double(x))return(paste0('D:',sprintf('%.17g',x)))
 stop('Unsupported primitive output type')
}
prep_decode_tokens<-function(tokens,type){
 switch(type,integer={out<-rep(NA_integer_,length(tokens));present<-tokens!='N';stopifnot(all(grepl('^I:-?[0-9]+$',tokens[present])));out[present]<-as.integer(substring(tokens[present],3));out},
 double={out<-rep(NA_real_,length(tokens));present<-!tokens%in%c('N','Q','P','M');stopifnot(all(grepl('^D:',tokens[present])));out[present]<-as.numeric(substring(tokens[present],3));out[tokens=='Q']<-NaN;out[tokens=='P']<-Inf;out[tokens=='M']<- -Inf;out},
 logical={stopifnot(all(tokens%in%c('N','B:TRUE','B:FALSE')));out<-rep(NA,length(tokens));out[tokens=='B:TRUE']<-TRUE;out[tokens=='B:FALSE']<-FALSE;out},
 character={stopifnot(all(tokens=='N'|grepl('^S:',tokens)));out<-rep(NA_character_,length(tokens));present<-tokens!='N';out[present]<-vapply(substring(tokens[present],3),prep_unhex,character(1));out},
 stop('Unsupported decoded primitive type'))
}
prep_write_object<-function(value,directory,limits){
 stopifnot(!dir.exists(directory));dir.create(directory);dir.create(file.path(directory,'tables'));total<-0;files<-list();nodes<-0
 encode<-function(x,depth=0L){
  nodes<<-nodes+1L;if(depth>48L||nodes>100000L)stop('Original structure node/depth capacity exceeded')
  if(is.null(x))return(list(kind='null'))
  type<-typeof(x);if(!type%in%c('list','double','integer','logical','character'))stop(paste('Unsupported original R type:',type))
  at<-attributes(x);if(is.data.frame(x))at$row.names<-.row_names_info(x,type=0L)
  attrs<-if(is.null(at))list()else lapply(names(at),function(n)list(name_hex=prep_hex(n),value=encode(at[[n]],depth+1L)))
  if(type=='list')return(list(kind='list',length=length(x),attributes=attrs,children=unname(lapply(x,encode,depth=depth+1L))))
  # Strip attributes without changing values before scalar extraction (factors
  # are integer data with separately retained class/level attributes).
  plain<-x;attributes(plain)<-NULL;size<-length(plain);total<<-total+size
  if(total>limits$maximum_values||total>limits$maximum_result_rows)stop('Original primitive-value capacity exceeded')
  node<-list(kind='atomic',type=type,length=size,attributes=attrs)
  if(size<=128L){node$values<-if(size)lapply(seq_len(size),function(i)prep_token(plain,i))else list();return(node)}
  id<-length(files)+1L;relative<-sprintf('tables/table-%06d.tsv',id);file<-file.path(directory,relative);con<-file(file,'wt',encoding='UTF-8');on.exit(close(con))
  writeLines('token',con)
  for(start in seq.int(1L,size,by=65536L)){end<-min(size,start+65535L);writeLines(vapply(start:end,function(i)prep_token(plain,i),character(1)),con,useBytes=TRUE)}
  close(con);on.exit(NULL);files[[id]]<<-relative;node$file<-relative;node
 }
 root<-encode(value);tree<-list(schema_version=1,root=root,vector_files=unname(files),primitive_cells=total,structure_nodes=nodes,opaque_RDS_interpreted=FALSE)
 jsonlite::write_json(tree,file.path(directory,'tree.json'),auto_unbox=TRUE,pretty=TRUE,null='null',digits=17)
 invisible(tree)
}
prep_read_object<-function(directory,limits){
 tree<-jsonlite::read_json(file.path(directory,'tree.json'),simplifyVector=FALSE);stopifnot(tree$schema_version==1);total<-0;nodes<-0;used<-character(0)
 decode<-function(node,depth=0L){
  nodes<<-nodes+1L;if(depth>48L||nodes>100000L)stop('Typed input node/depth capacity exceeded')
  if(node$kind=='null')return(NULL)
  stopifnot(node$kind%in%c('list','atomic'),is.numeric(node$length),node$length>=0,node$length==floor(node$length))
  if(node$kind=='list'){
   stopifnot(length(node$children)==node$length);value<-lapply(node$children,decode,depth=depth+1L)
  }else{
   stopifnot(node$type%in%c('double','integer','logical','character'));total<<-total+node$length
   if(total>limits$maximum_values||total>limits$maximum_result_rows)stop('Typed primitive-value capacity exceeded')
   if(!is.null(node$file)){
    stopifnot(is.null(node$values),grepl('^tables/table-[0-9]{6}\\.tsv$',node$file),!node$file%in%used);used<<-c(used,node$file)
    path<-file.path(directory,node$file);con<-file(path,'rt',encoding='UTF-8');on.exit(close(con));stopifnot(identical(readLines(con,n=1L,warn=FALSE),'token'))
    value<-switch(node$type,double=double(node$length),integer=integer(node$length),logical=logical(node$length),character=character(node$length));at<-0L
    repeat{
     lines<-readLines(con,n=65536L,warn=FALSE);if(!length(lines))break
     stopifnot(all(nchar(lines,type='bytes')<=limits$maximum_line_characters),at+length(lines)<=node$length)
     ix<-seq.int(at+1L,at+length(lines));value[ix]<-prep_decode_tokens(lines,node$type);at<-at+length(lines)
    }
    stopifnot(at==node$length);close(con);on.exit(NULL)
   }else{stopifnot(length(node$values)==node$length);value<-prep_decode_tokens(unlist(node$values,use.names=FALSE),node$type)}
  }
  attrs<-list();if(length(node$attributes))for(attribute in node$attributes){name<-prep_unhex(attribute$name_hex);stopifnot(name%in%c('names','dim','dimnames','class','row.names','levels','na.action'),!name%in%names(attrs));attrs[[name]]<-decode(attribute$value,depth+1L)}
  if(!is.null(attrs$class))stopifnot(is.character(attrs$class),all(attrs$class%in%c('data.frame','matrix','array','factor','ordered','omit','exclude','AsIs')))
  attributes(value)<-attrs;value
 }
 value<-decode(tree$root);actual<-sort(list.files(file.path(directory,'tables'),full.names=FALSE));declared<-vapply(tree$vector_files,identity,character(1))
 stopifnot(total==tree$primitive_cells,nodes==tree$structure_nodes,setequal(used,declared),identical(sort(basename(used)),actual))
 value
}
