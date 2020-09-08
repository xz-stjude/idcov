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

  ## # A tibble: 120 x 4
  ##    id             clade manhattan sample_id
  ##    <chr>          <chr>     <int> <chr>
  ##  1 gisaid         L             1 SA01
  ##  2 gisaid         G             2 SA01
  ##  3 gisaid         GH            3 SA01
  ##  4 gisaid         GR            3 SA01
  ##  5 gisaid         S             3 SA01
  ##  6 gisaid         V             3 SA01
  ##  7 new_nextstrain 19A           1 SA01
  ##  8 new_nextstrain 19B           3 SA01
  ##  9 new_nextstrain 20A           4 SA01
  ## 10 new_nextstrain 20C           5 SA01
  ## # … with 110 more rows

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
