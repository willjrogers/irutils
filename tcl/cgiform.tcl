# cgi form procedures

# Procedure: multipart_form_data
# Parse buffer containing input stream data of the Content-type:
# multipart/form-data
#
# To represent srch=dogfish, type=2 the stream looks like this:
# -----------------------------62242892625866\n
# Content-Disposition: form-data; name="srch"\n
# \n
# dogfish\n
# -----------------------------62242892625866\n
# Content-Disposition: form-data; name="type"\n
# \n
# 2\n
# -----------------------------62242892625866\n
# \r
#
# Input: 
#  buffer: buffer contain input stream data
#  content-type: value of content-type environment variable.
#  arrayname: name of array in which to store form data (default: form_data)
#
proc multipart_form_data { buffer content_type { arrayname form_data } } {
  global $arrayname

  set boundary [lindex [split [lindex [split $content_type " "] 1] "="] 1]
  set boundary_number [string trimleft $boundary "-"]
  set tokens [split $buffer "\n"]
  set i 0
  set not_done 1 
  while { $not_done } {
    set token [lindex $tokens $i]
    set trim_token [string trimleft $token "-"]
    if {[string compare  $trim_token $boundary_number] == 0} {
      incr i
      set token [lindex $tokens $i]
      if {[string length $token] > [string length "Content"]} {
	set subtokens [split $token ";"]
	set content_disposition [lindex $subtokens 0]
	set expected_content_disposition "Content-Disposition: form-data"
	if {[string compare $content_disposition $expected_content_disposition] == 0} {

	  set tuple_name [lindex [split [lindex $subtokens 1] "="] 1]

	  incr i 2
	  set getting_values 1
	  while { $getting_values != 0 } {
	    set tuple_value [lindex $tokens $i]
	    puts "here! token = $token"
	    puts "tuple exists: [lsearch -exact [array names $arrayname] $tuple_name]"
	    if {[array exists $arrayname] && 
		[lsearch -exact [array names $arrayname] $tuple_name] > -1 } {
	      lappend ${arrayname}($tuple_name) $tuple_value
	      puts "lappend $tuple_name = $tuple_value -> ${arrayname}($tuple_name)"
	    } else {
	      set ${arrayname}($tuple_name) $tuple_value
	      puts "set $tuple_name = $tuple_value"
	    }
	    parray $arrayname
	    incr i 1

	    set token [lindex $tokens $i]
	    set last_token [string trimleft $token "-"]
	    puts "token: $token, last_token: $last_token"
	    set getting_values [string compare $last_token $boundary_number]
	    if { $i > 20 } {
	      if {[array exists $arrayname] != 1} {
		set ${arrayname}(ERROR) "There was an error." 
	      }
	      return
	    }
	  } 

	}
      } else {
	set not_done 0
      }
    } else {
      set not_done 0
    }
  }
  if {[array exists $arrayname] != 1} {
    set ${arrayname}(ERROR) "There was an error." 
  }
  return
}
