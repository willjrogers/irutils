# -*-tcl-*-

# Procedure: word_map 
#  Generate word map 
# Params
#   filename  lisp file containg normalized terms
#   mapname   directory to place partitons and the name of partition map.
#   debug (optional) set to 1 if you want debuging
#
proc word_map { filename mapname { debug 0 } } {
 
  file mkdir $mapname

  set wordnum 0
  time {
    set fp [open $filename "r"]
    while {[gets $fp line] >= 0} {
      set list [split $line]
      set key [string trim [lindex $list 1] "\""]
      set value [string trimright [lindex $list 2] ")"]
      set wordlen [string length $key]
      if { $wordlen > 0 } {
	set hash${wordlen}($key) $value
      }
      incr wordnum
    }
    close $fp
  }
  puts "$wordnum words hashed."
  set hashlist [info locals hash*]
  puts "[llength $hashlist] hash partitions created"

  foreach hash $hashlist {
    puts "$hash: length: [llength [array names $hash]]"
  }

  puts "generating partitions in $mapname from file $filename"

  # write partitions in human readable format
  if { $debug == 1 } {
    puts "writing partitions in human readable format..."
    foreach hash $hashlist {
      set partfp [open $mapname/$hash "w"]
      foreach term [lsort -dictionary [array names $hash]] {
	puts $partfp "$term [set ${hash}($term)]"
      }
      close $partfp
    }
  }

  puts "writing partitions..."
  # write partitions with corresponding statistics 
  set statfp [open "$mapname/partition.stats" "w"]
  puts $statfp "\# $mapname/partition.log -- word_map.tcl status file"
  puts $statfp "\# total number of terms: $wordnum"
  puts $statfp "\#"
  puts $statfp "\# table format: "
  puts $statfp "\#  partition_filename termlen nterms"

  set rcfp [open "$mapname/mapinfo.tcl" "w"]
  puts $rcfp "\# quick load partition map for Tcl"
  puts $rcfp "\# format: "
  puts $rcfp "\#  set partition(<term length>) { partitionfilename numofrecords }"

  foreach hash $hashlist {
    # given hash table of terms of length n, write in binary format.
    set partfp [open "$mapname/partition_$hash" "w"]
    fconfigure $partfp -translation binary -encoding binary
    foreach term [lsort -dictionary [array names $hash]] {
      set len [string length $term]
      puts $partfp [binary format "a$len d1" $term [set ${hash}($term)]]
    }
    close $partfp
    puts $statfp "partition_$hash $len [llength [array names $hash]]"
    puts $rcfp "set partition($mapname,$len) { partition_$hash [llength [array names $hash]] }"
  } 
  close $statfp

  puts "done"
}

# code to read word maps created with word_map.tcl

# Procedure: lookup_term
#
# Given a word map, lookup up based on term length and ordinal value
# using binary search partitions.
# 
proc lookup_term { mapname term } {
  global partd
  global partition

  set len [string length $term]
  if {[info exists partition($mapname,$len)] != 0 } {
    if {[info exists partd($mapname,$len)] == 0 } {
      if [file exists $mapname/[lindex $partition($mapname,$len) 0]] {
	set partd($mapname,$len) [open "$mapname/[lindex $partition($mapname,$len) 0]" "r"]
	fconfigure $partd($mapname,$len) -translation binary -encoding binary
      } else {
	return {}
      }
    } 
    return [binsearch $partd($mapname,$len) $term $len [lindex $partition($mapname,$len) 1]]
  }
  return {}
}

# Procedure: binsearch
#  Disk based binary search implementation
#
proc binsearch { bsfp word recordlen numrecs } {
  set sizeof_double 9
  set low 0
  set high $numrecs

  while { $low < $high } {
    set mid [expr $low + ($high- $low) / 2]
    
    seek $bsfp [expr $mid * ($recordlen+$sizeof_double)] start 
    set tstword [read $bsfp $recordlen]
    set cond [string compare $word $tstword]
    if {$cond < 0} {
      set high $mid
    } elseif { $cond > 0 } {
      set low [expr $mid + 1]
    } else {
      binary scan [read $bsfp $sizeof_double] "d" weight
      return [list $word $weight]
    }
  }
  return {}
}

proc setup_word_map { mapname } {
  source $mapname/mapinfo.tcl
}
