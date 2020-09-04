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
  print
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

naming_system_df <-
  read_csv(args[7]) %>%
  print
## naming_system  id              link_to_nextstrain
## GISAID         gisaid          https://nextstrain.org/ncov/global?c=GISAID_clade
## Old Nextstrain old_next_strain https://nextstrain.org/ncov/global?c=legacy_clade_membership
## New Nextstrain new_next_strain https://nextstrain.org/ncov/global?c=clade_membership

for (i_naming_system in 1:nrow(naming_system_df)) {
  naming_system <- naming_system_df$naming_system[i_naming_system]
  ns_id <- naming_system_df$id[i_naming_system]
  link_to_nextstrain <- naming_system_df$link_to_nextstrain[i_naming_system]

  strains_df <-
    ## read_tsv('./7-strains/strains.txt') %>%
    read_csv(str_c(args[4], "_", ns_id, ".csv")) %>%
    print
  ##  clade 241 1059 3037 8782 11083 23403 25563 26144 28144 28882
  ##  L     C        C    C    G     A     G     G     T     G
  ##  S     C        C    T    G     A     G     G     C     G
  ##  V     C        C    C    T     A     G     T     T     G
  ##  G     T        T    C    G     G     G     G     T     G
  ##  GR    T        T    C    G     G     G     G     T     A
  ##  GH    T        T    C    G     G     T     G     T     G

  strains_df %>%
    gather(key='pos', value='alt', -clade) %>%
    group_by(clade) %>%
    group_data %>%
    print

  ## scores_list <- list()
  ## for (strain in c("A1a", "A1b", "A1c", "A1d", "A2", "A2a1", "A2a2", "A2a3", "A3", "A6", "B", "B1", "B4")) {
  ##   strain_ref <- strains_df[[strain]]
  ##   scores <- if_else(out_df$alt == 'Z', 0.5, if_else(strain_ref != out_df$alt, 1, 0))
  ##   scores_list[[strain]] <- sum(scores)
  ## }
  ## score_df <- scores_list %>% enframe('strain', 'score') %>% unnest(score)
  ## score_df %>% write_csv(args[5])
  ## ## strain score
  ## ## A1a    3
  ## ## A1b    1
  ## ## A1c    1
  ## ## A1d    1
  ## ## A2     4
  ## ## A2a1   8
  ## ## A2a2   5
  ## ## A2a3   7
  ## ## A3     0
  ## ## A6     1
  ## ## B      2
  ## ## B1     6
  ## ## B4     2

  ## write_csv(out_df, args[6], na='')
}


