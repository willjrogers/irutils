# bsp_map : binary search partitioned map
#
#
# add new logic:   if typelist of form: TXT|INT|...|INT  then use array
#                  else use inverted file.
#

# binary search partitions
# 
# * setting directory where indices reside
#
#  bsp_map::set_root directory (is there a better name for this)
#  defaults to current directory   
#  NOTE: should be a full path in most cases.
#
# e.g.:
#  bsp_map::set_root /home/wrogers/exper
#
# * index creation and updating
#  
#  bsp_map::create_lisp lispfile mapname
#  bsp_map::update_lisp lispfile mapname
#
#  bsp_map::create config
#  bsp_map::update config
#
# e.g.:
#  bsp_map::create_lisp /lisp/word-signal-lw.9901-12.l word-signal-lw.9901-12
#  bsp_map::update_lisp /lisp/word-signal-lw.9901-12.l word-signal-lw.9901-12
#
#  bsp_map::create wordidf_lw.txt|wordidf_lw|2|word|normwc|TXT|TXT
#  bsp_map::update wordidf_lw.txt|wordidf_lw|2|word|normwc|TXT|TXT
#
# * indexing use initialization and term lookup 
# 
#  bsp_map::setup mapname
#  bsp_map::lookup_term mapname term
# 
# e.g.:
#  bsp_map::setup word-signal-lw.9901-1
#  bsp_map::lookup_term word-signal-lw.9901-12 WESTERNBLOTTING
#
# File organization:
#    FILEARRAY: directory containing:
#       mapinforc.tcl                  -- setup file containing map 
#                                         parameters including size of
#                                         partitions.
#       partition.stats                -- a sort of human readable file
#                                         partition sizes and term-lengths
#       partition_<mapname><termlen>   -- a partition of all terms of
#                                         term length <termlen> and 
#                                         associated values.
#    INVERTED FILE: directory containing:
#       mapinforc.tcl                  -- setup file containing map 
#                                         parameters including size of
#                                         partitions.
#       partition.stats                -- a sort of human readable file
#                                         partition sizes and term-lengths
#       partition_<mapname><termlen>   -- a partition of all terms of
#                                         term length <termlen> and 
#                                         pointers to associated values in
#                                         postings file.
#       postings                       -- postings file containing associated
#                                         values.
#       


namespace eval bsp_map {
  variable index_root
  variable table_root
  variable lisp_table_root
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

  # set index_root to current directory
  set index_root [pwd]

  # set table_root to current directory
  set table_root [pwd]
  
