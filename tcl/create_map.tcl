# bsp_map : binary search partitioned map
#
#
# add new logic:   if typelist of form: TXT|INT|...|INT  then use array
#                  else use inverted file.
#

# binary search paritions
# 
# * setting directory where indices reside
#
#  word_map::set_root directory (is there a better name for this)
#  defaults to current directory   
#  NOTE: should be a full path in most cases.
#
# e.g.:
#  word_map::set_root /home/wrogers/exper
#
# * index creation and updating
#  
#  word_map::create lispfile mapname
#  word_map::update lispfile mapname
#
# e.g.:
#  word_map::create /lisp/word-signal-lw.9901-12.l word-signal-lw.9901-12
#  word_map::update /lisp/word-signal-lw.9901-12.l word-signal-lw.9901-12
#
# * indexing use initialization and term lookup 
# 
#  word_map::setup mapname
#  word_map::lookup_term mapname term
# 
# e.g.:
#  word_map::setup word-signal-lw.9901-1
#  word_map::lookup_term word-signal-lw.9901-12 WESTERNBLOTTING
#
#


namespace eval create_map {
  variable root_path
  variable partd
  variable partition
  variable binformat
  variable hashlist
  variable mapformat
  variable index_org
  variable postingsd
  variable dataformat
  variable datalen
  
  set binformat(INT) "d1"
  set binformat(TXT) "a"
  set binformat(PTR) "i1"

  # set root_path to current directory
  set root_path [pwd]
  
  # Params
  #   filename  lisp file containg normalized terms
  #   mapname   directory to place partitons and the name of partition map.
  proc load_map_from_lisp { filename mapname } {
    variable hashlist
    variable mapformat
    variable wordnum
    variable keylen
    
    # format is in "filename|mapname|2|word|normwc|TXT|INT" it is expressed as a tcl list for speed
    # to output a separated list use:  `join <list> "|"`
    #
    set mapformat($mapname) [list $filename $mapname 2 word normwc TXT INT]
    set wordnum($mapname) 0
    time {
      set fp [open $filename "r"]
      while {[gets $fp line] >= 0} {
	set list [split $line]
	set key [string trim [lindex $list 1] "\""]
	set value [string trimright [lindex $list 2] ")"]
	set wordlen [string length $key]
	if { $wordlen > 0 } {
	  if { [info exists ${mapname}${wordlen}] == 0 } {
	    variable ${mapname}${wordlen}
	    set keylen(${mapname}${wordlen}) $wordlen
	  }
	  set ${mapname}${wordlen}($key) [list $key $value]
	}
	incr wordnum($mapname)
      }
      close $fp
    }
    puts "$wordnum($mapname) words hashed."
    set hashlist [info vars ${mapname}*]
    puts "[llength $hashlist] hash partitions created"

    foreach hash $hashlist {
      puts "$hash: length: [llength [array names $hash]]"
    }
  }

