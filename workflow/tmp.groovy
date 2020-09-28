ch_fagz = Channel.fromFilePairs("/rgs01/project_space/cab/automapper/common/yhui/Ti-Cheng/LAB/Webby/StrainTyping/19*/*_R{1,2}.fastq.gz")
ch_fagz.filter {a -> ["4-2-2020_Random_SNAP_S2", "4-2-2020_GeneSpecific_SNAP_S3"].every {!(a[0] =~ it)}}
       .view()
