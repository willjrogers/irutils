#include <stdio.h>
#include <errno.h>
#include <string.h>
#include "chk_fopen.h"

FILE *
chk_fopen(const char* filename, const char* mode)
{
  char *errstr;
  FILE* fp = fopen(filename, mode);
  /** check for valid file descriptor  */
  if ( fp == NULL )
    fprintf( stderr, "error opening file: %s: %s\n", 
	     filename, 
	     (errstr = strerror(errno)) == NULL ? "" : errstr);
  return fp;
}
