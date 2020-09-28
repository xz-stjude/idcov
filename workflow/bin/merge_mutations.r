#!/usr/bin/env Rscript

# TODO: NEXT: fixed the ...markers.csv: add marker profiles

MIN_FRAGS_TO_CONFIRM_MUTATION <- 8

library(tidyverse)

args <- commandArgs(trailingOnly = TRUE)
SAMPLE_ID <- args[1]
MARKER_REF <- args[2]
STRAIN_REF_PREFIX <- args[3]
NAMING_SYSTEMS <- args[4]

df_markers <-
  read_csv(str_c(SAMPLE_ID, "_coverage.csv")) %>%
  select(pos = end, n_covering_frags) %>%
  print()

df_mutations <-
  read_csv(
    str_c(SAMPLE_ID, "_mutations.csv"),
    col_types = cols(
      pos = col_integer(),
      ref = col_character(),
      alt = col_character(),
      pos_c = col_integer(),
      ref_c = col_character(),
      alt_c = col_character()
    )
  ) %>%
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
  select(pos=pos_c, ref=ref_c, alt=alt_c) %>%
  ## pos   ref alt
  ## 241   C   T
  ## 1059  C   T
  ## 3037  C   T
  ## 14408 C   T
  ## 21073 G   A
  ## 23403 A   G
  ## 25563 G   T
  ## 25564 G   A
  ## 25565 G   C
  ## 27964 C   T
  mutate(is_mutated='yes') %>%
  print()

marker_ref <-
  read_csv(
    MARKER_REF,
    col_types = cols(
      pos = col_integer(),
      ref = col_character()
    )
  ) %>%
  select(pos, original_ref=ref) %>%
  print

out_df <- df_markers %>%
  left_join(df_mutations) %>%
  left_join(marker_ref) %>%
  mutate(alt=if_else(n_covering_frags < MIN_FRAGS_TO_CONFIRM_MUTATION, '?', if_else(is.na(is_mutated), original_ref, alt))) %>%
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
  write_csv(str_c(SAMPLE_ID, "_markers.csv"), na='')


##--------------------------------------------------------------------------------


work <- function(ns_id) {
  read_csv(str_c(STRAIN_REF_PREFIX, "_", ns_id, ".csv")) %>%
    ##  clade 241 1059 3037 8782 11083 23403 25563 26144 28144 28882
    ##  L     C        C    C    G     A     G     G     T     G
    ##  S     C        C    T    G     A     G     G     C     G
    ##  V     C        C    C    T     A     G     T     T     G
    ##  G     T        T    C    G     G     G     G     T     G
    ##  GR    T        T    C    G     G     G     G     T     A
    ##  GH    T        T    C    G     G     T     G     T     G
    gather(key='pos', value='alt', -clade) %>%
    mutate(pos=as.integer(pos)) %>%
    ## 20B   241   T
    ## 20B   1059  C
    ## 20B   3037  T
    ## 20B   8782  C
    ## 20B   14408 T
    ## 20B   23403 G
    ## 20B   25563 G
    ## 20B   28144 T
    ## 20B   28881 A
    ## 20B   28882 A
    ## 20B   28883 C
    left_join(out_df %>% select(pos, alt), by=c("pos"), suffix=c("_strain", "_sample")) %>%
    mutate(delta=if_else(alt_sample == '?', 0.5, if_else(alt_sample != alt_strain, 1, 0))) %>%
    group_by(clade) %>%
    summarize(manhattan=sum(delta)) %>%
    print %>%
    list
}

naming_system_df <-
  read_csv(NAMING_SYSTEMS) %>%
  ## naming_system  id              link_to_nextstrain
  ## GISAID         gisaid          https://nextstrain.org/ncov/global?c=GISAID_clade
  ## Old Nextstrain old_next_strain https://nextstrain.org/ncov/global?c=legacy_clade_membership
  ## New Nextstrain new_next_strain https://nextstrain.org/ncov/global?c=clade_membership
  select(id) %>%
  rowwise %>%
  mutate(data=work(id)) %>%
  unnest(data) %>%
  ## id             clade manhattan
  ## gisaid         G             4
  ## gisaid         GH            5
  ## gisaid         GR            5
  ## gisaid         L             1
  ## gisaid         S             3
  ## gisaid         V             1
  ## old_nextstrain A1a           3
  ## old_nextstrain A1b           1
  ## old_nextstrain A1c           1
  ## old_nextstrain A1d           1
  ## old_nextstrain A2            4
  ## old_nextstrain A2a1          8
  ## old_nextstrain A2a2          5
  ## old_nextstrain A2a3          7
  ## old_nextstrain A3            0
  ## old_nextstrain A6            1
  ## old_nextstrain B             2
  ## old_nextstrain B1            6
  ## old_nextstrain B4            2
  ## new_nextstrain 19A           0
  ## new_nextstrain 19B           2
  ## new_nextstrain 20A           5
  ## new_nextstrain 20B           7
  ## new_nextstrain 20C           6
  arrange(id, manhattan) %>%
  write_csv(str_c(SAMPLE_ID, "_scores_vs_strains.csv"), na='')
