package irutils;

import java.util.*;
import java.io.*;

/**
 * Implementation of Binary Search Partition Index.
 * <p>
 * Example of use:
 * <pre>
 *   String indexRoot = "/home/wrogers/devel/exper/irutils/java/indices";
 *   String tableRoot = "/home/wrogers/devel/exper/irutils/java/tables";
 *   String indexname = "recommendations";
 *   // open a container to keep track of the indices       
 *   BSPContainer bspContainer = new BSPContainer(tableRoot, indexRoot);
 *
 *   // check to see if index exists, if not then create it. 
 *   bspIndex.update();
 *
 *   // setup index for retrieval.
 *   bspIndex.setup();
 *  
 *   BSPTuple result = bspIndex.lookup("zytoplasm");
 *   System.out.println("result: " + result);
 * </pre>
 * </p>
 * Created: Fri Jul  6 15:37:53 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: BSPIndex.java,v 1.10 2002/10/08 20:32:58 wrogers Exp $
 */

public class BSPIndex implements Serializable
{
  /** index is organized as an file array: a dictionary contain keys both and data. */
  static final int FILEARRAY = 0;
  /** index is organized as an inverted file: a dictionary file and a postings file. */
  static final int INVERTED_FILE = 1;
  static String indexOrgTypeString[] = new String[] { "FILEARRAY", "INVERTEDFILE" };
  /** list of supportted binary formats */
  static Map<String,String> binFormats = new HashMap<String,String>(4);
  /** do this once at class instantiation */
  static
   {
     binFormats.put("INT", "d1");
     binFormats.put("TXT", "a");
     binFormats.put("PTR", "i1");
   }

  /** hashlist of hash or tree maps for generating new indices. */
  transient Map<String,Map<String,String>> hashlist = new HashMap<String,Map<String,String>>(5);

  /** hashmap of open partition files. */
  transient Map<String, RandomAccessFile> partitionFiles = new HashMap<String, RandomAccessFile>(5);

  /** postings file */
  transient RandomAccessFile postingsFile;

  /** Is current index valid? */
  boolean valid = false;

  /** name of index */
  String indexname;

  /** name of table index is derived from */
  String tablefilename;

  /** directory path of parent directory for index */
  String indexParentDirectoryPath;

  /** format for data */
  List<String> indexFormat;
  
  /** number of words in index */
  int wordnum;

  /** the index organization: FILEARRAY, INVERTEDFILE, etc. */
  int indexOrg;

  /** number of records in each partition */
  Map<String,Integer> numrecs;

  /** datalen in dictionary part of each partition */
  Map<String,Integer> dataLength;

