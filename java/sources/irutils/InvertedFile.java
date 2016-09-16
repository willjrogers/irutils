package irutils;

import java.util.*;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Implementation of Binary Search Partition Inverted File.
 * <p>
 * Example of use:
 * <pre>
 *   String indexRoot = "/home/wrogers/devel/exper/irutils/java/indices";
 *   String tableRoot = "/home/wrogers/devel/exper/irutils/java/tables";
 *   String indexname = "mrcon";
 *   // open a container to keep track of the indices       
 *   InvertedFileContainer bspContainer =
 *           new InvertedFileContainer(tableRoot, indexRoot);
 *
 *   // get a index instance for "MRCON"
 *   InvertedFile index = container.get(indexname);
 *   if (index == null) 
 *     System.out.println("index is null");
 *
 *   // check to see if index exists, if not then create it. 
 *   index.update();
 *
 *   // setup index for retrieval.
 *   index.setup();
 *   
 *   // lookup "C00001403"
 *   BSPTuple result = index.lookup("C00001403");
 *   List list = (List)result.getValue();
 *   for (Iterator j = list.iterator(); j.hasNext(); ) {
 *     System.out.println(j.next());
 *   }
 * </pre>
 * Resulting output:
 * <pre>
 *  C0001403|ENG|P|L0001403|PF|S0010794|Addison's Disease|0
 *  C0001403|ENG|P|L0001403|VC|S0352253|ADDISON'S DISEASE|0
 *  C0001403|ENG|P|L0001403|VC|S0354372|Addison's disease|0
 *  C0001403|ENG|P|L0001403|VO|S0010792|Addison Disease|0
 *  C0001403|ENG|P|L0001403|VO|S0010796|Addisons Disease|0
 *  C0001403|ENG|P|L0001403|VO|S0033587|Disease, Addison|0
 *  C0001403|ENG|P|L0001403|VO|S0352252|ADDISON DISEASE|0
 *  C0001403|ENG|P|L0001403|VO|S0469271|Addison's disease, NOS|3
 *  C0001403|ENG|P|L0001403|VO|S1911394|Disease;Addisons|3
 *   ... output truncated ...
 * </pre>
 * ifconfig for this table (see utils.InvertedFileContainer):
 * <pre>
 * NUM_TABLES: 1
 * MRCON|mrcon|8|0|cui|lat|ts|lui|stt|sui|str|lrl|TXT|TXT|TXT|TXT|TXT|TXT|TXT|TXT
 * </pre>
 * </p>
 * Created: Fri Jul  6 15:37:53 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: InvertedFile.java,v 1.11 2003/05/28 17:12:45 wrogers Exp $
 * @see irutils.InvertedFileContainer
 */

public class InvertedFile implements Serializable
{
  /** serialization version unique identifier for this class. */ 
  static final long serialVersionUID = -6131431462910547522L;
  /** canonical name of Serialized version of object */
  public static String canonicalSerializedName = "InvertedFileInfo.ser";
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
  // transient Map<String,Map<String,Integer>> hashlist = new HashMap<String,Map<String,Integer>>(350, 0.96f);
  transient Map<String,Map<String,Integer>> hashlist = new TreeMap<String,Map<String,Integer>>();

  /** hashmap of open partition files. */
  transient Map<String,Object> partitionFiles = new HashMap<String,Object>(5);

  /** postings file */
  transient RandomAccessFile postingsFile;

  /** mapped version of postings file */
  transient MappedByteBuffer postingsByteBuffer;

  /** if true, don't close index file pointer after release(). */
  transient boolean deferClosing = false;

  /** Is current index valid? */
  boolean valid = false;

  /** name of index */
  String indexname;

  /** name of table index is derived from */
  transient String tablefilename;

  /** directory path of parent directory for index */
  transient String indexParentDirectoryPath;

  /** format for data */
  List<String> indexFormat;
  
  /** number of words in index */
  int wordnum;

  /** number of records in each partition */
  Map<String,Integer> numrecs;

  /** datalen in dictionary part of each partition */
  Map<String,Integer> dataLength;

  /** list of key indices (Integer)  used for this index. (default is [0]) */
  List<Integer> keyIndices = null;		// if null, key index is zero