  # Procedure: load_map_from_lisp
  # Params
  #   filename  lisp file containg normalized terms
  #   mapname   directory to place partitons and the name of partition map.
  # NOTE: not a true lisp parser.
  proc load_map_from_lisp { filename mapname } {
    variable hashlist
    variable mapformat
    variable wordnum
    variable keylen
    variable lisp_table_root
    
    # format is in "filename|mapname|2|word|normwc|TXT|INT" it is expressed as a tcl list for speed
    # to output a separated list use:  `join <list> "|"`
    set mapformat($mapname) [list $filename $mapname 2 word normwc TXT INT]
    set wordnum($mapname) 0

    set fp [open $lisp_table_root/$filename "r"]
    while {[gets $fp line] >= 0} {
      set list [split $line]
      set key [string tolower [string trim [lindex $list 1] "\""]]
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
    
    puts "$wordnum($mapname) words hashed."
    set hashlist [info vars ${mapname}*]
    puts "[llength $hashlist] hash partitions created"

    foreach hash $hashlist {
      puts "$hash: length: [llength [array names $hash]]"
    }
  }

  proc load_map_from_db_dump { filename config_entry } {
    variable hashlist
    variable mapformat
    variable wordnum
    variable keylen
    variable table_root

    set config_list [split $config_entry "|"]
    set filename [lindex $config_list 0]
    set mapname [lindex $config_list 1]

    set mapformat($mapname) $config_list
    set wordnum($mapname) 0
  }

  # Procedure: load_table
  #  Load table referred to by read entry of the form:
  #   input_file|tablename|num_fields|fieldname1|...|N|fieldtype1|...|N|
  #  Params:
  #     config_entry -- a configuration entry of form shown above.
  # 
  proc load_map { config_entry } {
    variable hashlist
    variable mapformat
    variable wordnum
    variable keylen
    variable table_root

    set config_list [split $config_entry "|"]
    set filename [lindex $config_list 0]
    set mapname [lindex $config_list 1]

    set mapformat($mapname) $config_list
    set wordnum($mapname) 0

    puts "loading from file $table_root/$filename"
    set fp [open $table_root/$filename "r"]
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
  
  
  # Procedure: bsp_map::create
  #  Generate disk-based word map from existing map in memory
  # Params
  #   mapname   directory to place partitons and the name of partition map.
  #   debug (optional) set to 1 if you want debuging
  #
  proc create { mapname { debug 0 } } {
    variable index_root
    variable binformat
    variable hashlist
    variable mapformat
    variable wordnum
    variable keylen
    
    set rowlen [lindex $mapformat($mapname) 2]
    # namelist[] = elements[3..(3 + rowlen - 1)]
    # set namelist [lrange $mapformat($mapname) 3 [expr 2 + $rowlen]]
    # typelist[] = elements[(3 + rowlen)..(3 + (rowlen * 2) - 1)]
    set typelist [lrange $mapformat($mapname) [expr 3 + $rowlen] [expr 3 + $rowlen + $rowlen]]
		  
    set datalen 0
    set index_org FILEARRAY
    set dataformatlist {}
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
    file mkdir $index_root/$mapname

    # write partitions in human readable format
    if { $debug == 1 } {
      puts "writing partitions in human readable format..."
      foreach hash $hashlist {
	variable $hash
	set partfp [open $index_root/$mapname/$hash "w"]
	foreach term [lsort -dictionary [array names $hash]] {
	  puts $partfp "$term [set ${hash}($term)]"
	}
	close $partfp
      }
    }

    # if inverted file than open postings.
    switch $index_org {
      INVERTEDFILE {
	set postingsname $index_root/$mapname/postings
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
    set statfp [open "$index_root/$mapname/partition.stats" "w"]
    puts $statfp "\# $mapname/partition.log -- bsp_map.tcl status file"
    puts $statfp "\# total number of terms: $wordnum($mapname)"
    puts $statfp "\#"
    puts $statfp "\# table format: "
    puts $statfp "\#  partition_filename termlen nterms"
    
    set rcfp [open "$index_root/$mapname/mapinforc.tcl" "w"]
    puts $rcfp "\# Tcl rc file for bsp_map."
    puts $rcfp "\#"
    puts $rcfp "\# record format:"
    puts $rcfp "\#   [join $mapformat($mapname) "|"]"
    puts $rcfp "bsp_map::mapformat $mapname [list $mapformat($mapname)]"
    puts $rcfp "bsp_map::index_org $mapname $index_org"
    puts $rcfp "bsp_map::dictdataformat $mapname $dictdatafmt $datalen"
    
    puts $rcfp "\# quick load partition map for Tcl"
    puts $rcfp "\# format: "
    puts $rcfp "\#  bsp_map::partition <mapname> <term length> <partitionfilename> <num of records>"
    
    foreach hash $hashlist {
      variable $hash
      # given hash table of terms of length n, write in binary format.
      
      # generate format string for "puts"
      set fieldtype [lindex $typelist 0]
      set wformat $binformat($fieldtype)
      set formatlist [list ${wformat}$keylen($hash) ]
      set formatstr [join [concat $formatlist $dictdatafmt] ""]
      puts "binary format string for partition $hash: $formatstr"
      set partfp [open "$index_root/$mapname/partition_$hash" "w"]
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
      puts $rcfp "bsp_map::partition $mapname $keylen($hash) partition_$hash [llength [array names $hash]]"
    } 
    if { [string equal $index_org INVERTEDFILE] } {
      close $postingsfp
    }
    close $statfp
    close $rcfp
    
    puts "done"
  }
  
  #-----------------------------------------------------------------
  # code to read word maps created with bsp_map::create
  #-----------------------------------------------------------------
  
  # Procedure: bsp_map::lookup_term
  #
  # Given a word map named "mapname", lookup up "term" based on term
  # length and ordinal value using binary search partitions.
  # 
  proc lookup_term { mapname term } {
    variable partd
    variable partition
    variable index_root
    variable index_org
    variable mapformat
    variable postingsd
    variable datalen
    
    set len [string length $term]
    if {[info exists partition($mapname,$len)] != 0 } {
      if {[info exists partd($mapname,$len)] == 0 } {
	if [file exists $index_root/$mapname/[lindex $partition($mapname,$len) 0]] {
	  set partd($mapname,$len) [open "$index_root/$mapname/[lindex $partition($mapname,$len) 0]" "r"]
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
	  if { $entry == {} } {
	    return {}
	  }
	  binary scan [lindex $entry 1] "i1" offset
	  # puts "term: [lindex $entry 0], offset: $offset"
	  if {[info exists postingsd($mapname)] == 0 } {
	    if [file exists $index_root/$mapname/postings] {
	      set postingsd($mapname) [open "$index_root/$mapname/postings" "r"]
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
  #   datalen:    length of associated data
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

  # Procedure: bsp_map::index_org
  # set indexing organization of map "mapname", usually "FILEARRAY" or
  #  "INVERTEDFILE"
  proc index_org { mapname organization } {
    variable index_org
    set index_org($mapname) $organization
  }

  # Procedure: dictdataformat
  # Binary format and data length of associated value data for map
  # "mapname"
  proc dictdataformat { mapname binformat length } {
    variable dataformat
    variable datalen
    set dataformat($mapname) $binformat
    set datalen($mapname) $length
  }

  # Procedure: bsp_map::mapformat
  # table format for table "mapname" as a Tcl list.
  proc mapformat { mapname formatlist } {
    variable mapformat 
    set mapformat($mapname) $formatlist
  }

  # Procedure: bsp_map::partition 
  # Set term length, partition filename, and number of records for a
  # partition in the index.
  proc partition { mapname term_length partitionfilename numofrecords } {
    variable partition
    set partition($mapname,$term_length) [list $partitionfilename $numofrecords]
  }

  # Procedure: bsp_map::setup
  # Load setup file for map "mapname"
  proc setup { mapname } {
    variable index_root
    source $index_root/$mapname/mapinforc.tcl
  }

  # Procedure: bsp_map::release
  #  Release file pointer resources used by index "mapname"
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
  
  # Procedure: bsp_map::set_root
  #  Change root directory for indices
  proc set_index_root { index_root } {
    variable index_root
    
    set index_root $index_root
  }

  # Procedure: bsp_map::set_index_root
  #  Change root directory for indices
  proc set_index_root { root } {
    variable index_root
    
    set index_root $root
  }
  # Procedure: bsp_map::set_table_root
  #  Change root directory for tables
  proc set_table_root { root } {
    variable table_root
    
    set table_root $root
  }

  #  Change root directory for lisp_tables
  proc set_lisp_table_root { root } {
    variable lisp_table_root
    
    set lisp_table_root $root
  }

  # procedure: bsp_map::list_partition 
  # List key/value pairs in a partition for map "mapname" using
  # key (term) length "keylen".
  proc list_partition { mapname keylen } {
    variable partition
    variable index_root
    variable index_org

    set fp [open "$index_root/[lindex $partition($mapname,$keylen) 0]" "r"]
    fconfigure $fp -translation binary -encoding binary
    for {set i 0} { $i < [lindex $partition($mapname,$keylen) 1] } { incr i } {
      seek $fp [expr $i * ($keylen + 4)]
      binary scan [read $fp [expr $keylen + 4]] a10i1 term offset
      puts "term: $term, offset: $offset"
    }
    close $fp
  }

  # Procedure: bsp_map::update
  # 
  # if modification time of table file is later than index then rebuild index
  # using lisp file. See procedure "create".
  proc update { config_entry } {
    variable index_root
    variable table_root

    set config_list [split $config_entry "|"]
    set filename [lindex $config_list 0]
    set mapname [lindex $config_list 1]

    set mapfile_exists [file exists $index_root/$mapname]
    if { $mapfile_exists && [file isdirectory $index_root/$mapname] == 0 } {
      puts stderr "file $mapname is not a directory"
      return 
    }
    if { $mapfile_exists == 0 || 
	 [file mtime $table_root/$filename] > [file mtime $index_root/$mapname] } {
      load_map $config_entry
      create $mapname
    }
  }

  # Procedure: bsp_map::update_lisp
  # 
  # if modification time of table file is later than index then rebuild index
  # using lisp file. See procedure "create".
  proc update_lisp { filename mapname } {
    variable index_root
    variable lisp_table_root

    set mapfile_exists [file exists $index_root/$mapname]
    if { $mapfile_exists && [file isdirectory $index_root/$mapname] == 0 } {
      puts stderr "file $mapname is not a directory"
      return 
    }
    if { $mapfile_exists == 0 || 
	 [file mtime $lisp_table_root/$filename] > [file mtime $index_root/$mapname] } {
      load_map_from_lisp $filename $mapname
      create $mapname
    }
  }

}
# namespace bs_map 