  #
  # load table referred to by read entry of the form:
  #   input_file|tablename|num_fields|fieldname1|...|N|fieldtype1|...|N|
  #
  proc load_map { config_entry } {
    variable hashlist
    variable mapformat
    variable wordnum
    variable keylen

    set config_list [split $config_entry "|"]
    set filename [lindex $config_list 0]
    set mapname [lindex $config_list 1]

    set mapformat($mapname) $config_list
    set wordnum($mapname) 0

    puts "loading from file $filename"
    set fp [open $filename "r"]
    while {[gets $fp line] >= 0} {
      set row [split $line "|"]
      set key [lindex $row 0]
      set wordlen [string length $key]
      if { $wordlen > 0 } {
	if { [info exists ${mapname}${wordlen}] == 0 } {
	  variable ${mapname}${wordlen}
	  set keylen(${mapname}${wordlen}) $wordlen
	}
	set ${mapname}${wordlen}($key) $row
      }
      incr wordnum($mapname)
    }
    close $fp
    set hashlist [info vars ${mapname}*]
    foreach hash $hashlist {
      puts "$hash: length: [llength [array names $hash]]"
      parray $hash
    }
  }
  
  
  # Procedure: create_map::create
  #  Generate word map from existing map in memory
  # Params
  #   mapname   directory to place partitons and the name of partition map.
  #   debug (optional) set to 1 if you want debuging
  #
  proc create { mapname { debug 0 } } {
    variable root_path
    variable binformat
    variable hashlist
    variable mapformat
    variable wordnum
    variable keylen
    
    set rowlen [lindex $mapformat($mapname) 2]
    # namelist[] = elements[3..(3 + rowlen - 1)]
    set namelist [lrange $mapformat($mapname) 3 [expr 2 + $rowlen]]
    # typelist[] = elements[(3 + rowlen)..(3 + (rowlen * 2) - 1)]
    set typelist [lrange $mapformat($mapname) [expr 3 + $rowlen] [expr 2 + $rowlen + $rowlen]]
		  
    set datalen 0
    set index_org FILEARRAY
    set postingformatlist {}
    for {set i 1} {$i < $rowlen} {incr i} {
      set fieldtype [lindex $typelist $i]
      set wformat $binformat($fieldtype)
      lappend dataformatlist $wformat
      if { [string equal $fieldtype "TXT"] } {
	set index_org INVERTEDFILE
      } else {
	incr datalen 8
      }
    }

    puts "creating index in $mapname"
    file mkdir $mapname

    # write partitions in human readable format
    if { $debug == 1 } {
      puts "writing partitions in human readable format..."
      foreach hash $hashlist {
	variable $hash
	set partfp [open $root_path/$mapname/$hash "w"]
	foreach term [lsort -dictionary [array names $hash]] {
	  puts $partfp "$term [set ${hash}($term)]"
	}
	close $partfp
      }
    }

    # if inverted file than open postings.
    switch $index_org {
      INVERTEDFILE {
	set postingsname $root_path/$mapname/postings
	puts "opening postings file: $postingsname"
	set postingsfp [open $postingsname "w"]
	fconfigure $postingsfp -translation binary -encoding binary
	set nextpost 0
	set dictdatafmt $binformat(PTR)
      } 
      FILEARRAY {
	set dictdatafmt $dataformatlist
      }
    }
    puts "generating partitions..."
    
    # write partitions with corresponding statistics 
    set statfp [open "$root_path/$mapname/partition.stats" "w"]
    puts $statfp "\# $mapname/partition.log -- create_map.tcl status file"
    puts $statfp "\# total number of terms: $wordnum($mapname)"
    puts $statfp "\#"
    puts $statfp "\# table format: "
    puts $statfp "\#  partition_filename termlen nterms"
    
    set rcfp [open "$root_path/$mapname/mapinforc.tcl" "w"]
    puts $rcfp "\# Tcl rc file for create_map."
    puts $rcfp "\#"
    puts $rcfp "\# record format:"
    puts $rcfp "\#   [join $mapformat($mapname) "|"]"
    puts $rcfp "create_map::mapformat $mapname [list $mapformat($mapname)]"
    puts $rcfp "create_map::index_org $mapname $index_org"
    puts $rcfp "create_map::dictdataformat $mapname $dictdatafmt $datalen"
    
    puts $rcfp "\# quick load partition map for Tcl"
    puts $rcfp "\# format: "
    puts $rcfp "\#  create_map::partition <mapname> <term length> <partitionfilename> <num of records>"
    
    foreach hash $hashlist {
      variable $hash
      # given hash table of terms of length n, write in binary format.
      
      # generate format string for "puts"
      set fieldtype [lindex $typelist 0]
      set wformat $binformat($fieldtype)
      set formatlist [list ${wformat}$keylen($hash) ]
      set formatstr [join [concat $formatlist $dictdatafmt] ""]
      puts "binary format string for partition $hash: $formatstr"
      set partfp [open "$root_path/$mapname/partition_$hash" "w"]
      fconfigure $partfp -translation binary -encoding binary
      switch $index_org {
	FILEARRAY {
	  foreach term [lsort -dictionary [array names $hash]] {
	    # puts "term: $term"
	    set len [string length $term]
	    set datarow [set ${hash}($term)]
	    set script [concat [list binary format $formatstr] $datarow]
	    # puts "script $script"
	    puts -nonewline $partfp [eval $script]
	  }
	}
	INVERTEDFILE {
	  foreach term [lsort -dictionary [array names $hash]] {
	    # puts "term: $term"
	    set len [string length $term]
	    set datarow [join [set ${hash}($term)] "|"]
	    set datalen [string bytelength $datarow]
	    puts -nonewline $postingsfp [binary format "ia$datalen" $datalen $datarow]
	    set script [concat [list binary format $formatstr] [list $term] $nextpost]
	    # puts "script: $script"
	    puts -nonewline $partfp [eval $script]
	    incr nextpost [expr $datalen + 4]
	  }
	}
      }
      close $partfp
      puts $statfp "partition_$hash $keylen($hash) [llength [array names $hash]]"
      puts $rcfp "create_map::partition $mapname $keylen($hash) partition_$hash [llength [array names $hash]]"
    } 
    if { [string equal $index_org INVERTEDFILE] } {
      close $postingsfp
    }
    close $statfp
    close $rcfp
    
    puts "done"
  }
  
  #-----------------------------------------------------------------
  # code to read word maps created with create_map::create
  #-----------------------------------------------------------------
  