  /** display informational messages */
  private boolean verbose =
    Boolean.getBoolean(System.getProperty("ifbuild.verbose","false"));

  /** flag to use Memory Mapped version */
  private boolean useMappedFile = 
    Boolean.parseBoolean(System.getProperty("ifread.mapped","true"));

  /** lowercase all keys */
  boolean invfLowerCaseKeys = 
    Boolean.parseBoolean(System.getProperty("ifbuild.lowercase.keys","false"));

  /** flag to use MappedByteBuffer to build index (Memory Mapped version) */
  boolean useMappedByteBuffer = 
    Boolean.getBoolean(System.getProperty("ifbuild.usemappedbytebuffer", "false"));

  /** default constructor for serialization purposes only. */
  public InvertedFile()
  {
    if (this.verbose) {
      System.out.println("verbose mode is " + this.verbose);
      System.out.println("InvertedFile: instantiation");
    }
  }

  /** Constructor 
   * @param indexname      name of index
   * @param tablefilename   filename containing source table data
   * @param indexParentDir parent directory of index
   * @param format         table data format
   */
  public InvertedFile(String indexname, String tablefilename, 
		  String indexParentDir, List<String> format)
  {
    this.indexname = indexname;
    this.tablefilename = tablefilename;
    this.indexParentDirectoryPath = indexParentDir;
    this.indexFormat = format;
    List<String> keyList = utils.StringUtils.split(format.get(3), ",");
    // convert key indices to Integer.
    this.keyIndices = new ArrayList<Integer>();
    for (int i = 0; i < keyList.size(); i++) {
      keyIndices.add(new Integer(keyList.get(i)));
    }
  }

  /**
   * load table into in-memory term -> value map.
   Column zero is used as key for index.
   * @exception FileNotFoundException if an error occurs
   * @exception IOException if an error occurs
   */
  public void load_map()
     throws FileNotFoundException, IOException
  {
    // Load records into buckets based on term length.  Each bucket is
    // a TreeMap where record is stored by the ordinal value of the
    // first element (key) of the record.
    String line;
    String key = null;
    List<String> lineList;
    int i = 0;
    if (this.verbose) {
       System.out.println("loading map " + this.indexname );
    }
    TemporaryPostingsPool pool =
      new TemporaryPostingsPool(this.indexname + "_tposts", "rw");
    BufferedReader reader = 
      new BufferedReader(new FileReader( this.tablefilename ));
    while ( (line = reader.readLine()) != null )
      {
	Map<String,Integer> bucket;
	i++;
	if (line.trim().length() > 0) {
	  lineList = utils.StringUtils.split(line, "|");
	  if (lineList.size() > 0) {
	    if (this.keyIndices == null || 
		(this.keyIndices.size() == 1 && 
		 (this.keyIndices.get(0)).intValue() == 0)) {
	      if (invfLowerCaseKeys) {
		key = lineList.get(0).toLowerCase();
	      } else {
		key = lineList.get(0);
	      }
	    } else {
	      StringBuffer strBuf = new StringBuffer();
	      for (int j = 0; j < keyIndices.size(); j++) {
		int col = keyIndices.get(j).intValue();
		if (invfLowerCaseKeys) {
		  strBuf.append(lineList.get(col).toLowerCase());
		} else {
		  strBuf.append(lineList.get(col));
		}
	      }
	      key = strBuf.toString();
	    }
	  } else {
	    System.err.println("lineList size <= 0, line = " + line);
	  }
	}
	String keyLength = new Integer (key.length()).toString();
	bucket = this.hashlist.get(this.indexname+keyLength);
	if (bucket == null ) {
	  bucket = new TreeMap<String,Integer>();
	  this.hashlist.put(this.indexname+keyLength, bucket);
	  
	  /*List postings = new ArrayList();
	    postings.add(line);*/
	  bucket.put(key, new Integer(pool.add(line, -1)));
	} else {
	if ( bucket.containsKey(key) )
	  {
	    /*	    List postings = (List)bucket.get(key);
		    postings.add(line);*/
	    int link = bucket.get(key).intValue();
	    bucket.put(key, new Integer(pool.add(line, link)));
	  }
	else
	  {
	    /*List postings = new ArrayList();
	    postings.add(line);
	    bucket.put(key, postings);*/
	    bucket.put(key, new Integer(pool.add(line, -1)));
	  }
	}
	wordnum++;
	if (this.verbose) {
	  System.out.println("bucket.put(key: " + key + ", value), " + 
			     "keylength: " + keyLength + ", bucket size: " + bucket.size());
	}
      }
    reader.close();
    if (this.verbose) {
      System.out.println("# of input lines: " + i );
      System.out.println("# of buckets: " + hashlist.size());
    }
    Iterator<Map<String,Integer>> iter = hashlist.values().iterator();
    while (iter.hasNext()) {
      Map<String,Integer> bucket = iter.next();
      if (this.verbose) {
	System.out.println("bucket, size: " + bucket.size());
      }
    }
    pool.close();
  }

