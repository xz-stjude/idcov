#!/usr/bin/env Rscript

MIN_FRAGS_TO_CONFIRM_MUTATION <- 8

library(tidyverse)

args <- commandArgs(trailingOnly = TRUE)

df_markers <- read_tsv(
  args[1],
  ## './results/23_Rabeh14_S22_L001_coverage.bed',
  col_names = c(
    "chr",
    "start",
    "end",
    "n_covering_frags",
    "n_covered_bases",
    "size",
    "frac_covered"
  )
) %>%
  select(pos = end, n_covering_frags) %>%
  print()

df_mutations <- read_tsv(
  args[2],
  ## './results/23_Rabeh14_S22_L001.vcf',
  comment = "#",
  col_names = c(
    "chr",
    "pos",
    "id",
    "ref",
    "alt",
    "qual",
    "filter",
    "info",
    "format",
    "scores"
  ),
  col_types = cols(
    pos = col_integer(),
    ref = col_character(),
    alt = col_character()
  )
) %>%
  select(pos, ref, alt) %>%
  mutate(is_mutated='yes') %>%
  print()

marker_ref <-
  ## read_csv('./6-markers/markers.csv') %>%
  read_csv(args[3],
           col_types = cols(
             pos = col_integer(),
             ref = col_character()
           )) %>%
  select(pos, original_ref=ref) %>%
  print

out_df <-
  left_join(df_markers, df_mutations) %>%
  left_join(marker_ref) %>%
  mutate(alt=if_else(n_covering_frags < MIN_FRAGS_TO_CONFIRM_MUTATION, 'Z', if_else(is.na(is_mutated), original_ref, alt))) %>%
  mutate(ref=original_ref) %>%

## pos   n_covering_frags ref alt is_mutated original_ref
## 241   3531             C   C              C
## 1059  2253             C   C              C
## 3037  2340             C   C              C
## 8782  2777             C   C              C
## 11083 1991             G   T   yes        G
## 14408 3793             C   C              C
## 14805 2840             C   C              C
## 17747 1624             C   C              C
## 17858 1490             A   A              A
## 18060 1179             C   C              C
## 23403 1829             A   A              A
## 25563 2605             G   G              G
## 26144 6348             G   G              G
## 28144 2364             T   T              T
## 28881 316              G   G              G
## 28882 316              G   G              G
## 28883 307              G   G              G

  write_csv(args[6], na='')


##--------------------------------------------------------------------------------


work <- function(ns_id) {
  ## read_tsv('./7-strains/strains.txt') %>%
  read_csv(str_c(args[4], "_", ns_id, ".csv")) %>%

  ##  clade 241 1059 3037 8782 11083 23403 25563 26144 28144 28882
  ##  L     C        C    C    G     A     G     G     T     G
  ##  S     C        C    T    G     A     G     G     C     G
  ##  V     C        C    C    T     A     G     T     T     G
  ##  G     T        T    C    G     G     G     G     T     G
  ##  GR    T        T    C    G     G     G     G     T     A
  ##  GH    T        T    C    G     G     T     G     T     G

    gather(key='pos', value='alt', -clade) %>%
    mutate(pos=as.integer(pos)) %>%

  ## [[1]]
  ## # A tibble: 11 x 3
  ##    clade pos   alt
  ##    <chr> <chr> <chr>
  ##  1 20B   241   T
  ##  2 20B   1059  C
  ##  3 20B   3037  T
  ##  4 20B   8782  C
  ##  5 20B   14408 T
  ##  6 20B   23403 G
  ##  7 20B   25563 G
  ##  8 20B   28144 T
  ##  9 20B   28881 A
  ## 10 20B   28882 A
  ## 11 20B   28883 C
  ## ...

    left_join(out_df %>% select(pos, alt), by=c("pos"), suffix=c("_strain", "_sample")) %>%
    mutate(delta=if_else(alt_sample == 'Z', 0.5, if_else(alt_sample != alt_strain, 1, 0))) %>%
    group_by(clade) %>%
    summarize(manhattan=sum(delta)) %>%
    print %>%
    list
}

naming_system_df <-
  read_csv(args[7]) %>%

## naming_system  id              link_to_nextstrain
## GISAID         gisaid          https://nextstrain.org/ncov/global?c=GISAID_clade
## Old Nextstrain old_next_strain https://nextstrain.org/ncov/global?c=legacy_clade_membership
## New Nextstrain new_next_strain https://nextstrain.org/ncov/global?c=clade_membership

  select(id) %>%
  rowwise %>%
  mutate(data=work(id)) %>%
  unnest(data) %>%

