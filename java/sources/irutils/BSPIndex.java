package utils;

import java.util.*;
import java.io.*;

/**
 * BSPIndex.java
 *
 *
 * Created: Fri Jul  6 15:37:53 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: BSPIndex.java,v 1.1 2001/07/25 13:05:01 wrogers Exp $
 */

public class BSPIndex implements Serializable
{
  /** index is organized as an file array: a dictionary contain keys both and data. */
  static final int FILEARRAY = 0;
  /** index is organized as an inverted file: a dictionary file and a postings file. */
  static final int INVERTED_FILE = 1;
  static String indexOrgTypeString[] = new String[] { "FILEARRAY", "INVERTEDFILE" };
  /** list of supportted binary formats */
  static HashMap binFormats = new HashMap(4);
  /** do this once at class instantiation */
  static {
    binFormats.put("INT", "d1");
    binFormats.put("TXT", "a");
    binFormats.put("PTR", "i1");
  }

  /** hashlist of hash or tree maps for generating new indices. */
  transient HashMap hashlist= new HashMap(5);

  /** hashmap of open partition files. */
  transient HashMap partitionFiles = new HashMap(5);

  /** postings file */
  transient RandomAccessFile postingsFile;

  /** name of index */
  String indexname;

  /** name of table index is derived from */
  String tablefilename;

  /** directory path of parent directory for index */
  String indexParentDirectoryPath;

  /** format for data */
  ArrayList indexFormat;
  
  /** number of words in index */
  int wordnum;

  /** the index organization: FILEARRAY, INVERTEDFILE, etc. */
  int indexOrg;

  /** number of records in each partition */
  HashMap numrecs;

  /** datalen in dictionary part of each partition */
  HashMap dataLength;

  /** default constructor for serialization purposes only. */
  public BSPIndex()
  {
    // System.out.println("BSPIndex: instantiation");
  }

  /** Constructor 
   * @param indexname      name of index
   * @param tablefilename   filename containing source table data
   * @param indexParentDir parent directory of index
   * @param format         table data format
   */
  public BSPIndex(String indexname, String tablefilename, 
		  String indexParentDir, ArrayList format)
  {
    this.indexname = indexname;
    this.tablefilename = tablefilename;
    this.indexParentDirectoryPath = indexParentDir;
    this.indexFormat = format;
  }

  /** load table into in-memory term -> value map. */
  public void load_map()
     throws FileNotFoundException, IOException
  {
    // Load records into buckets based on term length.  Each bucket is
    // a TreeMap where record is stored by the ordinal value of the
    // first element (key) of the record.
    String line;
    String key;
    ArrayList lineList;
    int i = 0;
    // System.out.println("loading map " + this.indexname );
    BufferedReader reader = 
      new BufferedReader(new FileReader( this.tablefilename ));
    while ( (line = reader.readLine()) != null )
      {
	TreeMap bucket;
	i++;
	lineList = utils.StringUtils.split(line, "|");
	key = (String)lineList.get(0);
	String keyLength = new Integer (key.length()).toString();
	bucket = (TreeMap)this.hashlist.get(this.indexname+keyLength);
	if (bucket == null ) {
	  bucket = new TreeMap();
	  this.hashlist.put(this.indexname+keyLength, bucket);
	} 
	bucket.put(key, line);
	wordnum++;
	// System.out.println("bucket.put(key: " + key + ", value), " + 
	// "keylength: " + keyLength + ", bucket size: " + bucket.size());
      }
    reader.close();
    // System.out.println("# of input lines: " + i );
    // System.out.println("# of buckets: " + hashlist.size());
    Iterator iter = hashlist.values().iterator();
    while (iter.hasNext()) {
      Map bucket = (Map)iter.next();
      // System.out.println("bucket, size: " + bucket.size());
    }
  }

