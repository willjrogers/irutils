#-*-tcl-*-

# Procedure: binsearch
#  Disk based binary search implementation
# params:
#   bsfp:       file pointer for binary search table
#   term:       search term
#   termlen:    entry term length
#   numrecs:    number of records in table
#
# returns:
#   Two element Tcl list containing word and weight associated with it
#   or empty list if term not found.
proc binsearch { bsfp term termlen numrecs } {
  set sizeof_double 9
  set low 0
  set high $numrecs
  
  while { $low < $high } {
    set mid [expr $low + ($high- $low) / 2]
    
    seek $bsfp [expr $mid * ($termlen+$sizeof_double)] start 
    set tstterm [read $bsfp $termlen]
    set cond [string compare $term $tstterm]
    if {$cond < 0} {
      set high $mid
    } elseif { $cond > 0 } {
      set low [expr $mid + 1]
    } else {
      binary scan [read $bsfp $sizeof_double] "d" weight
      return [list $term $weight]
    }
  }
  return {}
}


proc install { file dest { verbose 0 } } {
  set basename [file tail $file]
  if { [file exists $dest/$basename] == 0 ||
       [file mtime $file] > [file mtime $dest/$basename] } {
    exec cp $file $dest
    puts "copied $file to $dest/$basename"
  } elseif { $verbose } {
    puts stderr "$file is older than $dest/$basename"
    puts stderr [exec ls -l $file]
    puts stderr [exec ls -l $dest/$basename]
  }
}
