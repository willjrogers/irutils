/* -----------------------------------------------------------------------------
 * tree.c 
 * Routines that implement a right-threaded (Knuth, vol. 1, p. 325) term tree
 * in a large malloced buffer.  Buffer size is calculated from size of text so
 * as to be (perhaps) big enough; if it fills up, it is reallocated to twice its
 * size.
 *
 * Contents:
 *  starttree       start new tree
 *  growtree        search with update
 *  searchtree      read-only search
 *  initwalktree    call before each walk
 *  walktree        to walk, call many times
 *  readtree        read tree from file
 *  writetree       write tree to file
 *  freetree        free tree buffer
 *
 *  getnode         (static) allocate a node
 * ----------------------------------------------------------------------------- */

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>

#ifndef max
#define max(a,b)  ((a) > (b)) ? (a) : (b)
#endif

/* Tree buffer starts at CTREE_FAC times size of text to be indexed: */
#define CTREE_FAC .02

typedef struct {
  int left;     /* left link */
  int right;    /* right link (> 0) or right thread (<= 0); */
		/* -1 is a null right thread */
  int numpost;  /* number of postings */
  int fpo;      /* first posting offset */
  char term[1]; /* don't worry, the term can be as long as needed */
} CTREE_NODE;

#define omem(o, mem) (((CTREE_NODE *)(buf + (o)))->mem)

static char *buf = 0; /* tree stored here */
static unsigned int bufsize;
static unsigned int top;       /* first unused offset in buf */
static int walknode;  /* next node to examine when walking tree */
static int walklink;  /* 1: connector to walknode was link; 0: thread */
static int walkdone;

/***************************************************************************/

/* -----------------------------------------------------------------------------
 * Call this when starting a new tree.  Given size of text to be indexed,
 * allocates a buffer which will probably be big enough to contain the
 * entire tree.  (Buffer will be reallocated as necessary.) 
 * ----------------------------------------------------------------------------- */

void
starttree(textbytes)
unsigned int textbytes;
{
  extern char *malloc();
  char es[100];

  buf = malloc(bufsize = (CTREE_FAC * textbytes));
  if(!buf) {
    sprintf(es, "starttree (ctree.c): malloc of %d bytes", bufsize);
    pexit(es);
  }
  top = 0;
}

/***************************************************************************/

/* -----------------------------------------------------------------------------
 * Enters new data into tree.  After calling this, you should write the
 * posting at offset napo in the linked-postings file, writing growtree's
 * return value as its link field.
 * (Posting link fields contain offsets in the linked-postings file.  Each
 * posting list is singly linked and grows at its BEGINNING.  A null link is
 * indicated by a 0 value.) 
 * ----------------------------------------------------------------------------- */

unsigned int
growtree(char *term, unsigned long napo, unsigned long *maxposts)  /* napo: next available posting offset */
{
  int node, newnode, comp;
  unsigned int ret;
  static unsigned int getnode();

  if(!top) {
    getnode(term);
    omem(0, left) =  0;
    omem(0, right) = -1;
    omem(0, numpost) = 1;
    omem(0, fpo) = napo;
    strcpy(omem(0, term), term);
    return 0;
  }
  node = 0;
  while(1) {
    if((comp = strcmp(term, omem(node, term))) < 0) {
      if(omem(node, left)) {
	node = omem(node,left);
	continue;
      }
      /* no left child; make one */
      newnode = getnode(term);
      omem(node, left) = newnode;
      omem(newnode, left) = 0;
      omem(newnode, right) = -node;
      omem(newnode, numpost) = 1;
      omem(newnode, fpo) = napo;
      strcpy(omem(newnode, term), term);
      return 0;
    }
    else if(comp > 0) {
      if(omem(node, right) > 0) { /* right link; go to right child */
	node = omem(node, right);
	continue;
      }
      /* right thread, so there's no right child; make one */
      newnode = getnode(term);
      omem(newnode, right) = omem(node, right);
      omem(node, right) = newnode;
      omem(newnode, left) = 0;
      omem(newnode, numpost) = 1;
      omem(newnode, fpo) = napo;
      strcpy(omem(newnode, term), term);
      return 0;
    }
    else { /* match */
      omem(node, numpost) = omem(node, numpost) + 1;
      *maxposts = max(omem(node, numpost), *maxposts);
      ret = omem(node, fpo);
      omem(node, fpo) = napo;
      return ret;
    }
  }
}

/***************************************************************************/

/* -----------------------------------------------------------------------------
 * Searches for a node matching the provided term.  If found, returns 
 * numpost, first-posting offset, and function value 1; otherwise, 
 * function value is 0. 
 * ----------------------------------------------------------------------------- */

