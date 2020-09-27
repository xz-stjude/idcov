cov:
	@echo "Downloading the virus genome MN908947.3.fa ..."
	wget -O MN908947.3.fa 'https://www.ncbi.nlm.nih.gov/sviewer/viewer.cgi?tool=portal&save=file&log$=seqview&db=nuccore&report=fasta&id=1798172431&extrafeat=null&conwithfeat=on&hide-cdd=on'
	@echo "Downloading the human genome hg38 ..."
	wget -O - 'https://hgdownload.soe.ucsc.edu/goldenPath/hg38/bigZips/hg38.fa.gz' | gunzip > hg38.fa
	@echo "Concatenating the virus genome with the human genome ..."
	cat MN908947.3.fa hg38.fa > MN908947.3_hg38.fa
	@echo "Index the fasta file ..."
	bwa index MN908947.3_hg38.fa
	@echo "Done!"
