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
    df <- read_csv(
        score_filename,
        col_types = cols(
            id = col_character(),
            clade = col_character(),
            manhattan = col_integer()
        )
    ) %>%
      mutate(sample_id=str_match(score_filename, '^./(.*)_scores_vs_strains.csv$')[,2])

    ## df <- read_csv(
    ##     score_filename,
    ##     col_types = cols(
    ##         pos = col_integer(),
    ##         n_covering_frags = col_double(),
    ##         ref = col_character(),
    ##         alt = col_character(),
    ##         is_mutated = col_character()
    ##     )
    ## )
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

score_df <-
  bind_rows(score_df_list) %>%

## # A tibble: 24 x 4
##    id    clade manhattan sample_id
##    <chr> <chr>     <int> <chr>
##  1 NT05  L             1 NT05
##  2 NT05  V             1 NT05
##  3 NT05  S             3 NT05
##  4 NT05  G             4 NT05
##  5 NT05  GH            5 NT05
##  6 NT05  GR            5 NT05
##  7 NT05  19A           0 NT05
##  8 NT05  19B           2 NT05
##  9 NT05  20A           5 NT05
## 10 NT05  20C           6 NT05
## 11 NT05  20B           7 NT05
## 12 NT05  A3            0 NT05
## 13 NT05  A1b           1 NT05
## 14 NT05  A1c           1 NT05
## 15 NT05  A1d           1 NT05
## 16 NT05  A6            1 NT05
## 17 NT05  B             2 NT05
## 18 NT05  B4            2 NT05
## 19 NT05  A1a           3 NT05
## 20 NT05  A2            4 NT05
## 21 NT05  A2a2          5 NT05
## 22 NT05  B1            6 NT05
## 23 NT05  A2a3          7 NT05
## 24 NT05  A2a1          8 NT05

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
