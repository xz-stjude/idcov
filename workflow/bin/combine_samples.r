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
    id <- ids[[i]]

    mutations_filename <- glue("./{id}_markers.csv")
    df <- read_csv(
        mutations_filename,
        col_types = cols(
            pos = col_integer(),
            n_covering_frags = col_double(),
            ref = col_character(),
            alt = col_character(),
            is_mutated = col_character()
        )
    )
    df$id <- ids[[i]]
    ## df$mutations_filename <- mutations_filename
    mutation_df_list[[i]] <- df

    score_filename <- glue("./{id}_scores_vs_strains.csv")
    df <- read_csv(
        score_filename,
        col_types = cols(
            pos = col_integer(),
            n_covering_frags = col_double(),
            ref = col_character(),
            alt = col_character(),
            is_mutated = col_character()
        )
    )
    df$id <- ids[[i]]
    ## df$score_filename <- score_filename
    score_df_list[[i]] <- df
}

mutation_df <- bind_rows(mutation_df_list)
mutation_df %>% write_csv("./all_samples.csv", na = "")

mutation_df_wide <- mutation_df %>%
    mutate(summary = if_else(is_mutated == "yes" & alt != "Z",
        sprintf("%s (%s > %s)", n_covering_frags, ref, alt),
        sprintf("%s", n_covering_frags),
        missing = sprintf("%s", n_covering_frags)
    )) %>%
    select(pos, summary, id) %>%
    spread(key = pos, value = summary) %>%
    print()

mutation_df_wide %>% write_csv("./all_samples_mutation_wide.csv", na = "")

## strain score id                  $
## A1a    4.5   10_Rabeh1_S1_L001   $
## A1b    2.5   10_Rabeh1_S1_L001   $
## A1c    2.5   10_Rabeh1_S1_L001   $
## A1d    2.5   10_Rabeh1_S1_L001   $
## A2     4.5   10_Rabeh1_S1_L001   $
## A2a1   8.5   10_Rabeh1_S1_L001   $
## A2a2   5.5   10_Rabeh1_S1_L001   $
## A2a3   7.5   10_Rabeh1_S1_L001   $
## A3     3.5   10_Rabeh1_S1_L001   $
## A6     2.5   10_Rabeh1_S1_L001   $
## B      2.5   10_Rabeh1_S1_L001   $
## B1     4.5   10_Rabeh1_S1_L001   $
## B4     2.5   10_Rabeh1_S1_L001   $
## A1a    4     11_Rabeh2_S5_L001   $
## A1b    2     11_Rabeh2_S5_L001   $
## A1c    2     11_Rabeh2_S5_L001   $
## A1d    2     11_Rabeh2_S5_L001   $
## A2     5     11_Rabeh2_S5_L001   $

score_df <-
    bind_rows(score_df_list) %>%
    group_by(id) %>%
    filter(min_rank(score) == 1) %>%
    summarize(
        min_score = min(score),
        call = if_else(
            min(score) >= 4,
            "-",
            str_c(sprintf("%s(%s)", strain, score),
                collapse = " or "
            )
        )
    )

score_df %>% write_csv("./all_samples_score_df.csv", na = "")
