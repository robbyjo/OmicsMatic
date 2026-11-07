            nucfreqs <- letterFrequency(seqs, c("A", "C", "G", "T"))
            gc <- rowSums(nucfreqs[, 2:3]) / rowSums(nucfreqs)
