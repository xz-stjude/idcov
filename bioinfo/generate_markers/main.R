library(tidyverse)

markers <- read_csv('markers.csv')
nextstrain <- read_csv('nextstrain.csv') %>%
  rename(seqname='Strain',
         gisaid_clade='GISAID Clade',
         clade='Clade',
         old_nextstrain_clade='Old Nextstrain clade')

## isolate_list <- list()
## i <- 1
## process <- function(x, pos) {
##   print(pos)
##   seqname <- x$seqname
##   seq <- str_split(x$seq, "")[[1]]
##   isolate_list[[i]] <<-
##     markers %>%
##     mutate(allele=seq[pos]) %>%
##     mutate(seqname)
##   i <<- i + 1
## }

## read_csv_chunked('aligned.csv', process, 1)

## isolates_tmp <-
##   bind_rows(isolate_list) %>%
##   write_csv('isolates_tmp.csv')


## isolates <-
##   isolates_tmp %>%
##   ## chr        pos   ref allele seqname
##   ## MN908947.3 241   C   T      England/20104003002/2020
##   ## MN908947.3 1059  C   C      England/20104003002/2020
##   ## MN908947.3 3037  C   T      England/20104003002/2020
##   ## MN908947.3 8782  C   C      England/20104003002/2020
##   ## MN908947.3 11083 G   G      England/20104003002/2020
##   ## MN908947.3 14408 C   T      England/20104003002/2020
##   ## MN908947.3 14805 C   C      England/20104003002/2020
##   ## MN908947.3 17747 C   C      England/20104003002/2020
##   mutate(pos=pos %>% as_factor() %>% fct_inorder) %>%
##   pivot_wider(id_cols=c('seqname'), names_from=pos, names_prefix='pos_', values_from='allele') %>%
##   arrange(seqname) %>%
##   ## seqname                     pos_241 pos_1059 pos_3037 pos_8782
##   ## England/20104003002/2020    T       C        T        C
##   ## England/20108003302/2020    T       C        T        C
##   ## England/20104009002/2020    T       C        T        C
##   ## England/20104008402/2020    T       C        T        C
##   ## England/20134018004/2020    T       C        T        C
##   ## Ireland/21145/2020          T       C        T        C
##   ## Australia/VIC142/2020       T       T        T        C
##   ## Australia/VIC141/2020       T       C        T        C
##   ## Australia/VIC180/2020       C       C        C        C
##   ## Australia/VIC153/2020       T       C        T        C
##   write_csv('isolates.csv')

# -----------------------------------------------------

isolates <- read_csv('isolates.csv')

isolates %>%
  inner_join(nextstrain, by='seqname') %>%
  select(seqname, ends_with('clade'), starts_with('pos_')) %>%
  pivot_longer(starts_with('pos_'), names_to='pos', names_prefix='pos_', values_to='allele') %>%
  ## group_by(gisaid_clade, pos) %>%
  group_by(clade, pos) %>%
  ## group_by(gisaid_clade, pos) %>%
  count(allele, sort=T) %>%
  summarize(alleles=str_c(allele, '=', n, collapse=' ')) %>%
  mutate(pos=pos %>% as.numeric) %>%
  arrange(pos) %>%
  mutate(pos=pos %>% as_factor() %>% fct_inorder) %>%
  pivot_wider(names_from='pos', values_from='alleles') %>%
  write_csv('tmp1.csv')