int
searchtree(char *term, int *numpost, int *fpo)
{
  int node, comp;

  node = 0;
  while(1) {
    if((comp = strcmp(term, omem(node,term))) < 0) {
      if(!omem(node, left))
	return 0;
      node = omem(node, left);
    }
    else if(comp > 0) {
      if(omem(node, right) <= 0)
	return 0;
      node = omem(node, right);
    }
    else {
      *numpost = omem(node, numpost);
      *fpo = omem(node, fpo);
      return 1;
    }
  }
}

/***************************************************************************/

/* -----------------------------------------------------------------------------
 * To walk tree, you should call this, then call walktree repeatedly. 
 * ----------------------------------------------------------------------------- */

int
initwalktree()
{
  walknode = walkdone = 0;
  walklink = 1;
  return (1);
}

/***************************************************************************/

/* ----------------------------------------------------------------------------- 
 * Each time you call walktree, it returns the data of one node; nodes are
 * returned in order of term.  If there is another node to return, it is
 * returned, and function value is 1; if the last node has already been
 * returned, function value is 0. 
 * ----------------------------------------------------------------------------- */

int
walktree(char *term, int *numpost, int *fpo)
{
  int left, right;

  if(walkdone)
    return 0;
  else {
    if(walklink)
      while(left = omem(walknode, left))
	  walknode = left;
    strcpy(term, omem(walknode, term));
    *numpost = omem(walknode, numpost);
    *fpo = omem(walknode, fpo);
    right = omem(walknode, right);
    if(right == -1)
      walkdone = 1;
    else {
      walknode = abs(right);
      walklink = (right > 0);
    }
    return 1;
  }
}

/***************************************************************************/

/* ----------------------------------------------------------------------------- 
 * Reads tree from a file.  (Then, you can grow it some more, search it,
 * or walk it.)  Discards the current tree. 
 * ----------------------------------------------------------------------------- */

void
readtree(char *filename)
{
  FILE *fp;
  struct stat strstat;
  int nread;
  char es[100];
  extern char *malloc();

  if(!(fp = fopen(filename, "r"))) {
    sprintf(es, "readtree (ctree.c), fopen, filename %s", filename);
    pexit(es);
  }
  if(stat(filename, &strstat))
    pexit("readtree (ctree.c), stat");
  freetree();
  buf = malloc(bufsize = top = strstat.st_size);
  if(!buf) {
    sprintf(es, "readtree (ctree.c), malloc of %d bytes", bufsize);
    pexit(es);
  }
  nread = fread(buf, 1, bufsize, fp);
  fclose(fp);
  if(nread != bufsize) {
    sprintf(es, "readtree (ctree.c): fread of %d bytes;\n\
%d bytes actually read\n", bufsize, nread);
    pexit(es);
  }
}

/***************************************************************************/

/* ----------------------------------------------------------------------------- 
 * Writes tree to desired filename. (Does not discard tree, i.e., it is
 * still in its buffer.) 
 * ----------------------------------------------------------------------------- */

void
writetree(filename)
char *filename;
{
  FILE *fp;
  int nwritten;
  char es[100];

  if(!(fp = fopen(filename, "w"))) {
    sprintf(es, "writetree (ctree.c), fopen, filename %s", filename);
    pexit(es);
  }
  nwritten = fwrite(buf, 1, top, fp);
  fclose(fp);
  if(nwritten != top) {
    sprintf(es, "writetree (ctree.c), fwrite of %d bytes;\n\
%d bytes actually written", top, nwritten);
    pexit(es);
  }
}


/***************************************************************************/

/* ----------------------------------------------------------------------------- 
 * Frees the tree buffer (if it exists). 
 * ----------------------------------------------------------------------------- */

int
freetree() {
  if(buf) {
    free(buf);
    buf = 0;
  }
  return (1);
}

/***************************************************************************/

/* ----------------------------------------------------------------------------- 
 * Gets a new node from top of tree buffer, and returns its offset.
 * Reallocates buffer (to twice its size) if necessary. 
 * ----------------------------------------------------------------------------- */

static unsigned int
getnode(char *term)
{
  int exact, rounded;
  char es[100];
  unsigned int oldtop;

  exact = 17 + strlen(term);
  rounded = ((exact + 3) / 4) * 4;
  oldtop = top;
  top += rounded;
  if(top > bufsize) {
    buf = (char *)realloc(buf, bufsize = (2 * bufsize));
    if(!buf) {
      sprintf(es, "getnode (ctree.c), realloc from %d to %d bytes\n",
	bufsize, 2 * bufsize);
      pexit(es);
    }
  }
  return oldtop;
}

/***************************************************************************/
