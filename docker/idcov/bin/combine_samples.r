#!/usr/bin/env Rscript

library(tidyverse)
library(glue)
library(tidyr)

args <- commandArgs(trailingOnly = TRUE)

filenames <- Sys.glob("./*_markers.csv")

## filenames <- Sys.glob("./results/*_markers.csv")

ids <- str_replace(filenames, "^\\.\\/(.*)_markers\\.csv$", "\\1")

mutation_df_list <- list()
score_df_list <- list()
for (i in seq_len(length(ids))) {
    sample_id <- ids[[i]]

    mutations_filename <- glue("./{sample_id}_markers.csv")
    df <-
      read_csv(
        mutations_filename,
        col_types = cols(
          pos = col_integer(),
          n_covering_frags = col_double(),
          ref = col_character(),
          alt = col_character(),
          is_mutated = col_character()
        )
      ) %>%
      mutate(sample_id)
    ## df$mutations_filename <- mutations_filename
    mutation_df_list[[i]] <- df

    score_filename <- glue("./{sample_id}_scores_vs_strains.csv")
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
    df <-
      read_csv(
        score_filename,
        col_types = cols(
          id = col_character(),
          clade = col_character(),
          manhattan = col_double()
        )
      ) %>%
      mutate(sample_id)

    score_df_list[[i]] <- df
}

mutation_df <- bind_rows(mutation_df_list)
mutation_df %>% write_csv("./all_samples.csv", na = "")

mutation_df_wide <-
  mutation_df %>%
  mutate(summary = alt) %>%
  select(pos, summary, sample_id) %>%
  spread(key = pos, value = summary) %>%
  write_csv("./all_samples_marker_profiles.csv", na = "")

mutation_df_wide <-
  mutation_df %>%
  mutate(summary = n_covering_frags) %>%
  select(pos, summary, sample_id) %>%
  spread(key = pos, value = summary) %>%
  write_csv("./all_samples_marker_coverages.csv", na = "")

score_df <-
  bind_rows(score_df_list) %>%
  ## id             clade manhattan sample_id
  ## gisaid         L             1 SA01
  ## gisaid         G             2 SA01
  ## gisaid         GH            3 SA01
  ## gisaid         GR            3 SA01
  ## gisaid         S             3 SA01
  ## gisaid         V             3 SA01
  ## new_nextstrain 19A           1 SA01
  ## new_nextstrain 19B           3 SA01
  ## new_nextstrain 20A           4 SA01
  ## new_nextstrain 20C           5 SA01
  write_csv("./all_samples_score_df.csv", na='')

##   group_by(id) %>%
##   filter(min_rank(score) == 1) %>%
##   summarize(
##       min_score = min(score),
##       call = if_else(
##           min(score) >= 4,
##           "-",
##           str_c(sprintf("%s(%s)", strain, score),
##               collapse = " or "
##           )
##       )
##   )

## score_df %>% write_csv("./all_samples_score_df.csv", na = "")