  /**
   * Generate disk-based word map from existing map in memory
   */
  public void create()
    throws BSPIndexCreateException, IOException
  {
    DataOutputStream postingsWriter = null;
    ArrayList dictDataFormat = new ArrayList(1);
    int nextpost = 0;
    int rowLen = Integer.parseInt((String)indexFormat.get(2));
    ArrayList typeList = new ArrayList(rowLen);
    this.dataLength = new HashMap(5);
    this.numrecs = new HashMap(5);
    for (int i = 3 + rowLen, j = 0; i < 3 + rowLen + rowLen; i++, j++)
      {
	typeList.add(j, indexFormat.get(i));
	// System.out.println("type: " + indexFormat.get(i));
      }
    // System.out.println("indexFormat: " + utils.StringUtils.list(indexFormat));
    // System.out.println("typeList: " + utils.StringUtils.list(typeList));
    int dataLen = 0;
    this.indexOrg = FILEARRAY;

    ArrayList dataFormatList = new ArrayList(10);
    for (int i = 1; i < rowLen; i++ )
      {
	String fieldtype = (String)typeList.get(i);
	dataFormatList.add(binFormats.get(fieldtype));
	if ( fieldtype.compareTo("TXT") == 0 ) {
	  indexOrg = INVERTED_FILE;
	} else {
	  dataLen=+8;
	}
      }
    // create index

    File indexDirectory = new File(this.indexParentDirectoryPath + "/" + this.indexname);
    if (indexDirectory.mkdir() == false)
      {
	throw new BSPIndexCreateException("unable to create index directory");
      }
    switch (indexOrg)
      {
      case FILEARRAY:
	dictDataFormat = dataFormatList;
	break;
      case INVERTED_FILE:
	dictDataFormat.add(binFormats.get("PTR"));
	postingsWriter = 
	  new DataOutputStream 
	  ( new BufferedOutputStream
	    (new FileOutputStream
	     (indexParentDirectoryPath + "/" + this.indexname + "/postings" )));
	break;
      }
    PrintWriter statfp = new PrintWriter
      (new BufferedWriter(new FileWriter( indexParentDirectoryPath + "/" +
					  this.indexname + "/partition.stats")));
    statfp.println( "# " + this.indexname + "/partition.log -- bsp_map.tcl status file" );
    statfp.println( "# total number of terms: " + this.wordnum );
    statfp.println( "#" );
    statfp.println( "# table format: " );
    statfp.println( "#  partition_filename termlen nterms" );

    PrintWriter rcfp = new PrintWriter
      (new BufferedWriter(new FileWriter( indexParentDirectoryPath + "/" +
					  this.indexname + "/mapinforc.tcl")));
    rcfp.println( "# Tcl rc file for bsp_map." );
    rcfp.println( "#" );
    rcfp.println( "# record format:" );
    rcfp.println( "#   " + utils.StringUtils.join(indexFormat, "|") );
    rcfp.println( "bsp_map::mapformat " + this.indexname + " " +
		  utils.StringUtils.list(indexFormat));
    rcfp.println( "bsp_map::index_org " + this.indexname + " " + indexOrgTypeString[indexOrg] );
    rcfp.println( "bsp_map::dictdataformat " + this.indexname + " " +
		  StringUtils.join(dictDataFormat, " ") + " " + dataLen );
    
    rcfp.println( "# quick load partition map for Tcl" );
    rcfp.println( "# format: " );
    rcfp.println( "#  bsp_map::partition <mapname> <term length> <partitionfilename> <num of records>" );
 
    
    Iterator iter = this.hashlist.keySet().iterator();
    while (iter.hasNext()) 
      {
	String key = (String)iter.next();
	TreeMap tree = (TreeMap)hashlist.get(key);
	
	DataOutputStream partitionWriter = 
	  new DataOutputStream 
	  ( new BufferedOutputStream
	    ( new FileOutputStream
	     ( indexParentDirectoryPath + "/" + this.indexname + "/partition_" + key )));
	switch (indexOrg)
	  {
	  case FILEARRAY:
	    buildFileArray(partitionWriter, dictDataFormat, tree, key);
	    this.dataLength.put(key, new Integer(dataLen));
	    break;
	  case INVERTED_FILE:
	    int numrecs = 0;
	    Iterator keyIter = tree.keySet().iterator();
	    while (keyIter.hasNext()) {
	      String termKey = (String)keyIter.next();
	      String dataRecord = (String)tree.get(termKey);
	      // write posting
	      postingsWriter.writeInt(dataRecord.length());
	      postingsWriter.writeBytes(dataRecord);
	      // write dictionary entry
	      partitionWriter.writeBytes(termKey);
	      partitionWriter.writeInt(nextpost);
	      nextpost = nextpost + dataRecord.length() + 4;
	      numrecs++;
	    }
	    this.numrecs.put(key, new Integer(numrecs));
	    // System.out.println("key: " + key );
	    this.dataLength.put(key, new Integer(4));
	    break;
	  }
	int keylength = ((String)tree.firstKey()).length();
	statfp.println( "partition_" + key + " " + 
			keylength + " " + tree.size());
	rcfp.println( "bsp_map::partition " + this.indexname + " " +
		      keylength + " partition_" + key + " " + tree.size());
	partitionWriter.close();
      }
    
    postingsWriter.close();
    statfp.close();
    rcfp.close();

    /** serialize info on object to indexname/bspIndexInfo.ser */
    FileOutputStream ostream = 
      new FileOutputStream(this.indexParentDirectoryPath + "/" + this.indexname +
			   "/" + "bspIndexInfo.ser");
    ObjectOutputStream p = new ObjectOutputStream(ostream);
    
    p.writeObject(this);
    p.flush();
    ostream.close();
  }

