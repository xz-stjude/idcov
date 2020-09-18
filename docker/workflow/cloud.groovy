nextflow.preview.dsl=2

reference_indexes = Channel.fromPath("${params.reference}.*").collect()
reference_indexes.view()

ch_fagz = Channel.fromFilePairs(params.path_to_fastq_gzipped)


process bwa {

  conda 'bioconda::bwa bioconda::samtools conda-forge::openssl=1.0 conda-forge::ncurses=5.9'

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

  conda 'bioconda::samtools conda-forge::openssl=1.0 conda-forge::ncurses=5.9'

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

  conda 'bioconda::freebayes'

    input:
    file(_)
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("${sample_id}.vcf")

    publishDir params.result_folder

    script:
    """
    freebayes -f ${params.reference} --ploidy 1 ${sample_id}.bam -r MN908947.3 -C 1 -v ${sample_id}.vcf
    """
}


process bedtools {

  conda 'bioconda::bedtools'

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

  conda 'bioconda::bedtools'

    input:
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("./${sample_id}_coverage.bed")

    publishDir params.result_folder

    script:
    """
      bedtools coverage -a ${params.markers_bed} -b ${sample_id}.bam > ./${sample_id}_coverage.bed
    """
}


process compare_mutations {

  conda 'conda-forge::icu conda-forge::r-base conda-forge::r-stringi conda-forge::r-tidyverse=1.3.0'

    input:
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("${sample_id}_markers.csv"), file("${sample_id}_scores_vs_strains.csv")

    publishDir params.result_folder

    script:
    """
      ln -s ${baseDir}/bin/merge_mutations.r ./
      Rscript merge_mutations.r ${sample_id}_coverage.bed ${sample_id}.vcf ${params.marker_ref} ${params.strain_ref_prefix} ${sample_id}_scores_vs_strains.csv ${sample_id}_markers.csv ${baseDir}/refs/naming_systems.csv
    """
}


process collect_all_samples {

  conda 'conda-forge::icu conda-forge::r-base conda-forge::r-stringi conda-forge::r-tidyverse=1.3.0 conda-forge::r-rmarkdown'

    input:
    file _

    output:
    file "all_samples*.csv"

    publishDir params.result_folder

    script:
    """
      ln -s ${baseDir}/bin/combine_samples.r ./
      Rscript combine_samples.r
    """
}


process generate_report {

  conda 'conda-forge::icu conda-forge::r-base conda-forge::r-stringi conda-forge::r-rmarkdown conda-forge::r-tidyverse=1.3.0 conda-forge::pandoc conda-forge::r-kableextra'

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
