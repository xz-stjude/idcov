library(tidyverse)

args <- commandArgs(trailingOnly=T)
ID <- args[1]

## pos   ref alt
## 241   C   T
## 1059  C   T
## 3037  C   T
## 14408 C   T
## 21073 G   A
## 23403 A   G
## 25563 GGG TAC
## 27964 C   T

separate_consecutive_mutations <- function(pos, ref, alt) {
  if (str_length(ref) == 1) {
    return(tibble(pos_c=pos, ref_c=ref, alt_c=alt))
  }

  tb_list <- list()

  ref_cs <- ref %>% str_split("") %>% .[[1]]
  alt_cs <- alt %>% str_split("") %>% .[[1]]
  pos_c <- pos

  min_len <- min(length(ref_cs), length(alt_cs))

  for (i in 1:min_len) {
    ref_c <- ref_cs[i]
    alt_c <- alt_cs[i]
    tb_list[[i]] <- tibble(pos_c, ref_c, alt_c)
    pos_c <- pos_c + 1
  }
  tb <- bind_rows(tb_list)

  print(tb)

  return(tb)
}

tb <-
  read_tsv(str_c(ID, '.vcf'), comment='#',
           col_names=c('chrom', 'pos', 'id', 'ref', 'alt', 'qual', 'filter', 'info', 'format', 'tas111')) %>%
  select(pos, ref, alt, qual) %>%
  filter(qual > 10) %>%
  rowwise %>%
  mutate(data=list(separate_consecutive_mutations(pos, ref, alt))) %>%
  unnest(data) %>%
  ## pos   ref alt qual    pos_c ref_c alt_c
  ## 241   C   T   31925.6 241   C     T
  ## 1059  C   T   105165  1059  C     T
  ## 3037  C   T   64711.2 3037  C     T
  ## 14408 C   T   393119  14408 C     T
  ## 21073 G   A   59988.7 21073 G     A
  ## 23403 A   G   179887  23403 A     G
  ## 25563 GGG TAC 221834  25563 G     T
  ## 25563 GGG TAC 221834  25564 G     A
  ## 25563 GGG TAC 221834  25565 G     C
  ## 27964 C   T   179372  27964 C     T
  write_csv(str_c(ID, '_mutations.csv'))
