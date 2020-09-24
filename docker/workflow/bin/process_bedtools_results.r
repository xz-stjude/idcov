library(tidyverse)

args <- commandArgs(trailingOnly=T)
ID <- args[1]


## MN908947.3 240   241   1113  1 1 1.0000000
## MN908947.3 1058  1059  3541  1 1 1.0000000
## MN908947.3 3036  3037  2281  1 1 1.0000000
## MN908947.3 8781  8782  1413  1 1 1.0000000
## MN908947.3 11082 11083 2548  1 1 1.0000000
## MN908947.3 14407 14408 13151 1 1 1.0000000
## MN908947.3 14804 14805 2892  1 1 1.0000000

tb <-
  read_tsv(str_c(ID, '_coverage.bed'),
           col_names = c(
             "chr",
             "start",
             "end",
             "n_covering_frags",
             "n_covered_bases",
             "size",
             "frac_covered"
           )) %>%
  write_csv(str_c(ID, '_coverage.csv'))