  /**
   * Generate disk-based word map from existing map in memory
   * @exception BSPIndexCreateException if an error occurs
   * @exception IOException if an error occurs
   */
  public void create()
    throws BSPIndexCreateException, IOException
  {
    RunLengthPostingsWriter postingsWriter = null;
    List<String> dictDataFormat = new ArrayList<String>(1);
    int rowLen = Integer.parseInt(indexFormat.get(2));
    List<String> typeList = new ArrayList<String>(rowLen);

    this.dataLength = new HashMap<String,Integer>(5);
    this.numrecs = new HashMap<String,Integer>(5);
    try {
      for (int i = 3 + rowLen, j = 0; i < 4 + rowLen + rowLen; i++, j++)
        {
          typeList.add(j, this.indexFormat.get(i));
	  if (this.verbose) {
	    System.out.println("type: " + indexFormat.get(i));
	  }
        }
    } catch (Exception exception) {
      exception.printStackTrace(System.err);
      throw new BSPIndexCreateException
        ("configuration file specified data length of " + 
         this.dataLength + 
         ", but was unable to access type field, please check configuration file ");
    }
    if (this.verbose) {
      System.out.println("indexFormat: " + utils.StringUtils.list(indexFormat));
      System.out.println("typeList: " + utils.StringUtils.list(typeList));
    }
    int dataLen = 0;
    List<String> dataFormatList = new ArrayList<String>(10);
    for (int i = 1; i < rowLen; i++ )
      {
        String fieldtype = typeList.get(i);
        dataFormatList.add(binFormats.get(fieldtype));
      }
    
    // create index

    File indexDirectory = new File(this.indexParentDirectoryPath + File.separator + this.indexname);
    if ((! indexDirectory.exists()) && (! indexDirectory.isDirectory()))
      {
	if (indexDirectory.mkdir() == false)
	  {
	    throw new BSPIndexCreateException
              ("unable to create index directory: " + this.indexParentDirectoryPath + 
               File.separator + this.indexname);
	  }
      }
    dictDataFormat.add(binFormats.get("PTR"));
    postingsWriter = new FileRunLengthPostingsWriter 
      (indexParentDirectoryPath + File.separator + this.indexname );
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
    rcfp.println( "bsp_map::index_org " + this.indexname + " TRUE INVERTED FILE ");
    rcfp.println( "bsp_map::dictdataformat " + this.indexname + " " +
		  utils.StringUtils.join(dictDataFormat, " ") + " " + dataLen );
    
    rcfp.println( "# quick load partition map for Tcl" );
    rcfp.println( "# format: " );
    rcfp.println( "#  bsp_map::partition <mapname> <term length> <partitionfilename> <num of records>" );
 
    
    Iterator<String> iter = this.hashlist.keySet().iterator();
    while (iter.hasNext()) 
      {
	String key = iter.next();
	Map<String,Integer> map = hashlist.get(key);
	
	buildInvertedFile(dictDataFormat, map, key, postingsWriter);
	int keylength = 0;
	Iterator<String> mapIter = map.keySet().iterator();
	if (mapIter.hasNext()) {
	  keylength = (mapIter.next()).length();
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
    if (this.verbose) {
      System.out.println("Index info: \n" + this);
    }
    /* serialize info on object to indexname/<Canonical Serialized Name> */
    FileOutputStream ostream = 
      new FileOutputStream(this.indexParentDirectoryPath + File.separator + this.indexname +
			   File.separator + canonicalSerializedName);
    ObjectOutputStream p = new ObjectOutputStream(ostream);
    
    p.writeObject(this);
    p.flush();
    p.close();
    ostream.close();
    }

  /**
   * Build index in inverted file organization.
   * @param dataFormat  format of data to be stored with key.
   * @param aTermMap    Map containing key/value pairs to be stored in index.
   * @param partitionId     partition identifier.
   * @param postingsWriter  postings file writer.
   */
  private void buildInvertedFile( List<String> dataFormat, 
				  Map<String,Integer> aTermMap, 
				  String partitionId, 
				  RunLengthPostingsWriter postingsWriter)
    throws IOException
  {
    int nextpost = 0;
    TemporaryPostingsPool pool = new TemporaryPostingsPool(this.indexname + "_tposts", "r");
    NioDictionaryBinSearchMap intPartition = 
      new NioDictionaryBinSearchMap ( indexParentDirectoryPath + File.separator +
				   this.indexname + File.separator + "partition_" + partitionId, 
				   BinSearchMap.WRITE );
    Iterator<String> keyIter = aTermMap.keySet().iterator();
    while (keyIter.hasNext()) {
      String termKey = keyIter.next();
      if (this.verbose) {
	System.out.println("termKey: " + termKey );
      }
      /* List postings = (List)aTermMap.get(termKey);*/
      int link = (aTermMap.get(termKey)).intValue();
      List<String> postings = pool.getv2(link);
      if (this.verbose) {
	System.out.println("postings size: " + postings.size());
      }
      Iterator<String> postingIter = postings.iterator();
      if (postingIter.hasNext()) 
	{
	  String dataRecord = postingIter.next();
	  if (this.verbose) {
	    System.out.println("dataRecord: " + dataRecord);
	  }
	  // write posting
	  nextpost = postingsWriter.writeString(dataRecord);
	  while (postingIter.hasNext()) 
	    {
	      dataRecord = postingIter.next();
	      // write posting
	      postingsWriter.writeString(dataRecord);
	    }
	}
      // write dictionary entry
      intPartition.writeEntry(termKey, postings.size(), nextpost);
    }
    synchronized (this.numrecs) {
      this.numrecs.put(partitionId, new Integer(intPartition.getNumberOfRecords()));
    }
    synchronized (this.dataLength) {
      this.dataLength.put(partitionId, new Integer(4));
    }
    intPartition.close();
    pool.close();
  }

  /**
   * If modification time of table file is later than index then rebuild index
   * using lisp file. See method "create".
   * @exception IOException if an error occurs
   * @exception BSPIndexCreateException if an error occurs
   */
  public void update()
    throws IOException, BSPIndexCreateException
  {
    String indexDir = this.indexParentDirectoryPath + File.separator + this.indexname;
    if (this.verbose) {
      System.out.println("updating index: " + this.indexname );
    }
    File tablefile = new File(this.tablefilename);
    File mapfile = new File(indexDir);
    if (this.verbose) {
      System.out.println("mapfile.exists(): " + mapfile.exists());
    }
    if (mapfile.exists() == false ||
	tablefile.lastModified() > mapfile.lastModified())  {
      if (this.hashlist == null) 
	{
	  this.hashlist = new HashMap<String,Map<String,Integer>>(5);
	}
      this.load_map();
      this.create();
    } else if (mapfile.isFile())  {
	throw new IOException("file " + indexname + " is not a directory!");
    } 
  } // InvertedFile.update


  /**
   * setup newly read serialized index.
   * @exception BSPIndexInvalidException if an error occurs
   */
  public void setup()
    throws BSPIndexInvalidException
  {
    if ( this.valid == false ) {
      throw new BSPIndexInvalidException("Index is not valid. run method update(), " + this);
    }
    if ( this.partitionFiles == null ) {
      this.partitionFiles = new HashMap<String,Object>(4);
    }
  }

  /**
   * Look up word in index, return corresponding key and value pair if
   * found, null if otherwise.
   * @param word word to lookup in index.
   * @return tuple containing key/value pair, null if key not found.
   * @exception FileNotFoundException if an error occurs
   * @exception IOException if an error occurs
   */
  public BSPTuple<List<String>> lookup(String word)
    throws FileNotFoundException, IOException
  {
    return lookup(word, false); // don't load everything at once.
  }

  /**
   * Look up word in index, return corresponding key and value pair if
   * found, null if otherwise.
   * @param targetWord word to lookup in index.
   * @param loadAllData if true then load all the data.
   * @return tuple containing key/value pair, null if key not found.
   * @exception FileNotFoundException if an error occurs
   * @exception IOException if an error occurs
   */
  public BSPTuple<List<String>> lookup(String targetWord, boolean loadAllData)
    throws FileNotFoundException, IOException
  {
    RandomAccessFile dictionaryRAFFile;
    MappedByteBuffer dictionaryByteBuffer = null;
    File dictionaryFile;
    DictionaryEntry entry;
    String word;
    if (this.invfLowerCaseKeys) {
      word = targetWord.toLowerCase();
    } else {
      word = targetWord;
    }
    String keyLength = new Integer (word.length()).toString();
    String key = this.indexname + keyLength;
    List<String> postings;
    // if (this.verbose) {
    //   System.out.println("lookup(): verbose mode is " + this.verbose);
    //   System.out.println("lookup(): this.partitionFiles: " + this.partitionFiles );
    // }
    if (useMappedFile) {
      if ( this.partitionFiles.containsKey(key) ) 
	{
	  dictionaryByteBuffer = (MappedByteBuffer)this.partitionFiles.get(key);
	}
      else 
	{
	  File partitionFile = 
	    new File ( indexParentDirectoryPath + File.separator +
		       indexname + File.separator +
		       "partition_" + key );
	  if (partitionFile.exists()) {
	    FileChannel dictionaryFileChannel = 
	      new FileInputStream(partitionFile).getChannel();
	    int sz = (int)dictionaryFileChannel.size();
	    dictionaryByteBuffer = 
	      dictionaryFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, sz);
	    this.partitionFiles.put(key, dictionaryByteBuffer);
	    dictionaryFileChannel.close();
	  } else {
	    // partition doesn't exist.
	    return new BSPTuple<List<String>>(word, new ArrayList<String>(0));
	  }
	}
      if (this.verbose) {
	System.out.println("mapping file");
      }
      entry =
	MappedFileBinarySearch.dictionaryBinarySearch(dictionaryByteBuffer, word, word.length(), 
						      (this.numrecs.get(key)).intValue() );
    } else {
      // if (this.verbose) {
      // 	System.out.println("lookup(): opening file for random access");
      // }
      try {
	if ( this.partitionFiles.containsKey(key) ) 
	  {
	    dictionaryRAFFile = (RandomAccessFile)this.partitionFiles.get(key);
	  }
	else 
	  {
	    dictionaryRAFFile = 
	      new RandomAccessFile ( indexParentDirectoryPath + File.separator +
				     indexname + File.separator + "partition_" + key, "r" );
	    
	    this.partitionFiles.put(key, dictionaryRAFFile);
	  } 
	entry = 
	  DiskBinarySearch.dictionaryBinarySearch(dictionaryRAFFile, word, word.length(), 
						  (this.numrecs.get(key)).intValue() );
      } catch (FileNotFoundException exception) {
	return new BSPTuple<List<String>>(word, new ArrayList<String>(0)); // partition not found, return an empty list
      }
    }
    if (entry == null) {
      return new BSPTuple<List<String>>(word, new ArrayList<String>(0)); // entry not found, return an empty list
    }
    long count = entry.getNumberOfPostings();
    long address = entry.getAddress();
    // if (this.verbose) {
    //   System.out.println("lookup(): postings count : " + count);
    //   System.out.println("lookup(): address : " + address);
    // }
    if (useMappedFile) {
      if ( this.postingsByteBuffer == null ) {
	FileChannel postingsFileChannel = 
	  (new FileInputStream( new File ( indexParentDirectoryPath + 
					   File.separator + indexname +
					   File.separator + "postings"))).getChannel();
	int sz = (int)postingsFileChannel.size();
	if (this.verbose) {
	  System.out.println("lookup(): mapping buffer of size: " + sz);
	}
	try {
	  this.postingsByteBuffer =
	    postingsFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, sz);
	} catch (IOException exception) {
	  System.err.println("Exception mapping postings buffer of size: " + sz );
	  throw exception;
	}
	// postingsFileChannel.close();
      }
      if (loadAllData)
	{
	  postings = new ArrayList<String>((int)count);
	  this.postingsByteBuffer.position((int)address);
	  for (int i = 0; i < count; i++)
	    {
	      int postingsLen = this.postingsByteBuffer.getInt();
	      byte[] databuf = new byte[postingsLen];
	      this.postingsByteBuffer.get(databuf);
	      postings.add(new String(databuf));
	    }
	} else {
	postings = new MappedPostingsList(this.postingsByteBuffer, (int)address, (int)count);
      }
    } else {
      if ( this.postingsFile == null ) {
	this.postingsFile = 
	  new RandomAccessFile ( indexParentDirectoryPath + File.separator +
				 indexname + File.separator + "postings", "r" );
      }
      if (loadAllData)
	{
	  postings = new ArrayList<String>((int)count);
	  postingsFile.seek(address);
	  for (int i = 0; i < count; i++)
	    {
	      int postingsLen = postingsFile.readInt();
	      // if (this.verbose) {
	      // 	System.out.println("lookup(): postingsLen : " + postingsLen);
	      // }
	      byte[] databuf = new byte[postingsLen];
	      postingsFile.read(databuf);
	      postings.add(new String(databuf));
	    }
	} else {
	if (this.verbose) {
	  System.out.println("lookup(): postingsFile: " + postingsFile);
	}
	postings = new PostingsList(postingsFile, address, (int)count);
      }
    }
    return new BSPTuple<List<String>>(word, postings);
  }

