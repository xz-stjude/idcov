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
    pos = col_integer()
  )
) %>%
  select(pos, ref, alt) %>%
  mutate(is_mutated='yes') %>%
  print()

marker_ref <-
  ## read_csv('./6-markers/markers.csv') %>%
  read_csv(args[3],
           col_types = cols(
             pos = col_integer()
           )) %>%
  select(pos, original_ref=ref) %>%
  print

out_df <-
  left_join(df_markers, df_mutations) %>%
  left_join(marker_ref) %>%
  mutate(alt=if_else(n_covering_frags < MIN_FRAGS_TO_CONFIRM_MUTATION, 'Z', if_else(is.na(is_mutated), original_ref, alt))) %>%
  mutate(ref=original_ref) %>%
  print

strains_df <-
  ## read_tsv('./7-strains/strains.txt') %>%
  read_tsv(args[4]) %>%
  print

scores_list <- list()
for (strain in c("A1a", "A1b", "A1c", "A1d", "A2", "A2a1", "A2a2", "A2a3", "A3", "A6", "B", "B1", "B4")) {
  strain_ref <- strains_df[[strain]]
  scores <- if_else(out_df$alt == 'Z', 0.5, if_else(strain_ref != out_df$alt, 1, 0))
  scores_list[[strain]] <- sum(scores)
}
score_df <- scores_list %>% enframe('strain', 'score') %>% unnest(score)
score_df %>% write_csv(args[5])

write_csv(out_df, args[6], na='')
