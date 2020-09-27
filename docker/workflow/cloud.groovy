nextflow.preview.dsl=2

reference_indexes = Channel.fromPath("${params.reference}.*").collect()
reference_indexes.view()

ch_fagz = Channel.fromFilePairs(params.path_to_fastq_gzipped)


process bwa {
    input:
    file _
    tuple val(sample_id), file(reads)

    output:
    tuple val(sample_id), file("${sample_id}.bam")

    publishDir params.result_folder

    script:
    """
			bwa mem -t ${params.threads} ${params.reference} $reads -R "@RG\\tID:${sample_id}\\tSM:${sample_id}\\tPL:ILLUMINA\\tPI:0" \
        | samtools sort -@${params.threads} -o ${sample_id}.bam -T ${sample_id}.temp -
    """
}

process samtools_index {
    input:
    tuple val(sample_id), file("${sample_id}.bam")

    output:
    tuple val(sample_id), file("${sample_id}.bam"), file("${sample_id}.bam.bai")

    publishDir params.result_folder

    script:
    """
      samtools index ${sample_id}.bam
    """
}

process freebayes {
    input:
    file(_)
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("${sample_id}_mutations.csv")

    publishDir params.result_folder

    script:
    """
      freebayes \\
        --ploidy 1 \\
        --min-mapping-quality 10 \\
        --min-base-quality 15 \\
        --min-coverage 10 \\
        --min-alternate-count 5 \\
        --region MN908947.3 \\
        --fasta-reference ${params.reference} \\
        --vcf ${sample_id}.vcf \\
        ${sample_id}.bam
      ln -sf ${baseDir}/bin/process_freebayes_results.r ./
      Rscript process_freebayes_results.r ${sample_id}
    """
}


process bedtools {
    input:
    tuple sample_id, file(_), file(_)

    output:
    // tuple sample_id, file("${sample_id}.bedgraph") into ch_bedgraph
    tuple sample_id, file("${sample_id}_uncovered_intervals.bedgraph")

    publishDir params.result_folder

    script:
    """
      bedtools genomecov -ibam ${sample_id}.bam -bga | grep '^MN908947' > ${sample_id}.bedgraph
      awk '\$4 < 1' ${sample_id}.bedgraph > ${sample_id}_uncovered_intervals.bedgraph
    """
}


process get_coverage_of_markers {
    input:
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("${sample_id}_coverage.csv")

    publishDir params.result_folder

    script:
    """
      bedtools coverage -a ${params.markers_bed} -b ${sample_id}.bam > ./${sample_id}_coverage.bed
      ln -sf ${baseDir}/bin/process_bedtools_results.r ./
      Rscript process_bedtools_results.r ${sample_id}
    """
}


process compare_mutations {
    input:
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("${sample_id}_markers.csv"), file("${sample_id}_scores_vs_strains.csv")

    publishDir params.result_folder

    script:
    """

      ln -sf ${baseDir}/bin/merge_mutations.r ./
      Rscript merge_mutations.r ${sample_id} ${params.marker_ref} ${params.strain_ref_prefix} ${baseDir}/refs/naming_systems.csv
    """
}


process collect_all_samples {
    input:
    file _

    output:
    file "all_samples*.csv"

    publishDir params.result_folder

    script:
    """
      ln -sf ${baseDir}/bin/combine_samples.r ./
      Rscript combine_samples.r
    """
}


process generate_report {
    input:
    file _

    output:
    file "index.html"

    publishDir params.result_folder

    script:
    """
      cp -f ${baseDir}/refs/generate_report.Rmd ./
      Rscript -e "rmarkdown::render('generate_report.Rmd', output_format='html_document', output_dir='.', output_file='index.html', params=list(base_dir='${baseDir}'))"
    """
}

workflow {
    bwa(reference_indexes, ch_fagz)
    samtools_index(bwa.out)
    freebayes(reference_indexes, samtools_index.out)
    bedtools(samtools_index.out)
    get_coverage_of_markers(samtools_index.out)
    compare_mutations(get_coverage_of_markers.out.join(freebayes.out))
    collect_all_samples(compare_mutations.out.collect())
    generate_report(collect_all_samples.out)
    println("All DONE! Please check the generated Report.")
}