  /**
   * if true, don't close index file pointer after release().
   *
   * @param status a <code>boolean</code> value,
   *    if true, don't close index file pointer after release().
   */
  public void setDeferClosing(boolean status)
  {
    this.deferClosing = status;
  }

  /**
   * Attempt to release resources used in index generation.
   * @exception IOException if an error occurs
   */
  public void release() 
    throws IOException
  {
    Iterator<String> partIter = this.partitionFiles.keySet().iterator();
    while (partIter.hasNext()) {
      String key = partIter.next();
      if (! useMappedFile) {
	RandomAccessFile raFile = (RandomAccessFile)this.partitionFiles.get(key);
	raFile.close();
	if (this.postingsFile != null && deferClosing == false)  {
	  this.postingsFile.close();
	  this.postingsFile = null;
	}
      } 
      // else {
	// MappedByteBuffer dictionaryByteBuffer = (MappedByteBuffer)this.partitionFiles.get(key);
	// dictionaryByteBuffer.finalize();
	// if (this.postingsByteBuffer != null && deferClosing == false)  {
	//	  this.postingsByteBuffer = null;
	//}
      //}
      this.partitionFiles.remove(key);
    }
  }

  /**
   * List keys of all OPEN partition files.
   *
   * @return a <code>List</code> value
   */
  public List<String> listKeys()
  {
    Iterator<Object> iter = this.partitionFiles.values().iterator();
    while (iter.hasNext()) 
      {
 	RandomAccessFile dictionaryFile = (RandomAccessFile)iter.next();
      }
    return new ArrayList<String>();
  }

  
  /**
   * implementation of Object's finalize() method.
   * @exception Throwable if an error occurs
   */
  protected void finalize()
    throws Throwable
  {
    // try to release what we can and then call superclass's version
    // of this method. 
    try {
      this.release();
    } catch (IOException exception) {
      throw new java.lang.RuntimeException(exception.getMessage());
    }
    super.finalize();
  }

  public String getIndexName() {
    return this.indexname;
  }

  public void setUseMappedFile(boolean state) {
    this.useMappedFile = state;
  }

  public void setInvfLowerCaseKeys(boolean state) {
    this.invfLowerCaseKeys = state;
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
    sb.append(" numrecs: ").append(numrecs).append("\n");
    sb.append(" dataLength: ").append(dataLength).append("\n");
    sb.append(" keyIndices: ").append(keyIndices).append("\n");
    sb.append(" partitionFiles: ").append(partitionFiles).append("\n");
    return sb.toString();
  }

}// InvertedFile