  /**
   * Build index in file array organization.
   * @param writer      dictionary to write key/value data to.
   * @param dataFormat  format of data to be stored with key.
   * @param aTermMap    Map containing key/value pairs to be stored in index.
   * @param partitionId     partition identifier.
   */
  void buildFileArray(DataOutputStream writer, ArrayList dataFormat, 
		      Map aTermMap, String partitionId)
    throws IOException
  {
    int numrecs = 0;
    Iterator keyIter = aTermMap.values().iterator();
    while (keyIter.hasNext()) {
      String key = (String)keyIter.next();
      String dataRecord = (String)aTermMap.get(key);
      int dataIndex = 0;
      ArrayList dataList = utils.StringUtils.split(dataRecord, "|");
      writer.writeBytes(key);
      Iterator iter = dataFormat.iterator();
      while (iter.hasNext()) {
	String format = (String)iter.next();
	if (format.compareTo("INT") == 0 ) {
	  writer.writeInt(Integer.parseInt((String)dataList.get(dataIndex)));
	} else if (format.compareTo("DBL") == 0 ) {
	  writer.writeDouble(Double.parseDouble((String)dataList.get(dataIndex)));
	}
	dataIndex++;
      }
      numrecs++;
    }
    this.numrecs.put(partitionId, new Integer(numrecs));
  }

  /**
   * If modification time of table file is later than index then rebuild index
   * using lisp file. See method "create".
   *
   * @param config_entry umls style configuration entry. 
   */
  public void update()
    throws IOException, BSPIndexCreateException
  {
    String indexDir = this.indexParentDirectoryPath + "/" + this.indexname;
    // System.out.println("updating index: " + this.indexname );
    File tablefile = new File(this.tablefilename);
    File mapfile = new File(indexDir);
    // System.out.println("mapfile.exists(): " + mapfile.exists());
    if (mapfile.exists() == false ||
	tablefile.lastModified() > mapfile.lastModified())  {
	this.load_map();
	this.create();
    } else if (mapfile.isFile())  {
	throw new IOException("file " + indexname + " is not a directory!");
    } 
  } // BSPIndex.update



  /**
   *  Disk based binary search implementation
   *
   * @param bsfp       file pointer for binary search table
   * @param word       search word
   * @param wordlen    wordlength
   * @param numrecs    number of records in table
   * @param datalen    length of associated data

   * @return byte array containing binary data
   *          associated with search word or null if term not found.
   */
  byte[] binsearch(RandomAccessFile bsfp, String word, int wordlen, int numrecs, int datalen)
    throws IOException
  {
    // d1 or i1 if double then bytelen is 8 else int of bytelen 4.
    int low = 0;
    int high = numrecs;
    int cond;
    int mid;
    byte[] wordbuf = new byte[wordlen];
    String tstword;
    byte[] data = new byte[datalen];

    while ( low < high )
      {
	mid = low + (high- low) / 2;
	bsfp.seek(mid * (wordlen+datalen));
	bsfp.read(wordbuf);
	tstword = new String(wordbuf);
	cond = word.compareTo(tstword);
	if (cond < 0) {
	  high = mid;
	} else if (cond > 0) {
	  low = mid + 1;
	} else {
	  bsfp.read(data);
	  return data;
	}
      }
    return null;
  }


