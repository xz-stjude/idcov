// -*- mode: groovy; -*-

nextflow.preview.dsl=2

// TODO: rename these processes and channels

// params.path = "/research/sharedservices/gsf/runs/*/Unaligned/webbygrp_000001_COVID_Nextera_XT"
// params.reference = "./3-reference_genome/hg38_SARScoV2.fa"
// params.virus_ref = "./3-reference_genome/sars-cov-2.fa"
// params.threads = 1

// // Channel.fromFilePairs('./12-bams/*.bam{,.bai}', size: -1) { file -> file.name.replaceAll(/.bam|.bai$/,'') }
// //     .into { ch_bams; ch_bams_1; ch_bams_3 }

// // reference = './3-reference_genome/hg38_SARScoV2.fa'
// reference = file('./3-reference_genome/hg38_SARScoV2.fa')
// reference_indexes = Channel.fromPath('./3-reference_genome/hg38_SARScoV2.fa.*').collect()
// virus_ref = file("./3-reference_genome/sars-cov-2.fa")
// markers_bed = file("./6-markers/markers.bed")
// // ch_fagz = Channel.fromFilePairs("./1-webbygrp_covid/*/*_R{1,2}_*.fastq.gz")
// ch_fagz = Channel.fromFilePairs("./8-zhou_et_al/SRR11772359_{1,2}_head100000.fastq")
// marker_ref = file("./6-markers/markers.csv")
// strain_ref = file("./7-strains/strains.txt")


// on St. Jude HPC (LSF)
params.reference = "/rgs01/project_space/cab/automapper/common/yhui/Ti-Cheng/VirusREF/Human_SARS_CoV2/hg38_SARScoV2.fa"
params.virus_ref = "/rgs01/project_space/cab/automapper/common/yhui/Ti-Cheng/VirusREF/Human_SARS_CoV2/SARS-CoV2"
params.threads = 4

reference = file('/rgs01/project_space/cab/automapper/common/yhui/Ti-Cheng/VirusREF/Human_SARS_CoV2/hg38_SARScoV2.fa')
reference_indexes = Channel.fromPath('/rgs01/project_space/cab/automapper/common/yhui/Ti-Cheng/VirusREF/Human_SARS_CoV2/hg38_SARScoV2.fa.*').collect()
virus_ref = file("/rgs01/project_space/cab/automapper/common/yhui/Ti-Cheng/VirusREF/Human_SARS_CoV2/SARS-CoV2")
markers_bed = file("./6-markers/markers.bed")
ch_fagz = Channel.fromFilePairs("/rgs01/project_space/cab/automapper/common/yhui/Ti-Cheng/LAB/Webby/StrainTyping/19*/*_R{1,2}.fastq.gz")
marker_ref = file("./6-markers/markers.csv")
strain_ref = file("./7-strains/strains.txt")


// process splitBam {

//     input:
//     tuple sample_id, file(bam) from ch_bams

//     output:
//     tuple sample_id, file("${sample_id}_cov.bam") into splitBam, splitBam2

//     publishDir 'results'

//     script:
//     """
//       bamtools split -in ${sample_id}.bam -reference
//       f=( "*MN908947.3*.bam" )
//       mv \${f[0]} ${sampleId}_cov.bam
//     """
// }

// splitBam.into {splitBam1, splitBam2}

process bwa {

  conda 'bioconda::bwa bioconda::samtools'
  cpus 4
  queue 'short'

    input:
    file(reference_fa)
    file(_)
    tuple val(sample_id), file(reads)

    output:
    tuple val(sample_id), file("${sample_id}.bam")

    publishDir 'results'

    script:
    """
			bwa mem -t ${params.threads} ${reference_fa} $reads -R "@RG\\tID:${sample_id}\\tSM:${sample_id}\\tPL:ILLUMINA\\tPI:0" \
        | samtools sort -@${params.threads} -o ${sample_id}.bam -T ${sample_id}.temp -
    """
}

process samtools_index {

  conda 'bioconda::samtools'

    input:
    tuple val(sample_id), file("${sample_id}.bam")

    output:
    tuple val(sample_id), file("${sample_id}.bam"), file("${sample_id}.bam.bai")

    publishDir 'results'

    script:
    """
      samtools index ${sample_id}.bam
    """
}

