#!/usr/local/bin/tclsh8.3


puts "Content-type: text/plain\n"

puts "Environment variables:"
puts ""

foreach evar [array names env] {
    puts "set env $evar $env($evar)"
}

puts ""

if { $env(REQUEST_METHOD) == "POST" } {
  puts "POST input:"
  set buffer [read stdin $env(CONTENT_LENGTH)]
  puts -nonewline $buffer
  set tmpfile [open "/tmp/test.tcl.output.[pid]" "w"]
  puts -nonewline $tmpfile $buffer
  close $tmpfile
}

exit 0