## # A tibble: 24 x 3
##    id             clade manhattan
##    <chr>          <chr>     <dbl>
##  1 gisaid         G             4
##  2 gisaid         GH            5
##  3 gisaid         GR            5
##  4 gisaid         L             1
##  5 gisaid         S             3
##  6 gisaid         V             1
##  7 old_nextstrain A1a           3
##  8 old_nextstrain A1b           1
##  9 old_nextstrain A1c           1
## 10 old_nextstrain A1d           1
## 11 old_nextstrain A2            4
## 12 old_nextstrain A2a1          8
## 13 old_nextstrain A2a2          5
## 14 old_nextstrain A2a3          7
## 15 old_nextstrain A3            0
## 16 old_nextstrain A6            1
## 17 old_nextstrain B             2
## 18 old_nextstrain B1            6
## 19 old_nextstrain B4            2
## 20 new_nextstrain 19A           0
## 21 new_nextstrain 19B           2
## 22 new_nextstrain 20A           5
## 23 new_nextstrain 20B           7
## 24 new_nextstrain 20C           6

  arrange(id, manhattan) %>%
  write_csv(args[5], na='')





## manhattan_df_list <- list()
## for (i_naming_system in 1:nrow(naming_system_df)) {
##   naming_system <- naming_system_df$naming_system[i_naming_system]
##   ns_id <- naming_system_df$id[i_naming_system]
##   link_to_nextstrain <- naming_system_df$link_to_nextstrain[i_naming_system]

##   strains_df <-
##     ## read_tsv('./7-strains/strains.txt') %>%
##     read_csv(str_c(args[4], "_", ns_id, ".csv")) %>%
##     print
##   ##  clade 241 1059 3037 8782 11083 23403 25563 26144 28144 28882
##   ##  L     C        C    C    G     A     G     G     T     G
##   ##  S     C        C    T    G     A     G     G     C     G
##   ##  V     C        C    C    T     A     G     T     T     G
##   ##  G     T        T    C    G     G     G     G     T     G
##   ##  GR    T        T    C    G     G     G     G     T     A
##   ##  GH    T        T    C    G     G     T     G     T     G

##   work <- function(x, y) {
##     print(x)
##     print(y)
##   }

##   manhattan_df_list[[i_naming_system]] <-
##     strains_df %>%
##     gather(key='pos', value='alt', -clade) %>%
##     mutate(pos=as.integer(pos)) %>%

##   ## [[1]]
##   ## # A tibble: 11 x 3
##   ##    clade pos   alt
##   ##    <chr> <chr> <chr>
##   ##  1 20B   241   T
##   ##  2 20B   1059  C
##   ##  3 20B   3037  T
##   ##  4 20B   8782  C
##   ##  5 20B   14408 T
##   ##  6 20B   23403 G
##   ##  7 20B   25563 G
##   ##  8 20B   28144 T
##   ##  9 20B   28881 A
##   ## 10 20B   28882 A
##   ## 11 20B   28883 C
##   ## ...

##     left_join(out_df %>% select(pos, alt), by=c("pos"), suffix=c("_strain", "_sample")) %>%
##     mutate(delta=if_else(alt_sample == 'Z', 0.5, if_else(alt_sample != alt_strain, 1, 0))) %>%
##     print(n=1000) %>%
##     group_by(clade) %>%
##     summarize(manhattan=sum(delta)) %>%
##     mutate(naming_system=naming_system) %>%
##     print

##   ## scores_list <- list()
##   ## for (strain in c("A1a", "A1b", "A1c", "A1d", "A2", "A2a1", "A2a2", "A2a3", "A3", "A6", "B", "B1", "B4")) {
##   ##   strain_ref <- strains_df[[strain]]
##   ##   scores <- if_else(out_df$alt == 'Z', 0.5, if_else(strain_ref != out_df$alt, 1, 0))
##   ##   scores_list[[strain]] <- sum(scores)
##   ## }
##   ## score_df <- scores_list %>% enframe('strain', 'score') %>% unnest(score)
##   ## score_df %>% write_csv(args[5])
##   ## ## strain score
##   ## ## A1a    3
##   ## ## A1b    1
##   ## ## A1c    1
##   ## ## A1d    1
##   ## ## A2     4
##   ## ## A2a1   8
##   ## ## A2a2   5
##   ## ## A2a3   7
##   ## ## A3     0
##   ## ## A6     1
##   ## ## B      2
##   ## ## B1     6
##   ## ## B4     2

##   ## write_csv(out_df, args[6], na='')
## }

## manhattan_df_list %>%
##   bind_rows %>%
##   print(n=1000)
