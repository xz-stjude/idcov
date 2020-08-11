nextflow.preview.dsl=2

reference_indexes = Channel.fromPath("${params.reference}.*").collect()
ch_fagz = Channel.fromFilePairs(params.path_to_fastq_gzipped)
                 .filter {a -> params.blacklist.every {!(a[0] =~ it)}}


process bwa {

  conda 'python=3'

    input:
    file _
    tuple val(sample_id), file(reads)

    output:
    tuple val(sample_id), file("${sample_id}.bam")

    publishDir 'results'

    script:
    """
      mock.py bwa ${params.threads} ${params.reference} ${reads}
    """
}


// process collect_all_samples {

//   conda 'r::r r::r-tidyverse'

//     input:
//     file _

//     output:
//     file "all_samples*.csv"

//     publishDir 'results'

//     script:
//     """
//       combine_samples.r
//     """
// }


workflow {
    bwa(reference_indexes, ch_fagz)
    // collect_all_samples(compare_mutations.out.collect())
}