process freebayes {

  conda 'bioconda::freebayes'

    input:
    file(reference_fa)
    file(_)
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("${sample_id}.vcf")

    publishDir 'results'

    script:
    """
    freebayes -f ${reference_fa} --ploidy 1 ${sample_id}.bam -r MN908947.3 -C 1 -v ${sample_id}.vcf
    """
}


process bedtools {

  conda 'bioconda::bedtools'

    input:
    tuple sample_id, file(_), file(_)

    output:
    // tuple sample_id, file("${sample_id}.bedgraph") into ch_bedgraph
    tuple sample_id, file("${sample_id}_uncovered_intervals.bedgraph")

    publishDir 'results'

    script:
    """
      bedtools genomecov -ibam ${sample_id}.bam -bga | grep '^MN908947' > ${sample_id}.bedgraph
      awk '\$4 < 1' ${sample_id}.bedgraph > ${sample_id}_uncovered_intervals.bedgraph
    """
}


process get_coverage_of_markers {

  conda 'bioconda::bedtools'

    input:
    file markers_bed
    tuple sample_id, file(_), file(_)

    output:
    tuple sample_id, file("./${sample_id}_coverage.bed")

    publishDir 'results'

    script:
    """
      bedtools coverage -a ${markers_bed} -b ${sample_id}.bam > ./${sample_id}_coverage.bed
    """
}


process compare_mutations {

  conda 'bioconda::r bioconda::r-tidyverse'

    input:
    tuple sample_id, file(_), file(_)
    file marker_ref
    file strain_ref

    output:
    tuple sample_id, file("${sample_id}_markers.csv"), file("${sample_id}_scores_vs_strains.csv")

    publishDir 'results'

    script:
    """
      merge_mutations.r ${sample_id}_coverage.bed ${sample_id}.vcf ${marker_ref} ${strain_ref} ${sample_id}_scores_vs_strains.csv ${sample_id}_markers.csv
    """
}


process collect_all_samples {

  conda 'bioconda::r bioconda::r-tidyverse'

    input:
    file _

    output:
    file "all_samples*.csv"

    publishDir 'results'

    script:
    """
      combine_samples.r
    """
}

// process hello {
//   """
//     echo "Hello!"
//   """
// }

// workflow {
//   hello()
// }

workflow {
    bwa(reference, reference_indexes, ch_fagz)
    samtools_index(bwa.out)
    freebayes(reference, reference_indexes, samtools_index.out)
    bedtools(samtools_index.out)
    get_coverage_of_markers(markers_bed, samtools_index.out)
    compare_mutations(get_coverage_of_markers.out.join(freebayes.out), marker_ref, strain_ref)
    collect_all_samples(compare_mutations.out.collect())
}

// process vcflib {

//     input:
//     tuple sample_id, file(bedgraph), file(vcf) from ch_bedgraph.join(ch_vcfs)

//     output:
//     tuple sample_id, file("${sample_id}_masked.fa") into ch_vcflib

//     publishDir 'results'

//     script:
//     """
//       vcf2fasta -f ${virus_ref} -P 1 ${sample_id}.vcf -p vcf2fasta
//       fa=( "vcf2fasta*.fa" )
//       mv \${fa[0]} ${sample_id}.fa
//       bedtools maskfasta -fi ${sample_id}.fa -fo ${sample_id}_masked.fa -bed ${sample_id}_uncovered_intervals.bedgraph
//     """
// }



// bedtools genomecov -ibam $bam -bga > ${bam}.bedgraph
// awk '$4 < 1' ${bam}.bedgraph > ${bam}.bedgraph.uncovered.intevals

// ###freebayes -f COVID19.Sprotein.fasta -p 1 Sprotein.pangolin.merged.bam -C 1 > calls.vcf

// module load vcflib
// vcf2fasta -f $ref -P 1 $vcf -p vcf2fasta
// fa=$(ls vcf2fasta*.fasta)
// #mv SRR10168377_NC_045512.2:21563-25384:0.fasta cns.fa

// bedtools maskfasta -fi $fa -fo vcf2fasta.cns.masked.fa -bed ${bam}.bedgraph.uncovered.intevals


// freebayes -f $reference --ploidy 1 ${sample}.bam -r MN908947.3 -C 1 -v virusC1.vcf
