#!/usr/bin/env python

import fire

def bwa(n_threads, reference, r1, r2):
    print(f"n_threads = {n_threads}")
    print(f"reference = {reference}")
    print(f"r1 = {r1}")
    print(f"r2 = {r2}")

if __name__ == "__main__":
    fire.Fire()