  # Procedure: create_map::lookup_term
  #
  # Given a word map, lookup up based on term length and ordinal value
  # using binary search partitions.
  # 
  proc lookup_term { mapname term } {
    variable partd
    variable partition
    variable root_path
    variable index_org
    variable mapformat
    variable postingsd
    variable datalen
    
    set len [string length $term]
    if {[info exists partition($mapname,$len)] != 0 } {
      if {[info exists partd($mapname,$len)] == 0 } {
	if [file exists $mapname/[lindex $partition($mapname,$len) 0]] {
	  set partd($mapname,$len) [open "$root_path/$mapname/[lindex $partition($mapname,$len) 0]" "r"]
	  fconfigure $partd($mapname,$len) -translation binary -encoding binary
	} else {
	  return {}
	}
      } 
      switch $index_org($mapname) {
	FILEARRAY {
	  set result [binsearch $partd($mapname,$len) $term $len [lindex $partition($mapname,$len) 1] $datalen($mapname)]
	  set datalist [list [lindex $result 0]]
	  foreach bindouble [lrange $result 1 end] {
	    binary scan $bindouble "d1" num
	    lappend datalist $num
	  }
	  return [join $datalist "|"]
	}
	INVERTEDFILE {
	  set entry [binsearch $partd($mapname,$len) $term $len [lindex $partition($mapname,$len) 1] 4]
	  binary scan [lindex $entry 1] "i1" offset
	  # puts "term: [lindex $entry 0], offset: $offset"
	  if {[info exists postingsd($mapname)] == 0 } {
	    if [file exists $mapname/postings] {
	      set postingsd($mapname) [open "$root_path/$mapname/postings" "r"]
	      fconfigure $postingsd($mapname) -translation binary -encoding binary
	    } else {
	      puts stderr "postings file is missing!!"
	      return {}
	    }
	  }
	  seek $postingsd($mapname) $offset start
	  binary scan [read $postingsd($mapname) 4] "i1" postingslen
	  binary scan [read $postingsd($mapname) $postingslen] "a$postingslen" data
	  return $data
	}
      } 
    }
    return {}
  }

  # Procedure: binsearch
  #  Disk based binary search implementation
  # params:
  #   bsfp:       file pointer for binary search table
  #   word:       search word
  #   wordlen:    wordlength
  #   numrecs:    number of records in table
  #   datalen:    datalen
  # returns:
  #   Two element Tcl list containing word and binary data associated with it
  #   or empty list if term not found.
  proc binsearch { bsfp word wordlen numrecs datalen } {
    # d1 or i1 if double then bytelen is 8 else int of bytelen 4.
    set low 0
    set high $numrecs
    
    while { $low < $high } {
      set mid [expr $low + ($high- $low) / 2]
      
      seek $bsfp [expr $mid * ($wordlen+$datalen)] start 
      set tstword [read $bsfp $wordlen]
      set cond [string compare $word $tstword]
      if {$cond < 0} {
	set high $mid
      } elseif { $cond > 0 } {
	set low [expr $mid + 1]
      } else {
	set data [read $bsfp $datalen]
	return [list $word $data]
      }
    }
    return {}
  }

  # Procedure: create_map::index_org
  proc index_org { mapname organization } {
    variable index_org
    set index_org($mapname) $organization
  }

  #
  proc dictdataformat { mapname binformat length } {
    variable dataformat
    variable datalen
    set dataformat($mapname) $binformat
    set datalen($mapname) $length
  }

  # Procedure: create_map::mapformat
  proc mapformat { mapname formatlist } {
    variable mapformat 
    set mapformat($mapname) $formatlist
  }

  # Procedure: create_map::partition 
  proc partition { mapname term_length partitionfilename numofrecords } {
    variable partition
    set partition($mapname,$term_length) [list $partitionfilename $numofrecords]
  }

  # Procedure: create_map::setup
  proc setup { mapname } {
    variable root_path
    source $root_path/$mapname/mapinforc.tcl
  }

  # Procedure: create_map::release
  #  Release file pointer resources used by index
  #
  proc release { mapname } {
    variable partd

    foreach fdesc [array names partd] {
      set list [split $fdesc ","]
      if [string equal [lindex $list 0] $mapname] {
	close $partd($fdesc)
	unset partd($fdesc)
      }
    }
  }
  
  # Procedure: create_map::update
  # 
  # if modification time of lisp file is later than index then rebuild index
  # using lisp file.
  proc update { config_entry } {
    set config_list [split $config_entry "|"]
    set filename [lindex $config_list 0]
    set mapname [lindex $config_list 1]

    set mapfile_exists [file exists $mapname]
    if { $mapfile_exists && [file isdirectory $mapname] == 0 } {
      puts stderr "file $mapname is not a directory"
      return 
    }
    if { $mapfile_exists == 0 || 
	 [file mtime $filename] > [file mtime $mapname] } {
      load_map $config_entry
      create $mapname
    }
  }
  
  # Procedure: create_map::set_root
  #  Change root directory for indices
  proc set_root { index_root_path } {
    variable root_path
    
    set root_path $index_root_path
  }

  # procedure: create_map::list_partition 
  proc list_partition { mapname keylen } {
    variable partition
 p   variable root_path
    variable index_org

    set fp [open [lindex $partition($mapname,$keylen) 0] "r"]
    fconfigure $fp -translation binary -encoding binary
    for {set i 0} { $i < [lindex $partition($mapname,$keylen) 1] } { incr i } {
      seek $fp [expr $i * ($keylen + 4)]
      binary scan [read $fp [expr $keylen + 4]] a10i1 term offset
      puts "term: $term, offset: $offset"
    }
    close $fp
  }
}