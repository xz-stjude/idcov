// -*- mode: groovy; -*-

nextflow.preview.dsl=2

process generate {
    output:
    file "foo.bin"

    script:
    """
      sleep 1
      head -c4096 /dev/urandom > foo.bin
    """
}

process base64 {
    input:
    file foo_bin

    output:
    file "foo.txt"

    script:
    """
      sleep 1
      base64 ${foo_bin} > foo.txt
    """
}

process sort {
    input:
    file foo

    output:
    file "foo_sorted.txt"

    publishDir 'results'

    script:
    """
      sleep 1
      sort ${foo} > foo_sorted.txt
    """
}


workflow {
    generate()
    base64(generate.out)
    sort(base64.out)
}