  /** index of key used for this index. */
  int keyIndex = 0;		// default key index is zero

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
		  String indexParentDir, List<String> format)
  {
    this.indexname = indexname;
    this.tablefilename = tablefilename;
    this.indexParentDirectoryPath = indexParentDir;
    this.indexFormat = format;
  }

  /** Constructor 
   * @param indexname      name of index
   * @param tablefilename   filename containing source table data
   * @param indexParentDir parent directory of index
   * @param format         table data format
   * @param keyIndex       key column index to be used for searching.
   */
  public BSPIndex(String indexname, String tablefilename, 
		  String indexParentDir, List<String> format, 
		  int keyIndex)
  {
    this.indexname = indexname;
    this.tablefilename = tablefilename;
    this.indexParentDirectoryPath = indexParentDir;
    this.indexFormat = format;
    this.keyIndex = keyIndex;
  }

  /** load table into in-memory term -> value map.
      Column zero is used as key for index. */
  public void load_map()
     throws FileNotFoundException, IOException
  {
    this.load_map(0);
  }

  /**
   * load table into in-memory term -> value map.
   * @param keyIndex index of col to be used as key for index.
   */
  public void load_map(int keyIndex)
     throws FileNotFoundException, IOException
  {
    // Load records into buckets based on term length.  Each bucket is
    // a TreeMap where record is stored by the ordinal value of the
    // first element (key) of the record.
    String line;
    String key = null;
    List<String> lineList;
    int i = 0;
    // System.out.println("loading map " + this.indexname );
    BufferedReader reader = 
      new BufferedReader(new FileReader( this.tablefilename ));
    while ( (line = reader.readLine()) != null )
      {
	Map<String,String> bucket;
	i++;
	if (line.trim().length() > 0) {
	  lineList = utils.StringUtils.split(line, "|");
	  if (lineList.size() > 0) {
	    key = (String)lineList.get(keyIndex);
	  } else {
	    System.err.println("lineList size <= 0, line = " + line);
	  }
	}
	String keyLength = new Integer (key.length()).toString();
	bucket = this.hashlist.get(this.indexname+keyLength);
	if (bucket == null ) {
	  bucket = new TreeMap<String,String>();
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
    for (Map<String,String> bucket : hashlist.values() ) {
      // Iterator iter = hashlist.values().iterator();
      // while (iter.hasNext()) {
      // Map<String,String> bucket = iter.next();
      System.out.println("bucket, size: " + bucket.size());
    }
  }

  /**
   * Generate disk-based word map from existing map in memory
   * @exception BSPIndexCreateException occurs if index cannot be created.
   */
  public void create()
    throws BSPIndexCreateException, IOException
  {
    RunLengthPostingsWriter postingsWriter = null;
    List<String> dictDataFormat = new ArrayList<String>(1);
    int rowLen = Integer.parseInt(this.indexFormat.get(2));
    List<String> typeList = new ArrayList<String>(rowLen);
    this.dataLength = new HashMap<String,Integer>(5);
    this.numrecs = new HashMap<String,Integer>(5);
    for (int i = 3 + rowLen, j = 0; i < 3 + rowLen + rowLen; i++, j++)
      {
	typeList.add(j, this.indexFormat.get(i));
	// System.out.println("type: " + indexFormat.get(i));
      }
    // System.out.println("indexFormat: " + StringUtils.list(indexFormat));
    // System.out.println("typeList: " + StringUtils.list(typeList));
    int dataLen = 0;
    this.indexOrg = FILEARRAY;

    List<String> dataFormatList = new ArrayList<String>(10);
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

    File indexDirectory = new File(this.indexParentDirectoryPath + File.separator + this.indexname);
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
	postingsWriter = new FileRunLengthPostingsWriter 
	  (indexParentDirectoryPath + File.separator + this.indexname );
	break;
      }
    PrintWriter statfp = new PrintWriter
      (new BufferedWriter(new FileWriter( indexParentDirectoryPath + File.separator +
					  this.indexname + File.separator + "partition.stats")));
    statfp.println( "# " + this.indexname + File.separator + "partition.log -- bsp_map.tcl status file" );
    statfp.println( "# total number of terms: " + this.wordnum );
    statfp.println( "#" );
    statfp.println( "# table format: " );
    statfp.println( "#  partition_filename termlen nterms" );

    PrintWriter rcfp = new PrintWriter
      (new BufferedWriter(new FileWriter( indexParentDirectoryPath + File.separator +
					  this.indexname + File.separator + "mapinforc.tcl")));
    rcfp.println( "# Tcl rc file for bsp_map." );
    rcfp.println( "#" );
    rcfp.println( "# record format:" );
    rcfp.println( "#   " + utils.StringUtils.join(indexFormat, "|") );
    rcfp.println( "bsp_map::mapformat " + this.indexname + " " +
		  utils.StringUtils.list(indexFormat));
    rcfp.println( "bsp_map::index_org " + this.indexname + " " + indexOrgTypeString[indexOrg] );
    rcfp.println( "bsp_map::dictdataformat " + this.indexname + " " +
		  utils.StringUtils.join(dictDataFormat, " ") + " " + dataLen );
    
    rcfp.println( "# quick load partition map for Tcl" );
    rcfp.println( "# format: " );
    rcfp.println( "#  bsp_map::partition <mapname> <term length> <partitionfilename> <num of records>" );
 
    
    Iterator iter = this.hashlist.keySet().iterator();
    while (iter.hasNext()) 
      {
	String key = (String)iter.next();
	Map map = (Map)hashlist.get(key);
	
	switch (indexOrg)
	  {
	  case FILEARRAY:
	    buildFileArray(dictDataFormat, map, key);
	    break;
	  case INVERTED_FILE:
	    buildInvertedFile(dictDataFormat, map, key, postingsWriter);
	    break;
	  }
	int keylength = 0;
	Iterator mapIter = map.keySet().iterator();
	if (mapIter.hasNext()) {
	  keylength = ((String)mapIter.next()).length();
	}
	statfp.println( "partition_" + key + " " + 
			keylength + " " + map.size());
	rcfp.println( "bsp_map::partition " + this.indexname + " " +
		      keylength + " partition_" + key + " " + map.size());
      }
    
    postingsWriter.close();
    statfp.close();
    rcfp.close();
    // we've gotten this far, assume we have a valid index.
    this.valid = true;
    System.out.println("Index info: \n" + this);

    /* serialize info on object to indexname/bspIndexInfo.ser */
    FileOutputStream ostream = 
      new FileOutputStream(this.indexParentDirectoryPath + File.separator + this.indexname +
			   File.separator + "bspIndexInfo.ser");
    ObjectOutputStream p = new ObjectOutputStream(ostream);
    
    p.writeObject(this);
    p.flush();
    p.close();
    ostream.close();
  }

  /**
   * Build index in file array organization.
   * @param dataFormat  format of data to be stored with key.
   * @param aTermMap    Map containing key/value pairs to be stored in index.
   * @param partitionId     partition identifier.
   */
  private void buildFileArray( List dataFormat, Map aTermMap, String partitionId)
    throws IOException
  {
    BinSearchMap writer = 
      new DataBinSearchMap ( this.indexParentDirectoryPath + File.separator +
			 this.indexname + File.separator + "partition_" + partitionId, BinSearchMap.WRITE );
    Iterator keyIter = aTermMap.values().iterator();
    while (keyIter.hasNext()) {
      String key = (String)keyIter.next();
      String dataRecord = (String)aTermMap.get(key);
      List dataList = utils.StringUtils.split(dataRecord, "|");
      Iterator iter = dataFormat.iterator();
      //writer.writeEntry(key, );
    }
    this.numrecs.put(partitionId, new Integer(writer.getNumberOfRecords()));
    this.dataLength.put(partitionId, new Integer(writer.getDataLength()));
    writer.close();
  }

  /**
   * Build index in inverted file organization.
   * @param dataFormat  format of data to be stored with key.
   * @param aTermMap    Map containing key/value pairs to be stored in index.
   * @param partitionId     partition identifier.
   * @param postingsWriter  postings file writer.
   */
  private void buildInvertedFile( List dataFormat, Map aTermMap, String partitionId, 
				  RunLengthPostingsWriter postingsWriter)
    throws IOException
  {
    int nextpost = 0;
    int numrecs = 0;
    IntBinSearchMap intPartition = 
      new IntBinSearchMap ( indexParentDirectoryPath + File.separator +
			    this.indexname + File.separator + "partition_" 
                            + partitionId, BinSearchMap.WRITE );
    Iterator keyIter = aTermMap.keySet().iterator();
    while (keyIter.hasNext()) {
      String termKey = (String)keyIter.next();
      String dataRecord = (String)aTermMap.get(termKey);
      // write posting
      nextpost = postingsWriter.writeString(dataRecord);
      // write dictionary entry
      intPartition.writeEntry(termKey, nextpost);
    }
    this.numrecs.put(partitionId, new Integer(intPartition.getNumberOfRecords()));
    // System.out.println("key: " + key );
    this.dataLength.put(partitionId, new Integer(4));
    intPartition.close();
  }

  /**
   * If modification time of table file is later than index then rebuild index
   * using lisp file. See method "create".
   */
  public void update()
    throws IOException, BSPIndexCreateException
  {
    String indexDir = this.indexParentDirectoryPath + File.separator + this.indexname;
    // System.out.println("updating index: " + this.indexname );
    File tablefile = new File(this.tablefilename);
    File mapfile = new File(indexDir);
    // System.out.println("mapfile.exists(): " + mapfile.exists());
    if (mapfile.exists() == false ||
	tablefile.lastModified() > mapfile.lastModified())  {
	this.load_map(this.keyIndex);
	this.create();
    } else if (mapfile.isFile())  {
	throw new IOException("file " + indexname + " is not a directory!");
    } 
  } // BSPIndex.update


  /**
   * setup newly read serialized index.
   */
  public void setup()
    throws BSPIndexInvalidException
  {
    if ( this.valid == false ) {
      throw new BSPIndexInvalidException("Index is not valid. run method update(), "+ this);
    }
    if ( this.partitionFiles == null ) {
      this.partitionFiles = new HashMap<String,RandomAccessFile>(4);
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
    System.out.println("this.partitionFiles: " + this.partitionFiles );
    if ( this.partitionFiles.containsKey(key) ) 
      {
	dictionaryFile = (RandomAccessFile)this.partitionFiles.get(key);
      }
    else 
      {
	dictionaryFile = 
	  new RandomAccessFile ( indexParentDirectoryPath + File.separator +
				 indexname + "/partition_" + key, "r" );
	this.partitionFiles.put(key, dictionaryFile);
      } 
    switch (this.indexOrg)
      {
      case FILEARRAY:
	break;
      case INVERTED_FILE:
	int address = DiskBinarySearch.intBinarySearch(dictionaryFile, word, word.length(), 
			    ((Integer)this.numrecs.get(key)).intValue() );
	if (address == -1)
	  return null;
	// System.out.println("address : " + address);
	if ( this.postingsFile == null ) {
	  this.postingsFile = new RandomAccessFile ( indexParentDirectoryPath + File.separator +
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
   * @return string representation of index object.
   */
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append(" indexname: ").append(indexname).append("\n");
    sb.append(" tablefilename: ").append(tablefilename).append("\n");
    sb.append(" indexParentDirectoryPath: ").append(indexParentDirectoryPath).append("\n");
    sb.append(" indexOrg: ").append(indexOrg).append("\n");
    sb.append(" numrecs: ").append(numrecs).append("\n");
    sb.append(" dataLength: ").append(dataLength).append("\n");
    sb.append(" keyIndex: ").append(keyIndex).append("\n");
    sb.append(" partitionFiles: ").append(partitionFiles).append("\n");
    return sb.toString();
  }

  /**
   * main program 
   * @param argv argument vector.
   */
  public static void main(String[] argv)
  {
    
  }

}// BSPIndex
