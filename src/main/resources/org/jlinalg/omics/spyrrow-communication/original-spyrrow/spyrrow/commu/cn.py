from .basic import *

def call(adata,location = ['x','y'], group = None, subset = None, connect_dist = 0.04, database = 'CellChatDB_cellphoneDB_iTALK'):
    with open('./database/'+database+'.pickle','rb') as f:
        ligand,receptor = pickle.load(f)
    if group is None:
        whole_combined = cal_cell_communication(adata.X,adata.obs[location[0]],adata.obs[location[1]],[ligand,receptor],connect_dist)
    else:
        whole_combined = cal_cell_communication(adata.X,adata.obs[location[0]],adata.obs[location[1]],[ligand,receptor],connect_dist)
    return whole_combined


with open('/rsrch6/home/genomic_med/lwang22_lab/Yunhe/Fibroblast_Project/Analysis_Out/TLS/cell_cell_communication_combine_0908/'+nam+'_0908.pickle','rb') as f:
    whole_combined = pickle.load(f)
inter_pair = [each.replace('_a2b','') for each in whole_combined.columns if '_a2b' in each]
ligand = [each.split(':')[0] for each in inter_pair]
receptor = [each.split(':')[1] for each in inter_pair]
#split the cell
sub_anno = tls_file.loc[tls_file[nam]=='Yes']['anno']
pair_a = [each.split('|')[0] for each in whole_combined.index]
pair_b = [each.split('|')[1] for each in whole_combined.index]
#get cell cell attribute
new_attri = [each[0]+'|'+each[1] for each in zip(sub_anno.loc[np.array(pair_a)],sub_anno.loc[np.array(pair_b)])]
rev__attri = [each[1]+'|'+each[0] for each in zip(sub_anno.loc[np.array(pair_a)],sub_anno.loc[np.array(pair_b)])]
#_a2b;_b2a;_al;_ar;_bl;_br
a_whole_combined = whole_combined[[col for col in whole_combined.columns if ('_a2b' in col) or ('_al' in col) or ('_ar' in col)]].copy()
b_whole_combined = whole_combined[[col for col in whole_combined.columns if ('_b2a' in col) or ('_bl' in col) or ('_br' in col)]].copy()
b_whole_combined.index = [each+'}r' for each in b_whole_combined.index]
#
a_whole_combined['cell_cell'] = new_attri
b_whole_combined['cell_cell'] = rev__attri
#
a_whole_combined.columns = [col.replace('_a2b','').replace('_al','_l').replace('_ar','_r') for col in a_whole_combined.columns]
b_whole_combined.columns = [col.replace('_b2a','').replace('_bl','_l').replace('_br','_r') for col in b_whole_combined.columns]
whole_combined_up = pd.concat([a_whole_combined,b_whole_combined])
#get interaction mean value
mean_values = whole_combined_up.groupby('cell_cell').mean()
mean_values['TLS'] = nam
mean_values['cell_cell'] = mean_values.index
#
pair_values = pd.DataFrame(Counter(whole_combined_up['cell_cell']), index=[0])
pair_values['TLS'] = nam
#
if start == 0:
    aggregate_summary = mean_values
    pair_summary = pair_values
    start = start+1
else:
    aggregate_summary = pd.concat([aggregate_summary,mean_values], ignore_index=True)
    pair_summary = pd.concat([pair_summary,pair_values], ignore_index=True)