  /**
   *  Disk based binary search implementation
   *
   * @param bsfp       file pointer for binary search table
   * @param word       search word
   * @param wordlen    wordlength
   * @param numrecs    number of records in table
   * @return int containing address of posting.
   */
  int intBinsearch(RandomAccessFile bsfp, String word, int wordlen, int numrecs)
    throws IOException
  {
    // d1 or i1 if double then bytelen is 8 else int of bytelen 4.
    int datalen = 4;
    int low = 0;
    int high = numrecs;
    int cond;
    int mid;
    byte[] wordbuf = new byte[wordlen];
    String tstword;

    while ( low < high )
      {
	mid = low + (high- low) / 2;
	bsfp.seek(mid * (wordlen+datalen));
	bsfp.read(wordbuf);
	tstword = new String(wordbuf);
	cond = word.compareTo(tstword);
	if (cond < 0) {
	  high = mid;
	} else if (cond > 0) {
	  low = mid + 1;
	} else {
	  return bsfp.readInt();
	}
      }
    return -1;
  }

  /**
   * setup unserialized index.
   */
  public void setup()
  {
    if ( this.partitionFiles == null ) {
      this.partitionFiles = new HashMap(4);
    }
  }

  /**
   * Look up word in index, return corresponding key and value pair if
   * found, null if otherwise.
   * @param word word to lookup in index.
   * @return tuple containing key/value pair, null if key not found.
   */
  public BSPTuple lookup(String word)
    throws FileNotFoundException, IOException
  {
    RandomAccessFile dictionaryFile;
    String keyLength = new Integer (word.length()).toString();
    String key = this.indexname + keyLength;
    // System.out.println("this.partitionFiles: " + this.partitionFiles );
    if ( this.partitionFiles.containsKey(key) ) 
      {
	dictionaryFile = (RandomAccessFile)this.partitionFiles.get(key);
      }
    else 
      {
	dictionaryFile = 
	  new RandomAccessFile ( indexParentDirectoryPath + "/" +
				 indexname + "/partition_" + key, "r" );
	this.partitionFiles.put(key, dictionaryFile);
      } 
    switch (this.indexOrg)
      {
      case FILEARRAY:
	break;
      case INVERTED_FILE:
	int address = intBinsearch(dictionaryFile, word, word.length(), 
			    ((Integer)this.numrecs.get(key)).intValue() );
	// System.out.println("address : " + address);
	if ( this.postingsFile == null ) {
	  this.postingsFile = new RandomAccessFile ( indexParentDirectoryPath + "/" +
						     indexname + "/postings", "r" );
	}
	postingsFile.seek(address);
	int postingsLen = postingsFile.readInt();
	// System.out.println("postingsLen : " + postingsLen);
	byte[] databuf = new byte[postingsLen];
	postingsFile.read(databuf);
	return new BSPTuple(word, new String(databuf));
      }
    return null;
  }

  /**
   * Attempt to release resources used in index generation.
   */
  public void release() 
    throws IOException
  {
    Iterator partIter = this.partitionFiles.keySet().iterator();
    while (partIter.hasNext()) {
      String key = (String)partIter.next();
      ((RandomAccessFile)this.partitionFiles.get(key)).close();
      this.partitionFiles.remove(key);
    }
    if (this.postingsFile != null)  {
      this.postingsFile.close();
      this.postingsFile = null;
    }
  }

  /**
   * Implementation of toString to override default implementation in
   * java.lang.Object.
   */
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append(" indexname: ").append(indexname).append("\n");
    sb.append(" tablefilename: ").append(tablefilename).append("\n");
    sb.append(" indexParentDirectoryPath: ").append(indexParentDirectoryPath).append("\n");
    sb.append(" partitionFiles:").append(partitionFiles).append("\n");
    return sb.toString();
  }

  public static void main(String[] argv)
  {
    
  }

}// BSPIndex
